package es.alejandro.grandarbiter.data.cards.scryfall

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Scryfall requires an identifying User-Agent and an Accept header on every request (API and
 * image CDN) and answers HTTP 400 without them. OkHttp/Coil defaults ("okhttp/x") don't qualify.
 * Applied only to Scryfall hosts, so other requests keep their defaults.
 */
class ScryfallHeadersInterceptor(private val userAgent: String = USER_AGENT) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!isScryfall(request.url.host)) return chain.proceed(request)
        val accept = if (request.url.host == API_HOST) "application/json;q=0.9,*/*;q=0.8" else "image/avif,image/webp,image/*,*/*;q=0.8"
        return chain.proceed(
            request.newBuilder()
                .header("User-Agent", userAgent)
                .header("Accept", request.header("Accept") ?: accept)
                .build()
        )
    }

    companion object {
        const val USER_AGENT = "GrandArbiter/1.0 (judge reference app; https://github.com/arojasp/Grand-Arbiter)"
        private const val API_HOST = "api.scryfall.com"
        fun isScryfall(host: String) = host == "scryfall.com" || host.endsWith(".scryfall.com") || host.endsWith(".scryfall.io")
    }
}
