package es.alejandro.grandarbiter.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import es.alejandro.grandarbiter.data.local.dao.CardDao
import es.alejandro.grandarbiter.data.local.entities.CardCacheEntity

/**
 * App/user data. Rules content lives in its own database (data.rules.local.RulesDatabase).
 * Keep real migrations here: this is where judge data will live, so it must never be wiped.
 */
@Database(
    entities = [
        CardCacheEntity::class,
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao

    companion object {
        /** v7: the old CR tables moved to rules.db; drop them, keep everything else. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS comprehensive_rules")
                db.execSQL("DROP TABLE IF EXISTS glossary")
                db.execSQL("DROP TABLE IF EXISTS table_of_contents")
            }
        }
    }
}
