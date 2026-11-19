package com.tomessa.miff.data.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * CNC-A, guarded on the sources: the READING of the journal of the server is switched off in every build that can be distributed (until the PM has deployed the rules and the functions), it
 * never listens (no permanent listener), never logs or sends an event, never stores a name or a photo, and the container only reaches it through the switch.
 */
class NotifJournalGuardTest {
    private fun normalised(file: File): String = file.readText().replace(Regex("\\s+"), " ")

    private val gradle = normalised(File("build.gradle.kts"))
    private val main = File("src/main/java/com/tomessa/miff")

    private fun code(file: File) =
        file
            .readLines()
            .filter {
                val line = it.trimStart()
                !line.startsWith("*") && !line.startsWith("//") && !line.startsWith("/*")
            }.joinToString("\n")

    private val journalFiles =
        listOf(
            "domain/notification/NotificationJournal.kt",
            "domain/notification/CenterAvatars.kt",
            "data/notification/NotificationJournalSync.kt",
            "data/notification/CenterFaces.kt",
            "data/journal/FirestoreJournalGateway.kt",
            "data/journal/FirestoreMemberFaceReader.kt",
            "ui/notification/CenterAvatarView.kt",
        ).map { File(main, it) }

    // ---- the switch ---------------------------------------------------------------------------------------------------------------------

    @Test
    fun theFlagIsFalseByDefault_trueInDebugOnly_andARelease_refusesTrueUnlessAskedOnPurpose() {
        assertTrue(gradle.contains("""buildConfigField("boolean", "NOTIF_JOURNAL", notifJournalProperty ?: "false")"""))
        val debugBlock = gradle.substringAfter("debug {").substringBefore("release {")
        assertTrue(debugBlock.contains("""notifJournalProperty ?: "true""""))
        val restOfBuildTypes = gradle.substringAfter("release {").substringBefore("compileOptions")
        assertFalse(restOfBuildTypes.contains("""notifJournalProperty ?: "true""""))
        val guard = gradle.substringAfter("val journalOnInRelease")
        assertTrue(guard.contains("miff.allowNotifJournalInRelease"))
        assertTrue(guard.contains("preReleaseBuild") && guard.contains("preReleaseLikeBuild") && guard.contains("GradleException"))
    }

    @Test
    fun theContainerReachesTheJournalOnlyThroughTheSwitch() {
        val container = normalised(File(main, "AppContainer.kt"))
        assertTrue(container.contains("enabled = BuildConfig.NOTIF_JOURNAL"))
        assertTrue(container.contains("fun journalSyncSoon(reason: JournalSyncReason) { if (!BuildConfig.NOTIF_JOURNAL) return"))
        // the only entry points of a synchronization: the foreground and a push (in the container), the opening of the centre (its route, through the same class that checks the switch)
        assertTrue(container.contains("journalSyncSoon(JournalSyncReason.FOREGROUND)"))
        assertTrue(container.contains("journalSyncSoon(JournalSyncReason.PUSH_RECEIVED)"))
        val sync = code(File(main, "data/notification/NotificationJournalSync.kt"))
        assertTrue(sync.contains("if (!enabled) return false"))
        assertTrue(sync.contains("if (!enabled) return"))
    }

    // ---- cost: never a permanent listener, a bounded request ---------------------------------------------------------------------------------

    @Test
    fun theJournalIsNeverListenedTo_onlyReadFromACursor_100AtMost_twoPagesAtMost() {
        val sync = code(File(main, "data/notification/NotificationJournalSync.kt"))
        val gateway = code(File(main, "data/journal/FirestoreJournalGateway.kt"))
        val reader = code(File(main, "data/journal/FirestoreMemberFaceReader.kt"))
        val faces = code(File(main, "data/notification/CenterFaces.kt"))
        listOf(sync, gateway, reader, faces).forEach {
            assertFalse(Regex("""addSnapshotListener|callbackFlow|snapshots\(""").containsMatchIn(it))
        }
        assertTrue(gateway.contains("Source.SERVER"))
        assertTrue(gateway.contains(".limit(NotificationJournal.PAGE_SIZE.toLong())"))
        assertEquals(100, com.tomessa.miff.domain.notification.NotificationJournal.PAGE_SIZE)
        assertEquals(2, com.tomessa.miff.domain.notification.NotificationJournal.MAX_PAGES)
        // the notification packages themselves never touch Firestore (NotificationPrivacyTest): only the two files of data/journal do
        assertFalse(Regex("""FirebaseFirestore|\.collection\(""").containsMatchIn(sync + faces))
        // contract 2.21: from a cursor ascending, the first synchronization descending, a limit always
        assertTrue(gateway.contains("Query.Direction.ASCENDING") && gateway.contains("Query.Direction.DESCENDING"))
    }

    // ---- privacy and security ---------------------------------------------------------------------------------------------------------------------

    @Test
    fun nothingOfTheJournalOrTheFacesIsLogged_sentAsAnEvent_reportedInACrash_orCopied() {
        val found =
            journalFiles.filter { file ->
                Regex(
                    """\bLog\.[a-z]\(|\bprintln\(|\bprintStackTrace\b|\bTimber\b|Crashlytics|recordException|setCustomKey|FirebaseAnalytics|logEvent|AnalyticsEvent|AnalyticsTracker|ClipboardManager|ClipData|setPrimaryClip""",
                ).containsMatchIn(code(file))
            }
        assertEquals(emptyList<File>(), found)
    }

    @Test
    fun theCentreStoresNoPhotoAndNoNameOfTheJournal_aLineIsAKeyATypeAMiffAndDates() {
        val store = code(File(main, "data/notification/NotificationCenter.kt")).substringAfter("private class StoredItem").substringBefore(")\n")
        assertFalse(Regex("""avatar|photo|jpeg|bitmap""", RegexOption.IGNORE_CASE).containsMatchIn(store))
        // the faces live in memory only: nothing of CenterFaces writes a file or a preference
        val faces = code(File(main, "data/notification/CenterFaces.kt"))
        assertFalse(Regex("""SharedPreferences|FileOutputStream|openFileOutput|writeBytes|DataStore|Room""").containsMatchIn(faces))
    }

    @Test
    fun theJournalIsParsedStrictly_everyFieldFromTheNetworkIsCheckedBeforeUse() {
        val domain = code(File(main, "domain/notification/NotificationJournal.kt"))
        assertTrue(domain.contains("""Regex("^[A-Za-z0-9_-]{1,128}$")"""))
        assertTrue(domain.contains("if ((data[\"v\"] as? Number)?.toLong() != VERSION) return null"))
        assertTrue(domain.contains("JournalKind.fromCode(data[\"kind\"] as? String) ?: return null"))
    }

    @Test
    fun theFirestoreReadersNeverQuoteAnExceptionMessage() {
        listOf(
            "data/notification/NotificationJournalSync.kt",
            "data/notification/CenterFaces.kt",
            "data/journal/FirestoreJournalGateway.kt",
            "data/journal/FirestoreMemberFaceReader.kt",
        ).forEach {
            val text = code(File(main, it))
            assertFalse("$it must not use the message of an exception", Regex("""\.message\b|\.localizedMessage""").containsMatchIn(text))
        }
    }

    @Test
    fun theFacesAreErasedWithTheSession_andTheCursorToo() {
        val container = normalised(File(main, "AppContainer.kt"))
        assertTrue(container.contains("notificationJournalSync::purge"))
        assertTrue(container.contains("requesterDirectory, memberFaces,"))
    }
}
