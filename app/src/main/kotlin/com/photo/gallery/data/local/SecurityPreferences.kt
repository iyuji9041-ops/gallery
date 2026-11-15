package com.photo.gallery.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gallery_security_prefs")

data class SecuritySettings(
    val hasPinSet: Boolean = false,
    val biometricsEnabled: Boolean = true,
    val deletionLockEnabled: Boolean = true,
    val vaultSecretCode: String = "\\vault",
    val hasSeenVaultTutorial: Boolean = false
)

class SecurityPreferences(private val context: Context) {

    companion object {
        private val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
        private val KEY_PIN_SALT = stringPreferencesKey("pin_salt")
        private val KEY_BIOMETRICS_ENABLED = booleanPreferencesKey("biometrics_enabled")
        private val KEY_DELETION_LOCK_ENABLED = booleanPreferencesKey("deletion_lock_enabled")
        // Vault access is no longer a labeled bottom-nav tab (so a casual glance at the
        // app doesn't reveal a hidden vault exists) - instead, typing this exact phrase
        // into the search bar opens it. Stored in plaintext (not hashed) because it is
        // NOT the actual security boundary - it's just a navigation trigger. The vault
        // still requires the real PIN/biometric unlock afterwards regardless of how the
        // user navigated to it, so this code being readable on-device isn't a security
        // weakening; it only controls discoverability, not access.
        private val KEY_VAULT_SECRET_CODE = stringPreferencesKey("vault_secret_code")
        private val KEY_HAS_SEEN_VAULT_TUTORIAL = booleanPreferencesKey("has_seen_vault_tutorial")
        const val DEFAULT_VAULT_SECRET_CODE = "\\vault"
    }

    val securitySettings: Flow<SecuritySettings> = context.dataStore.data.map { prefs ->
        val hash = prefs[KEY_PIN_HASH]
        SecuritySettings(
            hasPinSet = !hash.isNullOrEmpty(),
            biometricsEnabled = prefs[KEY_BIOMETRICS_ENABLED] ?: true,
            deletionLockEnabled = prefs[KEY_DELETION_LOCK_ENABLED] ?: true,
            vaultSecretCode = prefs[KEY_VAULT_SECRET_CODE] ?: DEFAULT_VAULT_SECRET_CODE,
            hasSeenVaultTutorial = prefs[KEY_HAS_SEEN_VAULT_TUTORIAL] ?: false
        )
    }

    suspend fun setVaultSecretCode(code: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_VAULT_SECRET_CODE] = code
        }
    }

    suspend fun markVaultTutorialSeen() {
        context.dataStore.edit { prefs ->
            prefs[KEY_HAS_SEEN_VAULT_TUTORIAL] = true
        }
    }

    suspend fun setPin(newPin: String) {
        val salt = generateSalt()
        val hash = hashPin(newPin, salt)
        context.dataStore.edit { prefs ->
            prefs[KEY_PIN_HASH] = hash
            prefs[KEY_PIN_SALT] = salt
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        var isValid = false
        context.dataStore.edit { prefs ->
            val storedHash = prefs[KEY_PIN_HASH]
            val storedSalt = prefs[KEY_PIN_SALT]
            if (storedHash.isNullOrEmpty() || storedSalt == null) {
                // If no PIN has been set yet, consider "0000" or empty as default or setup needed
                isValid = false
            } else {
                val computedHash = hashPin(pin, storedSalt)
                isValid = (storedHash == computedHash)
            }
        }
        return isValid
    }

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

    private fun generateSalt(): String {
        val random = SecureRandom()
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashPin(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val input = "$salt:$pin"
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
