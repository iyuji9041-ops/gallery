package com.photo.gallery.data.vault

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.photo.gallery.data.local.dao.SecurityDao
import com.photo.gallery.data.local.model.HiddenMediaEntity
import com.photo.gallery.security.crypto.VaultCryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID

/**
 * Manages the Encrypted Hidden Vault storage.
 * - Files are encrypted at rest with Tink Streaming AEAD (AesGcmHkdfStreaming).
 * - Isolated strictly in context.filesDir ("vault") with a .nomedia marker.
 * - External storage directories are completely excluded (Problem 7).
 * - Decryption occurs on demand into streams/memory; no plaintext files on disk (Problem 1).
 * - Periodic single-pass reconciliation replaces background FileObserver (Problem 8).
 */
class VaultManager(
    private val context: Context,
    private val securityDao: SecurityDao,
    val cryptoManager: VaultCryptoManager
) {
    val vaultDirectory: File
        get() {
            val dir = File(context.filesDir, "vault")
            ensureNoMedia(dir)
            return dir
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
            if (!cryptoManager.isUnlocked) {
                // Ensure keystore is unlocked
                cryptoManager.unlockWithKeystore()
            }

            val extension = displayName.substringAfterLast(".", "jpg")
            val secureFileName = "vault_${UUID.randomUUID()}_$extension.enc"
            val targetFile = File(vaultDirectory, secureFileName)

            // Stream plaintext into Tink Streaming AEAD Encrypting Stream
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { rawOutput ->
                    cryptoManager.newEncryptingStream(rawOutput).use { encOutput ->
                        input.copyTo(encOutput)
                    }
                }
            } ?: return@withContext Result.failure(Exception("Cannot open source file"))

            // Save in Room database
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
                // Scoped storage consent handled by caller if required
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

            if (!cryptoManager.isUnlocked) {
                cryptoManager.unlockWithKeystore()
            }

            val isVideo = hiddenItem.mimeType.startsWith("video", true)
            val collectionUri = if (isVideo) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, hiddenItem.displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, hiddenItem.mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val relPath = if (isVideo) {
                        Environment.DIRECTORY_MOVIES + "/Restored"
                    } else {
                        Environment.DIRECTORY_PICTURES + "/Restored"
                    }
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val targetUri = context.contentResolver.insert(collectionUri, values)
                ?: return@withContext Result.failure(Exception("Failed to create MediaStore entry"))

            // Stream decrypted content directly into MediaStore OutputStream
            context.contentResolver.openOutputStream(targetUri)?.use { rawOutput ->
                if (cryptoManager.isEncryptedFile(vaultFile)) {
                    FileInputStream(vaultFile).use { rawInput ->
                        cryptoManager.newDecryptingStream(rawInput).use { decInput ->
                            decInput.copyTo(rawOutput)
                        }
                    }
                } else {
                    // Legacy plaintext fallback
                    FileInputStream(vaultFile).use { rawInput ->
                        rawInput.copyTo(rawOutput)
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(targetUri, values, null, null)
            }

            // Securely wipe and remove from vault
            secureDeleteFile(vaultFile)
            securityDao.deleteHiddenMediaById(hiddenItem.id)

            Result.success(targetUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePermanentlyFromVault(hiddenItem: HiddenMediaEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(hiddenItem.vaultPath)
            secureDeleteFile(file)
            securityDao.deleteHiddenMediaById(hiddenItem.id)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Reconcile pass replacing VaultMonitorService:
     * - Cleans up database rows if physical files were deleted.
     * - Restores database records for any orphan encrypted vault files found on disk.
     */
    suspend fun reconcileVault(): Unit = withContext(Dispatchers.IO) {
        try {
            val dir = vaultDirectory
            val filesOnDisk = dir.listFiles { _, name -> name != ".nomedia" && !name.endsWith(".tmp_enc") }
                ?: emptyArray()
            val filesOnDiskMap = filesOnDisk.associateBy { it.absolutePath }

            val dbItems = securityDao.getAllHiddenList()
            val dbItemsMap = dbItems.associateBy { it.vaultPath }

            // 1. Remove DB rows whose physical file no longer exists
            for (item in dbItems) {
                if (!filesOnDiskMap.containsKey(item.vaultPath)) {
                    securityDao.deleteHiddenMediaById(item.id)
                }
            }

            // 2. Add DB rows for any unindexed files on disk
            for (file in filesOnDisk) {
                if (!dbItemsMap.containsKey(file.absolutePath)) {
                    val name = file.name
                    val isVideo = name.contains("video", true) || name.contains("mp4", true) || name.contains("mkv", true)
                    val mime = if (isVideo) "video/mp4" else "image/jpeg"
                    val entity = HiddenMediaEntity(
                        originalPath = "",
                        vaultPath = file.absolutePath,
                        displayName = name,
                        mimeType = mime,
                        size = file.length(),
                        dateAdded = file.lastModified() / 1000L,
                        dateHidden = file.lastModified()
                    )
                    securityDao.insertHiddenMedia(entity)
                }
            }
        } catch (e: Exception) {
            // Ignore background reconciliation errors
        }
    }

    private fun secureDeleteFile(file: File) {
        try {
            if (file.exists() && file.isFile) {
                val length = file.length()
                if (length > 0) {
                    RandomAccessFile(file, "rws").use { raf ->
                        val zeroBuffer = ByteArray(64 * 1024)
                        var written = 0L
                        while (written < length) {
                            val toWrite = minOf(zeroBuffer.size.toLong(), length - written).toInt()
                            raf.write(zeroBuffer, 0, toWrite)
                            written += toWrite
                        }
                    }
                }
                file.delete()
            }
        } catch (e: Exception) {
            file.delete()
        }
    }
}
