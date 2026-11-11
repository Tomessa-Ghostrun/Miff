package com.tomessa.miff.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tomessa.miff.AppContainer
import com.tomessa.miff.navigation.InternalDestinations
import kotlinx.serialization.Serializable

/** Typed routes (Navigation Compose + kotlinx.serialization). */
@Serializable
data object AppRoute

/**
 * The app always starts on [AppRoute] (splash, then carousel / sign-in / Feed according to the state), in every build. Since the PM's request of
 * 2026-11-12 the internal diagnostic screen is no longer the start destination of the test builds: debug builds add it as a side destination, reached
 * from Settings or by the 10 s hold; release builds add nothing.
 */
@Composable
fun MiffNavHost(container: AppContainer) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = AppRoute) {
        composable<AppRoute> {
            AppRoot(container = container)
        }
        InternalDestinations.register(this, navController, container)
    }
    InternalDestinations.OpenOnRequest(navController)
}
