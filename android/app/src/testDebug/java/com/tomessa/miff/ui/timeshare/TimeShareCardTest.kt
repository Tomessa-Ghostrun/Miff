package com.tomessa.miff.ui.timeshare

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.domain.place.PlaceCategory
import com.tomessa.miff.domain.timeshare.SliceKind
import com.tomessa.miff.domain.timeshare.TimeShareTracking
import com.tomessa.miff.domain.timeshare.TrackingHealth
import com.tomessa.miff.ui.theme.MiffTheme
import com.tomessa.miff.ui.theme.colorBgCanvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.text.NumberFormat
import java.util.Locale

/**
 * The card "Répartition du temps" on the JVM (Robolectric, no phone; docs/tickets/profil-repartition-temps.md sections 5, 7 and 9, layout of the PM of 2026-11-17): the header, the legend
 * on the left with its four lines, the exact texts in French, the green band with the party emoji, the states without a chart (with the number of days left), the selector, the detail of
 * the danger zone, the way it is read by a screen reader and the largest font. The figures are those of the mockup (Maison 48, Sport 24, Zone de danger 12, Hors-zone 16).
 * Pictures: `build/screenshots/timeshare-*.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w390dp-h844dp-port-xxhdpi")
class TimeShareCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun text(id: Int): String = context().getString(id)

    private fun pct(value: Int): String = NumberFormat.getPercentInstance(Locale.FRENCH).format(value / 100.0)

    private fun item(
        kind: SliceKind,
        name: String?,
        category: PlaceCategory?,
        percent: Int,
        rank: Int = 0,
        count: Int = 0,
        details: List<DetailItem> = emptyList(),
        tiny: Boolean = false,
    ) = LegendItem(kind, name, category, count, percent, tiny, rank, details, percent / 100f)

    private val mockup =
        TimeShareBody.Chart(
            items =
                listOf(
                    item(SliceKind.PLACE, "Maison", PlaceCategory.HOME, 48, rank = 0),
                    item(SliceKind.PLACE, "Sport", PlaceCategory.SPORT, 24, rank = 1),
                    item(
                        SliceKind.DANGER,
                        null,
                        null,
                        12,
                        count = 2,
                        details =
                            listOf(
                                DetailItem("Le parc", null, 7, false),
                                DetailItem("Chantier rue Haute", PlaceCategory.OTHER, 5, false),
                            ),
                    ),
                    item(SliceKind.ELSEWHERE, null, null, 16),
                ),
            centerPercent = 72,
        )

    private fun shown(
        body: TimeShareBody = mockup,
        days: Int = 7,
        health: TrackingHealth = TrackingHealth.ACTIVE,
        overLimit: Boolean = false,
    ): TimeShareUiState = TimeShareUiState.Shown("miffA", days, body, TimeShareTracking(health, overLimit))

    private fun show(
        state: TimeShareUiState,
        actions: TimeShareActions = TimeShareActions(),
        fontScale: Float? = null,
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            val provided = if (fontScale == null) density else Density(density.density, fontScale = fontScale)
            CompositionLocalProvider(LocalDensity provides provided) { Frame { TimeShareCard(state, actions) } }
        }
    }

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        MiffTheme {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colorBgCanvas)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) { content() }
        }
    }

    // The legend and the ring are hidden from the screen reader (the sentence of the chart says it all): the tests look at the tree as drawn, not as merged.
    private fun node(text: String) = composeRule.onNodeWithText(text, useUnmergedTree = true)

    private fun nodes(text: String) = composeRule.onAllNodesWithText(text, useUnmergedTree = true)

    private fun shoot(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "timeshare-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // --- The nominal card ---------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun theNominalCard_hasTheHeader_theLegend_theCentre_andTheBand() {
        show(shown())
        node("Répartition du temps").assertIsDisplayed()
        nodes("Sur les 7 derniers jours").assertCountEquals(0)
        node("7 jours").assertIsDisplayed()
        node("Maison").assertIsDisplayed()
        node("Sport").assertIsDisplayed()
        node("Zones de danger").assertIsDisplayed()
        node("Hors-zone").assertIsDisplayed()
        // The centre: the share in the trusted places, in big, and "en zone de confiance" under it (the same figure is in the band).
        nodes(pct(72)).assertCountEquals(1)
        node("en zone de confiance").assertIsDisplayed()
        nodes("Ces données restent sur ton téléphone. Personne d'autre ne peut les voir.").assertCountEquals(0)
        shoot("nominal")
    }

    @Test
    fun theBand_saysTheShareInTheTrustedZones_withTheTick_andThePartyEmoji_andNeverTheDangerZone() {
        show(shown())
        val sentence = "Tu as passé ${pct(72)} de ton temps dans tes zones de confiance"
        node(sentence).assertIsDisplayed()
        node("🎉").assertExists()
        assertFalse(sentence.contains("danger", ignoreCase = true))
    }

    @Test
    fun theBand_isTheSame_whateverTheFigure() {
        show(shown(TimeShareBody.Chart(mockup.items, 3)))
        node("Tu as passé ${pct(3)} de ton temps dans tes zones de confiance").assertIsDisplayed()
        node("🎉").assertExists()
    }

    @Test
    fun theLegendHasTheFourBiggestShares_theRingKeepsAll_theDescriptionListsAll() {
        val six =
            TimeShareBody.Chart(
                items =
                    listOf(
                        item(SliceKind.PLACE, "Maison", PlaceCategory.HOME, 30, rank = 0),
                        item(SliceKind.PLACE, "Sport", PlaceCategory.SPORT, 20, rank = 1),
                        item(SliceKind.PLACE, "Bureau", PlaceCategory.WORK, 18, rank = 2),
                        item(SliceKind.PLACE, "Chez Léa", PlaceCategory.RELATIVE, 14, rank = 3),
                        item(SliceKind.PLACE, "Club", PlaceCategory.LEISURE, 8, rank = 4),
                        item(SliceKind.ELSEWHERE, null, null, 10),
                    ),
                centerPercent = 90,
            )
        show(shown(six))
        listOf("Maison", "Sport", "Bureau", "Chez Léa").forEach { node(it).assertExists() }
        // "Hors-zone" (10 %) and "Club" (8 %) are the smallest shares: not in the four lines, but in the full description for the screen reader.
        nodes("Club").assertCountEquals(0)
        nodes("Hors-zone").assertCountEquals(0)
        val described =
            composeRule
                .onAllNodes(
                    hasContentDescription("Club ${pct(8)}, Hors-zone ${pct(10)}", substring = true),
                ).fetchSemanticsNodes()
                .size
        assertEquals(1, described)
        shoot("six-parts")
    }

    @Test
    fun theLegendWritesTheWholePercentagesAsTheSystemDoes_andTheTinyShareAsLessThanOne() {
        val tiny =
            TimeShareBody.Chart(
                items =
                    listOf(
                        item(SliceKind.PLACE, "Maison", PlaceCategory.HOME, 100, rank = 0),
                        item(SliceKind.PLACE, "Coin", null, 0, rank = 1, tiny = true),
                    ),
                centerPercent = 100,
            )
        show(shown(tiny))
        // 100 % is in the centre, on the line of the place and in the band.
        nodes(pct(100)).assertCountEquals(2)
        node(text(R.string.profile_timeshare_percent_tiny)).assertIsDisplayed()
    }

    // --- The selector -----------------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun theSelector_isAnnouncedAsAPeriodButton_andOffersTheThreeWindows() {
        var chosen = 0
        show(shown(), TimeShareActions(onWindowChosen = { chosen = it }))
        composeRule.onNodeWithContentDescription("Période, 7 jours").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Période, 7 jours").performClick()
        node("14 jours").assertIsDisplayed()
        node("30 jours").assertIsDisplayed()
        node("14 jours").performClick()
        assertEquals(14, chosen)
    }

    @Test
    fun noSubtitleUnderTheTitle_theWindowIsTheSelectorAlone() {
        show(shown(days = 30))
        nodes("Sur les 30 derniers jours").assertCountEquals(0)
        node("30 jours").assertIsDisplayed()
    }

    // --- The danger zone ----------------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun theDangerZone_isOneLine_thatOpensItsDetail_whichAddsUpToTheLine_andClosesWithAButton() {
        show(shown())
        nodes("Zones de danger").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Zones de danger, ${pct(12)}, afficher le détail").performClick()
        node("Le parc").assertIsDisplayed()
        node("Chantier rue Haute").assertIsDisplayed()
        node(pct(7)).assertIsDisplayed()
        node(pct(5)).assertIsDisplayed()
        node("Fermer").performClick()
        composeRule.waitForIdle()
        nodes("Le parc").assertCountEquals(0)
    }

    @Test
    fun aSingleDangerPlace_isTheSingularLabel() {
        val one =
            item(SliceKind.DANGER, null, null, 12, count = 1, details = listOf(DetailItem("Le parc", null, 12, false)))
        show(shown(TimeShareBody.Chart(listOf(mockup.items[0], one, mockup.items[3]), 48)))
        node("Zone de danger").assertIsDisplayed()
    }

    @Test
    fun noDangerLine_whenThereIsNoTimeInADangerZone() {
        show(shown(TimeShareBody.Chart(listOf(mockup.items[0], mockup.items[1], mockup.items[3]), 72)))
        nodes("Zones de danger").assertCountEquals(0)
        nodes("Zone de danger").assertCountEquals(0)
    }

    // --- Reading by a screen reader ------------------------------------------------------------------------------------------------------------------------

    @Test
    fun theChartIsDescribedByOneSentence_theRingAndTheEmojiAreNotRead() {
        show(shown())
        // The danger line is a button of its own: the sentence does not read it a second time.
        val expected = "Répartition du temps : Maison ${pct(48)}, Sport ${pct(24)}, Hors-zone ${pct(16)}."
        val described = composeRule.onAllNodes(hasContentDescription(expected)).fetchSemanticsNodes().size
        assertEquals("one description of the chart", 1, described)
        val root = composeRule.onRoot(useUnmergedTree = false).fetchSemanticsNode()
        listOf("🏠", "⚽", "🎉").forEach { emoji ->
            assertFalse("the emoji $emoji must not be read", collect(root).any { it.contains(emoji) })
        }
    }

    @Test
    fun theDangerLine_isReadOnce_asAButton_andNotInTheSentence() {
        show(shown())
        val inSentence =
            composeRule
                .onAllNodes(
                    hasContentDescription("Zones de danger ${pct(12)}", substring = true),
                ).fetchSemanticsNodes()
                .size
        assertEquals("not in the sentence", 0, inSentence)
        val asButton =
            composeRule
                .onAllNodes(
                    hasContentDescription("Zones de danger, ${pct(12)}, afficher le détail"),
                ).fetchSemanticsNodes()
                .size
        assertEquals("once, as the button", 1, asButton)
    }

    @Test
    fun aDangerLineThatIsNotAmongTheFourLines_isStillInTheSentence_sinceNoButtonReadsIt() {
        val small =
            item(SliceKind.DANGER, null, null, 4, count = 1, details = listOf(DetailItem("Le parc", null, 4, false)))
        val body =
            TimeShareBody.Chart(
                items =
                    listOf(
                        item(SliceKind.PLACE, "Maison", PlaceCategory.HOME, 30, rank = 0),
                        item(SliceKind.PLACE, "Sport", PlaceCategory.SPORT, 26, rank = 1),
                        item(SliceKind.PLACE, "Bureau", PlaceCategory.WORK, 20, rank = 2),
                        item(SliceKind.PLACE, "Club", PlaceCategory.LEISURE, 12, rank = 3),
                        item(SliceKind.ELSEWHERE, null, null, 8),
                        small,
                    ),
                centerPercent = 88,
            )
        show(shown(body))
        val inSentence =
            composeRule
                .onAllNodes(
                    hasContentDescription("Zone de danger ${pct(4)}", substring = true),
                ).fetchSemanticsNodes()
                .size
        assertEquals(1, inSentence)
    }

    private fun collect(node: SemanticsNode): List<String> {
        val own =
            listOfNotNull(
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
                node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text },
            )
        return own + node.children.flatMap { collect(it) }
    }

    // --- The states: no chart, no selector ----------------------------------------------------------------------------------------------------------------

    @Test
    fun underSevenDays_noChartAndNoSelector_onlyTheWordingFixedByThePm_withTheDaysLeft() {
        show(shown(TimeShareBody.NotEnough(daysLeft = 4)))
        node("Répartition du temps").assertIsDisplayed()
        node("Ton graphique arrive dans 4 jours, nous analysons tes habitudes").assertIsDisplayed()
        nodes("Ces données restent sur ton téléphone. Personne d'autre ne peut les voir.").assertCountEquals(0)
        // No selector, no subtitle, no legend, no band.
        nodes("7 jours").assertCountEquals(0)
        nodes("Sur les 7 derniers jours").assertCountEquals(0)
        nodes("Hors-zone").assertCountEquals(0)
        nodes("en zone de confiance").assertCountEquals(0)
        shoot("pas-assez-de-donnees")
    }

    @Test
    fun theWordingIsSingularForOneDay_andSevenWithNoJournalYet() {
        show(shown(TimeShareBody.NotEnough(daysLeft = 1)))
        node("Ton graphique arrive dans 1 jour, nous analysons tes habitudes").assertIsDisplayed()
    }

    @Test
    fun theWordingSaysSevenDays_whenTheJournalIsEmpty() {
        show(shown(TimeShareBody.NotEnough(daysLeft = 7)))
        node("Ton graphique arrive dans 7 jours, nous analysons tes habitudes").assertIsDisplayed()
    }

    @Test
    fun aTripWithNoData_saysTheChartIsPaused_withoutAnyButton() {
        show(shown(TimeShareBody.NotEnough(4), health = TrackingHealth.TRAVEL))
        node("Tu es en mode voyage : ta répartition est en pause jusqu'à ton retour.").assertIsDisplayed()
        nodes("Ton graphique arrive dans 4 jours, nous analysons tes habitudes").assertCountEquals(0)
    }

    @Test
    fun aSharingStoppedWithNoData_offersTheScreenOfTheMiff() {
        var opened: String? = null
        show(
            shown(TimeShareBody.NotEnough(4), health = TrackingHealth.SHARING_STOPPED),
            TimeShareActions(onOpenMiff = {
                opened =
                    it
            }),
        )
        node(
            "Tu as arrêté de partager ton statut avec cette Miff : ta répartition n'est plus mise à jour pour elle.",
        ).assertIsDisplayed()
        node("Voir cette Miff").performClick()
        assertEquals("miffA", opened)
    }

    @Test
    fun aLocationThatIsNotEnough_withNoData_saysWhatIsNeeded_andOffersTheExistingStep() {
        show(shown(TimeShareBody.NotEnough(4), health = TrackingHealth.LOCATION_INSUFFICIENT))
        node(
            "Pour mesurer ton temps dans tes lieux, Miff a besoin de la localisation « Toujours » avec la position précise. Tu peux aussi continuer sans.",
        ).assertIsDisplayed()
        composeRule
            .onNodeWithText(
                text(R.string.permission_location_action),
            ).assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun aPausedTracking_withEnoughData_keepsTheChart_andAddsOneCalmLine() {
        show(shown(health = TrackingHealth.TRAVEL))
        node("Maison").assertIsDisplayed()
        node("Le suivi de tes lieux est en pause.").assertIsDisplayed()
    }

    @Test
    fun moreThanTwentyZones_addTheNoteUnderTheRing() {
        show(shown(overLimit = true))
        node(
            "Tous tes lieux ne sont pas suivis en même temps : la répartition peut être incomplète.",
        ).assertIsDisplayed()
    }

    @Test
    fun theTextsOfTheStates_haveNoExclamationMark() {
        listOf(
            R.string.profile_timeshare_paused,
            R.string.profile_timeshare_travel,
            R.string.profile_timeshare_sharing_stopped,
            R.string.profile_timeshare_location_needed,
            R.string.profile_timeshare_over_limit,
            R.string.profile_timeshare_error,
        ).forEach { assertFalse(text(it).contains("!")) }
    }

    @Test
    fun aFailedReading_isACalmSentence_andARetryThatWorks() {
        var retried = 0
        show(TimeShareUiState.Failed, TimeShareActions(onRetry = { retried++ }))
        node("La répartition n'est pas disponible pour le moment.").assertIsDisplayed()
        node("Réessayer").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun hiddenAndLoading_drawNothingAtFirst() {
        show(TimeShareUiState.Hidden)
        nodes("Répartition du temps").assertCountEquals(0)
    }

    @Test
    fun loading_showsNothingBefore300ms_thenAQuietPlaceholderWithoutWords() {
        composeRule.mainClock.autoAdvance = false
        show(TimeShareUiState.Loading)
        composeRule.mainClock.advanceTimeBy(100)
        nodes("Répartition du temps").assertCountEquals(0)
        composeRule.mainClock.advanceTimeBy(400)
        nodes("Répartition du temps").assertCountEquals(0)
    }

    // --- The largest font -----------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun atTheLargestFont_theRingGoesAboveTheLegend_andEverythingStaysReadable() {
        show(shown(), fontScale = 2f)
        node("Maison").assertExists()
        node(pct(72)).assertExists()
        node("en zone de confiance").assertExists()
        shoot("police-200")
    }

    @Test
    fun atTheLargestFont_theStatesAreReadable_too() {
        show(shown(TimeShareBody.NotEnough(4), health = TrackingHealth.SHARING_STOPPED), fontScale = 2f)
        node("Voir cette Miff").assertIsDisplayed()
        shoot("police-200-partage-arrete")
    }
}
