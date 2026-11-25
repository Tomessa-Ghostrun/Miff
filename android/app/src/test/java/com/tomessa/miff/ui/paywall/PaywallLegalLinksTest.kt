package com.tomessa.miff.ui.paywall

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * "Conditions d'utilisation" and "Politique de confidentialité" of the offer (decision of the PM, 2026-11-25) sit side by side on the SAME line, centred, in the six languages, and each
 * fits on ONE line on a 360 dp phone (the offer's own margins taken off); on a 320 dp phone they stay on the same line even if one wraps inside its own half. The texts are the real ones of
 * each language; text measured with the real fonts (native graphics).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "en-w360dp-h800dp-xhdpi", application = android.app.Application::class)
class PaywallLegalLinksTest {
    @get:Rule
    val rule = createComposeRule()

    private val density =
        ApplicationProvider
            .getApplicationContext<Context>()
            .resources.displayMetrics.density

    private val languages = listOf("fr", "en", "es", "it", "pt", "de")

    private fun textsOf(language: String): Pair<String, String> {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val configuration =
            Configuration(
                base.resources.configuration,
            ).apply { setLocale(Locale.forLanguageTag(language)) }
        val resources = base.createConfigurationContext(configuration).resources
        return resources.getString(R.string.paywall_terms_link) to resources.getString(R.string.paywall_privacy_link)
    }

    private fun check(
        widthDp: Int,
        mustFitOneLine: Boolean,
    ) {
        var texts by mutableStateOf(textsOf("fr"))
        rule.setContent {
            MiffTheme {
                Box(Modifier.width(widthDp.dp)) {
                    LegalLinksRow(terms = texts.first, privacy = texts.second, onTerms = {}, onPrivacy = {})
                }
            }
        }
        for (language in languages) {
            texts = textsOf(language)
            rule.waitForIdle()
            val terms = rule.onNodeWithText(texts.first, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val privacy = rule.onNodeWithText(texts.second, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            // Same row: the two links overlap vertically (their centres are less than a line apart) and the second is on the right of the first.
            assertTrue(
                "$language: same line (${terms.center.y} / ${privacy.center.y})",
                kotlin.math.abs(terms.center.y - privacy.center.y) < terms.height,
            )
            assertTrue("$language: side by side", privacy.left >= terms.right)
            val oneLine = 16f * density * 1.5f
            if (mustFitOneLine) {
                assertTrue(
                    "$language: \"${texts.first}\" on one line (height ${terms.height})",
                    terms.height <= oneLine,
                )
                assertTrue(
                    "$language: \"${texts.second}\" on one line (height ${privacy.height})",
                    privacy.height <= oneLine,
                )
            }
            // Centred: the pair is as far from the left edge as from the right one (to a few dp).
            val widthPx = widthDp * density
            assertEquals("$language: centred", terms.left, widthPx - privacy.right, 8f * density)
        }
    }

    @Test
    fun on360dp_eachLinkIsOnOneLine_inSixLanguages() = check(widthDp = 360 - 32, mustFitOneLine = true)

    @Test
    fun on320dp_theLinksStayOnTheSameLine_inSixLanguages() = check(widthDp = 320 - 32, mustFitOneLine = false)
}
