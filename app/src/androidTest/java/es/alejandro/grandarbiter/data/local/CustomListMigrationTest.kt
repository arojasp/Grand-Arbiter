package es.alejandro.grandarbiter.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import es.alejandro.grandarbiter.data.customlists.CustomListRepositoryImpl
import es.alejandro.grandarbiter.data.local.entities.CustomListEntryEntity
import es.alejandro.grandarbiter.domain.model.customlists.CustomList
import es.alejandro.grandarbiter.domain.model.customlists.CustomListEntry
import es.alejandro.grandarbiter.domain.model.customlists.ListStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The 8 -> 9 migration on a real device DB: the v8 database is written with v8's SQL (the same
 * the 7 -> 8 migration runs) and a quick reference in it. Room validates the migrated schema
 * against the v9 entities when it opens, so a wrong CREATE TABLE fails here.
 */
@RunWith(AndroidJUnit4::class)
class CustomListMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "custom-list-migration-test.db"
    private var db: AppDatabase? = null

    @Before fun clean() { context.deleteDatabase(name) }
    @After fun close() { db?.close(); context.deleteDatabase(name) }

    private fun open(): AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9)
        .addCallback(AppDatabase.SEED_PRESETS)
        .build().also { db = it }

    @Test fun v8_migratesKeepingQuickReferences() = runBlocking {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { v8 ->
            v8.execSQL(
                "CREATE TABLE IF NOT EXISTS `card_cache` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `oracleText` TEXT NOT NULL, " +
                    "`typeLine` TEXT NOT NULL, `manaCost` TEXT NOT NULL, `legalities` TEXT NOT NULL, `rulings` TEXT NOT NULL, " +
                    "`cachedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            v8.execSQL(
                "CREATE TABLE IF NOT EXISTS `quick_reference` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, `documentId` TEXT NOT NULL, `position` INTEGER NOT NULL, `presetKey` TEXT)"
            )
            v8.execSQL(
                "CREATE TABLE IF NOT EXISTS `quick_reference_item` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`referenceId` INTEGER NOT NULL, `sectionId` TEXT NOT NULL, `position` INTEGER NOT NULL, `note` TEXT, " +
                    "FOREIGN KEY(`referenceId`) REFERENCES `quick_reference`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            v8.execSQL("CREATE INDEX IF NOT EXISTS `index_quick_reference_item_referenceId` ON `quick_reference_item` (`referenceId`)")
            v8.execSQL("INSERT INTO quick_reference (title, documentId, position, presetKey) VALUES ('Mine', 'ipg', 0, NULL)")
            v8.version = 8
        }

        val database = open()
        val refs = database.quickReferenceDao().observeAll().first()    // opening runs + validates the migration
        assertEquals(listOf("Mine"), refs.map { it.title })
        assertTrue(database.customListDao().observeLists().first().isEmpty())
    }

    @Test fun saveReplaceDelete() = runBlocking {
        val repo = CustomListRepositoryImpl(open().customListDao())
        val list = CustomList(
            id = null, name = " 7PH ", note = "Max 7 points",
            entries = listOf(
                CustomListEntry("a", "Card A", ListStatus.Points(3)),
                CustomListEntry("b", "Card B", ListStatus.Banned),
            ),
        )
        val id = repo.save(list)
        val saved = repo.get(id)!!
        assertEquals("7PH", saved.name)
        assertEquals(list.entries.toSet(), saved.entries.toSet())

        repo.save(saved.copy(entries = listOf(CustomListEntry("c", "Card C", ListStatus.Restricted))))
        assertEquals(listOf("c"), repo.get(id)!!.entries.map { it.oracleId })
        assertEquals(1, repo.observeAll().first().size)

        repo.delete(id)
        assertNull(repo.get(id))
        assertTrue(db!!.customListDao().entries(id).isEmpty())
    }

    @Test(expected = SQLiteConstraintException::class)
    fun oneCardOncePerList() = runBlocking {
        val dao = open().customListDao()
        val row = CustomListEntryEntity(listId = 0, oracleId = "a", name = "Card A", status = "banned", points = null)
        dao.save(null, "List", "", listOf(row, row))
        Unit
    }
}
