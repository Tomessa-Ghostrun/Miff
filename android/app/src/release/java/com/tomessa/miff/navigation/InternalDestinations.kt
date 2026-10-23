package com.tomessa.miff.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import com.tomessa.miff.AppContainer
import com.tomessa.miff.ui.navigation.HomeRoute

/** Release build: no internal screen. The only destination is the welcome screen. */
object InternalDestinations {
    val startDestination: Any = HomeRoute

    @Suppress("UNUSED_PARAMETER")
    fun register(
        builder: NavGraphBuilder,
        navController: NavController,
        container: AppContainer,
    ) = Unit
}
