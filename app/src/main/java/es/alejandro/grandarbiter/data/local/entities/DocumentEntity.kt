package es.alejandro.grandarbiter.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val abbreviation: String,
    val title: String,
    val content: String,
    val version: String,
    val lastUpdated: Long
)
