package es.alejandro.grandarbiter.domain.repository

import es.alejandro.grandarbiter.data.local.entities.CREntity
import es.alejandro.grandarbiter.data.local.entities.TOCEntity
import kotlinx.coroutines.flow.Flow

interface CRRepository {
    fun getAllRules(): Flow<List<CREntity>>
    fun searchRules(query: String): Flow<List<CREntity>>
    suspend fun getRuleByNumber(ruleNumber: String): CREntity?
    fun getTOC(): Flow<List<TOCEntity>>
}
