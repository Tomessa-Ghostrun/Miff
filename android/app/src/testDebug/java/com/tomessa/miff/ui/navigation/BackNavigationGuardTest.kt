package com.tomessa.miff.ui.navigation

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Two quick taps on "back" must never empty the stack (white screen), and two quick taps on "Mot de passe oublié ?" must
 * open ONE screen. Real NavHost on the JVM (Robolectric), no phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BackNavigationGuardTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var nav: NavHostController

    @Composable
    private fun Host() {
        nav = rememberNavController()
        NavHost(navController = nav, startDestination = SignInRoute) {
            composable<SignInRoute> { Text("sign-in") }
            composable<PasswordResetRoute> { Text("reset") }
        }
    }

    @Test
    fun twoQuickTapsOnTheLink_openOneScreen() {
        composeRule.setContent { Host() }
        composeRule.runOnIdle {
            nav.navigate(PasswordResetRoute("a@b.fr")) { launchSingleTop = true }
            nav.navigate(PasswordResetRoute("a@b.fr")) { launchSingleTop = true }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(
                "sign-in + one reset screen",
                2,
                nav.currentBackStack.value.count {
                    it.destination.route !=
                        null
                },
            )
        }
    }

    @Test
    fun twoQuickBackTaps_popTheResetScreenOnly_neverTheSignInScreen() {
        composeRule.setContent { Host() }
        composeRule.runOnIdle { nav.navigate(PasswordResetRoute()) { launchSingleTop = true } }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            val first = nav.popBackStackIfSafe()
            val second = nav.popBackStackIfSafe() // same instant: the first pop is not finished
            assertTrue(first)
            assertFalse("the sign-in screen stays", second)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertNotNull("the stack is not empty", nav.currentBackStackEntry)
            assertFalse("and nothing is left to pop on the sign-in screen", nav.popBackStackIfSafe())
        }
    }
}
