package es.alejandro.grandarbiter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import es.alejandro.grandarbiter.data.local.entities.QuickReferenceEntity
import es.alejandro.grandarbiter.data.local.entities.QuickReferenceItemEntity
import kotlinx.coroutines.flow.Flow

data class QuickReferenceRow(
    val id: Long,
    val title: String,
    val documentId: String,
    val presetKey: String?,
    /** First rule's section id, for the "CR · 613.1" subtitle. Null when empty. */
    val firstSectionId: String?,
    val itemCount: Int,
)

@Dao
abstract class QuickReferenceDao {

    @Query(
        """
        SELECT r.id, r.title, r.documentId, r.presetKey,
               (SELECT i.sectionId FROM quick_reference_item i WHERE i.referenceId = r.id ORDER BY i.position LIMIT 1) AS firstSectionId,
               (SELECT COUNT(*) FROM quick_reference_item i WHERE i.referenceId = r.id) AS itemCount
        FROM quick_reference r ORDER BY r.position, r.id
        """
    )
    abstract fun observeAll(): Flow<List<QuickReferenceRow>>

    @Query("SELECT * FROM quick_reference WHERE id = :id")
    abstract fun observe(id: Long): Flow<QuickReferenceEntity?>

    @Query("SELECT * FROM quick_reference WHERE id = :id")
    abstract suspend fun get(id: Long): QuickReferenceEntity?

    @Query("SELECT * FROM quick_reference_item WHERE referenceId = :id ORDER BY position")
    abstract fun observeItems(id: Long): Flow<List<QuickReferenceItemEntity>>

    @Query("SELECT * FROM quick_reference_item WHERE referenceId = :id ORDER BY position")
    abstract suspend fun items(id: Long): List<QuickReferenceItemEntity>

    @Query("SELECT presetKey FROM quick_reference WHERE presetKey IS NOT NULL")
    abstract fun observePresetKeys(): Flow<List<String>>

    @Query("SELECT presetKey FROM quick_reference WHERE presetKey IS NOT NULL")
    abstract suspend fun presetKeys(): List<String>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM quick_reference")
    abstract suspend fun nextPosition(): Int

    @Insert abstract suspend fun insert(reference: QuickReferenceEntity): Long
    @Insert abstract suspend fun insertItems(items: List<QuickReferenceItemEntity>)

    @Query("UPDATE quick_reference SET title = :title WHERE id = :id")
    abstract suspend fun rename(id: Long, title: String)

    @Query("DELETE FROM quick_reference_item WHERE referenceId = :id")
    abstract suspend fun deleteItems(id: Long)

    @Query("DELETE FROM quick_reference WHERE id = :id")
    protected abstract suspend fun deleteReference(id: Long)

    /** Items explicitly too: the FK cascade only works if the connection has foreign_keys on. */
    @Transaction
    open suspend fun delete(id: Long) {
        deleteItems(id)
        deleteReference(id)
    }

    /** Creates (id == null) or replaces a reference and its whole item list, atomically. */
    @Transaction
    open suspend fun save(id: Long?, title: String, documentId: String, items: List<Pair<String, String?>>): Long {
        val refId = if (id == null) {
            insert(QuickReferenceEntity(title = title, documentId = documentId, position = nextPosition(), presetKey = null))
        } else {
            rename(id, title)
            deleteItems(id)
            id
        }
        insertItems(items.mapIndexed { i, (sectionId, note) ->
            QuickReferenceItemEntity(referenceId = refId, sectionId = sectionId, position = i, note = note?.takeIf { it.isNotBlank() })
        })
        return refId
    }
}
