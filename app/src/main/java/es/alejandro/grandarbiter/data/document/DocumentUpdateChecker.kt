package es.alejandro.grandarbiter.data.document

import es.alejandro.grandarbiter.data.remote.AcademyRuinsService

class DocumentUpdateChecker(
    private val apiService: AcademyRuinsService
) {
    suspend fun checkForUpdates(localVersions: Map<String, String>): List<String> {
        // Silently check remote versions and compare with local
        return emptyList()
    }
}
