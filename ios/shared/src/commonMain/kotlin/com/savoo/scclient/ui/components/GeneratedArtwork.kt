package com.savoo.scclient.ui.components

import com.savoo.scclient.compat.*
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.graphics.shapes.RoundedPolygon
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

fun generatedArtworkSeedColor(seed: Any?): Color {
    val hue = Random(seed?.hashCode() ?: 0).nextFloat()
    return Color.hsl(hue * 360f, 0.72f, 0.58f)
}

val LocalAnimatedArtwork = staticCompositionLocalOf { false }

object GeneratedArtworkDebugState {
    private val _forceEverywhere = MutableStateFlow(false)
    val forceEverywhere = _forceEverywhere.asStateFlow()

    fun setForceEverywhere(value: Boolean) {
        _forceEverywhere.value = value
    }
}

@Composable
fun GeneratedArtwork(seed: Any?, modifier: Modifier = Modifier, animated: Boolean = false) {
    GradientArtwork(seed, modifier)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val artworkPolygons: List<RoundedPolygon> by lazy {
    listOf(
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Cookie12Sided,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.Clover8Leaf,
        MaterialShapes.Sunny,
        MaterialShapes.VerySunny,
        MaterialShapes.SoftBurst,
        MaterialShapes.Flower,
        MaterialShapes.Puffy,
        MaterialShapes.PuffyDiamond,
        MaterialShapes.Gem,
        MaterialShapes.Pill,
        MaterialShapes.Circle,
        MaterialShapes.Arch,
    ).map { it.normalized() }
}

private class Blob(
    val polygon: RoundedPolygon,
    val center: Offset,
    val size: Float,
    val rotation: Float,
    val brushFrom: Color,
    val brushTo: Color,
    val alpha: Float,
    val outlined: Boolean,
)

private class ArtworkSpec(
    val background: List<Color>,
    val backgroundAngle: Float,
    val iridescent: List<Color>,
    val sweepCenter: Offset,
    val sheenCenter: Offset,
    val bandAngle: Float,
    val bandWidth: Float,
    val blobs: List<Blob>,
)

private fun hsl(hue: Float, saturation: Float, lightness: Float): Color =
    Color.hsl(((hue % 360f) + 360f) % 360f, saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))

private fun artworkSpec(seed: Any?): ArtworkSpec {
    val random = Random(seed?.hashCode() ?: 0)
    val baseHue = random.nextFloat() * 360f
    val spread = 35f + random.nextFloat() * 45f
    val saturation = 0.62f + random.nextFloat() * 0.25f
    fun tone(offset: Float, light: Float) = hsl(baseHue + offset, saturation, light)

    val palette = listOf(
        tone(0f, 0.62f),
        tone(spread, 0.7f),
        tone(spread * 2.4f, 0.66f),
        tone(180f + spread, 0.74f),
        tone(-spread, 0.58f),
    )
    val iridescent = listOf(
        hsl(baseHue + 90f, 0.9f, 0.78f),
        hsl(baseHue + 170f, 0.85f, 0.8f),
        hsl(baseHue + 260f, 0.9f, 0.82f),
        hsl(baseHue + 330f, 0.85f, 0.8f),
        hsl(baseHue + 90f, 0.9f, 0.78f),
    )
    val polygons = artworkPolygons.shuffled(random)
    val big = Blob(
        polygon = polygons[0],
        center = Offset(0.2f + random.nextFloat() * 0.6f, 0.2f + random.nextFloat() * 0.6f),
        size = 0.62f + random.nextFloat() * 0.3f,
        rotation = random.nextFloat() * 360f,
        brushFrom = palette[2],
        brushTo = palette[3],
        alpha = 0.92f,
        outlined = false,
    )
    val small = Blob(
        polygon = polygons[1],
        center = Offset(
            if (big.center.x > 0.5f) 0.12f + random.nextFloat() * 0.25f else 0.63f + random.nextFloat() * 0.25f,
            if (big.center.y > 0.5f) 0.12f + random.nextFloat() * 0.25f else 0.63f + random.nextFloat() * 0.25f,
        ),
        size = 0.24f + random.nextFloat() * 0.16f,
        rotation = random.nextFloat() * 360f,
        brushFrom = Color.White,
        brushTo = palette[1],
        alpha = 0.85f,
        outlined = false,
    )
    val ring = Blob(
        polygon = polygons[2],
        center = Offset(0.25f + random.nextFloat() * 0.5f, 0.25f + random.nextFloat() * 0.5f),
        size = 0.4f + random.nextFloat() * 0.35f,
        rotation = random.nextFloat() * 360f,
        brushFrom = Color.White,
        brushTo = Color.White,
        alpha = 0.55f,
        outlined = true,
    )
    return ArtworkSpec(
        background = palette.take(3).let { if (random.nextBoolean()) it else it.reversed() } + palette[4],
        backgroundAngle = random.nextFloat() * 360f,
        iridescent = iridescent,
        sweepCenter = Offset(random.nextFloat(), random.nextFloat()),
        sheenCenter = Offset(0.15f + random.nextFloat() * 0.7f, 0.1f + random.nextFloat() * 0.4f),
        bandAngle = 20f + random.nextFloat() * 50f,
        bandWidth = 0.08f + random.nextFloat() * 0.08f,
        blobs = if (random.nextFloat() < 0.6f) listOf(big, ring, small) else listOf(big, small),
    )
}

