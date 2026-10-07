package es.alejandro.grandarbiter.data.cards

import es.alejandro.grandarbiter.data.cards.scryfall.BulkDataEntry
import es.alejandro.grandarbiter.data.cards.scryfall.ScryfallHeadersInterceptor
import es.alejandro.grandarbiter.data.cards.scryfall.decompressedIfGzip
import es.alejandro.grandarbiter.data.cards.update.ScryfallBulkClient
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPOutputStream

/** No network: a fake interceptor answers like Scryfall. */
class ScryfallBulkClientTest {

    @get:Rule val tmp = TemporaryFolder()

    private val requests = mutableListOf<Request>()

    private fun client(answer: (Request) -> Response): ScryfallBulkClient {
        val http = OkHttpClient.Builder()
            .addInterceptor(ScryfallHeadersInterceptor())
            .addInterceptor(Interceptor { chain -> requests += chain.request(); answer(chain.request()) })
            .build()
        return ScryfallBulkClient(http, baseUrl = "https://api.scryfall.com")
    }

    private fun response(req: Request, code: Int = 200, body: ResponseBody = "".toResponseBody(null)) =
        Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("x").body(body).build()

    private val catalogJson = """
        {"object":"list","data":[
          {"type":"oracle_cards","updated_at":"2026-10-14T09:00:00.000+00:00","uri":"https://api.scryfall.com/bulk-data/1",
           "jsonl_download_uri":"https://data.scryfall.io/oracle-cards/o.jsonl.gz","compressed_size":10,"new_field":1},
          {"type":"rulings","updated_at":"2026-10-14T09:00:00.000+00:00","uri":"https://api.scryfall.com/bulk-data/2"}
        ]}
    """.trimIndent()

    @Test
    fun catalog_parses_andSendsScryfallHeaders() = runBlocking {
        val c = client { response(it, body = catalogJson.toResponseBody("application/json".toMediaType())) }
        val catalog = c.catalog()
        assertEquals(listOf("oracle_cards", "rulings"), catalog.map { it.type })
        assertTrue(requests.single().header("User-Agent")!!.startsWith("GrandArbiter/"))
        assertTrue(requests.single().header("Accept")!!.contains("application/json"))
    }

    @Test
    fun downloadUrl_prefersJsonl_elseAsksTheEntryDetails() = runBlocking {
        val c = client {
            response(it, body = """{"type":"rulings","updated_at":"x","download_uri":"https://data.scryfall.io/r.json"}"""
                .toResponseBody("application/json".toMediaType()))
        }
        assertEquals("https://data.scryfall.io/o.jsonl.gz",
            c.downloadUrl(BulkDataEntry("oracle_cards", "x", jsonlDownloadUri = "https://data.scryfall.io/o.jsonl.gz")))
        assertEquals("https://data.scryfall.io/r.json",
            c.downloadUrl(BulkDataEntry("rulings", "x", uri = "https://api.scryfall.com/bulk-data/2")))
    }

    @Test
    fun download_writesTheFile_andReportsProgress() = runBlocking {
        val bytes = ByteArray(600_000) { (it % 251).toByte() }
        val c = client { response(it, body = bytes.toResponseBody("application/gzip".toMediaType())) }
        val target = tmp.root.resolve("o.gz")
        val progress = mutableListOf<Long>()
        c.download("https://data.scryfall.io/o.jsonl.gz", target) { done, total -> progress += done; assertEquals(600_000L, total) }
        assertTrue(target.readBytes().contentEquals(bytes))
        assertEquals(600_000L, progress.last())
        assertFalse(tmp.root.resolve("o.gz.part").exists())
    }

    @Test
    fun download_cutShort_failsAndKeepsNoFile() = runBlocking {
        val truncated = object : ResponseBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength() = 1_000L                           // promises more than it sends
            override fun source(): BufferedSource = Buffer().write(ByteArray(10))
        }
        val c = client { response(it, body = truncated) }
        val target = tmp.root.resolve("o.gz")
        val error = runCatching { c.download("https://data.scryfall.io/o", target) { _, _ -> } }.exceptionOrNull()
        assertTrue(error is IOException)
        assertFalse(target.exists())
    }

    @Test
    fun httpError_isAnIOException() = runBlocking {
        val c = client { response(it, code = 400) }
        assertTrue(runCatching { c.catalog() }.exceptionOrNull() is IOException)
    }

    @Test
    fun decompressedIfGzip_handlesBothForms() {
        val plain = "{\"a\":1}\n".toByteArray()
        val gz = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(plain) } }.toByteArray()
        assertTrue(gz.inputStream().decompressedIfGzip().readBytes().contentEquals(plain))
        assertTrue(plain.inputStream().decompressedIfGzip().readBytes().contentEquals(plain))
    }
}
