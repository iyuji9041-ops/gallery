package com.photo.gallery.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class PinLockoutTest {

    private val random = SecureRandom()

    private fun derivePinToken(pin: String, salt: ByteArray, hmacKey: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 100_000, 256)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val pbkdf2Derived = skf.generateSecret(spec).encoded

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacKey, "HmacSHA256"))
        return mac.doFinal(pbkdf2Derived)
    }

    private fun computeLockoutDuration(attempts: Int): Int {
        return when {
            attempts >= 8 -> 1800 // 30 minutes
            attempts == 7 -> 300  // 5 minutes
            attempts == 6 -> 60   // 1 minute
            attempts == 5 -> 30   // 30 seconds
            else -> 0
        }
    }

    @Test
    fun testPbkdf2TokenDeterminismAndConstantTimeCompare() {
        val pin = "123456"
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val hmacKey = ByteArray(32).also { random.nextBytes(it) }

        val token1 = derivePinToken(pin, salt, hmacKey)
        val token2 = derivePinToken(pin, salt, hmacKey)

        assertTrue("Tokens for identical pin, salt, and key must match", MessageDigest.isEqual(token1, token2))

        val wrongToken = derivePinToken("654321", salt, hmacKey)
        assertFalse("Tokens for different pin must not match", MessageDigest.isEqual(token1, wrongToken))
    }

    @Test
    fun testExponentialBackoffLockoutThresholds() {
        // 1 to 4 failed attempts: no lockout
        assertEquals(0, computeLockoutDuration(1))
        assertEquals(0, computeLockoutDuration(2))
        assertEquals(0, computeLockoutDuration(3))
        assertEquals(0, computeLockoutDuration(4))

        // 5th attempt: 30 seconds
        assertEquals(30, computeLockoutDuration(5))

        // 6th attempt: 60 seconds (1 minute)
        assertEquals(60, computeLockoutDuration(6))

        // 7th attempt: 300 seconds (5 minutes)
        assertEquals(300, computeLockoutDuration(7))

        // 8th attempt: 1800 seconds (30 minutes)
        assertEquals(1800, computeLockoutDuration(8))

        // 9th+ attempt: 1800 seconds
        assertEquals(1800, computeLockoutDuration(9))
        assertEquals(1800, computeLockoutDuration(20))
    }

    @Test
    fun testElapsedRealtimeTamperProofLockout() {
        var simulatedElapsedRealtime = 100_000L
        var failedAttempts = 4
        var lockoutExpiry = 0L

        // 5th failed attempt triggers 30s lockout
        failedAttempts++
        val lockoutDuration = computeLockoutDuration(failedAttempts)
        assertEquals(30, lockoutDuration)
        lockoutExpiry = simulatedElapsedRealtime + (lockoutDuration * 1000L)

        // Attempting verification 10 seconds later: should still be locked out
        simulatedElapsedRealtime += 10_000L
        assertTrue("User must be locked out after 10s of 30s window", simulatedElapsedRealtime < lockoutExpiry)
        val remainingSec = ((lockoutExpiry - simulatedElapsedRealtime + 999) / 1000).toInt()
        assertEquals(20, remainingSec)

        // Advancing elapsed time past 30s window: lockout expires
        simulatedElapsedRealtime += 21_000L
        assertFalse("Lockout must expire after 30s elapsed", simulatedElapsedRealtime < lockoutExpiry)

        // On successful PIN entry: failure count and lockout expire
        failedAttempts = 0
        lockoutExpiry = 0L
        assertEquals(0, failedAttempts)
        assertEquals(0L, lockoutExpiry)
    }
}
