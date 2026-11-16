package com.photo.gallery.data.local.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing private photos safely stored in the app's Hidden Vault.
 * Files are encrypted at rest with Tink Streaming AEAD (AesGcmHkdfStreaming).
 *
 * Indices:
 * - unique index on `vaultPath` prevents duplicate entries.
 */
@Entity(
    tableName = "hidden_media",
    indices = [Index(value = ["vaultPath"], unique = true)]
)
data class HiddenMediaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val originalPath: String,
    val vaultPath: String,
    val displayName: String,
    val mimeType: String,
    val size: Long,
    val dateAdded: Long,
    val dateHidden: Long = System.currentTimeMillis(),
    val isEncrypted: Boolean = true,
    val relativeFolder: String? = null,
    val dateTaken: Long = 0L
)
