package com.photo.gallery.security.crypto

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.StandardOpenOption

/**
 * Media3 DataSource for Encrypted Vault Videos:
 * - Decrypts video streams on the fly using Tink Streaming AEAD (AesGcmHkdfStreaming).
 * - Full random-access SEEKING support via SeekableByteChannel.
 * - Streams directly into ExoPlayer decoders without saving plaintext to disk or cache.
 */
@OptIn(UnstableApi::class)
class EncryptedMedia3DataSource(
    private val cryptoManager: VaultCryptoManager
) : DataSource {

    private var currentUri: Uri? = null
    private var rawFileChannel: FileChannel? = null
    private var decryptingChannel: SeekableByteChannel? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var isOpen = false

    override fun addTransferListener(transferListener: TransferListener) {
        // No-op for local crypto streaming
    }

    override fun open(dataSpec: DataSpec): Long {
        if (!cryptoManager.isUnlocked) {
            throw IOException("Vault is locked; video playback forbidden.")
        }

        currentUri = dataSpec.uri
        val filePath = dataSpec.uri.path ?: throw IOException("URI path is null: ${dataSpec.uri}")
        val file = File(filePath)
        if (!file.exists()) {
            throw IOException("Vault video file not found: $filePath")
        }

        val fileChannel = FileChannel.open(file.toPath(), StandardOpenOption.READ)
        rawFileChannel = fileChannel

        val channel = if (cryptoManager.isEncryptedFile(file)) {
            cryptoManager.newSeekableDecryptingChannel(fileChannel)
        } else {
            fileChannel
        }
        decryptingChannel = channel

        // Support seeking to position
        if (dataSpec.position > 0) {
            channel.position(dataSpec.position)
        }

        val totalSize = channel.size()
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            totalSize - dataSpec.position
        }

        isOpen = true
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val channel = decryptingChannel ?: throw IOException("DataSource not open")
        val bytesToRead = if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            minOf(length.toLong(), bytesRemaining).toInt()
        } else {
            length
        }

        val byteBuffer = ByteBuffer.wrap(buffer, offset, bytesToRead)
        val bytesRead = channel.read(byteBuffer)
        if (bytesRead == -1) {
            bytesRemaining = 0
            return C.RESULT_END_OF_INPUT
        }

        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= bytesRead
        }

        return bytesRead
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        isOpen = false
        try {
            decryptingChannel?.close()
        } catch (_: Exception) {}
        try {
            rawFileChannel?.close()
        } catch (_: Exception) {}
        decryptingChannel = null
        rawFileChannel = null
        currentUri = null
    }

    class Factory(private val cryptoManager: VaultCryptoManager) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return EncryptedMedia3DataSource(cryptoManager)
        }
    }
}
