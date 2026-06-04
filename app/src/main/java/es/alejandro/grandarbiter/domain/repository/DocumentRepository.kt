package es.alejandro.grandarbiter.domain.repository

import es.alejandro.grandarbiter.data.local.entities.DocumentEntity
import es.alejandro.grandarbiter.data.local.entities.DocumentSectionEntity
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    fun getAllDocuments(): Flow<List<DocumentEntity>>
    fun getSectionsForDocument(documentId: String): Flow<List<DocumentSectionEntity>>
    suspend fun checkForUpdates()
    suspend fun downloadAndParseDocument(documentId: String)
}
