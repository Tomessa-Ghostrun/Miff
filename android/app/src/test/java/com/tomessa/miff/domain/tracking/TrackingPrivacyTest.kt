package com.tomessa.miff.domain.tracking

import com.tomessa.miff.domain.miff.LocationAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the detection must never do, checked on the SOURCE so that a later change cannot break it unnoticed (criteria AUT7, VIE1, VIE2, VIE4, POS5, POS6,
 * AND1): the location that comes with a geofence event is never read, a coordinate is written in ONE place only, nothing is logged or sent as an
 * event, the journal of the detection is not in the release.
 */
class TrackingPrivacyTest {
    private val main = File("src/main/java/com/tomessa/miff")

    private fun kotlinFiles(root: File) = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun codeLines(file: File): List<String> =
        file.readLines().filter {
            val line = it.trimStart()
            !line.startsWith("*") && !line.startsWith("//") && !line.startsWith("/*")
        }

    private fun trackingSources() =
        kotlinFiles(File(main, "data/tracking")) + kotlinFiles(File(main, "domain/tracking")) +
            kotlinFiles(File("src/debug/java/com/tomessa/miff/data/tracking")) +
            kotlinFiles(File("src/release/java/com/tomessa/miff/data/tracking"))

    @Test
    fun theSourcesAreFound() {
        assertTrue(trackingSources().size >= 15)
    }

    @Test
    fun theGeofenceReceiver_neverReadsTheLocationThatComesWithAnEvent() {
        val receiver = codeLines(File(main, "data/tracking/TrackingReceiver.kt")).joinToString("\n")
        // `triggeringLocation` is the position the system attaches to the event: not read (decision of the ticket, criterion VIE1).
        assertFalse(receiver.contains("triggeringLocation"))
        assertFalse(receiver.contains("getTriggeringLocation"))
        // Only the transition and the identifiers of the zones are taken from the event.
        assertTrue(receiver.contains("event.geofenceTransition"))
        assertTrue(receiver.contains("triggeringGeofences"))
        // The one place where a location of the provider is read in the receiver is the report of a move asked for by the engine.
        assertEquals(1, Regex("""LocationResult\.extractResult""").findAll(receiver).count())
        kotlinFiles(main).filter { it.name != "TrackingReceiver.kt" }.forEach {
            val code = codeLines(it).joinToString("\n")
            assertFalse("${it.name} reads the location of a geofence event", code.contains("triggeringLocation"))
        }
    }

    @Test
    fun aCoordinateIsWrittenToFirestoreInOnePlaceOnly_theLastPositionOfAMiff() {
        // The documents of a status never carry a coordinate: nothing of the tracking package builds a map with "lat" or "lng" except TrackingWrites.position.
        val offenders =
            trackingSources()
                .filter { it.name != "TrackingWrites.kt" && it.name != "TrackingLocalStore.kt" }
                .flatMap { file ->
                    codeLines(file)
                        .filter { Regex(""""(lat|lng|latitude|longitude)"\s+to\b""").containsMatchIn(it) }
                        .map { "${file.name}: ${it.trim()}" }
                }
        assertEquals(emptyList<String>(), offenders)
        val writes = codeLines(File(main, "domain/tracking/TrackingWrites.kt")).joinToString("\n")
        assertEquals(
            "only the position document carries lat and lng",
            1,
            Regex(""""lat"\s+to\b""").findAll(writes).count(),
        )
        assertTrue(writes.contains("/positions/"))
        // The status builders do not mention a coordinate at all.
        val status = writes.substringAfter("fun statusUpdate").substringBefore("fun accessUpdate")
        assertFalse(Regex("""(?i)\blat\b|\blng\b|latitude|longitude""").containsMatchIn(status))
    }

