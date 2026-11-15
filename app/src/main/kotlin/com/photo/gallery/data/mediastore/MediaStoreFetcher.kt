package com.photo.gallery.data.mediastore

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
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

        // If gallery is empty (e.g. fresh emulator or test environment), provide starter demonstration assets
        if (mediaList.isEmpty()) {
            mediaList.addAll(generateSampleMedia(lockedPaths, lockedAlbums))
        }

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
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val mime = if (mimeCol != -1) cursor.getString(mimeCol) ?: "image/jpeg" else "image/jpeg"
                    val width = if (widthCol != -1) cursor.getInt(widthCol) else 0
                    val height = if (heightCol != -1) cursor.getInt(heightCol) else 0

                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                    val isLocked = lockedPaths.contains(path) || lockedAlbums.contains(album)

                    destination.add(
                        MediaItem(
                            id = id,
                            uri = uri,
                            path = path,
                            displayName = name,
                            albumName = album,
                            dateAdded = dateAdded,
                            size = size,
                            mimeType = mime,
                            width = width,
                            height = height,
                            isVideo = false,
                            isLocked = isLocked
                        )
                    )
                }
            }
        } catch (e: Exception) {
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
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val mime = if (mimeCol != -1) cursor.getString(mimeCol) ?: "video/mp4" else "video/mp4"
                    val width = if (widthCol != -1) cursor.getInt(widthCol) else 0
                    val height = if (heightCol != -1) cursor.getInt(heightCol) else 0
                    val duration = if (durationCol != -1) cursor.getLong(durationCol) else 0L

                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    val isLocked = lockedPaths.contains(path) || lockedAlbums.contains(album)

                    destination.add(
                        MediaItem(
                            id = id,
                            uri = uri,
                            path = path,
                            displayName = name,
                            albumName = album,
                            dateAdded = dateAdded,
                            size = size,
                            mimeType = mime,
                            width = width,
                            height = height,
                            isVideo = true,
                            durationMs = duration,
                            isLocked = isLocked
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Permission or MediaStore issue handled gracefully
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

    /**
     * Starter demo media for immediate interactivity when device has zero MediaStore images.
     */
    private fun generateSampleMedia(
        lockedPaths: Set<String>,
        lockedAlbums: Set<String>
    ): List<MediaItem> {
        val now = System.currentTimeMillis() / 1000
        val samples = listOf(
            Triple("Mountain_Summit_HDR.jpg", "Camera", "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1080"),
            Triple("Neon_Cyber_Tokyo.jpg", "Downloads", "https://images.unsplash.com/photo-1514565131-fce0801e5785?w=1080"),
            Triple("Financial_Statement_Private.jpg", "Screenshots", "https://images.unsplash.com/photo-1554224155-8d04cb21cd6c?w=1080"),
            Triple("Sunset_Pacific_Beach.jpg", "Camera", "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=1080"),
            Triple("Architectural_Minimalism.jpg", "Camera", "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?w=1080"),
            Triple("Personal_ID_Card.jpg", "Screenshots", "https://images.unsplash.com/photo-1589829545856-d10d557cf95f?w=1080"),
            Triple("Forest_Mist_Canopy.jpg", "Camera", "https://images.unsplash.com/photo-1448375240586-882707db888b?w=1080"),
            Triple("Desert_Star_Trail.jpg", "Camera", "https://images.unsplash.com/photo-1509316975850-ff9c5deb0cd9?w=1080")
        )

        return samples.mapIndexed { index, (name, album, url) ->
            val fakePath = "/storage/emulated/0/$album/$name"
            val isLocked = lockedPaths.contains(fakePath) || lockedAlbums.contains(album) || (name.contains("Private") || name.contains("ID"))
            MediaItem(
                id = -(index + 1).toLong(),
                uri = Uri.parse(url),
                path = fakePath,
                displayName = name,
                albumName = album,
                dateAdded = now - (index * 86400L),
                size = 2_845_000L + (index * 320_000L),
                mimeType = "image/jpeg",
                width = 1920,
                height = 1080,
                isVideo = false,
                isLocked = isLocked
            )
        }
    }
}
