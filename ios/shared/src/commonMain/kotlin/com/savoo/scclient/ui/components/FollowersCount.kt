package com.savoo.scclient.ui.components

import com.savoo.scclient.compat.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import org.jetbrains.compose.resources.pluralStringResource
import com.savoo.scclient.resources.*

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun followersCountText(count: Long): String =
    pluralStringResource(Res.plurals.followers_count, count.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(), count)
