package com.photo.gallery.security

import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.StandardOpenOption
import java.security.SecureRandom
import java.util.Arrays

class VaultCryptoTest {

    private val masterKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private val associatedData = "TestAssociatedData".toByteArray(Charsets.UTF_8)
    private val magicHeader = byteArrayOf(0x56, 0x4C, 0x54, 0x31)

    private fun createStreamingAead(): AesGcmHkdfStreaming {
        return AesGcmHkdfStreaming(
            masterKey,
            "HmacSha256",
            32,
            4096, // 4KB segment size for test
            0
        )
    }

    @Test
    fun testEncryptDecryptRoundTrip() {
        val streaming = createStreamingAead()
        val originalData = ByteArray(50 * 1024) { (it % 256).toByte() }

        // Encrypt
        val cipherOut = ByteArrayOutputStream()
        cipherOut.write(magicHeader)
        val encStream = streaming.newEncryptingStream(cipherOut, associatedData)
        encStream.write(originalData)
        encStream.close()

        val ciphertext = cipherOut.toByteArray()
        assertTrue("Ciphertext must be larger than plaintext due to AEAD segments and headers", ciphertext.size > originalData.size)

        // Decrypt
        val cipherIn = ByteArrayInputStream(ciphertext)
        val header = ByteArray(magicHeader.size)
        cipherIn.read(header)
        assertArrayEquals("Magic header must match", magicHeader, header)

        val decStream = streaming.newDecryptingStream(cipherIn, associatedData)
        val decryptedData = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        var read: Int
        while (decStream.read(buf).also { read = it } != -1) {
            decryptedData.write(buf, 0, read)
        }
        decStream.close()

        assertArrayEquals("Decrypted data must match original plaintext exactly", originalData, decryptedData.toByteArray())
    }

    @Test
    fun testTamperDetection() {
        val streaming = createStreamingAead()
        val originalData = "Super secret vault content that must not be altered".toByteArray()

        val cipherOut = ByteArrayOutputStream()
        cipherOut.write(magicHeader)
        val encStream = streaming.newEncryptingStream(cipherOut, associatedData)
        encStream.write(originalData)
        encStream.close()

        val ciphertext = cipherOut.toByteArray()

        // Tamper with one byte in the ciphertext body
        ciphertext[ciphertext.size - 5] = (ciphertext[ciphertext.size - 5] + 1).toByte()

        val cipherIn = ByteArrayInputStream(ciphertext)
        val header = ByteArray(magicHeader.size)
        cipherIn.read(header)

        try {
            val decStream = streaming.newDecryptingStream(cipherIn, associatedData)
            val buf = ByteArray(1024)
            while (decStream.read(buf) != -1) {}
            fail("Decryption must throw an exception when ciphertext is tampered with")
        } catch (e: Exception) {
            // Expected AEAD authentication failure
            assertTrue(e.message != null || e.cause != null)
        }
    }

    @Test
    fun testRandomAccessSeeking() {
        val streaming = createStreamingAead()
        // Create 64 KB of patterned data
        val originalData = ByteArray(64 * 1024) { (it % 251).toByte() }

        val tempFile = File.createTempFile("vault_seek_test", ".enc")
        tempFile.deleteOnExit()

        val fileOut = tempFile.outputStream()
        fileOut.write(magicHeader)
        val encStream = streaming.newEncryptingStream(fileOut, associatedData)
        encStream.write(originalData)
        encStream.close()

        val rawChannel = FileChannel.open(tempFile.toPath(), StandardOpenOption.READ)

        // Consume header
        val headerBuf = ByteBuffer.allocate(magicHeader.size)
        rawChannel.read(headerBuf)
        headerBuf.flip()

        val subChannel = object : SeekableByteChannel {
            override fun isOpen(): Boolean = rawChannel.isOpen
            override fun close() = rawChannel.close()
            override fun read(dst: ByteBuffer): Int = rawChannel.read(dst)
            override fun write(src: ByteBuffer): Int = throw UnsupportedOperationException()
            override fun position(): Long = rawChannel.position() - magicHeader.size
            override fun position(newPosition: Long): SeekableByteChannel {
                rawChannel.position(newPosition + magicHeader.size)
                return this
            }
            override fun size(): Long = rawChannel.size() - magicHeader.size
            override fun truncate(size: Long): SeekableByteChannel = throw UnsupportedOperationException()
        }

        val seekChannel = streaming.newSeekableDecryptingChannel(subChannel, associatedData)

        // Seek to arbitrary offsets and verify
        val seekOffsets = listOf(0L, 512L, 4096L, 10240L, 32768L, 60000L)
        for (offset in seekOffsets) {
            seekChannel.position(offset)
            assertEquals("Channel position must match requested seek position", offset, seekChannel.position())

            val readBuf = ByteBuffer.allocate(256)
            val bytesRead = seekChannel.read(readBuf)
            assertEquals("Must read 256 bytes", 256, bytesRead)
            readBuf.flip()

            val actualSlice = ByteArray(bytesRead)
            readBuf.get(actualSlice)

            val expectedSlice = originalData.copyOfRange(offset.toInt(), (offset + bytesRead).toInt())
            assertArrayEquals("Read bytes at offset $offset must match original plaintext slice", expectedSlice, actualSlice)
        }

        seekChannel.close()
        tempFile.delete()
    }
}
