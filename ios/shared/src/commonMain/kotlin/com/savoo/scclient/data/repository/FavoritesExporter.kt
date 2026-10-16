package com.savoo.scclient.data.repository

import com.savoo.scclient.compat.Context
import com.savoo.scclient.compat.File
import com.savoo.scclient.compat.Instant
import com.savoo.scclient.compat.Uri
import com.savoo.scclient.compat.contentResolver
import com.savoo.scclient.compat.currentTimeMillis
import com.savoo.scclient.data.local.FavoritesDao
import com.savoo.scclient.data.local.LocalPlaylistDao
import com.savoo.scclient.data.model.FavoriteArtist
import com.savoo.scclient.data.model.FavoritePlaylist
import com.savoo.scclient.data.model.FavoriteTrack
import com.savoo.scclient.data.model.LocalPlaylist
import com.savoo.scclient.data.model.LocalPlaylistTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class FavoritesExport(
    val version: Int = 1,
    val exportedAt: String,
    val tracks: List<FavoriteTrack>,
    val artists: List<FavoriteArtist>,
    val playlists: List<FavoritePlaylist>,
)

class EmptyFavoritesBackupException : IllegalStateException("Favorites are empty, the existing backup was kept")
class FavoritesExporter(
    private val favoritesDao: FavoritesDao,
    private val localPlaylistDao: LocalPlaylistDao,
    private val context: Context,
) {
    suspend fun exportToFile(uri: Uri, protectExistingBackup: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        val temp = File.createTempFile("favorites_export", ".json", context.cacheDir)
        try {
            if (protectExistingBackup && isFavoritesEmpty() && destinationHasFavorites(uri)) {
                throw EmptyFavoritesBackupException()
            }
            temp.writeText(buildExport())
            val output = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IllegalStateException("Cannot open file for writing")
            output.use { it.write(temp.readBytes()) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } catch (e: OutOfMemoryError) {
            Result.failure(IllegalStateException("Not enough memory to export favorites"))
        } finally {
            temp.delete()
        }
    }

    private suspend fun buildExport(): String {
        val tracks = buildJsonArray {
            var offset = 0
            while (true) {
                val page = favoritesDao.getTracksPage(EXPORT_PAGE_SIZE, offset)
                page.forEach { t ->
                    add(buildJsonObject {
                        put("trackId", t.trackId)
                        put("title", t.title)
                        put("username", t.username)
                        put("artworkUrl", t.artworkUrl)
                        put("durationMs", t.durationMs)
                        put("permalinkUrl", t.permalinkUrl)
                        put("userId", t.userId)
                        put("userAvatarUrl", t.userAvatarUrl)
                        put("addedAt", t.addedAt)
                    })
                }
                if (page.size < EXPORT_PAGE_SIZE) break
                offset += page.size
            }
        }
        val root = buildJsonObject {
            put("version", 1)
            put("exportedAt", Instant.now().toString())
            put("tracks", tracks)
            put("artists", buildJsonArray {
                favoritesDao.getAllArtistsSync().forEach { a ->
                    add(buildJsonObject {
                        put("artistId", a.artistId)
                        put("username", a.username)
                        put("fullName", a.fullName)
                        put("avatarUrl", a.avatarUrl)
                        put("followersCount", a.followersCount)
                        put("permalinkUrl", a.permalinkUrl)
                        put("addedAt", a.addedAt)
                    })
                }
            })
            put("playlists", buildJsonArray {
                favoritesDao.getAllPlaylistsSync().forEach { p ->
                    add(buildJsonObject {
                        put("playlistId", p.playlistId)
                        put("title", p.title)
                        put("artworkUrl", p.artworkUrl)
                        put("trackCount", p.trackCount.toLong())
                        put("username", p.username)
                        put("permalinkUrl", p.permalinkUrl)
                        put("addedAt", p.addedAt)
                    })
                }
            })
            put("localPlaylists", buildJsonArray {
                localPlaylistDao.getAllSync().forEach { p ->
                    add(buildJsonObject {
                        put("id", p.id)
                        put("title", p.title)
                        put("createdAt", p.createdAt)
                        put("updatedAt", p.updatedAt)
                    })
                }
            })
            put("localPlaylistTracks", buildJsonArray {
                localPlaylistDao.getAllTracksSync().forEach { t ->
                    add(buildJsonObject {
                        put("playlistId", t.playlistId)
                        put("trackId", t.trackId)
                        put("position", t.position.toLong())
                        put("addedAt", t.addedAt)
                        put("title", t.title)
                        put("username", t.username)
                        put("artworkUrl", t.artworkUrl)
                        put("durationMs", t.durationMs)
                        put("permalinkUrl", t.permalinkUrl)
                        put("userId", t.userId)
                        put("userAvatarUrl", t.userAvatarUrl)
                        put("genre", t.genre)
                    })
                }
            })
        }
        return ExportJson.encodeToString(JsonElement.serializer(), root)
    }

    private suspend fun isFavoritesEmpty(): Boolean =
        favoritesDao.trackCount() == 0 && favoritesDao.artistCount() == 0 && favoritesDao.playlistCount() == 0 &&
            localPlaylistDao.playlistCount() == 0

    private suspend fun destinationHasFavorites(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: return@runCatching false
            val root = Json.parseToJsonElement(text) as? JsonObject ?: return@runCatching false
            FAVORITE_ARRAYS.any { (root[it] as? JsonArray)?.isNotEmpty() == true }
        }.getOrDefault(false)
    }

    suspend fun importFromFile(
        uri: Uri,
        onProgress: (ImportProgress) -> Unit = {},
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        val counter = ImportCounter()
        val localPlaylistIds = HashMap<Long, Long>()
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: throw IllegalStateException("Cannot open file for reading")
            val root = Json.parseToJsonElement(text) as? JsonObject
                ?: throw IllegalArgumentException("Not a favorites export")
            val totalEntries = root.values.sumOf { (it as? JsonArray)?.size ?: 0 }.coerceAtLeast(1)
            var processed = 0
            val report = {
                onProgress(ImportProgress((processed.toFloat() / totalEntries).coerceIn(0f, 1f), counter.toResult()))
            }
            for ((name, value) in root) {
                when (name) {
                    "version" -> {
                        val version = (value as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt() ?: 1
                        if (version > 1) throw IllegalArgumentException("Unsupported export version: $version")
                    }
                    "tracks" -> readEntries(value, counter, report, { processed += it }, ::toTrack, favoritesDao::addTracks, favoritesDao::addTrack) {
                        counter.tracks += it
                    }
                    "artists" -> readEntries(value, counter, report, { processed += it }, ::toArtist, favoritesDao::addArtists, favoritesDao::addArtist) {
                        counter.artists += it
                    }
                    "playlists" -> readEntries(value, counter, report, { processed += it }, ::toPlaylist, favoritesDao::addPlaylists, favoritesDao::addPlaylist) {
                        counter.playlists += it
                    }
                    "localPlaylists" -> readEntries(
                        value, counter, report, { processed += it }, ::toLocalPlaylist,
                        { batch -> batch.forEach { importLocalPlaylist(it, localPlaylistIds) } },
                        { importLocalPlaylist(it, localPlaylistIds) },
                    ) {
                        counter.playlists += it
                    }
                    "localPlaylistTracks" -> readEntries(
                        value, counter, report, { processed += it }, ::toLocalPlaylistTrack,
                        { batch -> importLocalPlaylistTracks(batch, localPlaylistIds) },
                        { importLocalPlaylistTracks(listOf(it), localPlaylistIds) },
                    ) {}
                }
            }
            Result.success(counter.toResult())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(ImportFailedException(e, counter.toResult()))
        } catch (e: OutOfMemoryError) {
            Result.failure(ImportFailedException(IllegalStateException("Not enough memory to read this file"), counter.toResult()))
        }
    }

    private class ImportCounter {
        var tracks = 0
        var artists = 0
        var playlists = 0
        var skipped = 0

        fun toResult() = ImportResult(tracks, artists, playlists, skipped)
    }

    private suspend fun <T> readEntries(
        value: JsonElement,
        counter: ImportCounter,
        report: () -> Unit,
        onProcessed: (Int) -> Unit,
        map: (Map<String, String?>) -> T,
        insertBatch: suspend (List<T>) -> Unit,
        insertOne: suspend (T) -> Unit,
        onImported: (Int) -> Unit,
    ) {
        val array = value as? JsonArray ?: return
        val batch = ArrayList<T>(IMPORT_BATCH_SIZE)
        suspend fun flush() {
            if (batch.isEmpty()) return
            try {
                insertBatch(batch)
                onImported(batch.size)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                batch.forEach { entry ->
                    try {
                        insertOne(entry)
                        onImported(1)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        counter.skipped++
                    }
                }
            }
            batch.clear()
            report()
        }
        for (element in array) {
            currentCoroutineContext().ensureActive()
            onProcessed(1)
            val obj = element as? JsonObject
            if (obj == null) {
                counter.skipped++
                continue
            }
            val entry = try {
                map(obj.toFlatMap())
            } catch (e: IllegalArgumentException) {
                counter.skipped++
                null
            }
            if (entry != null) {
                batch.add(entry)
                if (batch.size >= IMPORT_BATCH_SIZE) flush()
            }
        }
        flush()
    }

    private fun toTrack(t: Map<String, String?>) = FavoriteTrack(
        trackId = t.requireLong("trackId"),
        title = t.requireText("title"),
        username = t.requireText("username"),
        artworkUrl = t.optionalUrl("artworkUrl"),
        durationMs = t.requireLong("durationMs"),
        permalinkUrl = t.optionalUrl("permalinkUrl"),
        userId = t.requireLong("userId"),
        userAvatarUrl = t.optionalUrl("userAvatarUrl"),
        addedAt = t.optionalLong("addedAt") ?: currentTimeMillis(),
    )

    private fun toArtist(a: Map<String, String?>) = FavoriteArtist(
        artistId = a.requireLong("artistId"),
        username = a.requireText("username"),
        fullName = a.optionalString("fullName")?.take(MAX_IMPORT_TEXT_CHARS),
        avatarUrl = a.optionalUrl("avatarUrl"),
        followersCount = a.optionalLong("followersCount")?.takeIf { it > 0 },
        permalinkUrl = a.optionalUrl("permalinkUrl"),
        addedAt = a.optionalLong("addedAt") ?: currentTimeMillis(),
    )

    private suspend fun importLocalPlaylist(playlist: LocalPlaylist, ids: MutableMap<Long, Long>) {
        ids[playlist.id] = localPlaylistDao.findId(playlist.title, playlist.createdAt)
            ?: localPlaylistDao.insert(playlist.copy(id = 0))
    }

    private suspend fun importLocalPlaylistTracks(tracks: List<LocalPlaylistTrack>, ids: Map<Long, Long>) {
        val mapped = tracks.mapNotNull { track -> ids[track.playlistId]?.let { track.copy(playlistId = it) } }
        if (mapped.isNotEmpty()) localPlaylistDao.insertTracks(mapped)
    }

    private fun toLocalPlaylist(p: Map<String, String?>): LocalPlaylist {
        val createdAt = p.optionalLong("createdAt") ?: currentTimeMillis()
        return LocalPlaylist(
            id = p.requireLong("id"),
            title = p.requireText("title"),
            createdAt = createdAt,
            updatedAt = p.optionalLong("updatedAt") ?: createdAt,
        )
    }

    private fun toLocalPlaylistTrack(t: Map<String, String?>) = LocalPlaylistTrack(
        playlistId = t.requireLong("playlistId"),
        trackId = t.requireLong("trackId"),
        position = t.requireLong("position").toInt(),
        addedAt = t.optionalLong("addedAt") ?: currentTimeMillis(),
        title = t.requireText("title"),
        username = t.requireText("username"),
        artworkUrl = t.optionalUrl("artworkUrl"),
        durationMs = t.requireLong("durationMs"),
        permalinkUrl = t.optionalUrl("permalinkUrl"),
        userId = t.requireLong("userId"),
        userAvatarUrl = t.optionalUrl("userAvatarUrl"),
        genre = t.optionalString("genre")?.take(MAX_IMPORT_TEXT_CHARS),
    )

    private fun toPlaylist(p: Map<String, String?>) = FavoritePlaylist(
        playlistId = p.requireLong("playlistId"),
        title = p.requireText("title"),
        artworkUrl = p.optionalUrl("artworkUrl"),
        trackCount = p.requireLong("trackCount").toInt(),
        username = p.requireText("username"),
        permalinkUrl = p.optionalUrl("permalinkUrl"),
        addedAt = p.optionalLong("addedAt") ?: currentTimeMillis(),
    )

    data class ImportProgress(val fraction: Float?, val result: ImportResult)

    class ImportFailedException(cause: Throwable, val partial: ImportResult) :
        IllegalStateException(cause.message, cause)

    data class ImportResult(
        val tracks: Int,
        val artists: Int,
        val playlists: Int,
        val skipped: Int = 0,
    ) {
        val total: Int get() = tracks + artists + playlists
    }
}

