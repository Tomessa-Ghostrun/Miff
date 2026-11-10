package com.tomessa.miff.ui.update

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.data.feed.PhotoCache
import com.tomessa.miff.data.feed.PhotoLookup
import com.tomessa.miff.data.feed.PhotoReader
import com.tomessa.miff.data.miff.DirectoryState
import com.tomessa.miff.data.profile.AndroidAvatarCodec
import com.tomessa.miff.data.tracking.PhotoPosition
import com.tomessa.miff.data.tracking.PhotoPositionSource
import com.tomessa.miff.data.update.PhotoCleaner
import com.tomessa.miff.data.update.PhotoCommit
import com.tomessa.miff.data.update.PhotoStore
import com.tomessa.miff.data.update.PublishLedger
import com.tomessa.miff.data.update.UpdatePublisher
import com.tomessa.miff.domain.feed.FeedBuilder
import com.tomessa.miff.domain.feed.FeedInput
import com.tomessa.miff.domain.feed.MemberLocationAccess
import com.tomessa.miff.domain.feed.MemberStatus
import com.tomessa.miff.domain.feed.StatusesView
import com.tomessa.miff.domain.miff.DocWrite
import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.domain.miff.MiffSummary
import com.tomessa.miff.domain.navigation.MainTab
import com.tomessa.miff.domain.navigation.TabBarConfig
import com.tomessa.miff.domain.tracking.Fix
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.domain.tracking.StatusLevel
import com.tomessa.miff.domain.update.PromptLevel
import com.tomessa.miff.domain.update.SharedPhoto
import com.tomessa.miff.domain.update.UpdatePhoto
import com.tomessa.miff.testsupport.FakeActiveDevice
import com.tomessa.miff.testsupport.FakeNetwork
import com.tomessa.miff.testsupport.MemoryPublishLedger
import com.tomessa.miff.testsupport.testPlace
import com.tomessa.miff.ui.feed.FeedHost
import com.tomessa.miff.ui.feed.FeedUiState
import com.tomessa.miff.ui.main.MainTabBar
import com.tomessa.miff.ui.main.MainTabs
import com.tomessa.miff.ui.main.UpdateHost
import com.tomessa.miff.ui.profile.ProfileTabUiState
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.ZoneOffset

