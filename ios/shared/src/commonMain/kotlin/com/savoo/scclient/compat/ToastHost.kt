package com.savoo.scclient.compat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

object ToastHost {
    private data class Message(val text: String, val long: Boolean, val id: Long)

    private val current = MutableStateFlow<Message?>(null)
    private var counter = 0L
    var dockInset by mutableStateOf(0.dp)

    fun show(text: String, long: Boolean) {
        current.value = Message(text, long, ++counter)
    }

    @Composable
    fun Overlay() {
        val message by current.collectAsState()
        LaunchedEffect(message?.id) {
            val shown = message ?: return@LaunchedEffect
            delay(if (shown.long) 3500 else 2000)
            if (current.value?.id == shown.id) current.value = null
        }
        val bottom = if (dockInset > 0.dp) dockInset + 12.dp else 96.dp
        Box(Modifier.fillMaxSize().then(if (dockInset > 0.dp) Modifier.statusBarsPadding() else Modifier.safeDrawingPadding()), contentAlignment = Alignment.BottomCenter) {
            AnimatedVisibility(
                visible = message != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(bottom = bottom, start = 24.dp, end = 24.dp),
                ) {
                    Text(
                        message?.text.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}
