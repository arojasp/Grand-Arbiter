package es.alejandro.grandarbiter.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import es.alejandro.grandarbiter.data.local.dao.CardDao
import es.alejandro.grandarbiter.data.local.dao.QuickReferenceDao
import es.alejandro.grandarbiter.data.local.entities.CardCacheEntity
import es.alejandro.grandarbiter.data.local.entities.QuickReferenceEntity
import es.alejandro.grandarbiter.data.local.entities.QuickReferenceItemEntity
import es.alejandro.grandarbiter.data.quickrefs.QuickReferencePresets

/**
 * App/user data. Rules content lives in its own database (data.rules.local.RulesDatabase).
 * Real migrations only and NO destructive fallback: this holds the judge's own data (quick
 * references since v8). Schemas are exported to app/schemas/ from v8 on.
 */
@Database(
    entities = [
        CardCacheEntity::class,
        QuickReferenceEntity::class,
        QuickReferenceItemEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun quickReferenceDao(): QuickReferenceDao

    companion object {
        /** v7: the old CR tables moved to rules.db; drop them, keep everything else. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS comprehensive_rules")
                db.execSQL("DROP TABLE IF EXISTS glossary")
                db.execSQL("DROP TABLE IF EXISTS table_of_contents")
            }
        }

        /**
         * v8: quick references. The SQL is what Room generates for the entities (Room validates it
         * after migrating; QuickReferenceMigrationTest runs it). Then the built-in references.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createQuickReferenceTables(db)
                QuickReferencePresets.insert(db)
            }
        }

        fun createQuickReferenceTables(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `quick_reference` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, `documentId` TEXT NOT NULL, `position` INTEGER NOT NULL, `presetKey` TEXT)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `quick_reference_item` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`referenceId` INTEGER NOT NULL, `sectionId` TEXT NOT NULL, `position` INTEGER NOT NULL, `note` TEXT, " +
                    "FOREIGN KEY(`referenceId`) REFERENCES `quick_reference`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_quick_reference_item_referenceId` ON `quick_reference_item` (`referenceId`)"
            )
        }

        /** Fresh installs: Room creates the tables itself; this only adds the built-in references. */
        val SEED_PRESETS = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                QuickReferencePresets.insert(db)
            }
        }
    }
}
