package com.tomessa.miff.ui.review

import com.tomessa.miff.domain.review.ReviewState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Criteria AV2 and AV4 of docs/tickets/pop-ins-avis.md, checked on the SOURCE so that a later change cannot break them unnoticed:
 * - AV2: the review window is ABSENT from the builds that can be distributed (release, releaseLike): its flag `BuildConfig.REVIEW_PROMPT` is false there, a release
 *   build can only turn it on with an explicit Gradle flag (guard on preReleaseBuild, like LOCATION_BLOCKING), and every use of the window outside its own package
 *   sits behind that flag, so that R8 removes it. The proof on the real bundle is a step of `.github/workflows/android.yml` (text REVIEWPROMPTPROBE).
 * - AV4: no analytics event, no log, no permission, no personal data in the window; the feedback form address comes from the build and nowhere else.
 *
 * The checks read the code with its WHITESPACE NORMALISED (one space), so that a reformatting by Spotless never breaks them.
 */
class ReviewPromptReleaseTest {
    private fun normalised(file: File): String = file.readText().replace(Regex("\\s+"), " ")

    private val gradle = normalised(File("build.gradle.kts"))
    private val main = File("src/main/java/com/tomessa/miff")

    private fun kotlinFiles(vararg dirs: String): List<File> =
        dirs.flatMap { File(main, it).walkTopDown().filter { f -> f.isFile && f.extension == "kt" }.toList() }

    private fun codeLines(file: File): List<String> =
        file.readLines().filter {
            val line = it.trimStart()
            !line.startsWith("*") && !line.startsWith("//") && !line.startsWith("/*")
        }

    private fun isInsideReview(file: File) = file.path.replace('\\', '/').contains("/review/")

    // --- AV2: the flag --------------------------------------------------------------------------------------------------------------------------

