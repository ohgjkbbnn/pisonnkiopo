package com.savoo.scclient.ui.navigation

import com.savoo.scclient.compat.Context
import com.savoo.scclient.compat.Uri

object SoundCloudLinks {

    val hosts = setOf("soundcloud.com", "m.soundcloud.com", "www.soundcloud.com", "on.soundcloud.com")

    fun startPositionMs(url: String): Long {
        val fragment = Uri.parse(url).fragment ?: return 0L
        val raw = fragment.split('&')
            .firstOrNull { it.startsWith("t=") }
            ?.removePrefix("t=")
            ?.trim()
            ?: return 0L
        return parseTime(raw)?.times(1000L) ?: 0L
    }

    private fun parseTime(raw: String): Long? {
        if (raw.contains(':')) {
            val parts = raw.split(':').map { it.toLongOrNull() ?: return null }
            if (parts.size > 3) return null
            return parts.fold(0L) { acc, part -> acc * 60 + part }
        }
        raw.toLongOrNull()?.let { return it }
        val match = Regex("""^(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?$""").matchEntire(raw) ?: return null
        if (match.value.isEmpty()) return null
        val (h, m, s) = match.destructured
        return (h.toLongOrNull() ?: 0) * 3600 + (m.toLongOrNull() ?: 0) * 60 + (s.toLongOrNull() ?: 0)
    }

    fun isHandlingEnabled(context: Context): Boolean = true

    fun openHandlingSettings(context: Context) = Unit
}
