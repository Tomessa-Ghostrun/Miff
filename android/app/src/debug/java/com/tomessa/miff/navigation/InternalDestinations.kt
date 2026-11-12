package com.tomessa.miff.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tomessa.miff.AppContainer
import com.tomessa.miff.R
import com.tomessa.miff.data.FirebaseDiagnosticRepository
import com.tomessa.miff.data.SharedPreferencesDebugAppCheckSetting
import com.tomessa.miff.ui.diagnostic.DiagnosticScreen
import com.tomessa.miff.ui.diagnostic.DiagnosticViewModel
import com.tomessa.miff.ui.diagnostic.access.DiagnosticOverlay
import com.tomessa.miff.ui.diagnostic.access.DiagnosticRequests
import com.tomessa.miff.ui.diagnostic.access.diagnosticHoldModifier

/**
 * Debug build: the internal diagnostic screen is NOT the start destination any more (PM, 2026-11-12): the app starts like in production. The
 * screen is reached only from Settings > Diagnostic or by holding a finger down for 10 s anywhere (see ui/diagnostic/access). It opens ABOVE
 * the app, which stays composed underneath (no navigation: the way back returns to the app exactly as it was).
 */
object InternalDestinations {
    /** The line of Settings (debug build only); the strings live in src/debug/res. */
    @Composable
    fun SettingsEntry(row: @Composable (title: String, subtitle: String, onClick: () -> Unit) -> Unit) {
        row(
            stringResource(R.string.android_diag_settings_title),
            stringResource(R.string.android_diag_settings_subtitle),
        ) { DiagnosticRequests.request() }
    }

    /** The 10 s hold detector, to apply to the root of a window. */
    @Composable
    fun holdModifier(): Modifier = diagnosticHoldModifier()

    /** The diagnostic screen, shown above the app when it was requested. */
    @Composable
    fun Overlay(container: AppContainer) {
        DiagnosticOverlay { onClose ->
            val context = LocalContext.current.applicationContext
            val viewModel: DiagnosticViewModel =
                viewModel(
                    factory =
                        viewModelFactory {
                            initializer {
                                DiagnosticViewModel(
                                    repository =
                                        FirebaseDiagnosticRepository(
                                            SharedPreferencesDebugAppCheckSetting(context),
                                        ),
                                    privacy = container.privacySettings,
                                    tracker = container.analyticsTracker,
                                )
                            }
                        },
                )
            DiagnosticScreen(viewModel = viewModel, onBack = onClose)
        }
    }
}
