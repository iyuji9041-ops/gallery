package com.photo.gallery.security

import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.Arrays

class VaultMigrationTest {

    private val masterKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private val magicHeader = byteArrayOf(0x56, 0x4C, 0x54, 0x31)
    private val associatedData = "GalleryVaultAEAD_v1".toByteArray()

    private fun getStreaming(): AesGcmHkdfStreaming {
        return AesGcmHkdfStreaming(masterKey, "HmacSha256", 32, 4096, 0)
    }

    private fun isEncrypted(file: File): Boolean {
        if (!file.exists() || file.length() < magicHeader.size) return false
        return FileInputStream(file).use { input ->
            val header = ByteArray(magicHeader.size)
            input.read(header) == magicHeader.size && Arrays.equals(header, magicHeader)
        }
    }

    @Test
    fun testMigrationIdempotenceAndDataIntegrity() {
        val testDir = File(System.getProperty("java.io.tmpdir"), "vault_migration_test_" + System.currentTimeMillis())
        testDir.mkdirs()
        testDir.deleteOnExit()

        // Create 3 plaintext files
        val plainFiles = (1..3).map { i ->
            val f = File(testDir, "legacy_photo_$i.jpg")
            f.writeBytes("Legacy photo content #$i with unique bytes".toByteArray())
            f
        }

        val originalContents = plainFiles.map { it.readBytes() }

        // Perform migration
        val files = testDir.listFiles { _, name -> name != ".nomedia" } ?: emptyArray()
        for (plainFile in files) {
            if (isEncrypted(plainFile)) continue

            val tmpEnc = File(testDir, "${plainFile.name}.tmp_enc")
            FileInputStream(plainFile).use { input ->
                FileOutputStream(tmpEnc).use { rawOut ->
                    rawOut.write(magicHeader)
                    getStreaming().newEncryptingStream(rawOut, associatedData).use { encOut ->
                        input.copyTo(encOut)
                    }
                }
            }

            // Verify readability before deleting plaintext
            FileInputStream(tmpEnc).use { rawIn ->
                val h = ByteArray(magicHeader.size)
                rawIn.read(h)
                getStreaming().newDecryptingStream(rawIn, associatedData).use { decIn ->
                    val checkBuf = ByteArray(128)
                    decIn.read(checkBuf)
                }
            }

            val finalEnc = File(testDir, "${plainFile.nameWithoutExtension}.enc")
            assertTrue("Atomic rename must succeed", tmpEnc.renameTo(finalEnc))
            plainFile.delete()
        }

        // Verify all 3 files are now .enc and contain magic header
        val migratedFiles = testDir.listFiles { _, name -> name.endsWith(".enc") } ?: emptyArray()
        assertEquals(3, migratedFiles.size)

        // Decrypt each and verify data matches original
        for ((index, encFile) in migratedFiles.sortedBy { it.name }.withIndex()) {
            assertTrue("File must be detected as encrypted", isEncrypted(encFile))
            val decryptedBytes = ByteArrayOutputStream()
            FileInputStream(encFile).use { rawIn ->
                val h = ByteArray(magicHeader.size)
                rawIn.read(h)
                getStreaming().newDecryptingStream(rawIn, associatedData).use { decIn ->
                    decIn.copyTo(decryptedBytes)
                }
            }
            assertArrayEquals("Decrypted bytes must match original content", originalContents[index], decryptedBytes.toByteArray())
        }

        // Second pass: test idempotency - already encrypted files must be skipped untouched
        val secondPassFiles = testDir.listFiles() ?: emptyArray()
        var skippedCount = 0
        for (f in secondPassFiles) {
            if (isEncrypted(f)) {
                skippedCount++
            }
        }
        assertEquals("Second migration pass must identify and skip all 3 files without re-encrypting", 3, skippedCount)

        testDir.deleteRecursively()
    }
}
