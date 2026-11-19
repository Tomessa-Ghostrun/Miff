package com.tomessa.miff.ui.alert

import com.tomessa.miff.data.push.PushMessageHandler
import com.tomessa.miff.domain.notification.CircleAlertPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The alert of danger, guarded on the sources (criteria AD16, AD33, AD43, AD45): the switch of the function is OFF in every build that can be distributed, nothing about an alert is
 * logged, sent as an event or copied, no permission "call" or "contacts" exists, and a message of the alert is ignored while the function is off.
 */
class AlertDangerGuardTest {
    private fun normalised(file: File): String = file.readText().replace(Regex("\\s+"), " ")

    private val gradle = normalised(File("build.gradle.kts"))
    private val main = File("src/main/java/com/tomessa/miff")

    private fun sources() = main.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun code(file: File) =
        file
            .readLines()
            .filter {
                val line = it.trimStart()
                !line.startsWith("*") && !line.startsWith("//") && !line.startsWith("/*")
            }.joinToString("\n")

    private val alertFiles =
        listOf(
            "domain/alert/AlertRules.kt",
            "domain/alert/AlertHold.kt",
            "domain/alert/EmergencyCatalog.kt",
            "domain/notification/CircleAlert.kt",
            "data/alert/AlertStore.kt",
            "data/alert/AlertController.kt",
            "data/alert/AlertLocalStore.kt",
            "ui/alert/AlertHoldGesture.kt",
            "ui/alert/AlertComponents.kt",
            "ui/alert/AlertSentScreen.kt",
            "ui/alert/AlertHost.kt",
            "ui/alert/AlertViewModel.kt",
        ).map { File(main, it) }

    // ---- the switch -----------------------------------------------------------------------------------------------------------------

