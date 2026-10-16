package com.savoo.scclient.data.remote

import com.savoo.scclient.data.model.LyricsSearchResult

class LyricsApi(private val rest: RestClient) {

    suspend fun search(trackName: String, artistName: String): List<LyricsSearchResult> =
        rest.get("api/search", "track_name" to trackName, "artist_name" to artistName)

    suspend fun searchQuery(query: String): List<LyricsSearchResult> = rest.get("api/search", "q" to query)

    suspend fun searchByTitle(trackName: String): List<LyricsSearchResult> = rest.get("api/search", "track_name" to trackName)
}
