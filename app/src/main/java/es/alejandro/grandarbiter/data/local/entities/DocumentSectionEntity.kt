package es.alejandro.grandarbiter.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "document_sections")
data class DocumentSectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: String,
    val sectionNumber: String,
    val sectionTitle: String,
    val parentSectionId: Long?,
    val content: String
)
