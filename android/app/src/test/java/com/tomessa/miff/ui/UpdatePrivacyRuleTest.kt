package com.tomessa.miff.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the Update must never do, checked on its SOURCE so that a later change cannot break it unnoticed (ticket Update, criteria V3, V4, U8, U9, L1; decision of
 * the PM of 2026-11-06 "une photo doit être attachée à une position"): nothing is logged, sent as an event or put on the clipboard; no file of the photo is
 * ever written (no gallery, no storage); a coordinate is written in ONE place, always with its two companions; the photo never carries the session
 * and the Update never writes the position document nor the presence; nothing deletes a photo on a refusal.
 */
class UpdatePrivacyRuleTest {
    private val main = File("src/main/java/com/tomessa/miff")

    private val updateFiles =
        listOf("domain/update", "data/update", "ui/update").flatMap { dir ->
            File(main, dir).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        } +
            listOf(
                File(main, "data/tracking/PhotoPositionSource.kt"),
                File(main, "data/feed/PhotoReader.kt"),
                File(main, "ui/feed/FeedPhoto.kt"),
                File(main, "ui/components/SystemCamera.kt"),
            )

    private fun codeLines(file: File) =
        file.readLines().filter {
            val line = it.trimStart()
            !line.startsWith("*") && !line.startsWith("//") && !line.startsWith("/*")
        }

    private fun code(file: File) = codeLines(file).joinToString("\n")

    private fun named(name: String) = updateFiles.first { it.name == name }

    private fun offenders(vararg patterns: Regex): List<String> =
        updateFiles.flatMap { file ->
            codeLines(file)
                .filter { line ->
                    patterns.any { it.containsMatchIn(line) }
                }.map { "${file.name}: ${it.trim()}" }
        }

    @Test
    fun theSourcesAreFound() {
        assertTrue("sources not found from ${File(".").absolutePath}", updateFiles.size >= 18)
    }

    @Test
    fun nothingLeavesThroughALog_anEvent_aCrashReport_orTheClipboard() {
        val found =
            offenders(
                Regex("""\bLog\.[a-z]\("""),
                Regex("""\bprintln\("""),
                Regex("""\bprintStackTrace\b"""),
                Regex("""\bTimber\b"""),
                Regex("""\bCrashlytics\b|\brecordException\b|\bFirebaseCrashlytics\b|\bsetCustomKey\b|\bsetUserId\b"""),
                Regex("""\bFirebaseAnalytics\b|\blogEvent\b|\bAnalyticsEvent\b|\bAnalyticsTracker\b"""),
                Regex("""ClipboardManager|ClipData|setPrimaryClip"""),
                Regex("""Intent\.ACTION_SEND"""),
            )
        assertEquals("a photo, a position or a name could leave through: $found", emptyList<String>(), found)
    }

    @Test
    fun noFileOfThePhotoIsEverWritten_noGallery_noStorage() {
        val found =
            offenders(
                Regex(
                    """FileOutputStream|FileWriter|\.writeBytes\(|\.writeText\(|\.outputStream\(|openFileOutput|createTempFile""",
                ),
                Regex("""MediaStore\.(Images|Downloads|Video)|insertImage|ContentValues"""),
                Regex("""getExternal|externalCacheDir|externalMediaDirs|Environment\.|DIRECTORY_PICTURES"""),
                Regex("""OutputFileOptions|ImageCapture\.OutputFile"""),
                Regex("""\bFile\("""),
            )
        assertEquals("the photo must stay in memory: $found", emptyList<String>(), found)
        // The photo is encoded into a byte array in memory.
        assertTrue(code(named("AndroidPhotoEncoder.kt")).contains("ByteArrayOutputStream"))
        // CameraX is asked for an image IN MEMORY, never for a file.
        assertTrue(code(named("CameraSurface.kt")).contains("OnImageCapturedCallback"))
    }

