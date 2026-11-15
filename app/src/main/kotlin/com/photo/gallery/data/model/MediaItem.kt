package com.photo.gallery.data.model

import android.net.Uri

/**
 * Model representing a photo or video item from MediaStore or the Vault.
 */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val path: String,
    val displayName: String,
    val albumName: String,
    val dateAdded: Long,
    val size: Long,
    val mimeType: String,
    val width: Int = 0,
    val height: Int = 0,
    val isVideo: Boolean = false,
    val durationMs: Long = 0L,
    val isLocked: Boolean = false,
    val isHidden: Boolean = false
) {
    val formattedSize: String
        get() {
            if (size <= 0) return "0 B"
            val kb = size / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> "%.2f GB".format(gb)
                mb >= 1.0 -> "%.1f MB".format(mb)
                kb >= 1.0 -> "%.1f KB".format(kb)
                else -> "$size B"
            }
        }
}

data class AlbumItem(
    val name: String,
    val coverUri: Uri?,
    val itemCount: Int,
    val isLocked: Boolean = false
)
