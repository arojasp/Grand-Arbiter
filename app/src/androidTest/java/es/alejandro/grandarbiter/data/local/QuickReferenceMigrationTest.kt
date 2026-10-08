package es.alejandro.grandarbiter.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.alejandro.grandarbiter.data.quickrefs.QuickReferencePresets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The 7 -> 8 migration on a real device DB. There's no exported v7 schema (export starts at v8),
 * so the v7 database is written by hand with the SQL Room generated for it. Room validates the
 * migrated schema against the v8 entities when it opens, so a wrong CREATE TABLE fails here.
 */
@RunWith(AndroidJUnit4::class)
class QuickReferenceMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"
    private var db: AppDatabase? = null

    @Before fun clean() { context.deleteDatabase(name) }
    @After fun close() { db?.close(); context.deleteDatabase(name) }

    private fun open(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8)
        .addCallback(AppDatabase.SEED_PRESETS)
        .build().also { db = it }

    @Test fun v7_migratesKeepingData_andAddsPresets() = runBlocking {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { v7 ->
            v7.execSQL(
                "CREATE TABLE IF NOT EXISTS `card_cache` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `oracleText` TEXT NOT NULL, " +
                    "`typeLine` TEXT NOT NULL, `manaCost` TEXT NOT NULL, `legalities` TEXT NOT NULL, `rulings` TEXT NOT NULL, " +
                    "`cachedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            v7.execSQL("INSERT INTO card_cache VALUES ('x', 'Shock', '', '', '', '', '', 0)")
            v7.version = 7
        }

        val dao = open().quickReferenceDao()
        val rows = dao.observeAll().first()          // opening runs + validates the migration
        assertEquals(QuickReferencePresets.ALL.map { it.key }.toSet(), rows.mapNotNull { it.presetKey }.toSet())
        assertTrue(rows.all { it.itemCount > 0 })
        open().close()
        db!!.openHelper.readableDatabase.query("SELECT COUNT(*) FROM card_cache").use {
            it.moveToFirst(); assertEquals(1, it.getInt(0))
        }
    }

    @Test fun freshInstall_hasPresets_andDeletingCascades() = runBlocking {
        val dao = open().quickReferenceDao()
        val layers = dao.observeAll().first().first { it.presetKey == "layers" }
        assertEquals(2, dao.items(layers.id).size)

        dao.delete(layers.id)
        assertTrue(dao.items(layers.id).isEmpty())
        assertEquals(listOf("cast", "copiable"), dao.presetKeys().sorted())

        val id = dao.save(null, "Mine", "ipg", listOf("ipg/2.5" to "note", "ipg/3.1" to null))
        assertEquals(listOf("ipg/2.5", "ipg/3.1"), dao.items(id).map { it.sectionId })
        dao.save(id, "Mine", "ipg", listOf("ipg/3.1" to null))
        assertEquals(listOf("ipg/3.1"), dao.items(id).map { it.sectionId })
    }
}
