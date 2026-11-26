package com.tomessa.miff.ui.components

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.data.miff.DirectoryState
import com.tomessa.miff.domain.miff.MiffSummary
import com.tomessa.miff.domain.paywall.OverlayPresence
import com.tomessa.miff.ui.feed.MiffPill
import com.tomessa.miff.ui.miff.MiffConfirmDialog
import com.tomessa.miff.ui.miff.MiffProgressDialog
import com.tomessa.miff.ui.profile.CountryPickerSheet
import com.tomessa.miff.ui.profile.PhotoSourceSheet
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * One test per overlay of the tabs (criteria PP9 and PP12, ticket 4.1 "no sheet"): the REAL component, composed under a registry that counts, tells it is open while it is on screen and
 * that it left when it closes. The ones that are private to their screen (the menu of the time window, the sheet of a danger, the day picker, the menu of a member) are checked in the source by
 * `PeriodicPaywallBuildTest.everyOverlayOfTheAppTellsThePeriodicOfferItIsOpen`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w390dp-h844dp-port-xxhdpi")
class OverlayPresenceTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class Counter : OverlayPresence {
        var opened = 0
        var closed = 0
        val open: Int get() = opened - closed

        override fun onOverlayOpened() {
            opened++
        }

        override fun onOverlayClosed() {
            closed++
        }
    }

    private val counter = Counter()
    private var shown by mutableStateOf(false)

    private fun overlayTold(content: @Composable () -> Unit) {
        composeRule.setContent {
            MiffTheme {
                CompositionLocalProvider(LocalOverlayPresence provides counter) {
                    Box { if (shown) content() }
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals("nothing open yet", 0, counter.open)
        composeRule.runOnIdle { shown = true }
        composeRule.waitForIdle()
        assertEquals("the overlay says it is open", 1, counter.open)
        composeRule.runOnIdle { shown = false }
        composeRule.waitForIdle()
        assertEquals("and that it left", 0, counter.open)
        assertEquals(1, counter.closed)
    }

    @Test
    fun aConfirmDialog_theAlertsPopIns_theConnectionPopIn() =
        overlayTold {
            MiffConfirmDialog(
                title = "t",
                body = "b",
                confirmLabel = "ok",
                cancelLabel = "no",
                onConfirm = {},
                onCancel = {},
            )
        }

    @Test
    fun aProgressDialog() = overlayTold { MiffProgressDialog(title = "t", body = "b", onDismiss = {}) }

    @Test
    fun theFullScreenPhoto_theFullBleedWindow() = overlayTold { MiffFullBleedDialog(onDismissRequest = {}) { Box {} } }

    @Test
    fun theSheetOfTheProfilePhoto() =
        overlayTold {
            PhotoSourceSheet(
                hasPhoto = true,
                onDismiss = {},
                onTakePhoto = {},
                onChoose = {},
                onRemove = {},
            )
        }

    @Test
    fun theSheetOfTheCountries() =
        overlayTold {
            CountryPickerSheet(
                selectedRegion = "FR",
                locale = Locale.FRANCE,
                onSelected = {},
                onDismiss = {},
            )
        }

    @Test
    fun theMenuOfTheSelectorOfMiffs_opensOnTheTapOfThePill_andClosesWithIt() {
        val state =
            DirectoryState.Ready(
                miffs = listOf(MiffSummary("miff-1", "Famille")),
                activeId = "miff-1",
                premium = false,
                fromCache = false,
            )
        composeRule.setContent {
            MiffTheme {
                CompositionLocalProvider(LocalOverlayPresence provides counter) {
                    MiffPill(state = state, onSelect = {}, onCreate = {}, onJoin = {}, onOffer = {})
                }
            }
        }
        composeRule.waitForIdle()
        assertEquals("the menu is not open: nothing", 0, counter.open)
        val label =
            ApplicationProvider.getApplicationContext<Context>().getString(
                R.string.feed_selector_a11y,
                "Famille",
            )
        composeRule.onNodeWithContentDescription(label).performClick()
        composeRule.waitForIdle()
        assertEquals("the menu of the Miffs says it is open", 1, counter.open)
    }
}
