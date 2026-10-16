package com.savoo.scclient.data.model

import com.savoo.scclient.compat.*
import androidx.room.Entity

@Entity(tableName = "lyrics_cache", primaryKeys = ["trackId", "provider"])
data class LyricsCacheEntity(
    val trackId: Long,
    val provider: String,
    val type: String,
    val content: String?,
    val source: String?,
    val sourceDurationMs: Long? = null,
    val fetchedAt: Long = currentTimeMillis(),
)
