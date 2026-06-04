package es.alejandro.grandarbiter.domain.repository

import es.alejandro.grandarbiter.data.local.entities.CREntity
import kotlinx.coroutines.flow.Flow

interface CRRepository {
    fun getAllRules(): Flow<List<CREntity>>
    fun searchRules(query: String): Flow<List<CREntity>>
    suspend fun getRuleByNumber(ruleNumber: String): CREntity?
    suspend fun downloadCR():List<CREntity>
    suspend fun storeRules(rules: List<CREntity>)
}
