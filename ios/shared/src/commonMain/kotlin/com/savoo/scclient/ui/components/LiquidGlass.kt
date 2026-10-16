package com.savoo.scclient.ui.components

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
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

val LocalGlassState = staticCompositionLocalOf<HazeState?> { null }

@Composable
fun Modifier.liquidGlass(shape: Shape, tint: Color = MaterialTheme.colorScheme.surfaceContainerHigh): Modifier {
    val state = LocalGlassState.current ?: return this
    val dark = tint.luminance() < 0.5f
    val style = HazeStyle(
        backgroundColor = tint,
        tints = listOf(HazeTint(tint.copy(alpha = if (dark) 0.42f else 0.5f))),
        blurRadius = 28.dp,
        noiseFactor = 0f,
    )
    val rimStrong = Color.White.copy(alpha = if (dark) 0.34f else 0.75f)
    val rimSoft = Color.White.copy(alpha = if (dark) 0.06f else 0.18f)
    val sheen = Color.White.copy(alpha = if (dark) 0.07f else 0.22f)
    return this
        .clip(shape)
        .hazeEffect(state = state, style = style)
        .drawWithContent {
            drawContent()
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(
                outline = outline,
                brush = Brush.verticalGradient(
                    0f to sheen,
                    0.45f to Color.Transparent,
                    1f to Color.Transparent,
                ),
            )
            drawOutline(
                outline = outline,
                brush = Brush.linearGradient(
                    colors = listOf(rimStrong, rimSoft, rimSoft, rimStrong.copy(alpha = rimStrong.alpha * 0.6f)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
                style = Stroke(width = 1.2.dp.toPx()),
            )
        }
}
