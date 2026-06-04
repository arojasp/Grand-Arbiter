package es.alejandro.grandarbiter.domain.repository

import es.alejandro.grandarbiter.data.local.entities.CardCacheEntity
import kotlinx.coroutines.flow.Flow

interface CardRepository {
    fun getCardById(cardId: String): Flow<CardCacheEntity?>
    suspend fun searchCards(query: String, lang: String?): List<CardCacheEntity>
}
