package es.alejandro.grandarbiter.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quick_references")
data class QuickReferenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customTitle: String,
    val documentId: String,
    val sectionId: Long,
    val sectionTitle: String,
    val isPinned: Boolean
)
