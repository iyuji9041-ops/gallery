# VaultCore Security Model & Architecture (Phase 2)

## 1. Overview
The Gallery application implements **VaultCore**, a defense-in-depth security architecture designed to safeguard private media, prevent accidental or unauthorized deletion, and secure files at rest using authenticated streaming cryptography.

---

## 2. Cryptographic Architecture

### 2.1 Encryption at Rest (Tink Streaming AEAD)
- **Algorithm**: `AesGcmHkdfStreaming` (Tink Subtle streaming AEAD).
- **Cipher Suite**: AES-256 in Galois/Counter Mode (GCM) with HMAC-SHA256 Key Derivation Function (HKDF).
- **Segment Size**: 64 KB (65,536 bytes) chunks. Each segment is individually authenticated with a 128-bit authentication tag and counter-based nonce, providing strong protection against block-reordering and chunk-swapping attacks.
- **Header**: Each vault file begins with an 8-byte magic identifier (`VLT1`) followed by the Tink streaming AEAD header (salt, segment count).
- **Random Access & Seeking**: Supported via `SeekableByteChannel` decrypters, allowing Media3 ExoPlayer to seek arbitrary video positions without buffering whole files or saving plaintext to disk.

### 2.2 Key Hierarchy & Keystore Binding
```
                    +------------------------------------+
                    | Android Keystore (StrongBox / TEE) |
                    +------------------------------------+
                                      |
                       +--------------+--------------+
                       |                             |
                       v                             v
           [gallery_vault_kek]            [gallery_vault_biometric_kek]
          (Hardware AES-256-GCM)          (Auth-per-use, StrongBox)
                       |                             |
                       +--------------+--------------+
                                      |
                                      v
                         [256-bit Vault Master Key]
                             (Random per install)
                                      |
                                      v
                         [AesGcmHkdfStreaming AEAD]
                                      |
                                      v
                             [Encrypted Vault Files]
```
- **Master Key**: A random 256-bit key generated per installation using `SecureRandom`. It is never hardcoded.
- **Hardware Wrapping**:
  - The master key is wrapped using an Android Keystore key (`gallery_vault_kek`) with StrongBox backing where available (fallback to default hardware TEE).
  - When Biometrics are enabled, the master key is also wrapped using `gallery_vault_biometric_kek` (`setUserAuthenticationRequired(true)`, `AUTH_BIOMETRIC_STRONG`, `setInvalidatedByBiometricEnrollment(true)`).
- **Volatile In-Memory Lifecycle**:
  - Decrypted master key material lives strictly in memory while the vault is actively unlocked.
  - On vault lock, background timeout, tab change, or application termination, all master key byte arrays are zeroed out (`Arrays.fill(bytes, 0)`).

### 2.3 Master PIN Protection & Attempt Limiting
- **KDF**: `PBKDF2WithHmacSHA256` with **100,000 iterations** and random 16-byte salt.
- **Hardware Keystore Binding**: The derived PBKDF2 token is signed using an Android Keystore HMAC-SHA256 key (`gallery_pin_mac_key`). Offline brute-force attacks against stolen application preferences cannot compute valid tokens without the physical device's Keystore.
- **Constant-Time Comparison**: Evaluated using `MessageDigest.isEqual` to eliminate timing side-channel attacks.
- **Exponential Backoff Lockout**:
  - Attempts 1–4: Standard retry prompt.
  - Attempt 5: 30-second lockout.
  - Attempt 6: 1-minute lockout.
  - Attempt 7: 5-minute lockout.
  - Attempts 8+: 30-minute lockout.
  - Lockout tracking uses `SystemClock.elapsedRealtime()`, rendering attacks that manipulate the device wall clock ineffective.

### 2.4 Vault Secret Search-Bar Code
- Stored exclusively as a PBKDF2 cryptographic hash with random salt.
- Never displayed or retrievable in plaintext.
- The onboarding tutorial prompts users to configure their own custom secret phrase.

---

## 3. Data Protection & Leak Prevention

| Threat Vector | Mitigation |
|---|---|
| **Cloud / Local Backups** | `allowBackup="false"`, with explicit `backup_rules.xml` and `data_extraction_rules.xml` excluding all domains (`root`, `file`, `database`, `sharedpref`, `external`). |
| **External File Managers / USB** | Vault files reside exclusively in internal `context.filesDir/vault`. External storage (`getExternalFilesDir`) is completely disabled. |
| **Plaintext Disk / Cache Leaks** | Image thumbnails and video streams decode directly into memory via custom Coil `EncryptedVaultFetcher` and Media3 `EncryptedMedia3DataSource`. Disk caching for vault items is disabled. |
| **Memory Persistence** | Memory cache is flushed (`imageLoader.memoryCache.clear()`) and master key bytes are zeroed immediately upon locking. |
| **Screenshots & Recents Previews** | `WindowManager.LayoutParams.FLAG_SECURE` is dynamically applied whenever the vault or a locked photo is displayed, with an optional toggle for app-wide protection. |
| **Background Exposure** | `ProcessLifecycleOwner` observer automatically locks the vault immediately or after configurable timeouts (30s / 1 min). |

---

## 4. Threat Model & Boundaries

### 4.1 What IS Protected
1. **Device Theft / Lost Device (Vault Locked)**:
   - All vault files are encrypted with AES-256-GCM. Without the device Keystore and PIN/biometric release, data cannot be extracted.
2. **Offline Data Extraction**:
   - Extracting the database or DataStore preferences from disk does not allow brute-forcing the PIN due to hardware Keystore HMAC binding.
3. **Malicious Installed Applications**:
   - Standard sandbox boundaries and internal storage isolation prevent non-root apps from reading files in `context.filesDir/vault`.
4. **Casual Screen Snooping & Recents**:
   - `FLAG_SECURE` prevents Android system snapshots in the app switcher and blocks screen recording / screenshot tools.
5. **Unauthorized Deletions Inside App**:
   - Deletion Lock requires PIN or biometric verification before any protected media or albums can be deleted.

### 4.2 What IS NOT Protected (Out of Scope)
1. **Rooted Device with Vault Unlocked in Memory**:
   - A process running as `root` or an attached debugger (e.g. Frida) on a rooted device can inspect the application's volatile heap memory while the vault is actively unlocked and read decrypted frames or key material.
2. **External File Deletion Outside the Gallery App**:
   - Deletion Lock protects files deleted *within* this gallery application. Other apps with system-level storage permissions (such as Google Files or desktop MTP file transfer) can delete files directly from shared storage (`/sdcard/DCIM/`).
3. **Hardware-Level Compromise / Key Extraction**:
   - Devices with compromised TEE hardware or unpatched bootloader vulnerabilities that allow raw memory dumps of secure hardware enclaves.
