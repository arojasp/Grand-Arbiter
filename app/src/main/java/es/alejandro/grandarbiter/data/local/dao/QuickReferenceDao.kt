package es.alejandro.grandarbiter.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import es.alejandro.grandarbiter.data.local.entities.QuickReferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickReferenceDao {
    @Query("SELECT * FROM quick_references")
    fun getAllQuickReferences(): Flow<List<QuickReferenceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuickReference(quickReference: QuickReferenceEntity)

    @Delete
    suspend fun deleteQuickReference(quickReference: QuickReferenceEntity)
}
