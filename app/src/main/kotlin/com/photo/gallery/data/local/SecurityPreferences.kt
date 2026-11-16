package com.photo.gallery.data.local

import android.content.Context
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gallery_security_prefs")

enum class AutoLockDelay(val seconds: Int, val displayName: String) {
    IMMEDIATELY(0, "Immediately"),
    SECONDS_30(30, "30 Seconds"),
    MINUTES_1(60, "1 Minute");

    companion object {
        fun fromSeconds(seconds: Int): AutoLockDelay =
            values().find { it.seconds == seconds } ?: IMMEDIATELY
    }
}

sealed class PinVerifyResult {
    object Success : PinVerifyResult()
    data class LockedOut(val secondsRemaining: Int) : PinVerifyResult()
    data class Incorrect(val attemptsRemainingBeforeLockout: Int, val lockoutDurationSeconds: Int = 0) : PinVerifyResult()
}

data class SecuritySettings(
    val hasPinSet: Boolean = false,
    val pinLength: Int = 4,
    val isPinUpgradeAvailable: Boolean = false,
    val biometricsEnabled: Boolean = true,
    val deletionLockEnabled: Boolean = true,
    val hasSecretCodeSet: Boolean = false,
    val hasSeenVaultTutorial: Boolean = false,
    val autoLockDelay: AutoLockDelay = AutoLockDelay.IMMEDIATELY,
    val secureModeAppWide: Boolean = false,
    val isLockedOut: Boolean = false,
    val lockoutSecondsRemaining: Int = 0
)

