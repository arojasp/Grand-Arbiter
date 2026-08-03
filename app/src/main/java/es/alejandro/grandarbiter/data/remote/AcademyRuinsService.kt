package es.alejandro.grandarbiter.data.remote

import es.alejandro.grandarbiter.data.remote.model.CRGlossaryTerm
import es.alejandro.grandarbiter.data.remote.model.CRRuleModel
import es.alejandro.grandarbiter.data.remote.model.CRTOCItem
import es.alejandro.grandarbiter.data.remote.model.MtrResponse
import retrofit2.http.GET

interface AcademyRuinsService {
    @GET("cr")
    suspend fun getCr(): Map<String, CRRuleModel>

    @GET("cr/toc")
    suspend fun getCrTOC(): List<CRTOCItem>

    @GET("cr/glossary")
    suspend fun getCrGlossary(): Map<String, CRGlossaryTerm>

    @GET("mtr")
    suspend fun getMtr(): MtrResponse
}
