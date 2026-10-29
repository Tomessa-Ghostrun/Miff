package com.tomessa.miff.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import com.tomessa.miff.AppContainer
import com.tomessa.miff.ui.navigation.AppRoute

/** Release build: no internal screen. The only destination is the app itself. */
object InternalDestinations {
    val startDestination: Any = AppRoute

    @Suppress("UNUSED_PARAMETER")
    fun register(
        builder: NavGraphBuilder,
        navController: NavController,
        container: AppContainer,
    ) = Unit
}
