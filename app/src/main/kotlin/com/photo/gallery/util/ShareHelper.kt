package com.photo.gallery.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.photo.gallery.data.model.MediaItem

object ShareHelper {
    fun shareMedia(context: Context, items: List<MediaItem>) {
        if (items.isEmpty()) return
        try {
            if (items.size == 1) {
                val item = items.first()
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = item.mimeType.ifEmpty { "image/*" }
                    putExtra(Intent.EXTRA_STREAM, item.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share media"))
            } else {
                val uris = ArrayList(items.map { it.uri })
                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share ${items.size} items"))
            }
        } catch (e: Exception) {
            // Guard against devices with no share activity
        }
    }
}
