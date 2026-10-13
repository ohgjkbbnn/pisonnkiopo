package com.savoo.scclient.ui.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.savoo.scclient.data.repository.HapticsIntensity

val LocalHapticsEnabled = staticCompositionLocalOf { true }
val LocalHapticsIntensity = staticCompositionLocalOf { HapticsIntensity.MEDIUM }

private fun Context.vibratorService(): Vibrator =
    if (Build.VERSION.SDK_INT >= 31) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

private fun HapticsIntensity.multiplier(): Float = when (this) {
    HapticsIntensity.LOW -> 0.6f
    HapticsIntensity.MEDIUM -> 1f
    HapticsIntensity.HIGH -> 1.45f
}

private enum class Tap { SOFT, LIGHT, MEDIUM, HEAVY }

private class Pulse(val tap: Tap, val scale: Float, val delayMs: Int = 0)

class Haptics internal constructor(
    private val vibrator: Vibrator,
    private val enabled: Boolean,
    private val intensity: HapticsIntensity,
) {
    private val primitives: Boolean = Build.VERSION.SDK_INT >= 30 && vibrator.areAllPrimitivesSupported(
        VibrationEffect.Composition.PRIMITIVE_TICK,
        VibrationEffect.Composition.PRIMITIVE_CLICK,
    )
    private val lowTick: Boolean = primitives && Build.VERSION.SDK_INT >= 31 &&
        vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)

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
        runCatching {
            val effect = when {
                primitives -> composed(pulses, gain)
                Build.VERSION.SDK_INT >= 29 && pulses.size == 1 -> predefined(pulses[0].tap)
                else -> waveform(pulses, gain)
            }
            vibrator.vibrate(effect)
        }
    }

    @androidx.annotation.RequiresApi(30)
    private fun composed(pulses: Array<out Pulse>, gain: Float): VibrationEffect {
        val composition = VibrationEffect.startComposition()
        pulses.forEach { pulse ->
            val primitive = when (pulse.tap) {
                Tap.SOFT -> if (lowTick) VibrationEffect.Composition.PRIMITIVE_LOW_TICK else VibrationEffect.Composition.PRIMITIVE_TICK
                Tap.LIGHT -> VibrationEffect.Composition.PRIMITIVE_TICK
                Tap.MEDIUM, Tap.HEAVY -> VibrationEffect.Composition.PRIMITIVE_CLICK
            }
            composition.addPrimitive(primitive, (pulse.scale * gain).coerceIn(0.05f, 1f), pulse.delayMs)
        }
        return composition.compose()
    }

    @androidx.annotation.RequiresApi(29)
    private fun predefined(tap: Tap): VibrationEffect = VibrationEffect.createPredefined(
        when (tap) {
            Tap.SOFT, Tap.LIGHT -> VibrationEffect.EFFECT_TICK
            Tap.MEDIUM -> VibrationEffect.EFFECT_CLICK
            Tap.HEAVY -> VibrationEffect.EFFECT_HEAVY_CLICK
        },
    )

    private fun waveform(pulses: Array<out Pulse>, gain: Float): VibrationEffect {
        val timings = ArrayList<Long>()
        val amplitudes = ArrayList<Int>()
        pulses.forEach { pulse ->
            timings += pulse.delayMs.toLong()
            amplitudes += 0
            val (length, base) = when (pulse.tap) {
                Tap.SOFT -> 5L to 90
                Tap.LIGHT -> 7L to 130
                Tap.MEDIUM -> 9L to 180
                Tap.HEAVY -> 12L to 230
            }
            timings += length
            amplitudes += (base * pulse.scale * gain).toInt().coerceIn(1, 255)
        }
        return VibrationEffect.createWaveform(timings.toLongArray(), amplitudes.toIntArray(), -1)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    val enabled = LocalHapticsEnabled.current
    val intensity = LocalHapticsIntensity.current
    return remember(enabled, intensity) { Haptics(context.vibratorService(), enabled, intensity) }
}

@Composable
fun rememberHapticTick(): () -> Unit {
    val haptics = rememberHaptics()
    return { haptics.tick() }
}
