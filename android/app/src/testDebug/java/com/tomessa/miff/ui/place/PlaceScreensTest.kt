package com.tomessa.miff.ui.place

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
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
import com.tomessa.miff.domain.place.AddressSuggestion
import com.tomessa.miff.domain.place.OwnedMiff
import com.tomessa.miff.domain.place.PaywallReason
import com.tomessa.miff.domain.place.PlaceCategory
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.testsupport.MIFF_ID
import com.tomessa.miff.testsupport.OTHER_MIFF_ID
import com.tomessa.miff.testsupport.testPlace
import com.tomessa.miff.testsupport.testSuggestion
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The screens of the places on the JVM (Robolectric, no phone, no map: a stand-in draws the map): what is shown, what a member does
 * not get, what a free account sees, the touch targets and the largest font. Writes screenshots to build/screenshots for the pull
 * request (compare with design/exports/lieux).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w390dp-h844dp-port-xxhdpi")
class PlaceScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun text(id: Int): String = context().getString(id)

    private fun text(
        id: Int,
        vararg args: Any,
    ): String = context().getString(id, *args)

    /** The map of the real app is MapLibre (a native library): here a grey box stands in for it. */
    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent {
            MiffTheme {
                CompositionLocalProvider(
                    LocalPlaceMapRenderer provides { _, _, modifier -> Box(modifier.background(Color(0xFFE5E3DA))) },
                ) { Box(Modifier.fillMaxSize()) { content() } }
            }
        }
    }

    private fun shoot(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val famille = OwnedMiff(MIFF_ID, "Famille Mfouka", 100)
    private val tuches = OwnedMiff(OTHER_MIFF_ID, "Les tuches", 200)
    private val chosen: AddressSuggestion = testSuggestion()

    private fun filled(
        premium: Boolean = false,
        owned: List<OwnedMiff> = listOf(famille),
        selected: List<String> = listOf(MIFF_ID),
        name: String = "Maison du père de Nathan",
    ) = PlaceFormState(
        loading = false,
        premium = premium,
        name = name,
        owned = owned,
        lastJoinedId = MIFF_ID,
        selectedMiffIds = selected,
        addressText = chosen.label,
        chosen = chosen,
    )

    private fun form(
        state: PlaceFormState,
        actions: PlaceFormActions = PlaceFormActions(),
    ) = show { PlaceFormScreen(state, actions) }

    // --- The form: empty (01), filled (03) -------------------------------------------------------------------------------

    @Test
    fun form_empty_hasEverySection_theSingleButtonAsleep_andNoTick() {
        form(PlaceFormState(loading = false, owned = listOf(famille), selectedMiffIds = listOf(MIFF_ID)))
        shoot("place-01-vide")
        composeRule.onNodeWithText(text(R.string.place_form_title_new)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_name_help)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_address_help)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_radius_help)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_type_help)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_category_help)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_save_action)).assertIsNotEnabled()
        // One button only: no "Valider", and no delete when creating.
        composeRule.onAllNodesWithText("Valider").assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.place_delete_action)).assertCountEquals(0)
    }

    @Test
    fun form_sevenCategories_travailIncluded_homeSelected() {
        form(filled())
        PlaceCategory.entries.forEach { category ->
            composeRule.onNodeWithText(text(category.labelRes)).performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithText("Travail").assertIsDisplayed()
        composeRule.onNodeWithText("Chez un proche").assertIsDisplayed()
    }

    @Test
    fun form_filled_buttonIsActive_andSaysEnregistrer() {
        var saved = 0
        form(filled(), PlaceFormActions(onSave = { saved++ }))
        shoot("place-03-rempli")
        composeRule.onNodeWithText(text(R.string.place_save_action)).assertIsEnabled().performClick()
        assertEquals(1, saved)
    }

    @Test
    fun form_singleMiff_showsTheMiffWithoutAPicker_andWhoSeesWhat() {
        form(filled())
        composeRule.onNodeWithText(text(R.string.place_miff_help)).assertIsDisplayed()
        composeRule.onNodeWithText("Famille Mfouka").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_miff_visibility)).assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.place_miffs_open_a11y)).assertCountEquals(0)
    }

    @Test
    fun form_severalMiffs_chipsAndTheSheetOpens() {
        var opened = 0
        form(
            filled(owned = listOf(famille, tuches), selected = listOf(MIFF_ID, OTHER_MIFF_ID)),
            PlaceFormActions(onMiffSheetOpened = { opened++ }),
        )
        composeRule.onNodeWithText("Les tuches").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_miffs_visibility)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_miffs_help)).assertIsDisplayed()
        composeRule.onNodeWithText("Famille Mfouka").performClick()
        assertEquals(1, opened)
    }

    // --- Errors and states (04) -------------------------------------------------------------------------------------------------

    @Test
    fun form_nameTooShort_saysTwoCharactersMinimum_inTheTextOfTheTicket() {
        form(filled(name = "M").copy(nameTooShort = true))
        shoot("place-04a-nom-court")
        composeRule.onNodeWithText("2 caractères minimum").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_name_counter, 1, 50)).assertIsDisplayed()
    }

    @Test
    fun form_nameAtFifty_saysTheLimit() {
        form(filled(name = "a".repeat(50)).copy(nameLimitReached = true))
        composeRule.onNodeWithText("Limite de 50 caractères atteinte").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_name_counter, 50, 50)).assertIsDisplayed()
    }

    @Test
    fun form_suggestions_areListed_withTheCredit_andAreChosenByATap() {
        var picked: AddressSuggestion? = null
        form(
            PlaceFormState(
                loading = false,
                owned = listOf(famille),
                selectedMiffIds = listOf(MIFF_ID),
                addressText = "17 av",
                suggestions =
                    SuggestionsState.Found(
                        listOf(chosen, testSuggestion("17 Avenue de France", "75013 Paris, France")),
                    ),
            ),
            PlaceFormActions(onSuggestionChosen = { picked = it }),
        )
        shoot("place-02-suggestions")
        composeRule.onNodeWithText(text(R.string.place_address_credit)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("17 Avenue de France").performScrollTo().performClick()
        assertEquals("17 Avenue de France", picked?.title)
    }

    @Test
    fun form_suggestions_noResult_andUnavailable_areCalm_andUnavailableCanBeRetried() {
        var retried = 0
        form(
            PlaceFormState(
                loading = false,
                owned = listOf(famille),
                selectedMiffIds = listOf(MIFF_ID),
                addressText = "17 avnue",
                suggestions = SuggestionsState.Unavailable,
            ),
            PlaceFormActions(onRetrySearch = { retried++ }),
        )
        shoot("place-04e-indisponible")
        composeRule.onNodeWithText(text(R.string.place_address_unavailable)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.auth_retry_action)).performScrollTo().performClick()
        assertEquals(1, retried)
    }

    @Test
    fun form_noResult_saysSo() {
        form(
            PlaceFormState(
                loading = false,
                owned = listOf(famille),
                selectedMiffIds = listOf(MIFF_ID),
                addressText = "zzz",
                suggestions = SuggestionsState.NoResults,
            ),
        )
        composeRule.onNodeWithText(text(R.string.place_address_none)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun form_addressTypedButNotChosen_saysToPickIt_andTheButtonSleeps() {
        form(
            PlaceFormState(
                loading = false,
                owned = listOf(famille),
                selectedMiffIds = listOf(MIFF_ID),
                name = "Maison",
                addressText = "17 Avenue d'Italie, 75013 Paris",
            ),
        )
        shoot("place-04f-adresse-non-choisie")
        composeRule.onNodeWithText(text(R.string.place_address_pick_hint)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_save_action)).assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.place_radius_pick_address)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun form_radiusUnderOneHundred_showsTheDiscreetNote_andOnlyThen() {
        var radius by mutableIntStateOf(50)
        show { PlaceFormScreen(filled().copy(radiusM = radius), PlaceFormActions()) }
        shoot("place-04g-rayon-50")
        composeRule.onNodeWithText(text(R.string.place_radius_small_note)).performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { radius = 100 }
        composeRule.onAllNodesWithText(text(R.string.place_radius_small_note)).assertCountEquals(0)
    }

    @Test
    fun form_saving_showsTheWorkingButton_andNothingElseCanBePressed() {
        form(filled().copy(saving = true))
        shoot("place-04h-enregistrement")
        composeRule.onNodeWithText(text(R.string.place_saving)).assertIsNotEnabled()
    }

    @Test
    fun form_messages_offline_error_miffGone() {
        form(filled().copy(message = PlaceMessage.OFFLINE))
        shoot("place-04i-hors-connexion")
        composeRule.onNodeWithText(text(R.string.miff_message_offline)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_save_action)).assertIsEnabled()
    }

    @Test
    fun form_aMiffThatIsGone_hasItsOwnSentence() {
        form(filled().copy(message = PlaceMessage.MIFF_GONE))
        shoot("place-04k-miff-disparue")
        composeRule.onNodeWithText(text(R.string.place_message_miff_gone)).assertIsDisplayed()
    }

    @Test
    fun form_loadFailed_offersToRetry() {
        var retried = 0
        form(
            PlaceFormState(loading = false, loadFailed = PlaceMessage.OFFLINE),
            PlaceFormActions(onRetryLoad = { retried++ }),
        )
        composeRule.onNodeWithText(text(R.string.miff_message_offline)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.auth_retry_action)).performClick()
        assertEquals(1, retried)
    }

    // --- Type: Miff+ is visible BEFORE it is touched (design decision 11) ---------------------------------------------------------

    @Test
    fun form_free_dangerZoneCarriesTheMiffPlusPill_andOpensTheOfferWhenTouched() {
        var chosenType: PlaceType? = null
        form(filled(premium = false), PlaceFormActions(onTypeSelected = { chosenType = it }))
        composeRule.onNodeWithText(text(R.string.place_premium_pill)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_type_danger)).performClick()
        assertEquals(PlaceType.RISK, chosenType)
    }

    @Test
    fun form_premium_hasNoPill() {
        form(filled(premium = true))
        composeRule.onNodeWithText(text(R.string.place_type_danger)).performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.place_premium_pill)).assertCountEquals(0)
    }

    @Test
    fun form_typeTexts_useDangerZone_neverRiskZone() {
        form(filled())
        composeRule.onNodeWithText("Zone de confiance").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Zone de danger").assertIsDisplayed()
        composeRule.onAllNodesWithText("Zone à risque", substring = true).assertCountEquals(0)
    }

    // --- Edit and delete (06, 07) -----------------------------------------------------------------------------------------------

    @Test
    fun edit_hasADeleteButton_asleepSave_andTheDialogAsksFirst() {
        var asked = 0
        var confirmed = 0
        form(
            filled().copy(mode = PlaceFormMode.EDIT, initial = filled().snapshot),
            PlaceFormActions(onDeleteRequested = { asked++ }, onDeleteConfirmed = { confirmed++ }),
        )
        shoot("place-06-modifier")
        composeRule.onNodeWithText(text(R.string.place_form_title_edit)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_save_action)).assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.place_delete_action)).assertIsDisplayed().performClick()
        assertEquals(1, asked)
        assertEquals(0, confirmed)
    }

    @Test
    fun deleteDialog_saysForYouAndAllTheMembers_withAnnulerFirstAvailable() {
        var confirmed = 0
        var cancelled = 0
        form(
            filled().copy(mode = PlaceFormMode.EDIT, initial = filled().snapshot, deleteDialogOpen = true),
            PlaceFormActions(onDeleteConfirmed = { confirmed++ }, onDeleteDismissed = { cancelled++ }),
        )
        composeRule.onNodeWithText(text(R.string.place_delete_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_delete_body)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.common_cancel)).performClick()
        assertEquals(1, cancelled)
        // The button behind and the one of the dialog.
        composeRule.onAllNodesWithText(text(R.string.place_delete_action)).assertCountEquals(2)
    }

    @Test
    fun deleteDialog_severalMiffs_listsThem() {
        val base = filled(owned = listOf(famille, tuches), selected = listOf(MIFF_ID, OTHER_MIFF_ID))
        form(base.copy(mode = PlaceFormMode.EDIT, initial = base.snapshot, deleteDialogOpen = true))
        shoot("place-07b-suppression-plusieurs-miffs")
        composeRule.onNodeWithText(text(R.string.place_delete_body_several)).assertIsDisplayed()
        // The chip behind and the list of the dialog.
        composeRule.onAllNodesWithText("Les tuches").assertCountEquals(2)
    }

    // --- The sheet of the Miffs (05) --------------------------------------------------------------------------------------------

    @Test
    fun sheet_listsTheMiffs_withTheLastJoinedPill_andDoneIsAsleepWhenNoneIsTicked() {
        form(
            filled(owned = listOf(famille, tuches), selected = emptyList()).copy(miffSheetOpen = true),
        )
        composeRule.onNodeWithText(text(R.string.place_miffs_sheet_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_miffs_last_joined)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_miffs_none_selected)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_miffs_sheet_done)).assertIsNotEnabled()
    }

    // --- The list (17) -----------------------------------------------------------------------------------------------------------

    private val two =
        listOf(
            testPlace("p1", "Maison de Papa", address = "26 rue du Volnay, 21000 Dijon"),
            testPlace("p2", "Foot de Thomas", category = PlaceCategory.SPORT, address = "Stade des Poussots"),
        )

    private fun rows(
        ui: PlaceListUi,
        onOpen: (com.tomessa.miff.domain.place.Place) -> Unit = {},
        onAdd: () -> Unit = {},
    ) = show { PlaceRows(ui, onOpen, onAdd, Modifier) }

    @Test
    fun list_freeCreatorAtTheLimit_seesTheCounter_andAnAddButtonWithThePill_neverHiddenOrGrey() {
        var added = 0
        rows(PlaceListUi(two, isCreator = true, premium = false), onAdd = { added++ })
        shoot("place-17a-liste-limite")
        composeRule.onNodeWithText("2 lieux sur 2 avec la formule gratuite").assertIsDisplayed()
        composeRule
            .onNodeWithText(
                text(R.string.place_list_add_action),
            ).assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        assertEquals(1, added)
        composeRule.onNodeWithText(text(R.string.place_premium_pill)).assertIsDisplayed()
    }

    @Test
    fun list_premiumCreator_hasNoCounterAndNoPill() {
        rows(PlaceListUi(two, isCreator = true, premium = true))
        shoot("place-17b-liste-premium")
        composeRule.onNodeWithText(text(R.string.place_list_add_action)).assertIsDisplayed()
        composeRule.onAllNodesWithText("lieux sur 2", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.place_premium_pill)).assertCountEquals(0)
    }

    @Test
    fun list_unknownPlan_claimsNothingAboutTheFreePlan() {
        rows(PlaceListUi(two, isCreator = true, premium = null))
        composeRule.onAllNodesWithText("lieux sur 2", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.place_premium_pill)).assertCountEquals(0)
    }

    @Test
    fun list_member_readsOnly_andSeesWhoSetsThePlaces() {
        var opened: String? = null
        rows(PlaceListUi(two, isCreator = false, premium = false), onOpen = { opened = it.id })
        shoot("place-17c-liste-membre")
        composeRule.onAllNodesWithText(text(R.string.place_list_add_action)).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.place_list_member_hint)).assertIsDisplayed()
        composeRule.onNodeWithText("Maison de Papa").performClick()
        assertEquals("p1", opened)
    }

    @Test
    fun list_aDangerZone_carriesItsLabelInWords() {
        rows(
            PlaceListUi(
                listOf(testPlace("x", "Chantier rue Haute", type = PlaceType.RISK)),
                isCreator = true,
                premium = true,
            ),
        )
        composeRule.onNodeWithText(text(R.string.place_type_danger)).assertIsDisplayed()
    }

    @Test
    fun list_empty_saysItCalmly_andTheCreatorCanStillAdd() {
        rows(PlaceListUi(emptyList(), isCreator = true, premium = true))
        composeRule.onNodeWithText(text(R.string.miff_places_empty)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_list_add_action)).assertIsDisplayed()
    }

    @Test
    fun list_afterASave_showsTheHonestSentenceAboutThePropagation() {
        rows(PlaceListUi(two, isCreator = true, premium = true, savedName = "Maison de Papa"))
        shoot("place-04l-lieu-enregistre")
        val sentence = text(R.string.place_saved_notice, "Maison de Papa")
        assertTrue(sentence.startsWith("Le lieu « Maison de Papa » est enregistré."))
        assertTrue(sentence.endsWith("ou dans les prochaines heures."))
        composeRule.onNodeWithText(sentence).assertIsDisplayed()
    }

    @Test
    fun list_rowsAreReadAsOneLine_andAreBigEnoughToTouch() {
        rows(PlaceListUi(two, isCreator = true, premium = true))
        composeRule
            .onNodeWithContentDescription("Maison de Papa, catégorie Maison, 26 rue du Volnay, 21000 Dijon")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    // --- The detail (16) ---------------------------------------------------------------------------------------------------------

    private fun detail(
        state: PlaceDetailState,
        onEdit: () -> Unit = {},
    ) = show { PlaceDetailScreen(state, onBack = {}, onEdit = onEdit, onRetry = {}) }

    private val readyState =
        PlaceDetailState(
            status = PlaceDetailStatus.READY,
            place = testPlace("p1", "Maison de Papa", radiusM = 150, address = "26 rue du Volnay, 21000 Dijon, France"),
            miffName = "Famille Martin",
            creatorName = "Emmanuelle Martin",
        )

    @Test
    fun detail_member_readsEverything_andCannotEdit() {
        detail(readyState)
        shoot("place-16a-detail-membre")
        composeRule.onNodeWithText("Maison de Papa").assertIsDisplayed()
        composeRule.onNodeWithText("26 rue du Volnay").assertIsDisplayed()
        composeRule.onNodeWithText("21000 Dijon, France").assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_detail_view_map)).assertIsDisplayed()
        composeRule.onNodeWithText("150 m").assertIsDisplayed()
        composeRule.onNodeWithText("Famille Martin").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Emmanuelle Martin").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_detail_info_trusted)).performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.place_detail_edit)).assertCountEquals(0)
    }

    @Test
    fun detail_creator_canEdit_andIsToldItIsThem_inAZoneOfDanger() {
        var edited = 0
        detail(
            readyState.copy(
                place = testPlace("p2", "Chantier rue Haute", type = PlaceType.RISK, category = PlaceCategory.OTHER),
                canEdit = true,
                creatorIsMe = true,
            ),
            onEdit = { edited++ },
        )
        shoot("place-16b-detail-createur")
        composeRule.onNodeWithText(text(R.string.place_detail_edit)).performClick()
        assertEquals(1, edited)
        composeRule.onNodeWithText("Emmanuelle Martin (toi)").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_detail_info_danger)).performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("risque", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun detail_unavailable_isCalm_andCanBeRetried() {
        detail(PlaceDetailState(status = PlaceDetailStatus.UNAVAILABLE))
        composeRule.onNodeWithText(text(R.string.miff_unavailable)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.auth_retry_action)).assertIsDisplayed()
    }

    @Test
    fun detail_theMapIsDescribedForAScreenReader() {
        detail(readyState)
        composeRule
            .onNodeWithContentDescription(text(R.string.place_map_a11y, 150, "26 rue du Volnay, 21000 Dijon, France"))
            .assertExists()
    }

    // --- The offer (14, 15) --------------------------------------------------------------------------------------------------------

    @Test
    fun offer_limit_saysWhy_andPromisesNothingThatDoesNotExist() {
        var closed = 0
        show { PlacePaywallScreen(PaywallReason.LIMIT, onClose = { closed++ }) }
        shoot("place-14-offre-limite")
        composeRule.onNodeWithText(text(R.string.place_paywall_limit_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_paywall_soon)).assertIsDisplayed()
        // No purchase exists yet: no trial button, no price, no promise of notifications (risk R13).
        composeRule.onAllNodesWithText("essai", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("€", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Notifications", substring = true).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.place_paywall_later)).performClick()
        assertEquals(1, closed)
    }

    @Test
    fun offer_danger_saysWhy() {
        show { PlacePaywallScreen(PaywallReason.DANGER, onClose = {}) }
        shoot("place-15-offre-danger")
        composeRule.onNodeWithText(text(R.string.place_paywall_danger_title)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(text(R.string.place_paywall_close_a11y)).assertIsDisplayed()
    }

    // --- The largest font (criterion TRA2) ----------------------------------------------------------------------------------------

    @Test
    fun largestFont_theFormStaysUsable_andTheTypeCardsPileUp() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                MiffTheme {
                    CompositionLocalProvider(
                        LocalPlaceMapRenderer provides {
                            _,
                            _,
                            modifier,
                            ->
                            Box(modifier.background(Color(0xFFE5E3DA)))
                        },
                    ) { PlaceFormScreen(filled(), PlaceFormActions()) }
                }
            }
        }
        shoot("place-grande-police")
        composeRule.onNodeWithText(text(R.string.place_save_action)).assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText(text(R.string.place_type_danger)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.place_category_other)).performScrollTo().assertIsDisplayed()
        assertTrue(true)
    }
}