    @Test
    fun theFlagIsFalseByDefault_trueInDebugOnly_andARelease_refusesTrueUnlessAskedOnPurpose() {
        assertTrue(gradle.contains("""buildConfigField("boolean", "ALERT_DANGER", alertDangerProperty ?: "false")"""))
        val debugBlock = gradle.substringAfter("debug {").substringBefore("release {")
        assertTrue(debugBlock.contains("""alertDangerProperty ?: "true""""))
        val restOfBuildTypes = gradle.substringAfter("release {").substringBefore("compileOptions")
        assertFalse(restOfBuildTypes.contains("""alertDangerProperty ?: "true""""))
        val guard = gradle.substringAfter("val alertOnInRelease")
        assertTrue(guard.contains("miff.allowAlertDangerInRelease"))
        assertTrue(
            guard.contains("preReleaseBuild") && guard.contains("preReleaseLikeBuild") &&
                guard.contains("GradleException"),
        )
    }

    @Test
    fun everyUseOfTheFunctionOutsideItsPackagesSitsBehindTheFlag() {
        val container = normalised(File(main, "AppContainer.kt"))
        assertTrue(container.contains("alertsEnabled = BuildConfig.ALERT_DANGER"))
        assertTrue(container.contains("onCircleAlert = if (BuildConfig.ALERT_DANGER) {"))
        val shell = normalised(File(main, "ui/main/MainShell.kt"))
        assertTrue(shell.contains("if (BuildConfig.ALERT_DANGER) { viewModel("))
        // the Feed listens to nothing when the function is off
        assertTrue(shell.contains("alerts = if (BuildConfig.ALERT_DANGER) container.alertStore else AlertFeed.None"))
    }

    // ---- privacy and security ----------------------------------------------------------------------------------------------------------

    @Test
    fun nothingAboutAnAlertIsLogged_sentAsAnEvent_reportedInACrash_orCopied() {
        val found =
            alertFiles.filter { file ->
                Regex(
                    """\bLog\.[a-z]\(|\bprintln\(|\bprintStackTrace\b|\bTimber\b|Crashlytics|recordException|setCustomKey|FirebaseAnalytics|logEvent|AnalyticsEvent|AnalyticsTracker|ClipboardManager|ClipData|setPrimaryClip""",
                ).containsMatchIn(code(file))
            }
        assertEquals(emptyList<File>(), found)
    }

    @Test
    fun theAlertNeverTouchesAPosition_exceptThroughTheDetectionOfTheApp() {
        val found =
            alertFiles.filter { file ->
                Regex(
                    """LocationServices|FusedLocation|getCurrentLocation|lastLocation|ACCESS_FINE|ACCESS_COARSE|GeoPoint|\blatitude\b|\blongitude\b""",
                ).containsMatchIn(code(file))
            }
        assertEquals(emptyList<File>(), found)
    }

    @Test
    fun noPermissionToCallOrToReadContacts_andNeverACall() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("CALL_PHONE"))
        assertFalse(manifest.contains("READ_CONTACTS"))
        assertFalse(manifest.contains("USE_FULL_SCREEN_INTENT"))
        val calls = sources().filter { Regex("""ACTION_CALL\b""").containsMatchIn(code(it)) }
        assertEquals(emptyList<File>(), calls)
        // the two ways to a number go through the dialler
        assertTrue(code(File(main, "ui/feed/FeedIntents.kt")).contains("Intent.ACTION_DIAL"))
    }

    @Test
    fun theAlertNotificationCarriesNoPositionNoPlaceNoMiffName() {
        val text =
            code(
                File(main, "data/notification/NotificationPresenter.kt"),
            ).substringAfter("override fun showCircleAlert").substringBefore("private companion object")
        assertFalse(
            Regex("""miffName|miff\.name|latitude|longitude|\bplace""", RegexOption.IGNORE_CASE).containsMatchIn(text),
        )
        assertTrue(text.contains("NotificationChannels.ALERTS"))
        assertFalse(text.contains("setFullScreenIntent"))
    }

    @Test
    fun theChannelOfTheAlertIsHighFromTheFirstVersion_andHasNoQuietVersion() {
        val text = normalised(File(main, "data/notification/NotificationPlatform.kt"))
        assertTrue(
            text.contains(
                """ALERTS, R.string.android_notif_channel_alerts, R.string.android_notif_channel_alerts_desc, NotificationManager.IMPORTANCE_HIGH""",
            ),
        )
        assertFalse(text.contains("ALERTS_QUIET"))
    }

    // ---- colours: small white text on the banner must reach AA (4.5:1)

    private fun luminance(c: androidx.compose.ui.graphics.Color): Double {
        fun lin(v: Float) = if (v <= 0.03928f) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        return 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
    }

    private fun contrast(
        a: androidx.compose.ui.graphics.Color,
        b: androidx.compose.ui.graphics.Color,
    ): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun theBannerCarriesSmallWhiteText_soItsRedReachesAA_andTheBigButtonReachesTheLargeTextRatio() {
        assertTrue(contrast(com.tomessa.miff.ui.theme.colorOnAlert, com.tomessa.miff.ui.theme.colorAlertBanner) >= 4.5)
        // the big bold "Appeler" (22 sp bold) is large text: 3:1
        assertTrue(contrast(com.tomessa.miff.ui.theme.colorOnAlert, com.tomessa.miff.ui.theme.colorAlertFill) >= 3.0)
    }

    // ---- a message of the alert while the function is off ----------------------------------------------------------------------------

    @Test
    fun aMessageOfTheAlert_isIgnoredWhileTheFunctionIsOff_andHandedOverWhenItIsOn() {
        val data =
            mapOf(
                "type" to "circle_alert",
                "event" to "start",
                "miffId" to "m1",
                "authorUid" to "u1",
                "firstName" to "Thomas",
            )
        val off = PushMessageHandler(currentUid = { "me" }, onCircleNews = { _, _ -> })
        assertFalse(off.handle(data))
        val received = mutableListOf<CircleAlertPayload>()
        val on =
            PushMessageHandler(currentUid = { "me" }, onCircleNews = { _, _ -> }, onCircleAlert = { _, alert ->
                received +=
                    alert
            })
        assertTrue(on.handle(data))
        assertEquals(1, received.size)
        assertNotNull(received.single().miffId)
        // nobody signed in: nothing
        val nobody =
            PushMessageHandler(currentUid = { null }, onCircleNews = { _, _ -> }, onCircleAlert = { _, alert ->
                received +=
                    alert
            })
        assertFalse(nobody.handle(data))
        assertEquals(1, received.size)
    }

    @Test
    fun deletingOrLeavingAMiffTellsTheAlertController_theCreatorsOwnDeletionIncluded() {
        val container = normalised(File(main, "AppContainer.kt"))
        val leftBlock =
            container
                .substringAfter(
                    "suspend fun onMiffLeft(miffId: String) {",
                ).substringBefore("miffDirectory.expectDeparture")
        assertTrue(leftBlock.contains("if (BuildConfig.ALERT_DANGER) alertGoneRelay.miffGone(miffId)"))
        // the repository tells the relay too, never the controller directly (it lives on the main thread)
        assertTrue(container.contains("onAlertGone = { miffId -> alertGoneRelay.miffGone(miffId) }"))
        // the deletion of a Miff by its creator ends in onMiffLeft
        assertTrue(container.contains("onDeleted = { miffId -> onMiffLeft(miffId) }"))
    }
}
