package dev.android.ide.runtime

import android.content.Context
import android.content.Intent
import android.system.Os
import android.system.OsConstants
import androidx.core.content.ContextCompat
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import dev.android.ide.contracts.ChildProcessDescriptor
import dev.android.ide.contracts.ErrorCategory
import dev.android.ide.contracts.OperationOutcome
import dev.android.ide.contracts.OperationReport
import dev.android.ide.contracts.ProjectIdentity
import dev.android.ide.contracts.ProjectLocation
import dev.android.ide.contracts.RuntimeCapabilities
import dev.android.ide.contracts.RuntimeAvailability
import dev.android.ide.contracts.RuntimePackage
import dev.android.ide.contracts.RuntimeWorkspaceAdapter
import dev.android.ide.contracts.SessionAvailability
import dev.android.ide.contracts.SessionDescriptor
import dev.android.ide.contracts.TerminalProjectAccess
import dev.android.ide.contracts.TerminalRuntimeAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Lifecycle-owned terminal runtime backed by the pinned Termux terminal-emulator.
 *
 * The terminal emulator owns ANSI parsing, scrollback, keyboard encoding and PTY
 * I/O. This adapter owns only Android IDE session identity, runtime paths and
 * lifecycle. The Termux bootstrap remains a separate, ABI-selected build asset.
 */
