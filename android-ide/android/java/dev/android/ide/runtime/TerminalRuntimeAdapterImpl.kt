package dev.android.ide.runtime

import android.content.Context
import android.content.Intent
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
    private val termuxPrefix get() = bundledInstaller.prefix()
    private val shell get() = File(termuxPrefix, "bin/sh")
    private val packageManager get() = File(termuxPrefix, "bin/pkg")
    private val runtimeHome get() = bundledInstaller.home()

    override suspend fun initialize(): OperationReport {
        val bootstrap = bundledInstaller.initialize()
        if (bootstrap.outcome != OperationOutcome.COMPLETE) return bootstrap
        if (!termuxPrefix.exists() || !shell.canExecute()) {
            return OperationReport(
                OperationOutcome.BLOCKED,
                "The terminal runtime is unavailable. Initialize the approved runtime before opening a terminal.",
                ErrorCategory.UNAVAILABLE_RUNTIME,
                "Keep the project registered and retry runtime initialization when the runtime is available.",
            )
        }
        runtimeHome.mkdirs()
        sessionStore.readAll().filter { it.availability == SessionAvailability.AVAILABLE }.forEach {
            sessionStore.upsert(it.copy(availability = SessionAvailability.UNAVAILABLE, terminationReason = "Terminal session was not reconnectable after restart"))
        }
        return OperationReport(OperationOutcome.COMPLETE, "Terminal runtime is available")
    }

    override suspend fun providerRootLocation(): ProjectLocation? =
        runtimeHome.takeIf { it.exists() }?.let {
            ProjectLocation(it.canonicalPath, "Terminal runtime workspace", it.canonicalPath)
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
            explanation = if (available) null else "The approved terminal runtime files were not found",
        )
    }

    override suspend fun installedPackages(): List<RuntimePackage> {
        if (!packageManager.canExecute()) return emptyList()
        return runCatching {
            ProcessBuilder(packageManager.absolutePath, "list-installed")
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
        if (!packageManager.canExecute()) return unavailable("The terminal package manager is unavailable")
        return runCommand(listOf(packageManager.absolutePath, "install", "-y") + packages, runtimeHome)
    }

    override suspend fun inspectProjectAccess(project: ProjectIdentity): TerminalProjectAccess {
        if (capabilities().availability != RuntimeAvailability.AVAILABLE) return TerminalProjectAccess(false, "The terminal runtime is unavailable")
        val path = project.location.userVisiblePath ?: return TerminalProjectAccess(false, "This project has no terminal working location")
        val directory = File(path)
        return TerminalProjectAccess(directory.isDirectory, "The selected project location is not an accessible folder".takeUnless { directory.isDirectory })
    }

    override suspend fun workingDirectory(project: ProjectIdentity): String? = project.location.userVisiblePath?.takeIf { File(it).isDirectory }

    override suspend fun createSession(workingDirectory: String?, name: String): SessionDescriptor {
        val id = UUID.randomUUID().toString()
        val sessionName = name.trim().ifBlank { "Terminal" }
        if (!shell.canExecute()) return unavailableSession(id, sessionName, "Terminal runtime unavailable")
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
            sessions[id] = LiveSession(descriptor, session)
            sessionStore.upsert(descriptor)
            ContextCompat.startForegroundService(appContext, Intent(appContext, TerminalForegroundService::class.java))
            descriptor
        }.getOrElse { error -> unavailableSession(id, sessionName, error.message ?: "Unable to start terminal session") }
    }

    /** Returns the live upstream session for the Android View bridge. */
    fun terminalSession(sessionId: String): TerminalSession? = sessions[sessionId]?.session

    fun bindTerminalView(sessionId: String, invalidate: () -> Unit) {
        viewInvalidators[sessionId] = invalidate
    }

    fun unbindTerminalView(sessionId: String) {
        viewInvalidators.remove(sessionId)
    }

    override suspend fun listSessions(): List<SessionDescriptor> =
        (sessionStore.readAll() + sessions.values.map { it.descriptor }).distinctBy { it.id }.sortedBy { it.createdAt }

    override suspend fun sendInput(sessionId: String, input: ByteArray): OperationReport {
        val session = sessions[sessionId]?.session ?: return unavailable("The terminal session is unavailable")
        return runCatching {
            session.write(input, 0, input.size)
            OperationReport(OperationOutcome.COMPLETE, "Input sent")
        }.getOrElse { OperationReport(OperationOutcome.FAILED, "Could not send terminal input", ErrorCategory.PROCESS_LOSS) }
    }

    override suspend fun readOutput(sessionId: String): ByteArray? = sessions[sessionId]?.session?.let { snapshot(it).toByteArray(Charsets.UTF_8) }

    override suspend fun resize(sessionId: String, columns: Int, rows: Int): OperationReport {
        val session = sessions[sessionId]?.session ?: return unavailable("The terminal session is unavailable")
        if (columns <= 0 || rows <= 0) return OperationReport(OperationOutcome.BLOCKED, "The terminal size is invalid", ErrorCategory.PROCESS_LOSS)
        return runCatching {
            session.updateSize(columns, rows, CELL_WIDTH_PX, CELL_HEIGHT_PX)
            OperationReport(OperationOutcome.COMPLETE, "Terminal size updated")
        }.getOrElse { OperationReport(OperationOutcome.FAILED, "Could not resize the terminal", ErrorCategory.PROCESS_LOSS) }
    }

    override suspend fun interrupt(sessionId: String): OperationReport {
        val session = sessions[sessionId]?.session ?: return unavailable("The terminal session is unavailable")
        session.write(byteArrayOf(3), 0, 1)
        return OperationReport(OperationOutcome.COMPLETE, "Terminal session interrupted")
    }

    override suspend fun listChildProcesses(sessionId: String): List<ChildProcessDescriptor> {
        val live = sessions[sessionId] ?: return emptyList()
        return runCatching {
            ProcessBuilder("/system/bin/ps", "-o", "PID,PPID,ARGS").redirectErrorStream(true).start()
                .inputStream.bufferedReader().readText().lineSequence().drop(1).mapNotNull { line ->
                    val parts = line.trim().split(Regex("\\s+"), limit = 3)
                    val pid = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
                    val ppid = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
                    if (ppid != live.session.getPid().toLong()) return@mapNotNull null
                    ChildProcessDescriptor(pid.toString(), sessionId, parts.getOrNull(2).orEmpty(), pid, live.descriptor.workingDirectory, Instant.now(), SessionAvailability.AVAILABLE)
                }.toList()
        }.getOrDefault(emptyList())
    }

    override suspend fun terminateChildProcess(processId: String): OperationReport = runCatching {
        val process = ProcessBuilder("/system/bin/kill", processId).start()
        if (process.waitFor() == 0) OperationReport(OperationOutcome.COMPLETE, "Child process terminated")
        else OperationReport(OperationOutcome.FAILED, "The child process could not be terminated", ErrorCategory.PROCESS_LOSS)
    }.getOrElse { OperationReport(OperationOutcome.FAILED, "The child process could not be terminated", ErrorCategory.PROCESS_LOSS) }

    override suspend fun closeSession(sessionId: String): OperationReport {
        val live = sessions.remove(sessionId)
        live?.session?.finishIfRunning()
        sessionStore.readAll().firstOrNull { it.id == sessionId }?.let {
            sessionStore.upsert(it.copy(availability = SessionAvailability.EXPLICITLY_CLOSED, terminationReason = "Closed by user"))
        }
        if (sessions.isEmpty()) appContext.stopService(Intent(appContext, TerminalForegroundService::class.java))
        return OperationReport(OperationOutcome.COMPLETE, "Terminal session closed")
    }

    override suspend fun closeAllSessions(): OperationReport {
        sessions.keys.toList().forEach { closeSession(it) }
        appContext.stopService(Intent(appContext, TerminalForegroundService::class.java))
        return OperationReport(OperationOutcome.COMPLETE, "All terminal sessions closed")
    }

    fun outputSnapshot(sessionId: String): String = sessions[sessionId]?.session?.let(::snapshot).orEmpty()

    fun shutdown() {
        sessions.values.forEach {
            it.session.finishIfRunning()
            sessionStore.upsert(it.descriptor.copy(availability = SessionAvailability.INVALIDATED, terminationReason = "Terminal runtime boundary stopped"))
        }
        sessions.clear()
        viewInvalidators.clear()
        scope.cancel()
    }

    private fun snapshot(session: TerminalSession): String = session.getEmulator()?.getScreen()?.getTranscriptText().orEmpty()

    private suspend fun runCommand(command: List<String>, directory: File): OperationReport = runCatching {
        val process = ProcessBuilder(command).directory(directory).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val code = process.waitFor()
        if (code == 0) OperationReport(OperationOutcome.COMPLETE, output.ifBlank { "Package operation completed" })
        else OperationReport(OperationOutcome.FAILED, output.ifBlank { "Package operation failed" }, ErrorCategory.PACKAGE_FAILURE)
    }.getOrElse { OperationReport(OperationOutcome.FAILED, it.message ?: "Package operation failed", ErrorCategory.PACKAGE_FAILURE) }

    private fun unavailable(message: String) = OperationReport(OperationOutcome.BLOCKED, message, ErrorCategory.UNAVAILABLE_RUNTIME)

    private fun unavailableSession(id: String, name: String, reason: String): SessionDescriptor =
        SessionDescriptor(id, "global", name = name, createdAt = Instant.now(), availability = SessionAvailability.UNAVAILABLE, terminationReason = reason).also(sessionStore::upsert)

    private val sessionClient = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) {
            sessions.entries.firstOrNull { it.value.session === changedSession }?.key?.let { id ->
                viewInvalidators[id]?.invoke()
            }
        }
        override fun onTitleChanged(changedSession: TerminalSession) = Unit
        override fun onSessionFinished(finishedSession: TerminalSession) = Unit
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
