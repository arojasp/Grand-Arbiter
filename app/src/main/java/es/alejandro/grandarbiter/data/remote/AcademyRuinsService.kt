package es.alejandro.grandarbiter.data.remote

import retrofit2.http.GET
import retrofit2.http.Path

interface AcademyRuinsService {
    @GET("versions")
    suspend fun checkDocumentVersions(): Map<String, String>

    @GET("documents/cr")
    suspend fun getComprehensiveRules(): String // JSON structured

    @GET("documents/ipg")
    suspend fun getIPG(): String // PDF raw or JSON

    @GET("documents/mtr")
    suspend fun getMTR(): String

    @GET("documents/jar")
    suspend fun getJAR(): String
}
