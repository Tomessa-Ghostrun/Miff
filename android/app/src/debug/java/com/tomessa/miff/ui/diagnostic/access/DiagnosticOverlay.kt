package com.tomessa.miff.ui.diagnostic.access

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Shows the diagnostic screen ABOVE the app, in a full-screen window, while the app itself stays composed underneath: closing it returns to the
 * app exactly as it was (open dialogs, typed text, banners). A window of its own also stacks above the dialogs and sheets of the app, so the
 * screen is never hidden behind one of them when it was opened from inside one. The back gesture closes it.
 */
@Composable
fun DiagnosticOverlay(content: @Composable (onClose: () -> Unit) -> Unit) {
    val open by DiagnosticRequests.isOpen.collectAsState()
    if (open) {
        Dialog(
            onDismissRequest = DiagnosticRequests::close,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(Modifier.fillMaxSize()) { content(DiagnosticRequests::close) }
        }
    }
}
