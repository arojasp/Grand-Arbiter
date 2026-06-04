package es.alejandro.grandarbiter.data.document

import android.content.Context
import es.alejandro.grandarbiter.data.local.entities.DocumentSectionEntity
import java.io.File

class DocumentParser(private val context: Context) {
    suspend fun parsePdf(file: File, documentId: String): List<DocumentSectionEntity> {
        // Skeleton for PDF parsing using PDFBox
        return emptyList()
    }
}
