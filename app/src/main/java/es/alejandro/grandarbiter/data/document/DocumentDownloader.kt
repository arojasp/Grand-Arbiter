package es.alejandro.grandarbiter.data.document

import okhttp3.OkHttpClient
import java.io.File

class DocumentDownloader(
    private val okHttpClient: OkHttpClient
) {
    suspend fun downloadDocument(url: String, targetFile: File) {
        // Skeleton for downloading PDF
    }
}
