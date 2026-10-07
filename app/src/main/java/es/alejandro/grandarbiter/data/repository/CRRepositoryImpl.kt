package es.alejandro.grandarbiter.data.repository

import es.alejandro.grandarbiter.data.local.dao.CRDao
import es.alejandro.grandarbiter.data.local.entities.CREntity
import es.alejandro.grandarbiter.data.local.entities.TOCEntity
import es.alejandro.grandarbiter.domain.repository.CRRepository
import kotlinx.coroutines.flow.Flow

/** Reads the CR tables in AppDatabase. Superseded by rules.db; removed once the reader moves there. */
class CRRepositoryImpl(
    private val crDao: CRDao,
) : CRRepository {

    override fun getAllRules(): Flow<List<CREntity>> = crDao.getAllRules()

    override fun searchRules(query: String): Flow<List<CREntity>> = crDao.searchRules(query)

    override suspend fun getRuleByNumber(ruleNumber: String): CREntity? = crDao.getRuleByNumber(ruleNumber)

    override fun getTOC(): Flow<List<TOCEntity>> = crDao.getAllTOC()
}
