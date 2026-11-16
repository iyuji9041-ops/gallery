package com.photo.gallery

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder
import com.photo.gallery.security.crypto.EncryptedVaultFetcher
import com.photo.gallery.security.crypto.EncryptedVaultKeyer
import com.photo.gallery.security.crypto.VaultCryptoManager

/**
 * Gallery Application configuration:
 * 1. VideoFrameDecoder for playing video thumbnails.
 * 2. EncryptedVaultFetcher & EncryptedVaultKeyer for decrypting vault items in-memory.
 */
class GalleryApplication : Application(), SingletonImageLoader.Factory {

    lateinit var cryptoManager: VaultCryptoManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        cryptoManager = VaultCryptoManager(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(EncryptedVaultFetcher.Factory(cryptoManager))
                add(EncryptedVaultKeyer(cryptoManager))
                add(VideoFrameDecoder.Factory())
            }
            .build()
    }

    companion object {
        lateinit var instance: GalleryApplication
            private set
    }
}
