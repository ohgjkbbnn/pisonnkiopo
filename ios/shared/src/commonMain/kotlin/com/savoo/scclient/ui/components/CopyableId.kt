package com.savoo.scclient.ui.components

import com.savoo.scclient.compat.*
import com.savoo.scclient.compat.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.savoo.scclient.resources.*

@Composable
fun CopyableId(
    id: Long,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    Text(
        text = stringResource(Res.string.artist_id, id),
        style = style,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                ClipboardManager
                    ?.setPrimaryClip(ClipData.newPlainText("ID", id.toString()))
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(context, context.getString(Res.string.id_copied), Toast.LENGTH_SHORT).show()
                }
            }
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}
