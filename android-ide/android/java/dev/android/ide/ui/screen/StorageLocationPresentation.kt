package dev.android.ide.ui.screen

import android.net.Uri
import android.provider.DocumentsContract

/**
 * Converts provider-specific identifiers into a concise user-facing path.
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
                    ?: return@runCatching ""
                Uri.decode(decodedId)
                    .substringAfter(':', Uri.decode(decodedId))
                    .replace(":", "/")
                    .trim('/')
            }
            value.startsWith("file://", ignoreCase = true) -> Uri.decode(Uri.parse(value).path.orEmpty()).ifBlank { "Device file" }
            else -> Uri.decode(value).replace("%2F", "/", ignoreCase = true)
        }
    }.getOrElse { "Location unavailable" }.ifBlank { "Location unavailable" }
}
