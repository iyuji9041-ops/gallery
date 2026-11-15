package com.photo.gallery.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.photo.gallery.data.local.model.HiddenMediaEntity
import com.photo.gallery.data.local.model.LockedMediaEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Deletion Lock and Hidden Vault states.
 */
@Dao
interface SecurityDao {

    // --- Deletion Lock Queries ---

    @Query("SELECT * FROM locked_media WHERE isLocked = 1")
    fun getAllLockedMedia(): Flow<List<LockedMediaEntity>>

    @Query("SELECT * FROM locked_media WHERE mediaPath = :path LIMIT 1")
    suspend fun getLockedByPath(path: String): LockedMediaEntity?

    @Query("SELECT * FROM locked_media WHERE albumName = :albumName AND isAlbumLocked = 1 LIMIT 1")
    suspend fun getLockedAlbum(albumName: String): LockedMediaEntity?

    @Query("SELECT DISTINCT albumName FROM locked_media WHERE isAlbumLocked = 1 AND isLocked = 1")
    fun getLockedAlbumNames(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateLocked(lockedMedia: LockedMediaEntity)

    @Query("DELETE FROM locked_media WHERE mediaPath = :path")
    suspend fun deleteLockedByPath(path: String)

    @Query("DELETE FROM locked_media WHERE albumName = :albumName AND isAlbumLocked = 1")
    suspend fun unlockAlbum(albumName: String)

    // --- Hidden Vault Queries ---

    @Query("SELECT * FROM hidden_media ORDER BY dateHidden DESC")
    fun getAllHiddenMedia(): Flow<List<HiddenMediaEntity>>

    @Query("SELECT * FROM hidden_media WHERE id = :id LIMIT 1")
    suspend fun getHiddenById(id: Long): HiddenMediaEntity?

    // IGNORE (not REPLACE) so that whichever of the two racing inserts (the app's own
    // moveToVault() write, or VaultMonitorService's FileObserver reacting to that same
    // write) gets there FIRST wins and keeps its original id/dateHidden - the second,
    // now-redundant insert is silently dropped by the unique index on vaultPath instead
    // of creating a duplicate row.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHiddenMedia(hiddenMedia: HiddenMediaEntity): Long

    @Query("DELETE FROM hidden_media WHERE id = :id")
    suspend fun deleteHiddenMediaById(id: Long)

    @Query("SELECT * FROM hidden_media WHERE vaultPath = :path LIMIT 1")
    suspend fun getHiddenByVaultPath(path: String): HiddenMediaEntity?

    @Query("DELETE FROM hidden_media WHERE vaultPath = :path")
    suspend fun deleteHiddenByVaultPath(path: String)

    @Query("SELECT * FROM hidden_media")
    suspend fun getAllHiddenList(): List<HiddenMediaEntity>
}
