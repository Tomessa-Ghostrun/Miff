package com.tomessa.miff.ui.review

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
 * - AV4: no analytics event, no log, no permission, no network address, no personal data in the window.
 */
class ReviewPromptReleaseTest {
    private val gradle = File("build.gradle.kts").readText()
    private val main = File("src/main/java/com/tomessa/miff")

    private fun kotlinFiles(vararg dirs: String): List<File> =
        dirs.flatMap { File(main, it).walkTopDown().filter { f -> f.isFile && f.extension == "kt" }.toList() }

    private fun codeLines(file: File): List<String> =
        file.readLines().filter {
            val line = it.trimStart()
            !line.startsWith("*") && !line.startsWith("//") && !line.startsWith("/*")
        }

    // --- AV2: the flag --------------------------------------------------------------------------------------------------------------------------

    @Test
    fun theFlagIsFalseByDefault_trueInDebug_andNothingElseTurnsItOn() {
        // Default for every build type: false (release and releaseLike inherit it) ...
        assertTrue(gradle.contains("""buildConfigField("boolean", "REVIEW_PROMPT", reviewPromptProperty ?: "false")"""))
        // ... and only the debug build type says true.
        val trueOnes =
            Regex(
                """buildConfigField\("boolean", "REVIEW_PROMPT", reviewPromptProperty \?: "true"\)""",
            ).findAll(gradle).count()
        assertEquals(1, trueOnes)
        val debugBlock = gradle.substringAfter("debug {").substringBefore("release {")
        assertTrue(debugBlock.contains("""reviewPromptProperty ?: "true""""))
        val releaseBlocks = gradle.substringAfter("release {")
        assertFalse(releaseBlocks.substringBefore("compileOptions").contains("""reviewPromptProperty ?: "true""""))
    }

    @Test
    fun aReleaseBuildRefusesTheFlag_unlessAskedOnPurpose() {
        assertTrue(gradle.contains("""providers.gradleProperty("miff.reviewPrompt")"""))
        assertTrue(gradle.contains("miff.allowReviewPromptInRelease"))
        val guard = gradle.substringAfter("val reviewOnInRelease")
        assertTrue(guard.contains("preReleaseBuild") && guard.contains("preReleaseLikeBuild"))
        assertTrue(guard.contains("GradleException"))
        // Only the value "true" is dangerous; anything else is refused at configuration time.
        assertTrue(gradle.contains("miff.reviewPrompt must be true or false"))
    }

    @Test
    fun theWindowIsOnlyUsedBehindTheFlag() {
        val outside = kotlinFiles(".").filter { !it.path.replace('\\', '/').contains("/review/") }
        val uses =
            Regex(
                """\b(ReviewPromptHost|ReviewPromptWindow|rememberReviewHost|ReviewPromptViewModel|PlayStoreReviewRequester|SharedPreferencesReviewStateStore)\b""",
            )
        val offenders =
            outside.flatMap { file ->
                val lines = file.readLines()
                lines
                    .withIndex()
                    .filter { (_, line) ->
                        !line.trimStart().startsWith("import ") && !line.trimStart().startsWith("*") &&
                            !line.trimStart().startsWith("//")
                    }.filter { (_, line) -> uses.containsMatchIn(line) }
                    // The line of use, or one of the three lines before it, names the flag (or, for the host factory, the function checks it itself).
                    .filter { (index, _) ->
                        lines
                            .subList(
                                (index - 3).coerceAtLeast(0),
                                index + 1,
                            ).none { it.contains("BuildConfig.REVIEW_PROMPT") }
                    }.map { (index, line) -> "${file.name}:${index + 1}: ${line.trim()}" }
            }
        // The only allowed use outside the flag: the call of rememberReviewHost, whose first line is the check of the flag (asserted below).
        assertEquals(listOf<String>(), offenders.filterNot { it.contains("rememberReviewHost(container, scopeKey)") })
        val factory = File(main, "ui/review/ReviewPromptHost.kt").readText()
        assertTrue(
            factory.substringAfter("fun rememberReviewHost").contains("if (!BuildConfig.REVIEW_PROMPT) return null"),
        )
        val shell = File(main, "ui/main/MainShell.kt").readText()
        assertTrue(shell.contains("if (BuildConfig.REVIEW_PROMPT && review != null)"))
    }

    @Test
    fun theDebugOptions_liveInTheDebugSourceSetOnly_andTheForceIsHonouredByDebugBuildsOnly() {
        assertTrue(File("src/debug/java/com/tomessa/miff/ui/diagnostic/ReviewDebugCard.kt").exists())
        assertFalse(File(main, "ui/diagnostic").exists())
        assertTrue(
            File(main, "ui/review/ReviewPromptHost.kt").readText().contains("debugForceAllowed = BuildConfig.DEBUG"),
        )
    }

    @Test
    fun theWindowKeepsItsMarker_forTheCheckOfTheBundle() {
        assertEquals("REVIEWPROMPTPROBE", REVIEW_PROMPT_PROBE)
        val workflow = File("../../.github/workflows/android.yml").readText()
        assertTrue("android.yml must look for the marker in the release bundle", workflow.contains("REVIEWPROMPTPROBE"))
    }

    // --- AV4: nothing leaves, nothing is logged --------------------------------------------------------------------------------------------------

    private val reviewFiles = kotlinFiles("ui/review", "domain/review", "data/review")

    @Test
    fun noAnalyticsEvent_noLog_noNetworkAddress_inTheReviewCode() {
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
    fun theStarThatWasTouched_isNeverKept() {
        // The store keeps four values: none of them is a rating.
        val store = File(main, "data/review/ReviewStateStore.kt").readText()
        assertFalse(
            Regex(
                """stars?|rating|note""",
                RegexOption.IGNORE_CASE,
            ).containsMatchIn(store.substringAfter("class SharedPreferencesReviewStateStore")),
        )
        val state =
            File(
                main,
                "domain/review/ReviewPrompt.kt",
            ).readText().substringAfter("data class ReviewState").substringBefore("/** What the screen")
        assertFalse(Regex("""stars?|rating""", RegexOption.IGNORE_CASE).containsMatchIn(state))
    }

    @Test
    fun theMailGoesThroughTheEmailApplicationOfThePerson_withTheSupportAddressAndNothingElse() {
        // No permission is added (the merged manifests are checked by android.yml): the mail is the same mailto: as Settings, with no subject, no body, no extra.
        val links = File(main, "ui/settings/ExternalLinks.kt").readText()
        assertTrue(links.contains("""Uri.parse("mailto:" + BuildConfig.SUPPORT_EMAIL)"""))
        assertTrue(File(main, "ui/review/ReviewPromptHost.kt").readText().contains("ExternalLinks::contactSupport"))
    }

    @Test
    fun theStoreIsNeverToldWhatWasAnswered() {
        // The request of the store has no argument but the screen: no note, no text, no identifier.
        val requester = File(main, "data/review/StoreReview.kt").readText()
        assertTrue(requester.contains("fun interface StoreReviewRequester"))
        assertTrue(requester.contains("suspend fun request(activity: Activity)"))
    }
}
