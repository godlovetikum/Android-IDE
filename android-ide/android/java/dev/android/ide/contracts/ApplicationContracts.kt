// Shared application and project contracts keep ownership stable while providers change.
// Provider classes stay out of these types so higher-level services cannot acquire a
// second source of truth through an implementation detail.
package dev.android.ide.contracts

import java.time.Instant

enum class CapabilityState {
    NOT_YET_CHECKED,
    SUPPORTED,
    UNSUPPORTED,
    UNAVAILABLE,
    PERMISSION_LOST,
}

data class ProjectLocation(
    val stableId: String,
    val displayLabel: String,
    val userVisiblePath: String? = null,
    val capabilityState: CapabilityState = CapabilityState.NOT_YET_CHECKED,
    val capabilityExplanation: String? = null,
)

/** Capabilities owned by the project-storage provider; terminal/Git access is reported elsewhere.
 * SUPPORTED means project files are readable and writable. Other mutations are independent flags.
 */
data class ProjectStorageCapabilities(
    val state: CapabilityState,
    val readable: Boolean,
    val writable: Boolean,
    val canCreate: Boolean,
    val canRename: Boolean,
    val canDelete: Boolean,
    val canObserveChanges: Boolean,
    val explanation: String? = null,
)

data class ProjectIdentity(
    val id: String,
    val name: String,
    val description: String,
    val location: ProjectLocation,
    val registeredAt: Instant,
    val lastOpenedAt: Instant? = null,
)

enum class Surface {
    HOME,
    DIAGNOSTICS,
    PROJECTS,
    PROJECT_DETAILS,
    EDITOR,
    TERMINAL,
    BROWSER,
    GIT,
    EXTENSIONS,
    SETTINGS,
}

data class NavigationState(
    val currentSurface: Surface = Surface.HOME,
    val selectedProjectId: String? = null,
    val previousSurface: Surface? = null,
)

enum class SessionAvailability {
    AVAILABLE,
    UNAVAILABLE,
    EXPLICITLY_CLOSED,
    INVALIDATED,
}

enum class RuntimeAvailability {
    NOT_INITIALIZED,
    INITIALIZING,
    AVAILABLE,
    UNAVAILABLE,
    INVALIDATED,
}

data class RuntimeCapabilities(
    val availability: RuntimeAvailability,
    val architecture: String? = null,
    val shellAvailable: Boolean = false,
    val ptyAvailable: Boolean = false,
    val packageManagerAvailable: Boolean = false,
    val executableFilesSupported: Boolean = false,
    val symlinksSupported: Boolean = false,
    val explanation: String? = null,
)

data class RuntimePackage(
    val name: String,
    val version: String? = null,
    val installed: Boolean = false,
)

data class ChildProcessDescriptor(
    val id: String,
    val sessionId: String,
    val command: String,
    val processId: Long? = null,
    val workingDirectory: String? = null,
    val startedAt: Instant,
    val availability: SessionAvailability,
    val terminationReason: String? = null,
)

data class SessionDescriptor(
    val id: String,
    val ownerScope: String,
    val backendId: String? = null,
    val name: String = "Untitled session",
    val workingDirectory: String? = null,
    val createdAt: Instant,
    val availability: SessionAvailability,
    val terminationReason: String? = null,
)

data class EditorTabIdentity(
    val documentUri: String,
    val projectId: String,
    val isPinned: Boolean = false,
    val isDirty: Boolean = false,
)

data class BrowserTabIdentity(
    val id: String,
    val url: String?,
    val projectId: String? = null,
)

enum class OperationOutcome {
    COMPLETE,
    PARTIAL,
    BLOCKED,
    INTERRUPTED,
    FAILED,
    CANCELLED,
}

enum class ErrorCategory {
    PERMISSION_LOST,
    UNSUPPORTED_PROVIDER_CAPABILITY,
    DESTINATION_CONFLICT,
    INVALID_ARCHIVE,
    UNAVAILABLE_RUNTIME,
    PACKAGE_FAILURE,
    PROCESS_LOSS,
    MALFORMED_METADATA,
    EXTERNAL_FILE_CHANGE,
    CREDENTIAL_FAILURE,
    USER_CANCELLED,
}

data class OperationReport(
    val outcome: OperationOutcome,
    val message: String,
    val errorCategory: ErrorCategory? = null,
    val affectedIds: List<String> = emptyList(),
    val recoveryHint: String? = null,
)

