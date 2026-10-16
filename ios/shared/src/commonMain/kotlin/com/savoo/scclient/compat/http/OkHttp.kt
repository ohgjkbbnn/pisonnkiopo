package com.savoo.scclient.compat.http

import com.savoo.scclient.compat.IOException
import com.savoo.scclient.compat.InputStream
import com.savoo.scclient.compat.encodeUrlComponent
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.takeFrom
import kotlinx.coroutines.runBlocking

class HttpException(private val code: Int, message: String? = null) : Exception(message ?: "HTTP $code") {
    fun code(): Int = code
}

class OkHttpClient internal constructor(internal val client: HttpClient, internal val headers: Map<String, String>) {
    constructor() : this(defaultClient, emptyMap())

    fun newCall(request: Request): Call = Call(this, request)

    fun newBuilder(): Builder = Builder(headers.toMutableMap())

    class Builder internal constructor(private val headers: MutableMap<String, String> = mutableMapOf()) {
        constructor() : this(mutableMapOf())
        fun header(name: String, value: String) = apply { headers[name] = value }
        fun build(): OkHttpClient = OkHttpClient(defaultClient, headers)
    }

    companion object {
        internal val defaultClient by lazy {
            HttpClient {
                expectSuccess = false
                install(HttpTimeout) {
                    connectTimeoutMillis = 15_000
                    socketTimeoutMillis = 30_000
                }
            }
        }
    }
}

class Call internal constructor(private val owner: OkHttpClient, private val request: Request) {
    private var canceled = false

    fun execute(): Response = runBlocking {
        val response = try {
            owner.client.request(request.url) {
                method = HttpMethod(request.method)
                owner.headers.forEach { (k, v) -> headers.append(k, v) }
                request.headers.forEach { (k, v) -> headers.remove(k); headers.append(k, v) }
                request.body?.let { body ->
                    headers.remove("Content-Type")
                    headers.append("Content-Type", body.contentType)
                    setBody(body.bytes)
                }
            }
        } catch (e: Throwable) {
            throw IOException(e.message, e)
        }
        Response(
            code = response.status.value,
            message = response.status.description,
            headers = response.headers.entries().associate { it.key.lowercase() to it.value.firstOrNull().orEmpty() },
            body = ResponseBody(response.readRawBytes(), response.headers["Content-Type"]),
            request = request,
        )
    }

    fun cancel() {
        canceled = true
    }

    fun isCanceled(): Boolean = canceled
}

class Request internal constructor(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val body: RequestBody?,
) {
    fun newBuilder(): Builder = Builder().also { b ->
        b.url(url)
        headers.forEach { (k, v) -> b.header(k, v) }
        if (method == "POST" && body != null) b.post(body)
    }

    class Builder {
        private var url: String = ""
        private var method = "GET"
        private val headers = LinkedHashMap<String, String>()
        private var body: RequestBody? = null

        fun url(url: String) = apply { this.url = url }
        fun url(url: HttpUrl) = apply { this.url = url.toString() }
        fun header(name: String, value: String) = apply { headers[name] = value }
        fun addHeader(name: String, value: String) = apply { headers[name] = value }
        fun get() = apply { method = "GET" }
        fun post(body: RequestBody) = apply { method = "POST"; this.body = body }
        fun put(body: RequestBody) = apply { method = "PUT"; this.body = body }
        fun delete() = apply { method = "DELETE" }
        fun build(): Request = Request(url, method, headers.toMap(), body)
    }
}

open class RequestBody(val bytes: ByteArray, val contentType: String)

class FormBody private constructor(bytes: ByteArray) : RequestBody(bytes, "application/x-www-form-urlencoded") {
    class Builder {
        private val parts = ArrayList<String>()
        fun add(name: String, value: String) = apply { parts += "${name.encodeUrlComponent()}=${value.encodeUrlComponent()}" }
        fun build(): FormBody = FormBody(parts.joinToString("&").encodeToByteArray())
    }
}

fun String.toRequestBody(contentType: String = "application/json; charset=utf-8"): RequestBody =
    RequestBody(encodeToByteArray(), contentType)

class Response internal constructor(
    val code: Int,
    val message: String,
    private val headers: Map<String, String>,
    val body: ResponseBody?,
    val request: Request,
) : AutoCloseable {
    val isSuccessful: Boolean get() = code in 200..299
    fun header(name: String): String? = headers[name.lowercase()]
    fun peekBody(byteCount: Long): ResponseBody = body ?: ResponseBody(ByteArray(0), null)
    override fun close() = Unit
}

class ResponseBody internal constructor(private val bytes: ByteArray, private val type: String?) : AutoCloseable {
    fun string(): String = bytes.decodeToString()
    fun bytes(): ByteArray = bytes
    fun byteStream(): InputStream = InputStream(bytes)
    fun contentLength(): Long = bytes.size.toLong()
    override fun close() = Unit
}

class HttpUrl private constructor(private val builder: URLBuilder) {
    val host: String get() = builder.host
    fun queryParameter(name: String): String? = builder.parameters[name]
    fun newBuilder(): Builder = Builder(URLBuilder().takeFrom(builder))
    override fun toString(): String = builder.buildString()

    class Builder internal constructor(private val b: URLBuilder) {
        fun addQueryParameter(name: String, value: String?) = apply { if (value != null) b.parameters.append(name, value) }
        fun setQueryParameter(name: String, value: String?) = apply {
            b.parameters.remove(name)
            if (value != null) b.parameters.append(name, value)
        }
        fun removeAllQueryParameters(name: String) = apply { b.parameters.remove(name) }
        fun addPathSegment(segment: String) = apply { b.pathSegments = b.pathSegments + segment }
        fun build(): HttpUrl = HttpUrl(URLBuilder().takeFrom(b))
    }

    companion object {
        fun String.toHttpUrlOrNull(): HttpUrl? = runCatching { HttpUrl(URLBuilder().takeFrom(Url(this))) }.getOrNull()
        fun String.toHttpUrl(): HttpUrl = toHttpUrlOrNull() ?: throw IllegalArgumentException("bad url $this")
    }
}
