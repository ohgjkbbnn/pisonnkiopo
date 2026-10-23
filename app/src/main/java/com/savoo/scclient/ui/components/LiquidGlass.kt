package com.savoo.scclient.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

val LocalGlassState = staticCompositionLocalOf<HazeState?> { null }
val LocalLiquidGlass = staticCompositionLocalOf { false }
val LocalDockBackground = staticCompositionLocalOf { true }

@Composable
fun Modifier.liquidGlass(
    shape: Shape,
    tint: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    density: Float = 0.16f,
    blurRadius: Dp = 14.dp,
): Modifier {
    if (!LocalLiquidGlass.current) return this
    val state = LocalGlassState.current ?: return this
    val dark = tint.luminance() < 0.5f
    val style = HazeStyle(
        backgroundColor = tint,
        tints = listOf(
            HazeTint(tint.copy(alpha = if (dark) density else density + 0.06f)),
            HazeTint(Color.White.copy(alpha = if (dark) 0.06f else 0.28f)),
        ),
        blurRadius = blurRadius,
        noiseFactor = 0f,
    )
    return this
        .clip(shape)
        .hazeChild(state = state, style = style)
        .glassHighlight(shape, dark)
}

/**
 * Лёгкое стекло без блюра: полупрозрачная заливка + блик и обводка.
 * Почти не грузит GPU и работает везде (в шторках, диалогах, на кнопках),
 * потому что не требует HazeState. Включается тем же переключателем Liquid Glass.
 */
@Composable
fun Modifier.liquidGlassLite(
    shape: Shape,
    tint: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    alpha: Float = 0.55f,
): Modifier {
    if (!LocalLiquidGlass.current) return this
    val dark = tint.luminance() < 0.5f
    return this
        .clip(shape)
        .background(tint.copy(alpha = if (dark) alpha else alpha + 0.1f))
        .glassHighlight(shape, dark)
}

fun Modifier.glassHighlight(shape: Shape, dark: Boolean, strength: Float = 1f): Modifier = composed {
    if (!LocalLiquidGlass.current) return@composed Modifier
    Modifier.drawWithContent {
    drawContent()
    val rimBright = Color.White.copy(alpha = (if (dark) 0.55f else 0.9f) * strength)
    val rimFaint = Color.White.copy(alpha = (if (dark) 0.05f else 0.15f) * strength)
    val sheen = Color.White.copy(alpha = (if (dark) 0.08f else 0.2f) * strength)
    val outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(
        outline = outline,
        brush = Brush.verticalGradient(
            0f to sheen,
            0.35f to Color.Transparent,
            1f to Color.Transparent,
        ),
    )
    drawOutline(
        outline = outline,
        brush = Brush.linearGradient(
            0f to rimBright,
            0.3f to rimFaint,
            0.7f to rimFaint,
            1f to rimBright.copy(alpha = rimBright.alpha * 0.7f),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
        style = Stroke(width = 1.dp.toPx()),
    )
    }
}

private val GlassPressSpring = spring<Float>(dampingRatio = 0.6f, stiffness = 520f)
private val GlassReleaseSpring = spring<Float>(dampingRatio = 0.42f, stiffness = 380f)
private val GlassShiftReleaseSpring = spring<Offset>(dampingRatio = 0.42f, stiffness = 380f)

fun Modifier.glassStretch(pressScale: Float = 1.06f, stretch: Float = 0.14f): Modifier = composed {
    if (!LocalLiquidGlass.current) return@composed Modifier
    val scope = rememberCoroutineScope()
    val scaleX = remember { Animatable(1f) }
    val scaleY = remember { Animatable(1f) }
    val shift = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    Modifier
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                scope.launch { scaleX.animateTo(pressScale, GlassPressSpring) }
                scope.launch { scaleY.animateTo(pressScale, GlassPressSpring) }
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val delta = change.position - down.position
                    val nx = (delta.x / size.width.coerceAtLeast(1)).coerceIn(-1f, 1f)
                    val ny = (delta.y / size.height.coerceAtLeast(1)).coerceIn(-1f, 1f)
                    scope.launch {
                        scaleX.snapTo(pressScale + abs(nx) * stretch - abs(ny) * stretch * 0.35f)
                        scaleY.snapTo(pressScale + abs(ny) * stretch - abs(nx) * stretch * 0.35f)
                        shift.snapTo(Offset(nx * size.width * 0.05f, ny * size.height * 0.05f))
                    }
                }
                scope.launch { scaleX.animateTo(1f, GlassReleaseSpring) }
                scope.launch { scaleY.animateTo(1f, GlassReleaseSpring) }
                scope.launch { shift.animateTo(Offset.Zero, GlassShiftReleaseSpring) }
            }
        }
        .graphicsLayer {
            this.scaleX = scaleX.value
            this.scaleY = scaleY.value
            translationX = shift.value.x
            translationY = shift.value.y
        }
}
