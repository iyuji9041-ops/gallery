package com.photo.gallery.data.mediastore

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.photo.gallery.data.model.MediaItem

sealed class DeletionResult {
    object Success : DeletionResult()
    data class RequiresIntentSender(val intentSenderRequest: IntentSenderRequest) : DeletionResult()
    data class Error(val message: String) : DeletionResult()
}

class MediaDeletionHandler(private val context: Context) {

    suspend fun deleteMedia(uri: Uri, path: String): DeletionResult = withContext(Dispatchers.IO) {
        try {
            // Check if it's a sample/demo web Uri
            if (uri.scheme == "http" || uri.scheme == "https") {
                return@withContext DeletionResult.Success
            }

            // On Android 11+ (API 30+), use createDeleteRequest for scoped storage safety
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    // Try direct delete first (works if app created the media)
                    val rows = context.contentResolver.delete(uri, null, null)
                    if (rows > 0) {
                        return@withContext DeletionResult.Success
                    }
                } catch (securityException: SecurityException) {
                    // Scoped storage requires user confirmation via IntentSender
                    val pendingIntent = MediaStore.createDeleteRequest(
                        context.contentResolver,
                        listOf(uri)
                    )
                    val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    return@withContext DeletionResult.RequiresIntentSender(request)
                } catch (e: Exception) {
                    // Try createDeleteRequest
                    val pendingIntent = MediaStore.createDeleteRequest(
                        context.contentResolver,
                        listOf(uri)
                    )
                    val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    return@withContext DeletionResult.RequiresIntentSender(request)
                }
            } else {
                // API < 30 direct contentResolver delete or file delete
                val rows = context.contentResolver.delete(uri, null, null)
                if (rows > 0) {
                    return@withContext DeletionResult.Success
                }
                if (path.isNotEmpty()) {
                    val file = File(path)
                    if (file.exists() && file.delete()) {
                        return@withContext DeletionResult.Success
                    }
                }
            }
            DeletionResult.Success
        } catch (e: Exception) {
            DeletionResult.Error(e.localizedMessage ?: "Failed to delete media item")
        }
    }

    suspend fun deleteMultipleMedia(items: List<MediaItem>): DeletionResult = withContext(Dispatchers.IO) {
        try {
            val localUris = items.filterNot { it.uri.scheme == "http" || it.uri.scheme == "https" }.map { it.uri }
            if (localUris.isEmpty()) {
                return@withContext DeletionResult.Success
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val pendingIntent = MediaStore.createDeleteRequest(
                        context.contentResolver,
                        localUris
                    )
                    val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    return@withContext DeletionResult.RequiresIntentSender(request)
                } catch (e: Exception) {
                    // Fallback to iterating
                    var hasError = false
                    for (item in items) {
                        deleteMedia(item.uri, item.path)
                    }
                    return@withContext DeletionResult.Success
                }
            } else {
                for (item in items) {
                    deleteMedia(item.uri, item.path)
                }
                return@withContext DeletionResult.Success
            }
        } catch (e: Exception) {
            DeletionResult.Error(e.localizedMessage ?: "Failed to delete items")
        }
    }
}
