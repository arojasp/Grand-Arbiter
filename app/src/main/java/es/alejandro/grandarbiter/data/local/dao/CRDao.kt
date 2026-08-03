package es.alejandro.grandarbiter.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import es.alejandro.grandarbiter.data.local.entities.CREntity
import es.alejandro.grandarbiter.data.local.entities.GlossaryEntity
import es.alejandro.grandarbiter.data.local.entities.TOCEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CRDao {
    // --- Rules ---
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

    // --- TOC ---
    @Query("SELECT * FROM table_of_contents ORDER BY number ASC")
    fun getAllTOC(): Flow<List<TOCEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTOC(tocEntries: List<TOCEntity>)

    @Query("DELETE FROM table_of_contents")
    suspend fun clearTOC()

    // --- Glossary ---
    @Query("SELECT * FROM glossary ORDER BY term ASC")
    fun getAllGlossaryTerms(): Flow<List<GlossaryEntity>>

    @Query("SELECT * FROM glossary WHERE term LIKE :query || '%'")
    fun searchGlossaryTerms(query: String): Flow<List<GlossaryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGlossary(glossaryEntries: List<GlossaryEntity>)

    @Query("DELETE FROM glossary")
    suspend fun clearGlossary()
}
