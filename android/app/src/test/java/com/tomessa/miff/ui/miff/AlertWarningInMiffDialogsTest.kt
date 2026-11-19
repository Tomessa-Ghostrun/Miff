package com.tomessa.miff.ui.miff

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Stopping the sharing, leaving or deleting a Miff while an alert of mine is in it (review of #275, M4; ticket 12.2): the dialog says, calmly and before the person confirms, that the alert
 * will be removed and that the people of the Miff will not be told it ended. Nothing is added when there is no alert.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en", application = android.app.Application::class)
class AlertWarningInMiffDialogsTest {
    @get:Rule
    val rule = createComposeRule()

    private val warning =
        "You have an alert in progress in this Miff: it will be removed, " +
            "and your people won't be told it ended."

    @Test
    fun withAnAlertInThisMiff_theQuestionCarriesTheWarning() {
        rule.setContent {
            CompositionLocalProvider(LocalAlertInThisMiff provides true) { Text(withAlertWarning("Stop sharing?")) }
        }
        rule.onNodeWithText("Stop sharing?\n\n$warning").assertExists()
    }

    @Test
    fun withoutAnAlert_theQuestionIsUnchanged() {
        rule.setContent { Text(withAlertWarning("Stop sharing?")) }
        rule.onNodeWithText("Stop sharing?").assertExists()
    }

    @Test
    fun theThreeDialogsOfTheMiffScreenUseIt() {
        val text = File("src/main/java/com/tomessa/miff/ui/miff/MiffScreen.kt").readText().replace(Regex("\\s+"), " ")
        assertTrue(text.contains("withAlertWarning(stringResource(R.string.miff_sharing_stop_body))"))
        assertTrue(text.contains("withAlertWarning(stringResource(R.string.miff_leave_body))"))
        assertTrue(text.contains("withAlertWarning( if (others)"))
    }

    @Test
    fun theLeaveDialogOfTheConsentRenewalAlsoWarns() {
        val text =
            File(
                "src/main/java/com/tomessa/miff/ui/permission/PermissionScreens.kt",
            ).readText().replace(Regex("\\s+"), " ")
        assertTrue(text.contains("withAlertWarning(stringResource(R.string.miff_leave_body))"))
        assertTrue(text.contains("LocalAlertMiffId.current == miff.miffId"))
    }
}
