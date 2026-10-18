package com.savoo.scclient.player

import com.savoo.scclient.compat.System
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.value
import platform.CoreAudioTypes.AudioBufferList
import platform.CoreAudioTypes.AudioStreamBasicDescription
import platform.CoreAudioTypes.kAudioFormatFlagIsFloat
import platform.CoreAudioTypes.kAudioFormatFlagIsNonInterleaved
import platform.CoreFoundation.kCFAllocatorDefault
import platform.MediaToolbox.MTAudioProcessingTapCallbacks
import platform.MediaToolbox.MTAudioProcessingTapCreate
import platform.MediaToolbox.MTAudioProcessingTapGetSourceAudio
import platform.MediaToolbox.MTAudioProcessingTapGetStorage
import platform.MediaToolbox.MTAudioProcessingTapRef
import platform.MediaToolbox.MTAudioProcessingTapRefVar
import platform.MediaToolbox.kMTAudioProcessingTapCallbacksVersion_0
import platform.MediaToolbox.kMTAudioProcessingTapCreationFlag_PreEffects
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal fun createSoundEffectsTap(effects: SoundEffects): MTAudioProcessingTapRef? = memScoped {
    val ref = StableRef.create(SoundEffectsTapProcessor(effects))
    val callbacks = alloc<MTAudioProcessingTapCallbacks>().apply {
        version = kMTAudioProcessingTapCallbacksVersion_0
        clientInfo = ref.asCPointer()
        init = tapInit
        finalize = tapFinalize
        prepare = tapPrepare
        unprepare = null
        process = tapProcess
    }
    val tapOut = alloc<MTAudioProcessingTapRefVar>()
    val status = MTAudioProcessingTapCreate(kCFAllocatorDefault, callbacks.ptr, kMTAudioProcessingTapCreationFlag_PreEffects, tapOut.ptr)
    if (status != 0) {
        ref.dispose()
        null
    } else {
        tapOut.value
    }
}

private fun processorOf(tap: MTAudioProcessingTapRef?): SoundEffectsTapProcessor? =
    MTAudioProcessingTapGetStorage(tap)?.asStableRef<SoundEffectsTapProcessor>()?.get()

private val tapInit = staticCFunction { _: MTAudioProcessingTapRef?, clientInfo: COpaquePointer?, storageOut: CPointer<COpaquePointerVar>? ->
    storageOut?.pointed?.value = clientInfo
}

private val tapFinalize = staticCFunction { tap: MTAudioProcessingTapRef? ->
    MTAudioProcessingTapGetStorage(tap)?.asStableRef<SoundEffectsTapProcessor>()?.dispose()
    Unit
}

private val tapPrepare = staticCFunction { tap: MTAudioProcessingTapRef?, _: Long, format: CPointer<AudioStreamBasicDescription>? ->
    val description = format?.pointed ?: return@staticCFunction
    processorOf(tap)?.prepare(
        sampleRate = description.mSampleRate.roundToInt(),
        channels = description.mChannelsPerFrame.toInt(),
        isFloat = description.mFormatFlags and kAudioFormatFlagIsFloat != 0u && description.mBitsPerChannel == 32u,
        interleaved = description.mFormatFlags and kAudioFormatFlagIsNonInterleaved == 0u,
    )
}

private val tapProcess = staticCFunction { tap: MTAudioProcessingTapRef?, frames: Long, _: UInt, buffers: CPointer<AudioBufferList>?, framesOut: CPointer<LongVar>?, flagsOut: CPointer<UIntVar>? ->
    val status = MTAudioProcessingTapGetSourceAudio(tap, frames, buffers, flagsOut, null, framesOut)
    if (status != 0 || buffers == null) return@staticCFunction
    processorOf(tap)?.process(buffers, framesOut?.pointed?.value ?: 0L)
}

private class SoundEffectsTapProcessor(private val effects: SoundEffects) {

