package com.tomessa.miff.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the permissions of LOC-2a and the detection of LOC-2b must never do, checked on the SOURCE so that a later change cannot break it unnoticed
 * (criteria AUT7, VIE1, VIE4, decisions (f), (i), (j) of the PM): the exact list of declared permissions, a position read ONLY in data/tracking (see
 * TrackingPrivacyTest), nothing logged or sent as an event by the permission screens, the disclosure that Google Play requires, tutoiement.
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
        Regex(
            """permission\.(ACCESS_\w*LOCATION\w*|FOREGROUND_SERVICE\w*|POST_NOTIFICATIONS|ACCESS_WIFI_STATE|NEARBY_\w+|ACTIVITY_RECOGNITION|CAMERA|RECORD_AUDIO|READ_MEDIA_\w+|READ_EXTERNAL_STORAGE|WRITE_EXTERNAL_STORAGE)""",
        )

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
                // LOC-2b: the zones of the geofencing are erased when the phone restarts; they are armed again at the restart (criterion AND2).
                "android.permission.RECEIVE_BOOT_COMPLETED",
                // UPDATE-1: the viewfinder of the Update screen. The ONLY new permission: criterion P4 of the Update ticket.
                "android.permission.CAMERA",
            ),
            names,
        )
        // No foreground service (the system delivers the zones and the moves to the receiver), no media location.
        assertFalse(manifest.contains("FOREGROUND_SERVICE"))
        assertFalse(manifest.contains("ACCESS_MEDIA_LOCATION"))
    }

    @Test
    fun theCameraIsTheOnlyNewPermissionOfTheUpdate_noStorage_noPhotoLibrary_noMicrophone_andNeverRequired() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        // Nothing of the photo library, the storage or the microphone: the photo is taken in memory and never written anywhere (criteria P4, V4).
        listOf(
            "READ_MEDIA_IMAGES",
            "READ_MEDIA_VISUAL_USER_SELECTED",
            "READ_EXTERNAL_STORAGE",
            "WRITE_EXTERNAL_STORAGE",
            "MANAGE_EXTERNAL_STORAGE",
            "RECORD_AUDIO",
        ).forEach { assertFalse("$it must never be declared", manifest.contains(it)) }
        // The camera and the front camera are NOT required: a phone without them can install Miff (criterion P4).
        listOf("android.hardware.camera", "android.hardware.camera.front").forEach { feature ->
            val declaration =
                Regex("""<uses-feature\s+android:name="${Regex.escape(feature)}"\s+android:required="false"\s*/>""")
            assertTrue("$feature must be declared as not required", declaration.containsMatchIn(manifest))
        }
        assertFalse(Regex("""android:required="true"""").containsMatchIn(manifest))
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
                    "android.permission.CAMERA",
                ),
                found,
            )
        }
    }

    @Test
    fun aReleaseBuildRefusesTheRelaxedLocationBlocking_unlessAskedOnPurpose() {
        val gradle = File("build.gradle.kts").readText()
        // The default is "blocking" ...
        assertTrue(gradle.contains("""providers.gradleProperty("miff.locationBlocking").getOrElse("true")"""))
        // ... and a release build (release, releaseLike) fails with the relaxed value unless the second property is given too.
        assertTrue(gradle.contains("miff.allowRelaxedLocationInRelease"))
        assertTrue(gradle.contains("preReleaseBuild") && gradle.contains("preReleaseLikeBuild"))
        assertTrue(gradle.contains("GradleException"))
    }

    @Test
    fun aPositionIsReadOnlyInTheTrackingPackage_andTheOnlyLocationLibraryIsPlayServicesLocation() {
        val forbidden =
            listOf(
                Regex("""android\.location\."""),
                Regex("""\bLocationManager\b"""),
                Regex("""\bFusedLocationProviderClient\b|\bLocationServices\b|play\.services\.location"""),
                Regex("""\bGeofencingClient\b|\bGeofencingRequest\b"""),
                Regex("""getLastKnownLocation|requestLocationUpdates|getCurrentLocation|\.lastLocation\b"""),
                Regex("""\bLocationCallback\b|\bLocationRequest\b|\bLocationListener\b"""),
                Regex("""LocationManagerCompat|LOCATION_SERVICE|ActivityRecognition"""),
            )
        // Everything that asks the system for a position or a zone lives in data/tracking: nowhere else (criteria AUT7, VIE1).
        val offenders =
            sources("com")
                .filter { !it.path.replace('\\', '/').contains("/data/tracking/") }
                .flatMap { file ->
                    codeLines(file)
                        .filter { line ->
                            forbidden.any { it.containsMatchIn(line) }
                        }.map { "${file.name}: ${it.trim()}" }
                }
        assertEquals(
            "a position is only read by the detection (data/tracking), after the agreement",
            emptyList<String>(),
            offenders,
        )
        val gradle = File("build.gradle.kts").readText() + File("../gradle/libs.versions.toml").readText()
        // LOC-2b: the one location library is Google Play services location (geofencing); no other one.
        assertTrue(Regex("""play-services-location""").containsMatchIn(gradle))
        assertFalse(
            Regex(
                """(?i)mapbox-.*location|maps-utils|fused-location-provider-compat|com\.huawei""",
            ).containsMatchIn(gradle),
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
