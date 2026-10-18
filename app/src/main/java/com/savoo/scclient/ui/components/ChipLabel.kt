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
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val maxWidth = constraints.maxWidth
        val textWidth = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
        val iconWidth = if (icon != null) with(density) { (ToggleButtonDefaults.IconSize + ToggleButtonDefaults.IconSpacing).roundToPx() } else 0
        val bounded = constraints.hasBoundedWidth
        val showIcon = icon != null && (!bounded || textWidth + iconWidth <= maxWidth)
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
