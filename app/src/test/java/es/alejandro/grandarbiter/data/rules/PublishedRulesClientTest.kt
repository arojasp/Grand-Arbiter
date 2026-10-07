package es.alejandro.grandarbiter.data.rules

import es.alejandro.grandarbiter.data.rules.update.PublishedRulesClient
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PublishedRulesClientTest {

    private fun client(code: Int, body: String = "") = PublishedRulesClient(
        OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(body.toResponseBody("text/plain".toMediaType())).build()
        }).build(),
        baseUrl = "https://raw.githubusercontent.com/o/r/main/app/src/main/assets/rules/",
    )

    @Test fun manifest_parses() = runBlocking {
        val m = client(200, """{"schemaVersion":1,"bundleVersion":2026100801,"documents":{"cr":"2026-09-25"},"extra":1}""").manifest()!!
        assertEquals(2026100801L, m.bundleVersion)
        assertEquals("2026-09-25", m.documents["cr"])
    }

    @Test fun nothingPublishedYet_isNull() = runBlocking {
        assertNull(client(404).manifest())
    }

    @Test fun serverError_isAnIOException() = runBlocking {
        assertTrue(runCatching { client(500).manifest() }.exceptionOrNull() is IOException)
        assertTrue(runCatching { client(404).bundle() }.exceptionOrNull() is IOException)
    }
}
