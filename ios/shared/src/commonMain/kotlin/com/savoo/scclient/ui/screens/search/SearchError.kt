package com.savoo.scclient.ui.screens.search

import com.savoo.scclient.compat.*
import com.savoo.scclient.compat.http.HttpException
import com.savoo.scclient.compat.IOException

enum class SearchErrorKind { NETWORK, AUTH, NOT_FOUND, SERVER, TRACK_GONE, LINK, UNKNOWN }

data class SearchError(val kind: SearchErrorKind, val detail: String? = null)

fun Throwable.toSearchError(): SearchError = when (this) {
    is UnknownHostException -> SearchError(SearchErrorKind.NETWORK, message ?: "unknown host")
    is SocketTimeoutException -> SearchError(SearchErrorKind.NETWORK, "timeout")
    is HttpException -> when (code()) {
        401, 403 -> SearchError(SearchErrorKind.AUTH, "HTTP ${code()}")
        404 -> SearchError(SearchErrorKind.NOT_FOUND, "HTTP 404")
        in 500..599 -> SearchError(SearchErrorKind.SERVER, "HTTP ${code()}")
        else -> SearchError(SearchErrorKind.UNKNOWN, "HTTP ${code()}")
    }
    is IOException -> SearchError(SearchErrorKind.NETWORK, message ?: this::class.simpleName)
    else -> SearchError(SearchErrorKind.UNKNOWN, message ?: this::class.simpleName)
}
