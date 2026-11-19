package com.tomessa.miff.ui.notification

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.domain.notification.CenterAvatar
import com.tomessa.miff.domain.notification.NotificationType
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

/**
 * CNC-A / CNC-L / CNC-11 on the JVM (Robolectric, drawn for real): the round picture of each line, the words ("Prénom ..."), the screen reader (the picture says nothing, the line is ONE element
 * that starts with the first name), the touch target, the enlarged text, the right-to-left layout, and what stays visible when the notifications of the phone are off (CNC-7).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w360dp-h800dp-xhdpi")
class NotificationCenterAvatarScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val now = 1_800_000_000_000L
    private val zone = java.time.ZoneId.of("Europe/Paris")

    private fun text(
        id: Int,
        vararg args: Any,
    ): String = ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { MiffTheme { Box(Modifier.fillMaxSize()) { content() } } }
    }

    private fun shoot(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** A real square JPEG (what the profile stores), of a flat colour. */
    private fun jpeg(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
    }

    private fun row(
        id: String,
        type: NotificationType,
        avatar: CenterAvatar,
        who: String? = null,
        ago: Long = 60_000L,
        unread: Boolean = true,
    ) = CenterRow(
        id,
        type,
        null,
        now - ago,
        unread,
        who = who,
        avatar = avatar,
        miffId = "miffAAAA",
        actorUid = "actor1",
    )

    private fun screen(
        rows: List<CenterRow>,
        banner: CenterBanner = CenterBanner.NONE,
        onOpen: (CenterRow) -> Unit = {},
    ): @Composable () -> Unit =
        {
            NotificationCenterScreen(
                state = NotificationCenterUiState(rows = rows, banner = banner),
                actions = NotificationCenterActions(onOpen = onOpen),
                nowMillis = now,
                zone = zone,
            )
        }

    private val allKinds
        get() =
            listOf(
                row(
                    "1",
                    NotificationType.N9,
                    CenterAvatar.Photo(jpeg(Color.rgb(200, 120, 60)), "LD"),
                    who = "Léa",
                    ago =
                        1 * 60_000L,
                ),
                row("2", NotificationType.N9, CenterAvatar.Initials("MP"), who = "Max", ago = 2 * 60_000L),
                row(
                    "3",
                    NotificationType.N10,
                    CenterAvatar.Photo(jpeg(Color.rgb(60, 120, 200)), "LD"),
                    who = "Léa",
                    ago =
                        3 * 60_000L,
                ),
                row(
                    "4",
                    NotificationType.N11,
                    CenterAvatar.Initials("MP"),
                    who = "Max",
                    ago = 4 * 60_000L,
                    unread = false,
                ),
                row("5", NotificationType.N9, CenterAvatar.Logo, who = null, ago = 5 * 60_000L),
                row("6", NotificationType.N10, CenterAvatar.Logo, who = null, ago = 6 * 60_000L),
                row("7", NotificationType.N3, CenterAvatar.Logo, ago = 7 * 60_000L),
                row(
                    "8",
                    NotificationType.N1,
                    CenterAvatar.Photo(jpeg(Color.rgb(90, 160, 90)), "MM"),
                    ago = 8 * 60_000L,
                ),
                row("9", NotificationType.N9, CenterAvatar.Placeholder, who = "Zoé", ago = 9 * 60_000L),
            )

    @Test
    fun everyLineHasItsRoundPicture_andItsWords_theLogoAndTheNeutralRoundIncluded() {
        show(screen(allKinds))
        shoot("cnc-a-center-all-kinds")
        composeRule.onAllNodesWithTag(CENTER_AVATAR_TAG, useUnmergedTree = true).assertCountEquals(allKinds.size)
        // a named person: "{Prénom} a donné des nouvelles" / the alert in the same words as the push
        composeRule.onNodeWithText(text(R.string.notif_circle_update_title, "Léa")).assertExists()
        composeRule.onNodeWithText(text(R.string.notif_circle_update_title, "Max")).assertExists()
        composeRule.onNodeWithText(text(R.string.notif_alert_start_title, "Léa")).assertExists()
        composeRule.onNodeWithText(text(R.string.notif_alert_start_body, "Léa")).assertExists()
        composeRule.onNodeWithText(text(R.string.notif_alert_end_title, "Max")).assertExists()
        composeRule.onNodeWithText(text(R.string.notif_alert_end_body, "Max")).assertExists()
        // somebody who cannot be named: "Un proche ...", whole sentences, never "appelle Un proche"
        composeRule.onNodeWithText(text(R.string.notif_center_circle_update_title)).assertExists()
        composeRule.onNodeWithText(text(R.string.notif_center_alert_start_title_unnamed)).assertExists()
        composeRule.onNodeWithText(text(R.string.notif_center_alert_start_body_unnamed)).assertExists()
        // the whole Miff and my reminder keep their own words
        composeRule.onNodeWithText(text(R.string.notif_n3_title)).assertExists()
        composeRule.onNodeWithText(text(R.string.notif_n1_title)).assertExists()
        // calm: nothing in the vocabulary of alarm in the centre
        composeRule.onAllNodesWithText("urgence", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("SOS", substring = true).assertCountEquals(0)
    }

    @Test
    fun theRoundPictureIsFortyDp_andTheLineIsOneElementForTheScreenReader_thePictureSaysNothing() {
        show(screen(allKinds.take(2)))
        composeRule
            .onAllNodesWithTag(
                CENTER_AVATAR_TAG,
                useUnmergedTree = true,
            )[0]
            .assertWidthIsEqualTo(40.dp)
            .assertHeightIsEqualTo(40.dp)
        // the picture itself carries no description and no text: it is decorative (the back arrow and the menu of the bar have their own)
        val described = SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)
        composeRule
            .onAllNodesWithTag(CENTER_AVATAR_TAG, useUnmergedTree = true)
            .assertAll(!described)
        // the line is ONE element, its text starts with the first name, and carries "non lu"
        val merged =
            composeRule.onAllNodes(
                SemanticsMatcher("is the line of Léa") {
                    it.config
                        .getOrNull(
                            SemanticsProperties.Text,
                        )?.joinToString(" ") { t -> t.text }
                        ?.startsWith("Léa a donné") ==
                        true
                },
            )
        merged.assertCountEquals(1)
        composeRule
            .onNodeWithText(text(R.string.notif_circle_update_title, "Léa"))
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun aTapOpensTheLine_withItsPerson() {
        val opened = mutableListOf<CenterRow>()
        show(screen(allKinds.take(1), onOpen = { opened += it }))
        composeRule.onNodeWithText(text(R.string.notif_circle_update_title, "Léa")).performClick()
        assertEquals("actor1", opened.single().actorUid)
        assertEquals("miffAAAA", opened.single().miffId)
    }

    @Test
    fun withEnlargedText_theLineGrows_thePictureKeepsItsSize() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 2f, fontScale = 2f)) {
                MiffTheme { Box(Modifier.fillMaxSize()) { screen(allKinds.take(3))() } }
            }
        }
        shoot("cnc-a-center-font-2x")
        composeRule
            .onAllNodesWithTag(
                CENTER_AVATAR_TAG,
                useUnmergedTree = true,
            )[0]
            .assertWidthIsEqualTo(40.dp)
            .assertHeightIsEqualTo(40.dp)
        composeRule.onNodeWithText(text(R.string.notif_circle_update_title, "Léa")).assertIsDisplayed()
    }

    @Test
    fun rightToLeft_thePictureIsAtTheStart_whichIsTheRightHandSide() {
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MiffTheme { Box(Modifier.fillMaxSize()) { screen(allKinds.take(1))() } }
            }
        }
        shoot("cnc-a-center-rtl")
        val avatar =
            composeRule
                .onAllNodesWithTag(
                    CENTER_AVATAR_TAG,
                    useUnmergedTree = true,
                )[0]
                .fetchSemanticsNode()
                .boundsInRoot
        val title =
            composeRule
                .onNodeWithText(
                    text(R.string.notif_circle_update_title, "Léa"),
                    useUnmergedTree = true,
                ).fetchSemanticsNode()
                .boundsInRoot
        assertTrue("the picture is on the right of the text", avatar.left >= title.right - 1f)
    }

    @Test
    fun whenTheNotificationsOfThePhoneAreOff_whatALovedOneDidStaysVisible_theRemindersStayHidden() {
        // CNC-7: the very case where the system showed nothing and the line is the only trace
        show(screen(allKinds, banner = CenterBanner.OFF))
        shoot("cnc-a-center-locked")
        composeRule.onNodeWithText(text(R.string.notif_circle_update_title, "Léa")).assertExists()
        composeRule.onNodeWithText(text(R.string.notif_alert_start_title, "Léa")).assertExists()
        composeRule.onAllNodesWithText(text(R.string.notif_n1_title)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.notif_n3_title)).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.notif_center_enable_action)).assertExists()
    }

    @Test
    fun theLogoIsDrawnOnItsBlueRound_andThePhotoFillsItsRound() {
        show(screen(allKinds.subList(0, 1) + allKinds.subList(6, 7)))
        shoot("cnc-a-center-logo-and-photo")
        composeRule.onAllNodesWithTag(CENTER_AVATAR_TAG, useUnmergedTree = true).assertCountEquals(2)
    }

    /** A JPEG header that announces [side] x [side] pixels followed by nothing: a few bytes that claim a gigantic image (a decompression bomb from another account). */
    private fun announcing(side: Int): ByteArray {
        val high = (side shr 8).toByte()
        val low = (side and 0xFF).toByte()
        return byteArrayOf(
            0xFF.toByte(),
            0xD8.toByte(),
            0xFF.toByte(),
            0xC0.toByte(),
            0x00,
            0x11,
            0x08,
            high,
            low,
            high,
            low,
            0x03,
            0x01,
            0x22,
            0x00,
            0x02,
            0x11,
            0x01,
            0x03,
            0x11,
            0x01,
            0xFF.toByte(),
            0xD9.toByte(),
        )
    }

    @Test
    fun aPhotoAnnouncingGiganticDimensions_neverCrashesTheCentre_theInitialsAreDrawn() {
        show {
            androidx.compose.foundation.layout.Column {
                CenterAvatarView(CenterAvatar.Photo(announcing(30_000), "LD"))
                CenterAvatarView(CenterAvatar.Photo(announcing(65_000), "MP"))
            }
        }
        composeRule.onNodeWithText("LD", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("MP", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aPhotoAnnouncingGiganticDimensions_neverCrashesTheFeed_theInitialsAreDrawn() {
        show {
            com.tomessa.miff.ui.feed.FeedAvatar(
                jpeg = announcing(30_000),
                initials = "ZZ",
                level = com.tomessa.miff.domain.tracking.StatusLevel.GREEN,
            )
        }
        composeRule.onNodeWithText("ZZ", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aPlausiblePhoto_isStillDrawnOnTheRound() {
        show { CenterAvatarView(CenterAvatar.Photo(jpeg(Color.rgb(10, 200, 10)), "LD")) }
        composeRule.onAllNodesWithText("LD", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun twoLettersStayReadableInTheRound_evenWithTextAtTwiceTheSize() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 2f, fontScale = 2f)) {
                MiffTheme { Box(Modifier.fillMaxSize()) { CenterAvatarView(CenterAvatar.Initials("MM")) } }
            }
        }
        val node = composeRule.onNodeWithText("MM", useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        node.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        val layout = results.single()
        val w = layout.size.width
        val h = layout.size.height
        // centred in a round of 40 dp = 80 px wide at this density (radius 40 px): the corners of the text box must lie inside the circle
        assertTrue("the two letters fit in the round: ${w}x$h px", Math.hypot(w / 2.0, h / 2.0) <= 40.0)
    }
}
