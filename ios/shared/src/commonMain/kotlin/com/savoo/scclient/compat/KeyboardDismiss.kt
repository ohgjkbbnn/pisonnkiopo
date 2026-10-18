package com.savoo.scclient.compat

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlin.math.abs

fun Modifier.dismissKeyboardOnTapOrScroll(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val dismiss = remember(focusManager, keyboard) {
        {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }
    val scrollConnection = remember(dismiss) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && abs(available.y) > 4f) dismiss()
                return Offset.Zero
            }
        }
    }
    this
        .nestedScroll(scrollConnection)
        .pointerInput(dismiss) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                val up = waitForUpOrCancellation(PointerEventPass.Final) ?: return@awaitEachGesture
                if (!down.isConsumed && !up.isConsumed) dismiss()
            }
        }
}

@Composable
fun hideKeyboardOnSearch(): (String) -> Unit {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return remember(focusManager, keyboard) {
        { _: String ->
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }
}
