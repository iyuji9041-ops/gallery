package com.photo.gallery.data.local.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing private photos safely stored in the app's Hidden Vault.
 * These items reside in a protected directory with a .nomedia marker, excluding them
 * completely from Android MediaStore scans and public gallery views.
 *
 * UNIQUE INDEX ON vaultPath (bug fix): VaultManager.moveToVault() and
 * VaultMonitorService's FileObserver can both try to insert a row for the SAME
 * physical file at nearly the same instant - the service watches the vault
 * directory for "external" changes and can't always tell its own app's write
 * apart from one made by another app in the small window before the app's own
 * DB insert commits. Without this constraint that race produced two rows (two
 * vault items) for one real file. With it, the second insert is silently
 * ignored (see SecurityDao's OnConflictStrategy.IGNORE) instead of creating a
 * duplicate.
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
    val dateHidden: Long = System.currentTimeMillis()
)
