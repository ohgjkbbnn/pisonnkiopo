package com.savoo.scclient.data.remote

import com.savoo.scclient.compat.*
import kotlinx.coroutines.IO
import com.savoo.scclient.compat.Context
import com.savoo.scclient.compat.SharedPreferences
import com.savoo.scclient.data.model.BadgeEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.savoo.scclient.compat.http.OkHttpClient
import com.savoo.scclient.compat.http.Request
class BadgeRepository(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val client: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _badges = MutableStateFlow<Map<Long, BadgeEntry>>(emptyMap())
    val badges = _badges.asStateFlow()

    companion object {
        private const val GIST_API_URL = "https://api.github.com/gists/360fbdb72347b29d58afa990237d1155"
        private const val KEY_BADGES_CACHE = "badges_json_cache"
    }

    init {
        loadFromCache()
        refresh()
    }

    private fun loadFromCache() {
        val cached = prefs.getString(KEY_BADGES_CACHE, null) ?: return
        parseAndSet(cached)
    }

    fun refresh() {
        scope.launch {
            runCatching {
                val apiJson = client.newCall(Request.Builder().url(GIST_API_URL).build())
                    .execute().use { it.body?.string().orEmpty() }
                val rawUrl = JSONObject(apiJson)
                    .getJSONObject("files")
                    .getJSONObject("badges.json")
                    .getString("raw_url")
                val json = client.newCall(Request.Builder().url(rawUrl).build())
                    .execute().use { it.body?.string().orEmpty() }
                if (json.isNotBlank() && json.startsWith("{")) {
                    prefs.edit().putString(KEY_BADGES_CACHE, json).apply()
                    parseAndSet(json)
                }
            }
        }
    }

    private fun parseAndSet(json: String) {
        runCatching {
            val raw = ApiJson.decodeFromString<Map<String, BadgeEntry>>(json)
            _badges.value = raw.mapKeys { it.key.toLongOrNull() ?: 0L }
                .filterKeys { it != 0L }
        }
    }

    fun getBadges(userId: Long): StateFlow<List<String>> =
        badges.map { it[userId]?.badges.orEmpty() }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())
}
