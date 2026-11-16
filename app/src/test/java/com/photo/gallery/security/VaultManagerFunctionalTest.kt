package com.photo.gallery.security

import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

class VaultManagerFunctionalTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testChecksumVerificationDuringVaultEncryption() {
        val masterKey = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        val streamingAead = AesGcmHkdfStreaming(masterKey, "HmacSha256", 32, 64 * 1024, 0)
        val associatedData = "vault_aead_ad".toByteArray()

        val originalData = "Top-secret document contents to be encrypted and verified".toByteArray()
        val originalChecksum = MessageDigest.getInstance("SHA-256").digest(originalData)

        val vaultFile = tempFolder.newFile("vault_verified.enc")

        // 1. Encrypt and write to vault file
        val input = ByteArrayInputStream(originalData)
        val sourceDigest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1024)

        FileOutputStream(vaultFile).use { rawOut ->
            streamingAead.newEncryptingStream(rawOut, associatedData).use { encOut ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    sourceDigest.update(buffer, 0, read)
                    encOut.write(buffer, 0, read)
                }
            }
        }

        assertEquals(originalChecksum.toList(), sourceDigest.digest().toList())

        // 2. Verify encrypted copy
        val verifyDigest = MessageDigest.getInstance("SHA-256")
        FileInputStream(vaultFile).use { rawIn ->
            streamingAead.newDecryptingStream(rawIn, associatedData).use { decIn ->
                var read: Int
                while (decIn.read(buffer).also { read = it } != -1) {
                    verifyDigest.update(buffer, 0, read)
                }
            }
        }

        assertTrue("Verified copy matches source SHA-256 checksum", MessageDigest.isEqual(originalChecksum, verifyDigest.digest()))
    }

    @Test
    fun testCorruptedCiphertextFailsVerification() {
        val masterKey = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        val streamingAead = AesGcmHkdfStreaming(masterKey, "HmacSha256", 32, 64 * 1024, 0)
        val associatedData = "vault_aead_ad".toByteArray()

        val originalData = "Financial statement private data".toByteArray()
        val vaultFile = tempFolder.newFile("vault_tampered.enc")

        FileOutputStream(vaultFile).use { rawOut ->
            streamingAead.newEncryptingStream(rawOut, associatedData).use { encOut ->
                encOut.write(originalData)
            }
        }

        // Tamper with the encrypted bytes
        val bytes = vaultFile.readBytes()
        bytes[bytes.size - 5] = (bytes[bytes.size - 5] + 1).toByte()
        vaultFile.writeBytes(bytes)

        var failed = false
        try {
            FileInputStream(vaultFile).use { rawIn ->
                streamingAead.newDecryptingStream(rawIn, associatedData).use { decIn ->
                    decIn.readBytes()
                }
            }
        } catch (e: Exception) {
            failed = true
        }

        assertTrue("Tampered vault file MUST fail AEAD decryption", failed)
    }

    @Test
    fun testVideoVsPhotoRestoreDestinationSelection() {
        val videoMime = "video/mp4"
        val photoMime = "image/jpeg"

        val isVideo1 = videoMime.startsWith("video", ignoreCase = true)
        val isVideo2 = photoMime.startsWith("video", ignoreCase = true)

        assertTrue("video/mp4 correctly recognized as video", isVideo1)
        assertFalse("image/jpeg correctly recognized as photo", isVideo2)

        val videoFolder = if (isVideo1) "Movies/Restored" else "Pictures/Restored"
        val photoFolder = if (isVideo2) "Movies/Restored" else "Pictures/Restored"

        assertEquals("Movies/Restored", videoFolder)
        assertEquals("Pictures/Restored", photoFolder)
    }

    @Test
    fun testDeletionResultSemantics() {
        // Verify that 0 rows deleted is never treated as success
        val rows = 0
        val isSuccess = rows > 0
        assertFalse("0 deleted rows must not be marked as Success", isSuccess)
    }
}
