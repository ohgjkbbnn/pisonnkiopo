package com.savoo.scclient.i18n

import com.savoo.scclient.compat.Context
import com.savoo.scclient.compat.InputStream

data class CustomStringsStats(val applied: Int, val unknown: Int, val total: Int)

enum class CustomStringsDisableReason { CRASHES, TOO_LARGE }

class CustomStringsCheck internal constructor(
    val total: Int,
    val unknown: Int,
    val longStrings: Int,
    val brokenPlaceholders: Int,
    val sizeBytes: Long,
) {
    val hasWarnings: Boolean get() = longStrings > 0 || brokenPlaceholders > 0
}

object CustomStrings {
    var stats: CustomStringsStats? = null
        private set

    fun hasFile(context: Context): Boolean = false
    fun isSelected(context: Context): Boolean = false
    fun inspect(context: Context, input: InputStream): Result<CustomStringsCheck> =
        Result.failure(UnsupportedOperationException("Custom translations are not available on iOS"))
    fun install(context: Context, check: CustomStringsCheck, skipProblems: Boolean): Result<CustomStringsStats> =
        Result.failure(UnsupportedOperationException("Custom translations are not available on iOS"))
    fun enterSafeModeIfCrashLooping(context: Context): Boolean = false
    fun consumeDisableNotice(context: Context): CustomStringsDisableReason? = null
    fun clear(context: Context) = Unit
}