private fun linearAt(size: Size, angleDeg: Float, colors: List<Color>, tileMode: TileMode = TileMode.Clamp, span: Float = 1f): Brush {
    val radians = Math.toRadians(angleDeg.toDouble())
    val half = max(size.width, size.height) * span / 2f
    val dx = (cos(radians) * half).toFloat()
    val dy = (sin(radians) * half).toFloat()
    val c = Offset(size.width / 2f, size.height / 2f)
    return Brush.linearGradient(colors, start = c - Offset(dx, dy), end = c + Offset(dx, dy), tileMode = tileMode)
}

@Composable
private fun GradientArtwork(seed: Any?, modifier: Modifier) {
    val spec = remember(seed) { artworkSpec(seed) }
    Spacer(
        modifier = modifier.drawWithCache {
            val side = size.minDimension
            val background = linearAt(size, spec.backgroundAngle, spec.background)
            val iridescent = Brush.sweepGradient(
                spec.iridescent,
                center = Offset(size.width * spec.sweepCenter.x, size.height * spec.sweepCenter.y),
            )
            val sheen = Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0f)),
                center = Offset(size.width * spec.sheenCenter.x, size.height * spec.sheenCenter.y),
                radius = side * 0.6f,
            )
            val bands = linearAt(
                size,
                spec.bandAngle,
                listOf(
                    Color.White.copy(alpha = 0f),
                    Color.White.copy(alpha = 0.18f),
                    Color.White.copy(alpha = 0f),
                ),
                tileMode = TileMode.Mirror,
                span = spec.bandWidth * 2f,
            )
            val shade = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f)))
            val blobPaths = spec.blobs.map { blob ->
                val px = side * blob.size
                val path: Path = blob.polygon.toComposePath()
                path.transform(Matrix().apply {
                    translate(size.width * blob.center.x - px / 2f, size.height * blob.center.y - px / 2f)
                    scale(px, px)
                })
                val brush = Brush.linearGradient(
                    listOf(blob.brushFrom, lerp(blob.brushFrom, blob.brushTo, 0.5f), blob.brushTo),
                    start = Offset(size.width * blob.center.x - px / 2f, size.height * blob.center.y - px / 2f),
                    end = Offset(size.width * blob.center.x + px / 2f, size.height * blob.center.y + px / 2f),
                )
                Triple(blob, path, brush)
            }
            val ringStroke = Stroke(width = side * 0.035f)
            onDrawBehind {
                drawRect(background)
                drawRect(iridescent, alpha = 0.42f)
                blobPaths.forEach { (blob, path, brush) ->
                    val pivot = Offset(size.width * blob.center.x, size.height * blob.center.y)
                    withTransform({ rotate(blob.rotation, pivot) }) {
                        if (blob.outlined) {
                            drawPath(path, brush, alpha = blob.alpha, style = ringStroke)
                        } else {
                            drawPath(path, brush, alpha = blob.alpha)
                            drawPath(path, iridescent, alpha = 0.28f)
                        }
                    }
                }
                drawRect(bands)
                drawRect(sheen)
                drawRect(shade)
            }
        },
    )
}