    private var sampleRate = 0
    private var channels = 0
    private var isFloat = false
    private var interleaved = false
    private var reverbs: Array<Freeverb> = emptyArray()
    private var shelves: Array<LowShelf> = emptyArray()
    private var appliedBass = -1f
    private var beatLowpass = 0f
    private var beatLowpassCoef = 0f
    private var beatSum = 0f
    private var beatCount = 0
    private var beatChunkFrames = 441
    private var beatPeak = 0f
    private var beatClockNanos = 0L

    fun prepare(sampleRate: Int, channels: Int, isFloat: Boolean, interleaved: Boolean) {
        this.sampleRate = sampleRate
        this.channels = channels
        this.isFloat = isFloat
        this.interleaved = interleaved
        val processed = channels.coerceAtMost(2)
        reverbs = Array(processed) { channel -> Freeverb(sampleRate, stereoSpread = if (channel == 1) 23 else 0) }
        shelves = Array(processed) { LowShelf() }
        appliedBass = -1f
        beatLowpass = 0f
        beatLowpassCoef = if (sampleRate > 0) (1.0 - kotlin.math.exp(-2.0 * PI * BEAT_LOWPASS_HZ / sampleRate)).toFloat() else 0f
        beatChunkFrames = (sampleRate / 100).coerceAtLeast(1)
        beatSum = 0f
        beatCount = 0
        beatClockNanos = 0L
    }

    fun process(buffers: CPointer<AudioBufferList>, frameCount: Long) {
        if (!isFloat || channels == 0 || sampleRate == 0 || frameCount <= 0) return
        val list = buffers.pointed
        val stride: Int
        val left: CPointer<FloatVar>
        val right: CPointer<FloatVar>
        val rightOffset: Int
        if (interleaved) {
            left = list.mBuffers[0].mData?.reinterpret() ?: return
            right = left
            stride = channels
            rightOffset = if (channels >= 2) 1 else 0
        } else {
            if (list.mNumberBuffers.toInt() < channels.coerceAtMost(2)) return
            left = list.mBuffers[0].mData?.reinterpret() ?: return
            right = if (channels >= 2) list.mBuffers[1].mData?.reinterpret() ?: return else left
            stride = 1
            rightOffset = 0
        }
        val frames = frameCount.toInt()
        val stereo = channels == 2

        trackBeat(left, right, stride, rightOffset, frames)

        val reverb = effects.reverb
        val bass = effects.bass
        if ((reverb <= 0f && bass <= 0f) || channels > 2) return

        if (bass != appliedBass) {
            shelves.forEach { it.configure(sampleRate, BASS_FREQUENCY_HZ, bass * MAX_BASS_DB) }
            appliedBass = bass
        }
        val headroom = if (bass > 0f) 10f.pow(-(bass * MAX_BASS_DB) / 40f) else 1f
        val roomSize = 0.72f + 0.26f * reverb
        val wet = reverb * 0.9f
        val dry = 1f - reverb * 0.35f
        reverbs.forEach { it.setRoom(roomSize, damping = 0.45f) }

        for (frame in 0 until frames) {
            val li = frame * stride
            val ri = li + rightOffset
            val inLeft = left[li]
            val inRight = if (stereo) right[ri] else inLeft

            var l = if (bass > 0f) shelves[0].process(inLeft) * headroom else inLeft
            var r = if (stereo) {
                if (bass > 0f) shelves[1].process(inRight) * headroom else inRight
            } else {
                l
            }

            if (reverb > 0f) {
                val mono = (l + r) * FREEVERB_GAIN
                val wetL = reverbs[0].process(mono)
                if (stereo) {
                    val wetR = reverbs[1].process(mono)
                    l = l * dry + (wetL * 0.8f + wetR * 0.2f) * wet
                    r = r * dry + (wetR * 0.8f + wetL * 0.2f) * wet
                } else {
                    l = l * dry + wetL * wet
                }
            }

            left[li] = l.coerceIn(-1f, 1f)
            if (stereo) right[ri] = r.coerceIn(-1f, 1f)
        }
    }

