package com.photo.gallery.security

import androidx.sqlite.db.SupportSQLiteDatabase
import com.photo.gallery.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class RoomDatabaseMigrationTest {

    @Test
    fun testMigration1To2AddsUniqueIndex() {
        val migration = AppDatabase.MIGRATION_1_2
        assertEquals(1, migration.startVersion)
        assertEquals(2, migration.endVersion)

        val executedSql = mutableListOf<String>()
        val dbProxy = createMockDb(executedSql)

        migration.migrate(dbProxy)

        assertTrue("Expected index creation SQL", executedSql.any { it.contains("CREATE UNIQUE INDEX") && it.contains("index_hidden_media_vaultPath") })
    }

    @Test
    fun testMigration2To3AddsEncryptionAndMetadataColumns() {
        val migration = AppDatabase.MIGRATION_2_3
        assertEquals(2, migration.startVersion)
        assertEquals(3, migration.endVersion)

        val executedSql = mutableListOf<String>()
        val dbProxy = createMockDb(executedSql)

        migration.migrate(dbProxy)

        assertTrue("Expected isEncrypted column added", executedSql.any { it.contains("ADD COLUMN `isEncrypted`") })
        assertTrue("Expected relativeFolder column added", executedSql.any { it.contains("ADD COLUMN `relativeFolder`") })
        assertTrue("Expected dateTaken column added", executedSql.any { it.contains("ADD COLUMN `dateTaken`") })
    }

    private fun createMockDb(executedSql: MutableList<String>): SupportSQLiteDatabase {
        return Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase
    }
}