    @Test
    fun theFlagIsFalseByDefault_trueInDebug_andNothingElseTurnsItOn() {
        // Default for every build type: false (release and releaseLike inherit it) ...
        assertTrue(gradle.contains("""buildConfigField("boolean", "REVIEW_PROMPT", reviewPromptProperty ?: "false")"""))
        // ... and only the debug build type says true.
        val trueOnes =
            Regex(
                """buildConfigField\( ?"boolean", "REVIEW_PROMPT", reviewPromptProperty \?: "true" ?\)""",
            ).findAll(gradle).count()
        assertEquals(1, trueOnes)
        val debugBlock = gradle.substringAfter("debug {").substringBefore("release {")
        assertTrue(debugBlock.contains("""reviewPromptProperty ?: "true""""))
        val restOfBuildTypes = gradle.substringAfter("release {").substringBefore("compileOptions")
        assertFalse(restOfBuildTypes.contains("""reviewPromptProperty ?: "true""""))
    }

    @Test
    fun aReleaseBuildRefusesTheFlag_unlessAskedOnPurpose() {
        assertTrue(gradle.contains("""providers.gradleProperty("miff.reviewPrompt")"""))
        assertTrue(gradle.contains("miff.allowReviewPromptInRelease"))
        val guard = gradle.substringAfter("val reviewOnInRelease")
        assertTrue(guard.contains("preReleaseBuild") && guard.contains("preReleaseLikeBuild"))
        assertTrue(guard.contains("GradleException"))
        // Only the value "true" is dangerous; anything else than true or false is refused at configuration time.
        assertTrue(gradle.contains("miff.reviewPrompt must be true or false"))
    }

    @Test
    fun theWindowIsOnlyUsedBehindTheFlag_andOnlyFromMainShell() {
        val uses =
            Regex(
                """\b(ReviewPromptHost|ReviewPromptWindow|rememberReviewHost|ReviewPromptViewModel|PlayStoreReviewRequester|PlayInAppReviewApi)\b""",
            )
        val users =
            kotlinFiles(".")
                .filter { !isInsideReview(it) }
                .filter { file ->
                    codeLines(file).any { line ->
                        !line.trimStart().startsWith("import ") &&
                            uses.containsMatchIn(line)
                    }
                }.map { it.name }
        // Nothing but the main shell reaches for the window.
        assertEquals(listOf("MainShell.kt"), users)
        val shell = normalised(File(main, "ui/main/MainShell.kt"))
        assertTrue(shell.contains("if (BuildConfig.REVIEW_PROMPT && review != null) { ReviewPromptHost("))
        val factory = normalised(File(main, "ui/review/ReviewPromptHost.kt"))
        assertTrue(
            factory.substringAfter("fun rememberReviewHost").contains("if (!BuildConfig.REVIEW_PROMPT) return null"),
        )
    }

    @Test
    fun theDebugOptions_liveInTheDebugSourceSetOnly_andTheForceIsHonouredByDebugBuildsOnly() {
        assertTrue(File("src/debug/java/com/tomessa/miff/ui/diagnostic/ReviewDebugCard.kt").exists())
        assertFalse(File(main, "ui/diagnostic").exists())
        assertTrue(
            normalised(File(main, "ui/review/ReviewPromptHost.kt")).contains("debugForceAllowed = BuildConfig.DEBUG"),
        )
    }

    @Test
    fun theLibrariesActivity_isRemovedFromTheManifestOfTheBuildsThatAreDistributed() {
        val manifest = normalised(File("src/release/AndroidManifest.xml"))
        assertTrue(manifest.contains("com.google.android.play.core.common.PlayCoreDialogWrapperActivity"))
        assertTrue(manifest.contains("""tools:node="remove""""))
        // releaseLike has its own source set: it must be pointed at the same manifest.
        assertTrue(gradle.contains("""manifest.srcFile("src/release/AndroidManifest.xml")"""))
        assertTrue(normalised(File("../../.github/workflows/android.yml")).contains("PlayCoreDialogWrapperActivity"))
    }

    @Test
    fun theWindowKeepsItsMarker_forTheCheckOfTheBundle() {
        assertEquals("REVIEWPROMPTPROBE", REVIEW_PROMPT_PROBE)
        val workflow = normalised(File("../../.github/workflows/android.yml"))
        assertTrue("android.yml must look for the marker in the release bundle", workflow.contains("REVIEWPROMPTPROBE"))
    }

    // --- the feedback form -------------------------------------------------------------------------------------------------------------------------

    @Test
    fun theFeedbackFormAddress_isOneBuildSetting_validatedAsHttps_andReadFromBuildConfigOnly() {
        assertTrue(gradle.contains("""providers.gradleProperty("miff.feedbackFormUrl")"""))
        assertTrue(gradle.contains("miff.feedbackFormUrl must be empty or an https:// address"))
        assertTrue(gradle.contains("""buildConfigField("String", "FEEDBACK_FORM_URL""""))
        // The default is the public Google Forms address given by the PM (2026-11-13), written in ONE place and nowhere else in the code; no query, no fragment.
        assertTrue(gradle.contains("""getOrElse("https://forms.gle/5EEH4sEfsnCCLzEx7")"""))
        val everywhere = kotlinFiles(".").filter { it.readText().contains("forms.gle") }
        assertEquals(emptyList<File>(), everywhere)
        val readers =
            kotlinFiles(".")
                .filter { file -> codeLines(file).any { it.contains("FEEDBACK_FORM_URL") } }
                .map { it.name }
        assertEquals(listOf("ReviewPromptHost.kt"), readers)
        // Opened as it is: nothing is appended to the address, no extra is put in the intent.
        val launcher = normalised(File(main, "data/review/StoreReview.kt"))
        assertFalse(launcher.contains("putExtra"))
        assertFalse(launcher.contains("appendQueryParameter"))
        assertFalse(Regex("""openableUrl\([^)]*\) ?\+""").containsMatchIn(launcher))
    }

    // --- AV4: nothing leaves, nothing is logged --------------------------------------------------------------------------------------------------

    private val reviewFiles = kotlinFiles("ui/review", "domain/review", "data/review")

    @Test
    fun noAnalyticsEvent_noLog_noNetworkCode_inTheReviewCode() {
        val forbidden =
            listOf(
                Regex("""analytics""", RegexOption.IGNORE_CASE),
                Regex("""\bLog\.[a-z]\("""),
                Regex("""\bprintln\("""),
                Regex("""Crashlytics|FirebaseFirestore|FirebaseAuth|setUserId|setUserProperty"""),
                Regex("""http://"""),
                Regex("""\bHttpURLConnection\b|\bOkHttp|\bRetrofit\b"""),
            )
        val offenders =
            reviewFiles.flatMap { file ->
                codeLines(file)
                    .filter { line ->
                        forbidden.any { it.containsMatchIn(line) }
                    }.map { "${file.name}: ${it.trim()}" }
            }
        assertEquals(listOf<String>(), offenders)
    }

    @Test
    fun whatThePhoneRemembers_isThreeValues_noneOfThemTheContentOfAnAnswer() {
        val fields =
            ReviewState::class.java.declaredFields
                .map { it.name }
                .filter { !it.startsWith("$") }
                .toSet()
        assertEquals(setOf("firstMiffSeenAtMillis", "lastShownAtMillis", "lastOutcome"), fields)
    }

    @Test
    fun theMailGoesThroughTheEmailApplicationOfThePerson_withTheSupportAddressAndNothingElse() {
        // No permission is added (the merged manifests are checked by android.yml): the same mailto: as Settings, with no subject, no body, no extra.
        val links = normalised(File(main, "ui/settings/ExternalLinks.kt"))
        assertTrue(links.contains("""Uri.parse("mailto:" + BuildConfig.SUPPORT_EMAIL)"""))
        val mail = normalised(File(main, "data/review/StoreReview.kt"))
        assertTrue(mail.contains(""""mailto:${'$'}{BuildConfig.SUPPORT_EMAIL}".toUri()"""))
        assertFalse(mail.contains("EXTRA_"))
        assertTrue(normalised(File(main, "ui/review/ReviewPromptHost.kt")).contains("SupportMail::open"))
    }

    @Test
    fun theStoreIsNeverToldWhatWasAnswered() {
        // The request of the store has no argument but the screen: no note, no text, no identifier.
        val requester = normalised(File(main, "data/review/StoreReview.kt"))
        assertTrue(
            requester.contains(
                "fun interface StoreReviewRequester { suspend fun request(activity: Activity): Boolean }",
            ),
        )
    }
}
