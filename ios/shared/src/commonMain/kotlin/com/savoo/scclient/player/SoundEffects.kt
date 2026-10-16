package com.savoo.scclient.player

import kotlin.concurrent.Volatile


enum class SoundPreset(val rate: Float, val reverb: Float, val bass: Float) {
    OFF(1f, 0f, 0f),
    SLOWED_REVERB(0.85f, 0.55f, 0.25f),
    SLOWED(0.8f, 0f, 0.15f),
    SPED_UP(1.2f, 0f, 0f),
    NIGHTCORE(1.3f, 0f, 0.1f),
    CUSTOM(1f, 0f, 0f),
}

data class SoundMode(
    val preset: SoundPreset = SoundPreset.OFF,
    val customRate: Float = 1f,
    val customReverb: Float = 0f,
    val customBass: Float = 0f,
) {
    val rate: Float get() = if (preset == SoundPreset.CUSTOM) customRate else preset.rate
    val reverb: Float get() = if (preset == SoundPreset.CUSTOM) customReverb else preset.reverb
    val bass: Float get() = if (preset == SoundPreset.CUSTOM) customBass else preset.bass
    val isActive: Boolean get() = preset != SoundPreset.OFF

    companion object {
        const val MIN_RATE = 0.7f
        const val MAX_RATE = 1.4f
    }
}

class SoundEffects() {
    @Volatile var reverb: Float = 0f
    @Volatile var bass: Float = 0f
    @Volatile var outputRate: Float = 1f

    private val beatTimes = LongArray(BEAT_HISTORY)
    private val beatLevels = FloatArray(BEAT_HISTORY)
    @Volatile private var beatWrite = 0

    internal fun pushBeat(timeNanos: Long, level: Float) {
        val index = beatWrite % BEAT_HISTORY
        beatTimes[index] = timeNanos
        beatLevels[index] = level
        beatWrite = index + 1
    }

    fun beatLevelAt(timeNanos: Long): Float {
        val newest = beatWrite
        for (step in 1..BEAT_HISTORY) {
            val index = (newest - step + BEAT_HISTORY) % BEAT_HISTORY
            val time = beatTimes[index]
            if (time == 0L) return 0f
            if (time <= timeNanos) {
                return if (timeNanos - time > STALE_NANOS) 0f else beatLevels[index]
            }
        }
        return 0f
    }

    private companion object {
        const val BEAT_HISTORY = 512
        const val STALE_NANOS = 150_000_000L
    }
}
