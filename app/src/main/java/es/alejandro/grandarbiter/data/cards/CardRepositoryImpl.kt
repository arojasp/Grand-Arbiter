package es.alejandro.grandarbiter.data.cards

import androidx.sqlite.db.SimpleSQLiteQuery
import es.alejandro.grandarbiter.data.cards.local.CardImporter
import es.alejandro.grandarbiter.data.cards.local.CardMapper
import es.alejandro.grandarbiter.data.cards.local.CardsDao
import es.alejandro.grandarbiter.data.cards.search.CardQueryParser
import es.alejandro.grandarbiter.data.cards.search.CardQuerySql
import es.alejandro.grandarbiter.data.cards.search.OnlineSearch
import es.alejandro.grandarbiter.data.cards.search.ScryfallSearchClient
import es.alejandro.grandarbiter.domain.model.cards.CardDataInfo
import es.alejandro.grandarbiter.domain.model.cards.CardDetail
import es.alejandro.grandarbiter.domain.model.cards.CardSearchResult
import es.alejandro.grandarbiter.domain.model.cards.CardSummary
import es.alejandro.grandarbiter.domain.model.cards.FormatBanList
import es.alejandro.grandarbiter.domain.repository.CardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CardRepositoryImpl(
    private val dao: CardsDao,
    private val importer: CardImporter,
    private val online: ScryfallSearchClient,
) : CardRepository {

    override fun observeImportProgress(): Flow<Int?> = importer.progress

    override suspend fun search(query: String, limit: Int): CardSearchResult {
        val parsed = CardQueryParser.parse(query)
        if (parsed.needsOnline) return CardSearchResult.NeedsOnline(parsed.onlineParts)
        val sql = CardQuerySql.build(parsed, limit) ?: return CardSearchResult.Empty
        val hits = dao.search(SimpleSQLiteQuery(sql.text, sql.args.toTypedArray()))
        return CardSearchResult.Offline(hits.map { CardSummary(it.oracleId, it.name, it.manaCost, it.typeLine, it.isToken) })
    }

    override suspend fun searchOnline(query: String): CardSearchResult {
        val parts = CardQueryParser.parse(query).onlineParts
        return when (val r = online.search(query)) {
            is OnlineSearch.Found -> CardSearchResult.Online(r.cards, r.total, r.warnings)
            is OnlineSearch.Rejected -> CardSearchResult.OnlineFailed(
                CardSearchResult.OnlineFailed.Reason.REJECTED, parts, (listOf(r.details) + r.warnings).filter { it.isNotBlank() }.joinToString("\n"),
            )
            OnlineSearch.NoConnection -> CardSearchResult.OnlineFailed(CardSearchResult.OnlineFailed.Reason.NO_CONNECTION, parts)
            is OnlineSearch.Unavailable -> CardSearchResult.OnlineFailed(CardSearchResult.OnlineFailed.Reason.UNAVAILABLE, parts)
        }
    }

    override suspend fun card(oracleId: String): CardDetail? {
        val entity = dao.card(oracleId) ?: return null
        return CardMapper.toDetail(entity, dao.rulings(oracleId))
    }

    override fun observeBanLists(): Flow<List<FormatBanList>> = dao.observeMeta().map { meta ->
        if (meta == null) emptyList() else CardMapper.banLists(dao.bannedOrRestricted())
    }

    override fun observeDataInfo(): Flow<CardDataInfo?> = dao.observeMeta().map { m ->
        m?.let { CardDataInfo(it.oracleUpdatedAt, it.rulingsUpdatedAt, it.source, it.importedAt, it.cardCount) }
    }
}
