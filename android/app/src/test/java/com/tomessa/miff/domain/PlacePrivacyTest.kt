package com.tomessa.miff.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the places must never do (ticket LOC-1, criteria ADR3, ADR4, VIE1 for the part that is the app's own, QA3), checked on the SOURCE so that
 * a later change cannot break it unnoticed: no address, name or coordinate in a log, an event or a crash report; no key committed;
 * nothing but HTTPS; no Wi-Fi permission (the location ones are LOC-2a: see PermissionPrivacyTest).
 */
class PlacePrivacyTest {
    private val roots =
        listOf(
            "src/main/java/com/tomessa/miff/domain/place",
            "src/main/java/com/tomessa/miff/data/place",
            "src/main/java/com/tomessa/miff/ui/place",
        )

    private fun placeSources(): List<File> =
        roots.flatMap { root -> File(root).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    private fun codeLines(file: File): List<String> =
        file.readLines().filter {
            val line = it.trimStart()
            !line.startsWith("*") && !line.startsWith("//") && !line.startsWith("/*")
        }

    @Test
    fun theSourcesAreFound() {
        assertTrue(placeSources().size >= 15)
    }

    @Test
    fun nothingIsLogged_noExceptionIsReported_noEventCarriesAPlace() {
        val forbidden =
            listOf(
                Regex("""\bLog\.[a-z]\("""),
                Regex("""\bprintln\("""),
                Regex("""\bprintStackTrace\b"""),
                Regex("""\bTimber\b"""),
                Regex("""\bCrashlytics\b|\brecordException\b|\bFirebaseCrashlytics\b"""),
                Regex("""\bFirebaseAnalytics\b|\blogEvent\b"""),
                Regex("""\bToast\.makeText\([^)]*(name|address|label|title)\b"""),
            )
        val offenders =
            placeSources().flatMap { file ->
                codeLines(file)
                    .filter { line ->
                        forbidden.any { it.containsMatchIn(line) }
                    }.map { "${file.name}: ${it.trim()}" }
            }
        assertEquals(
            "a place must never reach a log, an event or a crash report: $offenders",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun theOnlyEventsOfThePlacesAreTheOffer_withAFixedTrigger() {
        val events =
            placeSources().flatMap { file ->
                codeLines(file).filter { "AnalyticsEvent." in it }.map { it.trim() }
            }
        assertTrue(events.isNotEmpty())
        events.forEach { line ->
            assertTrue(
                "unexpected event: $line",
                // The offer is shown / closed with a FIXED trigger of the contract, chosen by paywallTrigger (3rd place, 2nd Miff): nothing else.
                "PaywallShown(trigger)" in line ||
                    "PaywallDismissed(trigger)" in line ||
                    Regex("""PaywallReason\.\w+ -> AnalyticsEvent\.PaywallTrigger\.(EXTRA_TRUSTED_PLACE|EXTRA_MIFF)""").containsMatchIn(line) ||
                    line.contains("fun paywallTrigger(reason: PaywallReason): AnalyticsEvent.PaywallTrigger?") ||
                    line.startsWith("import "),
            )
        }
    }

    @Test
    fun everyAddressOfTheNetwork_isHttps() {
        val clients = File("src/main/java/com/tomessa/miff/data/place/AddressSearchClients.kt").readText()
        assertTrue(clients.contains("HttpsURLConnection"))
        assertFalse(clients.contains("HttpURLConnection(") || clients.contains("\"http://"))
        assertTrue(clients.contains("instanceFollowRedirects = false"))
        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("""require(mapStyleUrl.startsWith("https://"))"""))
        assertTrue(gradle.contains("""require(photonUrl.startsWith("https://"))"""))
        val network = File("src/main/res/xml/network_security_config.xml").readText()
        assertTrue(network.contains("""cleartextTrafficPermitted="false""""))
    }

    @Test
    fun noKeyIsCommitted_theFallbackIsOffUnlessAKeyIsGivenAtBuildTime() {
        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("""providers.gradleProperty("miff.geoapifyKey").getOrElse("")"""))
        // No literal key anywhere in the sources of the places or of the configuration.
        val keyLike = Regex("""(?i)(api[_-]?key|apikey)\s*=\s*"[A-Za-z0-9]{8,}"""")
        val offenders =
            (placeSources() + File("build.gradle.kts") + File("src/main/AndroidManifest.xml"))
                .filter { keyLike.containsMatchIn(it.readText()) }
                .map { it.name }
        assertEquals(emptyList<String>(), offenders)
    }

    private val wifiPermission = Regex("""ACCESS_WIFI_STATE""")

    @Test
    fun theSourceManifest_removesTheWifiStateOfTheMapLibrary() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val elements =
            Regex("""<uses-permission[^>]*?/>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(manifest)
                .map { it.value }
                .toList()
        val mentions = elements.filter { wifiPermission.containsMatchIn(it) }
        assertEquals("the removal of ACCESS_WIFI_STATE is missing", 1, mentions.size)
        mentions.forEach { assertTrue("not a removal: $it", it.contains("""tools:node="remove"""")) }
        // LOC-2a: the location permissions are now DECLARED, on purpose: PermissionPrivacyTest checks the exact list.
    }

    @Test
    fun theMergedManifests_doNotDeclareTheWifiState_whenTheyAreThere() {
        val merged =
            File("build/intermediates/merged_manifests")
                .walkTopDown()
                .filter { it.name == "AndroidManifest.xml" }
                .toList()
        merged.forEach { file ->
            assertFalse("${file.path} declares ACCESS_WIFI_STATE", wifiPermission.containsMatchIn(file.readText()))
        }
    }

    @Test
    fun theMapShowsNoPositionOfAPerson_noGesture_noLayerOfMyPosition() {
        val map = File("src/main/java/com/tomessa/miff/ui/place/PlaceMap.kt").readText()
        assertTrue(map.contains("setAllGesturesEnabled(false)"))
        assertFalse(map.contains("LocationComponent"))
        assertFalse(map.contains("locationComponent"))
    }
}
