package com.tomessa.miff.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the permissions of LOC-2a must never do, checked on the SOURCE so that a later change cannot break it unnoticed (criteria AUT7, VIE1,
 * VIE4, decisions (f), (i), (j) of the PM): the exact list of declared permissions, NO position read anywhere in this version, nothing
 * logged or sent as an event by the permission screens, the disclosure that Google Play requires, tutoiement.
 */
class PermissionPrivacyTest {
    private val main = File("src/main/java")

    private fun sources(vararg roots: String): List<File> =
        roots.flatMap { File(main, it).walkTopDown().filter { f -> f.isFile && f.extension == "kt" }.toList() }

    private fun codeLines(file: File): List<String> =
        file.readLines().filter {
            val line = it.trimStart()
            !line.startsWith("*") && !line.startsWith("//") && !line.startsWith("/*")
        }

    private val declared = Regex("""<uses-permission\s+android:name="([^"]+)"\s*/>""")
    private val sensitive =
        Regex("""permission\.(ACCESS_\w*LOCATION|FOREGROUND_SERVICE\w*|POST_NOTIFICATIONS|ACCESS_WIFI_STATE)""")

    @Test
    fun theSourceManifest_declaresExactlyTheLocationAndNotificationPermissionsOfTheTicket() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val names = declared.findAll(manifest).map { it.groupValues[1] }.toSet()
        // Declared (not removed): the three of the location, the notification one, and the network ones. Nothing else.
        assertEquals(
            setOf(
                "android.permission.INTERNET",
                "android.permission.ACCESS_NETWORK_STATE",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.ACCESS_BACKGROUND_LOCATION",
                "android.permission.POST_NOTIFICATIONS",
            ),
            names,
        )
        // No foreground service, no media location, no boot receiver: the geofencing of LOC-2b is a separate decision.
        assertFalse(manifest.contains("FOREGROUND_SERVICE"))
        assertFalse(manifest.contains("ACCESS_MEDIA_LOCATION"))
        assertFalse(manifest.contains("RECEIVE_BOOT_COMPLETED"))
    }

    @Test
    fun theMergedManifests_carryNoLocationOrServicePermissionBeyondTheThree_whenTheyAreThere() {
        val merged =
            File(
                "build/intermediates/merged_manifests",
            ).walkTopDown().filter { it.name == "AndroidManifest.xml" }.toList()
        merged.forEach { file ->
            val found = sensitive.findAll(file.readText()).map { "android.permission." + it.groupValues[1] }.toSet()
            assertEquals(
                "${file.path}: a library added a sensitive permission",
                setOf(
                    "android.permission.ACCESS_FINE_LOCATION",
                    "android.permission.ACCESS_COARSE_LOCATION",
                    "android.permission.ACCESS_BACKGROUND_LOCATION",
                    "android.permission.POST_NOTIFICATIONS",
                ),
                found,
            )
        }
    }

    @Test
    fun noPositionIsReadAnywhereInThisVersion_noLocationLibrary() {
        val forbidden =
            listOf(
                Regex("""android\.location\."""),
                Regex("""\bLocationManager\b"""),
                Regex("""\bFusedLocationProviderClient\b|\bLocationServices\b|play\.services\.location"""),
                Regex("""\bGeofencingClient\b|\bGeofencingRequest\b"""),
                Regex("""getLastKnownLocation|requestLocationUpdates|getCurrentLocation|\.lastLocation\b"""),
                Regex("""\bLocationCallback\b|\bLocationRequest\b"""),
            )
        val offenders =
            sources("com").flatMap { file ->
                codeLines(file)
                    .filter { line ->
                        forbidden.any { it.containsMatchIn(line) }
                    }.map { "${file.name}: ${it.trim()}" }
            }
        assertEquals(
            "no position may be read before LOC-2b (and never before the agreement)",
            emptyList<String>(),
            offenders,
        )
        val gradle = File("build.gradle.kts").readText() + File("../gradle/libs.versions.toml").readText()
        assertFalse(
            "no location library",
            Regex("""play-services-location|play\.services\.location""").containsMatchIn(gradle),
        )
    }

    @Test
    fun theLastKnownLocationIsOnlyEverDeletedHere_neverWritten() {
        val writes = File(main, "com/tomessa/miff/domain/miff/MiffWrites.kt").readLines()
        val positionLines = writes.filter { it.contains("/positions/") && it.contains("DocWrite(") }
        assertTrue(positionLines.size >= 5)
        positionLines.forEach { assertTrue("a position is only deleted: $it", it.contains("WriteKind.DELETE")) }
    }

    @Test
    fun thePermissionScreensLogNothing_sendNoEvent_readNoPosition() {
        val forbidden =
            listOf(
                Regex("""\bLog\.[a-z]\("""),
                Regex("""\bprintln\("""),
                Regex("""\bprintStackTrace\b"""),
                Regex("""\bTimber\b"""),
                Regex("""\bCrashlytics\b|\brecordException\b|\bFirebaseCrashlytics\b"""),
                Regex("""\bFirebaseAnalytics\b|\blogEvent\b|\bAnalyticsEvent\b|\bAnalyticsTracker\b"""),
            )
        val files =
            sources(
                "com/tomessa/miff/ui/permission",
                "com/tomessa/miff/domain/permission",
                "com/tomessa/miff/data/permission",
            ) +
                listOf(
                    File(main, "com/tomessa/miff/data/miff/ConsentRenewals.kt"),
                    File(main, "com/tomessa/miff/ui/miff/ConsentCards.kt"),
                )
        assertTrue(files.size >= 8)
        val offenders =
            files.flatMap { file ->
                codeLines(file)
                    .filter { line ->
                        forbidden.any { it.containsMatchIn(line) }
                    }.map { "${file.name}: ${it.trim()}" }
            }
        assertEquals(
            "a permission or a consent must never reach a log or an event (decision (i))",
            emptyList<String>(),
            offenders,
        )
    }

    private fun strings(path: String): Map<String, String> =
        Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(File(path).readText())
            .associate { it.groupValues[1] to it.groupValues[2] }

    private val fr get() = strings("src/main/res/values-fr/strings.xml")
    private val en get() = strings("src/main/res/values/strings.xml")

    @Test
    fun theDisclosureOfGooglePlay_isOnTheScreen_inBothLanguages() {
        val frText = fr.getValue("permission_location_disclosure").lowercase()
        // The words Google requires: location, background, app closed.
        assertTrue("localisation" in frText && "arrière-plan" in frText && "app est fermée" in frText)
        assertTrue("dernière position connue" in frText)
        val enText = en.getValue("permission_location_disclosure").lowercase()
        assertTrue("location" in enText && "background" in enText && "app is closed" in enText)
        // The lines under it say who sees it, that there is no history, and that the person can stop.
        assertTrue("qui partagent aussi" in fr.getValue("permission_location_seen_body"))
        assertTrue("Pas d'historique" in fr.getValue("permission_location_seen_body").replace("\\'", "'"))
        assertTrue("effacée" in fr.getValue("permission_location_control_body"))
    }

    @Test
    fun theTextOfTheConsentV3_hasTheOptionAnote_andNoMoreYouCanRefuse() {
        val note = fr.getValue("miff_consent3_system_note")
        assertTrue("« Toujours »" in note)
        assertFalse("Tu peux le refuser" in note)
        assertTrue("dernière position connue" in fr.getValue("miff_consent3_create"))
        assertTrue("dernière position connue" in fr.getValue("miff_consent3_join"))
        assertTrue("même quand l'app est fermée" in fr.getValue("miff_consent3_closed_body").replace("\\'", "'"))
    }

    @Test
    fun everyNewTextIsInTutoiement_andCalm() {
        val keys =
            fr.keys.filter {
                it.startsWith("permission_") || it.startsWith("miff_consent3_") ||
                    it.startsWith("miff_renewal_")
            }
        assertTrue(keys.size >= 40)
        val vouvoiement = Regex("""(?i)\b(vous|votre|vos|autorisez|acceptez|nous avons besoin de votre)\b""")
        keys.forEach { key ->
            assertFalse("$key is not in tutoiement", vouvoiement.containsMatchIn(fr.getValue(key)))
            assertTrue("$key has no English text", en.containsKey(key))
        }
        // "zone de danger" (never "zone à risque") is the wording of the product; none of these texts needs either.
        keys.forEach { assertFalse(it, fr.getValue(it).contains("zone à risque")) }
    }
}
