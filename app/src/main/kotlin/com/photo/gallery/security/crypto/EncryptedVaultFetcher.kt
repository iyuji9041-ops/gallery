package com.photo.gallery.security.crypto

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import okio.buffer
import okio.source
import java.io.File
import java.io.FileInputStream

/**
 * Coil 3 Fetcher for Encrypted Vault Files:
 * Decrypts files on the fly directly into an in-memory streaming ImageSource.
 * Prevents writing decrypted plaintext files to disk or cache (Problem 1).
 */
class EncryptedVaultFetcher(
    private val file: File,
    private val options: Options,
    private val cryptoManager: VaultCryptoManager
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        if (!cryptoManager.isUnlocked) {
            // Vault is locked; refuse to decrypt
            return null
        }

        val rawInput = FileInputStream(file)
        val decryptedStream = if (cryptoManager.isEncryptedFile(file)) {
            cryptoManager.newDecryptingStream(rawInput)
        } else {
            rawInput
        }

        val mimeType = when {
            file.name.endsWith(".png", true) -> "image/png"
            file.name.endsWith(".webp", true) -> "image/webp"
            file.name.endsWith(".mp4", true) -> "video/mp4"
            file.name.endsWith(".mkv", true) -> "video/x-matroska"
            else -> "image/jpeg"
        }

        return SourceFetchResult(
            source = ImageSource(
                source = decryptedStream.source().buffer(),
                fileSystem = options.fileSystem
            ),
            mimeType = mimeType,
            dataSource = DataSource.MEMORY
        )
    }

    class Factory(private val cryptoManager: VaultCryptoManager) : Fetcher.Factory<File> {
        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.absolutePath.contains("/vault/") && (cryptoManager.isEncryptedFile(data) || data.name.endsWith(".enc"))) {
                return EncryptedVaultFetcher(data, options, cryptoManager)
            }
            return null
        }
    }
}

/**
 * Custom Keyer for encrypted vault files:
 * Ensures memory cache keys invalidate when the vault locks or file changes.
 */
class EncryptedVaultKeyer(private val cryptoManager: VaultCryptoManager) : Keyer<File> {
    override fun key(data: File, options: Options): String? {
        if (data.absolutePath.contains("/vault/")) {
            return if (cryptoManager.isUnlocked) {
                "vault_enc_${data.name}_${data.lastModified()}"
            } else {
                null
            }
        }
        return null
    }
}
