package com.photo.gallery

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder

/**
 * BUG FIX: video thumbnails weren't showing anywhere (grid, albums, vault) because
 * Coil's default ImageLoader only knows how to decode image formats - it has no idea
 * how to pull a preview frame out of an .mp4/.mkv/etc. Registering VideoFrameDecoder
 * here (via ImageLoaderFactory, which Coil automatically picks up from the
 * Application class app-wide) means every AsyncImage(...) call in the app - grid
 * thumbnails, album covers, the vault grid - can now render a real frame from a
 * video file, with zero changes needed at each individual call site.
 */
class GalleryApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
    }
}
