package com.savoo.scclient.data.remote

import com.savoo.scclient.compat.IOException
import com.savoo.scclient.compat.http.HttpException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.utils.io.CancellationException
import kotlinx.serialization.json.Json

val ApiJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
}

class RestClient(val http: HttpClient, private val baseUrl: String) {

    fun url(path: String): String = if (path.startsWith("http")) path else baseUrl + path.removePrefix("/")

    suspend fun raw(path: String, vararg params: Pair<String, Any?>, block: HttpRequestBuilder.() -> Unit = {}): HttpResponse {
        val response = try {
            http.get(url(path)) {
                params.forEach { (k, v) -> if (v != null) parameter(k, v) }
                block()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            throw e
        } catch (e: Throwable) {
            throw IOException(e.message, e)
        }
        if (!response.status.isSuccess()) throw HttpException(response.status.value, "HTTP ${response.status.value}")
        return response
    }

    suspend inline fun <reified T> get(path: String, vararg params: Pair<String, Any?>): T {
        val text = raw(path, *params).bodyAsText()
        return ApiJson.decodeFromString<T>(text)
    }

    suspend fun text(path: String, vararg params: Pair<String, Any?>): String = raw(path, *params).bodyAsText()
}
