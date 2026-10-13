package com.savoo.scclient.ui.screens.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.RangeSliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.savoo.scclient.R
import com.savoo.scclient.player.LoopRegion
import com.savoo.scclient.player.PlayerController
import com.savoo.scclient.ui.haptics.rememberHaptics

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoopSheet(
    controller: PlayerController,
    waveform: FloatArray?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LoopContent(controller = controller, waveform = waveform)
    }
}

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoopContent(
    controller: PlayerController,
    waveform: FloatArray?,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val state by controller.state.collectAsState()
    val duration = state.durationMs.coerceAtLeast(1L)
    val loop = state.loop
    var range by remember(state.currentTrack?.id) {
        mutableStateOf(initialLoopRange(loop, state.positionMs, duration))
    }
    LaunchedEffect(loop) {
        if (loop != null) range = loop.startMs.toFloat()..loop.endMs.toFloat()
    }

    var appliedStart by remember(state.currentTrack?.id) { mutableStateOf(range.start) }

    fun apply(start: Float, end: Float, seekToStart: Boolean) {
        range = start..end
        appliedStart = start
        if (state.loop == null) return
        controller.setLoop(start.toLong(), end.toLong())
        if (seekToStart) controller.seekTo(start.toLong())
    }

    val scrubLevels = remember(waveform) { waveformLevels(waveform, SCRUB_DETENTS) }
    var lastDetent by remember { mutableIntStateOf(-1) }
    val startInteraction = remember { MutableInteractionSource() }
    val endInteraction = remember { MutableInteractionSource() }
    val startActive = startInteraction.collectIsDraggedAsState().value || startInteraction.collectIsPressedAsState().value
    val endActive = endInteraction.collectIsDraggedAsState().value || endInteraction.collectIsPressedAsState().value

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.player_loop_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(
                    R.string.player_loop_range,
                    formatTime(range.start.toLong()),
                    formatTime(range.endInclusive.toLong()),
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(16.dp))

        val accent = MaterialTheme.colorScheme.primary
        RangeSlider(
            value = range,
            onValueChange = { next ->
                if (next.endInclusive - next.start >= PlayerController.MIN_LOOP_MS) {
                    val moved = if (next.start != range.start) next.start else next.endInclusive
                    val detent = scrubDetent(moved, duration)
                    if (detent != lastDetent) {
                        haptics.scrub(if (waveform == null) 0.35f else scrubLevels[detent])
                        lastDetent = detent
                    }
                    range = next
                }
            },
            onValueChangeFinished = {
                haptics.seekRelease()
                lastDetent = -1
                apply(range.start, range.endInclusive, seekToStart = range.start != appliedStart)
            },
            valueRange = 0f..duration.toFloat(),
            startInteractionSource = startInteraction,
            endInteractionSource = endInteraction,
            modifier = Modifier.fillMaxWidth(),
            startThumb = { LoopThumb(color = accent, active = startActive) },
            endThumb = { LoopThumb(color = accent, active = endActive) },
            track = { sliderState ->
                LoopWaveformTrack(
                    sliderState = sliderState,
                    samples = waveform,
                    playheadFraction = (state.positionMs.toFloat() / duration).coerceIn(0f, 1f),
                    activeColor = accent,
                    inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    playheadColor = MaterialTheme.colorScheme.onSurface,
                )
            },
        )
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LoopPointCard(
                label = stringResource(R.string.player_loop_start),
                timeMs = range.start.toLong(),
                onNudge = { delta ->
                    haptics.tick()
                    val start = (range.start + delta)
                        .coerceIn(0f, range.endInclusive - PlayerController.MIN_LOOP_MS)
                    apply(start, range.endInclusive, seekToStart = true)
                },
                onSetNow = {
                    haptics.tick()
                    val now = controller.currentPosition().toFloat()
                    val end = range.endInclusive.coerceAtLeast(now + PlayerController.MIN_LOOP_MS).coerceAtMost(duration.toFloat())
                    apply(now.coerceAtMost(end - PlayerController.MIN_LOOP_MS).coerceAtLeast(0f), end, seekToStart = false)
                },
                modifier = Modifier.weight(1f),
            )
            LoopPointCard(
                label = stringResource(R.string.player_loop_end),
                timeMs = range.endInclusive.toLong(),
                onNudge = { delta ->
                    haptics.tick()
                    val end = (range.endInclusive + delta)
                        .coerceIn(range.start + PlayerController.MIN_LOOP_MS, duration.toFloat())
                    apply(range.start, end, seekToStart = false)
                },
                onSetNow = {
                    haptics.tick()
                    val now = controller.currentPosition().toFloat()
                    val start = range.start.coerceAtMost(now - PlayerController.MIN_LOOP_MS).coerceAtLeast(0f)
                    apply(start, now.coerceAtLeast(start + PlayerController.MIN_LOOP_MS).coerceAtMost(duration.toFloat()), seekToStart = false)
                },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(20.dp))

        AnimatedContent(
            targetState = loop != null,
            transitionSpec = { fadeIn(spring(stiffness = Spring.StiffnessMedium)) togetherWith fadeOut(spring(stiffness = Spring.StiffnessMedium)) },
            label = "loopToggle",
        ) { active ->
            if (active) {
                OutlinedButton(
                    onClick = { haptics.click(); controller.clearLoop() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(stringResource(R.string.player_loop_disable))
                }
            } else {
                Button(
                    onClick = {
                        haptics.success()
                        controller.setLoop(range.start.toLong(), range.endInclusive.toLong())
                        controller.seekTo(range.start.toLong())
                        if (!controller.state.value.isPlaying) controller.togglePlayPause()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Filled.Loop, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.player_loop_enable))
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun LoopThumb(color: Color, active: Boolean) {
    val width by animateFloatAsState(
        targetValue = if (active) 8f else 5f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "loopThumbWidth",
    )
    Box(modifier = Modifier.size(width = 8.dp, height = 64.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(width = width.dp, height = 64.dp)
                .background(color, RoundedCornerShape(50)),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoopWaveformTrack(
    sliderState: RangeSliderState,
    samples: FloatArray?,
    playheadFraction: Float,
    activeColor: Color,
    inactiveColor: Color,
    playheadColor: Color,
) {
    val span = (sliderState.valueRange.endInclusive - sliderState.valueRange.start).coerceAtLeast(0.0001f)
    val startFraction = ((sliderState.activeRangeStart - sliderState.valueRange.start) / span).coerceIn(0f, 1f)
    val endFraction = ((sliderState.activeRangeEnd - sliderState.valueRange.start) / span).coerceIn(0f, 1f)
    val density = LocalDensity.current
    var widthPx by remember { mutableIntStateOf(0) }
    val barWidthPx = with(density) { 3.dp.toPx() }
    val gapPx = with(density) { 2.dp.toPx() }
    val barCount = ((widthPx + gapPx) / (barWidthPx + gapPx)).toInt().coerceAtLeast(1)
    val levels = remember(samples, barCount) { waveformLevels(samples, barCount) }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .onSizeChanged { widthPx = it.width },
    ) {
        val minHeight = barWidthPx
        val step = (size.width - barWidthPx) / (levels.size - 1).coerceAtLeast(1)
        val midY = size.height / 2f
        val corner = CornerRadius(barWidthPx / 2f, barWidthPx / 2f)
        val left = size.width * startFraction
        val right = size.width * endFraction
        drawRoundRect(
            color = activeColor.copy(alpha = 0.12f),
            topLeft = Offset(left, 0f),
            size = Size((right - left).coerceAtLeast(0f), size.height),
            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
        )
        fun drawBars(color: Color) {
            for (index in levels.indices) {
                val h = (minHeight + (size.height * 0.85f - minHeight) * levels[index]).coerceAtLeast(minHeight)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(index * step, midY - h / 2f),
                    size = Size(barWidthPx, h),
                    cornerRadius = corner,
                )
            }
        }
        clipRect(right = left) { drawBars(inactiveColor) }
        clipRect(left = right) { drawBars(inactiveColor) }
        clipRect(left = left, right = right) { drawBars(activeColor) }
        val playheadX = size.width * playheadFraction
        drawLine(
            color = playheadColor,
            start = Offset(playheadX, 0f),
            end = Offset(playheadX, size.height),
            strokeWidth = 2.dp.toPx(),
        )
    }
}

@Composable
private fun LoopPointCard(
    label: String,
    timeMs: Long,
    onNudge: (Float) -> Unit,
    onSetNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatTimePrecise(timeMs),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onNudge(-NUDGE_MS) }) {
                    Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.player_loop_earlier))
                }
                FilledTonalIconButton(onClick = onSetNow) {
                    Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.player_loop_set_now))
                }
                IconButton(onClick = { onNudge(NUDGE_MS) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.player_loop_later))
                }
            }
        }
    }
}

