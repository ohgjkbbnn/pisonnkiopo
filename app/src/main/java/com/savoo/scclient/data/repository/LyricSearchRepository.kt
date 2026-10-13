package com.savoo.scclient.data.repository

import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.model.restrictionReason
import com.savoo.scclient.data.remote.GeniusApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class LyricMatch(
    val geniusId: Long,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val snippet: String,
)

@Singleton
class LyricSearchRepository @Inject constructor(
    private val geniusApi: GeniusApi,
    private val trackRepository: TrackRepository,
) {
    suspend fun search(line: String): List<LyricMatch> = withContext(Dispatchers.IO) {
        val body = geniusApi.search(line).use { it.string() }
        val sections = JSONObject(body).optJSONObject("response")?.optJSONArray("sections") ?: return@withContext emptyList()
        val matches = LinkedHashMap<Long, LyricMatch>()
        for (i in 0 until sections.length()) {
            val hits = sections.getJSONObject(i).optJSONArray("hits") ?: continue
            for (j in 0 until hits.length()) {
                val hit = hits.getJSONObject(j)
                if (hit.optString("index") != "lyric" || hit.optString("type") != "song") continue
                val result = hit.optJSONObject("result") ?: continue
                val id = result.optLong("id")
                val artist = result.optString("primary_artist_names").ifBlank { result.optString("artist_names") }
                if (id <= 0L || id in matches || artist.startsWith("Genius", ignoreCase = true)) continue
                val lyrics = hit.optJSONArray("highlights")?.let { highlights ->
                    (0 until highlights.length())
                        .map { highlights.getJSONObject(it) }
                        .firstOrNull { it.optString("property") == "lyrics" }
                        ?.optString("value")
                }.orEmpty()
                matches[id] = LyricMatch(
                    geniusId = id,
                    title = result.optString("title"),
                    artist = artist,
                    artworkUrl = result.optString("song_art_image_thumbnail_url").takeIf { it.startsWith("https://") },
                    snippet = bestSnippetLine(lyrics, line),
                )
            }
        }
        matches.values.take(MAX_MATCHES)
    }

    suspend fun findOnSoundCloud(match: LyricMatch): Track? {
        val candidates = trackRepository.searchTracks("${match.artist} ${match.title}", limit = 20).items
        val title = normalize(stripBrackets(match.title))
        val artist = normalize(match.artist)
        val variantWanted = VARIANT_WORDS.any { match.title.contains(it, ignoreCase = true) }
        return candidates
            .filter { it.restrictionReason() == null }
            .filter { normalize(stripBrackets(it.title)).contains(title) }
            .filter { variantWanted || VARIANT_WORDS.none { word -> it.title.contains(word, ignoreCase = true) } }
            .maxByOrNull { track ->
                val artistHit = normalize(track.user.username).contains(artist) || normalize(track.title).contains(artist)
                (if (artistHit) ARTIST_BONUS else 0L) + (track.playbackCount ?: 0L).coerceAtMost(ARTIST_BONUS - 1)
            }
    }

    private fun bestSnippetLine(lyrics: String, query: String): String {
        val words = normalizeWords(query)
        return lyrics.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .maxByOrNull { line -> normalizeWords(line).count { it in words } }
            ?: query
    }

    private fun normalizeWords(value: String): Set<String> =
        value.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }.toSet()

    private fun normalize(value: String): String = value.lowercase().filter { it.isLetterOrDigit() }

    private fun stripBrackets(value: String): String = value.replace(Regex("""[(\[][^)\]]*[)\]]"""), "")

    companion object {
        const val MIN_WORDS = 4
        private const val MAX_MATCHES = 3
        private const val ARTIST_BONUS = 1_000_000_000_000L
        private val VARIANT_WORDS = listOf("cover", "remix", "nightcore", "sped up", "slowed", "karaoke", "instrumental", "8d")
    }
}