    private fun trackBeat(left: CPointer<FloatVar>, right: CPointer<FloatVar>, stride: Int, rightOffset: Int, frames: Int) {
        val now = System.nanoTime()
        val rate = effects.outputRate.coerceAtLeast(0.1f)
        val chunkNanos = (beatChunkFrames * 1_000_000_000L / (sampleRate * rate)).toLong()
        if (beatClockNanos < now - chunkNanos * 4) beatClockNanos = now
        val stereo = channels >= 2
        for (frame in 0 until frames) {
            val li = frame * stride
            val mono = if (stereo) (left[li] + right[li + rightOffset]) / 2f else left[li]
            beatLowpass += (mono - beatLowpass) * beatLowpassCoef
            beatSum += beatLowpass * beatLowpass
            if (++beatCount >= beatChunkFrames) {
                val rms = sqrt(beatSum / beatCount)
                beatPeak = maxOf(rms, beatPeak * BEAT_PEAK_DECAY)
                val level = if (beatPeak < BEAT_SILENCE) 0f else ((rms / beatPeak - 0.6f) / 0.4f).coerceIn(0f, 1f)
                beatClockNanos += chunkNanos
                effects.pushBeat(beatClockNanos, level)
                beatSum = 0f
                beatCount = 0
            }
        }
    }

    private companion object {
        const val BASS_FREQUENCY_HZ = 100.0
        const val MAX_BASS_DB = 9.0f
        const val FREEVERB_GAIN = 0.015f
        const val BEAT_LOWPASS_HZ = 150.0
        const val BEAT_PEAK_DECAY = 0.997f
        const val BEAT_SILENCE = 0.004f
    }
}

private class Freeverb(sampleRate: Int, stereoSpread: Int) {
    private val scale = sampleRate / 44100.0
    private val combs = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        .map { Comb(((it + stereoSpread) * scale).roundToInt().coerceAtLeast(1)) }
    private val allpasses = intArrayOf(556, 441, 341, 225)
        .map { Allpass(((it + stereoSpread) * scale).roundToInt().coerceAtLeast(1)) }

    fun setRoom(roomSize: Float, damping: Float) {
        combs.forEach {
            it.feedback = roomSize
            it.damp = damping
        }
    }

    fun process(input: Float): Float {
        var out = 0f
        for (comb in combs) out += comb.process(input)
        for (allpass in allpasses) out = allpass.process(out)
        return out * 3f
    }

    private class Comb(size: Int) {
        private val buffer = FloatArray(size)
        private var index = 0
        private var store = 0f
        var feedback = 0.8f
        var damp = 0.5f

        fun process(input: Float): Float {
            val output = buffer[index]
            store = output * (1f - damp) + store * damp
            buffer[index] = input + store * feedback
            if (++index >= buffer.size) index = 0
            return output
        }
    }

    private class Allpass(size: Int) {
        private val buffer = FloatArray(size)
        private var index = 0

        fun process(input: Float): Float {
            val buffered = buffer[index]
            buffer[index] = input + buffered * 0.5f
            if (++index >= buffer.size) index = 0
            return buffered - input
        }
    }
}

private class LowShelf {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun configure(sampleRate: Int, frequency: Double, gainDb: Float) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2 * PI * frequency / sampleRate
        val cosW = cos(w0)
        val alpha = sin(w0) / 2 * sqrt(2.0)
        val sqrtA2Alpha = 2 * sqrt(a) * alpha
        val a0 = (a + 1) + (a - 1) * cosW + sqrtA2Alpha
        b0 = a * ((a + 1) - (a - 1) * cosW + sqrtA2Alpha) / a0
        b1 = 2 * a * ((a - 1) - (a + 1) * cosW) / a0
        b2 = a * ((a + 1) - (a - 1) * cosW - sqrtA2Alpha) / a0
        a1 = -2 * ((a - 1) + (a + 1) * cosW) / a0
        a2 = ((a + 1) + (a - 1) * cosW - sqrtA2Alpha) / a0
    }

    fun process(input: Float): Float {
        val x = input.toDouble()
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = x
        y2 = y1
        y1 = y
        return y.toFloat()
    }
}
