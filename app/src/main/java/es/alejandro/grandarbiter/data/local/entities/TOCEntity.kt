package es.alejandro.grandarbiter.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "table_of_contents")
data class TOCEntity(
    @PrimaryKey
    val number: String,
    val title: String,
    val subsections: List<TOCSubItem> = emptyList()
)

data class TOCSubItem(
    val number: String,
    val title: String
)
