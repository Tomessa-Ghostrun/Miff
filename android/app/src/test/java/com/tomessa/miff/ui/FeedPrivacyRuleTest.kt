package com.tomessa.miff.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the Feed must never do, checked in its source (tickets FEED, criteria Q1, M1, L1, L4, V1, B4):
 * - 0 write and 0 deletion on Firestore (the Feed only reads);
 * - no analytics event, no crash-report key, no log, no clipboard: no name, place or position can leave through them;
 * - "Localiser" reads from the SERVER, once, with no listener on `positions`;
 * - no call placed, no SMS sent, no permission "contacts", "appel" or "SMS" (the system applications do it);
 * - the wording "zone à risque" does not exist (the PM's wording is "zone de danger").
 */
class FeedPrivacyRuleTest {
    private val folders =
        listOf(
            "src/main/java/com/tomessa/miff/ui/feed",
            "src/main/java/com/tomessa/miff/data/feed",
            "src/main/java/com/tomessa/miff/domain/feed",
        )

    private fun sources(): List<File> =
        folders.flatMap { dir -> File(dir).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    private fun codeLines(file: File) =
        file.readLines().mapIndexed { i, line -> i + 1 to line }.filter { (_, line) ->
            val t = line.trimStart()
            !t.startsWith("*") && !t.startsWith("//") && !t.startsWith("/*")
        }

    private fun offenders(vararg patterns: Regex): List<String> =
        sources().flatMap { file ->
            codeLines(file)
                .filter { (_, line) ->
                    patterns.any { it.containsMatchIn(line) }
                }.map { (n, _) -> "${file.name}:$n" }
        }

    @Test
    fun theSourcesAreFound() {
        assertTrue("sources not found from ${File(".").absolutePath}", sources().size >= 8)
    }

    @Test
    fun theFeedNeverWritesNorDeletes() {
        val found =
            offenders(
                Regex("""\.(set|update|delete|add|runTransaction|runBatch|commit)\("""),
                Regex("""\bwriteBatch\b|\bFieldValue\b|\bSetOptions\b"""),
            )
        assertTrue("the Feed writes: $found", found.isEmpty())
    }

    @Test
    fun nothingLeavesThroughAnalyticsCrashReportsLogsOrTheClipboard() {
        val found =
            offenders(
                Regex("""Analytics|analyticsTracker|logEvent"""),
                Regex("""Crashlytics|setCustomKey|recordException"""),
                Regex("""\bLog\.|println\(|Timber|printStackTrace"""),
                Regex("""ClipboardManager|ClipData|setPrimaryClip"""),
            )
        assertTrue("a place, a name or a position could leave through: $found", found.isEmpty())
    }

    @Test
    fun localiserReadsOnceFromTheServer_withNoListenerOnPositions() {
        val reader = File("src/main/java/com/tomessa/miff/data/feed/PositionReader.kt").readText()
        assertTrue(reader.contains("Source.SERVER"))
        assertTrue(
            !reader.contains("addSnapshotListener") && !reader.contains("Source.CACHE") &&
                !reader.contains(".whereEqualTo"),
        )
        val status = File("src/main/java/com/tomessa/miff/data/feed/StatusObserver.kt").readText()
        assertTrue("positions are never listened to", !status.contains("positions"))
    }

    @Test
    fun noCallIsPlaced_noSmsIsSent_noContactIsRead() {
        val found = offenders(Regex("""ACTION_CALL\b|SmsManager|ContactsContract|READ_CONTACTS|CALL_PHONE|SEND_SMS"""))
        assertTrue("the Feed hands over to the system applications: $found", found.isEmpty())
        val manifest = File("src/main/AndroidManifest.xml").readText()
        listOf("CALL_PHONE", "SEND_SMS", "READ_SMS", "READ_CONTACTS", "WRITE_CONTACTS", "READ_PHONE_STATE").forEach {
            assertTrue("permission $it must not be in the manifest", !manifest.contains(it))
        }
    }

    @Test
    fun theWordingZoneARisqueDoesNotExist() {
        val resources =
            listOf("src/main/res/values/strings.xml", "src/main/res/values-fr/strings.xml").map { File(it) }
        // The texts are read whole; the code without its comments (a comment may say what is forbidden).
        val inTexts =
            resources.filter { file ->
                // Only the texts of the Feed: the label of the onboarding carousel (`android_onboarding_zone_risk`) is the onboarding ticket's.
                file.readLines().filter { it.contains("name=\"feed_") }.any { line ->
                    val l = line.lowercase()
                    "zone à risque" in l || "risk zone" in l || "zones à risque" in l
                }
            }
        val inCode = offenders(Regex("zone à risque|zones à risque|risk zone", RegexOption.IGNORE_CASE))
        assertTrue(
            "\"zone à risque\" found in: ${inTexts.map { it.name }} $inCode",
            inTexts.isEmpty() && inCode.isEmpty(),
        )
    }

    @Test
    fun theThresholdsOfTheColourAreNeverWrittenAgainInTheFeed() {
        val found = offenders(Regex("""60L \* 60|2L \* 60|3_?600_?000|7_?200_?000|ORANGE_AFTER|RED_AFTER"""))
        assertTrue("1 h and 2 h live in StatusThresholds only: $found", found.isEmpty())
    }
}
