package es.alejandro.grandarbiter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import es.alejandro.grandarbiter.data.local.entities.CardCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM card_cache WHERE id = :cardId")
    fun getCardById(cardId: String): Flow<CardCacheEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: CardCacheEntity)

    @Query("DELETE FROM card_cache WHERE cachedAt < :timestamp")
    suspend fun clearOldCache(timestamp: Long)
}
