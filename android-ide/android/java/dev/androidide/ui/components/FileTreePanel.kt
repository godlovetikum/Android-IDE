// android-ide/android/java/dev/androidide/ui/components/FileTreePanel.kt
//
// Sidebar file tree — shows the SAF-backed project directory tree.
//
// Migration note (2026-06-12):
//   Replaces ui/components/file_tree.slint.
//   LazyColumn renders the flat + indented node list.
//   Directory nodes are expandable via [onDirToggle].

package dev.androidide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.androidide.ui.theme.*
import dev.androidide.viewmodel.model.FileNode

@Composable
fun FileTreePanel(
    nodes: List<FileNode>,
    onFileClick: (String) -> Unit,
    onDirToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (nodes.isEmpty()) {
        // Empty state
        Box(
            modifier          = modifier.padding(16.dp),
            contentAlignment  = Alignment.TopCenter,
        ) {
            Text(
                text  = "No project open.\nUse the folder icon to open a project.",
                style = MaterialTheme.typography.bodySmall,
                color = IdeTextDisabled,
            )
        }
    } else {
        LazyColumn(modifier = modifier.padding(vertical = 4.dp)) {
            items(
                items = flattenTree(nodes),
                key   = { (node, _) -> node.documentUri },
            ) { (node, depth) ->
                FileTreeRow(
                    node       = node,
                    depth      = depth,
                    onClick    = {
                        if (node.isDirectory) onDirToggle(node.documentUri)
                        else onFileClick(node.documentUri)
                    },
                )
            }
        }
    }
}

// ── Row ────────────────────────────────────────────────────────────────────────

@Composable
private fun FileTreeRow(
    node: FileNode,
    depth: Int,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start  = (12 + depth * 14).dp,
                end    = 8.dp,
                top    = 2.dp,
                bottom = 2.dp,
            ),
    ) {
        // Expand/collapse chevron for directories; spacer for files
        if (node.isDirectory) {
            Icon(
                imageVector        = if (node.isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = if (node.isExpanded) "Collapse" else "Expand",
                tint               = IdeTextSecondary,
                modifier           = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(2.dp))
        } else {
            Spacer(Modifier.width(16.dp))
        }

        // File/directory icon
        Icon(
            imageVector = when {
                node.isDirectory && node.isExpanded -> Icons.Default.FolderOpen
                node.isDirectory                    -> Icons.Default.Folder
                else                                -> Icons.Default.InsertDriveFile
            },
            contentDescription = null,
            tint               = if (node.isDirectory) IdeAccentLight else IdeTextSecondary,
            modifier           = Modifier.size(14.dp),
        )

        Spacer(Modifier.width(4.dp))

        // Display name
        Text(
            text     = node.displayName,
            style    = MaterialTheme.typography.bodyMedium,
            color    = IdeTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Tree flattening ────────────────────────────────────────────────────────────

/**
 * Flatten the recursive [FileNode] tree into a list of (node, depth) pairs for
 * [LazyColumn]. Expanded directories include their children inline.
 */
private fun flattenTree(
    nodes: List<FileNode>,
    depth: Int = 0,
): List<Pair<FileNode, Int>> {
    val result = mutableListOf<Pair<FileNode, Int>>()
    for (node in nodes) {
        result += node to depth
        if (node.isDirectory && node.isExpanded) {
            result += flattenTree(node.children, depth + 1)
        }
    }
    return result
}
