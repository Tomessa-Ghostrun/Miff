package com.tomessa.miff.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the push (SRV-2) must never do, checked on the SOURCE so that a later change cannot break it unnoticed: no log, no event, no crash report of a token or of a
 * message; Firebase Messaging only in data/push; no read of Firestore when a message arrives; no token written to the disk; a service that is not exported; no new
 * permission; the automatic start of the token off.
 */
class PushPrivacyTest {
    private val main = File("src/main/java/com/tomessa/miff")
    private val pushDir = File(main, "data/push")

    private fun codeLines(file: File): List<String> =
        file.readLines().filter {
            val line = it.trimStart()
            !line.startsWith("*") && !line.startsWith("//") && !line.startsWith("/*")
        }

    private val pushFiles get() = pushDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun nothingOfTheTokenOrOfAMessageIsLoggedSentAsAnEventOrPutInACrashReport() {
        val forbidden =
            Regex(
                """android\.util\.Log|\bLog\.[dviwe]\(|println\(|printStackTrace|AnalyticsTracker|AnalyticsEvent|FirebaseAnalytics|Crashlytics|recordException|setCustomKey|Timber""",
            )
        val offenders =
            pushFiles.flatMap { f ->
                codeLines(f).filter { forbidden.containsMatchIn(it) }.map { "${f.name}: ${it.trim()}" }
            }
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun firebaseMessagingIsUsedOnlyInThePushPackage() {
        val users =
            File(main, ".")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { f -> codeLines(f).any { it.contains("com.google.firebase.messaging") } }
                .map { it.name }
                .sorted()
                .toList()
        assertEquals(listOf("MiffMessagingService.kt", "PushLocalState.kt"), users)
    }

    @Test
    fun theHandlingOfAMessageReadsNothingFromTheServer_noFirestoreInTheHandlerNorInTheNotificationSide() {
        listOf("PushMessageHandler.kt", "MiffMessagingService.kt").forEach { name ->
            val code = codeLines(File(pushDir, name)).joinToString("\n")
            assertFalse(
                "$name must not touch Firestore",
                Regex("""firestore|\.collection\(|\.document\(""", RegexOption.IGNORE_CASE).containsMatchIn(code),
            )
        }
    }

    @Test
    fun theDocumentOfTheRegistryHoldsTheSevenKeysOfTheContractAndNothingElse() {
        val code = codeLines(File(pushDir, "DeviceTokenRegistry.kt")).joinToString("\n")
        val written = Regex(""""([A-Za-z]+)" to """).findAll(code).map { it.groupValues[1] }.toSet()
        assertEquals(
            setOf(
                "token",
                "appVersion",
                "tz",
                "notify",
                "updatedAt",
                "platform",
                "createdAt",
                "updates",
            ),
            written,
        )
        // No position, no name, no e-mail, no phone number, no advertising identifier, no identifier of a Miff in the document.
        assertFalse(
            Regex(
                """(?i)\blat\b|\blng\b|latitude|longitude|email|phone|name|miffId|adid""",
            ).containsMatchIn(written.joinToString(" ")),
        )
    }

    @Test
    fun theTokenItselfIsNeverWrittenToTheDisk_onlyItsFingerprint() {
        val local = codeLines(File(pushDir, "PushLocalState.kt")).joinToString("\n")
        // The preferences hold a fingerprint, a zone, a time, flags: no key called "token" is ever written.
        assertFalse(Regex("""put(String|Long|Boolean)\("token""").containsMatchIn(local))
        assertFalse(local.contains("KEY_TOKEN"))
        assertTrue(local.contains("KEY_FINGERPRINT"))
    }

    @Test
    fun theServiceIsNotExported_andTheAutomaticStartOfTheTokenIsOff_andNoPermissionWasAdded() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val service =
            Regex(
                """<service\s+android:name="\.data\.push\.MiffMessagingService"\s+android:exported="false">(.*?)</service>""",
                RegexOption.DOT_MATCHES_ALL,
            ).find(manifest)
        assertTrue("MiffMessagingService must be declared and not exported", service != null)
        val actions =
            Regex("<action android:name=\"([^\"]+)\"")
                .findAll(service!!.groupValues[1])
                .map {
                    it.groupValues[1]
                }.toList()
        assertEquals(listOf("com.google.firebase.MESSAGING_EVENT"), actions)
        assertTrue(manifest.contains("""android:name="firebase_messaging_auto_init_enabled""""))
        assertTrue(Regex("""firebase_messaging_auto_init_enabled"\s+android:value="false"""").containsMatchIn(manifest))
        // The permissions the app declares by itself are unchanged by the push: POST_NOTIFICATIONS was already there.
        val declared =
            Regex("""<uses-permission\s+android:name="([^"]+)"\s*/>""")
                .findAll(manifest)
                .map {
                    it.groupValues[1]
                }.toSet()
        assertFalse(declared.contains("android.permission.FOREGROUND_SERVICE"))
        assertFalse(declared.contains("android.permission.WAKE_LOCK"))
    }

    @Test
    fun thePushTextsHoldNoWordThatWorriesNorTheNameOfAMiff() {
        val fr = File("src/main/res/values-fr/strings.xml").readText()
        val en = File("src/main/res/values/strings.xml").readText()
        assertTrue(fr.contains("""<string name="notif_circle_update_title">%1${'$'}s a donné des nouvelles</string>"""))
        assertTrue(fr.contains("""<string name="notif_circle_update_body">Retrouve-les dans ta Miff.</string>"""))
        assertTrue(en.contains("""<string name="notif_circle_update_title">%1${'$'}s checked in</string>"""))
        assertTrue(en.contains("""<string name="notif_circle_update_body">See it in your Miff.</string>"""))
        // The body is fixed: no placeholder (no name of a Miff, no place, no hour can slip in).
        listOf(fr, en).forEach { xml ->
            val body = Regex("""<string name="notif_circle_update_body">(.*?)</string>""").find(xml)!!.groupValues[1]
            assertFalse(body.contains("%"))
        }
    }
}
