package com.savoo.scclient.data.remote

import com.savoo.scclient.compat.*
import com.savoo.scclient.compat.SharedPreferences
import com.savoo.scclient.compat.BuildConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.savoo.scclient.compat.http.OkHttpClient
import com.savoo.scclient.compat.http.Request
class ClientIdProvider(
    private val prefs: SharedPreferences,
    private val plainClient: OkHttpClient,
) {
    private val mutex = Mutex()
    @Volatile private var lastRefreshAttemptAt = 0L

    companion object {
        private const val KEY_CLIENT_ID = "sc_client_id"
        private const val MIN_REFRESH_INTERVAL_MS = 5 * 60 * 1000L
        private val APP_JS_REGEX = Regex("""src="(https://a-v2\.sndcdn\.com/assets/[^"]+\.js)"""")
        private val CLIENT_ID_REGEX = Regex("""client_id[=:]"?([a-zA-Z0-9]{32})"?""")
    }

    fun cachedOrFallback(): String =
        prefs.getString(KEY_CLIENT_ID, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.SC_CLIENT_ID_FALLBACK

    fun setManualOverride(id: String) {
        prefs.edit().putString(KEY_CLIENT_ID, id).apply()
    }

    suspend fun refresh(force: Boolean = false): String = mutex.withLock {
        val now = currentTimeMillis()
        if (!force && lastRefreshAttemptAt != 0L && now - lastRefreshAttemptAt < MIN_REFRESH_INTERVAL_MS) {
            return@withLock cachedOrFallback()
        }
        lastRefreshAttemptAt = now
        runCatching {
            val homepage = plainClient.newCall(
                Request.Builder().url("https://soundcloud.com").build()
            ).execute().use { it.body?.string().orEmpty() }

            val scriptUrls = APP_JS_REGEX.findAll(homepage).map { it.groupValues[1] }.toList()

            for (scriptUrl in scriptUrls.asReversed()) {
                val js = plainClient.newCall(Request.Builder().url(scriptUrl).build())
                    .execute().use { it.body?.string().orEmpty() }
                val match = CLIENT_ID_REGEX.find(js)
                if (match != null) {
                    val id = match.groupValues[1]
                    prefs.edit().putString(KEY_CLIENT_ID, id).apply()
                    return@runCatching id
                }
            }
            null
        }.getOrNull() ?: cachedOrFallback()
    }
}
