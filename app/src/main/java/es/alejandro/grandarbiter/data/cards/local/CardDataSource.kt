package es.alejandro.grandarbiter.data.cards.local

import android.content.Context
import es.alejandro.grandarbiter.data.cards.scryfall.ScryfallJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import es.alejandro.grandarbiter.data.cards.scryfall.decompressedIfGzip
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.time.OffsetDateTime

/** Scryfall's updated_at of the two bulk files a snapshot was built from. */
@Serializable
data class CardDataVersion(val oracleUpdatedAt: String, val rulingsUpdatedAt: String) {
    /** True if either file is newer than [installed]. */
    fun isNewerThan(installed: CardDataVersion): Boolean =
        instant(oracleUpdatedAt) > instant(installed.oracleUpdatedAt) ||
            instant(rulingsUpdatedAt) > instant(installed.rulingsUpdatedAt)

    private fun instant(s: String) = OffsetDateTime.parse(s).toInstant()
}

/**
 * A full card snapshot in Scryfall's bulk format: oracle-cards + rulings as JSON Lines.
 * Today: the APK seed. Next: the files downloaded from Scryfall. Same importer for both.
 * The streams are PLAIN (already decompressed): each source deals with its own compression.
 */
interface CardDataSource {
    val name: String
    /** Null if the source has no data. */
    suspend fun version(): CardDataVersion?
    fun openOracleCards(): InputStream
    fun openRulings(): InputStream
}

class AssetCardDataSource(
    private val context: Context,
    private val dir: String = "cards",
) : CardDataSource {
    override val name = "seed"

    override suspend fun version(): CardDataVersion? = withContext(Dispatchers.IO) {
        try {
            val text = context.assets.open("$dir/seed.json").bufferedReader().use { it.readText() }
            ScryfallJson.decodeFromString<CardDataVersion>(text)
        } catch (_: FileNotFoundException) {
            null
        }
    }

    override fun openOracleCards(): InputStream = open("oracle-cards.jsonl")
    override fun openRulings(): InputStream = open("rulings.jsonl")

    /**
     * The Android Gradle Plugin GUNZIPS `.gz` assets and drops the extension when it packages them
     * (src/main/assets/cards/x.jsonl.gz -> APK assets/cards/x.jsonl, deflated by the APK itself).
     * Fall back to the .gz name in case a future AGP stops doing that.
     */
    private fun open(file: String): InputStream = try {
        context.assets.open("$dir/$file")
    } catch (_: FileNotFoundException) {
        GZIPInputStream(context.assets.open("$dir/$file.gz"), 64 * 1024)
    }
}

/** A snapshot downloaded from Scryfall into two local files (gzipped or not). */
class FileCardDataSource(
    private val version: CardDataVersion,
    private val oracleCards: File,
    private val rulings: File,
) : CardDataSource {
    override val name = "scryfall"
    override suspend fun version(): CardDataVersion = version
    override fun openOracleCards(): InputStream = oracleCards.inputStream().decompressedIfGzip()
    override fun openRulings(): InputStream = rulings.inputStream().decompressedIfGzip()
}
