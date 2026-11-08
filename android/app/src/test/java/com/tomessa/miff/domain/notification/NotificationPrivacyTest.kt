package com.tomessa.miff.domain.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the notifications must never do, checked on the SOURCE so that a later change cannot break it unnoticed (ticket NOTIF-P0, criteria N-M8, N-C3,
 * N-C4, N-T3, N-P0-10, N-P0-11): no exact alarm, nothing logged or sent as an event, no word of a place in the pure rules, and no server in the part
 * of the notifications that has nothing to do with the server.
 */
class NotificationPrivacyTest {
    private val main = File("src/main/java/com/tomessa/miff")

    private fun kotlinFiles(dir: String): List<File> =
        File(main, dir).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun codeLines(file: File): List<String> =
        file.readLines().filter {
            val line = it.trimStart()
            !line.startsWith("*") && !line.startsWith("//") && !line.startsWith("/*")
        }

    private val notificationDirs = listOf("domain/notification", "data/notification", "ui/notification")

    @Test
    fun noExactAlarmIsDeclaredOrUsed_criterionNM8() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val declared =
            Regex("""<uses-permission\s+android:name="([^"]+)"\s*/>""")
                .findAll(manifest)
                .map {
                    it.groupValues[1]
                }.toSet()
        assertFalse(declared.contains("android.permission.SCHEDULE_EXACT_ALARM"))
        assertFalse(declared.contains("android.permission.USE_EXACT_ALARM"))
        // The only notification permission of the manifest is the one asked in the system window (Android 13+).
        assertTrue(declared.contains("android.permission.POST_NOTIFICATIONS"))
        val forbidden =
            Regex(
                """\.setExact\(|setExactAndAllowWhileIdle|setAlarmClock|canScheduleExactAlarms|SCHEDULE_EXACT_ALARM|USE_EXACT_ALARM""",
            )
        val offenders =
            File(main, ".")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { f ->
                    codeLines(f).filter { forbidden.containsMatchIn(it) }.map { "${f.name}: ${it.trim()}" }
                }.toList()
        assertEquals("a reminder uses an INEXACT alarm only", emptyList<String>(), offenders)
    }

    @Test
    fun theNotificationCodeNeverLogsNorSendsAnEvent_criterionNT3() {
        val forbidden =
            Regex(
                """android\.util\.Log|\bLog\.[dviwe]\(|println\(|printStackTrace|AnalyticsTracker|AnalyticsEvent|FirebaseAnalytics|Crashlytics|recordException|setCustomKey""",
            )
        val offenders =
            notificationDirs.flatMap { dir ->
                kotlinFiles(dir).flatMap { f ->
                    codeLines(f).filter { forbidden.containsMatchIn(it) }.map { "${f.name}: ${it.trim()}" }
                }
            }
        assertEquals(
            "nothing of a notification is logged, sent as an event or put in a crash report",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun thePureRulesAreFreeOfAndroidAndFirebase_andOfAnyPlace() {
        val forbidden =
            Regex(
                """import android\.|import androidx\.|com\.google\.|firebase|latitude|longitude|\blat\b|\blng\b|address|placeName""",
                RegexOption.IGNORE_CASE,
            )
        val offenders =
            kotlinFiles("domain/notification").flatMap { f ->
                codeLines(f).filter { forbidden.containsMatchIn(it) }.map { "${f.name}: ${it.trim()}" }
            }
        assertEquals(
            "domain/notification is pure: no Android, no Firebase, no position, no address",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun theCentreTheChoicesAndTheScreensTouchNoServer_criterionNC3() {
        // The notifications never touch Firestore from their own packages: the ONE reading of N3 and N4 lives in data/feed/CircleStatusReader.kt (tested below).
        val offenders =
            notificationDirs
                .filter { it != "domain/notification" }
                .flatMap { dir -> kotlinFiles(dir) }
                .flatMap { f ->
                    codeLines(f)
                        .filter {
                            Regex(
                                """com\.google\.firebase|FirebaseFirestore|\.collection\(|\.document\(""",
                            ).containsMatchIn(it)
                        }.map { "${f.name}: ${it.trim()}" }
                }
        assertEquals(
            "the centre, the choices and the screens never read or write Firestore",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun theOnlyReadingOfTheServerIsTheVerificationOfN3AndN4_aSingleCollectionFromTheServer_andNeverAWrite() {
        val reader = File(main, "data/feed/CircleStatusReader.kt")
        assertTrue(reader.exists())
        val code = codeLines(reader).joinToString("\n")
        assertTrue(code.contains(".collection(\"status\")") && code.contains("Source.SERVER"))
        assertEquals(
            "one reading, no write",
            emptyList<String>(),
            listOf(
                ".set(",
                ".update(",
                ".delete(",
                "runTransaction",
                "batch()",
                "addSnapshotListener",
            ).filter {
                code.contains(it)
            },
        )
        // Nothing else of the notifications reads the server (they all go through the interface).
        val users =
            File(main, ".")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.name != "CircleStatusReader.kt" }
                .filter { f -> codeLines(f).any { it.contains("FirestoreCircleStatusReader") } }
                .map { it.name }
                .toList()
        assertEquals(listOf("AppContainer.kt"), users)
    }

    @Test
    fun theReceiverIsNotExported_listensToFourSystemActionsOnly_andNoForegroundServiceOrWorkManagerIsNeeded() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val receiver =
            Regex(
                """<receiver\s+android:name="\.data\.notification\.NotificationAlarmReceiver"\s+android:exported="false">(.*?)</receiver>""",
                RegexOption.DOT_MATCHES_ALL,
            ).find(manifest)
        assertNotNull("NotificationAlarmReceiver must be declared, not exported", receiver)
        val actions =
            Regex("<action android:name=\"([^\"]+)\"")
                .findAll(receiver!!.groupValues[1])
                .map {
                    it.groupValues[1]
                }.toSet()
        assertEquals(
            setOf(
                "android.intent.action.BOOT_COMPLETED",
                "android.intent.action.MY_PACKAGE_REPLACED",
                "android.intent.action.TIME_SET",
                "android.intent.action.TIMEZONE_CHANGED",
            ),
            actions,
        )
        assertFalse(manifest.contains("FOREGROUND_SERVICE"))
        assertFalse(
            "no new library for the reminders",
            File("build.gradle.kts").readText().contains("androidx.work") ||
                File("build.gradle.kts").readText().contains("libs.androidx.work"),
        )
        // The alarm of a reminder is an inexact one: the API is named once, in one place.
        val users =
            File(main, ".")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { f -> codeLines(f).any { it.contains("setAndAllowWhileIdle") } }
                .map { it.name }
                .toList()
        assertEquals(listOf("NotificationAlarms.kt"), users)
    }

    @Test
    fun theTextsOfTheNotificationsHoldNoNameNoHourNoPlace_criterionNM7() {
        val fr = File("src/main/res/values-fr/strings.xml").readText()
        val en = File("src/main/res/values/strings.xml").readText()
        listOf("n1", "n2", "n3", "n4", "n6a").forEach { id ->
            listOf(fr, en).forEach { xml ->
                listOf("title", "body").forEach { part ->
                    val value =
                        Regex("""<string name="notif_${id}_$part">(.*?)</string>""").find(xml)?.groupValues?.get(1)
                    // A notification arrives late sometimes: its text says nothing that a delay could make false (no hour, no duration, no name, no placeholder).
                    assertTrue("notif_${id}_$part is missing", value != null)
                    assertFalse("notif_${id}_$part has a placeholder: $value", value!!.contains("%"))
                    assertFalse("notif_${id}_$part has a digit: $value", value.any { it.isDigit() })
                }
            }
        }
        // The words of the PM, mot pour mot (decision of the ticket, section 20.1).
        assertTrue(fr.contains("""<string name="notif_n1_title">Tu es passé orange</string>"""))
        assertTrue(fr.contains("""<string name="notif_n1_body">Indique à ta Miff que tout va bien.</string>"""))
        assertTrue(fr.contains("""<string name="notif_n2_title">Tu es passé rouge</string>"""))
        assertTrue(fr.contains("""<string name="notif_n3_title">Ta Miff est passée en orange</string>"""))
        assertTrue(fr.contains("""<string name="notif_n3_body">Prends des nouvelles de tes proches.</string>"""))
        assertTrue(fr.contains("""<string name="notif_n4_title">Ta Miff est passée en rouge</string>"""))
        assertTrue(fr.contains("""<string name="notif_n6a_title">Tu es dans une zone de danger de ta Miff</string>"""))
        assertTrue(
            fr.contains(
                """<string name="notif_n6a_body">Pendant que tu y es, tes proches voient un état différent.</string>""",
            ),
        )
        // Tutoiement everywhere in the texts of the notifications.
        val notifStrings =
            Regex("""<string name="notif_[a-z0-9_]+">(.*?)</string>""")
                .findAll(fr)
                .map {
                    it.groupValues[1]
                }.toList()
        val vouvoiement = Regex("""\b(vous|votre|vos|veuillez)\b""", RegexOption.IGNORE_CASE)
        assertEquals(emptyList<String>(), notifStrings.filter { vouvoiement.containsMatchIn(it) })
        // Nothing alarming nor guilt-tripping.
        val alarming = Regex("""\b(toujours pas|enfin|vite|urgent)\b""", RegexOption.IGNORE_CASE)
        assertEquals(emptyList<String>(), notifStrings.filter { alarming.containsMatchIn(it) })
    }
}