    @Test
    fun theDetectionLogsNothing_sendsNoEvent_reportsNoException() {
        val forbidden =
            listOf(
                Regex("""\bLog\.[a-z]\("""),
                Regex("""\bprintln\("""),
                Regex("""\bprintStackTrace\b"""),
                Regex("""\bTimber\b"""),
                Regex("""\bCrashlytics\b|\brecordException\b|\bFirebaseCrashlytics\b"""),
                Regex("""\bFirebaseAnalytics\b|\blogEvent\b|\bAnalyticsEvent\b|\bAnalyticsTracker\b"""),
                Regex("""\bsetCustomKey\b|\bsetUserId\b"""),
                Regex("""ClipboardManager|ClipData|Intent\.ACTION_SEND|Toast\.makeText"""),
            )
        val offenders =
            trackingSources().flatMap { file ->
                codeLines(file)
                    .filter { line -> forbidden.any { it.containsMatchIn(line) } }
                    .map { "${file.name}: ${it.trim()}" }
            }
        assertEquals(
            "the detection must never reach a log, an event, a report, the clipboard or a share",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun everyClassThatCarriesAPositionOrAZone_hidesItInToString() {
        val carriers =
            listOf(
                "PlaceFact",
                "TrackedZone",
                "Fix",
                "WrittenPosition",
                "LocalPlace",
                "LocalMiff",
                "LocalState",
                "PendingStatus",
                "MemberStatusFacts",
                "ZoneRef",
            )
        val text = trackingSources().joinToString("\n") { it.readText() }
        carriers.forEach { name ->
            val body = text.substringAfter("class $name(", "")
            assertTrue("$name is missing", body.isNotEmpty())
            assertTrue(
                "$name must hide itself in toString()",
                body.substringBefore("\n}\n").contains("""toString(): String = "$name(redacted)""""),
            )
        }
    }

    @Test
    fun theJournalOfTheDetection_isInTheDebugSourcesOnly_theReleaseHasNone() {
        val debug = File("src/debug/java/com/tomessa/miff/data/tracking/DetectionJournals.kt").readText()
        val release = File("src/release/java/com/tomessa/miff/data/tracking/DetectionJournals.kt").readText()
        // The writer to a file, and the marker the CI looks for in the release, are in the debug sources.
        assertTrue(debug.contains("class FileDetectionJournal"))
        assertTrue(debug.contains("DETECTIONJOURNAL"))
        // The release gives the journal that does nothing, and holds neither the writer nor the marker.
        assertTrue(release.contains("NoDetectionJournal"))
        assertFalse(release.contains("FileDetectionJournal"))
        assertFalse(release.contains("DETECTIONJOURNAL"))
        assertFalse(release.contains("writeText") || release.contains("File("))
        // The main sources know the journal only as an interface: no file writer there.
        kotlinFiles(main).forEach {
            val code = codeLines(it).joinToString("\n")
            assertFalse(
                "${it.name} writes the journal itself",
                code.contains("FileDetectionJournal") || code.contains("DETECTIONJOURNAL"),
            )
        }
        // Whatever the release gets, it records nothing and has nothing to show.
        assertTrue(NoDetectionJournal.lines().isEmpty())
        NoDetectionJournal.record(JournalKind.ARMED, "home", 10)
        assertTrue(NoDetectionJournal.lines().isEmpty())
        assertSame(NoDetectionJournal, NoDetectionJournal)
    }

    @Test
    fun theCiLooksForTheJournalInTheReleaseBundle() {
        val workflow = File("../../.github/workflows/android.yml").readText()
        assertTrue(
            "the CI must check that the release holds no journal of the detection",
            workflow.contains("DETECTIONJOURNAL"),
        )
    }

    @Test
    fun theJournalCanOnlySayAClosedListOfThings_noFreeText() {
        val journal = codeLines(File(main, "domain/tracking/DetectionJournal.kt")).joinToString("\n")
        // `record` takes a kind, a place identifier and a battery level: no string for a message, no coordinate.
        val signature = journal.substringAfter("fun record(").substringBefore(")")
        assertFalse(Regex("""(?i)\bmessage\b|\btext\b|\bdetail\b|\blat\b|\blng\b|Double""").containsMatchIn(signature))
        assertTrue(signature.contains("kind: JournalKind"))
    }

    @Test
    fun theManifest_declaresOneNonExportedReceiver_andNoService() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val receiver =
            Regex(
                """<receiver[^>]*?>""",
                RegexOption.DOT_MATCHES_ALL,
            ).findAll(manifest).map { it.value }.toList()
        assertEquals("one receiver for the whole detection", 1, receiver.size)
        assertTrue(receiver.single().contains("""android:exported="false""""))
        assertTrue(receiver.single().contains("data.tracking.TrackingReceiver"))
        assertFalse(manifest.contains("<service"))
        // Only the system broadcasts of the restart and of the update are filtered on: the other actions are those of our own PendingIntents.
        assertTrue(manifest.contains("android.intent.action.BOOT_COMPLETED"))
        assertTrue(manifest.contains("android.intent.action.MY_PACKAGE_REPLACED"))
    }

    @Test
    fun thePendingIntents_areExplicit_neverAnImplicitIntent() {
        val intents = codeLines(File(main, "data/tracking/AndroidTracking.kt")).joinToString("\n")
        assertTrue(intents.contains("Intent(context.applicationContext, TrackingReceiver::class.java)"))
        assertFalse(Regex("""Intent\("com\.""").containsMatchIn(intents))
    }

    @Test
    fun locationAccessIsOneOfTheFourValuesOfTheRules() {
        assertEquals(
            setOf("background", "foreground", "denied", "unknown"),
            LocationAccess.entries.map { it.value }.toSet(),
        )
    }
}
