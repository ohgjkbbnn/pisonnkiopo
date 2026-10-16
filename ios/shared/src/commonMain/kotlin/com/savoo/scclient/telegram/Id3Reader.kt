package com.savoo.scclient.telegram

import com.savoo.scclient.compat.File

data class Id3Tags(val title: String?, val performer: String?, val durationMs: Long?)

object Id3Reader {
    fun read(file: File): Id3Tags = Id3Tags(null, null, null)
}
