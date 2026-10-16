package com.savoo.scclient.security

import com.savoo.scclient.compat.Context

class Cipher internal constructor()

expect object PlatformBiometrics {
    fun isAvailable(): Boolean
    fun enrollmentState(): String?
    fun authenticate(reason: String, fallbackTitle: String, onResult: (Boolean) -> Unit)
}

fun canUseBiometric(context: Context): Boolean = PlatformBiometrics.isAvailable()

sealed interface BiometricKeyState {
    class Ready(val cipher: Cipher) : BiometricKeyState
    data object Invalidated : BiometricKeyState
    data object Unavailable : BiometricKeyState
}

object BiometricKey {
    private const val PREFS = "app_lock_biometric"
    private const val KEY_STATE = "enrollment_state"
    private val prefs get() = com.savoo.scclient.compat.SharedPreferencesImpl.get(PREFS)

    fun create(): Boolean {
        val state = PlatformBiometrics.enrollmentState() ?: return false
        prefs.edit().putString(KEY_STATE, state).apply()
        return true
    }

    fun delete() {
        prefs.edit().remove(KEY_STATE).apply()
    }

    fun state(): BiometricKeyState {
        val saved = prefs.getString(KEY_STATE, null) ?: return BiometricKeyState.Unavailable
        val current = PlatformBiometrics.enrollmentState() ?: return BiometricKeyState.Unavailable
        return if (saved == current) BiometricKeyState.Ready(Cipher()) else BiometricKeyState.Invalidated
    }
}

fun Context.promptBiometric(
    title: String,
    negativeText: String,
    cipher: Cipher,
    onResult: (Boolean) -> Unit,
) {
    PlatformBiometrics.authenticate(title, negativeText, onResult)
}
