package es.alejandro.grandarbiter.domain.repository

import es.alejandro.grandarbiter.data.local.entities.CREntity
import es.alejandro.grandarbiter.data.local.entities.GlossaryEntity
import es.alejandro.grandarbiter.data.local.entities.TOCEntity
import kotlinx.coroutines.flow.Flow

interface CRRepository {
    fun getAllRules(): Flow<List<CREntity>>
    fun searchRules(query: String): Flow<List<CREntity>>
    suspend fun getRuleByNumber(ruleNumber: String): CREntity?
    suspend fun downloadCR(): List<CREntity>
    suspend fun storeRules(rules: List<CREntity>)

    suspend fun downloadTOC(): List<TOCEntity>
    suspend fun storeTOC(toc: List<TOCEntity>)

    suspend fun downloadGlossary(): List<GlossaryEntity>
    suspend fun storeGlossary(glossary: List<GlossaryEntity>)

    fun getTOC(): Flow<List<TOCEntity>>
}
