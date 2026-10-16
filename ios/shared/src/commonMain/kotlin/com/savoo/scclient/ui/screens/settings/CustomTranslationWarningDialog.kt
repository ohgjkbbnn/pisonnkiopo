package com.savoo.scclient.ui.screens.settings

import com.savoo.scclient.compat.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.savoo.scclient.resources.*
import com.savoo.scclient.i18n.CustomStringsCheck

@Composable
fun CustomTranslationWarningDialog(
    check: CustomStringsCheck,
    onSkipProblems: () -> Unit,
    onLoadAnyway: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(Res.string.custom_language_warning_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        Res.string.custom_language_warning_size,
                        Formatter.formatShortFileSize(context, check.sizeBytes),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (check.longStrings > 0) {
                    Text(
                        stringResource(Res.string.custom_language_warning_long, check.longStrings),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (check.brokenPlaceholders > 0) {
                    Text(
                        stringResource(Res.string.custom_language_warning_placeholders, check.brokenPlaceholders),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    stringResource(Res.string.custom_language_warning_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSkipProblems) {
                Text(stringResource(Res.string.custom_language_warning_skip))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCancel) { Text(stringResource(Res.string.cancel)) }
                TextButton(onClick = onLoadAnyway) {
                    Text(stringResource(Res.string.custom_language_warning_anyway))
                }
            }
        },
    )
}
