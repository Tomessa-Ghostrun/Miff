package com.tomessa.miff.ui.notification

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.domain.feed.PlaceKind
import com.tomessa.miff.domain.notification.LastVisit
import com.tomessa.miff.domain.notification.NotificationPermissionState
import com.tomessa.miff.domain.notification.NotificationPreferences
import com.tomessa.miff.domain.notification.NotificationType
import com.tomessa.miff.domain.notification.VisitKind
import com.tomessa.miff.ui.settings.SettingsScreen
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

/** The screens of the notifications on the JVM (Robolectric): the words, the states of the permission, the gestures, the touch targets. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w360dp-h800dp-xhdpi")
class NotificationScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun text(id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { MiffTheme { Box(Modifier.fillMaxSize()) { content() } } }
    }

    private fun shoot(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // --- The list of Settings ---------------------------------------------------------------------------------------------------------

    @Test
    fun settingsList_hasTheNotificationsRow_whenTheAccountHasAMiff_andNotInTheRestrictedList() {
        var opened = 0
        show { SettingsScreen({}, {}, {}, {}, {}, onOpenNotifications = { opened++ }) }
        composeRule
            .onNodeWithText(text(R.string.notif_settings_title))
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, opened)
    }

    @Test
    fun restrictedSettings_haveNoNotificationsRow_criterionNR1() {
        show {
            SettingsScreen(
                onBack = {},
                onOpenMyInfo = null,
                onOpenAccount = {},
                onOpenLanguage = null,
                onOpenPrivacy = {},
            )
        }
        composeRule.onAllNodesWithText(text(R.string.notif_settings_title)).assertCountEquals(0)
    }

    // --- Settings > Notifications -----------------------------------------------------------------------------------------------------

    @Test
    fun allowed_showsTheStateTheSwitchesAndTheLinks_inTheWordsOfThePm() {
        var general: Boolean? = null
        val typed = mutableListOf<Pair<NotificationType, Boolean>>()
        var seen = 0
        show {
            NotificationSettingsScreen(
                state = NotificationSettingsUiState(),
                actions =
                    NotificationSettingsActions(
                        onGeneralToggled = { general = it },
                        onTypeToggled = { type, on -> typed += type to on },
                        onSeeNotifications = { seen++ },
                    ),
            )
        }
        shoot("notif-2a-allowed")
        composeRule.onNodeWithText(text(R.string.notif_state_allowed)).assertIsDisplayed()
        // The five switches, in the words of the ticket.
        listOf(
            R.string.notif_settings_n1,
            R.string.notif_settings_n2,
            R.string.notif_settings_n3,
            R.string.notif_settings_n4,
            R.string.notif_settings_n6,
        ).forEach { composeRule.onNodeWithText(text(it)).assertHasClickAction().assertHeightIsAtLeast(48.dp) }
        // N6: said on the screen that the switch cuts the notification, never what the Miff sees.
        composeRule.onNodeWithText(text(R.string.notif_settings_n6_hint)).assertExists()
        composeRule.onNodeWithText(text(R.string.notif_settings_night_title)).assertExists()
        composeRule.onNodeWithText(text(R.string.notif_settings_n6)).performClick()
        composeRule.onNodeWithText(text(R.string.notif_settings_general)).performClick()
        composeRule.onNodeWithText(text(R.string.notif_settings_see_all)).performClick()
        assertEquals(listOf(NotificationType.N6 to false), typed)
        assertEquals(false, general)
        assertEquals(1, seen)
        // No state "refused" and no button to the phone settings while allowed.
        composeRule.onAllNodesWithText(text(R.string.notif_state_open_settings)).assertCountEquals(0)
    }

    @Test
    fun noSwitchForWhatNeedsAServer_n5IsAbandoned() {
        show { NotificationSettingsScreen(NotificationSettingsUiState(), NotificationSettingsActions()) }
        composeRule.onAllNodesWithText("N5").assertCountEquals(0)
        composeRule.onAllNodesWithText("arrivée", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun refusedByThePhone_thePhoneStateIsSaid_theSwitchesAreGreyed_andTheButtonLeadsToTheSettingsOfThePhone() {
        var opened = 0
        var general = 0
        show {
            NotificationSettingsScreen(
                state = NotificationSettingsUiState(permission = NotificationPermissionState.OFF),
                actions =
                    NotificationSettingsActions(
                        onOpenPhoneSettings = { opened++ },
                        onGeneralToggled = { general++ },
                    ),
            )
        }
        shoot("notif-2a-off")
        composeRule.onNodeWithText(text(R.string.notif_state_off)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.notif_state_off_note)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.notif_settings_general)).assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.notif_settings_n1)).assertIsNotEnabled()
        composeRule
            .onNodeWithText(
                text(R.string.notif_state_open_settings),
            ).assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, opened)
        assertEquals("the greyed general switch does nothing", 0, general)
        // Nothing culpabilising.
        composeRule.onAllNodesWithText("erreur", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun neverAsked_saysSo_andTheGeneralSwitchStaysUsable() {
        show {
            NotificationSettingsScreen(
                state = NotificationSettingsUiState(permission = NotificationPermissionState.NOT_ASKED),
                actions = NotificationSettingsActions(),
            )
        }
        composeRule.onNodeWithText(text(R.string.notif_state_notasked)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.notif_settings_general)).assertExists()
        composeRule.onAllNodesWithText(text(R.string.notif_state_open_settings)).assertCountEquals(0)
    }

    @Test
    fun generalOff_greysTheTypes_butTheyKeepTheirValues() {
        show {
            NotificationSettingsScreen(
                state = NotificationSettingsUiState(preferences = NotificationPreferences(enabled = false)),
                actions = NotificationSettingsActions(),
            )
        }
        composeRule.onNodeWithText(text(R.string.notif_settings_general)).assertExists()
        composeRule.onNodeWithText(text(R.string.notif_settings_n3)).assertIsNotEnabled()
    }

    @Test
    fun aChannelTurnedOffInThePhone_isSignalledUnderItsRows() {
        show {
            NotificationSettingsScreen(
                state = NotificationSettingsUiState(circleChannelBlocked = true),
                actions = NotificationSettingsActions(),
            )
        }
        composeRule.onNodeWithText(text(R.string.android_notif_channel_blocked)).assertExists()
    }

    // --- The help "Notifications coupées" ---------------------------------------------------------------------------------------------

    @Test
    fun help_isCalm_hasThreeSteps_andOneButton() {
        var opened = 0
        show { NotificationsHelpScreen(onBack = {}, onOpenPhoneSettings = { opened++ }) }
        shoot("notif-1a-help")
        composeRule.onNodeWithText(text(R.string.notif_help_intro)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.notif_help_choice)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.notif_help_step_1)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.notif_help_step_2)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.notif_help_step_3)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.notif_help_footer)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.notif_state_open_settings)).assertHasClickAction().performClick()
        assertEquals(1, opened)
    }

    // --- The bell and the card of the second chance -------------------------------------------------------------------------------------

    @Test
    fun theBell_isNamedForTheScreenReader_with48dpToTouch_andNeverShowsANumber() {
        var taps = 0
        show { NotificationBell(unreadCount = 2, onClick = { taps++ }) }
        composeRule
            .onNodeWithContentDescription("Notifications, 2 non lues")
            .assertHasClickAction()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(1, taps)
        composeRule.onAllNodesWithText("2").assertCountEquals(0)
    }

    @Test
    fun theBell_withNothingUnread_isJustNotifications() {
        show { NotificationBell(unreadCount = 0, onClick = {}) }
        composeRule.onNodeWithContentDescription("Notifications").assertExists()
    }

    @Test
    fun theSecondChanceCard_hasTwoQuietAnswers() {
        var enable = 0
        var later = 0
        show { NotificationSecondChanceCard(onEnable = { enable++ }, onLater = { later++ }) }
        composeRule.onNodeWithText(text(R.string.notif_second_chance_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.notif_second_chance_later)).assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithText(text(R.string.notif_second_chance_action)).performClick()
        assertEquals(1, later)
        assertEquals(1, enable)
    }

    // --- The centre ---------------------------------------------------------------------------------------------------------------------

    private val now = 1_800_000_000_000L
    private val zone = java.time.ZoneId.of("Europe/Paris")

    private fun row(
        id: String,
        type: NotificationType,
        ago: Long,
        unread: Boolean = true,
        miff: String? = null,
    ) = CenterRow(id, type, miff, now - ago, unread)

    @Test
    fun theEmptyCentre_saysNothingToReport_inTheWordsOfThePm() {
        show { NotificationCenterScreen(NotificationCenterUiState(), NotificationCenterActions(), now, zone) }
        shoot("notif-6a-empty")
        composeRule.onNodeWithText(text(R.string.notif_center_empty_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.notif_center_empty_body)).assertIsDisplayed()
    }

    @Test
    fun theLines_showTheTextsOfThePm_byDay_andATapOpensTheLine() {
        val opened = mutableListOf<String>()
        val rows =
            listOf(
                row("a", NotificationType.N1, ago = 60_000L),
                row("b", NotificationType.N3, ago = 2 * 60_000L, unread = false),
                row("c", NotificationType.N6, ago = 3 * 24 * 3_600_000L, miff = "Famille"),
            )
        show {
            NotificationCenterScreen(
                state = NotificationCenterUiState(rows),
                actions = NotificationCenterActions(onOpen = { opened += it.id }),
                nowMillis = now,
                zone = zone,
            )
        }
        shoot("notif-6a-list")
        composeRule.onNodeWithText(text(R.string.notif_n1_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.notif_n1_body)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.notif_n3_title)).assertExists()
        composeRule.onNodeWithText(text(R.string.notif_n6a_title)).assertExists()
        composeRule.onNodeWithText(text(R.string.notif_center_section_older)).assertExists()
        // A Miff name only where the line carries one.
        composeRule.onNodeWithText("Miff « Famille »", substring = true).assertExists()
        composeRule.onNodeWithText(text(R.string.notif_n1_title)).performClick()
        assertEquals(listOf("a"), opened)
        // Nothing in the vocabulary of alarm.
        composeRule.onAllNodesWithText("urgent", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("danger", substring = true, ignoreCase = true).assertCountEquals(1)
    }

    @Test
    fun aLongPress_offersToDelete_andTheLineIsOneElementForTheScreenReader() {
        val deleted = mutableListOf<String>()
        show {
            NotificationCenterScreen(
                state = NotificationCenterUiState(listOf(row("a", NotificationType.N2, ago = 60_000L))),
                actions = NotificationCenterActions(onDelete = { deleted += it }),
                nowMillis = now,
                zone = zone,
            )
        }
        composeRule.onNodeWithText(text(R.string.notif_n2_title)).performTouchInput { longClick() }
        composeRule.onNodeWithText(text(R.string.notif_center_delete_a11y)).performClick()
        assertEquals(listOf("a"), deleted)
    }

    @Test
    fun clearAll_asksBeforeEffacing_andCancelChangesNothing() {
        var cleared = 0
        show {
            NotificationCenterScreen(
                state = NotificationCenterUiState(listOf(row("a", NotificationType.N1, ago = 1000L))),
                actions = NotificationCenterActions(onClearAll = { cleared++ }),
                nowMillis = now,
                zone = zone,
            )
        }
        composeRule
            .onNodeWithContentDescription(
                text(R.string.notif_center_menu_a11y),
            ).assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithText(text(R.string.notif_center_clear_all)).performClick()
        composeRule.onNodeWithText(text(R.string.notif_center_clear_confirm_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.common_cancel)).performClick()
        assertEquals(0, cleared)
        composeRule.onNodeWithContentDescription(text(R.string.notif_center_menu_a11y)).performClick()
        composeRule.onNodeWithText(text(R.string.notif_center_clear_all)).performClick()
        composeRule.onNodeWithText(text(R.string.notif_center_clear_confirm_action)).performClick()
        assertEquals(1, cleared)
    }

    @Test
    fun markAllRead_isInTheMenu() {
        var marked = 0
        show {
            NotificationCenterScreen(
                state = NotificationCenterUiState(listOf(row("a", NotificationType.N1, ago = 1000L))),
                actions = NotificationCenterActions(onMarkAllRead = { marked++ }),
                nowMillis = now,
                zone = zone,
            )
        }
        composeRule.onNodeWithContentDescription(text(R.string.notif_center_menu_a11y)).performClick()
        composeRule.onNodeWithText(text(R.string.notif_center_mark_all_read)).performClick()
        assertEquals(1, marked)
    }

    @Test
    fun refused_theCentreIsLocked_blurredBarsOnly_noRealLine_andOneButtonToTurnThemOn() {
        val taps = mutableListOf<CenterBanner>()
        // Real lines exist, but while the notifications are off they stay out of sight: nothing real is shown behind the blur.
        val rows =
            listOf(
                row("a", NotificationType.N1, ago = 60_000L),
                row("b", NotificationType.N6, ago = 120_000L, miff = "Famille"),
            )
        show {
            NotificationCenterScreen(
                state = NotificationCenterUiState(rows, banner = CenterBanner.OFF),
                actions = NotificationCenterActions(onBannerAction = { taps += it }),
                nowMillis = now,
                zone = zone,
            )
        }
        shoot("notif-6a-locked")
        composeRule.onNodeWithText(text(R.string.notif_center_off_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.notif_center_off_body)).assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.notif_n1_title)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.notif_n6a_title)).assertCountEquals(0)
        composeRule.onAllNodesWithText("Famille", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.notif_center_empty_title)).assertCountEquals(0)
        composeRule
            .onNodeWithText(text(R.string.notif_center_enable_action))
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(listOf(CenterBanner.OFF), taps)
        // The menu has nothing to act on.
        composeRule.onNodeWithContentDescription(text(R.string.notif_center_menu_a11y)).performClick()
        composeRule.onNodeWithText(text(R.string.notif_center_clear_all)).assertIsNotEnabled()
    }

    @Test
    fun neverAsked_theCentreIsLocked_too_withItsOwnWords() {
        val taps = mutableListOf<CenterBanner>()
        show {
            NotificationCenterScreen(
                state = NotificationCenterUiState(banner = CenterBanner.NOT_ASKED),
                actions = NotificationCenterActions(onBannerAction = { taps += it }),
                nowMillis = now,
                zone = zone,
            )
        }
        composeRule.onNodeWithText(text(R.string.notif_state_notasked)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.notif_center_notasked_body)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.notif_center_enable_action)).performClick()
        assertEquals(listOf(CenterBanner.NOT_ASKED), taps)
    }

    @Test
    fun allowed_noNotificationYet_theEmptyStateIsCalm_andNothingIsBlurred() {
        show { NotificationCenterScreen(NotificationCenterUiState(), NotificationCenterActions(), now, zone) }
        composeRule.onNodeWithText(text(R.string.notif_center_empty_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.notif_center_empty_body)).assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.notif_center_enable_action)).assertCountEquals(0)
    }

    @Test
    fun theGeneralSwitchOffInTheApp_isACalmBand_notALockedCentre() {
        val taps = mutableListOf<CenterBanner>()
        show {
            NotificationCenterScreen(
                state =
                    NotificationCenterUiState(
                        listOf(row("a", NotificationType.N1, ago = 1000L)),
                        banner = CenterBanner.DISABLED,
                    ),
                actions = NotificationCenterActions(onBannerAction = { taps += it }),
                nowMillis = now,
                zone = zone,
            )
        }
        composeRule.onNodeWithText(text(R.string.notif_center_off_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.notif_n1_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.notif_center_off_action)).performClick()
        assertEquals(listOf(CenterBanner.DISABLED), taps)
    }

    // --- "Depuis ta dernière visite" (NOTIF-P1A) ---------------------------------------------------------------------------------------------

    private fun visitRow(
        id: String,
        kind: VisitKind,
        ago: Long,
        name: String? = "Thomas",
        place: PlaceKind? = PlaceKind.SCHOOL,
        unread: Boolean = true,
    ) = CenterRow(
        id = id,
        type = NotificationType.C1,
        miffName = null,
        createdAtMillis = now - ago,
        unread = unread,
        visit = LastVisit.Parsed("uid-$id", kind, place, now - ago),
        visitName = name,
        miffId = "miff-1",
    )

    private val departure = VisitKind.DEPARTURE
    private val silence = VisitKind.SILENCE

    private fun sentenceOf(row: CenterRow) =
        LastVisitTexts(
            ApplicationProvider.getApplicationContext<Context>(),
            zone,
        ).sentence(row.visit!!, row.visitName, now)

    @Test
    fun theGroup_isAboveToday_hasItsLimitSentence_andNoSecondLineOfBodyOrHour() {
        val visit =
            listOf(
                visitRow("v1", departure, ago = 3_600_000L),
                visitRow("v2", silence, ago = 2 * 3_600_000L, name = "Amandine"),
            )
        show {
            NotificationCenterScreen(
                state =
                    NotificationCenterUiState(
                        listOf(row("a", NotificationType.N1, ago = 60_000L)),
                        visitRows = visit,
                    ),
                actions = NotificationCenterActions(),
                nowMillis = now,
                zone = zone,
            )
        }
        shoot("notif-p1a-group")
        val title = composeRule.onNodeWithText(text(R.string.notif_c1_title))
        title.assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.notif_c1_limit)).assertIsDisplayed()
        composeRule.onNodeWithText(sentenceOf(visit[0])).assertIsDisplayed()
        composeRule.onNodeWithText(sentenceOf(visit[1])).assertIsDisplayed()
        // Above the first day.
        val groupTop = title.fetchSemanticsNode().boundsInRoot.top
        val todayTop =
            composeRule
                .onNodeWithText(
                    text(R.string.notif_center_section_today),
                ).fetchSemanticsNode()
                .boundsInRoot.top
        assertTrue("the group is above the days", groupTop < todayTop)
        // The empty state is not shown, and the lines of the days are untouched.
        composeRule.onAllNodesWithText(text(R.string.notif_center_empty_title)).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.notif_n1_title)).assertIsDisplayed()
    }

    @Test
    fun aGroupAlone_isNotTheEmptyState_aTapOpensTheLine_andALongPressDeletesIt() {
        val row = visitRow("v1", departure, ago = 3_600_000L)
        val opened = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        show {
            NotificationCenterScreen(
                state = NotificationCenterUiState(visitRows = listOf(row)),
                actions = NotificationCenterActions(onOpen = { opened += it.id }, onDelete = { deleted += it }),
                nowMillis = now,
                zone = zone,
            )
        }
        composeRule.onAllNodesWithText(text(R.string.notif_center_empty_title)).assertCountEquals(0)
        composeRule.onNodeWithText(sentenceOf(row)).performTouchInput { longClick() }
        composeRule.onNodeWithText(text(R.string.notif_center_delete_a11y)).performClick()
        assertEquals(listOf("v1"), deleted)
        composeRule.onNodeWithText(sentenceOf(row)).performClick()
        assertEquals(listOf("v1"), opened)
    }

    @Test
    fun eachLine_isOneElementForTheScreenReader_unreadSaidInWords_andReadOnesSayNothing() {
        val unread = visitRow("v1", departure, ago = 3_600_000L)
        val read = visitRow("v2", silence, ago = 2 * 3_600_000L, name = "Amandine", unread = false)
        show {
            NotificationCenterScreen(
                NotificationCenterUiState(visitRows = listOf(unread, read)),
                NotificationCenterActions(),
                now,
                zone,
            )
        }
        composeRule
            .onNodeWithText(sentenceOf(unread))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    text(R.string.notif_center_unread_a11y),
                ),
            ).assertHeightIsAtLeast(48.dp)
        composeRule
            .onNodeWithText(sentenceOf(read))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
        composeRule
            .onNodeWithText(
                text(R.string.notif_c1_title),
            ).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun theGroup_staysVisible_whenTheNotificationsAreOffInThePhone_decisionQ4() {
        val row = visitRow("v1", departure, ago = 3_600_000L)
        show {
            NotificationCenterScreen(
                state = NotificationCenterUiState(banner = CenterBanner.OFF, visitRows = listOf(row)),
                actions = NotificationCenterActions(),
                nowMillis = now,
                zone = zone,
            )
        }
        composeRule.onNodeWithText(sentenceOf(row)).assertIsDisplayed()
        // The band of the centre stays as it was: the calm card and its one button.
        composeRule.onNodeWithText(text(R.string.notif_center_enable_action)).assertIsDisplayed()
        // The menu can act on the group.
        composeRule.onNodeWithContentDescription(text(R.string.notif_center_menu_a11y)).performClick()
        composeRule.onNodeWithText(text(R.string.notif_center_clear_all)).assertIsEnabled()
    }

    @Test
    fun theGroup_staysVisible_whenTheGeneralSwitchOfTheAppIsOff() {
        val row = visitRow("v1", departure, ago = 3_600_000L)
        show {
            NotificationCenterScreen(
                state = NotificationCenterUiState(banner = CenterBanner.DISABLED, visitRows = listOf(row)),
                actions = NotificationCenterActions(),
                nowMillis = now,
                zone = zone,
            )
        }
        composeRule.onNodeWithText(text(R.string.notif_center_off_title)).assertIsDisplayed()
        composeRule.onNodeWithText(sentenceOf(row)).assertIsDisplayed()
    }

    @Test
    fun withTheLargestSystemText_theLinesWrapAndStayReadable() {
        val row = visitRow("v1", silence, ago = 3_600_000L, name = "Marie-Charlotte")
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                MiffTheme {
                    Box(Modifier.fillMaxSize()) {
                        NotificationCenterScreen(
                            NotificationCenterUiState(visitRows = listOf(row)),
                            NotificationCenterActions(),
                            now,
                            zone,
                        )
                    }
                }
            }
        }
        shoot("notif-p1a-large-text")
        composeRule.onNodeWithText(sentenceOf(row)).assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText(text(R.string.notif_c1_limit)).assertIsDisplayed()
    }

    @Test
    fun theGroupOfAnAccountWithSeveralMiffs_namesTheMiffInASmallLine() {
        val base = visitRow("v1", departure, ago = 3_600_000L)
        val row =
            CenterRow(
                base.id,
                base.type,
                "Famille",
                base.createdAtMillis,
                true,
                visit = base.visit,
                visitName = base.visitName,
                miffId = base.miffId,
            )
        show {
            NotificationCenterScreen(
                NotificationCenterUiState(visitRows = listOf(row)),
                NotificationCenterActions(),
                now,
                zone,
            )
        }
        composeRule.onNodeWithText("Miff « Famille »", substring = true).assertExists()
    }
}
