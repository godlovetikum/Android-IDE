package dev.android.ide.ui.screen

import android.net.Uri
import android.provider.DocumentsContract

/**
 * Converts provider-specific encoded identifiers into a stable user-facing label.
 * The raw URI remains available to storage operations but is never used as visible copy.
 */
fun humanReadableStorageLocation(raw: String?): String {
    val value = raw?.trim().orEmpty()
    if (value.isBlank()) return "Location unavailable"
    return runCatching {
        when {
            value.startsWith("content://", ignoreCase = true) -> {
                val uri = Uri.parse(value)
                val decodedId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
                    ?: uri.lastPathSegment
                    ?: "Selected folder"
                val readableId = Uri.decode(decodedId).replace(':', '/')
                "Selected storage / $readableId"
            }
            value.startsWith("file://", ignoreCase = true) -> Uri.decode(Uri.parse(value).path.orEmpty()).ifBlank { "Device file" }
            else -> Uri.decode(value).replace("%2F", "/", ignoreCase = true)
        }
    }.getOrElse { "Selected storage location" }
}
