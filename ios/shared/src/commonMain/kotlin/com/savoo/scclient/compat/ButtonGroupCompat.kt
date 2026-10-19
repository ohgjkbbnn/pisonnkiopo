package com.savoo.scclient.compat

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.ButtonGroupScope as MaterialButtonGroupScope

interface ButtonGroupScope {
    fun Modifier.weight(weight: Float, fill: Boolean = true): Modifier
}

@Composable
fun ButtonGroup(
    modifier: Modifier = Modifier,
    expandedRatio: Float = ButtonGroupDefaults.ExpandedRatio,
    horizontalArrangement: Arrangement.Horizontal = ButtonGroupDefaults.HorizontalArrangement,
    content: @Composable ButtonGroupScope.() -> Unit,
) {
    androidx.compose.material3.ButtonGroup(
        modifier = modifier,
        expandedRatio = expandedRatio,
        horizontalArrangement = horizontalArrangement,
    ) {
        val scope = remember(this) { PressAnimatedButtonGroupScope(this) }
        scope.content()
    }
}

private class PressAnimatedButtonGroupScope(private val group: MaterialButtonGroupScope) : ButtonGroupScope {
    override fun Modifier.weight(weight: Float, fill: Boolean): Modifier = composed {
        val presses = remember { MutableInteractionSource() }
        with(group) { Modifier.weight(weight).animateWidth(presses) }
            .pointerInput(presses) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val press = PressInteraction.Press(down.position)
                    presses.tryEmit(press)
                    val up = waitForUpOrCancellation(PointerEventPass.Initial)
                    presses.tryEmit(if (up != null) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                }
            }
    }
}
