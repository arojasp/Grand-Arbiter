package es.alejandro.grandarbiter.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "card_cache")
data class CardCacheEntity(
    @PrimaryKey val id: String,
    val name: String,
    val oracleText: String,
    val typeLine: String,
    val manaCost: String,
    val legalities: String, // Simplified for skeleton
    val rulings: String, // Simplified for skeleton
    val cachedAt: Long
)
