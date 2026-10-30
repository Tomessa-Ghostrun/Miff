package com.tomessa.miff.ui.onboarding

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.ui.theme.MiffTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The welcome carousel against the PM's mockups (design/exports/onboarding-original): the headline and the sentences
 * of the cards are EXACTLY the ones of the mockups, there is no invented caption, and the images are the crops of the
 * exports. Also writes screenshots (build/screenshots/welcome-*.png) to compare with the mockups.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w360dp-h800dp-port-xxhdpi")
class WelcomeCarouselTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** The title of the mockups, on two lines (the line break after "bien," is the one of the mockups). */
    private companion object {
        const val HEADLINE = "Savoir qu’ils vont bien,\nsans les déranger"
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun show(initialPage: Int) {
        composeRule.setContent {
            MiffTheme {
                Box(Modifier.fillMaxSize()) {
                    WelcomeCarouselScreen(onStart = {}, onHaveAccount = {}, initialPage = initialPage)
                }
            }
        }
    }

    private fun shoot(name: String) {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun slide1_headlineAndButtonOfTheMockup_noCaption() {
        show(0)
        shoot("welcome-1")
        composeRule.onAllNodesWithText(HEADLINE).assertCountEquals(1)
        composeRule.onNodeWithText("Commencer").assertExists()
        // The captions invented for the first version are gone: the mockups have no sentence besides the headline.
        composeRule.onAllNodesWithText("Un coup d'œil, et tu sais que tout va bien.").assertCountEquals(0)
        composeRule.onAllNodesWithText("Tes lieux de confiance, repérés automatiquement.").assertCountEquals(0)
    }

    @Test
    fun slide2_isShown_withTheSameHeadline() {
        show(1)
        shoot("welcome-2")
        composeRule.onAllNodesWithText(HEADLINE).assertCountEquals(1)
    }

    @Test
    fun slide3_cardsSayExactlyWhatTheMockupSays() {
        show(2)
        shoot("welcome-3")
        listOf(
            "Thomas est bien arrivé à l’école",
            "Il y a 2 min.",
            "Amandine n’est toujours pas rentré à la maison",
            "Il y a 2 h.",
            "Maman vient de quitter le travail",
            "À l’instant",
        ).forEach { composeRule.onNodeWithText(it, useUnmergedTree = true).assertExists() }
        composeRule.onAllNodesWithText(HEADLINE).assertCountEquals(1)
    }

    @Test
    @Config(qualifiers = "en-w360dp-h800dp-port-xxhdpi")
    fun english_headlineAndButton() {
        show(0)
        shoot("welcome-1-en")
        composeRule.onNodeWithText(context().getString(R.string.onboarding_headline)).assertExists()
        composeRule.onNodeWithText("Get started").assertExists()
    }

    @Test
    fun theImagesAreTheCropsOfTheExports_atTheirNativeResolution() {
        fun size(id: Int): Pair<Int, Int> {
            val options = BitmapFactory.Options().apply { inScaled = false }
            val bitmap = BitmapFactory.decodeResource(context().resources, id, options)
            return bitmap.width to bitmap.height
        }
        // The exports are 320 x 640 px: this is all the resolution there is (to replace by better exports).
        assertEquals(155 to 290, size(R.drawable.onboarding_slide1_status))
        assertEquals(155 to 290, size(R.drawable.onboarding_slide2_place))
        assertEquals(34 to 34, size(R.drawable.onboarding_avatar_thomas))
        assertEquals(34 to 34, size(R.drawable.onboarding_avatar_amandine))
        assertEquals(34 to 34, size(R.drawable.onboarding_avatar_maman))
    }
}
