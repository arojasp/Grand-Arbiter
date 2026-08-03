package es.alejandro.grandarbiter.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "glossary")
data class GlossaryEntity(
    @PrimaryKey
    val term: String,
    val definition: String
)
