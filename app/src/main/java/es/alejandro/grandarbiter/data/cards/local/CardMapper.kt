package es.alejandro.grandarbiter.data.cards.local

import es.alejandro.grandarbiter.data.cards.scryfall.CardFilter
import es.alejandro.grandarbiter.data.cards.scryfall.ScryfallCard
import es.alejandro.grandarbiter.data.cards.scryfall.ScryfallFace
import es.alejandro.grandarbiter.data.cards.scryfall.ScryfallJson
import es.alejandro.grandarbiter.domain.model.cards.BanCategory
import es.alejandro.grandarbiter.domain.model.cards.BanListEntry
import es.alejandro.grandarbiter.domain.model.cards.CardDetail
import es.alejandro.grandarbiter.domain.model.cards.FormatBanList
import es.alejandro.grandarbiter.domain.model.cards.CardFace
import es.alejandro.grandarbiter.domain.model.cards.CardRuling
import es.alejandro.grandarbiter.domain.model.cards.FormatLegality
import es.alejandro.grandarbiter.domain.model.cards.Legality
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Scryfall card -> row, row -> domain. Pure, unit-tested. */
object CardMapper {

    private val TOKEN_LAYOUTS = setOf("token", "double_faced_token", "emblem")
    private const val WUBRG = "WUBRG"
    private val faceList = ListSerializer(ScryfallFace.serializer())

    fun toEntity(c: ScryfallCard, json: Json = ScryfallJson): CardEntity {
        val faces = c.cardFaces
        return CardEntity(
            oracleId = requireNotNull(c.oracleId) { "${c.name}: no oracle_id" },
            name = c.name,
            layout = c.layout,
            // Transform/MDFC cards have no top-level cost or text: Scryfall puts them on the faces.
            manaCost = c.manaCost.nonBlank()
                ?: faces.mapNotNull { it.manaCost.nonBlank() }.joinToString(" // ").nonBlank(),
            cmc = c.cmc,
            typeLine = c.typeLine.nonBlank() ?: faces.mapNotNull { it.typeLine.nonBlank() }.joinToString(" // ").nonBlank(),
            oracleText = c.oracleText.nonBlank()
                ?: faces.mapNotNull { it.oracleText.nonBlank() }.joinToString("\n\n").nonBlank(),
            power = c.power,
            toughness = c.toughness,
            loyalty = c.loyalty,
            defense = c.defense,
            handModifier = c.handModifier,
            lifeModifier = c.lifeModifier,
            colors = wubrg(c.colors.ifEmpty { faces.flatMap { it.colors.orEmpty() } }),
            colorIdentity = wubrg(c.colorIdentity),
            keywords = c.keywords.joinToString("|"),
            legalities = CardFilter.PAPER_FORMATS.joinToString(";") { "$it=${c.legalities[it] ?: "not_legal"}" },
            facesJson = if (faces.isEmpty()) null else json.encodeToString(faceList, faces),
            imageUrl = c.imageUris?.normal ?: faces.firstOrNull()?.imageUris?.normal,
            artCropUrl = c.imageUris?.artCrop ?: faces.firstOrNull()?.imageUris?.artCrop,
            artist = c.artist.nonBlank() ?: faces.mapNotNull { it.artist.nonBlank() }.distinct().joinToString(" & ").nonBlank(),
            setCode = c.set,
            setType = c.setType,
            gameChanger = c.gameChanger,
            reserved = c.reserved,
            isToken = c.layout in TOKEN_LAYOUTS || c.typeLine?.contains("Token") == true,
        )
    }

    fun toDetail(e: CardEntity, rulings: List<RulingRow>, json: Json = ScryfallJson): CardDetail {
        val faces = e.facesJson?.let { json.decodeFromString(faceList, it) }.orEmpty()
        return CardDetail(
            oracleId = e.oracleId,
            name = e.name,
            layout = e.layout,
            manaCost = e.manaCost,
            typeLine = e.typeLine,
            oracleText = if (faces.isEmpty()) e.oracleText else null,
            power = e.power,
            toughness = e.toughness,
            loyalty = e.loyalty,
            defense = e.defense,
            handModifier = e.handModifier,
            lifeModifier = e.lifeModifier,
            faces = faces.map {
                CardFace(it.name, it.manaCost.nonBlank(), it.typeLine, it.oracleText, it.power, it.toughness,
                    it.loyalty, it.defense, it.imageUris?.normal, it.imageUris?.artCrop, it.artist.nonBlank() ?: e.artist)
            },
            imageUrl = e.imageUrl,
            artCropUrl = e.artCropUrl,
            artist = e.artist,
            legalities = parseLegalities(e.legalities),
            gameChanger = e.gameChanger,
            reserved = e.reserved,
            isToken = e.isToken,
            rulings = rulings.map { CardRuling(it.date, it.source, it.text) },
            scryfallUrl = "https://scryfall.com/search?q=oracleid%3A${e.oracleId}",
        )
    }

    /** Formats Wizards manages; the rest of CardFilter.PAPER_FORMATS are community formats. */
    val OFFICIAL_FORMATS = setOf("standard", "pioneer", "modern", "legacy", "vintage", "pauper", "commander")

    /**
     * Per-format banned/restricted lists from the stored legalities. Every paper format appears,
     * empty ones too (Standard can have none). Official formats first, each in CardFilter order.
     * Note: Legacy/Vintage include ante, Conspiracy and the 2020 "offensive" cards one by one,
     * where Wizards' page lists them as categories.
     */
    fun banLists(rows: List<LegalityRow>): List<FormatBanList> {
        val byFormat = CardFilter.PAPER_FORMATS.associateWith { mutableListOf<BanListEntry>() }
        for (row in rows) {
            for (fl in parseLegalities(row.legalities)) {
                if (fl.legality == Legality.BANNED || fl.legality == Legality.RESTRICTED) {
                    byFormat[fl.format]?.add(BanListEntry(row.oracleId, row.name, fl.legality, banCategory(row)))
                }
            }
        }
        return CardFilter.PAPER_FORMATS
            .map { f -> FormatBanList(f, f in OFFICIAL_FORMATS, byFormat.getValue(f).sortedBy { it.name.lowercase() }) }
            .sortedBy { !it.official }   // stable: keeps CardFilter order inside each group
    }

    private val ANTE = Regex("""\bante\b""", RegexOption.IGNORE_CASE)

    /**
     * Wizards' lists name these as categories ("all cards with ante", "Conspiracy cards", Un-cards
     * with the acorn stamp / stickers / attractions), not one by one; the app groups them the same way.
     */
    fun banCategory(row: LegalityRow): BanCategory = when {
        row.setType == "funny" -> BanCategory.UN_CARD
        row.typeLine?.contains("Conspiracy") == true -> BanCategory.CONSPIRACY
        row.oracleText?.let { ANTE.containsMatchIn(it) } == true -> BanCategory.ANTE
        else -> BanCategory.NONE
    }

    fun parseLegalities(stored: String): List<FormatLegality> =
        stored.split(';').mapNotNull { pair ->
            val (format, status) = pair.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            val legality = when (status) {
                "legal" -> Legality.LEGAL
                "restricted" -> Legality.RESTRICTED
                "banned" -> Legality.BANNED
                else -> Legality.NOT_LEGAL
            }
            FormatLegality(format, legality)
        }

    private fun wubrg(colors: List<String>): String =
        colors.distinct().sortedBy { WUBRG.indexOf(it).let { i -> if (i < 0) 99 else i } }.joinToString("")

    private fun String?.nonBlank(): String? = this?.takeIf { it.isNotBlank() }
}
