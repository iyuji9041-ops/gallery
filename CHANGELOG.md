# Changelog

All notable changes to Secure Gallery are documented in this file.

## [1.2.1] - Hotfix: Startup Crash on Android KeyStore Caller-Provided IV

### Fixed
- **App Startup Crash (`InvalidAlgorithmParameterException: Caller-provided IV not permitted`)**:
  - **Root Cause**: On real devices (such as Xiaomi/MIUI HyperOS), Android KeyStore enforces randomized encryption by default and throws `InvalidAlgorithmParameterException` if a caller-provided IV is passed to `Cipher.init(Cipher.ENCRYPT_MODE, kek, GCMParameterSpec(...))` when wrapping the random master key in `ensureMasterKeyInitialized()`.
  - **Fix**: Removed caller-provided IV for KeyStore encryption; initialized cipher via `cipher.init(Cipher.ENCRYPT_MODE, kek)` and retrieved the system-generated 12-byte IV via `cipher.iv`. Added graceful fallback to software key, defensive file length verification, and atomic file replacement via temporary files.
  - Added unit test `testKeystoreAesGcmRandomizedIvEncryptionRoundTrip` verifying randomized IV generation and subsequent decryption round-trip.

## [1.2.0] - Phase 3 & Phase 4: Functional Bug Fixes & Floating Pill Navigation Bar

### Added
- **Floating Pill Navigation Bar (`PillNavigationBar.kt`)**:
  - Replaced legacy `BentoNavigationBar` with a modern floating capsule navigation bar.
  - Floating design with elevated container, rounded pill border, subtle active indicator glow, and animated tab transitions.
  - Proper edge-to-edge layout with dynamic navigation bar insets and 96.dp bottom grid padding to ensure zero content overlap on 3-button navigation, gesture navigation, and large display/font sizes (font scale 1.3+).
  - Accessibility compliant: Added explicit `Role.Tab` semantics, content descriptions, and `selected` state for TalkBack.
  - Test tags retained: `gallery_bottom_nav`, `nav_tab_photos`, `nav_tab_albums`, `nav_tab_settings`.
  - Secret Vault access preserved: Vault navigation tab remains completely hidden from the nav bar, opening exclusively through secret passcode entry in the search bar.
- **Explicit Room Database Migrations (`MIGRATION_1_2` & `MIGRATION_2_3`)**:
  - Removed destructive fallback migration to guarantee zero data loss across upgrades.
  - Migration 1 -> 2: Added unique index `index_hidden_media_vaultPath` on `hidden_media.vaultPath`.
  - Migration 2 -> 3: Added `isEncrypted` (INTEGER NOT NULL DEFAULT 1), `relativeFolder` (TEXT DEFAULT NULL), and `dateTaken` (INTEGER NOT NULL DEFAULT 0).
  - Room schema export enabled with verified JSON schemas under `app/schemas/`.
  - Unit tests added in `RoomDatabaseMigrationTest.kt` verifying SQL schema statements.
- **Real-Time Media ContentObserver**:
  - Registered `ContentObserver` on `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` and `MediaStore.Video.Media.EXTERNAL_CONTENT_URI`.
  - 500ms coroutine debounce to prevent redundant queries on rapid camera burst captures.
  - Automatic gallery refresh on `ON_RESUME` lifecycle event.
  - Safe lifecycle unregistration in `ViewModel.onCleared()`.
- **Storage Permission UI States**:
  - Permission Denied UI state with clear explanation, "Grant Permission" button, and "Open Settings" intent launcher.
  - Android 14+ Partial Visual Media Access banner (`READ_MEDIA_VISUAL_USER_SELECTED`) with "Select More Photos" action.
  - Empty gallery state when permission is granted but device contains no media items.
- **Debug-Only Logging (`GalleryLog.kt`)**:
  - Replaced raw `android.util.Log` calls with `GalleryLog` wrapper checking `BuildConfig.DEBUG`.
  - Strips all log strings and logcat output from release builds.
  - Path sanitization to prevent leaking filenames, folder paths, or master keys in debug logs.

