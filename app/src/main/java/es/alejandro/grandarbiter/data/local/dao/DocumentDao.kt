package es.alejandro.grandarbiter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import es.alejandro.grandarbiter.data.local.entities.DocumentEntity
import es.alejandro.grandarbiter.data.local.entities.DocumentSectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity)

    @Query("SELECT * FROM document_sections WHERE documentId = :documentId")
    fun getSectionsForDocument(documentId: String): Flow<List<DocumentSectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSections(sections: List<DocumentSectionEntity>)
}
