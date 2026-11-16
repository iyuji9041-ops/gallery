package com.photo.gallery.data.mediastore

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.photo.gallery.data.model.AlbumItem
import com.photo.gallery.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaStoreFetcher(private val context: Context) {

    /**
     * Query device media via MediaStore API.
     * Safe against column absence, cursor nulls, and permission states.
     * Truly returns empty list when device has no photos (no fake demo photos).
     */
    suspend fun fetchMedia(
        lockedPaths: Set<String>,
        lockedAlbums: Set<String>,
        hiddenPaths: Set<String> = emptySet()
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<MediaItem>()

        // 1. Fetch Images
        fetchImages(mediaList, lockedPaths, lockedAlbums, hiddenPaths)

        // 2. Fetch Videos
        fetchVideos(mediaList, lockedPaths, lockedAlbums, hiddenPaths)

        // Sort latest first
        mediaList.sortByDescending { it.dateAdded }

        mediaList
    }

    private fun fetchImages(
        destination: MutableList<MediaItem>,
        lockedPaths: Set<String>,
        lockedAlbums: Set<String>,
        hiddenPaths: Set<String>
    ) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val bucketCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val dateCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                val dateTakenCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                val widthCol = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val path = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""

                    // Ensure vault/hidden files are never shown in public gallery
                    if (path.contains("/vault/") || path.contains("/.vault/") || hiddenPaths.contains(path)) {
                        continue
                    }

                    val name = if (nameCol != -1) cursor.getString(nameCol) ?: "IMG_$id.jpg" else "IMG_$id.jpg"
                    val album = if (bucketCol != -1) cursor.getString(bucketCol) ?: "Camera" else "Camera"
                    val dateAdded = if (dateCol != -1) cursor.getLong(dateCol) else 0L
                    val dateTaken = if (dateTakenCol != -1) cursor.getLong(dateTakenCol) else dateAdded * 1000L
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val mime = if (mimeCol != -1) cursor.getString(mimeCol) ?: "image/jpeg" else "image/jpeg"
                    val width = if (widthCol != -1) cursor.getInt(widthCol) else 0
                    val height = if (heightCol != -1) cursor.getInt(heightCol) else 0

                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    val isLocked = lockedPaths.contains(path) || lockedAlbums.contains(album)
                    val relFolder = extractRelativeFolder(path, album)

                    destination.add(
                        MediaItem(
                            id = id,
                            uri = uri,
                            path = path,
                            displayName = name,
                            albumName = album,
                            dateAdded = dateAdded,
                            dateTaken = dateTaken,
                            size = size,
                            mimeType = mime,
                            width = width,
                            height = height,
                            isVideo = false,
                            isLocked = isLocked,
                            relativeFolder = relFolder
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Permission or MediaStore issue handled gracefully
        }
    }

    private fun fetchVideos(
        destination: MutableList<MediaItem>,
        lockedPaths: Set<String>,
        lockedAlbums: Set<String>,
        hiddenPaths: Set<String>
    ) {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DURATION
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val bucketCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                val dateTakenCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_TAKEN)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val path = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""

                    // Ensure vault/hidden files are never shown in public gallery
                    if (path.contains("/vault/") || path.contains("/.vault/") || hiddenPaths.contains(path)) {
                        continue
                    }

                    val name = if (nameCol != -1) cursor.getString(nameCol) ?: "VID_$id.mp4" else "VID_$id.mp4"
                    val album = if (bucketCol != -1) cursor.getString(bucketCol) ?: "Videos" else "Videos"
                    val dateAdded = if (dateCol != -1) cursor.getLong(dateCol) else 0L
                    val dateTaken = if (dateTakenCol != -1) cursor.getLong(dateTakenCol) else dateAdded * 1000L
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val mime = if (mimeCol != -1) cursor.getString(mimeCol) ?: "video/mp4" else "video/mp4"
                    val width = if (widthCol != -1) cursor.getInt(widthCol) else 0
                    val height = if (heightCol != -1) cursor.getInt(heightCol) else 0
                    val duration = if (durationCol != -1) cursor.getLong(durationCol) else 0L

                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    val isLocked = lockedPaths.contains(path) || lockedAlbums.contains(album)
                    val relFolder = extractRelativeFolder(path, album)

                    destination.add(
                        MediaItem(
                            id = id,
                            uri = uri,
                            path = path,
                            displayName = name,
                            albumName = album,
                            dateAdded = dateAdded,
                            dateTaken = dateTaken,
                            size = size,
                            mimeType = mime,
                            width = width,
                            height = height,
                            isVideo = true,
                            durationMs = duration,
                            isLocked = isLocked,
                            relativeFolder = relFolder
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Permission or MediaStore issue handled gracefully
        }
    }

    private fun extractRelativeFolder(path: String, fallbackAlbum: String): String {
        if (path.isEmpty()) return fallbackAlbum
        return try {
            val file = File(path)
            val parent = file.parentFile ?: return fallbackAlbum
            val parentPath = parent.absolutePath
            val emulatedPrefix = "/storage/emulated/0/"
            if (parentPath.startsWith(emulatedPrefix)) {
                parentPath.removePrefix(emulatedPrefix)
            } else {
                parent.name
            }
        } catch (_: Exception) {
            fallbackAlbum
        }
    }

    /**
     * Extracts organized albums by grouping media items.
     */
    fun extractAlbums(mediaItems: List<MediaItem>, lockedAlbums: Set<String>): List<AlbumItem> {
        return mediaItems
            .groupBy { it.albumName }
            .map { (albumName, items) ->
                AlbumItem(
                    name = albumName,
                    coverUri = items.firstOrNull()?.uri,
                    itemCount = items.size,
                    isLocked = lockedAlbums.contains(albumName)
                )
            }
            .sortedByDescending { it.itemCount }
    }
}
