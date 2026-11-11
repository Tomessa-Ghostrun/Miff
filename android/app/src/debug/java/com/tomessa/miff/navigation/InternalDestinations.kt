package com.tomessa.miff.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.tomessa.miff.AppContainer
import com.tomessa.miff.data.FirebaseDiagnosticRepository
import com.tomessa.miff.data.SharedPreferencesDebugAppCheckSetting
import com.tomessa.miff.ui.diagnostic.DiagnosticScreen
import com.tomessa.miff.ui.diagnostic.DiagnosticViewModel
import com.tomessa.miff.ui.diagnostic.access.DiagnosticRequests
import com.tomessa.miff.ui.diagnostic.access.diagnosticHoldModifier
import kotlinx.serialization.Serializable

@Serializable
data object DiagnosticRoute

/**
 * Debug build: the internal diagnostic screen is NOT the start destination any more (PM, 2026-11-12): the app starts like in production. The
 * screen is reached only from Settings > Diagnostic or by holding a finger down for 10 s anywhere (see ui/diagnostic/access). It opens ON TOP of
 * the app (the way back returns to where the person was).
 */
object InternalDestinations {
    /** The line of Settings is drawn (debug build only). */
    @Composable
    fun SettingsEntry(row: @Composable (onClick: () -> Unit) -> Unit) {
        row { DiagnosticRequests.request() }
    }

    /** The 10 s hold detector, to apply to the root of a window. */
    @Composable
    fun holdModifier(): Modifier = diagnosticHoldModifier()

    /** Opens the diagnostic screen each time one is requested (unless it is already the screen shown). */
    @Composable
    fun OpenOnRequest(navController: NavController) {
        LaunchedEffect(navController) {
            DiagnosticRequests.events.collect {
                if (navController.currentDestination?.hasRoute<DiagnosticRoute>() != true) {
                    navController.navigate(DiagnosticRoute) { launchSingleTop = true }
                }
            }
        }
    }

    fun register(
        builder: NavGraphBuilder,
        navController: NavController,
        container: AppContainer,
    ) {
        builder.composable<DiagnosticRoute> {
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
            DiagnosticScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