data class MutationPreflight(
    val allowed: Boolean,
    val message: String,
    val errorCategory: ErrorCategory? = null,
)

enum class ProjectMutationIntent { CREATE, WRITE, DELETE }

data class ProjectMutationRequest(
    val project: ProjectIdentity,
    val path: ProjectRelativePath,
    val intent: ProjectMutationIntent,
    val directory: Boolean = false,
)

enum class LocationOperationKind {
    COPY,
    MOVE,
    IMPORT,
    EXPORT,
    RELOCATE,
    DELETE,
}

data class LocationOperationRequest(
    val kind: LocationOperationKind,
    val source: ProjectLocation?,
    val destination: ProjectLocation?,
    val projectId: String,
)

/** Project-relative path used by adapters; provider-specific URIs stay behind them. */
typealias ProjectRelativePath = String

/** Stable provider location identity used only for same-root/ancestry comparisons. */
typealias ProjectLocationId = String

interface ProjectStorageAdapter {
    suspend fun inspectProjectStorage(location: ProjectLocation): ProjectStorageCapabilities
    /** Returns null when the provider cannot safely inspect either location. */
    suspend fun isSameOrDescendant(root: ProjectLocationId, candidate: ProjectLocationId): Boolean?
    /** Null means an inspection/permission failure; an empty list means a verified empty directory. */
    suspend fun list(project: ProjectIdentity, path: ProjectRelativePath): List<ProjectRelativePath>?
    /** Null means unavailable/inaccessible; a zero-byte array is a valid empty document. */
    suspend fun read(project: ProjectIdentity, path: ProjectRelativePath): ByteArray?
    suspend fun write(project: ProjectIdentity, path: ProjectRelativePath, content: ByteArray): OperationReport
    suspend fun preflightMutation(request: ProjectMutationRequest): MutationPreflight
    suspend fun preflightLocationOperation(request: LocationOperationRequest): MutationPreflight
    suspend fun executeLocationOperation(request: LocationOperationRequest): OperationReport
    suspend fun verifyLocationOperation(request: LocationOperationRequest): OperationReport
    /** Null means observation is unsupported or could not be established. Close to release observers. */
    suspend fun observeChanges(project: ProjectIdentity, listener: (ProjectRelativePath) -> Unit): AutoCloseable?
}

interface ProjectRegistryAdapter {
    suspend fun listRegistered(): List<ProjectIdentity>
    /** Checks project-root containment only; domain-specific runtime access is deliberately excluded. */
    suspend fun preflightRegistration(project: ProjectIdentity): MutationPreflight
    suspend fun register(project: ProjectIdentity): OperationReport
    suspend fun markUnavailable(
        projectId: String,
        reason: String,
        capabilityState: CapabilityState = CapabilityState.UNAVAILABLE,
    ): OperationReport
    suspend fun remove(projectId: String): OperationReport
}

interface ProjectMetadataAdapter {
    suspend fun ensurePortableState(project: ProjectIdentity): OperationReport
    suspend fun readIdentity(location: ProjectLocation): ProjectIdentity?
    suspend fun writeIdentity(project: ProjectIdentity): OperationReport
    suspend fun readWorkspaceDescriptor(projectId: String): ByteArray?
    suspend fun writeWorkspaceDescriptor(projectId: String, descriptor: ByteArray): OperationReport
}

interface RuntimeWorkspaceAdapter {
    suspend fun initialize(): OperationReport
    /** Runtime-owned storage is exposed as an ordinary provider location, not a project class. */
    suspend fun providerRootLocation(): ProjectLocation?
    suspend fun capabilities(): RuntimeCapabilities
    suspend fun installedPackages(): List<RuntimePackage>
    suspend fun installPackages(packages: List<String>): OperationReport
}

