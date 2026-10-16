package com.savoo.scclient.data.local

import com.savoo.scclient.compat.*
import androidx.room.RoomRawQuery
import com.savoo.scclient.data.model.favoriteTextKey

enum class FavoriteTrackOrder { ADDED, TITLE, ARTIST, DURATION }

data class FavoriteTrackFilter(
    val search: String = "",
    val order: FavoriteTrackOrder = FavoriteTrackOrder.ADDED,
    val descending: Boolean = false,
)

const val SQLITE_MAX_IDS_PER_QUERY = 900

object FavoriteTrackQueries {

    fun page(filter: FavoriteTrackFilter): RoomRawQuery = build("SELECT * FROM favorites", filter)

    fun window(filter: FavoriteTrackFilter, offset: Int, limit: Int): RoomRawQuery =
        build("SELECT * FROM favorites", filter, "LIMIT $limit OFFSET $offset")

    fun ids(filter: FavoriteTrackFilter): RoomRawQuery = build("SELECT trackId FROM favorites", filter)

    fun count(filter: FavoriteTrackFilter): RoomRawQuery =
        build("SELECT COUNT(*) FROM favorites", filter, ordered = false)

    private fun build(
        select: String,
        filter: FavoriteTrackFilter,
        suffix: String = "",
        ordered: Boolean = true,
    ): RoomRawQuery {
        val args = ArrayList<String>()
        val sql = StringBuilder(select)
        val needle = favoriteTextKey(filter.search.trim())
        if (needle.isNotEmpty()) {
            val pattern = "%" + escapeLike(needle) + "%"
            sql.append(" WHERE (titleKey LIKE ? ESCAPE '\\' OR artistKey LIKE ? ESCAPE '\\')")
            args += pattern
            args += pattern
        }
        if (ordered) sql.append(" ORDER BY ").append(orderBy(filter))
        if (suffix.isNotEmpty()) sql.append(' ').append(suffix)
        return RoomRawQuery(sql.toString()) { stmt -> args.forEachIndexed { i, arg -> stmt.bindText(i + 1, arg) } }
    }

    private fun orderBy(filter: FavoriteTrackFilter): String {
        val primary = if (filter.descending) "DESC" else "ASC"
        val tieBreak = if (filter.descending) "addedAt ASC, trackId DESC" else "addedAt DESC, trackId ASC"
        return when (filter.order) {
            FavoriteTrackOrder.ADDED -> if (filter.descending) "addedAt ASC, trackId DESC" else "addedAt DESC, trackId ASC"
            FavoriteTrackOrder.TITLE -> "titleKey $primary, $tieBreak"
            FavoriteTrackOrder.ARTIST -> "artistKey $primary, $tieBreak"
            FavoriteTrackOrder.DURATION -> "durationMs $primary, $tieBreak"
        }
    }

    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
