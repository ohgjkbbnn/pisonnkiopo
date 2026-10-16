package com.savoo.scclient.security

import com.savoo.scclient.compat.Context

object AppLockGuard {
    fun isEnabled(context: Context): Boolean = false
    fun setEnabled(context: Context, enabled: Boolean) = Unit
    fun verifyOrWipe(context: Context) = Unit
}
