package com.savoo.scclient.ui.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.savoo.scclient.data.repository.HapticsIntensity

val LocalHapticsEnabled = staticCompositionLocalOf { true }
val LocalHapticsIntensity = staticCompositionLocalOf { HapticsIntensity.MEDIUM }

private fun HapticsIntensity.multiplier(): Float = when (this) {
    HapticsIntensity.LOW -> 0.6f
    HapticsIntensity.MEDIUM -> 1f
    HapticsIntensity.HIGH -> 1.45f
}

private enum class Tap { SOFT, LIGHT, MEDIUM, HEAVY }

private class Pulse(val tap: Tap, val scale: Float, val delayMs: Int = 0)

class Haptics internal constructor(
    private val enabled: Boolean,
    private val intensity: HapticsIntensity,
) {
    fun tick() = play(Pulse(Tap.SOFT, 0.6f))
    fun click() = play(Pulse(Tap.LIGHT, 0.6f))
    fun longPress() = play(Pulse(Tap.MEDIUM, 0.55f))

    fun seekGrab() = play(Pulse(Tap.LIGHT, 0.7f))
    fun seekRelease() = play(Pulse(Tap.MEDIUM, 0.45f))
    fun scrub(level: Float) = play(Pulse(if (level > 0.6f) Tap.LIGHT else Tap.SOFT, 0.15f + 0.75f * level.coerceIn(0f, 1f)))

    fun skipForward() = play(Pulse(Tap.SOFT, 0.35f), Pulse(Tap.LIGHT, 0.55f, 35), Pulse(Tap.MEDIUM, 0.5f, 35))
    fun skipBack() = play(Pulse(Tap.MEDIUM, 0.5f), Pulse(Tap.LIGHT, 0.55f, 35), Pulse(Tap.SOFT, 0.35f, 35))

    fun like() = play(Pulse(Tap.MEDIUM, 0.55f), Pulse(Tap.LIGHT, 0.4f, 110))
    fun loopWrap() = play(Pulse(Tap.SOFT, 0.5f), Pulse(Tap.SOFT, 0.5f, 50))
    fun success() = play(Pulse(Tap.LIGHT, 0.5f), Pulse(Tap.MEDIUM, 0.8f, 90))
    fun warn() = play(Pulse(Tap.MEDIUM, 0.7f), Pulse(Tap.LIGHT, 0.5f, 110))
    fun error() = play(Pulse(Tap.MEDIUM, 0.8f), Pulse(Tap.MEDIUM, 0.8f, 70), Pulse(Tap.HEAVY, 0.9f, 70))
    fun remove() = play(Pulse(Tap.HEAVY, 0.8f))

    private fun play(vararg pulses: Pulse) {
        if (!enabled) return
        val gain = intensity.multiplier()
        var delay = 0L
        pulses.forEach { pulse ->
            delay += pulse.delayMs
            PlatformHaptics.impact(pulse.tap.ordinal, (pulse.scale * gain).coerceIn(0.05f, 1f), delay)
        }
    }
}

expect object PlatformHaptics {
    fun impact(style: Int, intensity: Float, delayMs: Long)
}

@Composable
fun rememberHaptics(): Haptics {
    val enabled = LocalHapticsEnabled.current
    val intensity = LocalHapticsIntensity.current
    return remember(enabled, intensity) { Haptics(enabled, intensity) }
}

@Composable
fun rememberHapticTick(): () -> Unit {
    val haptics = rememberHaptics()
    return { haptics.tick() }
}