class TerminalRuntimeAdapterImpl(context: Context) : TerminalRuntimeAdapter, RuntimeWorkspaceAdapter {
    private val appContext = context.applicationContext
    private val sessionStore = RuntimeSessionStore(appContext)
    private val bundledInstaller = BundledTermuxRuntimeInstaller(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessions = ConcurrentHashMap<String, LiveSession>()
    private val viewInvalidators = ConcurrentHashMap<String, () -> Unit>()
    @Volatile private var explicitlyShuttingDown = false
    private val termuxPrefix get() = bundledInstaller.prefix()
    private val shell get() = File(termuxPrefix, "bin/sh")
    private val packageManager get() = File(termuxPrefix, "bin/pkg")
    private val npm get() = File(termuxPrefix, "bin/npm")
    private val runtimeHome get() = bundledInstaller.home()
    private val developerBootstrapMarker get() = File(runtimeHome, ".android-ide-developer-bootstrap-v1")
    @Volatile private var developerBootstrapAttempted = false

    override suspend fun initialize(): OperationReport {
        val bootstrap = bundledInstaller.initialize()
        if (bootstrap.outcome != OperationOutcome.COMPLETE) return bootstrap
        if (!termuxPrefix.exists() || !shell.canExecute()) {
            return OperationReport(
                OperationOutcome.BLOCKED,
                "Terminal is unavailable. Initialize Terminal before opening a command-line session.",
                ErrorCategory.UNAVAILABLE_RUNTIME,
                emptyList(),
                "Keep the project registered and retry Terminal initialization when Terminal is available.",
            )
        }
        runtimeHome.mkdirs()
        // A TerminalSession owns an in-process PTY and cannot be reattached after
        // Android recreates the process. Drop stale descriptors and let the UI
        // create one fresh home-directory session instead of piling up dead rows.
        sessionStore.clear()
        return OperationReport(OperationOutcome.COMPLETE, "Terminal is available")
    }

    override suspend fun providerRootLocation(): ProjectLocation? =
        runtimeHome.takeIf { it.exists() }?.let {
            ProjectLocation(it.canonicalPath, "Terminal workspace", it.canonicalPath)
        }

    override suspend fun capabilities(): RuntimeCapabilities {
        val available = termuxPrefix.exists() && shell.canExecute()
        return RuntimeCapabilities(
            availability = if (available) RuntimeAvailability.AVAILABLE else RuntimeAvailability.UNAVAILABLE,
            architecture = System.getProperty("os.arch"),
            shellAvailable = available,
            ptyAvailable = available,
            packageManagerAvailable = available && packageManager.canExecute(),
            executableFilesSupported = available,
            symlinksSupported = available,
            explanation = if (available) null else "Terminal files were not found",
        )
    }

    override suspend fun installedPackages(): List<RuntimePackage> {
        if (!packageManager.isFile || !shell.canExecute()) return emptyList()
        return runCatching {
            ProcessBuilder(shell.absolutePath, packageManager.absolutePath, "list-installed")
                .redirectErrorStream(true).start().inputStream.bufferedReader().readText()
                .lineSequence().mapNotNull { line ->
                    val value = line.trim().removePrefix("Installing ").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val split = value.split("/", limit = 2)
                    RuntimePackage(split.first(), split.getOrNull(1), installed = true)
                }.toList()
        }.getOrDefault(emptyList())
    }

    override suspend fun installPackages(packages: List<String>): OperationReport {
        if (packages.isEmpty()) return OperationReport(OperationOutcome.BLOCKED, "Choose at least one package", ErrorCategory.PACKAGE_FAILURE)
        if (!packageManager.isFile || !shell.canExecute()) return unavailable("The terminal package manager is unavailable")
        return runCommand(listOf(shell.absolutePath, packageManager.absolutePath, "install", "-y") + packages, runtimeHome)
    }

    override suspend fun inspectProjectAccess(project: ProjectIdentity): TerminalProjectAccess {
        if (capabilities().availability != RuntimeAvailability.AVAILABLE) return TerminalProjectAccess(false, "Terminal is unavailable")
        val path = project.location.userVisiblePath ?: return TerminalProjectAccess(false, "This project has no terminal working location")
        // Android SAF locations are content URIs, not POSIX paths. They remain
        // valid project locations, but cannot be passed as cwd to a native PTY.
        // The session will start in the Terminal workspace instead of reporting
        // the selected project as an invalid directory.
        if (path.startsWith("content://")) {
            return TerminalProjectAccess(true, "This project uses Android document storage, so the command-line terminal will open in its Terminal home workspace instead of the project folder")
        }
        val directory = File(path)
        return TerminalProjectAccess(directory.isDirectory, "The selected project location is not an accessible folder".takeUnless { directory.isDirectory })
    }

    override suspend fun workingDirectory(project: ProjectIdentity): String? = project.location.userVisiblePath?.takeIf {
        !it.startsWith("content://") && File(it).isDirectory
    }

    override suspend fun createSession(workingDirectory: String?, name: String): SessionDescriptor {
        val id = UUID.randomUUID().toString()
        val sessionName = name.trim().ifBlank { "Untitled session" }
        if (!shell.canExecute()) return unavailableSession(id, sessionName, "Terminal is unavailable")
        // Package setup is deliberately first-use and idempotent. A failed network
        // operation must not prevent the shell itself from opening.
        if (!developerBootstrapMarker.exists() && !developerBootstrapAttempted) {
            developerBootstrapAttempted = true
            runCatching { ensureDeveloperPackages() }
        }
        val directory = workingDirectory?.let(::File)?.takeIf { it.isDirectory } ?: runtimeHome
        return runCatching {
            val environment = arrayOf(
                "HOME=${runtimeHome.absolutePath}",
                "PREFIX=${termuxPrefix.absolutePath}",
                "PATH=${File(termuxPrefix, "bin").absolutePath}:/system/bin:/system/xbin",
                "TERM=xterm-256color",
                "LANG=C.UTF-8",
            )
            val session = TerminalSession(shell.absolutePath, directory.absolutePath, arrayOf(shell.absolutePath), environment, TRANSCRIPT_ROWS, sessionClient)
            val descriptor = SessionDescriptor(id, "global", session.mHandle, sessionName, directory.absolutePath, Instant.now(), SessionAvailability.AVAILABLE)
            ContextCompat.startForegroundService(appContext, Intent(appContext, TerminalForegroundService::class.java))
            sessions[id] = LiveSession(descriptor, session)
            sessionStore.upsert(descriptor)
            descriptor
        }.getOrElse { error -> unavailableSession(id, sessionName, error.message ?: "Unable to start terminal session") }
    }

    /** Returns the live upstream session for the Android View bridge. */
    fun terminalSession(sessionId: String): TerminalSession? {
        val live = sessions[sessionId] ?: return null
        if (!live.session.isRunning()) {
            markUnavailable(sessionId, "The terminal process ended")
            return null
        }
        return live.session
    }

    fun bindTerminalView(sessionId: String, invalidate: () -> Unit) {
        viewInvalidators[sessionId] = invalidate
    }

    fun unbindTerminalView(sessionId: String) {
        viewInvalidators.remove(sessionId)
    }

    override suspend fun listSessions(): List<SessionDescriptor> {
        sessions.values.filterNot { it.session.isRunning() }.forEach { markUnavailable(it.descriptor.id, "The terminal process ended") }
        return sessions.values.map { it.descriptor }.sortedBy { it.createdAt }
    }

    override suspend fun renameSession(sessionId: String, name: String): OperationReport {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return OperationReport(OperationOutcome.BLOCKED, "Enter a session name", ErrorCategory.PROCESS_LOSS)
        val live = sessions[sessionId]
        val stored = sessionStore.readAll().firstOrNull { it.id == sessionId }
        if (live == null && stored == null) return unavailable("The terminal session is unavailable")
        val updated = (live?.descriptor ?: stored!!).copy(name = cleanName)
        live?.descriptor = updated
        sessionStore.upsert(updated)
        return OperationReport(OperationOutcome.COMPLETE, "Terminal session renamed", affectedIds = listOf(sessionId))
    }

    override suspend fun sendInput(sessionId: String, input: ByteArray): OperationReport {
        val session = liveSession(sessionId)?.session ?: return unavailable("The terminal session is unavailable")
        return runCatching {
            session.write(input, 0, input.size)
            OperationReport(OperationOutcome.COMPLETE, "Input sent")
        }.getOrElse { OperationReport(OperationOutcome.FAILED, "Could not send terminal input", ErrorCategory.PROCESS_LOSS) }
    }

    override suspend fun readOutput(sessionId: String): ByteArray? = liveSession(sessionId)?.session?.let { snapshot(it).toByteArray(Charsets.UTF_8) }

    override suspend fun resize(sessionId: String, columns: Int, rows: Int): OperationReport {
        val session = liveSession(sessionId)?.session ?: return unavailable("The terminal session is unavailable")
        if (columns <= 0 || rows <= 0) return OperationReport(OperationOutcome.BLOCKED, "The terminal size is invalid", ErrorCategory.PROCESS_LOSS)
        return runCatching {
            session.updateSize(columns, rows, CELL_WIDTH_PX, CELL_HEIGHT_PX)
            OperationReport(OperationOutcome.COMPLETE, "Terminal size updated")
        }.getOrElse { OperationReport(OperationOutcome.FAILED, "Could not resize the terminal", ErrorCategory.PROCESS_LOSS) }
    }

    override suspend fun interrupt(sessionId: String): OperationReport {
        val session = liveSession(sessionId)?.session ?: return unavailable("The terminal session is unavailable")
        session.write(byteArrayOf(3), 0, 1)
        return OperationReport(OperationOutcome.COMPLETE, "Terminal session interrupted")
    }

    override suspend fun listChildProcesses(sessionId: String): List<ChildProcessDescriptor> {
        val live = liveSession(sessionId) ?: return emptyList()
        return runCatching {
            val shellPid = live.session.getPid().toLong()
            ProcessBuilder("/system/bin/ps", "-o", "PID,PPID,PGID,ARGS").redirectErrorStream(true).start()
                .inputStream.bufferedReader().readText().lineSequence().drop(1).mapNotNull { line ->
                    val parts = line.trim().split(Regex("\\s+"), limit = 4)
                    val pid = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
                    val pgid = parts.getOrNull(2)?.toLongOrNull() ?: return@mapNotNull null
                    if (pid == shellPid || pgid != shellPid) return@mapNotNull null
                    ChildProcessDescriptor(pid.toString(), sessionId, parts.getOrNull(3).orEmpty(), pid, live.descriptor.workingDirectory, Instant.now(), SessionAvailability.AVAILABLE)
                }.toList()
        }.getOrDefault(emptyList())
    }

    override suspend fun terminateChildProcess(processId: String): OperationReport {
        val targetPid = processId.toLongOrNull()?.takeIf { it > 0 }
            ?: return OperationReport(OperationOutcome.BLOCKED, "The selected child process is invalid", ErrorCategory.PROCESS_LOSS)
        val owner = sessions.values.firstOrNull { live ->
            val shellPid = live.session.getPid().toLong()
            shellPid > 0 && targetPid != shellPid && processGroup(targetPid) == shellPid
        } ?: return OperationReport(OperationOutcome.BLOCKED, "That process is not owned by an active terminal session", ErrorCategory.PROCESS_LOSS)
        val groupId = processGroup(targetPid) ?: return unavailable("The child process is no longer available")
        return runCatching {
            Os.kill(-groupId.toInt(), OsConstants.SIGTERM)
            OperationReport(OperationOutcome.COMPLETE, "Owned child process group terminated")
        }.getOrElse { OperationReport(OperationOutcome.FAILED, "The owned child process group could not be terminated", ErrorCategory.PROCESS_LOSS) }
    }

    override suspend fun closeSession(sessionId: String): OperationReport {
        val live = sessions.remove(sessionId)
        live?.let(::terminateOwnedProcessGroup)
        sessionStore.remove(sessionId)
        if (sessions.isEmpty()) appContext.stopService(Intent(appContext, TerminalForegroundService::class.java))
        return OperationReport(OperationOutcome.COMPLETE, "Terminal session closed")
    }

    override suspend fun closeAllSessions(): OperationReport {
        sessions.keys.toList().forEach { closeSession(it) }
        sessionStore.clear()
        appContext.stopService(Intent(appContext, TerminalForegroundService::class.java))
        return OperationReport(OperationOutcome.COMPLETE, "All terminal sessions closed")
    }

    fun outputSnapshot(sessionId: String): String = liveSession(sessionId)?.session?.let(::snapshot).orEmpty()

    fun shutdown() {
        explicitlyShuttingDown = true
        sessions.values.forEach(::terminateOwnedProcessGroup)
        sessions.clear()
        viewInvalidators.clear()
        // Explicit application shutdown is the terminal exit boundary. Do not
        // resurrect those live sessions on a later launch.
        sessionStore.clear()
        appContext.stopService(Intent(appContext, TerminalForegroundService::class.java))
        scope.cancel()
    }

    private fun snapshot(session: TerminalSession): String = session.getEmulator()?.getScreen()?.getTranscriptText().orEmpty()

    private fun liveSession(sessionId: String): LiveSession? {
        val live = sessions[sessionId]
        if (live == null || !live.session.isRunning()) {
            if (live != null) markUnavailable(sessionId, "The terminal process ended")
            return null
        }
        return live
    }

    private fun markUnavailable(sessionId: String, reason: String) {
        val live = sessions.remove(sessionId) ?: return
        sessionStore.remove(live.descriptor.id)
        viewInvalidators.remove(sessionId)?.invoke()
    }

    private fun processGroup(pid: Long): Long? = runCatching {
        val stat = File("/proc/$pid/stat").readText()
        val endOfCommand = stat.lastIndexOf(')')
        if (endOfCommand < 0) return@runCatching null
        stat.substring(endOfCommand + 2).trim().split(Regex("\\s+")).getOrNull(2)?.toLongOrNull()
    }.getOrNull()

    private fun terminateOwnedProcessGroup(live: LiveSession) {
        val shellPid = live.session.getPid().toLong()
        if (shellPid > 0 && processGroup(shellPid) == shellPid) {
            runCatching { Os.kill(-shellPid.toInt(), OsConstants.SIGTERM) }
        }
        live.session.finishIfRunning()
    }

    private suspend fun runCommand(command: List<String>, directory: File): OperationReport = runCatching {
        val process = ProcessBuilder(command).directory(directory).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val code = process.waitFor()
        if (code == 0) OperationReport(OperationOutcome.COMPLETE, output.ifBlank { "Package operation completed" })
        else OperationReport(OperationOutcome.FAILED, output.ifBlank { "Package operation failed" }, ErrorCategory.PACKAGE_FAILURE)
    }.getOrElse { OperationReport(OperationOutcome.FAILED, it.message ?: "Package operation failed", ErrorCategory.PACKAGE_FAILURE) }

    private suspend fun ensureDeveloperPackages(): OperationReport {
        if (developerBootstrapMarker.exists()) return OperationReport(OperationOutcome.COMPLETE, "Developer packages are ready")
        if (!packageManager.isFile || !shell.canExecute()) {
            return unavailable("The bundled terminal package manager is unavailable; continuing without automatic package setup")
        }
        val packages = listOf("git", "curl", "wget", "ca-certificates", "nodejs", "npm", "python", "openssh", "unzip", "tar", "grep", "sed", "awk")
        val base = runCommand(listOf(shell.absolutePath, packageManager.absolutePath, "install", "-y") + packages, runtimeHome)
        if (base.outcome != OperationOutcome.COMPLETE) return base
        val liveServer = if (npm.isFile) {
            runCommand(listOf(shell.absolutePath, npm.absolutePath, "install", "--global", "live-server"), runtimeHome)
        } else {
            OperationReport(OperationOutcome.FAILED, "npm was not included in the terminal runtime", ErrorCategory.PACKAGE_FAILURE)
        }
        if (liveServer.outcome != OperationOutcome.COMPLETE) return liveServer
        developerBootstrapMarker.writeText("git curl wget nodejs npm python openssh unzip tar grep sed awk live-server\n")
        return OperationReport(OperationOutcome.COMPLETE, "Core developer packages and Live Server are ready")
    }

    private fun unavailable(message: String) = OperationReport(OperationOutcome.BLOCKED, message, ErrorCategory.UNAVAILABLE_RUNTIME)

    private fun unavailableSession(id: String, name: String, reason: String): SessionDescriptor =
        SessionDescriptor(id, "global", name = name, createdAt = Instant.now(), availability = SessionAvailability.UNAVAILABLE, terminationReason = reason)

    private val sessionClient = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) {
            sessions.entries.firstOrNull { it.value.session === changedSession }?.key?.let { id ->
                viewInvalidators[id]?.invoke()
            }
        }
        override fun onTitleChanged(changedSession: TerminalSession) = Unit
        override fun onSessionFinished(finishedSession: TerminalSession) {
            val entry = sessions.entries.firstOrNull { it.value.session === finishedSession } ?: return
            val id = entry.key
            if (explicitlyShuttingDown) {
                sessions.remove(id)
                viewInvalidators.remove(id)?.invoke()
                return
            }
            sessions.remove(id)
            sessionStore.remove(id)
            viewInvalidators.remove(id)?.invoke()
            if (sessions.isEmpty()) {
                appContext.stopService(Intent(appContext, TerminalForegroundService::class.java))
            }
        }
        override fun onCopyTextToClipboard(session: TerminalSession, text: String) = Unit
        override fun onPasteTextFromClipboard(session: TerminalSession) = Unit
        override fun onBell(session: TerminalSession) = Unit
        override fun onColorsChanged(session: TerminalSession) = Unit
        override fun onTerminalCursorStateChange(state: Boolean) = Unit
        override fun getTerminalCursorStyle(): Int? = null
        override fun logError(tag: String, message: String) = Unit
        override fun logWarn(tag: String, message: String) = Unit
        override fun logInfo(tag: String, message: String) = Unit
        override fun logDebug(tag: String, message: String) = Unit
        override fun logVerbose(tag: String, message: String) = Unit
        override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) = Unit
        override fun logStackTrace(tag: String, e: Exception) = Unit
    }

    private data class LiveSession(var descriptor: SessionDescriptor, val session: TerminalSession)

    private companion object {
        const val TRANSCRIPT_ROWS = 2_000
        const val CELL_WIDTH_PX = 8
        const val CELL_HEIGHT_PX = 18
    }
}
