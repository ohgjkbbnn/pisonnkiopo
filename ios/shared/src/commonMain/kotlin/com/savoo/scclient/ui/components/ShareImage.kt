package com.savoo.scclient.ui.components

import com.savoo.scclient.compat.*
import kotlinx.coroutines.IO
import com.savoo.scclient.compat.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalGraphicsContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.savoo.scclient.compat.File

@Composable
fun rememberCaptureLayer(): GraphicsLayer {
    val graphicsContext = LocalGraphicsContext.current
    val graphicsLayer = remember { graphicsContext.createGraphicsLayer() }
    DisposableEffect(graphicsLayer) {
        onDispose { graphicsContext.releaseGraphicsLayer(graphicsLayer) }
    }
    return graphicsLayer
}

fun Modifier.captureInto(layer: GraphicsLayer): Modifier = drawWithContent {
    layer.record { this@drawWithContent.drawContent() }
    drawLayer(layer)
}

suspend fun shareImage(context: Context, bitmap: ImageBitmap, namePrefix: String) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
        dir.listFiles()?.filter { currentTimeMillis() - it.lastModified() > 60 * 60 * 1000L }?.forEach { it.delete() }
        File(dir, "${namePrefix}_${currentTimeMillis()}.png").also { it.writeBytes(encodePng(bitmap)) }
    }
    Platform.shareFile(file.absolutePath, "image/png")
}

expect fun encodePng(bitmap: ImageBitmap): ByteArray
