package es.alejandro.grandarbiter.data.repository

import es.alejandro.grandarbiter.data.local.dao.CRDao
import es.alejandro.grandarbiter.data.local.entities.CREntity
import es.alejandro.grandarbiter.data.local.entities.NavigationEntity
import es.alejandro.grandarbiter.data.remote.AcademyRuinsService
import es.alejandro.grandarbiter.domain.repository.CRRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class CRRepositoryImpl(
    private val crDao: CRDao,
    private val academyRuinsService: AcademyRuinsService,
    private val settingsDataStore: es.alejandro.grandarbiter.data.local.datastore.SettingsDataStore
) : CRRepository {

    override fun getAllRules(): Flow<List<CREntity>> = crDao.getAllRules()

    override fun searchRules(query: String): Flow<List<CREntity>> = crDao.searchRules(query)

    override suspend fun getRuleByNumber(ruleNumber: String): CREntity? = crDao.getRuleByNumber(ruleNumber)

    override suspend fun downloadCR(): List<CREntity> {
        val rulesMap = academyRuinsService.getCr()
        
        return withContext(Dispatchers.Default) {
            rulesMap.values.map { rule ->
                CREntity(
                    ruleNumber = rule.ruleNumber,
                    ruleText = rule.ruleText,
                    fragment = rule.fragment,
                    examples = rule.examples ?: emptyList(),
                    navigation = NavigationEntity(
                        previousRule = rule.navigation?.previousRule,
                        nextRule = rule.navigation?.nextRule
                    )
                )
            }
        }
    }

    override suspend fun storeRules(rules: List<CREntity>) {
        crDao.deleteAllRules()
        crDao.insertRules(rules)
    }
}
