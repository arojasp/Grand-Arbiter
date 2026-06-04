package es.alejandro.grandarbiter.data.repository

import es.alejandro.grandarbiter.data.local.dao.CardDao
import es.alejandro.grandarbiter.data.local.entities.CardCacheEntity
import es.alejandro.grandarbiter.data.remote.ScryfallService
import es.alejandro.grandarbiter.domain.repository.CardRepository
import kotlinx.coroutines.flow.Flow

class CardRepositoryImpl(
    private val cardDao: CardDao,
    private val scryfallService: ScryfallService
) : CardRepository {
    override fun getCardById(cardId: String): Flow<CardCacheEntity?> = cardDao.getCardById(cardId)

    override suspend fun searchCards(query: String, lang: String?): List<CardCacheEntity> {
        // Implementation skeleton for searching and caching
        return emptyList()
    }
}
