package com.tomessa.miff.domain.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The validation of the push of the server (N9): one type, every field checked, everything else ignored; the first name cleaned and cut; the keys of the tray and of
 * the centre. The message comes from the network: nothing of it is trusted.
 */
class CircleNewsTest {
    private val sentAt = 1_800_000_000_000L

    private fun message(
        type: String? = "n9_update",
        miffId: String? = "AbCdEf0123456789xyZ0",
        authorUid: String? = "uidAuthor1234567890123456789",
        name: String? = "Tomessa",
        at: String? = sentAt.toString(),
        extra: Map<String, String> = emptyMap(),
    ): Map<String, String> =
        buildMap {
            type?.let { put("type", it) }
            miffId?.let { put("miffId", it) }
            authorUid?.let { put("authorUid", it) }
            name?.let { put("authorFirstName", it) }
            at?.let { put("sentAt", it) }
            putAll(extra)
        }

    @Test
    fun aValidMessageIsUnderstood() {
        val news = CircleNewsParser.parse(message())
        assertNotNull(news)
        assertEquals("AbCdEf0123456789xyZ0", news!!.miffId)
        assertEquals("uidAuthor1234567890123456789", news.authorUid)
        assertEquals("Tomessa", news.authorFirstName)
        assertEquals(sentAt, news.sentAtMillis)
    }

    @Test
    fun theAuthorIdentifierIsOptional_absentOrEmptyMeansNone_butAMalformedOneRejectsTheMessage() {
        assertNull(CircleNewsParser.parse(message(authorUid = null))!!.authorUid)
        assertNull(CircleNewsParser.parse(message(authorUid = ""))!!.authorUid)
        assertNull(CircleNewsParser.parse(message(authorUid = "a/b")))
        assertNull(CircleNewsParser.parse(message(authorUid = "x".repeat(129))))
    }

    @Test
    fun anUnknownTypeIsIgnored_aLaterVersionOfTheServerMaySendMore() {
        assertNull(CircleNewsParser.parse(message(type = "n10_something")))
        assertNull(CircleNewsParser.parse(message(type = "N9_UPDATE")))
        assertNull(CircleNewsParser.parse(message(type = "")))
        assertNull(CircleNewsParser.parse(message(type = null)))
    }

    @Test
    fun aMissingOrMalformedFieldIgnoresTheWholeMessage() {
        assertNull(CircleNewsParser.parse(message(miffId = null)))
        assertNull(CircleNewsParser.parse(message(miffId = "")))
        assertNull(CircleNewsParser.parse(message(miffId = "../../etc")))
        assertNull(CircleNewsParser.parse(message(miffId = "miff://open?x=1")))
        assertNull(CircleNewsParser.parse(message(miffId = "a".repeat(129))))
        assertNull(CircleNewsParser.parse(message(name = null)))
        assertNull(CircleNewsParser.parse(message(name = "")))
        assertNull(CircleNewsParser.parse(message(name = "   ")))
        assertNull(CircleNewsParser.parse(message(at = null)))
        assertNull(CircleNewsParser.parse(message(at = "yesterday")))
        assertNull(CircleNewsParser.parse(message(at = "-5")))
        assertNull(CircleNewsParser.parse(message(at = "1800000000")))
        assertNull(CircleNewsParser.parse(message(at = "9".repeat(13))))
        assertNull(CircleNewsParser.parse(message(at = "1500000000000")))
    }

    @Test
    fun aVeryLongFirstNameIsSuspect_theMessageIsIgnored() {
        assertNull(CircleNewsParser.parse(message(name = "A".repeat(65))))
        assertNotNull(CircleNewsParser.parse(message(name = "A".repeat(64))))
    }

    @Test
    fun unknownKeysAreIgnored_theOnesFcmAddsItself() {
        val news =
            CircleNewsParser.parse(
                message(extra = mapOf("google.message_id" to "0:123", "from" to "29899146512", "collapse_key" to "x")),
            )
        assertNotNull(news)
    }

    @Test
    fun theFirstNameIsCutAtTwentyCharactersWithAnEllipsis_neverTheFamilyName() {
        assertEquals("Tomessa", CircleNewsParser.cleanFirstName("Tomessa"))
        assertEquals("A".repeat(20), CircleNewsParser.cleanFirstName("A".repeat(20)))
        assertEquals("A".repeat(20) + "…", CircleNewsParser.cleanFirstName("A".repeat(21)))
        assertEquals("A".repeat(20) + "…", CircleNewsParser.cleanFirstName("A".repeat(50)))
    }

