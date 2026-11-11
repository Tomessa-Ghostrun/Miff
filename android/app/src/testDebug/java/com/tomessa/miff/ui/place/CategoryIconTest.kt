package com.tomessa.miff.ui.place

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.domain.place.PlaceCategory
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The category of a place as an EMOJI (decision of the PM, 2026-11-10): drawn as text, decorative for a screen reader (the name of the category is read,
 * never the emoji), the same size at the largest font, and a text fallback (the first letter of the name) when the phone's font cannot draw the emoji.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w390dp-h844dp-port-xxhdpi")
class CategoryIconTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun show(
        support: EmojiSupport = EmojiSupport { true },
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            MiffTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalEmojiSupport provides support,
                    LocalDensity provides Density(density.density, fontScale),
                ) { content() }
            }
        }
    }

    @Test
    fun everyCategory_isDrawnAsItsEmoji() {
        show { Column { PlaceCategory.entries.forEach { CategoryIcon(it, size = 44.dp) } } }
        PlaceCategory.entries.forEach { category ->
            composeRule.onNodeWithText(category.emoji, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test
    fun theEmoji_isDecorative_neverReadByAScreenReader() {
        show { Row { PlaceCategory.entries.forEach { CategoryIcon(it, size = 28.dp) } } }
        PlaceCategory.entries.forEach { category ->
            composeRule.onAllNodesWithText(category.emoji).assertCountEquals(0)
        }
    }

    @Test
    fun aPlaceWithoutACategory_hasAPin_notAnEmoji_andNotOther() {
        show { CategoryIcon(null, size = 44.dp) }
        PlaceCategory.entries.forEach { category ->
            composeRule.onAllNodesWithText(category.emoji, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @Test
    fun aFontThatCannotDrawTheEmoji_givesTheFirstLetterOfTheName_neverASquare() {
        show(support = { false }) {
            Column {
                CategoryIcon(PlaceCategory.SCHOOL, size = 44.dp)
                CategoryIcon(PlaceCategory.HOME, size = 44.dp)
            }
        }
        // "École" -> "É", "Maison" -> "M" (French qualifiers).
        composeRule.onNodeWithText("É", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("M", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onAllNodesWithText(PlaceCategory.SCHOOL.emoji, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun aFontThatCannotDrawOnlyTheFamily_fallsBackForThatOneAlone() {
        show(support = { it != PlaceCategory.RELATIVE.emoji }) {
            Column {
                CategoryIcon(PlaceCategory.HOME, size = 44.dp)
                CategoryIcon(PlaceCategory.RELATIVE, size = 44.dp)
            }
        }
        composeRule.onNodeWithText(PlaceCategory.HOME.emoji, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onAllNodesWithText(PlaceCategory.RELATIVE.emoji, useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithText("C", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theLargestFont_doesNotGrowTheBox_theEmojiStaysInside() {
        show(fontScale = 2f) { CategoryIcon(PlaceCategory.SPORT, size = 28.dp) }
        val box =
            composeRule
                .onNodeWithText(
                    PlaceCategory.SPORT.emoji,
                    useUnmergedTree = true,
                ).getUnclippedBoundsInRoot()
        assertEquals(
            "the emoji is smaller than its 28 dp box",
            true,
            box.right - box.left <= 28.dp && box.bottom - box.top <= 28.dp,
        )
    }
}
