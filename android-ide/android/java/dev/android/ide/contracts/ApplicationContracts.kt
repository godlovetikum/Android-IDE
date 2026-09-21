// Shared application and project contracts keep ownership stable while providers change.
// Provider classes stay out of these types so higher-level services cannot acquire a
// second source of truth through an implementation detail.
package dev.android.ide.contracts

import java.time.Instant

/** The two supported authoritative project-location classes. */
enum class ProjectLocationKind {
    USER_VISIBLE_LOCAL,
    PRIVATE_DEVELOPMENT_WORKSPACE,
}

enum class CapabilityState {
    NOT_YET_CHECKED,
    SUPPORTED,
    UNSUPPORTED,
    UNAVAILABLE,
    PERMISSION_LOST,
}

data class ProjectLocation(
    val kind: ProjectLocationKind,
    val stableId: String,
    val displayLabel: String,
    val userVisiblePath: String? = null,
    val capabilityState: CapabilityState = CapabilityState.NOT_YET_CHECKED,
)

data class LocationCapabilities(
    val state: CapabilityState,
    val readable: Boolean,
    val writable: Boolean,
    val canCreate: Boolean,
    val canRename: Boolean,
    val canDelete: Boolean,
    val canExecute: Boolean,
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

data class SessionDescriptor(
    val id: String,
    val ownerScope: String,
    val backendId: String? = null,
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

interface ProjectStorageAdapter {
    suspend fun inspectCapabilities(location: ProjectLocation): LocationCapabilities
    suspend fun list(path: ProjectRelativePath): List<ProjectRelativePath>
    suspend fun read(path: ProjectRelativePath): ByteArray
    suspend fun write(path: ProjectRelativePath, content: ByteArray): OperationReport
    suspend fun preflightMutation(path: ProjectRelativePath): MutationPreflight
    suspend fun preflightLocationOperation(request: LocationOperationRequest): MutationPreflight
    suspend fun executeLocationOperation(request: LocationOperationRequest): OperationReport
    suspend fun verifyLocationOperation(request: LocationOperationRequest): OperationReport
    suspend fun observeChanges(listener: (ProjectRelativePath) -> Unit)
}

interface ProjectRegistryAdapter {
    suspend fun listRegistered(): List<ProjectIdentity>
    suspend fun register(project: ProjectIdentity): OperationReport
    suspend fun markUnavailable(projectId: String, reason: String): OperationReport
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
    suspend fun rootIdentity(): ProjectLocation?
    suspend fun workingDirectory(project: ProjectIdentity): String?
}

interface TerminalRuntimeAdapter {
    suspend fun createSession(workingDirectory: String?): SessionDescriptor
    suspend fun sendInput(sessionId: String, input: ByteArray): OperationReport
    suspend fun resize(sessionId: String, columns: Int, rows: Int): OperationReport
    suspend fun closeSession(sessionId: String): OperationReport
    suspend fun closeAllSessions(): OperationReport
}

interface EditorDocumentAdapter {
    suspend fun load(project: ProjectIdentity, path: ProjectRelativePath): ByteArray
    suspend fun save(project: ProjectIdentity, path: ProjectRelativePath, content: ByteArray): OperationReport
    suspend fun reportExternalChange(projectId: String, path: ProjectRelativePath): OperationReport
}

interface GitAdapter {
    suspend fun status(project: ProjectIdentity): Result<String>
    suspend fun execute(project: ProjectIdentity, arguments: List<String>): OperationReport
}

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
