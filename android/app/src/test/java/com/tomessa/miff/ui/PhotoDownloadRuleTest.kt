package com.tomessa.miff.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * "Enregistrer la photo" (decision of the PM, 2026-11-08), guarded on the sources. The photo of the Feed now CAN leave the app, but only by a deliberate
 * touch on ONE button, in the full-screen photo of the Feed, and without any permission:
 * - the only file that writes into the pictures or a document of the phone is `data/media/PhotoSaver.kt`;
 * - only the full-screen photo (FeedPhoto.kt) reaches it: not the card, not the Update, not any other screen;
 * - the writing is called from the touch (or from the answer of the picker that touch opened), never from a LaunchedEffect;
 * - no storage permission appears in the manifest (the CI also checks the merged manifests);
 * - nothing about the photo goes to a log, an analytics event or the clipboard from these files.
 */
class PhotoDownloadRuleTest {
    private val main = File("src/main/java/com/tomessa/miff")

    private fun sources() = main.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun codeLines(file: File) =
        file.readLines().filter {
            val line = it.trimStart()
            !line.startsWith("*") && !line.startsWith("//") && !line.startsWith("/*")
        }

    private fun code(file: File) = codeLines(file).joinToString("\n")

    private fun named(name: String) = sources().first { it.name == name }

    @Test
    fun theSourcesAreFound() {
        assertTrue("sources not found from ${File(".").absolutePath}", sources().size > 200)
    }

    @Test
    fun onlyThePhotoSaverWritesIntoThePicturesOrADocument() {
        val found =
            sources()
                .filter { file ->
                    codeLines(file).any {
                        Regex(
                            """MediaStore\.Images|openOutputStream|RELATIVE_PATH|DIRECTORY_PICTURES""",
                        ).containsMatchIn(it)
                    }
                }.map { it.name }
        assertEquals(listOf("PhotoSaver.kt"), found)
    }

    @Test
    fun onlyTheFullScreenPhotoReachesTheSaver() {
        val allowed = setOf("PhotoSaver.kt", "PhotoSaveController.kt", "FeedPhoto.kt", "PhotoDownload.kt")
        val found =
            sources()
                .filter { file ->
                    codeLines(file).any {
                        Regex(
                            """\bPhotoSaver\b|\bContentPhotoSaver\b|\bPhotoSaveController\b|\bPhotoDownload\b|feed_photo_download""",
                        ).containsMatchIn(it)
                    }
                }.map { it.name }
                .toSet()
        assertEquals(allowed, found)
    }

    @Test
    fun theButtonIsDrawnInTheFullScreenPhotoOnly() {
        val photo = code(named("FeedPhoto.kt"))
        val fullScreen = photo.substringAfter("fun PhotoFullScreen(").substringBefore("private fun SavePhotoButton(")
        assertEquals("SavePhotoButton( is called once", 1, Regex("""\bSavePhotoButton\(""").findAll(fullScreen).count())
        // Not in the card (the window on the photo) nor in the tile of reciprocity.
        val card = photo.substringBefore("fun PhotoFullScreen(")
        assertFalse(card.contains("SavePhotoButton("))
        assertFalse(card.contains("feed_photo_download"))
    }

    @Test
    fun theWritingStartsFromATouch_neverFromAnEffect() {
        val photo = code(named("FeedPhoto.kt"))
        val fullScreen = photo.substringAfter("fun PhotoFullScreen(").substringBefore("private fun SavePhotoButton(")
        assertEquals(1, Regex("""controller\.saveToPictures\(""").findAll(fullScreen).count())
        assertEquals(1, Regex("""controller\.saveTo\(""").findAll(fullScreen).count())
        assertFalse("no automatic save", fullScreen.contains("LaunchedEffect"))
        assertFalse(fullScreen.contains("DisposableEffect"))
        // The touch is what starts it.
        val onClick = fullScreen.substringAfter("onClick = {").substringBefore("SaveRoute.DOCUMENT_PICKER")
        assertTrue(onClick.contains("controller.begin()") && onClick.contains("controller.saveToPictures("))
    }

    @Test
    fun noStoragePermissionIsDeclared() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        listOf("WRITE_EXTERNAL_STORAGE", "READ_EXTERNAL_STORAGE", "READ_MEDIA", "MANAGE_EXTERNAL_STORAGE").forEach {
            assertFalse("$it must not be declared", manifest.contains(it))
        }
    }

    @Test
    fun nothingAboutThePhotoLeavesThroughALog_anEvent_orTheClipboard() {
        listOf("PhotoSaver.kt", "PhotoSaveController.kt", "PhotoDownload.kt").forEach { name ->
            val body = code(named(name))
            listOf(
                Regex("""\bLog\.[a-z]\("""),
                Regex("""\bprintln\("""),
                Regex("""\bAnalyticsEvent\b|\bAnalyticsTracker\b|\blogEvent\b"""),
                Regex("""Crashlytics|recordException"""),
                Regex("""ClipboardManager|ClipData"""),
                Regex("""Intent\.ACTION_SEND"""),
            ).forEach { assertFalse("$name: ${it.pattern}", it.containsMatchIn(body)) }
        }
    }

    @Test
    fun theNameOfTheFileHoldsOnlyTheDate() {
        val body = code(named("PhotoDownload.kt"))
        val fileName = body.substringAfter("fun fileName(").substringBefore("/** Only a real JPEG")
        // Two arguments only: a moment and a zone. Nothing else (no first name, no place, no position) can reach it.
        assertTrue(fileName.contains("now: Instant"))
        assertTrue(fileName.contains("zone: ZoneId"))
        assertEquals(2, Regex(""":\s*[A-Z]\w+""").findAll(fileName.substringBefore(")")).count())
    }
}
