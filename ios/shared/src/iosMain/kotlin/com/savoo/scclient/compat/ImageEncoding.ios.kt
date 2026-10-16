package com.savoo.scclient.compat

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy

actual fun squareJpeg(source: ByteArray, size: Int): ByteArray? {
    val data = source.usePinned { NSData.create(bytes = it.addressOf(0), length = source.size.toULong()) }
    val image = UIImage.imageWithData(data) ?: return null
    val (w, h) = image.size.useContents { width to height }
    val side = minOf(w, h)
    if (side <= 0.0) return null
    val scale = size / side
    val format = UIGraphicsImageRendererFormat.defaultFormat().apply { this.scale = 1.0 }
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(size.toDouble(), size.toDouble()), format = format)
    val square = renderer.imageWithActions { _ ->
        image.drawInRect(CGRectMake(-(w - side) / 2 * scale, -(h - side) / 2 * scale, w * scale, h * scale))
    }
    val jpeg = UIImageJPEGRepresentation(square, 0.85) ?: return null
    val out = ByteArray(jpeg.length.toInt())
    if (out.isNotEmpty()) out.usePinned { memcpy(it.addressOf(0), jpeg.bytes, jpeg.length) }
    return out
}
