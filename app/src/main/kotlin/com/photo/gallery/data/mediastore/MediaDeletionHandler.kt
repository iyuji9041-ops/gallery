package com.photo.gallery.data.mediastore

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import com.photo.gallery.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed class DeletionResult {
    data class Success(val deletedCount: Int) : DeletionResult()
    data class RequiresIntentSender(
        val intentSenderRequest: IntentSenderRequest,
        val targetUris: List<Uri>,
        val isBulk: Boolean = false
    ) : DeletionResult()
    data class PartialFailure(
        val successCount: Int,
        val failedCount: Int,
        val message: String
    ) : DeletionResult()
    data class Error(val message: String) : DeletionResult()
}

class MediaDeletionHandler(private val context: Context) {

    suspend fun deleteMedia(uri: Uri, path: String): DeletionResult = withContext(Dispatchers.IO) {
        try {
            // Android 11+ (API 30+) Scoped Storage
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val rows = context.contentResolver.delete(uri, null, null)
                    if (rows > 0) {
                        return@withContext DeletionResult.Success(rows)
                    }
                } catch (_: SecurityException) {
                    // Fallthrough to createDeleteRequest below
                } catch (e: Exception) {
                    return@withContext DeletionResult.Error(e.localizedMessage ?: "Failed to delete item")
                }

                // If rows == 0 or SecurityException was thrown, request system delete consent
                return@withContext try {
                    val pendingIntent = MediaStore.createDeleteRequest(
                        context.contentResolver,
                        listOf(uri)
                    )
                    val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    DeletionResult.RequiresIntentSender(request, listOf(uri), isBulk = false)
                } catch (e: Exception) {
                    DeletionResult.Error("Could not request delete permission: ${e.localizedMessage}")
                }
            } else {
                // API < 30 (Android 10 and below)
                try {
                    val rows = context.contentResolver.delete(uri, null, null)
                    if (rows > 0) {
                        return@withContext DeletionResult.Success(rows)
                    }
                } catch (sec: SecurityException) {
                    return@withContext DeletionResult.Error("Permission denied: ${sec.localizedMessage}")
                }

                if (path.isNotEmpty()) {
                    val file = File(path)
                    if (file.exists() && file.delete()) {
                        return@withContext DeletionResult.Success(1)
                    }
                }
                return@withContext DeletionResult.Error("Failed to delete item: file not found or write protected")
            }
        } catch (e: Exception) {
            DeletionResult.Error(e.localizedMessage ?: "Failed to delete media item")
        }
    }

    suspend fun deleteMultipleMedia(items: List<MediaItem>): DeletionResult = withContext(Dispatchers.IO) {
        try {
            val localUris = items.map { it.uri }
            if (localUris.isEmpty()) {
                return@withContext DeletionResult.Success(0)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val pendingIntent = MediaStore.createDeleteRequest(
                        context.contentResolver,
                        localUris
                    )
                    val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    return@withContext DeletionResult.RequiresIntentSender(request, localUris, isBulk = true)
                } catch (e: Exception) {
                    // Fall back to iterating
                    var successCount = 0
                    var failCount = 0
                    for (item in items) {
                        when (deleteMedia(item.uri, item.path)) {
                            is DeletionResult.Success -> successCount++
                            else -> failCount++
                        }
                    }
                    return@withContext if (failCount == 0) {
                        DeletionResult.Success(successCount)
                    } else if (successCount > 0) {
                        DeletionResult.PartialFailure(successCount, failCount, "Deleted $successCount items, $failCount failed")
                    } else {
                        DeletionResult.Error("Failed to delete selected items: ${e.localizedMessage}")
                    }
                }
            } else {
                var successCount = 0
                var failCount = 0
                for (item in items) {
                    when (deleteMedia(item.uri, item.path)) {
                        is DeletionResult.Success -> successCount++
                        else -> failCount++
                    }
                }
                return@withContext if (failCount == 0) {
                    DeletionResult.Success(successCount)
                } else if (successCount > 0) {
                    DeletionResult.PartialFailure(successCount, failCount, "Deleted $successCount items, $failCount failed")
                } else {
                    DeletionResult.Error("Failed to delete items: permission denied or files protected")
                }
            }
        } catch (e: Exception) {
            DeletionResult.Error(e.localizedMessage ?: "Failed to delete items")
        }
    }
}