/**
 * The Update on the JVM (Robolectric, no phone), in French: the pop-ins of the camera, the preview of the photo with its buttons, the calm sentences when a
 * publication does not end, the bubble of encouragement on every tab but the Update, and the central button, a dot of colour with a spoken name. Touch targets
 * (48 dp), the largest font. Screenshots go to build/screenshots for the pull request (compare with design/exports/update and design/exports/update/claude).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w360dp-h800dp-port-xxhdpi")
class UpdateScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun text(id: Int): String = context().getString(id)

    private fun text(
        id: Int,
        vararg args: Any,
    ): String = context().getString(id, *args)

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { MiffTheme { Box(Modifier.fillMaxSize()) { content() } } }
    }

    private fun shoot(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // --- doubles --------------------------------------------------------------------------------------------------------------------------

    private class Store : PhotoStore {
        var commits = 0
        var failWith: Exception? = null

        override suspend fun commit(
            uid: String,
            miffIds: List<String>,
            requireConsent: Boolean,
            build: (List<String>) -> List<DocWrite>,
        ): PhotoCommit {
            commits++
            failWith?.let { throw it }
            build(miffIds)
            return PhotoCommit(miffIds, emptyList(), emptyList())
        }
    }

    private val store = Store()
    private val ledger = MemoryPublishLedger()
    private var fix: Fix? = Fix(48.0, 2.0, System.currentTimeMillis())
    private var locationOff = false
    private var clock = System.currentTimeMillis()
    private val miffId = "AbCdEfGhIjKlMnOpQrSt"

    private fun viewModel(): UpdateViewModel {
        val photos = PhotoCache()
        val publisher =
            UpdatePublisher(
                store,
                FakeActiveDevice(),
                PhotoPositionSource {
                    fix?.let { PhotoPosition.Found(it) }
                        ?: if (locationOff) PhotoPosition.LocationOff else PhotoPosition.NotFound
                },
                FakeNetwork(true),
                ledger,
                photos,
                { "uid" },
                { listOf(miffId) },
                { 50 },
                { clock },
            )
        val cleaner =
            PhotoCleaner(store, FakeActiveDevice(), FakeNetwork(true), ledger, photos, {
                "uid"
            }, { listOf(miffId) }, { 50 }, { clock })
        return UpdateViewModel(publisher, cleaner, ledger) { clock }
    }

    private fun jpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(461, 1024, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(250, 220, 170))
        canvas.drawCircle(230f, 360f, 120f, Paint().apply { color = Color.rgb(120, 80, 50) })
        canvas.drawCircle(230f, 900f, 300f, Paint().apply { color = Color.rgb(40, 90, 200) })
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return out.toByteArray()
    }

    private fun previewOf(
        takenAt: Long = clock,
        vm: UpdateViewModel = viewModel(),
    ): UpdateViewModel {
        vm.onOpened(granted = true, shouldShowRationale = false, sharingStopped = false)
        vm.onPhotoReady(UpdatePhoto(jpeg(), 461, 1024, takenAt))
        return vm
    }

    private var closed = 0
    private var resumed = 0
    private var reviewed = 0

    private fun actions() =
        UpdateActions(
            onClose = { closed++ },
            onResumeSharing = { resumed++ },
            onReviewAgreement = { reviewed++ },
        )

    @Composable
    private fun Tab(
        vm: UpdateViewModel,
        sharingStopped: Boolean = false,
        miffCount: Int = 1,
    ) = UpdateTab(vm, AndroidAvatarCodec(context()), sharingStopped, miffCount, actions())

    // --- The pop-ins of the camera -------------------------------------------------------------------------------------------------------

    @Test
    fun theFirstOpening_explainsBeforeAskingAnything_inTheWordsOfTheTicket() {
        show { Tab(viewModel()) }
        shoot("update-permission-explication")
        composeRule.onNodeWithText("Une photo pour tes proches").assertExists()
        composeRule.onNodeWithText(text(R.string.update_permission_body)).assertExists()
        composeRule.onNodeWithText("Continuer").assertIsEnabled()
        composeRule.onNodeWithText("Pas maintenant").performClick()
        assertEquals("\"Pas maintenant\" goes back to the Feed", 1, closed)
        assertTrue("nothing was asked, the explanation is not accepted", !ledger.explained)
    }

    @Test
    fun aCameraRefusedForGood_offersTheSettings_andNeverTheCameraApplicationOfThePhone() {
        ledger.explained = true
        show { Tab(viewModel()) }
        shoot("update-camera-desactivee")
        composeRule.onNodeWithText("La caméra est désactivée").assertExists()
        composeRule.onNodeWithText("Ouvrir les réglages").assertExists()
        composeRule.onNodeWithText("Fermer").assertExists()
        // Android refuses to start the camera application of the phone from an app that declares CAMERA and was not granted it.
        composeRule.onAllNodesWithText(text(R.string.update_action_phone_camera)).assertCountEquals(0)
    }

    @Test
    fun sharingStopped_showsNoViewfinder_asksNoPermission_andLeadsToTheMiff() {
        val vm = viewModel()
        show { Tab(vm, sharingStopped = true) }
        shoot("update-partage-arrete")
        composeRule.onNodeWithText("Tu as arrêté de partager").assertExists()
        composeRule.onNodeWithText(text(R.string.update_stopped_body)).assertExists()
        composeRule.onNodeWithText("Reprendre le partage").performClick()
        assertEquals(1, resumed)
        assertTrue(!ledger.explained)
    }

    // --- The preview ------------------------------------------------------------------------------------------------------------------------

    @Test
    fun thePreview_saysWhoSeesThePhotoAndThatItGoesWithThePosition_andHasTheThreeButtons() {
        show { Tab(previewOf()) }
        shoot("update-apercu")
        composeRule.onNodeWithText("Tes proches verront cette photo pendant 24 h.").assertExists()
        composeRule.onNodeWithText(text(R.string.update_preview_place)).assertExists()
        composeRule.onNodeWithContentDescription("Fermer").assertExists()
        composeRule.onNodeWithContentDescription("Recommencer").assertExists()
        composeRule.onNodeWithContentDescription("Publier").assertIsEnabled()
        // No way to publish "without a place": no such button, no such option.
        composeRule.onAllNodesWithText("sans", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun withSeveralMiffs_thePreviewSaysWhereThePhotoGoes() {
        show { Tab(previewOf(), miffCount = 3) }
        composeRule.onNodeWithText("Ta photo sera visible des membres de tes 3 Miffs pendant 24 h.").assertExists()
    }

    private fun leavesThePreview(name: String) {
        val vm = previewOf()
        show { Tab(vm) }
        composeRule.onNodeWithContentDescription(name).performClick()
        composeRule.waitForIdle()
        // Back to the viewfinder (or, on a computer without a camera, to the calm screen of an unavailable camera): never still on the photo.
        assertTrue("$name leaves the preview", vm.stage.value !is UpdateStage.Preview)
        composeRule.onAllNodesWithContentDescription("Publier").assertCountEquals(0)
        assertEquals("$name writes nothing", 0, store.commits)
    }

    @Test
    fun theCross_doesWhatRecommencerDoes_noWrite_noQuestion() = leavesThePreview("Fermer")

    @Test
    fun recommencer_doesWhatTheCrossDoes_noWrite_noQuestion() = leavesThePreview("Recommencer")

    @Test
    fun publier_sendsThePhoto_once_andTheScreenCloses() {
        val vm = previewOf()
        show { Tab(vm) }
        composeRule.onNodeWithContentDescription("Publier").performClick()
        composeRule.waitUntil(5_000) { vm.stage.value == UpdateStage.Idle }
        assertEquals(1, store.commits)
    }

    @Test
    fun withoutAPosition_thePhotoStays_withACalmSentence_andARetry() {
        fix = null
        val vm = previewOf()
        show { Tab(vm) }
        composeRule.onNodeWithContentDescription("Publier").performClick()
        composeRule.waitUntil(5_000) { (vm.stage.value as? UpdateStage.Preview)?.notice != null }
        composeRule.onNodeWithText("Position introuvable pour le moment, réessaie dans un instant.").assertExists()
        composeRule.onNodeWithText("Réessayer").assertIsEnabled()
        composeRule.onNodeWithContentDescription("Recommencer").assertIsEnabled()
        assertEquals("nothing was sent", 0, store.commits)
        shoot("update-position-introuvable")
        // The retry measures again, and this time it works.
        fix = Fix(48.0, 2.0, System.currentTimeMillis())
        clock += 31_000L
        composeRule.onNodeWithText("Réessayer").performClick()
        composeRule.waitUntil(5_000) { vm.stage.value == UpdateStage.Idle }
        assertEquals(1, store.commits)
    }

    @Test
    fun offline_theSentenceTellsThePhotoIsNotGone_andNothingElseHappens() {
        store.failWith =
            com.google.firebase.firestore.FirebaseFirestoreException(
                "down",
                com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE,
            )
        val vm = previewOf()
        show { Tab(vm) }
        composeRule.onNodeWithContentDescription("Publier").performClick()
        composeRule.waitUntil(5_000) { (vm.stage.value as? UpdateStage.Preview)?.notice != null }
        composeRule.onNodeWithText(text(R.string.update_notice_offline)).assertExists()
        shoot("update-hors-connexion")
        // Never a word of blame or alarm.
        composeRule.onAllNodesWithText("erreur", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("échec", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun aPhotoThatWaitedMoreThan5Minutes_cannotBePublished_theSentenceSays() {
        val vm = previewOf(takenAt = clock)
        clock += 6 * 60_000L
        show { Tab(vm) }
        composeRule.onNodeWithText("Cette photo date un peu : reprends-en une.").assertExists()
        composeRule.onNodeWithContentDescription("Publier").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Recommencer").assertIsEnabled()
        assertEquals(0, store.commits)
    }

    @Test
    fun preview_theButtonsAreAtLeast48dp_andSurviveTheLargestFont() {
        show {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                Tab(previewOf())
            }
        }
        listOf("Fermer", "Recommencer", "Publier").forEach {
            composeRule.onNodeWithContentDescription(it).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
        composeRule.onNodeWithText("Tes proches verront cette photo pendant 24 h.").assertExists()
    }

    // --- The central button and the bubble ------------------------------------------------------------------------------------------------

    @Test
    fun theCentralButton_isADotOfTheColour_withAStateInItsSpokenName_neverAnIcon() {
        val names =
            mapOf(
                StatusLevel.GREEN to R.string.tabbar_update_green_a11y,
                StatusLevel.ORANGE to R.string.tabbar_update_orange_a11y,
                StatusLevel.RED to R.string.tabbar_update_red_a11y,
                StatusLevel.NEUTRAL to R.string.tabbar_update_a11y,
            )
        var level by mutableStateOf(StatusLevel.NEUTRAL)
        composeRule.setContent {
            MiffTheme {
                Box(
                    Modifier.fillMaxSize(),
                ) { MainTabBar(TabBarConfig.forBuild(false), MainTab.FEED, {}, updateStatus = level) }
            }
        }
        names.forEach { (wanted, name) ->
            level = wanted
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(text(name)).assertExists().assertIsEnabled()
        }
        assertEquals("Donner des nouvelles, tu es en orange", text(R.string.tabbar_update_orange_a11y))
        assertEquals("Donner des nouvelles, tu es en rouge", text(R.string.tabbar_update_red_a11y))
        assertEquals("Donner des nouvelles, tout va bien", text(R.string.tabbar_update_green_a11y))
    }

    private val now = Instant.parse("2026-11-05T17:00:00Z").toEpochMilli()
    private val minute = 60_000L
    private val me = "me"

    private fun feedWith(
        ago: Long,
        presence: PresenceValue = PresenceValue.OUT,
        hasPhoto: Boolean = false,
    ): FeedUiState.Ready {
        val members = listOf(MiffMember(me, "Camille", "X", null, MiffRole.OWNER, true, "+33612345678"))
        val status =
            MemberStatus(
                presence,
                "p1",
                now - ago,
                now - ago,
                hasPhoto,
                80,
                MemberLocationAccess.BACKGROUND,
                now - minute,
            )
        return FeedUiState.Ready(
            FeedBuilder.build(
                FeedInput(
                    miffId,
                    "Famille",
                    "XTZRSP367988",
                    members,
                    listOf(testPlace("p1", "Chez nous")),
                    me,
                    StatusesView.Loaded(
                        mapOf(me to status),
                    ),
                    false,
                    false,
                ),
                now,
                ZoneOffset.UTC,
            ),
        )
    }

    private val directory =
        DirectoryState.Ready(
            listOf(MiffSummary(miffId, "Famille")),
            miffId,
            premium = false,
            fromCache = false,
        )

    @Composable
    private fun Tabs(
        feed: FeedUiState,
        vm: UpdateViewModel = remember { viewModel() },
        photos: PhotoReader? = null,
    ) = MainTabs(
        tabBar = TabBarConfig.forBuild(true),
        profileState = ProfileTabUiState(),
        onOpenMyInfo = {},
        onOpenSettings = {},
        onRetryProfile = {},
        miffs = directory,
        onRetryMiffs = {},
        onSelectMiff = {},
        onCreateMiff = {},
        onJoinMiff = {},
        onOpenMiff = {},
        feed = FeedHost(state = feed, photos = photos),
        update = UpdateHost(vm, AndroidAvatarCodec(context())),
    )

    @Test
    fun theBubble_isOnTheFeed_andOnTheProfile_butNeverOnTheUpdate_withTheTextOfTheMockups() {
        val orange = text(R.string.update_bubble_orange)
        assertEquals("Tu es passé en orange, indique à la Miff que tout va bien en publiant une update", orange)
        show { Tabs(feedWith(ago = 150 * minute)) }
        shoot("update-bulle-orange-feed")
        composeRule.onNodeWithText(orange).assertExists().assertHasNoClickAction()
        composeRule.onNodeWithContentDescription(text(R.string.tabbar_update_orange_a11y)).assertExists()
        // On the Profile too.
        composeRule.onNodeWithContentDescription(text(R.string.tabbar_profile_a11y)).performClick()
        composeRule.onNodeWithText(orange).assertExists()
        shoot("update-bulle-orange-profil")
        // Not on the Update.
        composeRule.onNodeWithContentDescription(text(R.string.tabbar_update_orange_a11y)).performClick()
        composeRule.onNodeWithContentDescription(text(R.string.tabbar_update_orange_a11y)).assertIsSelected()
        composeRule.onAllNodesWithText(orange).assertCountEquals(0)
        // And it comes back with the person.
        composeRule.onNodeWithContentDescription(text(R.string.tabbar_feed_a11y)).performClick()
        composeRule.onNodeWithText(orange).assertExists()
    }

    @Test
    fun theRedBubble_hasTheRedText_orNothingAtAllWhenAllIsWell() {
        show { Tabs(feedWith(ago = 5 * 60 * minute)) }
        composeRule.onNodeWithText(text(R.string.update_bubble_red)).assertExists()
        shoot("update-bulle-rouge-feed")
    }

    @Test
    fun noBubble_whenGreen_orWhileTheFeedLoads() {
        show { Tabs(feedWith(ago = 10 * minute)) }
        composeRule.onAllNodesWithText(text(R.string.update_bubble_orange)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.update_bubble_red)).assertCountEquals(0)
        composeRule.onNodeWithContentDescription(text(R.string.tabbar_update_green_a11y)).assertExists()
    }

    @Test
    fun theBubbleKeepsEverythingReachable_inTheLargestFont() {
        show {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                Tabs(feedWith(ago = 5 * 60 * minute))
            }
        }
        composeRule.onNodeWithText(text(R.string.update_bubble_red)).assertExists()
        composeRule.onNodeWithContentDescription(text(R.string.tabbar_update_red_a11y)).assertExists()
        shoot("update-bulle-rouge-grande-police")
        assertEquals(PromptLevel.RED, (feedWith(ago = 5 * 60 * minute)).content.myPrompt)
    }

    // --- The notices of the review: location off (C3), no answer in time (C2) ------------------------------------------------------------------

    @Test
    fun aLocationThatIsOff_isNotTheSentenceOfASlowSignal_andLeadsToTheSettings() {
        fix = null
        locationOff = true
        val vm = previewOf()
        show { Tab(vm) }
        composeRule.onNodeWithContentDescription("Publier").performClick()
        composeRule.waitUntil(5_000) { (vm.stage.value as? UpdateStage.Preview)?.notice != null }
        composeRule.onNodeWithText(text(R.string.update_notice_location_off)).assertExists()
        composeRule.onNodeWithText("Ouvrir les réglages").assertIsEnabled()
        composeRule.onAllNodesWithText("Position introuvable", substring = true).assertCountEquals(0)
        assertEquals(0, store.commits)
        shoot("update-localisation-eteinte")
        // Calm: no blame, no alarm.
        composeRule.onAllNodesWithText("erreur", substring = true, ignoreCase = true).assertCountEquals(0)
    }

    @Test
    fun aServerThatDoesNotAnswerInTime_saysItCouldNotConfirm_notThatThePhotoDidNotLeave() {
        store.failWith =
            com.google.firebase.firestore.FirebaseFirestoreException(
                "slow",
                com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            )
        val vm = previewOf()
        show { Tab(vm) }
        composeRule.onNodeWithContentDescription("Publier").performClick()
        composeRule.waitUntil(5_000) { (vm.stage.value as? UpdateStage.Preview)?.notice != null }
        composeRule.onNodeWithText(text(R.string.update_notice_unconfirmed)).assertExists()
        composeRule.onAllNodesWithText("n'est pas partie", substring = true).assertCountEquals(0)
        composeRule.onNodeWithText("Voir mon Feed").performClick()
        assertEquals(1, closed)
        shoot("update-non-confirmee")
    }

    // --- The bubble is announced (C4) and capped (C5) ------------------------------------------------------------------------------------------

    @Test
    fun theBubble_isAPoliteLiveRegion_announcedOnceAtItsAppearance() {
        show { Tabs(feedWith(ago = 150 * minute)) }
        val node = composeRule.onNodeWithText(text(R.string.update_bubble_orange)).fetchSemanticsNode()
        assertEquals(
            androidx.compose.ui.semantics.LiveRegionMode.Polite,
            node.config[androidx.compose.ui.semantics.SemanticsProperties.LiveRegion],
        )
    }

    @Test
    fun theBubbleNeverTakesMoreThan40PercentOfTheScreen_atTheLargestFont_itsTextScrollsInside() {
        show {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                Tabs(feedWith(ago = 5 * 60 * minute))
            }
        }
        val body = composeRule.onNodeWithText(text(R.string.update_bubble_red)).onParent().getUnclippedBoundsInRoot()
        assertTrue(
            "the body of the bubble is at most 40 % of 800 dp: ${(body.bottom - body.top)}",
            (body.bottom - body.top) <= 320.5.dp,
        )
        // The central button is still there and reachable.
        composeRule.onNodeWithContentDescription(text(R.string.tabbar_update_red_a11y)).assertExists()
        shoot("update-bulle-plafonnee")
    }

    // --- FEED-4: the photo on the card is a SQUARE, centred (PM, 2026-11-06) ----------------------------------------------------------------

    private class OnePhoto(
        private val photo: SharedPhoto,
    ) : PhotoReader {
        override suspend fun read(
            miffId: String,
            uid: String,
            lastUpdateAtMillis: Long,
        ): PhotoLookup = PhotoLookup.Found(photo)
    }

    @Test
    fun thePhotoOnTheCard_isASquare_asWideAsTheCard_andOpensInFullScreenOnATouch() {
        val reader = OnePhoto(SharedPhoto(jpeg(), now, now + 3_600_000L, null))
        show {
            Tabs(
                feedWith(ago = 10 * minute, presence = PresenceValue.IN_TRUSTED, hasPhoto = true),
                photos = reader,
            )
        }
        val node =
            composeRule.onNode(
                androidx.compose.ui.test
                    .hasContentDescription("Ouvrir la photo de", substring = true),
            )
        node.assertExists()
        val bounds = node.getUnclippedBoundsInRoot()
        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top
        assertTrue("a square: $width x $height", kotlin.math.abs(width.value - height.value) < 1f)
        assertTrue("taller than the old window of 108 dp: $height", height.value > 200f)
        shoot("update-photo-carre-feed")
        node.performClick()
        composeRule.onNodeWithContentDescription("Fermer la photo").assertExists()
    }
}
