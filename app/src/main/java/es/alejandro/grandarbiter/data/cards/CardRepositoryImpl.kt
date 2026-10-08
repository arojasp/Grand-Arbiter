package es.alejandro.grandarbiter.data.cards

import es.alejandro.grandarbiter.data.cards.local.CardMapper
import es.alejandro.grandarbiter.data.cards.local.CardsDao
import es.alejandro.grandarbiter.domain.model.cards.CardDataInfo
import es.alejandro.grandarbiter.domain.model.cards.CardDetail
import es.alejandro.grandarbiter.domain.model.cards.CardSummary
import es.alejandro.grandarbiter.domain.model.cards.FormatBanList
import es.alejandro.grandarbiter.domain.repository.CardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CardRepositoryImpl(private val dao: CardsDao) : CardRepository {

    override suspend fun search(query: String, inText: Boolean, limit: Int): List<CardSummary> {
        val match = CardSearchQuery.toFts(query, inText) ?: return emptyList()
        return dao.search(match, CardSearchQuery.typed(query), limit).map {
            CardSummary(it.oracleId, it.name, it.manaCost, it.typeLine, it.isToken)
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

/** What a judge types -> FTS query. Pure, unit-tested. */
object CardSearchQuery {
    private val WORD = Regex("""[\p{L}\p{N}]+""")

    /**
     * Every word must match as a prefix. Name only by default ("light bol" -> name:light* name:bol*);
     * [inText] also searches type line and rules text ("draw a card").
     */
    fun toFts(query: String, inText: Boolean): String? {
        val words = WORD.findAll(query.lowercase()).map { it.value }.toList()
        if (words.isEmpty()) return null
        return words.joinToString(" ") { if (inText) "$it*" else "name:$it*" }
    }

    /** For ranking exact and prefix name matches first. */
    fun typed(query: String): String = query.trim().lowercase()
}
