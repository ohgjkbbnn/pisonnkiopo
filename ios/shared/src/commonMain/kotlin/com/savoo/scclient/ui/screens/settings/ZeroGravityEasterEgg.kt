package com.savoo.scclient.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
fun ZeroGravityEasterEggDialog(onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { onDismiss() }
}
