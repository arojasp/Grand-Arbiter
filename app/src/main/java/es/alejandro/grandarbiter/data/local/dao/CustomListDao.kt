package es.alejandro.grandarbiter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import es.alejandro.grandarbiter.data.local.entities.CustomListEntity
import es.alejandro.grandarbiter.data.local.entities.CustomListEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CustomListDao {

    @Query("SELECT * FROM custom_list ORDER BY position, id")
    abstract fun observeLists(): Flow<List<CustomListEntity>>

    @Query("SELECT * FROM custom_list_entry")
    abstract fun observeEntries(): Flow<List<CustomListEntryEntity>>

    @Query("SELECT * FROM custom_list WHERE id = :id")
    abstract suspend fun get(id: Long): CustomListEntity?

    @Query("SELECT * FROM custom_list_entry WHERE listId = :id")
    abstract suspend fun entries(id: Long): List<CustomListEntryEntity>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM custom_list")
    abstract suspend fun nextPosition(): Int

    @Insert abstract suspend fun insert(list: CustomListEntity): Long
    @Insert abstract suspend fun insertEntries(entries: List<CustomListEntryEntity>)

    @Query("UPDATE custom_list SET name = :name, note = :note WHERE id = :id")
    abstract suspend fun update(id: Long, name: String, note: String)

    @Query("DELETE FROM custom_list_entry WHERE listId = :id")
    abstract suspend fun deleteEntries(id: Long)

    @Query("DELETE FROM custom_list WHERE id = :id")
    protected abstract suspend fun deleteList(id: Long)

    /** Entries explicitly too: the FK cascade only works if the connection has foreign_keys on. */
    @Transaction
    open suspend fun delete(id: Long) {
        deleteEntries(id)
        deleteList(id)
    }

    /** Creates (id == null) or replaces a list and all its entries, atomically. */
    @Transaction
    open suspend fun save(id: Long?, name: String, note: String, entries: List<CustomListEntryEntity>): Long {
        val listId = if (id == null) {
            insert(CustomListEntity(name = name, note = note, position = nextPosition()))
        } else {
            update(id, name, note)
            deleteEntries(id)
            id
        }
        insertEntries(entries.map { it.copy(id = 0, listId = listId) })
        return listId
    }
}
