package com.savoo.scclient.data.remote

import com.savoo.scclient.auth.TokenStore
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.save
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.Sender
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.CancellationException
import kotlin.uuid.Uuid

internal const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

class AuthInterceptor(
    private val clientIdProvider: ClientIdProvider,
    private val tokenStore: TokenStore,
    private val challenge: DataDomeChallenge,
) {

    fun install(client: HttpClient) {
        client.plugin(HttpSend).intercept { request -> intercept(request) }
    }

    private suspend fun Sender.proceed(request: HttpRequestBuilder): HttpClientCall = try {
        execute(request).also { ConnectivityEventBus.notifyReachable() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        ConnectivityEventBus.notifyUnreachable()
        throw e
    }

    private suspend fun Sender.intercept(request: HttpRequestBuilder): HttpClientCall {
        if (!isSoundCloudHost(request.url.host)) return proceed(request)

        val clientId = clientIdProvider.cachedOrFallback()
        if (request.url.parameters["client_id"] == null) request.url.parameters.append("client_id", clientId)
        applyCommonHeaders(request, clientId)

        var call = proceed(request).save()

        val challengeUrl = DataDomeChallenge.challengeUrl(
            call.response.status.value,
            runCatching { call.response.bodyAsText().take(64 * 1024) }.getOrNull(),
        )
        if (challengeUrl != null) {
            if (!challenge.solve(challengeUrl)) return call
            applyCommonHeaders(request, clientId)
            return proceed(request)
        }

        val code = call.response.status.value
        if (code == 401 || code == 403) {
            val freshClientId = clientIdProvider.refresh()
            if (freshClientId == clientId) return call
            request.url.parameters.remove("client_id")
            request.url.parameters.append("client_id", freshClientId)
            applyCommonHeaders(request, freshClientId)
            call = proceed(request)
        }
        return call
    }

    private fun applyCommonHeaders(builder: HttpRequestBuilder, clientId: String) {
        val headers = mapOf(
            "User-Agent" to BROWSER_USER_AGENT,
            "Accept" to "application/json, text/javascript, */*; q=0.1",
            "Origin" to "https://soundcloud.com",
            "Referer" to "https://soundcloud.com/",
            "X-SoundCloud-Client" to clientId,
            "X-Client-UUID" to Uuid.random().toString(),
        ) + listOfNotNull(
            tokenStore.accessToken?.let { "Authorization" to "OAuth $it" },
            tokenStore.webCookies?.let { "Cookie" to it },
        )
        headers.forEach { (name, value) ->
            builder.headers.remove(name)
            builder.headers.append(name, value)
        }
    }
}

internal fun isSoundCloudHost(host: String): Boolean =
    host == "soundcloud.com" || host.endsWith(".soundcloud.com")
