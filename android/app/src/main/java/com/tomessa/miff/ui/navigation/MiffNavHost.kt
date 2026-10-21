package com.tomessa.miff.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tomessa.miff.AppContainer
import com.tomessa.miff.navigation.InternalDestinations
import com.tomessa.miff.ui.home.HomeScreen
import kotlinx.serialization.Serializable

/** Typed routes (Navigation Compose + kotlinx.serialization). */
@Serializable
data object HomeRoute

@Composable
fun MiffNavHost(container: AppContainer) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = InternalDestinations.startDestination) {
        composable<HomeRoute> {
            HomeScreen()
        }
        // Debug builds add the internal diagnostic screen here; release builds add nothing.
        InternalDestinations.register(this, navController, container)
    }
}
