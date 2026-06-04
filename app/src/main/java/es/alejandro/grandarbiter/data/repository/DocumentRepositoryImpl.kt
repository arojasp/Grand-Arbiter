package es.alejandro.grandarbiter.data.repository

import es.alejandro.grandarbiter.data.document.DocumentDownloader
import es.alejandro.grandarbiter.data.document.DocumentParser
import es.alejandro.grandarbiter.data.document.DocumentUpdateChecker
import es.alejandro.grandarbiter.data.local.dao.DocumentDao
import es.alejandro.grandarbiter.data.local.entities.DocumentEntity
import es.alejandro.grandarbiter.data.local.entities.DocumentSectionEntity
import es.alejandro.grandarbiter.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow

class DocumentRepositoryImpl(
    private val documentDao: DocumentDao,
    private val updateChecker: DocumentUpdateChecker,
    private val downloader: DocumentDownloader,
    private val parser: DocumentParser,
    private val settingsDataStore: es.alejandro.grandarbiter.data.local.datastore.SettingsDataStore
) : DocumentRepository {
    override fun getAllDocuments(): Flow<List<DocumentEntity>> = documentDao.getAllDocuments()

    override fun getSectionsForDocument(documentId: String): Flow<List<DocumentSectionEntity>> =
        documentDao.getSectionsForDocument(documentId)

    override suspend fun checkForUpdates() {
        // Implementation skeleton
    }

    override suspend fun downloadAndParseDocument(documentId: String) {
        // Implementation skeleton
    }
}
