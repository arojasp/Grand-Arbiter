package es.alejandro.grandarbiter.data.rules.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/*
 * A SEPARATE database just for rules content.
 *
 * These tables are a disposable copy of the bundle: on any schema change we drop them and
 * re-import from the bundle, so fallbackToDestructiveMigration() is safe HERE and only here.
 * User data (bookmarks, judge logs, tournaments, card cache) stays in AppDatabase with real
 * migrations and refers to sections by their String id.
 */

@Entity(tableName = "rules_document")
data class RulesDocumentEntity(
    @PrimaryKey val id: String,              // "cr", "mtr", "ipg", "jar"
    val title: String,
    val effectiveDate: String,               // ISO yyyy-MM-dd
    val sourceUrl: String,
    val bundleVersion: Long,                 // bundle this row was imported from
)

@Entity(
    tableName = "rules_section",
    foreignKeys = [
        ForeignKey(
            entity = RulesDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["documentId", "ordinal"], unique = true),
        Index(value = ["parentId"]),
        Index(value = ["documentId", "number"]),
    ],
)
data class RulesSectionEntity(
    @PrimaryKey val id: String,              // "ipg/2.1"
    val documentId: String,
    val parentId: String?,
    val number: String?,
    val title: String?,
    val level: Int,                          // 0 = top level, computed at import
    val ordinal: Int,                        // reading order within the document
    val blocksJson: String,                  // List<Block> as JSON, decoded only for display
    val plainText: String,                   // markup stripped, feeds FTS
    val penalty: String?,                    // Penalty.name, IPG infractions only
    val infractionCategory: String?,         // InfractionCategory.name
)

/** External-content FTS over rules_section. Room generates the sync triggers. */
@Fts4(contentEntity = RulesSectionEntity::class)
@Entity(tableName = "rules_section_fts")
data class RulesSectionFts(
    val title: String?,
    val plainText: String,
)

@Entity(
    tableName = "rules_glossary",
    primaryKeys = ["documentId", "term"],
    foreignKeys = [
        ForeignKey(
            entity = RulesDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RulesGlossaryEntity(
    val documentId: String,
    val term: String,
    val definition: String,                  // with markup
)

/** Search hit: just enough for a result row. */
data class SectionHit(
    val id: String,
    val documentId: String,
    val number: String?,
    val title: String?,
    val snippet: String,
)

@Dao
interface RulesDao {

    // ---- reading ----

    @Query("SELECT * FROM rules_document ORDER BY id")
    fun observeDocuments(): Flow<List<RulesDocumentEntity>>

    @Query("SELECT * FROM rules_section WHERE documentId = :documentId ORDER BY ordinal")
    fun observeDocument(documentId: String): Flow<List<RulesSectionEntity>>

    /** Table of contents: top levels only, no CR subrules. */
    @Query(
        "SELECT * FROM rules_section WHERE documentId = :documentId AND level <= :maxLevel " +
            "ORDER BY ordinal"
    )
    suspend fun toc(documentId: String, maxLevel: Int): List<RulesSectionEntity>

    @Query("SELECT * FROM rules_section WHERE id = :id")
    suspend fun section(id: String): RulesSectionEntity?

    @Query("SELECT * FROM rules_document WHERE id = :documentId")
    suspend fun document(documentId: String): RulesDocumentEntity?

    /** Where a page that starts at [after] ends: the next section at the same or a higher level. */
    @Query(
        "SELECT MIN(ordinal) FROM rules_section WHERE documentId = :documentId " +
            "AND ordinal > :after AND level <= :level"
    )
    suspend fun nextOrdinalAtOrAbove(documentId: String, after: Int, level: Int): Int?

    @Query(
        "SELECT * FROM rules_section WHERE documentId = :documentId " +
            "AND ordinal >= :from AND ordinal < :toExclusive ORDER BY ordinal"
    )
    suspend fun range(documentId: String, from: Int, toExclusive: Int): List<RulesSectionEntity>

    @Query("SELECT COUNT(*) FROM rules_section WHERE parentId = :id")
    suspend fun childCount(id: String): Int

    @Query("SELECT * FROM rules_section WHERE parentId = :id ORDER BY ordinal")
    suspend fun children(id: String): List<RulesSectionEntity>

    /** Exact or prefix lookup by printed number ("603.1" also finds 603.1a…). FTS tokenizes numbers badly. */
    @Query(
        "SELECT * FROM rules_section WHERE documentId = :documentId " +
            "AND (number = :number OR number LIKE :number || '%') ORDER BY ordinal LIMIT 50"
    )
    suspend fun byNumber(documentId: String, number: String): List<RulesSectionEntity>

    /** Full-text search. Pass an FTS query, e.g. "missed* trigger*". documentId null = all documents. */
    @Query(
        """
        SELECT s.id, s.documentId, s.number, s.title,
               snippet(rules_section_fts, char(2), char(3), '…', -1, 12) AS snippet
        FROM rules_section_fts
        JOIN rules_section s ON s.rowid = rules_section_fts.rowid
        WHERE rules_section_fts MATCH :query
          AND (:documentId IS NULL OR s.documentId = :documentId)
        LIMIT 100
        """
    )
    suspend fun search(query: String, documentId: String?): List<SectionHit>

    @Query("SELECT * FROM rules_section WHERE penalty IS NOT NULL ORDER BY ordinal")
    suspend fun infractions(): List<RulesSectionEntity>

    @Query("SELECT * FROM rules_glossary WHERE documentId = :documentId AND term LIKE :prefix || '%' ORDER BY term")
    suspend fun glossary(documentId: String, prefix: String): List<RulesGlossaryEntity>

    @Query("SELECT MIN(bundleVersion) FROM rules_document")
    suspend fun installedBundleVersion(): Long?

    // ---- importing (called inside withTransaction by the importer) ----

    // Explicit deletes instead of relying on ON DELETE CASCADE (only enforced if foreign_keys is ON).
    @Query("DELETE FROM rules_glossary") suspend fun deleteAllGlossary()
    @Query("DELETE FROM rules_section") suspend fun deleteAllSections()     // FTS kept in sync by Room's triggers
    @Query("DELETE FROM rules_document") suspend fun deleteAllDocuments()

    @Transaction
    suspend fun deleteAllContent() {
        deleteAllGlossary()
        deleteAllSections()
        deleteAllDocuments()
    }

    @Insert suspend fun insertDocuments(docs: List<RulesDocumentEntity>)
    @Insert suspend fun insertSections(sections: List<RulesSectionEntity>)
    @Insert suspend fun insertGlossary(entries: List<RulesGlossaryEntity>)
}

@Database(
    entities = [
        RulesDocumentEntity::class,
        RulesSectionEntity::class,
        RulesSectionFts::class,
        RulesGlossaryEntity::class,
    ],
    version = 1,
    exportSchema = false,   // disposable content DB: no migrations, so no schema history needed
)
abstract class RulesDatabase : RoomDatabase() {
    abstract fun rulesDao(): RulesDao
}

/*
 * Koin wiring (sketch):
 *
 * single {
 *     Room.databaseBuilder(androidContext(), RulesDatabase::class.java, "rules.db")
 *         .fallbackToDestructiveMigration(dropAllTables = true)   // OK: content is re-importable
 *         .build()
 * }
 * single { get<RulesDatabase>().rulesDao() }
 */
