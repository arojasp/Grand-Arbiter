package es.alejandro.grandarbiter.data.repository

import es.alejandro.grandarbiter.data.local.dao.CRDao
import es.alejandro.grandarbiter.data.local.entities.CREntity
import es.alejandro.grandarbiter.data.local.entities.GlossaryEntity
import es.alejandro.grandarbiter.data.local.entities.NavigationEntity
import es.alejandro.grandarbiter.data.local.entities.TOCEntity
import es.alejandro.grandarbiter.data.local.entities.TOCSubItem
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

    override suspend fun downloadTOC(): List<TOCEntity> {
        val tocResponse = academyRuinsService.getCrTOC()

        return withContext(Dispatchers.Default) {
            tocResponse.map { item ->
                TOCEntity(
                    number = item.number ?: "",
                    title = item.title ?: "",
                    subsections = item.subsections?.map { sub ->
                        TOCSubItem(
                            number = sub.number ?: "",
                            title = sub.title ?: ""
                        )
                    } ?: emptyList()
                )
            }
        }
    }

    override suspend fun storeTOC(toc: List<TOCEntity>) {
        crDao.clearTOC()
        crDao.insertTOC(toc)
    }

    override suspend fun downloadGlossary(): List<GlossaryEntity> {
        val glossaryMap = academyRuinsService.getCrGlossary()

        return withContext(Dispatchers.Default) {
            glossaryMap.values.map { model ->
                GlossaryEntity(
                    term = model.term ?: "",
                    definition = model.definition ?: ""
                )
            }
        }
    }

    override suspend fun storeGlossary(glossary: List<GlossaryEntity>) {
        crDao.clearGlossary()
        crDao.insertGlossary(glossary)
    }

    override fun getTOC(): Flow<List<TOCEntity>> = crDao.getAllTOC()
}
