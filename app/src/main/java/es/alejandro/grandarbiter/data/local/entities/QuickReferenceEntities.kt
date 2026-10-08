package es.alejandro.grandarbiter.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * User data (AppDatabase): never wiped, real migrations only. The SQL in AppDatabase.MIGRATION_7_8
 * must match these definitions exactly (Room validates it); QuickReferenceMigrationTest checks it.
 */

/** A judge's quick reference: a titled list of rules from one document. */
@Entity(tableName = "quick_reference")
data class QuickReferenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val documentId: String,           // "cr" | "ipg" | "mtr" | "jar"
    val position: Int,
    /** Set on the built-in references ("layers"…), so "Restore" can bring back deleted ones. */
    val presetKey: String?,
)

/** One rule in a quick reference, with the judge's optional note. */
@Entity(
    tableName = "quick_reference_item",
    foreignKeys = [
        ForeignKey(
            entity = QuickReferenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["referenceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("referenceId")],
)
data class QuickReferenceItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val referenceId: Long,
    val sectionId: String,            // rules section id, e.g. "cr/613.1": shown with its subrules
    val position: Int,
    val note: String?,
)
