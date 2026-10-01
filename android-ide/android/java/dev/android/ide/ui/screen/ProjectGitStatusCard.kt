package dev.android.ide.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.android.ide.data.model.GitDetails
import java.net.URI

/** Shared, read-only repository status shown in Git and Project Details. */
@Composable
fun ProjectGitStatusCard(
    hasGit: Boolean?,
    git: GitDetails?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Git", style = MaterialTheme.typography.titleMedium)
            Text(
                when (hasGit) {
                    true -> "Git repository detected"
                    false -> "No Git repository detected"
                    null -> if (git != null) "Git repository detected" else "Repository status is not available yet"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (hasGit != false && git != null) {
                GitStatusLine("Current checkout", git.currentBranch ?: "Detached or unavailable")
                git.latestCommitMessage?.let { GitStatusLine("Latest commit", it) }
                if (git.branches.isNotEmpty()) {
                    GitStatusLine("Branches (${git.branches.size})", git.branches.take(6).joinToString(", "))
                    if (git.branches.size > 6) {
                        Text("And ${git.branches.size - 6} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (git.remotes.isEmpty()) {
                    GitStatusLine("Remotes", "None configured")
                } else {
                    Text("Remotes", style = MaterialTheme.typography.labelLarge)
                    git.remotes.take(5).forEach { remote ->
                        GitStatusLine(remote.name, safeGitRemoteUrl(remote.url))
                    }
                    if (git.remotes.size > 5) {
                        Text("And ${git.remotes.size - 5} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (hasGit == true) {
                Text(
                    "Repository details are still loading or unavailable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GitStatusLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/** Avoid exposing embedded credentials, URL queries, or fragments in status surfaces. */
internal fun safeGitRemoteUrl(rawUrl: String): String {
    val withoutQueryOrFragment = rawUrl.trim().substringBefore('?').substringBefore('#')
    val withoutScpPassword = withoutQueryOrFragment.replace(Regex("^([^/@:]+):[^/@]+@"), "\$1@")
    val uri = runCatching { URI(withoutScpPassword) }.getOrNull()
        ?: return withoutScpPassword.replace(Regex("(?<=://)[^/@]+@"), "")
    if (uri.scheme == null || uri.host == null) {
        return withoutScpPassword.replace(Regex("(?<=://)[^/@]+@"), "")
    }
    val safeUserInfo = uri.userInfo?.takeIf { uri.scheme.equals("ssh", ignoreCase = true) && it == "git" }
    return runCatching {
        URI(uri.scheme, safeUserInfo, uri.host, uri.port, uri.path, null, null).toASCIIString()
    }.getOrDefault(withoutScpPassword.replace(Regex("(?<=://)[^/@]+@"), ""))
}
