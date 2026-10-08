package es.alejandro.grandarbiter.data.cards.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/*
 * A SEPARATE, disposable database for card data (same idea as rules.db): every import replaces
 * the whole snapshot, so destructive migration is safe here and only here.
 */

@Entity(tableName = "card", indices = [Index("name")])
data class CardEntity(
    @PrimaryKey val oracleId: String,
    val name: String,
    val layout: String,
    val manaCost: String?,
    val cmc: Double?,
    val typeLine: String?,
    /** Searchable text: the card's text, or all faces' text joined. */
    val oracleText: String?,
    val power: String?,
    val toughness: String?,
    val loyalty: String?,
    val defense: String?,
    val handModifier: String?,
    val lifeModifier: String?,
    val colors: String,              // "WU", WUBRG order
    val colorIdentity: String,
    val keywords: String,            // "Flying|Transform"
    val legalities: String,          // "standard=legal;modern=banned;…" paper formats, fixed order
    val facesJson: String?,          // List<ScryfallFace> as JSON, multi-faced cards only
    val imageUrl: String?,
    val artCropUrl: String?,         // art only; shown with the artist (Scryfall guidelines)
    val artist: String?,
    val setCode: String?,
    val setType: String?,            // Scryfall set_type: "funny" = Un-sets (acorn, stickers, attractions)
    val gameChanger: Boolean,
    val reserved: Boolean,
    val isToken: Boolean,
)

/** unicode61 folds diacritics: "lim-dul" finds Lim-Dûl. */
@Fts4(contentEntity = CardEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "card_fts")
data class CardFts(
    val name: String,
    val typeLine: String?,
    val oracleText: String?,
)

/** Ruling texts are stored once: most are shared (79.7k references -> 34k texts). */
@Entity(tableName = "ruling")
data class RulingEntity(
    @PrimaryKey val id: Int,
    val text: String,
)

@Entity(tableName = "card_ruling", primaryKeys = ["oracleId", "rulingId"], indices = [Index("rulingId")])
data class CardRulingEntity(
    val oracleId: String,
    val rulingId: Int,
    val date: String,                // yyyy-MM-dd
    val source: String,              // "wotc" | "scryfall"
)

@Entity(tableName = "cards_meta")
data class CardsMetaEntity(
    @PrimaryKey val id: Int = 0,
    val oracleUpdatedAt: String,     // Scryfall's updated_at of the files imported
    val rulingsUpdatedAt: String,
    val source: String,              // "seed" | "scryfall"
    val importedAt: Long,
    val cardCount: Int,
    val rulingCount: Int,
)

data class CardHit(
    val oracleId: String,
    val name: String,
    val manaCost: String?,
    val typeLine: String?,
    val isToken: Boolean,
)

data class RulingRow(val date: String, val source: String, val text: String)

data class LegalityRow(
    val oracleId: String,
    val name: String,
    val legalities: String,
    val typeLine: String? = null,
    val oracleText: String? = null,
    val setType: String? = null,
)

@Dao
interface CardsDao {

    /**
     * [match] is an FTS query (see CardSearchQuery). Exact name first, then names starting with
     * what was typed, real cards before tokens, then alphabetical.
     */
    @Query(
        """
        SELECT c.oracleId, c.name, c.manaCost, c.typeLine, c.isToken
        FROM card_fts JOIN card c ON c.rowid = card_fts.rowid
        WHERE card_fts MATCH :match
        ORDER BY (lower(c.name) = :typed) DESC, (lower(c.name) LIKE :typed || '%') DESC,
                 c.isToken, c.name
        LIMIT :limit
        """
    )
    suspend fun search(match: String, typed: String, limit: Int): List<CardHit>

    @Query("SELECT * FROM card WHERE oracleId = :oracleId")
    suspend fun card(oracleId: String): CardEntity?

    @Query(
        """
        SELECT cr.date, cr.source, r.text FROM card_ruling cr JOIN ruling r ON r.id = cr.rulingId
        WHERE cr.oracleId = :oracleId ORDER BY cr.date, cr.rulingId
        """
    )
    suspend fun rulings(oracleId: String): List<RulingRow>

    @Query("SELECT * FROM cards_meta WHERE id = 0")
    suspend fun meta(): CardsMetaEntity?

    /** Cards banned or restricted in at least one paper format (a few hundred rows). */
    @Query(
        "SELECT oracleId, name, legalities, typeLine, oracleText, setType FROM card " +
            "WHERE legalities LIKE '%=banned%' OR legalities LIKE '%=restricted%' ORDER BY name"
    )
    suspend fun bannedOrRestricted(): List<LegalityRow>

    @Query("SELECT * FROM cards_meta WHERE id = 0")
    fun observeMeta(): Flow<CardsMetaEntity?>

    @Query("SELECT COUNT(*) FROM card")
    suspend fun cardCount(): Int

    // ---- importing (inside withTransaction) ----

    @Query("DELETE FROM card") suspend fun deleteCards()
    @Query("DELETE FROM ruling") suspend fun deleteRulings()
    @Query("DELETE FROM card_ruling") suspend fun deleteCardRulings()
    @Query("DELETE FROM cards_meta") suspend fun deleteMeta()

    @Insert suspend fun insertCards(cards: List<CardEntity>)
    @Insert suspend fun insertRulings(rulings: List<RulingEntity>)
    @Insert suspend fun insertCardRulings(refs: List<CardRulingEntity>)
    @Insert suspend fun insertMeta(meta: CardsMetaEntity)
}

@Database(
    entities = [CardEntity::class, CardFts::class, RulingEntity::class, CardRulingEntity::class, CardsMetaEntity::class],
    version = 3,                     // 2: artCropUrl, artist. 3: setType. Destructive: the seed re-imports itself.
    exportSchema = false,
)
abstract class CardsDatabase : RoomDatabase() {
    abstract fun cardsDao(): CardsDao
}
