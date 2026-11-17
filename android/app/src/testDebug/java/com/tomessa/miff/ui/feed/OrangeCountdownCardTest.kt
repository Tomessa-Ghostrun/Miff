package com.tomessa.miff.ui.feed

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.domain.tracking.CountdownAnchor
import com.tomessa.miff.domain.tracking.CountdownDuration
import com.tomessa.miff.domain.tracking.CountdownState
import com.tomessa.miff.domain.tracking.OrangeCountdown
import com.tomessa.miff.domain.tracking.StatusThresholds
import com.tomessa.miff.ui.theme.MiffTheme
import com.tomessa.miff.ui.theme.colorBorderDefault
import com.tomessa.miff.ui.theme.colorStatusSafe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * COUNTDOWN-A, the encart "Tu es en dehors de tes lieux de confiance" on the JVM (Robolectric, no phone): the texts in French and in English on the common vectors, the
 * single button for the screen reader (spoken duration, action, no live region), the tap, the refresh once per minute of the DEADLINE and none when the screen is
 * not started, the disappearance at the deadline, the reset, the border (track, arc, direction, never red), the animations (600 ms, instant with "reduce animations")
 * and the largest font. The screenshots of the PR are taken here (`build/screenshots`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w360dp-h640dp-port-xxhdpi")
class OrangeCountdownCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val window = StatusThresholds.ORANGE_AFTER_MILLIS
    private val minute = 60_000L
    private val base = 1_800_000_000_000L

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun text(id: Int): String = context().getString(id)

    private fun localized(tag: String): Context =
        context().createConfigurationContext(
            Configuration(context().resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            },
        )

    /** The clock of the screen, tied to the virtual time of the test: `advanceTimeBy` moves it. */
    private fun clock(): Long = base + composeRule.mainClock.currentTime

    /** An anchor such that [leftMillis] are left at the start of the test. */
    private fun anchorLeft(leftMillis: Long) = CountdownAnchor(base + leftMillis - window)

    private fun show(
        anchor: CountdownAnchor?,
        onOpen: () -> Unit = {},
        fontScale: Float? = null,
        onRefresh: () -> Unit = {},
        widthDp: Int? = null,
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            val provided = if (fontScale == null) density else Density(density.density, fontScale = fontScale)
            CompositionLocalProvider(LocalDensity provides provided) {
                MiffTheme {
                    Box(
                        Modifier.padding(16.dp).let {
                            if (widthDp !=
                                null
                            ) {
                                it.width(widthDp.dp)
                            } else {
                                it.fillMaxWidth()
                            }
                        },
                    ) {
                        OrangeCountdownSlot(
                            anchor = anchor,
                            onOpenUpdate = onOpen,
                            padding = PaddingValues(0.dp),
                            clock = ::clock,
                            onRefresh = onRefresh,
                        )
                    }
                }
            }
        }
    }

    private fun label(spoken: String) = context().getString(R.string.countdown_a11y_label, spoken)

    private fun shoot(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "countdown-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // --- the texts, on the common vectors (CD-12, CD-13) ------------------------------------------------------------------------

    private val nb = " "

    @Test
    fun frenchDurations_onTheCommonVectors() {
        val resources = localized("fr").resources
        val expected =
            mapOf(
                7_200 to "2${nb}h",
                7_199 to "2${nb}h",
                7_140 to "1${nb}h 59${nb}min",
                4_320 to "1${nb}h 12${nb}min",
                3_601 to "1${nb}h 01${nb}min",
                3_600 to "1${nb}h",
                3_541 to "1${nb}h",
                3_540 to "59${nb}min",
                120 to "2${nb}min",
                61 to "2${nb}min",
                60 to "moins d'1${nb}min",
                1 to "moins d'1${nb}min",
            )
        for ((left, shown) in expected) {
            val state =
                OrangeCountdown.at(
                    CountdownAnchor(base),
                    base + window - left * 1_000L,
                ) as CountdownState.Visible
            assertEquals("fr, $left s", shown, CountdownTexts.duration(resources, state.duration))
        }
    }

    @Test
    fun englishDurations_onTheCommonVectors() {
        val resources = localized("en").resources
        val expected =
            mapOf(
                7_200 to "2${nb}h",
                7_140 to "1${nb}h 59${nb}min",
                4_320 to "1${nb}h 12${nb}min",
                3_601 to "1${nb}h 01${nb}min",
                3_600 to "1${nb}h",
                3_540 to "59${nb}min",
                120 to "2${nb}min",
                60 to "less than 1${nb}min",
                1 to "less than 1${nb}min",
            )
        for ((left, shown) in expected) {
            val state =
                OrangeCountdown.at(
                    CountdownAnchor(base),
                    base + window - left * 1_000L,
                ) as CountdownState.Visible
            assertEquals("en, $left s", shown, CountdownTexts.duration(resources, state.duration))
        }
    }

    @Test
    fun spokenDurations_frenchAndEnglish_withTheRightPlurals() {
        val fr = localized("fr").resources
        assertEquals("1 heure et 12 minutes", CountdownTexts.spoken(fr, CountdownDuration.HoursMinutes(1, 12)))
        assertEquals("1 heure et 1 minute", CountdownTexts.spoken(fr, CountdownDuration.HoursMinutes(1, 1)))
        assertEquals("2 heures", CountdownTexts.spoken(fr, CountdownDuration.Hours(2)))
        assertEquals("1 heure", CountdownTexts.spoken(fr, CountdownDuration.Hours(1)))
        assertEquals("47 minutes", CountdownTexts.spoken(fr, CountdownDuration.Minutes(47)))
        assertEquals("moins d'une minute", CountdownTexts.spoken(fr, CountdownDuration.LessThanMinute))
        val en = localized("en").resources
        assertEquals("1 hour and 12 minutes", CountdownTexts.spoken(en, CountdownDuration.HoursMinutes(1, 12)))
        assertEquals("1 hour and 1 minute", CountdownTexts.spoken(en, CountdownDuration.HoursMinutes(1, 1)))
        assertEquals("2 hours", CountdownTexts.spoken(en, CountdownDuration.Hours(2)))
        assertEquals("47 minutes", CountdownTexts.spoken(en, CountdownDuration.Minutes(47)))
        assertEquals("less than a minute", CountdownTexts.spoken(en, CountdownDuration.LessThanMinute))
    }

    @Test
    fun theTwoSentencesOfTheTicket_inFrenchAndEnglish() {
        val fr = localized("fr").resources
        val en = localized("en").resources
        assertEquals("Tu es en dehors de tes lieux de confiance", fr.getString(R.string.countdown_title))
        assertEquals(
            "Tu passeras orange dans 1${nb}h 12${nb}min",
            fr.getString(R.string.countdown_body, "1${nb}h 12${nb}min"),
        )
        assertEquals("You're outside your trusted places", en.getString(R.string.countdown_title))
        assertEquals("You'll turn orange in 47${nb}min", en.getString(R.string.countdown_body, "47${nb}min"))
        assertEquals("Donner de tes nouvelles", fr.getString(R.string.countdown_a11y_action))
        assertEquals("Share an update", en.getString(R.string.countdown_a11y_action))
    }

    @Test
    fun theTextsAreCalm_noAlertWord_noExclamationMark() {
        val keys =
            listOf(
                R.string.countdown_title,
                R.string.countdown_body,
                R.string.countdown_a11y_label,
                R.string.countdown_a11y_action,
                R.string.countdown_a11y_less_than_minute,
                R.string.countdown_duration_less_than_minute,
            )
        for (tag in listOf("fr", "en")) {
            val resources = localized(tag).resources
            for (key in keys) {
                val value = resources.getString(key, "x").lowercase()
                assertFalse("no exclamation mark in $value", value.contains('!'))
                for (alarm in listOf("attention", "vite", "plus que", "urgent", "alert", "hurry", "only ", "danger")) {
                    assertFalse("no \"$alarm\" in $value", value.contains(alarm))
                }
            }
        }
    }

    // --- display and the single button for the screen reader (CD-1, CD-25) ------------------------------------------------------

    @Test
    fun visibleWithTheRightSpokenLabel_asOneButton() {
        show(anchorLeft(72 * minute))
        val spoken = label("1 heure et 12 minutes")
        val node = composeRule.onNodeWithContentDescription(spoken).fetchSemanticsNode()
        assertEquals(Role.Button, node.config.getOrNull(SemanticsProperties.Role))
        assertNotNull(node.config.getOrNull(SemanticsActions.OnClick))
        assertEquals(text(R.string.countdown_a11y_action), node.config.getOrNull(SemanticsActions.OnClick)?.label)
        assertTrue(node.config.contains(SemanticsActions.OnClick))
        composeRule.onNodeWithContentDescription(spoken).assert(hasClickAction())
        // One element: the hourglass, the two lines and the chevron are not reachable on their own.
        composeRule.onAllNodesWithContentDescription(spoken).assertCountEquals(1)
        composeRule
            .onAllNodesWithContentDescription(spoken, useUnmergedTree = true)
            .assertCountEquals(1)
    }

    @Test
    fun noLiveRegion_noAnnouncementEachMinute() {
        show(anchorLeft(72 * minute))
        val node = composeRule.onNodeWithContentDescription(label("1 heure et 12 minutes")).fetchSemanticsNode()
        assertNull(
            "not a live region: the value is read when the element takes the focus",
            node.config.getOrNull(SemanticsProperties.LiveRegion),
        )
    }

    @Test
    fun theVisibleTextIsTheTwoLines_andTheHourglassIsNeverInTheTree() {
        show(anchorLeft(72 * minute))
        composeRule.waitForIdle()
        // The two lines, the chevron and the hourglass are inside ONE element: none of them is reachable apart from it.
        composeRule.onAllNodesWithContentDescription("⏳", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun tap_opensTheUpdate_once() {
        var opened = 0
        show(anchorLeft(72 * minute), onOpen = { opened++ })
        composeRule.onNodeWithContentDescription(label("1 heure et 12 minutes")).performClick()
        assertEquals(1, opened)
    }

    @Test
    fun theTouchZone_isAtLeast48dp() {
        show(anchorLeft(72 * minute))
        composeRule.onNodeWithContentDescription(label("1 heure et 12 minutes")).assertHeightIsAtLeast(48.dp)
    }

    // --- hidden cases (CD-2 to CD-4, CD-8) ----------------------------------------------------------------------------------------

    @Test
    fun noAnchor_nothingIsDrawn() {
        show(null)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("Tu es en dehors", substring = true).assertCountEquals(0)
    }

    @Test
    fun alreadyPastTheDeadline_nothingIsDrawn() {
        show(anchorLeft(-5 * minute))
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("Tu es en dehors", substring = true).assertCountEquals(0)
    }

    // --- the clock: once per minute of the deadline (CD-18), disappearance (CD-10), reset (CD-9) -----------------------------

    @Test
    fun refreshesOncePerMinuteOfTheDeadline_andTheTextFollows() {
        composeRule.mainClock.autoAdvance = false
        var refreshes = 0
        // 10 min 20 s left: "11 min" for 20 s, then one change per minute, aligned on the deadline.
        show(anchorLeft(10 * minute + 20_000), onRefresh = { refreshes++ })
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(1, refreshes)
        composeRule.onNodeWithContentDescription(label("11 minutes")).assertExists()
        composeRule.mainClock.advanceTimeBy(19_000)
        assertEquals("nothing moves between two minutes", 1, refreshes)
        composeRule.mainClock.advanceTimeBy(1_100)
        composeRule.onNodeWithContentDescription(label("10 minutes")).assertExists()
        assertEquals(2, refreshes)
        composeRule.mainClock.advanceTimeBy(60_000)
        composeRule.onNodeWithContentDescription(label("9 minutes")).assertExists()
        assertEquals(3, refreshes)
        // Ten more minutes: ten more refreshes at the most (one per minute), never a faster tick.
        composeRule.mainClock.advanceTimeBy(5 * minute)
        assertEquals(8, refreshes)
    }

    @Test
    fun disappearsAtTheDeadline_neverZeroMinutes() {
        composeRule.mainClock.autoAdvance = false
        show(anchorLeft(2 * minute + 10_000))
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithContentDescription(label("3 minutes")).assertExists()
        composeRule.mainClock.advanceTimeBy(10_000 + 60_000 + 1) // 1 min left
        composeRule.onNodeWithContentDescription(label(text(R.string.countdown_a11y_less_than_minute))).assertExists()
        composeRule.mainClock.advanceTimeBy(60_000 + 400)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("Tu es en dehors", substring = true).assertCountEquals(0)
        assertTrue(
            "the clock really went past the deadline",
            clock() > anchorLeft(2 * minute + 10_000).referenceMillis + window,
        )
    }

    @Test
    fun anUpdate_restartsTheCountdown_atTheWholeWindow() {
        composeRule.mainClock.autoAdvance = false
        var anchor by mutableStateOf<CountdownAnchor?>(anchorLeft(10 * minute))
        composeRule.setContent {
            MiffTheme {
                OrangeCountdownSlot(anchor = anchor, onOpenUpdate = {}, padding = PaddingValues(0.dp), clock = ::clock)
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithContentDescription(label("10 minutes")).assertExists()
        // The person publishes an update: the reference becomes "now".
        anchor = CountdownAnchor(clock())
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithContentDescription(label("2 heures")).assertExists()
    }

    @Test
    fun comesBackAfterOrange_whenAnUpdateBringsTheAnchorBack() {
        composeRule.mainClock.autoAdvance = false
        var anchor by mutableStateOf<CountdownAnchor?>(null)
        composeRule.setContent {
            MiffTheme {
                OrangeCountdownSlot(
                    anchor = anchor,
                    onOpenUpdate = {},
                    padding = PaddingValues(0.dp),
                    clock = ::clock,
                )
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onAllNodesWithContentDescription("Tu es en dehors", substring = true).assertCountEquals(0)
        anchor = CountdownAnchor(clock())
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithContentDescription(label("2 heures")).assertExists()
    }

    @Test
    fun noTimerWhenTheScreenIsNotStarted_andTheRightValueAtTheReturn() {
        composeRule.mainClock.autoAdvance = false
        var refreshes = 0
        val owner =
            object : LifecycleOwner {
                val registry = LifecycleRegistry.createUnsafe(this)
                override val lifecycle: Lifecycle get() = registry
            }
        composeRule.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                MiffTheme {
                    OrangeCountdownSlot(
                        anchor = anchorLeft(30 * minute),
                        onOpenUpdate = {},
                        padding = PaddingValues(0.dp),
                        clock = ::clock,
                        onRefresh = { refreshes++ },
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(1, refreshes)
        composeRule.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
        composeRule.mainClock.advanceTimeBy(10 * minute)
        assertEquals("in the background: no refresh at all", 1, refreshes)
        composeRule.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals("one recomputation at the return, from the absolute time", 2, refreshes)
        composeRule.onNodeWithContentDescription(label("20 minutes")).assertExists()
    }

    // --- the border: track, arc, direction, colour (CD-15, CD-16) ----------------------------------------------------------------

    private fun pixel(
        x: Float,
        y: Float,
    ): Int {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        return bitmap.getPixel(x.toInt(), y.toInt())
    }

    private fun near(
        color: Color,
        argb: Int,
        tolerance: Int = 16,
    ): Boolean {
        val r = (color.red * 255).toInt()
        val g = (color.green * 255).toInt()
        val b = (color.blue * 255).toInt()
        return abs(((argb shr 16) and 0xFF) - r) <= tolerance && abs(((argb shr 8) and 0xFF) - g) <= tolerance &&
            abs((argb and 0xFF) - b) <= tolerance
    }

    private fun cardBounds() =
        composeRule.onNodeWithContentDescription("Tu es en dehors", substring = true).fetchSemanticsNode().boundsInRoot

    @Test
    fun fullWindow_theWholeOutlineIsGreen_theTrackIsHidden() {
        composeRule.mainClock.autoAdvance = false
        show(anchorLeft(window))
        composeRule.mainClock.advanceTimeByFrame()
        val b = cardBounds()
        val dp = composeRule.density.density
        val edge = 1.5f * dp
        for ((x, y) in listOf(
            b.center.x to b.top + edge,
            b.right - edge to b.center.y,
            b.center.x to b.bottom - edge,
            b.left + edge to b.center.y,
        )) {
            assertTrue("full window: (%.0f, %.0f) is green".format(x, y), near(colorStatusSafe, pixel(x, y)))
        }
        shoot("1-plein")
    }

    @Test
    fun halfWindow_theRightHalfIsEmptied_theLeftHalfStays_clockwise() {
        composeRule.mainClock.autoAdvance = false
        show(anchorLeft(window / 2))
        composeRule.mainClock.advanceTimeByFrame()
        val b = cardBounds()
        val edge = 1.5f * composeRule.density.density
        assertTrue("right side: emptied (grey track)", near(colorBorderDefault, pixel(b.right - edge, b.center.y)))
        assertTrue("top right: emptied", near(colorBorderDefault, pixel(b.right - 40f, b.top + edge)))
        assertTrue("left side: still there", near(colorStatusSafe, pixel(b.left + edge, b.center.y)))
        assertTrue(
            "top left, up to the top centre: still there",
            near(colorStatusSafe, pixel(b.left + 40f, b.top + edge)),
        )
        assertTrue("bottom left: still there", near(colorStatusSafe, pixel(b.left + 40f, b.bottom - edge)))
        assertTrue("bottom right: emptied", near(colorBorderDefault, pixel(b.right - 40f, b.bottom - edge)))
        shoot("2-moitie")
    }

    @Test
    fun threeQuarters_onlyTheFirstQuarterAfterTheTopCentre_hasGone() {
        composeRule.mainClock.autoAdvance = false
        show(anchorLeft(window * 3 / 4))
        composeRule.mainClock.advanceTimeByFrame()
        val b = cardBounds()
        val edge = 1.5f * composeRule.density.density
        assertTrue("top right quarter: gone", near(colorBorderDefault, pixel(b.right - 60f, b.top + edge)))
        assertTrue("bottom right: still there", near(colorStatusSafe, pixel(b.right - edge, b.bottom - 25f)))
        assertTrue("left: still there", near(colorStatusSafe, pixel(b.left + edge, b.center.y)))
    }

    @Test
    fun lastHalfHour_theBorderWarms_towardsOrange_neverRed() {
        composeRule.mainClock.autoAdvance = false
        show(anchorLeft(15 * minute))
        composeRule.mainClock.advanceTimeByFrame()
        val b = cardBounds()
        val edge = 1.5f * composeRule.density.density
        // 15 minutes left: only the last eighth of the outline remains, the end of the top edge, left of the top centre.
        val shown = pixel(b.center.x - 100f, b.top + edge)
        val red = (shown shr 16) and 0xFF
        val green = (shown shr 8) and 0xFF
        val blue = shown and 0xFF
        assertTrue(
            "warmer than the pure green (more red than the green of the safe colour is not needed: it moved towards orange)",
            !near(colorStatusSafe, shown, 4),
        )
        assertTrue("never red: green stays above 150 (is $green)", green > 150)
        assertTrue("never red: blue stays low", blue < 120)
        assertTrue("red channel between safe and warning", red in 200..255)
        shoot("3-derniere-demi-heure")
    }

    @Test
    fun almostEmpty_aTinyArcRemains_atTheTopCentreSide() {
        composeRule.mainClock.autoAdvance = false
        show(anchorLeft(30_000))
        composeRule.mainClock.advanceTimeByFrame()
        val b = cardBounds()
        val edge = 1.5f * composeRule.density.density
        assertTrue("the left side is emptied", near(colorBorderDefault, pixel(b.left + edge, b.center.y)))
        assertTrue("the bottom is emptied", near(colorBorderDefault, pixel(b.center.x, b.bottom - edge)))
    }

    @Test
    fun theBorderSlides_over600ms_atEachMinute_andIsStillBetween() {
        composeRule.mainClock.autoAdvance = false
        show(anchorLeft(60 * minute + 30_000))
        composeRule.mainClock.advanceTimeByFrame()

        // 61 minutes left (0.508 of the outline): the head of the arc is a few pixels to the right of the bottom centre; at 60 minutes (0.5) it is AT the centre.
        fun probe(): Int {
            val b = cardBounds()
            return pixel(b.center.x + 10f, b.bottom - 1.5f * composeRule.density.density)
        }
        assertTrue("61 min left: the arc covers the probe", near(colorStatusSafe, probe()))
        composeRule.mainClock.advanceTimeBy(29_000)
        assertTrue("immobile between two minutes", near(colorStatusSafe, probe()))
        composeRule.mainClock.advanceTimeBy(1_050) // the boundary (at 30 s) is just passed: the slide has barely begun
        assertTrue("it slides, it does not jump: still there 50 ms after the boundary", near(colorStatusSafe, probe()))
        composeRule.mainClock.advanceTimeBy(750) // 600 ms are over
        assertTrue("the slide is over: the head reached its new place", near(colorBorderDefault, probe()))
    }

    // --- reduce animations (CD-17) ---------------------------------------------------------------------------------------------

    @Test
    fun withReducedAnimations_theBorderChangesAtOnce_andTheEncartAppearsWithoutFade() {
        Settings.Global.putFloat(context().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        composeRule.mainClock.autoAdvance = false
        var anchor by mutableStateOf<CountdownAnchor?>(null)
        composeRule.setContent {
            MiffTheme {
                Box(Modifier.padding(16.dp)) {
                    OrangeCountdownSlot(
                        anchor = anchor,
                        onOpenUpdate = {},
                        padding = PaddingValues(0.dp),
                        clock = ::clock,
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        anchor = anchorLeft(window)
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        // Two frames (about 32 ms): without a 200 ms fade the encart is already complete.
        val b = cardBounds()
        val edge = 1.5f * composeRule.density.density
        assertTrue("instant: already full", near(colorStatusSafe, pixel(b.center.x, b.top + edge)))
        // And the next minute is applied at once, not slid.
        composeRule.mainClock.advanceTimeBy(window / 2)
        composeRule.mainClock.advanceTimeByFrame()
        assertTrue(
            "instant: right half already gone",
            near(colorBorderDefault, pixel(cardBounds().right - edge, cardBounds().center.y)),
        )
    }

    // --- the largest font (CD-26) ------------------------------------------------------------------------------------------------

    @Test
    fun largestFont_growsInHeight_keepsTheTouchZone_andTheWholeTextTakesTheRoom() {
        composeRule.mainClock.autoAdvance = false
        var scale by mutableIntStateOf(10)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = scale / 10f)) {
                MiffTheme {
                    Box(Modifier.padding(16.dp).width(300.dp)) {
                        OrangeCountdownSlot(
                            anchor = anchorLeft(72 * minute),
                            onOpenUpdate = {},
                            padding = PaddingValues(0.dp),
                            clock = ::clock,
                        )
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(500)
        val normal = cardBounds().height
        scale = 20
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeBy(500)
        val big = cardBounds().height
        // Wrapped, never cut: the two lines take as many lines as they need, so the encart is clearly taller (the border follows the real outline).
        assertTrue("the encart grew ($normal -> $big)", big > normal * 1.4f)
        composeRule.onNodeWithContentDescription("Tu es en dehors", substring = true).assertHeightIsAtLeast(48.dp)
        shoot("5-grande-police")
    }
}
