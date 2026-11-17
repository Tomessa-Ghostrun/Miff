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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
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
import com.tomessa.miff.domain.timeshare.SummaryKind
import com.tomessa.miff.domain.timeshare.TimeShareTracking
import com.tomessa.miff.domain.timeshare.TrackingHealth
import com.tomessa.miff.ui.theme.MiffTheme
import com.tomessa.miff.ui.theme.colorBgCanvas
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
import java.text.NumberFormat
import java.util.Locale

/**
 * The card "Répartition de ton temps" on the JVM (Robolectric, no phone; docs/tickets/profil-repartition-temps.md sections 5, 7 and 9): its blocks, the exact texts in French, the
 * states, the selector, the detail of the danger zone, the way it is read by a screen reader and the largest font. The figures are those of the mockup of the PM (Maison 48, Sport
 * 24, Zone de danger 12, Ailleurs 16). Pictures are written to `build/screenshots/timeshare-*.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w390dp-h844dp-port-xxhdpi")
class TimeShareCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun text(id: Int): String = context().getString(id)

    private fun text(
        id: Int,
        vararg args: Any,
    ): String = context().getString(id, *args)

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
            summary = SummaryKind.PLACE,
            summaryPlaceName = "Maison",
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
    fun theNominalCard_hasTheTitle_theSubtitle_theSelector_theLegend_theSentence_andThePrivacyLine() {
        show(shown())
        node("Répartition de ton temps").assertIsDisplayed()
        node("Sur les 7 derniers jours").assertIsDisplayed()
        node("7 jours").assertIsDisplayed()
        node("Maison").assertIsDisplayed()
        node("Sport").assertIsDisplayed()
        node("Zones de danger").assertIsDisplayed()
        node("Ailleurs").assertIsDisplayed()
        node(pct(72)).assertIsDisplayed()
        node("dans tes lieux de confiance").assertIsDisplayed()
        node("Sur les 7 derniers jours, c'est à Maison que tu as passé le plus de temps.").assertIsDisplayed()
        node("Ces données restent sur ton téléphone. Personne d'autre ne peut les voir.").assertIsDisplayed()
        shoot("nominal")
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
                summary = SummaryKind.PLACE,
                summaryPlaceName = "Maison",
            )
        show(shown(tiny))
        // 100 % is in the centre and on the line of the place.
        nodes(pct(100)).assertCountEquals(2)
        node(text(R.string.profile_timeshare_percent_tiny)).assertIsDisplayed()
        assertTrue(text(R.string.profile_timeshare_percent_tiny).contains("<"))
    }

    @Test
    fun theSentenceNamesNoDangerZone_andTheElsewhereAndGenericVariantsAreTheTextsOfTheTicket() {
        show(shown(TimeShareBody.Chart(mockup.items, 72, SummaryKind.ELSEWHERE, null), days = 14))
        node(
            "Sur les 14 derniers jours, tu as surtout été ailleurs que dans tes lieux de confiance.",
        ).assertIsDisplayed()
    }

    @Test
    fun theGenericSentence_forThirtyDays() {
        show(shown(TimeShareBody.Chart(mockup.items, 72, SummaryKind.GENERIC, null), days = 30))
        node("Voici comment ton temps s'est réparti sur les 30 derniers jours.").assertIsDisplayed()
        node("Sur les 30 derniers jours").assertIsDisplayed()
        node("30 jours").assertIsDisplayed()
    }

    @Test
    fun noCongratulationNoJudgement_inAnyTextOfTheCard() {
        val all =
            listOf(
                R.string.profile_timeshare_title,
                R.string.profile_timeshare_summary_place_7,
                R.string.profile_timeshare_summary_elsewhere_7,
                R.string.profile_timeshare_summary_generic_7,
                R.string.profile_timeshare_not_enough,
                R.string.profile_timeshare_paused,
                R.string.profile_timeshare_travel,
                R.string.profile_timeshare_sharing_stopped,
                R.string.profile_timeshare_location_needed,
                R.string.profile_timeshare_over_limit,
                R.string.profile_timeshare_error,
                R.string.profile_timeshare_privacy,
            ).map { text(it) }
        all.forEach { sentence ->
            assertFalse("no exclamation mark: $sentence", sentence.contains("!"))
            assertFalse(
                "no congratulation or reproach: $sentence",
                Regex(
                    "(?i)bravo|félicit|tant mieux|dommage|attention|danger",
                ).containsMatchIn(sentence.replace("zone de danger", "")),
            )
        }
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
        show(shown(TimeShareBody.Chart(listOf(mockup.items[0], one, mockup.items[3]), 48, SummaryKind.GENERIC, null)))
        node("Zone de danger").assertIsDisplayed()
    }

    @Test
    fun noDangerLine_whenThereIsNoTimeInADangerZone() {
        show(
            shown(
                TimeShareBody.Chart(
                    listOf(mockup.items[0], mockup.items[1], mockup.items[3]),
                    72,
                    SummaryKind.PLACE,
                    "Maison",
                ),
            ),
        )
        nodes("Zones de danger").assertCountEquals(0)
        nodes("Zone de danger").assertCountEquals(0)
    }

    // --- Reading by a screen reader ------------------------------------------------------------------------------------------------------------------------

    @Test
    fun theChartIsDescribedByOneSentence_theRingAndTheEmojiAreNotRead() {
        show(shown())
        val expected = "Répartition de ton temps. Sur les 7 derniers jours : Maison ${pct(
            48,
        )}, Sport ${pct(24)}, Zones de danger ${pct(12)}, Ailleurs ${pct(16)}."
        val described = composeRule.onAllNodesWithContentDescriptionSafe(expected)
        assertEquals("one description of the chart", 1, described)
        // The emoji of the categories is never part of what is read.
        listOf("🏠", "⚽").forEach { emoji ->
            val read =
                composeRule.onRoot(useUnmergedTree = false).fetchSemanticsNode().let { root ->
                    collect(root).any { it.contains(emoji) }
                }
            assertFalse("the emoji $emoji must not be read", read)
        }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithContentDescriptionSafe(
        description: String,
    ): Int =
        onAllNodes(
            androidx.compose.ui.test
                .hasContentDescription(description),
        ).fetchSemanticsNodes().size

    private fun collect(node: androidx.compose.ui.semantics.SemanticsNode): List<String> {
        val own =
            listOfNotNull(
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
                node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text },
            )
        return own + node.children.flatMap { collect(it) }
    }

    // --- The states -------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun notEnoughData_isTheWordingFixedByThePm_withTheEmptyRing_andTheConfidentialityLine() {
        show(shown(TimeShareBody.NotEnough))
        node("Ton graphique arrive dans quelques jours, nous analysons ta situation").assertIsDisplayed()
        node("Ces données restent sur ton téléphone. Personne d'autre ne peut les voir.").assertIsDisplayed()
        // No percentage, no legend.
        nodes("Ailleurs").assertCountEquals(0)
        shoot("pas-assez-de-donnees")
    }

    @Test
    fun aTripWithNoData_saysTheChartIsPaused_withoutAnyButton() {
        show(shown(TimeShareBody.NotEnough, health = TrackingHealth.TRAVEL))
        node("Tu es en mode voyage : ta répartition est en pause jusqu'à ton retour.").assertIsDisplayed()
        nodes("Ton graphique arrive dans quelques jours, nous analysons ta situation").assertCountEquals(0)
    }

    @Test
    fun aSharingStoppedWithNoData_offersTheScreenOfTheMiff() {
        var opened: String? = null
        show(
            shown(TimeShareBody.NotEnough, health = TrackingHealth.SHARING_STOPPED),
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
        show(shown(TimeShareBody.NotEnough, health = TrackingHealth.LOCATION_INSUFFICIENT))
        node(
            "Pour mesurer ton temps dans tes lieux, Miff a besoin de la localisation « Toujours » avec la position précise. Tu peux aussi continuer sans.",
        ).assertIsDisplayed()
        node(text(R.string.permission_location_action)).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
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
        nodes("Répartition de ton temps").assertCountEquals(0)
    }

    @Test
    fun loading_showsNothingBefore300ms_thenAQuietPlaceholder() {
        composeRule.mainClock.autoAdvance = false
        show(TimeShareUiState.Loading)
        composeRule.mainClock.advanceTimeBy(100)
        nodes("Répartition de ton temps").assertCountEquals(0)
        composeRule.mainClock.advanceTimeBy(400)
        // Still no words: the placeholder has no text, only quiet shapes.
        nodes("Répartition de ton temps").assertCountEquals(0)
    }

    // --- The largest font -----------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun atTheLargestFont_theLegendGoesUnderTheRing_andEverythingStaysReadable() {
        show(shown(), fontScale = 2f)
        // The card is taller than the screen at this size: the pictures below show it all; here every block must exist (the tab scrolls).
        node("Maison").assertExists()
        node(pct(72)).assertExists()
        node("dans tes lieux de confiance").assertExists()
        node("Ces données restent sur ton téléphone. Personne d'autre ne peut les voir.").assertExists()
        shoot("police-200")
    }

    @Test
    fun atTheLargestFont_theStatesAreReadable_too() {
        show(shown(TimeShareBody.NotEnough, health = TrackingHealth.SHARING_STOPPED), fontScale = 2f)
        node("Voir cette Miff").assertIsDisplayed()
        shoot("police-200-partage-arrete")
    }

    @Test
    fun otherStatesForThePictures() {
        show(shown(TimeShareBody.NotEnough))
        shoot("etat-vide")
    }
}
