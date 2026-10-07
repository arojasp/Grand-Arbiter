package es.alejandro.grandarbiter.domain.repository

import es.alejandro.grandarbiter.domain.model.cards.CardDataInfo
import es.alejandro.grandarbiter.domain.model.cards.CardDetail
import es.alejandro.grandarbiter.domain.model.cards.CardSummary
import es.alejandro.grandarbiter.domain.model.cards.FormatBanList
import kotlinx.coroutines.flow.Flow

/** Offline card data (Scryfall snapshot in cards.db). Images are the only online part. */
interface CardRepository {
    suspend fun search(query: String, inText: Boolean = false, limit: Int = 100): List<CardSummary>
    suspend fun card(oracleId: String): CardDetail?
    fun observeDataInfo(): Flow<CardDataInfo?>
    /** Banned/restricted lists per format; re-emits after a card data update. Empty until data is installed. */
    fun observeBanLists(): Flow<List<FormatBanList>>
}