    @Test
    fun aCoordinateIsWrittenInOnePlaceOnly_theDocumentOfThePhoto_withItsTwoCompanions() {
        val withLat =
            updateFiles
                .filter {
                    Regex(""""(lat|lng|measuredAt)"\s+to\b""").containsMatchIn(code(it))
                }.map { it.name }
        assertEquals(listOf("UpdateWrites.kt"), withLat)
        val writes = code(named("UpdateWrites.kt"))
        val photo = writes.substringAfter("fun photo(").substringBefore("fun publish(")
        // The three keys are in the same map, side by side: never one without the others.
        assertEquals(1, Regex(""""lat"\s+to\b""").findAll(photo).count())
        assertEquals(1, Regex(""""lng"\s+to\b""").findAll(photo).count())
        assertEquals(1, Regex(""""measuredAt"\s+to\b""").findAll(photo).count())
        // The photo never carries the session (the rules refuse the key), and the position document is never written.
        assertFalse(photo.contains("deviceId"))
        assertFalse(writes.contains("/positions/"))
    }

    @Test
    fun theUpdateNeverWritesThePresence_thePlaceOfThePerson_orThePositionDocument() {
        listOf("UpdateWrites.kt", "UpdatePublisher.kt", "PhotoStore.kt").forEach { name ->
            val text = code(named(name))
            listOf("\"presence\"", "\"placeId\"", "\"presenceAt\"", "/positions/", "positions/").forEach {
                assertFalse("$name mentions $it", text.contains(it))
            }
        }
        // And the status builder of the Update does not send them either (a partial update: what is not sent is kept).
        val tracking = File(main, "domain/tracking/TrackingWrites.kt").readText()
        val photoStatus = tracking.substringAfter("fun photoStatus(").substringBefore("/** The last known position")
        listOf("\"presence\"", "\"placeId\"", "\"presenceAt\"", "\"locationAccess\"", "\"lat\"", "\"lng\"").forEach {
            assertFalse("photoStatus sends $it", photoStatus.contains(it))
        }
    }

    @Test
    fun nothingIsDeletedOnARefusal_theFeedReadsAndNeverDeletes() {
        listOf("PhotoReader.kt", "FeedPhoto.kt").forEach { name ->
            val text = code(named(name))
            assertFalse(
                "$name deletes or writes",
                Regex("""\.(delete|set|update|add|runTransaction|runBatch|commit)\(""").containsMatchIn(text),
            )
        }
        // A refusal is reduced to a calm sentence, never to "expired".
        val reader = code(named("PhotoReader.kt"))
        assertTrue(Regex("""PhotoFetch\.Denied -> \{?\s*refusalLookup\(""").containsMatchIn(reader))
    }

    @Test
    fun aPublicationHasOnePath_thePreviewOfAPhoto() {
        // The only call of the publisher is in the view model, and only for a state that holds a photo.
        val callers = updateFiles.filter { Regex("""publisher\.publish\(""").containsMatchIn(code(it)) }.map { it.name }
        assertEquals(listOf("UpdateViewModel.kt"), callers)
        val viewModel = code(named("UpdateViewModel.kt"))
        assertTrue(
            viewModel
                .substringAfter(
                    "fun onPublish()",
                ).substringBefore("publishJob")
                .contains("as? UpdateStage.Preview"),
        )
        // The builders of the lot of an update are only used by the publisher.
        val builders =
            updateFiles
                .filter {
                    Regex(
                        """UpdateWrites\.publish\(""",
                    ).containsMatchIn(code(it))
                }.map { it.name }
        assertEquals(listOf("UpdatePublisher.kt"), builders)
    }

