package com.savoo.scclient.compat

import coil3.Image
import coil3.toBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

actual fun imageArgbPixels(image: Image): IntArray? {
    val bitmap = image.toBitmap()
    val info = ImageInfo(bitmap.width, bitmap.height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
    val bytes = bitmap.readPixels(info, bitmap.width * 4, 0, 0) ?: return null
    return IntArray(bitmap.width * bitmap.height) { i ->
        val o = i * 4
        ((bytes[o + 3].toInt() and 0xFF) shl 24) or
            ((bytes[o + 2].toInt() and 0xFF) shl 16) or
            ((bytes[o + 1].toInt() and 0xFF) shl 8) or
            (bytes[o].toInt() and 0xFF)
    }
}
