package com.photo.gallery.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.photo.gallery.data.local.dao.SecurityDao
import com.photo.gallery.data.local.model.HiddenMediaEntity
import com.photo.gallery.data.local.model.LockedMediaEntity

/**
 * Room Database for Secure Gallery storing Deletion Lock and Hidden Vault states.
 */
@Database(
    entities = [LockedMediaEntity::class, HiddenMediaEntity::class],
    version = 2, // bumped: HiddenMediaEntity gained a unique index on vaultPath (dup-hide fix)
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun securityDao(): SecurityDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "secure_gallery.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
