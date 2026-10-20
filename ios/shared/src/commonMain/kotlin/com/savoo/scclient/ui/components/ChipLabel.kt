package com.savoo.scclient.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

private val MinChipFontSize = 10.sp

private class ChipIconFit {
    val misfits = mutableStateMapOf<Any, Boolean>()
    val hideIcons: Boolean get() = misfits.values.any { it }
}

private val LocalChipIconFit = staticCompositionLocalOf<ChipIconFit?> { null }

@Composable
fun ChipIconGroup(content: @Composable () -> Unit) {
    val fit = remember { ChipIconFit() }
    CompositionLocalProvider(LocalChipIconFit provides fit, content = content)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChipLabel(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    icon: ImageVector? = null,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val group = LocalChipIconFit.current
    val key = remember { Any() }
    if (group != null) {
        DisposableEffect(group, key) { onDispose { group.misfits.remove(key) } }
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val maxWidth = constraints.maxWidth
        val textWidth = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
        val iconWidth = if (icon != null) with(density) { (ToggleButtonDefaults.IconSize + ToggleButtonDefaults.IconSpacing).roundToPx() } else 0
        val bounded = constraints.hasBoundedWidth
        val fitsWithIcon = icon != null && (!bounded || textWidth + iconWidth <= maxWidth)
        if (group != null && icon != null) {
            SideEffect { if (group.misfits[key] != !fitsWithIcon) group.misfits[key] = !fitsWithIcon }
        }
        val showIcon = fitsWithIcon && group?.hideIcons != true
        val available = maxWidth - if (showIcon) iconWidth else 0
        val fitted = if (bounded && textWidth > available && available > 0 && style.fontSize.isSpecified) {
            val scaled = (style.fontSize.value * available / textWidth * 0.98f).coerceAtLeast(MinChipFontSize.value)
            style.copy(fontSize = scaled.sp)
        } else {
            style
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showIcon && icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(ToggleButtonDefaults.IconSize))
                Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
            }
            Text(text, style = fitted, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
}
