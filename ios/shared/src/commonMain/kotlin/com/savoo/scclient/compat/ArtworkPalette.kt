package com.savoo.scclient.compat

import androidx.compose.ui.graphics.Color
import coil3.Image
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size

expect fun imageArgbPixels(image: Image): IntArray?

object Palette {
    suspend fun loadSwatchColor(url: String, size: Int = 128): Color? {
        val request = ImageRequest.Builder(PlatformContext.INSTANCE)
            .data(url)
            .size(Size(size, size))
            .build()
        val result = SingletonImageLoader.get(PlatformContext.INSTANCE).execute(request) as? SuccessResult ?: return null
        val pixels = imageArgbPixels(result.image) ?: return null
        return swatch(pixels)?.let { Color(it) }
    }

    fun swatch(pixels: IntArray): Int? {
        if (pixels.isEmpty()) return null
        val counts = IntArray(HUE_BINS * 4 * 4)
        val sums = Array(counts.size) { LongArray(3) }
        val hsv = FloatArray(3)
        for (argb in pixels) {
            if ((argb ushr 24) < 128) continue
            GraphicsColor.colorToHSV(argb, hsv)
            val h = ((hsv[0] / 360f) * HUE_BINS).toInt().coerceIn(0, HUE_BINS - 1)
            val s = (hsv[1] * 4).toInt().coerceIn(0, 3)
            val v = (hsv[2] * 4).toInt().coerceIn(0, 3)
            val bin = (h * 4 + s) * 4 + v
            counts[bin]++
            sums[bin][0] += ((argb shr 16) and 0xFF).toLong()
            sums[bin][1] += ((argb shr 8) and 0xFF).toLong()
            sums[bin][2] += (argb and 0xFF).toLong()
        }
        fun colorOf(bin: Int): Int {
            val n = counts[bin].toLong().coerceAtLeast(1)
            return (0xFF shl 24) or ((sums[bin][0] / n).toInt() shl 16) or ((sums[bin][1] / n).toInt() shl 8) or (sums[bin][2] / n).toInt()
        }
        var vibrant = -1
        var vibrantScore = 0.0
        var dominant = -1
        for (bin in counts.indices) {
            if (counts[bin] == 0) continue
            if (dominant < 0 || counts[bin] > counts[dominant]) dominant = bin
            val s = (bin / 4) % 4
            val v = bin % 4
            if (s >= 2 && v in 1..3) {
                val score = counts[bin] * (1.0 + s * 0.5)
                if (score > vibrantScore) {
                    vibrantScore = score
                    vibrant = bin
                }
            }
        }
        val total = pixels.size.toDouble()
        return when {
            vibrant >= 0 && counts[vibrant] / total > 0.01 -> colorOf(vibrant)
            dominant >= 0 -> colorOf(dominant)
            else -> null
        }
    }

    private const val HUE_BINS = 24
}
