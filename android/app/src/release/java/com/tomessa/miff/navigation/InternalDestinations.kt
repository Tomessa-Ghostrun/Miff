package com.tomessa.miff.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import com.tomessa.miff.AppContainer

/**
 * Release build (and releaseLike): no internal screen, no secret gesture, no Settings line. Every member does nothing; the CI proves that the
 * hold detector of the debug build is absent from the release bundle.
 */
object InternalDestinations {
    @Suppress("UNUSED_PARAMETER")
    @Composable
    fun SettingsEntry(row: @Composable (onClick: () -> Unit) -> Unit) = Unit

    @Composable
    fun holdModifier(): Modifier = Modifier

    @Suppress("UNUSED_PARAMETER")
    @Composable
    fun OpenOnRequest(navController: NavController) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun register(
        builder: NavGraphBuilder,
        navController: NavController,
        container: AppContainer,
    ) = Unit
}
