package es.alejandro.grandarbiter.data.cards.search

import es.alejandro.grandarbiter.data.cards.local.CardMapper
import es.alejandro.grandarbiter.data.cards.scryfall.ScryfallCard
import es.alejandro.grandarbiter.data.cards.scryfall.ScryfallCardList
import es.alejandro.grandarbiter.data.cards.scryfall.ScryfallError
import es.alejandro.grandarbiter.data.cards.scryfall.ScryfallJson
import es.alejandro.grandarbiter.domain.model.cards.CardSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** What Scryfall's search answered. */
sealed interface OnlineSearch {
    data class Found(val cards: List<CardSummary>, val total: Int, val warnings: List<String>) : OnlineSearch
    /** HTTP 400/422: a query Scryfall can't read. */
    data class Rejected(val details: String, val warnings: List<String>) : OnlineSearch
    data object NoConnection : OnlineSearch
    /** Busy (429), down (5xx) or anything unexpected. */
    data class Unavailable(val code: Int) : OnlineSearch
}

/**
 * The online half of the card search: queries the app can't answer offline go to
 * https://api.scryfall.com/cards/search, one request per explicit search (never per keystroke).
 * The OkHttpClient must carry ScryfallHeadersInterceptor. Only the first page is read (Scryfall
 * pages hold 175 cards); [OnlineSearch.Found.total] says how many there are in all.
 */
class ScryfallSearchClient(
    private val http: OkHttpClient,
    private val baseUrl: String = "https://api.scryfall.com",
    private val json: Json = ScryfallJson,
) {
    fun url(query: String): HttpUrl = "$baseUrl/cards/search".toHttpUrl().newBuilder()
        .addQueryParameter("q", query)
        .addQueryParameter("unique", "cards")      // one row per card, like the offline search
        .addQueryParameter("order", "name")
        .build()

    suspend fun search(query: String): OnlineSearch = withContext(Dispatchers.IO) {
        try {
            http.newCall(Request.Builder().url(url(query)).build()).execute().use { response ->
                val body = response.body.string()
                when {
                    response.isSuccessful -> {
                        val list = json.decodeFromString<ScryfallCardList>(body)
                        OnlineSearch.Found(toSummaries(list.data), list.totalCards, list.warnings)
                    }
                    // Scryfall answers "no cards" with a 404 error object.
                    response.code == 404 -> OnlineSearch.Found(emptyList(), 0, error(body).warnings)
                    response.code == 400 || response.code == 422 -> error(body).let { OnlineSearch.Rejected(it.details, it.warnings) }
                    else -> OnlineSearch.Unavailable(response.code)
                }
            }
        } catch (e: IOException) {
            OnlineSearch.NoConnection
        } catch (e: kotlinx.serialization.SerializationException) {
            OnlineSearch.Unavailable(0)
        }
    }

    private fun error(body: String): ScryfallError =
        runCatching { json.decodeFromString<ScryfallError>(body) }.getOrDefault(ScryfallError())

    companion object {
        /**
         * Same rows as the offline search, keyed by oracle id so a tap opens the local card.
         * Reversible cards have no oracle id of their own: their first face (a whole card) is used.
         */
        fun toSummaries(cards: List<ScryfallCard>): List<CardSummary> = cards.mapNotNull { c ->
            if (c.oracleId != null) {
                val e = CardMapper.toEntity(c)
                CardSummary(e.oracleId, e.name, e.manaCost, e.typeLine, e.isToken)
            } else {
                val face = c.cardFaces.firstOrNull { it.oracleId != null } ?: return@mapNotNull null
                CardSummary(face.oracleId!!, face.name, face.manaCost?.takeIf { it.isNotBlank() }, face.typeLine, isToken = false)
            }
        }.distinctBy { it.oracleId }
    }
}
