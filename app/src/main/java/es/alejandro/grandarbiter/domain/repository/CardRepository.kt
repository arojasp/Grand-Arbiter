package es.alejandro.grandarbiter.domain.repository

import es.alejandro.grandarbiter.domain.model.cards.CardDataInfo
import es.alejandro.grandarbiter.domain.model.cards.CardDetail
import es.alejandro.grandarbiter.domain.model.cards.CardSearchResult
import es.alejandro.grandarbiter.domain.model.cards.FormatBanList
import kotlinx.coroutines.flow.Flow

/**
 * Card data: an offline Scryfall snapshot in cards.db. Online only for images and for searches
 * the offline syntax can't answer (on request).
 */
interface CardRepository {
    /** [query]: card names and a subset of Scryfall's syntax (data/cards/search/CardQuery.kt). */
    suspend fun search(query: String, limit: Int = 100): CardSearchResult
    /** The whole [query] on Scryfall: Online or OnlineFailed. */
    suspend fun searchOnline(query: String): CardSearchResult
    suspend fun card(oracleId: String): CardDetail?
    fun observeDataInfo(): Flow<CardDataInfo?>
    /** Cards written so far by an import in progress; null when none runs. */
    fun observeImportProgress(): Flow<Int?>
    /** Banned/restricted lists per format; re-emits after a card data update. Empty until data is installed. */
    fun observeBanLists(): Flow<List<FormatBanList>>
}
