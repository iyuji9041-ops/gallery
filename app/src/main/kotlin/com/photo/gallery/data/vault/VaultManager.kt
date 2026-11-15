package com.photo.gallery.data.vault

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.photo.gallery.data.local.dao.SecurityDao
import com.photo.gallery.data.local.model.HiddenMediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

/**
 * Manages the Hidden Vault file storage.
 * Files are isolated in the app's internal storage directory with a .nomedia
 * file, ensuring complete privacy from device media scanners and external apps.
 */
class VaultManager(
    private val context: Context,
    private val securityDao: SecurityDao
) {
    val vaultDirectory: File
        get() {
            val dir = File(context.filesDir, "vault")
            ensureNoMedia(dir)
            return dir
        }

    val externalVaultDirectory: File?
        get() {
            return try {
                val extDir = context.getExternalFilesDir("vault")
                if (extDir != null) {
                    ensureNoMedia(extDir)
                }
                extDir
            } catch (e: Exception) {
                null
            }
        }

    fun getAllVaultDirectories(): List<File> {
        val dirs = mutableListOf(vaultDirectory)
        externalVaultDirectory?.let { dirs.add(it) }
        return dirs
    }

    companion object {
        fun ensureNoMedia(dir: File): Boolean {
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val noMedia = File(dir, ".nomedia")
            if (!noMedia.exists()) {
                try {
                    return noMedia.createNewFile()
                } catch (e: Exception) {
                    return false
                }
            }
            return true
        }
    }

    suspend fun moveToVault(
        sourceUri: Uri,
        originalPath: String,
        displayName: String,
        mimeType: String,
        size: Long,
        dateAdded: Long
    ): Result<HiddenMediaEntity> = withContext(Dispatchers.IO) {
        try {
            val extension = displayName.substringAfterLast(".", "jpg")
            val secureFileName = "vault_${UUID.randomUUID()}.$extension"
            val targetFile = File(vaultDirectory, secureFileName)

            // Copy content into private vault
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Cannot open source file"))

            // Save in database
            val entity = HiddenMediaEntity(
                originalPath = originalPath,
                vaultPath = targetFile.absolutePath,
                displayName = displayName,
                mimeType = mimeType,
                size = if (size > 0) size else targetFile.length(),
                dateAdded = dateAdded,
                dateHidden = System.currentTimeMillis()
            )
            val insertedId = securityDao.insertHiddenMedia(entity)
            val finalEntity = entity.copy(id = insertedId)

            // Try to delete original from MediaStore
            try {
                context.contentResolver.delete(sourceUri, null, null)
            } catch (e: Exception) {
                // If scoped storage requires IntentSender, caller handles it
            }

            Result.success(finalEntity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreFromVault(hiddenItem: HiddenMediaEntity): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val vaultFile = File(hiddenItem.vaultPath)
            if (!vaultFile.exists()) {
                return@withContext Result.failure(Exception("Vault file does not exist"))
            }

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, hiddenItem.displayName)
                put(MediaStore.Images.Media.MIME_TYPE, hiddenItem.mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Restored")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val targetUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext Result.failure(Exception("Failed to create MediaStore entry"))

            context.contentResolver.openOutputStream(targetUri)?.use { output ->
                FileInputStream(vaultFile).use { input ->
                    input.copyTo(output)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(targetUri, values, null, null)
            }

            // Remove from vault
            vaultFile.delete()
            securityDao.deleteHiddenMediaById(hiddenItem.id)

            Result.success(targetUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePermanentlyFromVault(hiddenItem: HiddenMediaEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(hiddenItem.vaultPath)
            if (file.exists()) {
                file.delete()
            }
            securityDao.deleteHiddenMediaById(hiddenItem.id)
            true
        } catch (e: Exception) {
            false
        }
    }
}
