package com.tomessa.miff.ui.update

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The description field of the preview (ticket description-update, criteria S1 to S5, S7, S8, A1, A2, A5): the placeholder, the cleaning and the limit as the text is typed
 * or pasted, the counter only from 120, the phrase of the limit, a name for the screen readers, a touch area of 48 dp.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en", application = android.app.Application::class)
class DescriptionFieldTest {
    @get:Rule
    val rule = createComposeRule()

    private val heard = mutableListOf<String>()

    private fun show(
        initial: String = "",
        enabled: Boolean = true,
    ) {
        heard.clear()
        rule.setContent { DescriptionField(initial = initial, enabled = enabled, onTextChange = { heard += it }) }
    }

    private fun field() = rule.onNode(hasSetTextAction())

    private fun cp(vararg codes: Int): String = codes.joinToString("") { String(Character.toChars(it)) }

    @Test
    fun theFieldShowsThePlaceholder_untilTheFirstCharacter_andAgainWhenEmptied() {
        show()
        rule.onNodeWithText("Add a description").assertIsDisplayed()
        field().performTextInput("a")
        rule.onNodeWithText("Add a description").assertDoesNotExist()
        field().performTextReplacement("")
        rule.onNodeWithText("Add a description").assertIsDisplayed()
    }

    @Test
    fun theFieldIsNamedForAScreenReader_withWhatTheLovedOnesWillSee() {
        show()
        val description = field().fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
        assertEquals(
            listOf("Photo description (optional). Your loved ones will see it with your photo for 24 hours."),
            description,
        )
    }

    @Test
    fun theTouchAreaIsAtLeast48dp() {
        show()
        field().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun whatIsTypedIsReported_cleaned() {
        show()
        field().performTextInput("Bien arrivé")
        assertEquals("Bien arrivé", heard.last())
        field().assertTextEquals("Bien arrivé")
    }

    @Test
    fun aPasteWithLineBreaksAndInvisibleCharacters_isCleaned_aLineBreakBecomesASpace() {
        show()
        field().performTextInput("a\nb\tc" + cp(0x200B) + "d" + cp(0x202E) + "e")
        assertEquals("a b cde", heard.last())
        assertTrue("no line break ever typed", heard.none { it.contains('\n') })
    }

    @Test
    fun aTextOverTheLimit_isCutTo140_withoutBreakingAnEmoji() {
        show()
        field().performTextInput("a".repeat(139) + cp(0x1F600))
        assertEquals("a".repeat(139), heard.last())
        field().performTextReplacement("b".repeat(300))
        assertEquals(140, heard.last().length)
    }

    @Test
    fun theCounterIsNotThereBefore120_andShowsFrom120() {
        show()
        field().performTextInput("a".repeat(119))
        rule.onNodeWithText("119/140").assertDoesNotExist()
        field().performTextReplacement("a".repeat(120))
        rule.onNodeWithText("120/140").assertIsDisplayed()
        // It goes away again under the threshold.
        field().performTextReplacement("a".repeat(100))
        rule.onNodeWithText("100/140").assertDoesNotExist()
    }

    @Test
    fun theCounterIsSpokenWithItsUnit_neverJustTwoNumbers() {
        show()
        field().performTextInput("a".repeat(125))
        val spoken =
            rule
                .onNodeWithText("125/140")
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.ContentDescription)
        assertEquals(listOf("125 of 140 characters"), spoken)
    }

    @Test
    fun atTheLimit_aCalmPhraseAppears_notAnError() {
        show()
        field().performTextInput("a".repeat(139))
        rule.onNodeWithText("Limit of 140 characters reached.").assertDoesNotExist()
        field().performTextInput("a")
        rule.onNodeWithText("140/140").assertIsDisplayed()
        rule.onNodeWithText("Limit of 140 characters reached.").assertIsDisplayed()
    }

    @Test
    fun aTextPastedBeyondTheLimit_alsoSaysTheLimitWasReached() {
        show()
        field().performTextInput("a".repeat(139) + cp(0x1F600))
        // 139 kept (the emoji did not fit): the person is told why their text was shortened.
        rule.onNodeWithText("Limit of 140 characters reached.").assertIsDisplayed()
    }

    @Test
    fun aDescriptionAlreadyThere_isShownAgain_whenTheScreenIsRebuilt() {
        show(initial = "Déjà écrit")
        field().assertTextEquals("Déjà écrit")
        rule.onNodeWithText("Add a description").assertDoesNotExist()
    }

    @Test
    fun whileThePhotoLeaves_theFieldCannotBeEdited() {
        show(initial = "Fixe", enabled = false)
        // A field that cannot be edited has no "set text" action any more: it is found by its text.
        rule.onNodeWithText("Fixe").assertIsNotEnabled()
    }
}
