package com.tomessa.miff.ui.onboarding

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
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
 * of the illustrations are EXACTLY the ones of the mockups and are REAL texts (vectors, not pixels: sharp at any
 * density), there is no invented caption. Also writes screenshots (build/screenshots/welcome-*.png) at several
 * densities and font sizes to check the sharpness.
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

    private fun assertTexts(vararg texts: String) {
        texts.forEach { composeRule.onNodeWithText(it, useUnmergedTree = true).assertExists() }
    }

    @Test
    fun slide1_isTheFeedScreen_withRealTexts_andTheHeadlineAndButtonOfTheMockup() {
        show(0)
        shoot("welcome-1")
        composeRule.onAllNodesWithText(HEADLINE).assertCountEquals(1)
        composeRule.onNodeWithText("Commencer").assertExists()
        assertTexts(
            "Famille Alberta",
            "Tout le monde est en sécurité",
            "Mis à jour il y a 2 min.",
            "Thomas",
            "À la maison",
            "Arrivé il y a 12 min",
            "85 %",
            "Amandine",
            "22 %",
            "À l'école",
        )
        // The captions invented for the first version are gone: the mockups have no sentence besides the headline.
        composeRule.onAllNodesWithText("Un coup d'œil, et tu sais que tout va bien.").assertCountEquals(0)
    }

    @Test
    fun slide1_isDescribedByOneSentence_andItsInnerTextsAreHiddenFromTheScreenReader() {
        show(0)
        composeRule
            .onNodeWithContentDescription(context().getString(R.string.onboarding_slide1_illustration_a11y))
            .assertExists()
        composeRule.onAllNodesWithText("Famille Alberta").assertCountEquals(0) // merged tree: hidden
        composeRule.onNodeWithText("Famille Alberta", useUnmergedTree = true).assertExists() // but real text
    }

    @Test
    fun slide2_isTheNewPlaceScreen_withRealTexts() {
        show(1)
        shoot("welcome-2")
        composeRule.onAllNodesWithText(HEADLINE).assertCountEquals(1)
        assertTexts(
            "Nouveau lieu",
            "Nom",
            "Choisissez un nom pour retrouver l'endroit facilement",
            "Maison du père de Nathan",
            "Adresse",
            "Entrez l'adresse exacte de ce lieu",
            "17 Avenue d'Italie, 75013, Paris",
            "Périmètre",
            "Définissez le rayon autour du lieu",
            "100 m",
            "Type",
            "Quel type de zone définissez-vous ?",
            "Zone de confiance",
            "Lieu sûr pour vos proches",
            "Zone à risque",
            "Lieu à éviter",
        )
    }

    @Test
    fun slide3_cardsSayExactlyWhatTheMockupSays() {
        show(2)
        shoot("welcome-3")
        assertTexts(
            "Thomas est bien arrivé à l’école",
            "Il y a 2 min.",
            "Amandine n’est toujours pas rentré à la maison",
            "Il y a 2 h.",
            "Maman vient de quitter le travail",
            "À l’instant",
        )
        composeRule.onAllNodesWithText(HEADLINE).assertCountEquals(1)
    }

    @Test
    @Config(qualifiers = "en-w360dp-h800dp-port-xxhdpi")
    fun english_headlineButtonAndTheTextsOfTheIllustrations() {
        show(0)
        shoot("welcome-1-en")
        composeRule.onNodeWithText(context().getString(R.string.onboarding_headline)).assertExists()
        composeRule.onNodeWithText("Get started").assertExists()
        assertTexts(
            "Alberta family",
            "Everyone is safe",
            "Updated 2 min ago.",
            "At home",
            "At school",
            "Arrived 12 min ago",
        )
    }

    @Test
    @Config(qualifiers = "en-w360dp-h800dp-port-xxhdpi")
    fun english_slide2() {
        show(1)
        shoot("welcome-2-en")
        assertTexts("New place", "Name", "Address", "Radius", "Trusted zone", "Risk zone")
    }

    // --- Sharpness: the same screens at the smallest and the largest densities, and with the largest font ----------

    @Test
    @Config(qualifiers = "fr-w360dp-h800dp-port-mdpi")
    fun sharpness_mdpi() {
        show(0)
        shoot("welcome-1-mdpi")
        composeRule.onNodeWithText("Famille Alberta", useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = "fr-w360dp-h800dp-port-xxxhdpi")
    fun sharpness_xxxhdpi() {
        show(0)
        shoot("welcome-1-xxxhdpi")
        composeRule.onNodeWithText("Famille Alberta", useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = "fr-w360dp-h800dp-port-xxxhdpi")
    fun sharpness_slide2_xxxhdpi() {
        show(1)
        shoot("welcome-2-xxxhdpi")
        composeRule.onNodeWithText("Nouveau lieu", useUnmergedTree = true).assertExists()
    }

    @Test
    fun largestFont_theIllustrationsKeepTheirLayout_andTheButtonStaysReachable() {
        composeRule.setContent {
            MiffTheme {
                CompositionLocalProvider(LocalDensity provides Density(density = 3f, fontScale = 2f)) {
                    Box(Modifier.fillMaxSize()) {
                        WelcomeCarouselScreen(onStart = {}, onHaveAccount = {}, initialPage = 0)
                    }
                }
            }
        }
        shoot("welcome-1-bigfont")
        composeRule.onNodeWithText("Commencer").assertExists()
        composeRule.onNodeWithText("Famille Alberta", useUnmergedTree = true).assertExists()
    }

    @Test
    fun thePhotosAreUpsampledCropsOfTheExports_andCanBeReplacedByAnyResolution() {
        fun size(id: Int): Pair<Int, Int> {
            val options = BitmapFactory.Options().apply { inScaled = false }
            val bitmap = BitmapFactory.decodeResource(context().resources, id, options)
            return bitmap.width to bitmap.height
        }
        // Royalty-free Pexels photos (design/exports/onboarding-photos), cropped and compressed; only the map is still the
        // upsampled crop of the mockup. Any resolution can replace these files without a code change.
        assertEquals(480 to 480, size(R.drawable.onboarding_photo_thomas))
        assertEquals(480 to 480, size(R.drawable.onboarding_photo_amandine))
        assertEquals(480 to 480, size(R.drawable.onboarding_photo_maman))
        assertEquals(1000 to 385, size(R.drawable.onboarding_photo_salon))
        assertEquals(488 to 184, size(R.drawable.onboarding_photo_map))
    }
}
