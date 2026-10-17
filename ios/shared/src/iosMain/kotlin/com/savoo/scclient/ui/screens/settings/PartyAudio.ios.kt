package com.savoo.scclient.ui.screens.settings

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSData
import platform.Foundation.create

actual object PartyAudio {
    private var player: AVAudioPlayer? = null

    actual fun play(pcm: ShortArray, sampleRate: Int) {
        stop()
        val wav = wav(pcm, sampleRate)
        val data = wav.usePinned { NSData.create(bytes = it.addressOf(0), length = wav.size.toULong()) }
        player = AVAudioPlayer(data = data, error = null).apply {
            numberOfLoops = -1
            prepareToPlay()
            play()
        }
    }

    actual fun stop() {
        player?.stop()
        player = null
    }

    private fun wav(pcm: ShortArray, sampleRate: Int): ByteArray {
        val dataSize = pcm.size * 2
        val out = ByteArray(44 + dataSize)
        fun putInt(at: Int, v: Int) {
            for (i in 0 until 4) out[at + i] = (v shr (8 * i)).toByte()
        }
        fun putShort(at: Int, v: Int) {
            out[at] = v.toByte()
            out[at + 1] = (v shr 8).toByte()
        }
        "RIFF".encodeToByteArray().copyInto(out, 0)
        putInt(4, 36 + dataSize)
        "WAVEfmt ".encodeToByteArray().copyInto(out, 8)
        putInt(16, 16)
        putShort(20, 1)
        putShort(22, 1)
        putInt(24, sampleRate)
        putInt(28, sampleRate * 2)
        putShort(32, 2)
        putShort(34, 16)
        "data".encodeToByteArray().copyInto(out, 36)
        putInt(40, dataSize)
        pcm.forEachIndexed { i, s -> putShort(44 + i * 2, s.toInt()) }
        return out
    }
}
