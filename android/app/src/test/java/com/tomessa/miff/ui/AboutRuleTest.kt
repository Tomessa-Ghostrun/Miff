package com.tomessa.miff.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Rules of Settings > "À propos et aide" (ticket reglages-a-propos.md, decisions of the PM of 2026-11-13). The wiring lives in graphs bound to the container,
 * which the screen tests cannot build: these checks read the sources, so that a merge or a refactor that drops a line fails here.
 *
 * If one fails, read the ticket (AP1, AP7, AP8, AP10, AP18, AP19) before changing anything.
 */
class AboutRuleTest {
    private fun source(path: String): String {
        val file = File("src/main/java/com/tomessa/miff/$path")
        assertTrue("source not found: ${file.absolutePath}", file.exists())
        return file.readText()
    }

    private val aboutFiles =
        listOf(
            "ui/settings/AboutScreen.kt",
            "ui/settings/AboutActions.kt",
            "ui/settings/LicensesScreens.kt",
            "ui/main/AboutRoutes.kt",
            "data/about/LicenseAssets.kt",
            "domain/about/AppVersion.kt",
            "domain/about/LicenseInventory.kt",
            "domain/about/LicenseTextFormat.kt",
        )

    /** The code of a file without its comments, so that a sentence that explains a rule is not mistaken for the thing the rule forbids. */
    private fun code(path: String): String =
        source(path)
            .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }

    @Test
    fun theLastLineOfTheSettings_isAboutAndHelp_inTheFullListAndInTheRestrictedOne() {
        val shell = source("ui/main/MainShell.kt")
        // The full Settings and the restricted list both give the way to the new screen (decision Q1).
        assertEquals(2, Regex("onOpenAbout = \\{ navController\\.openAbout\\(\\) \\}").findAll(shell).count())
        // And its screens are in the routes SHARED by both (one implementation, two hosts), the reminders help left out of the restricted ones.
        assertTrue(shell.contains("aboutRoutes(navController, showRemindersHelp)"))
        assertTrue(
            shell.contains("sharedSettingsRoutes(container, navController, scopeKey, showRemindersHelp = false)"),
        )
        val list = source("ui/settings/SettingsScreens.kt")
        // The row is drawn AFTER the optional lines (subscription, diagnostic of the test builds): it is the last one (AP1).
        val diagnostic = list.indexOf("InternalDestinations.SettingsEntry")
        val about = list.indexOf("R.string.about_title")
        assertTrue("the diagnostic line was not found", diagnostic > 0)
        assertTrue("\"À propos et aide\" must be drawn after every other line", about > diagnostic)
        assertFalse(
            "onOpenAbout must be given by the caller, never optional",
            list.contains("onOpenAbout: (() -> Unit)?"),
        )
    }

    @Test
    fun theTwoActionsComeFirstInTheSource_thenTheInformationSection() {
        // AP20 (decision of the PM, 2026-11-13): contact and feedback at the TOP as cards, everything else below under "Informations".
        val about = code("ui/settings/AboutScreen.kt")
        val contact = about.indexOf("R.string.settings_privacy_contact")
        val feedback = about.indexOf("R.string.about_feedback_title")
        val info = about.indexOf("AboutSection(R.string.about_info_title)")
        val version = about.indexOf("VersionRow(version")
        val help = about.indexOf("R.string.settings_detection_help_title")
        val licences = about.indexOf("R.string.about_licenses_title")
        assertTrue(contact in 0 until feedback)
        assertTrue(feedback in 0 until info)
        assertTrue(info in 0 until version)
        assertTrue(version < help && help < licences)
        // The two prominent lines are cards, the others are plain rows.
        assertEquals(2, Regex("ActionCard\\(\\s*icon").findAll(about).count())
    }

    @Test
    fun contactIsNoLongerInPrivacy_itMovedToAboutAndHelp() {
        val privacy = code("ui/settings/PrivacyScreen.kt")
        assertFalse(privacy.contains("settings_privacy_contact"))
        assertFalse(privacy.contains("contactSupport"))
        val about = code("ui/settings/AboutScreen.kt")
        assertTrue(about.contains("R.string.settings_privacy_contact"))
        assertTrue(about.contains("actions.openContact()"))
    }

    @Test
    fun theFeedbackLine_isDrawnOnlyWithAnAddress_andItsLabelIsTheOneOfThePm() {
        val about = code("ui/settings/AboutScreen.kt")
        assertTrue(about.contains("if (actions.feedbackFormUrl != null)"))
        assertTrue(about.contains("R.string.about_feedback_title"))
        val french = File("src/main/res/values-fr/strings.xml").readText()
        assertTrue(french.contains("<string name=\"about_feedback_title\">Un retour sur l\\'app ?</string>"))
        // AP10: nothing about the stores, a note or stars in the line.
        val words = Regex("store|note|étoile|etoile|avis|star|rating|review|play", RegexOption.IGNORE_CASE)
        listOf("src/main/res/values-fr/strings.xml", "src/main/res/values/strings.xml").forEach { path ->
            Regex(
                """<string name="about_feedback_[a-z0-9_]+"[^>]*>([^<]*)</string>""",
            ).findAll(File(path).readText()).forEach {
                assertFalse(
                    "$path: \"${it.groupValues[1]}\" talks about the stores or a note",
                    words.containsMatchIn(it.groupValues[1]),
                )
            }
        }
    }

    @Test
    fun theAddressOfTheForm_isReadOnlyFromTheBuildConfiguration_andOpenedAsItIs() {
        val actions = code("ui/settings/AboutActions.kt")
        assertEquals(1, Regex("BuildConfig\\.FEEDBACK_FORM_URL").findAll(actions).count())
        assertTrue(actions.contains("FeedbackForm.openableUrl("))
        assertTrue(actions.contains("FeedbackFormLauncher.open(context, it)"))
        // The address is not written anywhere in the code, nor in a text (the one place is the build file).
        File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach {
            assertFalse("${it.name} writes the address of the form", it.readText().contains("forms.gle"))
        }
        File("src/main/res").walkTopDown().filter { it.isFile && it.extension == "xml" }.forEach {
            assertFalse("${it.name} writes the address of the form", it.readText().contains("forms.gle"))
        }
        assertFalse(
            actions.contains("buildUpon") || actions.contains("appendQueryParameter") || actions.contains("?uid"),
        )
    }

    @Test
    fun theContactAddress_isNeverWrittenInATranslatedText() {
        listOf("src/main/res/values-fr/strings.xml", "src/main/res/values/strings.xml").forEach { path ->
            Regex("""<string name="about_[a-z0-9_]+"[^>]*>([^<]*)</string>""").findAll(File(path).readText()).forEach {
                assertFalse("$path: an about_ text holds an e-mail address", it.groupValues[1].contains("@"))
            }
        }
        assertTrue(code("ui/settings/AboutActions.kt").contains("BuildConfig.SUPPORT_EMAIL"))
    }

    @Test
    fun theScreens_readNothingFromTheServerCountNothingAndCallNoNetwork() {
        val forbidden =
            listOf(
                "Firestore",
                "FirebaseAuth",
                "Analytics",
                "logEvent",
                "Crashlytics",
                "HttpURLConnection",
                "URL(",
                "OkHttp",
                "requestPermissions",
                "ActivityResultContracts",
                "Log.",
                "println(",
            )
        aboutFiles.forEach { path ->
            val text = code(path)
            forbidden.forEach { word -> assertFalse("$path must not contain \"$word\" (AP18)", text.contains(word)) }
        }
    }

    @Test
    fun theLinksThatLeaveTheApp_goThroughTheHttpsOnlyOpeners() {
        val actions = code("ui/settings/AboutActions.kt")
        assertTrue(actions.contains("LegalLinks.termsUrl(context)"))
        assertTrue(actions.contains("LegalLinks.privacyUrl(context)"))
        assertTrue(actions.contains("SupportMail.open(context)"))
        // No other way out of the app: no raw Intent built here.
        assertFalse(actions.contains("Intent("))
        assertFalse(code("ui/settings/AboutScreen.kt").contains("Intent("))
    }

    @Test
    fun noPermissionIsAdded() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        // The permissions of the app are the ones of the merged-manifest check of the CI; this screen needs none (a clipboard write needs no permission).
        assertFalse(manifest.contains("READ_CLIPBOARD"))
        assertFalse(manifest.contains("WRITE_EXTERNAL_STORAGE"))
    }
}