    @Test
    fun everyClassThatCarriesAPhotoOrAPosition_hidesItInToString() {
        val carriers =
            mapOf(
                "UpdatePhoto" to "UpdatePhotoFormat.kt",
                "PhotoPlace" to "PhotoPlace.kt",
                "SharedPhoto" to "SharedPhoto.kt",
                "PhotoLocation" to "SharedPhoto.kt",
                "OpenedPhoto" to "FeedPhoto.kt",
                "PhotoCommit" to "PhotoStore.kt",
            )
        carriers.forEach { (name, file) ->
            val text = named(file).readText()
            val body = text.substringAfter("class $name(", text.substringAfter("class $name private constructor(", ""))
            assertTrue("$name is missing in $file", body.isNotEmpty())
            assertTrue(
                "$name must hide itself in toString()",
                body.substringBefore("\n}\n").contains("""toString(): String = "$name(redacted)""""),
            )
        }
        assertTrue(code(named("UpdateViewModel.kt")).contains("""toString(): String = "Preview(redacted)""""))
    }

    @Test
    fun theUpdateScreenHidesItselfFromTheRecentApps_andTheCameraIsOnlyBoundToTheScreen() {
        assertTrue(code(named("UpdateScreen.kt")).contains("RecentsPrivacy()"))
        val surface = code(named("CameraSurface.kt"))
        // Bound to the lifecycle of the screen, and released when the viewfinder leaves it.
        assertTrue(surface.contains("bindToLifecycle(owner"))
        assertTrue(surface.contains("unbindAll()"))
        // No flash, no zoom, no filter, no timer (V4).
        assertTrue(surface.contains("FLASH_MODE_OFF"))
        assertFalse(Regex("""setZoomRatio|enableTorch|TorchState|setLinearZoom""").containsMatchIn(surface))
    }

    @Test
    fun theBubbleIsAnnouncedPolitely_noSoundNoVibration_noBlockingWriteOfTheLedger() {
        val bubble = code(named("UpdateBubble.kt"))
        val incentive = bubble.substringAfter("fun IncentiveBubble(").substringBefore("fun UpdateConfirmation(")
        assertTrue(
            "the bubble is a polite live region (the ticket decides)",
            incentive.contains("LiveRegionMode.Polite"),
        )
        // The ledger is written with apply(): commit() would block the main thread (review C8).
        assertFalse(code(named("PublishLedger.kt")).contains(".commit()"))
        listOf(
            "Vibrator",
            "VibrationEffect",
            "performHapticFeedback",
            "MediaPlayer",
            "SoundPool",
            "ToneGenerator",
            "RingtoneManager",
        ).forEach {
            assertFalse("$it in an Update source", offenders(Regex(it)).isNotEmpty())
        }
    }

    @Test
    fun theBubbleHasNoTitle_noButton_noCrossAndTheTextsAreThoseOfTheMockups() {
        val strings = File("src/main/res/values-fr/strings.xml").readText()
        assertTrue(
            strings.contains(">Tu es passé en orange, indique à la Miff que tout va bien en publiant une update<"),
        )
        assertTrue(
            strings.contains(">Tu es passé en rouge, indique à la Miff que tout va bien en publiant une update<"),
        )
        val bubble =
            code(
                named("UpdateBubble.kt"),
            ).substringAfter("fun IncentiveBubble(").substringBefore("fun UpdateConfirmation(")
        assertFalse(Regex("""Button|clickable|onClick|IconButton|Icon\(""").containsMatchIn(bubble))
    }

    @Test
    fun theRealPublisherIsGivenTheListOfPublishedPositions_theDefaultOfTheClassIsNeverUsedInTheApp() {
        val container = File(main, "AppContainer.kt").readText()
        val publisher = container.substringAfter("val updatePublisher").substringBefore("val photoCleaner")
        assertTrue(publisher.contains("published = LocalPublishedPositions("))
        assertTrue(publisher.contains("handOver = ::handOverPosition"))
    }

    @Test
    fun theUpdateNeverCallsTheBuilderOfThePosition_itOnlyHandsItsMeasureToTheDetection() {
        // U8: the only writer of `positions/{moi}` is the detection (TrackingWrites.position, called by TrackingEngine); no file of the Update calls it.
        val callers = offenders(Regex("""TrackingWrites\s*\.\s*position\s*\("""))
        assertTrue("the Update writes the position itself: $callers", callers.isEmpty())
        val publisher = code(named("UpdatePublisher.kt"))
        assertTrue(publisher.contains("handOver(fix)"))
    }

    @Test
    fun theHandOverRunsInTheScopeOfTheDetection_soThatAGivenUpWaitNeverCutsTheEngineBeforeItsLocalSave() {
        val container = File(main, "AppContainer.kt").readText()
        val handOver =
            container
                .substringAfter(
                    "private suspend fun handOverPosition",
                ).substringBefore("val updatePublisher")
        assertTrue(handOver.contains("trackingScope"))
        assertTrue(handOver.contains(".async"))
        assertTrue(handOver.contains(".await()"))
    }
}
