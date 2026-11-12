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

    /** The server sends no time: the time of reception is given. */
    private fun parse(
        data: Map<String, String>,
        messageId: String? = null,
    ) = CircleNewsParser.parse(data, receivedAtMillis = sentAt, messageId = messageId)

    private fun message(
        type: String? = "circle_update",
        miffId: String? = "AbCdEf0123456789xyZ0",
        authorUid: String? = "uidAuthor1234567890123456789",
        name: String? = "Tomessa",
        quiet: String? = "0",
        extra: Map<String, String> = emptyMap(),
    ): Map<String, String> =
        buildMap {
            type?.let { put("type", it) }
            miffId?.let { put("miffId", it) }
            authorUid?.let { put("authorUid", it) }
            name?.let { put("firstName", it) }
            quiet?.let { put("quiet", it) }
            putAll(extra)
        }

    @Test
    fun aValidMessageIsUnderstood() {
        val news = parse(message())
        assertNotNull(news)
        assertEquals("AbCdEf0123456789xyZ0", news!!.miffId)
        assertEquals("uidAuthor1234567890123456789", news.authorUid)
        assertEquals("Tomessa", news.authorFirstName)
        assertEquals(sentAt, news.sentAtMillis)
    }

    @Test
    fun theMessageExactlyAsTheServerBuildsIt_valuesOfTheServerTest() {
        // backend/functions/test/unit/notif-regles.test.mjs, "Android : message data seulement": the data of `construireMessage`, nothing else.
        val day =
            mapOf(
                "type" to "circle_update",
                "miffId" to "MIFF123",
                "authorUid" to "AUTEUR1",
                "firstName" to "Tomessa",
                "quiet" to "0",
            )
        val night = day + ("quiet" to "1")
        val a = parse(day)!!
        assertEquals("MIFF123", a.miffId)
        assertEquals("AUTEUR1", a.authorUid)
        assertEquals("Tomessa", a.authorFirstName)
        assertFalse(a.serverQuiet)
        assertEquals("no time in the message: the time of reception", sentAt, a.sentAtMillis)
        assertTrue(parse(night)!!.serverQuiet)
    }

    @Test
    fun theQuietFlagIsAbsentOrZeroOrOneNothingElse() {
        assertFalse(parse(message(quiet = null))!!.serverQuiet)
        assertFalse(parse(message(quiet = "0"))!!.serverQuiet)
        assertTrue(parse(message(quiet = "1"))!!.serverQuiet)
    }

    @Test
    fun theIdentifierOfTheMessageDedupesARedelivery_andABadOneIsDropped() {
        val one = parse(message(), messageId = "0:1699999999999999%abc123")!!
        val two = parse(message(), messageId = "0:1699999999999999%abc123")!!
        assertEquals(CircleNewsKeys.centerId(one), CircleNewsKeys.centerId(two))
        val other = parse(message(), messageId = "0:1700000000000000%def456")!!
        assertTrue(CircleNewsKeys.centerId(one) != CircleNewsKeys.centerId(other))
        // A shape it does not know (a pipe would break the key) is not used: the time of reception stands in.
        assertEquals(null, parse(message(), messageId = "a|b")!!.messageId)
        assertEquals(null, parse(message(), messageId = "x".repeat(81))!!.messageId)
        assertEquals(CircleNewsKeys.trayTag(one), CircleNewsKeys.trayTagOf(CircleNewsKeys.centerId(one)))
    }

    @Test
    fun theAuthorIdentifierIsOptional_absentOrEmptyMeansNone_butAMalformedOneRejectsTheMessage() {
        assertNull(parse(message(authorUid = null))!!.authorUid)
        assertNull(parse(message(authorUid = ""))!!.authorUid)
        assertNull(parse(message(authorUid = "a/b")))
        assertNull(parse(message(authorUid = "x".repeat(129))))
    }

    @Test
    fun anUnknownTypeIsIgnored_aLaterVersionOfTheServerMaySendMore() {
        assertNull(parse(message(type = "n10_something")))
        assertNull(parse(message(type = "CIRCLE_UPDATE")))
        assertNull("the type of the first draft of the contract", parse(message(type = "n9_update")))
        assertNull(parse(message(type = "")))
        assertNull(parse(message(type = null)))
    }

    @Test
    fun aMissingOrMalformedFieldIgnoresTheWholeMessage() {
        assertNull(parse(message(miffId = null)))
        assertNull(parse(message(miffId = "")))
        assertNull(parse(message(miffId = "../../etc")))
        assertNull(parse(message(miffId = "miff://open?x=1")))
        assertNull(parse(message(miffId = "a".repeat(129))))
        assertNull(parse(message(name = null)))
        assertNull(parse(message(name = "")))
        assertNull(parse(message(name = "   ")))
        assertNull(parse(message(quiet = "yes")))
        assertNull(parse(message(quiet = "2")))
        assertNull(parse(message(quiet = "")))
    }

    @Test
    fun aVeryLongFirstNameIsSuspect_theMessageIsIgnored() {
        assertNull(parse(message(name = "A".repeat(65))))
        assertNotNull(parse(message(name = "A".repeat(64))))
    }

    @Test
    fun unknownKeysAreIgnored_theOnesFcmAddsItself() {
        val news =
            parse(
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
        val news = parse(message())!!
        assertEquals("CircleNewsPayload(redacted)", news.toString())
        assertFalse(news.toString().contains("Tomessa"))
    }

    // --- Keys ---------------------------------------------------------------------------------------------------------------------------

    @Test
    fun oneNotificationPerAuthor_evenAcrossTwoMiffs() {
        val a1 = parse(message(miffId = "miffAAAA"))!!
        val a2 = parse(message(miffId = "miffBBBB"))!!
        assertEquals(CircleNewsKeys.trayTag(a1), CircleNewsKeys.trayTag(a2))
        val other = parse(message(authorUid = "someoneElse"))!!
        assertTrue(CircleNewsKeys.trayTag(a1) != CircleNewsKeys.trayTag(other))
    }

    @Test
    fun withoutAnAuthorTheTagIsPerMiff() {
        val a = parse(message(authorUid = null, miffId = "miffAAAA"))!!
        val b = parse(message(authorUid = null, miffId = "miffBBBB"))!!
        assertTrue(CircleNewsKeys.trayTag(a) != CircleNewsKeys.trayTag(b))
    }

    @Test
    fun theLineOfTheCentreIsUniquePerMessage_andFindsItsNotificationAgain() {
        val first = parse(message(), messageId = "0:first")!!
        val second = parse(message(), messageId = "0:other")!!
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
        serverQuiet: Boolean = false,
    ) = NotificationDelivery.decide(
        type = NotificationType.N9,
        preferences = preferences,
        counters = counters,
        localHour = hour,
        appInForeground = foreground,
        latenessMillis = lateness,
        alreadyNotified = already,
        maxLatenessMillis = NotificationPolicy.N9_MAX_AGE_MILLIS,
        serverQuiet = serverQuiet,
    )

    @Test
    fun whenTheServerSaysQuiet_theQuietChannelIsUsedEvenByDay_ifNuitCalmIsOn() {
        assertEquals(true, (decide(hour = 14, serverQuiet = true) as Delivery.Show).quiet)
        assertEquals(false, (decide(hour = 14, serverQuiet = false) as Delivery.Show).quiet)
        // "Nuit calme" off: the person wants no quiet hours at all, the flag of the server included.
        assertEquals(
            false,
            (decide(hour = 14, serverQuiet = true, preferences = prefs.copy(nightCalm = false)) as Delivery.Show).quiet,
        )
    }

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
