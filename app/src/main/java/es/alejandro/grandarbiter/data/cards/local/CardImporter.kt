package es.alejandro.grandarbiter.data.cards.local

import androidx.room.withTransaction
import es.alejandro.grandarbiter.data.cards.scryfall.CardFilter
import es.alejandro.grandarbiter.data.cards.scryfall.ScryfallCard
import es.alejandro.grandarbiter.data.cards.scryfall.ScryfallJson
import es.alejandro.grandarbiter.data.cards.scryfall.ScryfallRuling
import es.alejandro.grandarbiter.data.cards.scryfall.forEachJsonLine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

sealed interface CardImportResult {
    data class Imported(
        val source: String,
        val version: CardDataVersion,
        val cards: Int,
        val rulingTexts: Int,
        val rulingRefs: Int,
        val dropped: Map<String, Int>,
        val millis: Long,
    ) : CardImportResult
    data class UpToDate(val installed: CardDataVersion) : CardImportResult
    data object NoData : CardImportResult
    data class Rejected(val reason: String) : CardImportResult
}

/**
 * Loads a full Scryfall snapshot into cards.db in ONE transaction: if anything fails (bad file,
 * truncated download, schema change), it rolls back and the previous cards stay in place.
 * Streams both files, so memory stays low even though the raw JSON is ~60 MB.
 */
class CardImporter(
    private val database: CardsDatabase,
    private val dao: CardsDao,
    private val json: Json = ScryfallJson,
    private val minCards: Int = MIN_CARDS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()   // startup seed import and the weekly update must not interleave

    suspend fun import(source: CardDataSource, force: Boolean = false): CardImportResult = mutex.withLock {
        val version = source.version() ?: return CardImportResult.NoData
        val installed = dao.meta()?.let { CardDataVersion(it.oracleUpdatedAt, it.rulingsUpdatedAt) }
        if (!force && installed != null && !version.isNewerThan(installed)) {
            return CardImportResult.UpToDate(installed)
        }

        val started = clock()
        val dropped = HashMap<String, Int>()
        var cards = 0
        var refs = 0
        val texts = HashMap<String, Int>(40_000)
        try {
            database.withTransaction {
                dao.deleteCardRulings()
                dao.deleteRulings()
                dao.deleteCards()
                dao.deleteMeta()

                // 1. cards
                val kept = HashSet<String>(48_000)
                val batch = ArrayList<CardEntity>(BATCH)
                var bad = 0
                source.openOracleCards().forEachJsonLine(gzipped = false) { line ->
                    val card = decodeOrNull<ScryfallCard>(line)
                    if (card == null) { bad++; return@forEachJsonLine }
                    val why = CardFilter.dropReason(card)
                    if (why != null) {
                        dropped[why] = (dropped[why] ?: 0) + 1
                    } else if (kept.add(card.oracleId!!)) {
                        batch += CardMapper.toEntity(card, json)
                        if (batch.size == BATCH) { dao.insertCards(batch); batch.clear() }
                    }
                }
                if (batch.isNotEmpty()) dao.insertCards(batch)
                cards = kept.size
                if (bad > MAX_BAD_LINES) throw ImportAbort("$bad card lines failed to decode (format change?)")
                if (cards < minCards) throw ImportAbort("only $cards cards (expected ≥ $minCards): truncated file?")

                // 2. rulings, each text stored once
                val rulings = ArrayList<RulingEntity>(BATCH)
                val links = ArrayList<CardRulingEntity>(BATCH)
                val linked = HashSet<String>(100_000)
                bad = 0
                source.openRulings().forEachJsonLine(gzipped = false) { line ->
                    val r = decodeOrNull<ScryfallRuling>(line)
                    if (r == null) { bad++; return@forEachJsonLine }
                    if (r.oracleId !in kept) return@forEachJsonLine
                    val id = texts[r.comment] ?: texts.size.also {
                        texts[r.comment] = it
                        rulings += RulingEntity(it, r.comment)
                    }
                    // Scryfall sometimes repeats a card's ruling under a later date: keep the first.
                    if (linked.add("${r.oracleId}#$id")) {
                        links += CardRulingEntity(r.oracleId, id, r.publishedAt.take(10), r.source)
                    }
                    if (rulings.size >= BATCH) { dao.insertRulings(rulings); rulings.clear() }
                    if (links.size >= BATCH) { dao.insertCardRulings(links); links.clear() }
                }
                if (rulings.isNotEmpty()) dao.insertRulings(rulings)
                if (links.isNotEmpty()) dao.insertCardRulings(links)
                refs = linked.size
                if (bad > MAX_BAD_LINES) throw ImportAbort("$bad ruling lines failed to decode (format change?)")

                dao.insertMeta(
                    CardsMetaEntity(
                        oracleUpdatedAt = version.oracleUpdatedAt,
                        rulingsUpdatedAt = version.rulingsUpdatedAt,
                        source = source.name,
                        importedAt = clock(),
                        cardCount = cards,
                        rulingCount = texts.size,
                    )
                )
            }
        } catch (e: ImportAbort) {
            return CardImportResult.Rejected(e.message.orEmpty())
        } catch (e: java.io.IOException) {
            return CardImportResult.Rejected("read failed: $e")
        }
        CardImportResult.Imported(source.name, version, cards, texts.size, refs, dropped, clock() - started)
    }

    /** One odd line must not sink a 35k-card import; many odd lines abort it (see MAX_BAD_LINES). */
    private inline fun <reified T> decodeOrNull(line: String): T? = try {
        json.decodeFromString<T>(line)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private class ImportAbort(message: String) : Exception(message)

    companion object {
        /** Today ~35k. Far below that means a broken or truncated file: keep the old data. */
        const val MIN_CARDS = 25_000
        private const val MAX_BAD_LINES = 50
        private const val BATCH = 500
    }
}
