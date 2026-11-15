package com.photo.gallery.data.repository

import android.net.Uri
import com.photo.gallery.data.local.SecurityPreferences
import com.photo.gallery.data.local.SecuritySettings
import com.photo.gallery.data.local.dao.SecurityDao
import com.photo.gallery.data.local.model.HiddenMediaEntity
import com.photo.gallery.data.local.model.LockedMediaEntity
import com.photo.gallery.data.model.MediaItem
import com.photo.gallery.data.vault.VaultManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class SecurityRepository(
    private val securityDao: SecurityDao,
    private val securityPreferences: SecurityPreferences,
    private val vaultManager: VaultManager
) {
    val allLockedMedia: Flow<List<LockedMediaEntity>> = securityDao.getAllLockedMedia()
    val lockedAlbumNames: Flow<List<String>> = securityDao.getLockedAlbumNames()
    val allHiddenMedia: Flow<List<HiddenMediaEntity>> = securityDao.getAllHiddenMedia()
    val securitySettings: Flow<SecuritySettings> = securityPreferences.securitySettings

    suspend fun isMediaLocked(path: String, albumName: String): Boolean {
        val settings = securitySettings.first()
        if (!settings.deletionLockEnabled) return false

        // Check if individual photo is locked
        val lockedPhoto = securityDao.getLockedByPath(path)
        if (lockedPhoto != null && lockedPhoto.isLocked) return true

        // Check if parent album is locked
        val lockedAlbum = securityDao.getLockedAlbum(albumName)
        return lockedAlbum != null && lockedAlbum.isLocked
    }

    suspend fun togglePhotoLock(item: MediaItem): Boolean {
        val existing = securityDao.getLockedByPath(item.path)
        return if (existing != null && existing.isLocked) {
            securityDao.deleteLockedByPath(item.path)
            false
        } else {
            val entity = LockedMediaEntity(
                mediaPath = item.path,
                mediaUriString = item.uri.toString(),
                albumName = item.albumName,
                isLocked = true,
                isAlbumLocked = false
            )
            securityDao.insertOrUpdateLocked(entity)
            true
        }
    }

    suspend fun toggleAlbumLock(albumName: String): Boolean {
        val existing = securityDao.getLockedAlbum(albumName)
        return if (existing != null && existing.isLocked) {
            securityDao.unlockAlbum(albumName)
            false
        } else {
            val entity = LockedMediaEntity(
                mediaPath = "album:$albumName",
                mediaUriString = "",
                albumName = albumName,
                isLocked = true,
                isAlbumLocked = true
            )
            securityDao.insertOrUpdateLocked(entity)
            true
        }
    }

    suspend fun moveToVault(
        uri: Uri,
        originalPath: String,
        displayName: String,
        mimeType: String,
        size: Long,
        dateAdded: Long
    ): Result<HiddenMediaEntity> {
        return vaultManager.moveToVault(uri, originalPath, displayName, mimeType, size, dateAdded)
    }

    suspend fun restoreFromVault(hiddenItem: HiddenMediaEntity): Result<Uri> {
        return vaultManager.restoreFromVault(hiddenItem)
    }

    suspend fun deletePermanentlyFromVault(hiddenItem: HiddenMediaEntity): Boolean {
        return vaultManager.deletePermanentlyFromVault(hiddenItem)
    }

    suspend fun setPin(pin: String) {
        securityPreferences.setPin(pin)
    }

    suspend fun verifyPin(pin: String): Boolean {
        return securityPreferences.verifyPin(pin)
    }

    suspend fun setBiometricsEnabled(enabled: Boolean) {
        securityPreferences.setBiometricsEnabled(enabled)
    }

    suspend fun setDeletionLockEnabled(enabled: Boolean) {
        securityPreferences.setDeletionLockEnabled(enabled)
    }

    suspend fun setVaultSecretCode(code: String) {
        securityPreferences.setVaultSecretCode(code)
    }

    suspend fun markVaultTutorialSeen() {
        securityPreferences.markVaultTutorialSeen()
    }
}
