package es.alejandro.grandarbiter.data.remote

import es.alejandro.grandarbiter.data.remote.model.CRRuleModel
import es.alejandro.grandarbiter.data.remote.model.MtrResponse
import retrofit2.http.GET

interface AcademyRuinsService {
        @GET("cr")
    suspend fun getCr(): Map<String, CRRuleModel>

    @GET("mtr")
    suspend fun getMtr(): MtrResponse
}
