package com.savoo.scclient.data.remote

import com.savoo.scclient.compat.http.ResponseBody

class GeniusApi(private val rest: RestClient) {

    suspend fun search(query: String): ResponseBody = body("api/search/multi", "q" to query)

    suspend fun fetchPage(url: String): ResponseBody = body(url)

    suspend fun referents(songId: Long, page: Int, perPage: Int = 50, textFormat: String = "plain"): ResponseBody =
        body("api/referents", "song_id" to songId, "page" to page, "per_page" to perPage, "text_format" to textFormat)

    private suspend fun body(path: String, vararg params: Pair<String, Any?>): ResponseBody =
        ResponseBody(rest.text(path, *params).encodeToByteArray(), null)
}
