package com.photo.gallery.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.photo.gallery.data.local.dao.SecurityDao
import com.photo.gallery.data.local.model.HiddenMediaEntity
import com.photo.gallery.data.local.model.LockedMediaEntity

/**
 * Room Database for Secure Gallery storing Deletion Lock and Hidden Vault states.
 * Uses explicit non-destructive migrations to protect all user data across updates.
 */
@Database(
    entities = [LockedMediaEntity::class, HiddenMediaEntity::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun securityDao(): SecurityDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Migration 1 -> 2:
         * Add unique index on `vaultPath` in `hidden_media` to prevent duplicate row races.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_hidden_media_vaultPath` ON `hidden_media` (`vaultPath`)")
            }
        }

        /**
         * Migration 2 -> 3:
         * Add encryption and metadata preservation columns:
         * - isEncrypted: Boolean flag (1=encrypted via Tink Streaming AEAD)
         * - relativeFolder: Original relative directory (e.g. DCIM/Camera)
         * - dateTaken: Preserved capture timestamp
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `hidden_media` ADD COLUMN `isEncrypted` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `hidden_media` ADD COLUMN `relativeFolder` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `hidden_media` ADD COLUMN `dateTaken` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "secure_gallery.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
