package com.dogmaticcentral.bookreader.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.dogmaticcentral.bookreader.R

@Composable
fun ErrorScreen(
    message: String,
    onDismiss: () -> Unit,
    dismissButtonText: String = stringResource(R.string.exit)
) {
    DisposableEffect(Unit) {
        onDispose {
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = { /* Prevent dismissing by clicking outside */ },
        title = {
            Text(
                text = stringResource(R.string.database_error_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissButtonText)
            }
        },
        containerColor = MaterialTheme.colorScheme.errorContainer,
        textContentColor = MaterialTheme.colorScheme.onErrorContainer
    )
}

@Preview
@Composable
fun ErrorScreenPreview() {
    ErrorScreen(
        message = "Database file not found. Please ensure the database is properly installed.",
        onDismiss = { }
    )
}