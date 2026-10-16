package com.savoo.scclient.ui.screens.player

import org.jetbrains.compose.resources.StringResource
import com.savoo.scclient.compat.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.savoo.scclient.resources.*
import com.savoo.scclient.player.PlayerController
import com.savoo.scclient.player.SoundMode
import com.savoo.scclient.player.SoundPreset
import com.savoo.scclient.ui.haptics.rememberHapticTick
import kotlin.math.roundToInt

private fun SoundPreset.labelRes(): StringResource = when (this) {
    SoundPreset.OFF -> Res.string.sound_preset_off
    SoundPreset.SLOWED_REVERB -> Res.string.sound_preset_slowed_reverb
    SoundPreset.SLOWED -> Res.string.sound_preset_slowed
    SoundPreset.SPED_UP -> Res.string.sound_preset_sped_up
    SoundPreset.NIGHTCORE -> Res.string.sound_preset_nightcore
    SoundPreset.CUSTOM -> Res.string.sound_preset_custom
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundModeSheet(
    controller: PlayerController,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        SoundModeContent(controller = controller)
    }
}

@Composable
fun SoundModeContent(
    controller: PlayerController,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHapticTick()
    val state by controller.state.collectAsState()
    val mode = state.soundMode

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.player_sound),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(Res.string.player_speed_format, formatSpeed(mode.rate)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(Res.string.sound_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        SoundPreset.entries.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { preset ->
                    PresetTile(
                        preset = preset,
                        selected = mode.preset == preset,
                        rate = if (preset == SoundPreset.CUSTOM) mode.customRate else preset.rate,
                        onClick = {
                            haptic()
                            controller.setSoundMode(mode.copy(preset = preset))
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = mode.preset == SoundPreset.CUSTOM,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                SoundSlider(
                    label = stringResource(Res.string.sound_rate),
                    valueText = stringResource(Res.string.player_speed_format, formatSpeed(mode.customRate)),
                    value = mode.customRate,
                    range = SoundMode.MIN_RATE..SoundMode.MAX_RATE,
                    steps = ((SoundMode.MAX_RATE - SoundMode.MIN_RATE) / RATE_STEP).roundToInt() - 1,
                    onChange = { controller.setSoundMode(mode.copy(customRate = snap(it, RATE_STEP))) },
                    onChangeFinished = haptic,
                )
                SoundSlider(
                    label = stringResource(Res.string.sound_reverb),
                    valueText = stringResource(Res.string.sound_percent_format, (mode.customReverb * 100).roundToInt()),
                    value = mode.customReverb,
                    range = 0f..1f,
                    steps = 19,
                    onChange = { controller.setSoundMode(mode.copy(customReverb = snap(it, LEVEL_STEP))) },
                    onChangeFinished = haptic,
                )
                SoundSlider(
                    label = stringResource(Res.string.sound_bass),
                    valueText = stringResource(Res.string.sound_percent_format, (mode.customBass * 100).roundToInt()),
                    value = mode.customBass,
                    range = 0f..1f,
                    steps = 19,
                    onChange = { controller.setSoundMode(mode.copy(customBass = snap(it, LEVEL_STEP))) },
                    onChangeFinished = haptic,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun PresetTile(
    preset: SoundPreset,
    selected: Boolean,
    rate: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "soundPresetContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "soundPresetContent",
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(if (selected) 24.dp else 16.dp),
        color = container,
        contentColor = content,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Text(
                stringResource(preset.labelRes()),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(Res.string.player_speed_format, formatSpeed(rate)),
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun SoundSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
    onChangeFinished: () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(
                valueText,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onChangeFinished,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val RATE_STEP = 0.05f
private const val LEVEL_STEP = 0.05f

private fun snap(value: Float, step: Float): Float = (value / step).roundToInt() * step
