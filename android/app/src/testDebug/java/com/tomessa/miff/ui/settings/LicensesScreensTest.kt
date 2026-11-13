package com.tomessa.miff.ui.settings

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.R
import com.tomessa.miff.data.about.AssetLicenseSource
import com.tomessa.miff.data.about.LicenseSource
import com.tomessa.miff.domain.about.LicenseGroup
import com.tomessa.miff.domain.about.LicenseInventory
import com.tomessa.miff.ui.theme.MiffTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * "Licences et attributions" on the JVM (Robolectric): ticket reglages-a-propos.md, AP12 (it opens with no network and every line opens its full text), AP13 (the
 * lines of the maps and addresses), AP14, AP17. Two kinds of test: with a small FAKE source (the behaviour of the screens), and with the REAL files of the app (what
 * the person will actually read).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "en-w360dp-h800dp-port-xxhdpi")
class LicensesScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun text(id: Int): String = context().getString(id)

    private val json =
        """
        {"schema":1,
         "texts":{"lic-a":{"name":"Licence A","file":"a.txt"},"lic-b":{"name":"Licence B","file":"missing.txt"}},
         "entries":[
           {"id":"lib-a","group":"software","name":"Library A","version":"1.0","licenses":["lic-a"],"includes":"part one, part two","modules":["a."]},
           {"id":"lib-b","group":"software","name":"Library B","licenses":["lic-b"],"modules":["b."]},
           {"id":"hidden","group":"software","name":"Not shipped","shipped":false,"licenses":["lic-a"],"modules":["h."]},
           {"id":"openfreemap","group":"maps","labelKey":"android_about_licenses_map_tiles","name":"OpenFreeMap","noticeKey":"android_about_licenses_map_tiles_notice","modules":[]},
           {"id":"font-x","group":"fonts","name":"Font X","licenses":["lic-a"],"modules":[]}
         ]}
        """.trimIndent()

    private class FakeSource(
        private val inventoryJson: String?,
    ) : LicenseSource {
        val reads = mutableListOf<String>()

        override suspend fun inventory(): LicenseInventory? = inventoryJson?.let { LicenseInventory.parse(it) }

        override suspend fun text(file: String): String? {
            reads += file
            return if (file ==
                "a.txt"
            ) {
                "First line of the licence\nstill the first paragraph.\n\nSecond paragraph."
            } else {
                null
            }
        }
    }

    private fun showList(
        source: LicenseSource,
        taps: MutableList<String> = mutableListOf(),
        fontScale: Float = 1f,
    ) {
        composeRule.setContent {
            MiffTheme {
                WithDensity(fontScale) {
                    Box(Modifier.fillMaxSize()) {
                        LicensesScreen(
                            source = source,
                            onBack = { taps += "back" },
                            onOpenEntry = { taps += it },
                            onOpenPrivacyPolicy = { taps += "privacy" },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun showDetail(
        id: String,
        source: LicenseSource,
    ) {
        composeRule.setContent {
            MiffTheme {
                Box(
                    Modifier.fillMaxSize(),
                ) { LicenseDetailScreen(entryId = id, source = source, onBack = {}) }
            }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun WithDensity(
        fontScale: Float,
        content: @Composable () -> Unit,
    ) {
        val current = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(current.density, fontScale), content = content)
    }

    // --- the list ------------------------------------------------------------------------------------------------------------------------

    @Test
    fun theList_showsTheGroupsAndTheLines_andLeavesOutWhatIsNotShipped() {
        showList(FakeSource(json))
        composeRule.onNodeWithText(text(R.string.about_licenses_intro)).assertExists()
        composeRule.onNodeWithText(text(R.string.about_licenses_software_title)).assertExists()
        composeRule.onNodeWithText("Library A 1.0").assertExists()
        composeRule.onNodeWithText("Library B").assertExists()
        composeRule.onAllNodesWithText("Not shipped").assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.about_licenses_maps_title)).performScrollTo().assertExists()
        composeRule.onNodeWithText(text(R.string.android_about_licenses_map_tiles)).performScrollTo().assertExists()
        composeRule.onNodeWithText(text(R.string.about_licenses_fonts_title)).performScrollTo().assertExists()
        composeRule.onNodeWithText("Font X").performScrollTo().assertExists()
    }

    @Test
    fun aTouch_opensTheEntry_andEveryLineHasATouchTargetOf48dp() {
        val taps = mutableListOf<String>()
        showList(FakeSource(json), taps)
        composeRule
            .onNodeWithText("Library A 1.0")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule
            .onNodeWithText("Font X")
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
        assertEquals(listOf("lib-a", "font-x"), taps)
    }

    @Test
    fun theLicenceName_isTheSubtitleOfTheLine() {
        showList(FakeSource(json))
        // Library A and Font X both name it.
        composeRule.onAllNodesWithText("Licence A").assertCountEquals(2)
    }

    @Test
    fun theList_saysWhatLeavesThePhoneForAnAddress_andLinksToThePrivacyPolicy() {
        val taps = mutableListOf<String>()
        showList(FakeSource(json), taps)
        composeRule.onNodeWithText(text(R.string.about_licenses_maps_privacy)).performScrollTo().assertIsDisplayed()
        composeRule
            .onNodeWithText(
                text(R.string.auth_consent_privacy_link),
            ).performScrollTo()
            .assertHasClickAction()
            .performClick()
        assertEquals(listOf("privacy"), taps)
    }

    @Test
    fun ifTheFileCannotBeRead_theScreenSaysSoCalmly_andDoesNotCrash() {
        showList(FakeSource(null))
        composeRule.onNodeWithText(text(R.string.android_about_licenses_unavailable)).assertIsDisplayed()
    }

    @Test
    fun theFootnote_neverSaysTheLicencesAreCheckedOrCompliant() {
        val footnotes = listOf(text(R.string.android_about_licenses_footnote), text(R.string.about_licenses_intro))
        footnotes.forEach { line ->
            listOf("conforme", "compliant", "validé", "validated", "relu", "reviewed", "juriste", "lawyer").forEach {
                assertFalse("\"$line\" must not say \"$it\"", line.contains(it, ignoreCase = true))
            }
        }
    }

    @Test
    fun withTheLargestFont_everyLineIsReachableByScrolling() {
        showList(FakeSource(json), fontScale = 2f)
        listOf(
            "Library A 1.0",
            text(R.string.android_about_licenses_map_tiles),
            "Font X",
            text(R.string.android_about_licenses_footnote),
        ).forEach {
            composeRule.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
    }

    // --- the detail ----------------------------------------------------------------------------------------------------------------------

    @Test
    fun theDetail_showsWhatItIncludes_andTheFullTextInParagraphs() {
        showDetail("lib-a", FakeSource(json))
        composeRule.onNodeWithText(text(R.string.android_about_licenses_includes)).assertExists()
        composeRule.onNodeWithText("part one, part two").assertExists()
        composeRule.onNodeWithText("Licence A").assertExists()
        // The line breaks of the paragraph are gone, the words are all there.
        composeRule.onNodeWithText("First line of the licence still the first paragraph.").assertExists()
        composeRule.onNodeWithText("Second paragraph.").assertExists()
    }

    @Test
    fun aTextThatCannotBeRead_saysSoCalmly() {
        showDetail("lib-b", FakeSource(json))
        composeRule.onNodeWithText("Licence B").assertExists()
        composeRule.onNodeWithText(text(R.string.android_about_licenses_text_unavailable)).assertIsDisplayed()
    }

    @Test
    fun anAttributionOfData_showsItsNotice() {
        showDetail("openfreemap", FakeSource(json))
        composeRule.onNodeWithText(text(R.string.android_about_licenses_map_tiles_notice)).assertIsDisplayed()
    }

    @Test
    fun anUnknownLine_orOneThatIsNotShipped_showsNothingButACalmMessage() {
        showDetail("hidden", FakeSource(json))
        composeRule.onNodeWithText(text(R.string.android_about_licenses_text_unavailable)).assertIsDisplayed()
    }

    // --- the real files of the app (AP12, AP13, AP14) -----------------------------------------------------------------------------------

    private fun realSource() = AssetLicenseSource(context().assets)

    @Test
    fun theRealInventory_opensWithNoNetwork_andEveryLineOpensItsFullText() =
        runBlocking {
            val source = realSource()
            val inventory = source.inventory()
            assertNotNull("licenses.json could not be read from the assets", inventory)
            LicenseGroup.entries.flatMap { inventory!!.shown(it) }.forEach { entry ->
                inventory!!.textsOf(entry).forEach { license ->
                    val full = source.text(license.file)
                    assertNotNull("the text ${license.file} of ${entry.id} cannot be read", full)
                    assertTrue("${license.file} is too short", full!!.length > 400)
                }
            }
        }

    @Test
    fun theRealList_onAndroid_hasTheMapLinesOfTheTicket_andNoStoreOrTestLibrary() {
        showList(realSource())
        composeRule
            .onNodeWithText(
                text(R.string.android_about_licenses_map_tiles),
            ).performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(
                text(R.string.android_about_licenses_address_search),
            ).performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(
                text(R.string.android_about_licenses_map_engine),
            ).performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Baloo Thambi 2").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Hanken Grotesk").performScrollTo().assertIsDisplayed()
        listOf("JUnit", "Robolectric", "In-App Review", "ui-tooling").forEach {
            composeRule.onAllNodesWithText(it, substring = true).assertCountEquals(0)
        }
    }

    @Test
    fun theRealDetailOfMapLibre_showsTheLicenceAndTheCopyright() {
        showDetail("maplibre", realSource())
        composeRule.onNodeWithText("BSD 2-Clause").assertExists()
        composeRule
            .onNodeWithText(
                "Copyright (c) 2021 MapLibre contributors",
                substring = true,
            ).performScrollTo()
            .assertExists()
    }

    @Test
    fun theSource_neverReadsOutsideItsFolder() =
        runBlocking {
            val source = realSource()
            listOf("../licenses.json", "..\\x.txt", "/etc/passwd", "a/b.txt", "UPPER.txt", "x.json", "").forEach {
                assertNull("\"$it\" must be refused", source.text(it))
            }
        }
}
