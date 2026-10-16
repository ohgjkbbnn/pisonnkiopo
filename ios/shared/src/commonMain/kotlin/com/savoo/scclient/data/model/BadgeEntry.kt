package com.savoo.scclient.data.model

import com.savoo.scclient.compat.*
import kotlinx.serialization.Serializable

@Serializable
data class BadgeEntry(
    val badges: List<String> = emptyList(),
)
