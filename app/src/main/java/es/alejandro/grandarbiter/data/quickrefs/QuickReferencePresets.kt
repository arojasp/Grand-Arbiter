package es.alejandro.grandarbiter.data.quickrefs

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The built-in quick references. Each item is a rule shown with its subrules (613.1 -> 613.1a…g).
 * Inserted when the tables are created (fresh install or MIGRATION_7_8) and by "Restore defaults".
 * Section ids checked against the CR bundle of 2026-09-25.
 */
object QuickReferencePresets {

    data class Preset(val key: String, val title: String, val documentId: String, val sectionIds: List<String>)

    val ALL = listOf(
        Preset("layers", "Layers", "cr", listOf("cr/613.1", "cr/613.4")),
        Preset("cast", "Steps to Cast a Spell", "cr", listOf("cr/601.2")),
        Preset("copiable", "Copiable Values", "cr", listOf("cr/707.2", "cr/707.3", "cr/707.9")),
    )

    /** Raw insert for migrations / onCreate, where no DAO exists yet. [keys] = which presets. */
    fun insert(db: SupportSQLiteDatabase, keys: Set<String> = ALL.map { it.key }.toSet()) {
        var position = db.query("SELECT COALESCE(MAX(position), -1) + 1 FROM quick_reference").use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
        for (preset in ALL.filter { it.key in keys }) {
            val refId = db.insert("quick_reference", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                put("title", preset.title)
                put("documentId", preset.documentId)
                put("position", position++)
                put("presetKey", preset.key)
            })
            preset.sectionIds.forEachIndexed { i, sectionId ->
                db.insert("quick_reference_item", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                    put("referenceId", refId)
                    put("sectionId", sectionId)
                    put("position", i)
                    putNull("note")
                })
            }
        }
    }
}
