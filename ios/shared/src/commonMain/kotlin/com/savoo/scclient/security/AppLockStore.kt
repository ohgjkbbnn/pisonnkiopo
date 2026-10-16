package com.savoo.scclient.security

import com.savoo.scclient.compat.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLockMode(val usesBiometric: Boolean) {
    OFF(false),
    PIN(false),
    BIOMETRIC_OR_PIN(true),
}

sealed interface PinCheckResult {
    data object Correct : PinCheckResult
    data object Duress : PinCheckResult
    data class Wrong(val lockedUntil: Long) : PinCheckResult
    data class LockedOut(val lockedUntil: Long) : PinCheckResult
}

const val MinPinLength = 4
const val MaxPinLength = 8

class AppLockStore(private val context: Context) {
    private val _mode = MutableStateFlow(AppLockMode.OFF)
    val mode: StateFlow<AppLockMode> = _mode.asStateFlow()

    private val _duressEnabled = MutableStateFlow(false)
    val duressEnabled: StateFlow<Boolean> = _duressEnabled.asStateFlow()

    var sessionUnlocked = true
        private set

    fun load() = Unit
    fun needsUnlock(): Boolean = false
    fun markUnlocked() = Unit
    fun hasPin(): Boolean = false
    fun lockedUntil(): Long = 0L
    suspend fun setPin(pin: String) = Unit
    fun setMode(mode: AppLockMode) = Unit
    suspend fun matchesPin(pin: String): Boolean = false
    suspend fun matchesDuressPin(pin: String): Boolean = false
    suspend fun setDuressPin(pin: String) = Unit
    fun clearDuress() = Unit
    suspend fun check(pin: String): PinCheckResult = PinCheckResult.Correct

    internal companion object {
        const val PREFS_FILE = "app_lock_secure_prefs"
    }
}
