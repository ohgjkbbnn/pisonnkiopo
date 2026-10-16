package com.savoo.scclient.ui.components

import org.jetbrains.compose.resources.StringResource
import com.savoo.scclient.compat.*
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UndoAction(
    val messageRes: StringResource,
    val messageArgs: List<Any> = emptyList(),
    val icon: ImageVector? = null,
    val onUndo: suspend () -> Unit,
    val id: Long = System.nanoTime(),
)
class UndoController() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _current = MutableStateFlow<UndoAction?>(null)
    val current = _current.asStateFlow()

    fun show(action: UndoAction) {
        _current.value = action
    }

    fun undo(action: UndoAction) {
        if (_current.compareAndSet(action, null)) scope.launch { action.onUndo() }
    }

    fun dismiss(action: UndoAction) {
        _current.compareAndSet(action, null)
    }
}
