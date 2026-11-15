package com.photo.gallery.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing media items or albums with Deletion Lock applied.
 * We store the file path and optional content hash because MediaStore IDs
 * can change or be re-indexed across Android storage scans.
 */
@Entity(tableName = "locked_media")
data class LockedMediaEntity(
    @PrimaryKey
    val mediaPath: String,
    val mediaUriString: String,
    val contentHash: String? = null,
    val albumName: String? = null,
    val isLocked: Boolean = true,
    val lockDate: Long = System.currentTimeMillis(),
    val isAlbumLocked: Boolean = false
)
