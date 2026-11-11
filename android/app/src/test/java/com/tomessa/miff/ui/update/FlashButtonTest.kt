package com.tomessa.miff.ui.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The flash button: announced as "Flash" with its state in words (not by colour alone), 48 dp to touch, one tap = one toggle. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en", application = android.app.Application::class)
class FlashButtonTest {
    @get:Rule
    val rule = createComposeRule()

    private fun state(text: String) =
        SemanticsMatcher(
            "stateDescription = $text",
        ) { it.config.getOrNull(SemanticsProperties.StateDescription) == text }

    @Test
    fun off_saysFlashOff_andTheTouchAreaIs48dp() {
        rule.setContent { FlashButton(on = false, onClick = {}) }
        rule
            .onNodeWithContentDescription("Flash")
            .assert(state("Flash off"))
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun on_saysFlashOn() {
        rule.setContent { FlashButton(on = true, onClick = {}) }
        rule.onNodeWithContentDescription("Flash").assert(state("Flash on"))
    }

    @Test
    fun aTap_togglesTheFlashOnceAndTheStateFollows() {
        var flash by mutableStateOf(FlashState().withCamera(true))
        var taps = 0
        rule.setContent {
            FlashButton(on = flash.on, onClick = {
                taps++
                flash = flash.toggled()
            })
        }
        rule.onNodeWithContentDescription("Flash").performClick()
        rule.waitForIdle()
        assertEquals(1, taps)
        rule.onNodeWithContentDescription("Flash").assert(state("Flash on"))
        rule.onNodeWithContentDescription("Flash").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Flash").assert(state("Flash off"))
    }

    @Test
    fun inFrench_theStateIsSpokenInFrench() {
        // The button reads its texts from the resources of the device language: checked on the French resources too.
        val fr =
            androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().let { ctx ->
                val config =
                    android.content.res
                        .Configuration(
                            ctx.resources.configuration,
                        ).apply { setLocale(java.util.Locale.FRENCH) }
                ctx.createConfigurationContext(config)
            }
        assertEquals("Flash activé", fr.getString(com.tomessa.miff.R.string.update_flash_state_on))
        assertEquals("Flash désactivé", fr.getString(com.tomessa.miff.R.string.update_flash_state_off))
    }
}
