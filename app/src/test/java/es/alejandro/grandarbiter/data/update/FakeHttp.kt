package es.alejandro.grandarbiter.data.update

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import java.util.Collections

/** An OkHttpClient that never touches the network: answers by URL, records every request. */
class FakeHttp {
    val requests: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val routes = mutableMapOf<String, (Request) -> Response>()

    fun on(url: String, code: Int = 200, body: String = "") = on(url, code, body.toByteArray())
    fun on(url: String, code: Int = 200, body: ByteArray) = apply { routes[url] = { respond(it, code, body.toResponseBody(null)) } }
    fun onBody(url: String, body: ResponseBody) = apply { routes[url] = { respond(it, 200, body) } }
    fun onThrow(url: String, error: java.io.IOException) = apply { routes[url] = { throw error } }

    fun count(url: String) = requests.count { it == url }

    val client: OkHttpClient = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        val url = chain.request().url.toString()
        requests += url
        val handler = routes[url] ?: { r: Request -> respond(r, 404, "".toResponseBody(null)) }
        handler(chain.request())
    }).build()

    private fun respond(req: Request, code: Int, body: ResponseBody) =
        Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("x").body(body).build()

    companion object {
        /** A body that promises [promised] bytes and sends 10: a connection cut mid-download. */
        fun truncated(promised: Long) = object : ResponseBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength() = promised
            override fun source(): BufferedSource = Buffer().write(ByteArray(10))
        }
    }
}

object SilentLog : UpdateLog {
    override fun info(message: String) = Unit
    override fun warn(message: String, error: Throwable?) = Unit
}