    @Test
    fun theCutIsIdempotent_aNameAlreadyCutByTheServerComesOutUnchanged() {
        val cutByServer = "B".repeat(20) + "…"
        assertEquals(cutByServer, CircleNewsParser.cleanFirstName(cutByServer))
    }

    @Test
    fun theCutCountsCharactersNotUtf16Units_anEmojiIsNotSplit() {
        val name = "😀".repeat(25)
        val cut = CircleNewsParser.cleanFirstName(name)!!
        assertEquals("😀".repeat(20) + "…", cut)
        assertEquals(21, cut.codePointCount(0, cut.length))
    }

    @Test
    fun controlAndDirectionCharactersAreRemoved_soTheNameCannotReorderOrBreakTheSentence() {
        // Right-to-left override and isolate marks, a zero-width space, a bell, a line feed in the middle of the name.
        val dirty = "‮Anna​⁦\u0007\nMarie‬"
        assertEquals("Anna Marie", CircleNewsParser.cleanFirstName(dirty))
        assertNull(CircleNewsParser.cleanFirstName("‮​\u0007"))
        assertEquals("Jean Pierre", CircleNewsParser.cleanFirstName("  Jean \t\t  Pierre  "))
    }

    @Test
    fun accentsAndOtherAlphabetsSurvive() {
        assertEquals("Élodie", CircleNewsParser.cleanFirstName("Élodie"))
        assertEquals("Zoë-Léa", CircleNewsParser.cleanFirstName("Zoë-Léa"))
        assertEquals("李小龍", CircleNewsParser.cleanFirstName("李小龍"))
    }

    @Test
    fun thePayloadNeverPrintsItsContent() {
        val news = CircleNewsParser.parse(message())!!
        assertEquals("CircleNewsPayload(redacted)", news.toString())
        assertFalse(news.toString().contains("Tomessa"))
    }

    // --- Keys ---------------------------------------------------------------------------------------------------------------------------

    @Test
    fun oneNotificationPerAuthor_evenAcrossTwoMiffs() {
        val a1 = CircleNewsParser.parse(message(miffId = "miffAAAA"))!!
        val a2 = CircleNewsParser.parse(message(miffId = "miffBBBB"))!!
        assertEquals(CircleNewsKeys.trayTag(a1), CircleNewsKeys.trayTag(a2))
        val other = CircleNewsParser.parse(message(authorUid = "someoneElse"))!!
        assertTrue(CircleNewsKeys.trayTag(a1) != CircleNewsKeys.trayTag(other))
    }

    @Test
    fun withoutAnAuthorTheTagIsPerMiff() {
        val a = CircleNewsParser.parse(message(authorUid = null, miffId = "miffAAAA"))!!
        val b = CircleNewsParser.parse(message(authorUid = null, miffId = "miffBBBB"))!!
        assertTrue(CircleNewsKeys.trayTag(a) != CircleNewsKeys.trayTag(b))
    }

    @Test
    fun theLineOfTheCentreIsUniquePerMessage_andFindsItsNotificationAgain() {
        val first = CircleNewsParser.parse(message())!!
        val second = CircleNewsParser.parse(message(at = (sentAt + 3_600_000).toString()))!!
        assertTrue(CircleNewsKeys.centerId(first) != CircleNewsKeys.centerId(second))
        assertEquals(CircleNewsKeys.trayTag(first), CircleNewsKeys.trayTagOf(CircleNewsKeys.centerId(first)))
        assertTrue(CircleNewsKeys.isCircleNews(CircleNewsKeys.centerId(first)))
        // Another type keeps its key as it is.
        assertEquals("n1|miff|me|5", CircleNewsKeys.trayTagOf("n1|miff|me|5"))
        assertFalse(CircleNewsKeys.isCircleNews("n6|a|b|1"))
    }

    // --- The Miff of a tap -----------------------------------------------------------------------------------------------------------

    @Test
    fun aMiffIdentifierFromAnIntentIsCheckedForItsShape() {
        assertTrue(MiffIdShape.isValid("AbCdEf0123456789xyZ0"))
        assertTrue(MiffIdShape.isValid("a_b-c"))
        assertFalse(MiffIdShape.isValid(null))
        assertFalse(MiffIdShape.isValid(""))
        assertFalse(MiffIdShape.isValid("a/b"))
        assertFalse(MiffIdShape.isValid("https://evil.example"))
        assertFalse(MiffIdShape.isValid("x".repeat(65)))
        assertFalse(MiffIdShape.isValid("a b"))
    }

    // --- Delivery of N9 -----------------------------------------------------------------------------------------------------------------

    private val day = "2026-11-12"
    private val prefs = NotificationPreferences()

