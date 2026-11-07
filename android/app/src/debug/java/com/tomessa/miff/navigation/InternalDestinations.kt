package com.tomessa.miff.navigation

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.tomessa.miff.AppContainer
import com.tomessa.miff.data.FirebaseDiagnosticRepository
import com.tomessa.miff.ui.diagnostic.DiagnosticScreen
import com.tomessa.miff.ui.diagnostic.DiagnosticViewModel
import com.tomessa.miff.ui.navigation.AppRoute
import kotlinx.serialization.Serializable

@Serializable
data object DiagnosticRoute

/** Debug build: the internal diagnostic screen is the start destination and can open the real app. */
object InternalDestinations {
    val startDestination: Any = DiagnosticRoute

    fun register(
        builder: NavGraphBuilder,
        navController: NavController,
        container: AppContainer,
    ) {
        builder.composable<DiagnosticRoute> {
            val viewModel: DiagnosticViewModel =
                viewModel(
                    factory =
                        viewModelFactory {
                            initializer {
                                DiagnosticViewModel(
                                    repository = FirebaseDiagnosticRepository(),
                                    privacy = container.privacySettings,
                                    tracker = container.analyticsTracker,
                                )
                            }
                        },
                )
            DiagnosticScreen(
                viewModel = viewModel,
                onOpenHome = { navController.navigate(AppRoute) },
            )
        }
    }
}
