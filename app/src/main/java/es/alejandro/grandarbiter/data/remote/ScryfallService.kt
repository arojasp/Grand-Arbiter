package es.alejandro.grandarbiter.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ScryfallService {
    @GET("cards/search")
    suspend fun searchCards(
        @Query("q") query: String,
        @Query("lang") language: String? = "en"
    ): String // Placeholder for Scryfall search response

    @GET("cards/{id}")
    suspend fun getCardById(@Path("id") id: String): String

    @GET("cards/{id}/rulings")
    suspend fun getCardRulings(@Path("id") id: String): String
}
