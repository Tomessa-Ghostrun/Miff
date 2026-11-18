package com.tomessa.miff.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The DESCRIPTION of an update (ticket description-update, criteria V1 to V5, F3, F10), guarded on the sources. A description is a free text that can hold personal
 * data: it lives in memory, goes to Firestore in the document of the photo and nowhere else, and is drawn as plain text.
 */
class DescriptionPrivacyRuleTest {
    private val main = File("src/main/java/com/tomessa/miff")

    private fun sources() = main.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun code(file: File) =
        file
            .readLines()
            .filter {
                val line = it.trimStart()
                !line.startsWith("*") && !line.startsWith("//") && !line.startsWith("/*")
            }.joinToString("\n")

    private fun named(name: String) = sources().first { it.name == name }

    private val carriers =
        listOf(
            "PhotoDescription.kt",
            "DescriptionField.kt",
            "UpdateViewModel.kt",
            "UpdateWrites.kt",
            "SharedPhoto.kt",
            "UpdatePublisher.kt",
        )

    @Test
    fun theSourcesAreFound() {
        carriers.forEach { named(it) }
        assertTrue(sources().size > 200)
    }

    @Test
    fun theDescriptionIsNeverLogged_sentAsAnEvent_reportedInACrash_orCopied() {
        val found =
            carriers.filter { name ->
                Regex(
                    """\bLog\.[a-z]\(|\bprintln\(|\bprintStackTrace\b|\bTimber\b|Crashlytics|recordException|setCustomKey|FirebaseAnalytics|logEvent|AnalyticsEvent|ClipboardManager|ClipData|setPrimaryClip""",
                ).containsMatchIn(code(named(name)))
            }
        assertEquals(emptyList<String>(), found)
    }

    @Test
    fun theDraftIsNeverSavedByTheSystem_noRememberSaveable_noSavedStateHandle_noPreferences() {
        listOf("DescriptionField.kt", "UpdateViewModel.kt").forEach { name ->
            val text = code(named(name))
            assertTrue(
                "$name saves state",
                !Regex(
                    """rememberSaveable|SavedStateHandle|SharedPreferences|DataStore|openFileOutput|writeText""",
                ).containsMatchIn(text),
            )
        }
    }

    @Test
    fun noNotificationNorPushCodeEverReadsTheDescription() {
        val found =
            sources()
                .filter {
                    it.path.replace('\\', '/').let { p ->
                        p.contains("/data/push/") ||
                            p.contains("/notification/") ||
                            p.contains("Notification")
                    }
                }.filter { Regex("""PhotoDescription|"description"""").containsMatchIn(code(it)) }
                .map { it.name }
        assertEquals("a notification must never carry the description", emptyList<String>(), found)
    }

    @Test
    fun theFullScreenDrawsThePlainText_noLink_noSelection_noAnnotatedString() {
        val text = code(named("FeedPhoto.kt"))
        assertTrue(
            !Regex(
                """SelectionContainer|ClickableText|LinkAnnotation|AnnotatedString|buildAnnotatedString|Linkify|autoLink|LocalUriHandler""",
            ).containsMatchIn(text),
        )
    }

    @Test
    fun savingThePhoto_neverSavesTheDescription() {
        listOf("PhotoSaver.kt", "PhotoSaveController.kt", "PhotoDownload.kt").forEach { name ->
            val text = code(named(name))
            assertTrue(
                "$name touches the description",
                !Regex("""PhotoDescription|"description"""").containsMatchIn(text),
            )
        }
    }

    @Test
    fun onlyThePhotoDocumentCarriesTheDescription_theStatusBuilderNeverDoes() {
        val tracking = code(File(main, "domain/tracking/TrackingWrites.kt"))
        assertTrue(
            "the status must not carry the description",
            !Regex("""PhotoDescription|"description"""").containsMatchIn(tracking),
        )
    }

    @Test
    fun theKeyboardIsAskedNotToLearnTheText_andTheActionIsDone_neverASend() {
        val text = code(named("DescriptionField.kt"))
        assertTrue(text.contains("noPersonalizedLearning"))
        assertTrue(text.contains("ImeAction.Done"))
        assertTrue(!text.contains("ImeAction.Send") && !text.contains("ImeAction.Go"))
    }
}
