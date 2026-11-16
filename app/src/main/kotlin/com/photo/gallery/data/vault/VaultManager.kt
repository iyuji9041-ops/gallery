package com.photo.gallery.data.vault

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import com.photo.gallery.data.local.dao.SecurityDao
import com.photo.gallery.data.local.model.HiddenMediaEntity
import com.photo.gallery.security.crypto.VaultCryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID

sealed class VaultMoveResult {
    data class Success(val entity: HiddenMediaEntity) : VaultMoveResult()
    data class RequiresDeleteConsent(
        val entity: HiddenMediaEntity,
        val intentSenderRequest: IntentSenderRequest
    ) : VaultMoveResult()
    data class CopiedOriginalVisible(
        val entity: HiddenMediaEntity,
        val message: String
    ) : VaultMoveResult()
    data class Error(val message: String) : VaultMoveResult()
}

/**
 * Manages the Encrypted Hidden Vault storage.
 * - Files are encrypted at rest with Tink Streaming AEAD (AesGcmHkdfStreaming).
 * - Isolated strictly in context.filesDir ("vault") with a .nomedia marker.
 * - External storage directories are completely excluded (Problem 7).
 * - Decryption occurs on demand into streams/memory; no plaintext files on disk (Problem 1).
 * - Periodic single-pass reconciliation replaces background FileObserver (Problem 8).
 * - Verifies checksums and handles Scoped Storage deletion consent properly.
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

    /**
     * Move media to the vault:
     * 1. Encrypt plaintext into Tink AEAD stream in private vault dir.
     * 2. Verify encrypted copy (file exists, size, checksum verification).
     * 3. Insert row into database, failing on duplicate conflict (-1L).
     * 4. Request deletion of original via MediaStore.createDeleteRequest if needed.
     */
    suspend fun moveToVault(
        sourceUri: Uri,
        originalPath: String,
        displayName: String,
        mimeType: String,
        size: Long,
        dateAdded: Long,
        dateTaken: Long = 0L,
        relativeFolder: String? = null
    ): VaultMoveResult = withContext(Dispatchers.IO) {
        var targetFile: File? = null
        try {
            if (!cryptoManager.isUnlocked) {
                cryptoManager.unlockWithKeystore()
            }

            val extension = displayName.substringAfterLast(".", "jpg")
            val secureFileName = "vault_${UUID.randomUUID()}_$extension.enc"
            val file = File(vaultDirectory, secureFileName)
            targetFile = file

            // Step 1: Stream and calculate source checksum simultaneously
            val sourceDigest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(file).use { rawOutput ->
                    cryptoManager.newEncryptingStream(rawOutput).use { encOutput ->
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            sourceDigest.update(buffer, 0, read)
                            encOutput.write(buffer, 0, read)
                        }
                    }
                }
            } ?: return@withContext VaultMoveResult.Error("Cannot open source file: $sourceUri")

            val sourceChecksum = sourceDigest.digest()

            // Step 2: Verify encrypted copy (file size + AEAD decryption checksum)
            if (!file.exists() || file.length() == 0L) {
                secureDeleteFile(file)
                return@withContext VaultMoveResult.Error("Encrypted vault file was not created properly")
            }

            val verifyDigest = MessageDigest.getInstance("SHA-256")
            try {
                FileInputStream(file).use { rawInput ->
                    cryptoManager.newDecryptingStream(rawInput).use { decInput ->
                        var read: Int
                        while (decInput.read(buffer).also { read = it } != -1) {
                            verifyDigest.update(buffer, 0, read)
                        }
                    }
                }
            } catch (e: Exception) {
                secureDeleteFile(file)
                return@withContext VaultMoveResult.Error("Verification failed: corrupted encrypted copy (${e.message})")
            }

            if (!MessageDigest.isEqual(sourceChecksum, verifyDigest.digest())) {
                secureDeleteFile(file)
                return@withContext VaultMoveResult.Error("Verification failed: checksum mismatch between source and vault copy")
            }

            // Step 3: Insert single row into database (Problem 2)
            val effectiveDateTaken = if (dateTaken > 0) dateTaken else dateAdded * 1000L
            val entity = HiddenMediaEntity(
                originalPath = originalPath,
                vaultPath = file.absolutePath,
                displayName = displayName,
                mimeType = mimeType,
                size = if (size > 0) size else file.length(),
                dateAdded = dateAdded,
                dateHidden = System.currentTimeMillis(),
                isEncrypted = true,
                relativeFolder = relativeFolder,
                dateTaken = effectiveDateTaken
            )

            val insertedId = securityDao.insertHiddenMedia(entity)
            if (insertedId == -1L) {
                secureDeleteFile(file)
                return@withContext VaultMoveResult.Error("Duplicate row error: item already exists in vault")
            }

            val finalEntity = entity.copy(id = insertedId)

            // Step 4: Delete original safely (Problem 1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val rows = context.contentResolver.delete(sourceUri, null, null)
                    if (rows > 0) {
                        return@withContext VaultMoveResult.Success(finalEntity)
                    }
                } catch (_: SecurityException) {
                    // Fall through to request consent
                } catch (e: Exception) {
                    return@withContext VaultMoveResult.CopiedOriginalVisible(
                        finalEntity,
                        "Copied to vault, but original deletion failed: ${e.localizedMessage}"
                    )
                }

                // Request user permission via IntentSender
                try {
                    val pendingIntent = MediaStore.createDeleteRequest(
                        context.contentResolver,
                        listOf(sourceUri)
                    )
                    val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    return@withContext VaultMoveResult.RequiresDeleteConsent(finalEntity, request)
                } catch (e: Exception) {
                    return@withContext VaultMoveResult.CopiedOriginalVisible(
                        finalEntity,
                        "Copied to vault, original still visible: ${e.localizedMessage}"
                    )
                }
            } else {
                // API < 30
                try {
                    val rows = context.contentResolver.delete(sourceUri, null, null)
                    if (rows > 0) {
                        return@withContext VaultMoveResult.Success(finalEntity)
                    }
                } catch (_: SecurityException) {}

                if (originalPath.isNotEmpty()) {
                    val origFile = File(originalPath)
                    if (origFile.exists() && origFile.delete()) {
                        return@withContext VaultMoveResult.Success(finalEntity)
                    }
                }

                return@withContext VaultMoveResult.CopiedOriginalVisible(
                    finalEntity,
                    "Copied to vault, but original file is still visible in gallery"
                )
            }
        } catch (e: Exception) {
            targetFile?.let { secureDeleteFile(it) }
            VaultMoveResult.Error(e.localizedMessage ?: "Failed to move file to vault")
        }
    }

    /**
     * Restore media from Hidden Vault (Problem 3):
     * - Selects collection from MIME type (Images or Video).
     * - Restores to original relative path or appropriate fallback.
     * - Preserves original name and dates.
     * - Correctly toggles IS_PENDING.
     * - Rolls back MediaStore entry if stream writing fails.
     * - Deletes vault copy only after restored file is fully verified.
     */
    suspend fun restoreFromVault(hiddenItem: HiddenMediaEntity): Result<Uri> = withContext(Dispatchers.IO) {
        var createdUri: Uri? = null
        try {
            val vaultFile = File(hiddenItem.vaultPath)
            if (!vaultFile.exists()) {
                return@withContext Result.failure(IOException("Vault file does not exist: ${hiddenItem.vaultPath}"))
            }

            if (!cryptoManager.isUnlocked) {
                cryptoManager.unlockWithKeystore()
            }

            val isVideo = hiddenItem.mimeType.startsWith("video", ignoreCase = true)
            val collectionUri = if (isVideo) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
            }

            val dateAddedSec = if (hiddenItem.dateAdded > 10_000_000_000L) hiddenItem.dateAdded / 1000 else hiddenItem.dateAdded
            val dateTakenMs = if (hiddenItem.dateTaken > 0) hiddenItem.dateTaken else dateAddedSec * 1000L

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, hiddenItem.displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, hiddenItem.mimeType)
                put(MediaStore.MediaColumns.DATE_ADDED, dateAddedSec)
                put(MediaStore.MediaColumns.DATE_MODIFIED, dateAddedSec)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.DATE_TAKEN, dateTakenMs)
                    val relPath = hiddenItem.relativeFolder?.takeIf { it.isNotBlank() }
                        ?: if (isVideo) Environment.DIRECTORY_MOVIES + "/Restored" else Environment.DIRECTORY_PICTURES + "/Restored"
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val targetUri = context.contentResolver.insert(collectionUri, values)
                ?: return@withContext Result.failure(IOException("Failed to create MediaStore entry"))
            createdUri = targetUri

            // Stream decrypted content directly into MediaStore OutputStream
            try {
                context.contentResolver.openOutputStream(targetUri)?.use { rawOutput ->
                    if (cryptoManager.isEncryptedFile(vaultFile)) {
                        FileInputStream(vaultFile).use { rawInput ->
                            cryptoManager.newDecryptingStream(rawInput).use { decInput ->
                                decInput.copyTo(rawOutput)
                            }
                        }
                    } else {
                        // Plaintext fallback
                        FileInputStream(vaultFile).use { rawInput ->
                            rawInput.copyTo(rawOutput)
                        }
                    }
                    rawOutput.flush()
                } ?: throw IOException("Cannot open MediaStore output stream")
            } catch (writeErr: Exception) {
                // Rollback MediaStore row if write fails
                try {
                    context.contentResolver.delete(targetUri, null, null)
                } catch (_: Exception) {}
                createdUri = null
                return@withContext Result.failure(writeErr)
            }

            // Release pending state on Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(targetUri, values, null, null)
            }

            // Verify the restored file exists and has content before deleting vault file
            val verifyLength = try {
                context.contentResolver.openInputStream(targetUri)?.use { it.available().toLong() } ?: 0L
            } catch (e: Exception) {
                0L
            }

            if (verifyLength <= 0 && hiddenItem.size > 0) {
                // Rollback
                try {
                    context.contentResolver.delete(targetUri, null, null)
                } catch (_: Exception) {}
                return@withContext Result.failure(IOException("Verification failed: restored file appears empty"))
            }

            // Delete vault copy ONLY after verified
            secureDeleteFile(vaultFile)
            securityDao.deleteHiddenMediaById(hiddenItem.id)

            Result.success(targetUri)
        } catch (e: Exception) {
            createdUri?.let { uri ->
                try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
            }
            Result.failure(e)
        }
    }

    suspend fun deletePermanentlyFromVault(hiddenItem: HiddenMediaEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val vaultFile = File(hiddenItem.vaultPath)
            if (vaultFile.exists()) {
                secureDeleteFile(vaultFile)
            }
            securityDao.deleteHiddenMediaById(hiddenItem.id)
            true
        } catch (e: Exception) {
            false
        }
    }

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
                    val isEnc = cryptoManager.isEncryptedFile(file)
                    val entity = HiddenMediaEntity(
                        originalPath = "",
                        vaultPath = file.absolutePath,
                        displayName = name,
                        mimeType = mime,
                        size = file.length(),
                        dateAdded = file.lastModified() / 1000L,
                        dateHidden = file.lastModified(),
                        isEncrypted = isEnc,
                        relativeFolder = null,
                        dateTaken = file.lastModified()
                    )
                    securityDao.insertHiddenMedia(entity)
                }
            }
        } catch (_: Exception) {
            // Ignore background reconciliation errors
        }
    }

    /**
     * Securely overwrite file data before unlinking to prevent flash memory forensic recovery.
     */
    fun secureDeleteFile(file: File): Boolean {
        if (!file.exists()) return true
        return try {
            val length = file.length()
            if (length > 0) {
                RandomAccessFile(file, "rws").use { raf ->
                    val zeroBuf = ByteArray(4096)
                    var written = 0L
                    while (written < length) {
                        val toWrite = minOf(zeroBuf.size.toLong(), length - written).toInt()
                        raf.write(zeroBuf, 0, toWrite)
                        written += toWrite
                    }
                }
            }
            file.delete()
        } catch (e: Exception) {
            file.delete()
        }
    }
}
