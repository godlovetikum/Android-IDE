// android-ide/android/java/dev/androidide/data/model/ProjectDetails.kt
//
// Computed project metadata shown by the project-management screen.
// Statistics are calculated from the current storage provider rather than
// persisted so details remain accurate after external file changes.

package dev.androidide.data.model

data class ProjectDetails(
    val project: Project,
    val creationTimeMs: Long?,
    val lastModifiedTimeMs: Long?,
    val storageProvider: String,
    val storagePath: String,
    val fileCount: Int,
    val folderCount: Int,
    val totalBytes: Long,
    val languageBytes: Map<String, Long> = emptyMap(),
    val git: GitDetails? = null,
)

data class GitRemote(
    val name: String,
    val url: String,
)

data class GitDetails(
    val currentBranch: String?,
    val branches: List<String>,
    val remotes: List<GitRemote>,
    val headCommit: String?,
    val latestCommitMessage: String?,
    val latestCommitTimeMs: Long?,
)