interface TerminalRuntimeAdapter {
    /** Per-project terminal suitability; failure never affects project registration or editor access. */
    suspend fun inspectProjectAccess(project: ProjectIdentity): TerminalProjectAccess
    suspend fun workingDirectory(project: ProjectIdentity): String?
    suspend fun createSession(workingDirectory: String?, name: String = ""): SessionDescriptor
    suspend fun listSessions(): List<SessionDescriptor>
    suspend fun renameSession(sessionId: String, name: String): OperationReport
    suspend fun capabilities(): RuntimeCapabilities
    suspend fun sendInput(sessionId: String, input: ByteArray): OperationReport
    suspend fun readOutput(sessionId: String): ByteArray?
    suspend fun resize(sessionId: String, columns: Int, rows: Int): OperationReport
    suspend fun interrupt(sessionId: String): OperationReport
    suspend fun listChildProcesses(sessionId: String): List<ChildProcessDescriptor>
    suspend fun terminateChildProcess(processId: String): OperationReport
    suspend fun closeSession(sessionId: String): OperationReport
    suspend fun closeAllSessions(): OperationReport
}

data class TerminalProjectAccess(
    val available: Boolean,
    val explanation: String? = null,
)

interface EditorDocumentAdapter {
    suspend fun load(project: ProjectIdentity, path: ProjectRelativePath): ByteArray
    suspend fun save(project: ProjectIdentity, path: ProjectRelativePath, content: ByteArray): OperationReport
    suspend fun reportExternalChange(projectId: String, path: ProjectRelativePath): OperationReport
}

interface GitAdapter {
    /** Git suitability is checked for the selected project and never gates registration or file editing. */
    suspend fun inspectProjectAccess(project: ProjectIdentity): GitProjectAccess
    suspend fun status(project: ProjectIdentity): Result<String>
    suspend fun execute(project: ProjectIdentity, arguments: List<String>): OperationReport
}

data class GitProjectAccess(
    val available: Boolean,
    val explanation: String? = null,
)

interface BrowserPreviewAdapter {
    suspend fun listTabs(): List<BrowserTabIdentity>
    suspend fun closeTab(tabId: String): OperationReport
}

interface CredentialVaultAdapter {
    suspend fun listCredentialIds(): List<String>
    suspend fun revoke(credentialId: String): OperationReport
    fun redact(value: String): String
}

interface LifecycleCoordinator {
    suspend fun restore(): OperationReport
    suspend fun onForeground(): OperationReport
    suspend fun onBackground(): OperationReport
    suspend fun onExplicitExit(): OperationReport
    suspend fun onRuntimeServiceStopped(reason: String): OperationReport = OperationReport(
        outcome = OperationOutcome.INTERRUPTED,
        message = "Runtime service stopped: $reason",
        errorCategory = ErrorCategory.PROCESS_LOSS,
    )
    suspend fun onProcessRecreated(): OperationReport = OperationReport(
        outcome = OperationOutcome.COMPLETE,
        message = "Application process recreated",
    )
}

/** Facts shared across domains. Event payloads must never contain secrets. */
sealed interface ApplicationEvent {
    val entityId: String

    data class ProjectRegistered(override val entityId: String) : ApplicationEvent
    data class ProjectUnavailable(override val entityId: String, val reason: String) : ApplicationEvent
    data class StoragePermissionLost(override val entityId: String) : ApplicationEvent
    data class ProjectFileChanged(override val entityId: String, val path: ProjectRelativePath) : ApplicationEvent
    data class SessionAvailabilityChanged(
        override val entityId: String,
        val availability: SessionAvailability,
    ) : ApplicationEvent
    data class RuntimeAvailabilityChanged(
        override val entityId: String,
        val availability: RuntimeAvailability,
        val explanation: String? = null,
    ) : ApplicationEvent
    data class PackageOperationCompleted(
        override val entityId: String,
        val packages: List<String>,
        val outcome: OperationOutcome,
    ) : ApplicationEvent
    data class ChildProcessStarted(
        override val entityId: String,
        val sessionId: String,
    ) : ApplicationEvent
    data class ChildProcessExited(override val entityId: String, val exitCode: Int?) : ApplicationEvent
    data class BrowserTabUnavailable(override val entityId: String) : ApplicationEvent
    data class GitRepositoryRefreshed(override val entityId: String) : ApplicationEvent
    data class LifecycleChanged(override val entityId: String, val state: String) : ApplicationEvent
}

object ApplicationIdentity {
    const val TARGET_APPLICATION_ID = "dev.android.ide"
    const val TARGET_METADATA_DIRECTORY = ".dev-android-ide"
    const val LEGACY_APPLICATION_ID = "dev.androidide"
    const val LEGACY_METADATA_DIRECTORY = ".androidide"
}
