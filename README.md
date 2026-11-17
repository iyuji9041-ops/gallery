# Secure Gallery (VaultCore)

An enterprise-grade, offline-first Android Gallery application featuring a Bento-styled design, floating pill navigation bars, and bank-grade **VaultCore** seekable streaming encryption.

---

## Key Features

- **Floating Pill Navigation**: Sleek floating capsule navigation with true edge-to-edge transparency for both the main navigation tabs and full-screen image viewer actions.
- **VaultCore Streaming AEAD Encryption**: File-at-rest encryption utilizing Google Tink's `AesGcmHkdfStreaming` (AES-256-GCM in 64KB seekable chunks) with hardware-backed Android KeyStore key wrapping (StrongBox TEE).
- **On-Demand Decryption**: Zero plaintext written to disk or cache; images and large 4K videos stream and decrypt directly into memory.
- **Strict Privacy**: Zero `INTERNET` permission in `AndroidManifest.xml` and Android cloud backups disabled across all domains.
- **Modern Architecture**: Jetpack Compose, Kotlin Coroutines & Flow, AndroidX Media3 / ExoPlayer, Coil 3 image loading, and Room database with non-destructive migrations.

---

## Project Structure & Architecture

```
swift-hopper/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml          # Offline privacy (no INTERNET permission)
│   │   │   ├── res/                         # Vector drawables, colors, adaptive launcher icons
│   │   │   └── kotlin/com/photo/gallery/
│   │   │       ├── GalleryApplication.kt    # Coil ImageLoader & crypto manager startup
│   │   │       ├── MainActivity.kt          # Edge-to-edge entry point & dynamic FLAG_SECURE
│   │   │       ├── data/
│   │   │       │   ├── model/               # Domain data classes (MediaItem, AlbumItem)
│   │   │       │   ├── local/
│   │   │       │   │   ├── model/           # Room Database entities & AppDatabase configuration
│   │   │       │   │   └── dao/             # SecurityDao for locked and hidden media records
│   │   │       │   └── repository/
│   │   │       │       ├── MediaStoreFetcher.kt     # Live MediaStore query & ContentObserver
│   │   │       │       ├── SecurityRepository.kt    # Vault operations & deletion lock manager
│   │   │       │       └── MediaDeletionHandler.kt  # Scoped Storage delete consent (API 30+)
│   │   │       ├── security/
│   │   │       │   ├── crypto/
│   │   │       │   │   ├── VaultCryptoManager.kt    # Tink AEAD streaming & Android KeyStore
│   │   │       │   │   ├── EncryptedMedia3DataSource.kt # Seekable video stream for ExoPlayer
│   │   │       │   │   ├── EncryptedVaultFetcher.kt # Coil 3 on-demand thumbnail fetcher
│   │   │       │   │   └── EncryptedVaultKeyer.kt   # Memory cache keyer for encrypted files
│   │   │       │   └── VaultManager.kt              # Move to vault, restore, checksum verification
│   │   │       ├── ui/
│   │   │       │   ├── components/          # Reusable Bento dialogs, headers, and sheets
│   │   │       │   ├── screens/
│   │   │       │   │   ├── GalleryAppRoot.kt        # Root scaffold, back navigation, floating nav host
│   │   │       │   │   ├── PillNavigationBar.kt     # Floating capsule bottom navigation bar
│   │   │       │   │   ├── MainGridScreen.kt        # 3-column Bento media timeline & hero card
│   │   │       │   │   ├── ImageViewerScreen.kt     # Pager viewer, pinch-zoom & floating pill action bar
│   │   │       │   │   ├── AlbumScreen.kt           # 3-column album folder grid
│   │   │       │   │   ├── HiddenVaultScreen.kt     # Hidden vault grid, unlock PIN/Biometric modal
│   │   │       │   │   └── SettingsScreen.kt        # Security settings, PIN setup, auto-lock
│   │   │       │   ├── theme/               # Bento color palette, shapes, and typography
│   │   │       │   └── viewmodel/           # GalleryViewModel & unidirectional UI state
│   │   │       └── util/
│   │   │           └── GalleryLog.kt        # BuildConfig.DEBUG gated secure logging wrapper
│   │   └── test/                            # Unit tests for Tink AEAD, migrations, and checksums
│   ├── schemas/                             # Versioned Room database schemas (1.json, 2.json)
│   └── build.gradle.kts                     # App Gradle build configuration (Compose, Room, KSP)
├── .github/workflows/
│   └── build-apk.yml                        # GitHub Actions CI workflow (Tests, Lint, APK builds & releases)
├── CHANGELOG.md                             # Version history and release notes
└── settings.gradle.kts                      # Gradle settings & dependency repositories
```

---

## How to Build the Project

### Building with GitHub Actions (Recommended)
Every push to `main` triggers `.github/workflows/build-apk.yml`:
- Runs all unit tests (`RoomDatabaseMigrationTest`, `VaultCryptoTest`, `VaultManagerFunctionalTest`, etc.)
- Builds optimized `app-debug.apk` and `app-release.apk` (R8 minified)
- Executes Android Lint
- Publishes a GitHub Release with download links for APK assets.

To trigger manually via the GitHub CLI:
```bash
gh workflow run "Build and Release Android APK"
```

### Local Development Build
If building locally with Gradle:
```bash
# Run Unit Tests
./gradlew test

# Assemble Debug APK
./gradlew assembleDebug

# Assemble Release APK
./gradlew assembleRelease
```

---

## Making Changes & Contributing

1. **Customizing the UI / Theme**:
   - Colors and styles are defined in `app/src/main/kotlin/com/photo/gallery/ui/theme/Color.kt` and `Theme.kt`.
   - Navigation bars (`PillNavigationBar.kt` and `ImageViewerScreen.kt`) use glassmorphic elevation (`BentoSurface.copy(alpha = 0.95f)`) and `CircleShape` capsules.
2. **Database Schema Changes**:
   - When modifying `HiddenMediaEntity` or `LockedMediaEntity`, increment `version` in `AppDatabase.kt`.
   - Never use `fallbackToDestructiveMigration()`. Write explicit `Migration(N, N+1)` objects to guarantee zero user data loss.
   - Room exports schemas to `app/schemas/` via KSP.
3. **Crypto & Vault Changes**:
   - Master key operations reside in `VaultCryptoManager.kt`.
   - When using Android KeyStore in `Cipher.ENCRYPT_MODE`, always use system-generated IVs (`cipher.init(Cipher.ENCRYPT_MODE, kek)` without IV) and retrieve the IV via `cipher.iv`.
