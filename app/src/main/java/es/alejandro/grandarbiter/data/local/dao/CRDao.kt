package es.alejandro.grandarbiter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import es.alejandro.grandarbiter.data.local.entities.CREntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CRDao {
    @Query("SELECT * FROM comprehensive_rules ORDER BY ruleNumber ASC")
    fun getAllRules(): Flow<List<CREntity>>

    @Query("SELECT * FROM comprehensive_rules WHERE ruleNumber = :ruleNumber")
    suspend fun getRuleByNumber(ruleNumber: String): CREntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<CREntity>)

    @Query("DELETE FROM comprehensive_rules")
    suspend fun deleteAllRules()

    @Query("SELECT * FROM comprehensive_rules WHERE ruleText LIKE '%' || :query || '%' OR ruleNumber LIKE :query || '%'")
    fun searchRules(query: String): Flow<List<CREntity>>
}