    private fun decide(
        counters: DailyCounters = DailyCounters(day),
        hour: Int = 14,
        foreground: Boolean = false,
        lateness: Long = 0L,
        already: Boolean = false,
        preferences: NotificationPreferences = prefs,
    ) = NotificationDelivery.decide(
        type = NotificationType.N9,
        preferences = preferences,
        counters = counters,
        localHour = hour,
        appInForeground = foreground,
        latenessMillis = lateness,
        alreadyNotified = already,
        maxLatenessMillis = NotificationPolicy.N9_MAX_AGE_MILLIS,
    )

    @Test
    fun byDayTheNewsIsShownOnTheNormalChannel_atNightOnTheQuietOne() {
        assertEquals(false, (decide(hour = 14) as Delivery.Show).quiet)
        assertEquals(true, (decide(hour = 22) as Delivery.Show).quiet)
        assertEquals(true, (decide(hour = 2) as Delivery.Show).quiet)
        assertEquals(true, (decide(hour = 6) as Delivery.Show).quiet)
        assertEquals(false, (decide(hour = 7) as Delivery.Show).quiet)
        assertEquals(false, (decide(hour = 21) as Delivery.Show).quiet)
        // "Nuit calme" off: the night is like the day.
        assertEquals(false, (decide(hour = 2, preferences = prefs.copy(nightCalm = false)) as Delivery.Show).quiet)
    }

    @Test
    fun theSwitchesDropTheNews() {
        assertEquals(Delivery.Drop, decide(preferences = prefs.copy(n9 = false)))
        assertEquals(Delivery.Drop, decide(preferences = prefs.copy(enabled = false)))
        // The other switches have nothing to do with it.
        assertTrue(
            decide(
                preferences = prefs.copy(n1 = false, n2 = false, n3 = false, n4 = false, n6 = false),
            ) is Delivery.Show,
        )
    }

    @Test
    fun aMessageAlreadyHandledIsDropped_aRedeliveryMakesNoSecondNotification() {
        assertEquals(Delivery.Drop, decide(already = true))
    }

    @Test
    fun inFrontOnlyTheCentreSpeaks() {
        assertEquals(Delivery.Silent, decide(foreground = true))
    }

    @Test
    fun aNewsOlderThanTwoHoursIsOnlyWrittenToTheCentre_butOneAQuarterOfAnHourOldIsShown() {
        val lateByTwentyMinutes = 20L * 60L * 1000L
        assertTrue(decide(lateness = lateByTwentyMinutes) is Delivery.Show)
        assertTrue(decide(lateness = NotificationPolicy.N9_MAX_AGE_MILLIS) is Delivery.Show)
        assertEquals(Delivery.Silent, decide(lateness = NotificationPolicy.N9_MAX_AGE_MILLIS + 1))
    }

    @Test
    fun sixAreShownAPerDay_thenOnlyTheCentre_andTheyDoNotEatTheRemindersCaps() {
        var counters = DailyCounters(day)
        repeat(NotificationPolicy.MAX_N9_PER_DAY) {
            assertTrue(decide(counters = counters) is Delivery.Show)
            counters = counters.afterShown(NotificationType.N9)
        }
        assertEquals(Delivery.Silent, decide(counters = counters))
        // N9 is outside the total of N1 to N4: the reminders still have their six.
        assertEquals(0, counters.total)
        assertTrue(counters.canShow(NotificationType.N1))
        // And the other way round: the reminders' total does not block the news.
        val full =
            DailyCounters(day)
                .afterShown(NotificationType.N1)
                .afterShown(NotificationType.N1)
                .afterShown(NotificationType.N2)
                .afterShown(NotificationType.N2)
                .afterShown(NotificationType.N3)
                .afterShown(NotificationType.N3)
        assertEquals(NotificationPolicy.MAX_TOTAL_PER_DAY, full.total)
        assertTrue(decide(counters = full) is Delivery.Show)
    }

    @Test
    fun theDefaultsAreOn_andTheServerSwitchFollowsTheGeneralOneAndItsOwn() {
        assertTrue(NotificationPreferences().n9)
        assertTrue(NotificationPreferences().wantsCircleNews())
        assertFalse(NotificationPreferences(n9 = false).wantsCircleNews())
        assertFalse(NotificationPreferences(enabled = false).wantsCircleNews())
        assertTrue(NotificationPreferences(n9 = false).with(NotificationType.N9, true).wantsCircleNews())
        assertEquals(NotificationFamily.NEWS, NotificationType.N9.family)
        assertEquals(NotificationDestination.FEED, NotificationType.N9.destination)
    }
}
