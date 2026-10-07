package es.alejandro.grandarbiter.data.cards.update

import es.alejandro.grandarbiter.data.cards.scryfall.BulkDataEntry
import es.alejandro.grandarbiter.data.cards.scryfall.BulkDataList
import es.alejandro.grandarbiter.data.cards.scryfall.ScryfallJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Talks to Scryfall's bulk-data API. The OkHttpClient must carry ScryfallHeadersInterceptor
 * (User-Agent + Accept), or Scryfall answers HTTP 400.
 */
class ScryfallBulkClient(
    private val http: OkHttpClient,
    private val baseUrl: String = "https://api.scryfall.com",
    private val json: Json = ScryfallJson,
) {
    /** The bulk-data catalog: one entry per file type, with its updated_at. */
    suspend fun catalog(): List<BulkDataEntry> =
        json.decodeFromString<BulkDataList>(getText("$baseUrl/bulk-data")).data

    /** JSON Lines if offered (what we read best), else the JSON file; asks the entry's details if needed. */
    suspend fun downloadUrl(entry: BulkDataEntry): String {
        entry.jsonlDownloadUri?.let { return it }
        entry.downloadUri?.let { return it }
        val detailsUri = entry.uri ?: throw IOException("no download link for ${entry.type}")
        val details = json.decodeFromString<BulkDataEntry>(getText(detailsUri))
        return details.jsonlDownloadUri ?: details.downloadUri ?: throw IOException("no download link for ${entry.type}")
    }

    /**
     * Streams [url] to [target] (via a .part file, renamed only when complete). Fails on HTTP
     * errors and on a body shorter than its Content-Length, so a cut connection never imports.
     */
    suspend fun download(url: String, target: File, onProgress: (done: Long, total: Long?) -> Unit) =
        withContext(Dispatchers.IO) {
            val part = File(target.path + ".part")
            try {
                fetchTo(url, part, onProgress)
            } catch (e: Throwable) {
                part.delete()   // a half-written file must never be mistaken for a download
                throw e
            }
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) throw IOException("could not save ${target.name}")
        }

    private suspend fun fetchTo(url: String, part: File, onProgress: (done: Long, total: Long?) -> Unit) =
        withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
                val body = response.body
                val total = body.contentLength().takeIf { it > 0 }
                var done = 0L
                body.byteStream().use { input ->
                    part.outputStream().buffered(64 * 1024).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var reported = 0L
                        while (true) {
                            ensureActive()   // cancelling the update stops the download
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            done += n
                            if (done - reported >= PROGRESS_STEP) { onProgress(done, total); reported = done }
                        }
                    }
                }
                onProgress(done, total)
                if (total != null && done != total) throw IOException("download cut short: $done of $total bytes")
            }
        }

    private suspend fun getText(url: String): String = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            response.body.string()
        }
    }

    private companion object {
        const val PROGRESS_STEP = 256L * 1024
    }
}
