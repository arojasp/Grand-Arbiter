package es.alejandro.grandarbiter.data.cards.scryfall

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * Scryfall's own bulk-data format (https://scryfall.com/docs/api/cards), only the fields we use.
 * The APK seed (tools/cards/build_cards.py) and the weekly download from Scryfall are both read
 * with these classes, so they always go through the same filter and mapper.
 */

/** Lenient on purpose: Scryfall adds fields over time, and an update must not break on them. */
val ScryfallJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

@Serializable
data class ScryfallCard(
    @SerialName("oracle_id") val oracleId: String? = null,
    val name: String,
    val layout: String = "normal",
    @SerialName("mana_cost") val manaCost: String? = null,
    val cmc: Double? = null,
    @SerialName("type_line") val typeLine: String? = null,
    @SerialName("oracle_text") val oracleText: String? = null,
    val power: String? = null,
    val toughness: String? = null,
    val loyalty: String? = null,
    val defense: String? = null,
    @SerialName("hand_modifier") val handModifier: String? = null,
    @SerialName("life_modifier") val lifeModifier: String? = null,
    val colors: List<String> = emptyList(),
    @SerialName("color_identity") val colorIdentity: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val legalities: Map<String, String> = emptyMap(),
    @SerialName("game_changer") val gameChanger: Boolean = false,
    val reserved: Boolean = false,
    val games: List<String> = emptyList(),
    val set: String? = null,
    @SerialName("set_type") val setType: String? = null,
    @SerialName("card_faces") val cardFaces: List<ScryfallFace> = emptyList(),
    @SerialName("image_uris") val imageUris: ScryfallImages? = null,
    val artist: String? = null,
)

@Serializable
data class ScryfallFace(
    val name: String,
    /** Only on reversible cards, whose card object has none (each face is a whole card). */
    @SerialName("oracle_id") val oracleId: String? = null,
    @SerialName("mana_cost") val manaCost: String? = null,
    @SerialName("type_line") val typeLine: String? = null,
    @SerialName("oracle_text") val oracleText: String? = null,
    val power: String? = null,
    val toughness: String? = null,
    val loyalty: String? = null,
    val defense: String? = null,
    val colors: List<String>? = null,
    @SerialName("image_uris") val imageUris: ScryfallImages? = null,
    val artist: String? = null,
)

@Serializable
data class ScryfallImages(
    val normal: String? = null,
    @SerialName("art_crop") val artCrop: String? = null,
)

@Serializable
data class ScryfallRuling(
    @SerialName("oracle_id") val oracleId: String,
    val source: String = "wotc",
    @SerialName("published_at") val publishedAt: String,
    val comment: String,
)

/** A page of https://api.scryfall.com/cards/search. [warnings]: parts of the query Scryfall ignored. */
@Serializable
data class ScryfallCardList(
    @SerialName("total_cards") val totalCards: Int = 0,
    @SerialName("has_more") val hasMore: Boolean = false,
    val data: List<ScryfallCard> = emptyList(),
    val warnings: List<String> = emptyList(),
)

/** Scryfall's error object (404 no cards, 400 a query it can't read, …). */
@Serializable
data class ScryfallError(
    val status: Int = 0,
    val code: String = "",
    val details: String = "",
    val warnings: List<String> = emptyList(),
)

/** https://api.scryfall.com/bulk-data: the catalog of daily bulk files. */
@Serializable
data class BulkDataList(val data: List<BulkDataEntry> = emptyList())

@Serializable
data class BulkDataEntry(
    val type: String,                                              // "oracle_cards", "rulings"…
    @SerialName("updated_at") val updatedAt: String,
    val uri: String? = null,                                       // details of this entry
    @SerialName("jsonl_download_uri") val jsonlDownloadUri: String? = null,
    @SerialName("download_uri") val downloadUri: String? = null,
    @SerialName("compressed_size") val compressedSize: Long? = null,
)