fun Modifier.loopRegion(
    loop: LoopRegion?,
    durationMs: Long,
    color: Color,
    trackInset: Dp,
): Modifier = composed {
    if (loop == null || durationMs <= 0L) return@composed this
    val insetPx = with(LocalDensity.current) { trackInset.toPx() }
    drawBehind {
        val usable = (size.width - insetPx * 2).coerceAtLeast(1f)
        val left = insetPx + usable * (loop.startMs.toFloat() / durationMs).coerceIn(0f, 1f)
        val right = insetPx + usable * (loop.endMs.toFloat() / durationMs).coerceIn(0f, 1f)
        val radius = 10.dp.toPx()
        drawRoundRect(
            color = color.copy(alpha = 0.16f),
            topLeft = Offset(left - radius / 2f, 0f),
            size = Size((right - left + radius).coerceAtLeast(0f), size.height),
            cornerRadius = CornerRadius(radius, radius),
        )
    }
}

private const val NUDGE_MS = 500f
private const val DEFAULT_LOOP_MS = 20_000L

private fun initialLoopRange(loop: LoopRegion?, positionMs: Long, durationMs: Long): ClosedFloatingPointRange<Float> {
    if (loop != null) return loop.startMs.toFloat()..loop.endMs.toFloat()
    val length = DEFAULT_LOOP_MS.coerceAtMost(durationMs)
    val start = positionMs.coerceIn(0L, (durationMs - length).coerceAtLeast(0L))
    return start.toFloat()..(start + length).toFloat()
}

private fun formatTimePrecise(ms: Long): String {
    val tenths = (ms % 1000) / 100
    return "${formatTime(ms)}.$tenths"
}