private val FAVORITE_ARRAYS = setOf("tracks", "artists", "playlists", "localPlaylists")
private const val EXPORT_PAGE_SIZE = 500
private const val IMPORT_BATCH_SIZE = 500
private const val MAX_IMPORT_TEXT_CHARS = 300
private const val MAX_IMPORT_URL_CHARS = 2048

private val ExportJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

private fun JsonObject.toFlatMap(): Map<String, String?> = mapValues { (_, v) ->
    when (v) {
        is JsonNull -> null
        is JsonPrimitive -> v.content
        else -> null
    }
}

private fun Map<String, String?>.requireString(key: String): String =
    this[key] ?: throw IllegalArgumentException("Missing $key")

private fun Map<String, String?>.optionalString(key: String): String? =
    this[key]?.takeIf { it.isNotEmpty() && it != "null" }

private fun Map<String, String?>.requireText(key: String): String =
    requireString(key).take(MAX_IMPORT_TEXT_CHARS)

private fun Map<String, String?>.optionalUrl(key: String): String? =
    optionalString(key)?.takeIf { it.length <= MAX_IMPORT_URL_CHARS }

private fun Map<String, String?>.optionalLong(key: String): Long? =
    this[key]?.let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() }

private fun Map<String, String?>.requireLong(key: String): Long =
    optionalLong(key) ?: throw IllegalArgumentException("Missing $key")
