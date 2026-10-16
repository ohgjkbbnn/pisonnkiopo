package com.savoo.scclient.debug

import com.savoo.scclient.compat.Context

object CrashReporter {
    const val EARLY_CRASH_WINDOW_MS = 15_000L

    fun install(context: Context) = Unit
    fun launchCrashStreak(context: Context): Int = 0
    fun resetLaunchCrashStreak(context: Context) = Unit
    fun consumePendingReport(): String? = null
}
