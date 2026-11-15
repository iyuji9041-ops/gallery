package com.photo.gallery

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder

/**
 * BUG FIX: video thumbnails weren't showing anywhere (grid, albums, vault) because
 * Coil's default ImageLoader only knows how to decode image formats - it has no idea
 * how to pull a preview frame out of an .mp4/.mkv/etc. Registering VideoFrameDecoder
 * here (via SingletonImageLoader.Factory, which Coil automatically picks up from the
 * Application class app-wide) means every AsyncImage(...) call in the app - grid
 * thumbnails, album covers, the vault grid - can now render a real frame from a
 * video file, with zero changes needed at each individual call site.
 */
class GalleryApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
    }
}
