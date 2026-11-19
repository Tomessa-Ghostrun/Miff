package com.tomessa.miff.ui.alert

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The Miff the alert goes to is the one selected at the INSTANT the 5 seconds are reached, not the one that was there when the viewfinder appeared (review of the iOS twin of #275, c): if the
 * Feed changes Miff while the Update is on screen, the hold must send to the NEW Miff, even if two Miffs carry the same name (the identifier decides, never the name).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en", application = android.app.Application::class)
class AlertTargetTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun theHoldSendsToTheMiffSelectedWhenTheFiveSecondsAreReached_notToTheOneOfTheStart() {
        val sentTo = mutableListOf<String>()
        var selected by mutableStateOf("miffA")
        rule.setContent {
            val state = remember { AlertHoldState() }
            // exactly the shape of the Update: the gesture's callback reads the CURRENT target at the moment it fires
            val target = selected
            Box(
                Modifier
                    .size(80.dp)
                    .testTag("shutter")
                    .alertHoldGesture(state, onDone = { sentTo += target }),
            )
        }
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("shutter").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(2_000)
        // the Feed changes Miff during the hold (two Miffs may even carry the same name)
        selected = "miffB"
        rule.mainClock.advanceTimeBy(4_000)
        assertEquals(listOf("miffB"), sentTo)
    }

    @Test
    fun theTriggerOfTheShellReadsTheCurrentActiveMiff_atTheMomentOfTheHold() {
        val shell = File("src/main/java/com/tomessa/miff/ui/main/MainShell.kt").readText().replace(Regex("\\s+"), " ")
        // the lambda reads `ready?.activeId` when it runs (by identifier), never a value kept from an earlier composition
        assertEquals(true, shell.contains("onTrigger = { val origin = ready?.activeId"))
    }
}
