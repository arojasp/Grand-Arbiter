package es.alejandro.grandarbiter.data.rules.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** tools/rules/build_bundle.py writes it next to bundle.json; always matches it (unit-tested). */
@Serializable
data class RulesManifest(
    val schemaVersion: Int,
    val bundleVersion: Long,
    val generatedAt: String? = null,
    val documents: Map<String, String> = emptyMap(),   // "cr" -> "2026-09-25"
)

/**
 * Reads the rules bundle published in the repo: [baseUrl] points at app/src/main/assets/rules/ on
 * branch main, so merging a rebuilt bundle to main IS publishing it. Same file as the APK's.
 * (OkHttp asks for gzip, so the ~1.5 MB bundle travels as ~300 KB.)
 */
class PublishedRulesClient(
    private val http: OkHttpClient,
    private val baseUrl: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    /** Null if nothing is published there yet (HTTP 404). */
    suspend fun manifest(): RulesManifest? {
        val text = get("manifest.json", allowMissing = true) ?: return null
        return json.decodeFromString<RulesManifest>(text)
    }

    suspend fun bundle(): String = get("bundle.json", allowMissing = false)!!

    private suspend fun get(file: String, allowMissing: Boolean): String? = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/" + file
        // no-cache: always ask GitHub for the current file, not a copy cached on the phone.
        val request = Request.Builder().url(url).header("Cache-Control", "no-cache").build()
        http.newCall(request).execute().use { response ->
            when {
                response.code == 404 && allowMissing -> null
                !response.isSuccessful -> throw IOException("HTTP ${response.code} for $url")
                else -> response.body.string()
            }
        }
    }
}
