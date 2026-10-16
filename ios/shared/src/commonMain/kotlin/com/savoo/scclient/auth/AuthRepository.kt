package com.savoo.scclient.auth

import com.savoo.scclient.compat.*
import kotlinx.coroutines.IO
import com.savoo.scclient.data.model.TokenResponse
import com.savoo.scclient.data.remote.ApiJson
import com.savoo.scclient.data.remote.ClientIdProvider
import com.savoo.scclient.debug.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.savoo.scclient.compat.http.FormBody
import com.savoo.scclient.compat.http.OkHttpClient
import com.savoo.scclient.compat.http.Request
class AuthRepository(
    private val client: OkHttpClient,
    private val clientIdProvider: ClientIdProvider,
    private val tokenStore: TokenStore,
) {
    suspend fun exchangeCodeForToken(code: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            DebugLog.log(TAG, "exchangeCodeForToken: starting")
            val body = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", clientIdProvider.cachedOrFallback())
                .add("redirect_uri", OAuthConfig.REDIRECT_URI)
                .add("code", code)
                .build()

            val request = Request.Builder()
                .url(OAuthConfig.TOKEN_URL)
                .post(body)
                .build()

            val responseBody = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) error("Token exchange failed: ${resp.code}")
                resp.body?.string().orEmpty()
            }

            val token = ApiJson.decodeFromString<TokenResponse>(responseBody)
            tokenStore.save(token)
            DebugLog.log(TAG, "exchangeCodeForToken: succeeded")
        }.onFailure { e -> DebugLog.log(TAG, "exchangeCodeForToken: failed: $e") }
    }

    fun logout() {
        DebugLog.log(TAG, "logout")
        tokenStore.clear()
        runCatching {
            CookieManager.getInstance().apply {
                removeAllCookies(null)
                flush()
            }
            WebStorage.getInstance().deleteAllData()
        }.onFailure { DebugLog.log(TAG, "clearing web session failed: ${it.message}") }
    }

    companion object {
        private const val TAG = "AuthRepository"
    }
}
