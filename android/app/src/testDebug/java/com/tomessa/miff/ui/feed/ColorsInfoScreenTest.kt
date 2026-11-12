package com.tomessa.miff.ui.feed

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.domain.feed.ColorsInfoDurations
import com.tomessa.miff.domain.feed.WholeDuration
import com.tomessa.miff.domain.tracking.StatusThresholds
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

/**
 * COULEURS-1, the page "Les couleurs de ta Miff" on the JVM (Robolectric, no phone): its blocks in order, the exact texts in French and in English, the
 * durations read from the thresholds and written by the formatter of the system (C2), the headings and the lines read as one block (A1), no button but the
 * way back, the largest font (A5), and the texts themselves (no hour count written in a catalogue, parity of the keys, calm tone: C5, C6, T1).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w360dp-h640dp-port-xxhdpi")
class ColorsInfoScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun text(id: Int): String = context().getString(id)

    private fun text(
        id: Int,
        vararg args: Any,
    ): String = context().getString(id, *args)

    private fun show(
        onBack: () -> Unit = {},
        durations: ColorsInfoDurations = ColorsInfoDurations.FromThresholds,
        fontScale: Float? = null,
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            val provided = if (fontScale == null) density else Density(density.density, fontScale = fontScale)
            CompositionLocalProvider(LocalDensity provides provided) {
                MiffTheme { Box(Modifier.fillMaxSize()) { ColorsInfoScreen(onBack = onBack, durations = durations) } }
            }
        }
    }

    private fun shoot(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // --- the durations (C2, STA9) -----------------------------------------------------------------------------------------------

    @Test
    fun theDurations_areTheThresholdsOfTheFeed() {
        val d = ColorsInfoDurations.FromThresholds
        assertEquals(StatusThresholds.ORANGE_AFTER_MILLIS, d.orangeAfterMillis)
        assertEquals(StatusThresholds.RED_AFTER_MILLIS, d.redAfterMillis)
        assertEquals(StatusThresholds.DANGER_ZONE_RED_AFTER_MILLIS, d.dangerGreenMillis)
        assertEquals(WholeDuration(2, WholeDuration.Unit.HOUR), WholeDuration.of(d.orangeAfterMillis))
        assertEquals(WholeDuration(4, WholeDuration.Unit.HOUR), WholeDuration.of(d.redAfterMillis))
        assertEquals(WholeDuration(1, WholeDuration.Unit.HOUR), WholeDuration.of(d.dangerGreenMillis))
    }

    @Test
    fun aDurationThatIsNotWholeHours_isSaidInMinutes_neverRoundedToWrongHours() {
        assertEquals(WholeDuration(90, WholeDuration.Unit.MINUTE), WholeDuration.of(90 * 60_000L))
        assertEquals(WholeDuration(1, WholeDuration.Unit.MINUTE), WholeDuration.of(1_000L))
    }

    private fun fr(hours: Long): String = formatDuration(hours * 3_600_000L, Locale.FRENCH)

    private fun en(hours: Long): String = formatDuration(hours * 3_600_000L, Locale.US)

    private fun normalized(s: String) = s.replace(' ', ' ').replace(' ', ' ')

    @Test
    fun theFormatterOfTheSystem_writesTheDuration_inTheLanguageOfThePhone() {
        assertEquals("2 heures", normalized(formatDuration(2 * 3_600_000L, Locale.FRENCH)))
        assertEquals("2 hours", normalized(formatDuration(2 * 3_600_000L, Locale.US)))
        assertEquals("1 heure", normalized(formatDuration(3_600_000L, Locale.FRENCH)))
        assertEquals("1 hour", normalized(formatDuration(3_600_000L, Locale.US)))
        assertEquals("90 minutes", normalized(formatDuration(90 * 60_000L, Locale.FRENCH)))
    }

    @Test
    fun theTexts_showTheThresholds_2h_4h_1h() {
        show()
        composeRule.onNodeWithText(text(R.string.feed_colors_person_green_body, fr(2))).assertExists()
        composeRule.onNodeWithText(text(R.string.feed_colors_person_orange_body, fr(2), fr(4))).assertExists()
        composeRule.onNodeWithText(text(R.string.feed_colors_person_red_body, fr(4))).assertExists()
        composeRule.onNodeWithText(text(R.string.feed_colors_danger_body, fr(1))).assertExists()
        composeRule.onNodeWithText(text(R.string.feed_colors_changes_time, fr(2), fr(4))).assertExists()
    }

    @Test
    fun changingAThreshold_changesTheTexts_noHourCountIsWrittenInTheScreen() {
        show(
            durations =
                ColorsInfoDurations(
                    orangeAfterMillis = 3 * 3_600_000L,
                    redAfterMillis = 6 * 3_600_000L,
                    dangerGreenMillis =
                        90 * 60_000L,
                ),
        )
        composeRule.onNodeWithText(text(R.string.feed_colors_person_orange_body, fr(3), fr(6))).assertExists()
        composeRule.onNodeWithText(text(R.string.feed_colors_person_red_body, fr(6))).assertExists()
        composeRule
            .onNodeWithText(
                text(R.string.feed_colors_danger_body, formatDuration(90 * 60_000L, Locale.FRENCH)),
            ).assertExists()
        composeRule.onNodeWithText(text(R.string.feed_colors_changes_time, fr(3), fr(6))).assertExists()
        composeRule.onAllNodesWithText(fr(2), substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText(fr(4), substring = true).assertCountEquals(0)
    }

    @Config(qualifiers = "en-rUS-w360dp-h640dp-port-xxhdpi")
    @Test
    fun inEnglish_theDurationsFollowTheLanguage() {
        show()
        composeRule.onNodeWithText(text(R.string.feed_colors_title)).assertIsDisplayed()
        composeRule.onNodeWithText("Your Miff's colors").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.feed_colors_person_orange_body, en(2), en(4))).assertExists()
        composeRule.onNodeWithText(text(R.string.feed_colors_danger_body, en(1))).assertExists()
    }

    // --- the blocks and their order (C1, A1) ------------------------------------------------------------------------------------

    private fun top(word: String): Float =
        composeRule
            .onNodeWithText(word)
            .fetchSemanticsNode()
            .positionInRoot.y

    @Test
    fun theFiveBlocks_areInTheOrderOfTheSpec() {
        show()
        shoot("colors-info-top")
        composeRule.onNodeWithText(text(R.string.feed_colors_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.feed_colors_intro)).assertIsDisplayed()
        val order =
            listOf(
                R.string.feed_colors_intro,
                R.string.feed_colors_person_title,
                R.string.feed_colors_person_intro,
                R.string.feed_colors_danger_title,
                R.string.feed_colors_changes_title,
                R.string.feed_colors_miff_title,
                R.string.feed_colors_miff_intro,
                R.string.feed_colors_miff_ignored,
                R.string.feed_colors_not_title,
                R.string.feed_colors_not_alert,
                R.string.feed_colors_not_location,
                R.string.feed_colors_not_verdict,
                R.string.feed_colors_not_delay,
            ).map { top(text(it)) }
        assertEquals("every block is below the previous one", order.sorted(), order)
        assertEquals(order.distinct(), order)
    }

    @Test
    fun theColorLines_areThereInOrder_withTheirName() {
        show()
        // Person: green, orange, red, grey, grey (travel); Miff: green, orange, red, grey. The names are in the lines, the colour is never alone (A3).
        val person =
            listOf(
                R.string.feed_colors_person_green_body to listOf(fr(2)),
                R.string.feed_colors_person_orange_body to listOf(fr(2), fr(4)),
                R.string.feed_colors_person_red_body to listOf(fr(4)),
                R.string.feed_colors_person_gray_body to emptyList(),
                R.string.feed_colors_person_travel_body to emptyList(),
            ).map { (id, args) -> top(text(id, *args.toTypedArray())) }
        assertEquals(person.sorted(), person)
        val miff =
            listOf(
                R.string.feed_colors_miff_green_body,
                R.string.feed_colors_miff_orange_body,
                R.string.feed_colors_miff_red_body,
                R.string.feed_colors_miff_gray_body,
            ).map { top(text(it)) }
        assertEquals(miff.sorted(), miff)
        assertTrue("the Miff's lines come after the person's", miff.first() > person.last())
    }

    @Test
    fun theTitleAndTheFourSections_areHeadings() {
        show()
        val headings =
            listOf(
                R.string.feed_colors_title,
                R.string.feed_colors_person_title,
                R.string.feed_colors_changes_title,
                R.string.feed_colors_miff_title,
                R.string.feed_colors_not_title,
            )
        headings.forEach { id ->
            val node = composeRule.onNodeWithText(text(id)).fetchSemanticsNode()
            assertTrue("${text(id)} is a heading", node.config.getOrNull(SemanticsProperties.Heading) != null)
        }
    }

    @Test
    fun eachColorLine_isReadAsOneBlock_nameThenSentence() {
        show()
        val body = text(R.string.feed_colors_person_green_body, fr(2))
        composeRule
            .onNodeWithContentDescription(
                "${text(R.string.feed_colors_person_green_title)}. $body",
            ).assertExists()
        composeRule
            .onNodeWithContentDescription(
                "${text(R.string.feed_colors_person_travel_title)}. ${text(R.string.feed_colors_person_travel_body)}",
            ).assertExists()
        composeRule
            .onNodeWithContentDescription(
                "${text(R.string.feed_colors_miff_red_title)}. ${text(R.string.feed_colors_miff_red_body)}",
            ).assertExists()
    }

    // --- nothing to press but the way back (D5, H1) -----------------------------------------------------------------------------

    @Test
    fun theOnlyActionIsTheWayBack_aTouchTargetOf48dp_namedRetour() {
        var back = 0
        show(onBack = { back++ })
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(1)
        composeRule
            .onNodeWithContentDescription(text(R.string.auth_back_a11y))
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, back)
    }

    // --- the largest font (A5) --------------------------------------------------------------------------------------------------

    @Test
    fun atTheLargestFont_everythingIsReachableByScrolling_andTheWayBackStays48dp() {
        show(fontScale = 2f)
        shoot("colors-info-large-font")
        composeRule
            .onAllNodesWithText(
                text(R.string.feed_colors_person_green_title),
            )[0]
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.feed_colors_not_delay)).performScrollTo().assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(text(R.string.auth_back_a11y))
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    private fun textStartsInsideTheRow(): Boolean {
        val title = text(R.string.feed_colors_miff_red_title)
        val row =
            composeRule
                .onNodeWithContentDescription("$title. ${text(R.string.feed_colors_miff_red_body)}")
                .fetchSemanticsNode()
                .positionInRoot
        val name = composeRule.onAllNodesWithText(title, useUnmergedTree = true)[1].fetchSemanticsNode().positionInRoot
        return name.x > row.x + 1f
    }

    @Test
    fun atTheNormalFont_thePictureIsBesideTheText() {
        show()
        assertTrue("the text starts after the picture", textStartsInsideTheRow())
    }

    @Test
    fun atTheLargestFont_thePictureMovesAboveTheText_whichKeepsTheFullWidth() {
        show(fontScale = 2f)
        assertFalse("the text starts at the start of the line", textStartsInsideTheRow())
    }

    // --- the texts themselves (C2, C5, C6, T1) ----------------------------------------------------------------------------------

    private val placeholderPattern = Regex("%\\d\\$" + "s")

    private fun catalogue(folder: String): Map<String, String> {
        val xml = File("src/main/res/$folder/strings.xml").readText()
        return Regex("""<string name="(feed_colors_[a-z0-9_]+)">([^<]*)</string>""")
            .findAll(xml)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    @Test
    fun bothLanguagesHaveTheSame39Keys_withTheSamePlaceholders() {
        val fr = catalogue("values-fr")
        val en = catalogue("values")
        assertEquals(39, fr.size)
        assertEquals(fr.keys, en.keys)
        val placeholders = placeholderPattern
        fr.forEach { (key, value) ->
            assertEquals(
                "$key: same placeholders in French and in English",
                placeholders.findAll(value).map { it.value }.toList(),
                placeholders.findAll(en.getValue(key)).map { it.value }.toList(),
            )
        }
    }

    @Test
    fun noCatalogue_writesAnHourCount_onlyPlaceholders() {
        listOf("values-fr", "values").forEach { folder ->
            catalogue(folder).forEach { (key, value) ->
                val withoutPlaceholders = value.replace(placeholderPattern, "")
                assertFalse("$folder/$key has a digit written in it", withoutPlaceholders.any { it.isDigit() })
                // "l'heure des dernières informations" is a normal word; what must never be written is a COUNT of hours ("heures").
                assertFalse("$folder/$key says \"heures\"", withoutPlaceholders.contains("heures", ignoreCase = true))
                assertFalse("$folder/$key says \"hour\"", withoutPlaceholders.contains("hour", ignoreCase = true))
            }
        }
    }

    @Test
    fun theTone_isCalm_noExclamation_noSafetyPromise_noAlarmWord() {
        listOf("values-fr", "values").forEach { folder ->
            catalogue(folder).forEach { (key, value) ->
                assertFalse("$folder/$key has an exclamation mark", value.contains('!'))
                assertFalse(
                    "$folder/$key says safe",
                    value.contains("sécurité", ignoreCase = true) || value.contains("safe", ignoreCase = true),
                )
                assertFalse("$folder/$key says safe", value.contains("en sûreté", ignoreCase = true))
                // "urgence / emergency" only in the sentence that says Miff is not an emergency alert; "danger" only in the proper name "zone de danger".
                if (key != "feed_colors_not_alert") {
                    assertFalse(
                        "$folder/$key",
                        value.contains("urgence", ignoreCase = true) || value.contains("emergency", ignoreCase = true),
                    )
                }
                if (key != "feed_colors_danger_title" && key != "feed_colors_danger_body") {
                    assertFalse("$folder/$key", value.contains("danger", ignoreCase = true))
                }
                assertFalse(
                    "$folder/$key shouts",
                    Regex("""\b[A-ZÉÈ]{4,}\b""").containsMatchIn(value.replace(placeholderPattern, "")),
                )
            }
        }
    }
}