### Fixed
- **False "Hidden" Success (Scoped Storage Deletion Consent)**:
  - Fixed issue on Android 11+ where `contentResolver.delete(sourceUri)` failed silently under Scoped Storage, reporting files as hidden when originals remained in the public gallery.
  - Files are encrypted and verified with SHA-256 checksums before initiating deletion.
  - Requests user confirmation using `MediaStore.createDeleteRequest(IntentSender)` for single and bulk selections.
  - Only reports success after the original file is confirmed deleted; marks items as "copied, original still visible" if the user cancels or declines the dialog.
- **Duplicate Row Race Condition**:
  - Fixed duplicate rows caused by simultaneous `moveToVault` and `FileObserver` callbacks.
  - Unified database insertion path with `OnConflictStrategy.ABORT` and `-1L` row ID check.
  - Preserved metadata: `originalPath`, `displayName`, `relativeFolder`, `mimeType`, and `dateTaken`.
- **Video Restore Destination & Transactional Recovery**:
  - Fixed video restore incorrectly saving to `Pictures/` instead of `Movies/`.
  - Dynamic MIME detection routes images to `Pictures/Restored` and videos to `Movies/Restored`.
  - Proper `IS_PENDING = 1` toggle during file decryption stream, resetting to `IS_PENDING = 0` on completion.
  - Transaction rollback: MediaStore row is immediately cleaned up if decryption/write fails.
  - Vault copy is verified prior to deletion, preventing data loss.
- **Accurate Deletion Result Semantics**:
  - Rewritten `MediaDeletionHandler` and `deleteMedia` to return `DeletionResult.Success` only when deleted rows > 0 or system dialog is confirmed.
  - Graceful handling of `RESULT_CANCELED`, partial failures, and `SecurityException` across all Android versions.
- **Removed Fake Demo Photos**:
  - Completely purged `generateSampleMedia` mock generator.
  - Removed unused `android.permission.INTERNET` from `AndroidManifest.xml` to enforce offline-only privacy.
- **Predictive Back Navigation & Vault Auto-Lock**:
  - Navigating back from Albums, Settings, or Vault now returns directly to the Photos (Timeline) tab before exiting the application.
  - Exiting the Hidden Vault via back button or tab switch immediately triggers `lockVault()`, preventing sensitive media exposure in memory or app switcher.
- **UI & ExoPlayer Performance**:
  - Added stable keys and `contentType` to `LazyVerticalGrid` items to eliminate redundant recomposition.
  - Fixed hero card span to use `GridItemSpan(maxLineSpan)`.
  - Added thumbnail downscaling in Coil (`size(192.dp)`).
  - Bound ExoPlayer to pager active status (`LaunchedEffect(isActive)`) to pause audio/video playback when swiped away and full player release in `DisposableEffect`.

---

## [1.1.0] - Phase 2: Hidden Vault Security & Encryption at Rest

### Added
- **Tink Streaming AEAD Encryption**:
  - Implemented `AesGcmHkdfStreaming` (64KB chunks) for authenticated seekable encryption at rest for large media and videos.
  - Per-installation random 256-bit master key generated via `SecureRandom`.
  - Master key wrapped and secured using Android Keystore AES-256-GCM with hardware-backed StrongBox support.
- **On-Demand Decryption**:
  - Stream and in-memory decryption for video playback and image previews.
  - Zero unencrypted files written to persistent disk storage.
- **Vault Access Protection**:
  - Secret trigger in search bar to reveal vault authentication prompt.
  - PIN and Biometric unlock with rate limiting and exponential lockout backoff.
  - Deletion Lock protecting selected gallery items from accidental deletion.

---

## [1.0.0] - Phase 1: Build System Modernization & Gradle Stabilization

### Added
- Upgraded Gradle build system to AGP 8.8.0, Kotlin 2.0.21, and Compose Compiler Plugin.
- Android 15 (API 35) target support with backwards compatibility down to Android 7.0 (API 24).
- Offline dependency resolution and localized build caching.
