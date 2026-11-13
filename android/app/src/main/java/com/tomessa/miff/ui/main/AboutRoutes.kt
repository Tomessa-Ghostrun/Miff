package com.tomessa.miff.ui.main

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.tomessa.miff.BuildConfig
import com.tomessa.miff.data.about.AssetLicenseSource
import com.tomessa.miff.domain.about.AppVersion
import com.tomessa.miff.ui.navigation.popBackStackIfSafe
import com.tomessa.miff.ui.settings.AboutScreen
import com.tomessa.miff.ui.settings.AndroidAboutActions
import com.tomessa.miff.ui.settings.LicenseDetailScreen
import com.tomessa.miff.ui.settings.LicensesScreen
import kotlinx.serialization.Serializable

/** Settings > "À propos et aide" (ticket reglages-a-propos.md). */
@Serializable
data object AboutRoute

/** Settings > À propos et aide > "Licences et attributions". */
@Serializable
data object LicensesRoute

/** The full text of the licences of ONE line of the inventory (its identifier in `assets/licenses/licenses.json`). */
@Serializable
data class LicenseDetailRoute(
    val id: String,
)

/** launchSingleTop: two quick taps open one screen, not two. */
internal fun NavHostController.openAbout() = navigate(AboutRoute) { launchSingleTop = true }

/**
 * The three screens of "À propos et aide", shared by the full Settings and the RESTRICTED Settings of a person with no Miff (decision Q1: the line is in both).
 * [showRemindersHelp] is false in the restricted Settings: "Si les rappels n'arrivent pas" is not offered without a Miff (like Settings > Notifications).
 * The screens read the app's own files and nothing else: no view model, no server, no network (criterion AP18).
 */
internal fun NavGraphBuilder.aboutRoutes(
    navController: NavHostController,
    showRemindersHelp: Boolean,
) {
    composable<AboutRoute> {
        val context = LocalContext.current
        val actions = remember(context) { AndroidAboutActions(context) }
        AboutScreen(
            version = AppVersion.of(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            actions = actions,
            onBack = { navController.popBackStackIfSafe() },
            onOpenDetectionHelp = { navController.navigate(DetectionHelpRoute) { launchSingleTop = true } },
            onOpenLicenses = { navController.navigate(LicensesRoute) { launchSingleTop = true } },
            // The SAME help as the link of Settings > Notifications (criterion AP5).
            onOpenRemindersHelp =
                if (showRemindersHelp) {
                    { navController.navigate(DetectionHelpRoute) { launchSingleTop = true } }
                } else {
                    null
                },
        )
    }
    composable<LicensesRoute> {
        val context = LocalContext.current
        val actions = remember(context) { AndroidAboutActions(context) }
        val source = remember(context) { AssetLicenseSource(context.applicationContext.assets) }
        LicensesScreen(
            source = source,
            onBack = { navController.popBackStackIfSafe() },
            onOpenEntry = { id -> navController.navigate(LicenseDetailRoute(id)) { launchSingleTop = true } },
            onOpenPrivacyPolicy = actions::openPrivacyPolicy,
        )
    }
    composable<LicenseDetailRoute> { entry ->
        val context = LocalContext.current
        val source = remember(context) { AssetLicenseSource(context.applicationContext.assets) }
        LicenseDetailScreen(
            entryId = entry.toRoute<LicenseDetailRoute>().id,
            source = source,
            onBack = { navController.popBackStackIfSafe() },
        )
    }
}
