package es.alejandro.grandarbiter.domain.model.cards

/** A search result row. */
data class CardSummary(
    val oracleId: String,
    val name: String,
    val manaCost: String?,
    val typeLine: String?,
    val isToken: Boolean,
)

data class CardDetail(
    val oracleId: String,
    val name: String,
    val layout: String,
    val manaCost: String?,
    val typeLine: String?,
    /** Single-faced cards only. Multi-faced cards carry their text in [faces]. */
    val oracleText: String?,
    val power: String?,
    val toughness: String?,
    val loyalty: String?,
    val defense: String?,
    val handModifier: String?,
    val lifeModifier: String?,
    val faces: List<CardFace>,
    /** Online only: Scryfall's CDN. Null for cards Scryfall has no image for. */
    val imageUrl: String?,
    /** Online only: the art alone. Must be shown with [artist] (Scryfall guidelines). */
    val artCropUrl: String?,
    val artist: String?,
    val legalities: List<FormatLegality>,
    val gameChanger: Boolean,
    val reserved: Boolean,
    val isToken: Boolean,
    val rulings: List<CardRuling>,
    val scryfallUrl: String,
)

data class CardFace(
    val name: String,
    val manaCost: String?,
    val typeLine: String?,
    val oracleText: String?,
    val power: String?,
    val toughness: String?,
    val loyalty: String?,
    val defense: String?,
    val imageUrl: String?,
    val artCropUrl: String?,
    val artist: String?,
)

enum class Legality { LEGAL, RESTRICTED, BANNED, NOT_LEGAL }

data class FormatLegality(val format: String, val legality: Legality)

/** [source] is "wotc" (official) or "scryfall" (Scryfall's own notes). */
data class CardRuling(val date: String, val source: String, val text: String)

/** Which card data is installed, for the "data from …" line in the UI. */
data class CardDataInfo(
    val oracleUpdatedAt: String,
    val rulingsUpdatedAt: String,
    val source: String,
    val importedAt: Long,
    val cardCount: Int,
)

/** Progress of a manual "check for updates" against Scryfall. */
sealed interface CardUpdateState {
    data object Idle : CardUpdateState
    data object Checking : CardUpdateState
    data class Downloading(val part: Part, val bytes: Long, val totalBytes: Long?) : CardUpdateState {
        enum class Part { CARDS, RULINGS }
    }
    /** Writing into the database: ~20 s, the old data stays readable meanwhile. */
    data object Importing : CardUpdateState
    data class UpToDate(val oracleUpdatedAt: String) : CardUpdateState
    data class Updated(val cards: Int, val oracleUpdatedAt: String) : CardUpdateState
    /** Nothing changed: the installed data is still the previous one. */
    data class Failed(val reason: String) : CardUpdateState
}