class SecurityPreferences(private val context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val PIN_HMAC_KEY_ALIAS = "gallery_pin_mac_key"
        private const val PBKDF2_ITERATIONS = 100_000
        private const val PBKDF2_KEY_LENGTH = 256
        private const val LOCKOUT_THRESHOLD_1 = 5

        private val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
        private val KEY_PIN_SALT = stringPreferencesKey("pin_salt")
        private val KEY_PIN_KDF_VERSION = intPreferencesKey("pin_kdf_version")
        private val KEY_PIN_LENGTH = intPreferencesKey("pin_length")
        private val KEY_FAILED_PIN_ATTEMPTS = intPreferencesKey("failed_pin_attempts")
        private val KEY_LOCKOUT_UNTIL_ELAPSED_MS = longPreferencesKey("lockout_until_elapsed_ms")

        private val KEY_BIOMETRICS_ENABLED = booleanPreferencesKey("biometrics_enabled")
        private val KEY_DELETION_LOCK_ENABLED = booleanPreferencesKey("deletion_lock_enabled")
        private val KEY_SECURE_MODE_APP_WIDE = booleanPreferencesKey("secure_mode_app_wide")
        private val KEY_AUTO_LOCK_DELAY_SECONDS = intPreferencesKey("auto_lock_delay_seconds")

        private val KEY_VAULT_CODE_HASH = stringPreferencesKey("vault_code_hash")
        private val KEY_VAULT_CODE_SALT = stringPreferencesKey("vault_code_salt")
        private val KEY_HAS_SEEN_VAULT_TUTORIAL = booleanPreferencesKey("has_seen_vault_tutorial")
    }

    private val secureRandom = SecureRandom()

    val securitySettings: Flow<SecuritySettings> = context.dataStore.data.map { prefs ->
        val hash = prefs[KEY_PIN_HASH]
        val pinLen = prefs[KEY_PIN_LENGTH] ?: 4
        val codeHash = prefs[KEY_VAULT_CODE_HASH]
        val lockoutUntil = prefs[KEY_LOCKOUT_UNTIL_ELAPSED_MS] ?: 0L
        val now = SystemClock.elapsedRealtime()
        val isLockedOut = now < lockoutUntil
        val secondsRemaining = if (isLockedOut) ((lockoutUntil - now + 999) / 1000).toInt() else 0
        val delaySec = prefs[KEY_AUTO_LOCK_DELAY_SECONDS] ?: AutoLockDelay.IMMEDIATELY.seconds

        SecuritySettings(
            hasPinSet = !hash.isNullOrEmpty(),
            pinLength = pinLen,
            isPinUpgradeAvailable = !hash.isNullOrEmpty() && pinLen < 6,
            biometricsEnabled = prefs[KEY_BIOMETRICS_ENABLED] ?: true,
            deletionLockEnabled = prefs[KEY_DELETION_LOCK_ENABLED] ?: true,
            hasSecretCodeSet = !codeHash.isNullOrEmpty(),
            hasSeenVaultTutorial = prefs[KEY_HAS_SEEN_VAULT_TUTORIAL] ?: false,
            autoLockDelay = AutoLockDelay.fromSeconds(delaySec),
            secureModeAppWide = prefs[KEY_SECURE_MODE_APP_WIDE] ?: false,
            isLockedOut = isLockedOut,
            lockoutSecondsRemaining = secondsRemaining
        )
    }

    // --- Hardware Keystore Binding for PIN ---

    private fun getOrCreatePinHmacKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(PIN_HMAC_KEY_ALIAS)) {
                val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
                val builder = KeyGenParameterSpec.Builder(
                    PIN_HMAC_KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN
                )
                keyGen.init(builder.build())
                keyGen.generateKey()
            }
            (keyStore.getEntry(PIN_HMAC_KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        } catch (e: Exception) {
            // Software fallback for non-Android environments (unit tests)
            getFallbackSoftwareHmacKey()
        }
    }

    private var fallbackHmacKey: SecretKey? = null

    private fun getFallbackSoftwareHmacKey(): SecretKey {
        fallbackHmacKey?.let { return it }
        val keyFile = File(context.filesDir, "vault_keys/pin_hmac.sw")
        val keyBytes = if (keyFile.exists() && keyFile.length() == 32L) {
            keyFile.readBytes()
        } else {
            val b = ByteArray(32)
            secureRandom.nextBytes(b)
            keyFile.parentFile?.mkdirs()
            keyFile.writeBytes(b)
            b
        }
        val key = SecretKeySpec(keyBytes, "HmacSHA256")
        fallbackHmacKey = key
        return key
    }

    private fun derivePinToken(pin: String, salt: ByteArray): ByteArray {
        // 1. Slow KDF: PBKDF2WithHmacSHA256 with 100,000 iterations
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val pbkdf2Derived = skf.generateSecret(spec).encoded

        // 2. Hardware Keystore binding: HMAC-SHA256 with AndroidKeyStore key
        val hmacKey = getOrCreatePinHmacKey()
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(hmacKey)
        return mac.doFinal(pbkdf2Derived)
    }

    // --- PIN Management ---

    suspend fun setPin(newPin: String) {
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val derived = derivePinToken(newPin, salt)
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        val tokenHex = derived.joinToString("") { "%02x".format(it) }

        context.dataStore.edit { prefs ->
            prefs[KEY_PIN_HASH] = tokenHex
            prefs[KEY_PIN_SALT] = saltHex
            prefs[KEY_PIN_KDF_VERSION] = 2 // version 2: PBKDF2 100k + Keystore HMAC
            prefs[KEY_PIN_LENGTH] = newPin.length
            prefs[KEY_FAILED_PIN_ATTEMPTS] = 0
            prefs[KEY_LOCKOUT_UNTIL_ELAPSED_MS] = 0L
        }
    }

    /**
     * Verifies PIN with constant-time comparison, slow KDF, Keystore binding,
     * and exponential backoff lockout.
     * Uses data.first() for reads instead of dataStore.edit.
     */
    suspend fun verifyPin(pin: String): PinVerifyResult {
        val prefs = context.dataStore.data.first()
        val storedHash = prefs[KEY_PIN_HASH]
        val storedSaltHex = prefs[KEY_PIN_SALT]
        if (storedHash.isNullOrEmpty() || storedSaltHex.isNullOrEmpty()) {
            return PinVerifyResult.Incorrect(0)
        }

        val now = SystemClock.elapsedRealtime()
        val lockoutUntil = prefs[KEY_LOCKOUT_UNTIL_ELAPSED_MS] ?: 0L
        if (now < lockoutUntil) {
            val remainingSec = ((lockoutUntil - now + 999) / 1000).toInt()
            return PinVerifyResult.LockedOut(remainingSec)
        }

        val version = prefs[KEY_PIN_KDF_VERSION] ?: 1
        val isValid: Boolean

        if (version == 1) {
            // Legacy single-round salted SHA-256 fallback & upgrade
            val legacyHash = hashLegacySha256(pin, storedSaltHex)
            isValid = MessageDigest.isEqual(legacyHash.toByteArray(), storedHash.toByteArray())
            if (isValid) {
                // Transparently upgrade to PBKDF2 100k + Keystore binding
                setPin(pin)
                return PinVerifyResult.Success
            }
        } else {
            val saltBytes = hexToBytes(storedSaltHex)
            val computedToken = derivePinToken(pin, saltBytes)
            val storedTokenBytes = hexToBytes(storedHash)
            isValid = MessageDigest.isEqual(computedToken, storedTokenBytes)
        }

        if (isValid) {
            // Reset failure counter on success
            context.dataStore.edit { editPrefs ->
                editPrefs[KEY_FAILED_PIN_ATTEMPTS] = 0
                editPrefs[KEY_LOCKOUT_UNTIL_ELAPSED_MS] = 0L
            }
            return PinVerifyResult.Success
        } else {
            // Increment failure count and compute exponential lockout
            val currentFailures = (prefs[KEY_FAILED_PIN_ATTEMPTS] ?: 0) + 1
            val lockoutSec = when {
                currentFailures >= 8 -> 1800 // 30 minutes
                currentFailures == 7 -> 300  // 5 minutes
                currentFailures == 6 -> 60   // 1 minute
                currentFailures == 5 -> 30   // 30 seconds
                else -> 0
            }

            val lockoutExpiry = if (lockoutSec > 0) now + (lockoutSec * 1000L) else 0L

            context.dataStore.edit { editPrefs ->
                editPrefs[KEY_FAILED_PIN_ATTEMPTS] = currentFailures
                if (lockoutExpiry > 0L) {
                    editPrefs[KEY_LOCKOUT_UNTIL_ELAPSED_MS] = lockoutExpiry
                }
            }

            val attemptsLeft = maxOf(0, LOCKOUT_THRESHOLD_1 - currentFailures)
            return if (lockoutSec > 0) {
                PinVerifyResult.LockedOut(lockoutSec)
            } else {
                PinVerifyResult.Incorrect(attemptsLeft)
            }
        }
    }

    // --- Secret Code Management (Problem 9) ---

    suspend fun setVaultSecretCode(code: String) {
        val trimmed = code.trim()
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val hash = hashSecretCode(trimmed, salt)
        val saltHex = salt.joinToString("") { "%02x".format(it) }

        context.dataStore.edit { prefs ->
            prefs[KEY_VAULT_CODE_HASH] = hash
            prefs[KEY_VAULT_CODE_SALT] = saltHex
            prefs[KEY_HAS_SEEN_VAULT_TUTORIAL] = true
        }
    }

    suspend fun verifyVaultSecretCode(input: String): Boolean {
        val prefs = context.dataStore.data.first()
        val storedHash = prefs[KEY_VAULT_CODE_HASH] ?: return false
        val storedSaltHex = prefs[KEY_VAULT_CODE_SALT] ?: return false
        val salt = hexToBytes(storedSaltHex)
        val computedHash = hashSecretCode(input.trim(), salt)
        return MessageDigest.isEqual(computedHash.toByteArray(), storedHash.toByteArray())
    }

    private fun hashSecretCode(code: String, salt: ByteArray): String {
        val spec = PBEKeySpec(code.toCharArray(), salt, 10_000, 256)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derived = skf.generateSecret(spec).encoded
        return derived.joinToString("") { "%02x".format(it) }
    }

    suspend fun markVaultTutorialSeen() {
        context.dataStore.edit { prefs ->
            prefs[KEY_HAS_SEEN_VAULT_TUTORIAL] = true
        }
    }

    // --- Additional Security Settings ---

    suspend fun setBiometricsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BIOMETRICS_ENABLED] = enabled
        }
    }

    suspend fun setDeletionLockEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DELETION_LOCK_ENABLED] = enabled
        }
    }

    suspend fun setSecureModeAppWide(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SECURE_MODE_APP_WIDE] = enabled
        }
    }

    suspend fun setAutoLockDelay(delay: AutoLockDelay) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_LOCK_DELAY_SECONDS] = delay.seconds
        }
    }

    // --- Helpers ---

    private fun hashLegacySha256(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val input = "$salt:$pin"
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val result = ByteArray(hex.length / 2)
        for (i in result.indices) {
            val index = i * 2
            result[i] = hex.substring(index, index + 2).toInt(16).toByte()
        }
        return result
    }
}
