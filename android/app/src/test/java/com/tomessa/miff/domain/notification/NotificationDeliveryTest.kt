package com.tomessa.miff.domain.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What to do with a notification that is due: show, write to the centre only, or drop (ticket 20.3, 20.8; decision Q30). */
class NotificationDeliveryTest {
    private val prefs = NotificationPreferences()
    private val day = "2026-11-08"

    private fun decide(
        type: NotificationType = NotificationType.N1,
        preferences: NotificationPreferences = prefs,
        counters: DailyCounters = DailyCounters(day),
        hour: Int = 14,
        foreground: Boolean = false,
        lateness: Long = 0,
        notified: Boolean = false,
    ) = NotificationDelivery.decide(type, preferences, counters, hour, foreground, lateness, notified)

    private fun shown(d: Delivery) = d is Delivery.Show

    // --- Order of the rules ------------------------------------------------------------------------------------------------------------------

    @Test
    fun anOrdinaryDueNotification_isShown_withSound_byDay() {
        val d = decide()
        assertTrue(d is Delivery.Show && !d.quiet)
    }

    @Test
    fun aKeyAlreadyNotified_isNeverNotifiedAgain_whateverTheRest() {
        assertEquals(Delivery.Drop, decide(notified = true))
        assertEquals(Delivery.Drop, decide(notified = true, foreground = true))
    }

    @Test
    fun aSwitchOff_dropsEverything_evenTheLinesOfTheCentre() {
        assertEquals(Delivery.Drop, decide(preferences = NotificationPreferences(n1 = false)))
        assertEquals(Delivery.Drop, decide(preferences = NotificationPreferences(enabled = false), foreground = true))
    }

    @Test
    fun inFront_onlyTheCentre() {
        assertEquals(Delivery.Silent, decide(foreground = true))
    }

    @Test
    fun lateness_fifteenMinutesPileIsStillShown_oneMillisecondMoreIsOnlyWrittenToTheCentre() {
        val limit = NotificationPolicy.CATCH_UP_MAX_LATENESS_MILLIS
        assertEquals(15L * 60 * 1000, limit)
        assertTrue(shown(decide(lateness = limit)))
        assertEquals(Delivery.Silent, decide(lateness = limit + 1))
        assertTrue(shown(decide(lateness = 0)))
    }

    // --- Daily caps ---------------------------------------------------------------------------------------------------------------------------

    @Test
    fun twoPerType_theThirdIsSilent_decisionQ30() {
        val one = DailyCounters(day).afterShown(NotificationType.N1)
        assertTrue(shown(decide(counters = one)))
        val two = one.afterShown(NotificationType.N1)
        assertEquals(Delivery.Silent, decide(counters = two))
        // Another type is not affected.
        assertTrue(shown(decide(type = NotificationType.N2, counters = two)))
    }

    @Test
    fun sixInAllForN1ToN4_theSeventhIsSilent_butN6IsOutsideTheTotal() {
        val six =
            DailyCounters(
                day,
                mapOf(
                    NotificationType.N1 to 2,
                    NotificationType.N2 to 2,
                    NotificationType.N3 to 1,
                    NotificationType.N4 to 1,
                ),
            )
        assertEquals(6, six.total)
        assertEquals(Delivery.Silent, decide(type = NotificationType.N3, counters = six))
        assertEquals(Delivery.Silent, decide(type = NotificationType.N4, counters = six))
        val fiveTotal = six.copy(perType = six.perType + (NotificationType.N4 to 0))
        assertEquals(5, fiveTotal.total)
        assertTrue(shown(decide(type = NotificationType.N4, counters = fiveTotal)))
        // N6: three a day, and the total of N1 to N4 does not count against it.
        assertTrue(shown(decide(type = NotificationType.N6, counters = six)))
        val threeN6 =
            six
                .afterShown(
                    NotificationType.N6,
                ).afterShown(NotificationType.N6)
                .afterShown(NotificationType.N6)
        assertEquals(6, threeN6.total)
        assertEquals(Delivery.Silent, decide(type = NotificationType.N6, counters = threeN6))
        assertTrue(
            shown(
                decide(
                    type = NotificationType.N6,
                    counters = six.afterShown(NotificationType.N6).afterShown(NotificationType.N6),
                ),
            ),
        )
    }

    @Test
    fun aNewLocalDayStartsFromZero() {
        val yesterday =
            DailyCounters(
                "2026-11-07",
                mapOf(
                    NotificationType.N1 to 2,
                    NotificationType.N2 to 2,
                    NotificationType.N3 to 2,
                ),
            )
        val today = yesterday.forDay(day)
        assertEquals(0, today.total)
        assertEquals(day, today.day)
        assertTrue(shown(decide(counters = today)))
        // The same day keeps its counters.
        assertEquals(yesterday, yesterday.forDay("2026-11-07"))
    }

    @Test
    fun onlyWhatTheSystemShowedCounts_aSilentLineDoesNotUseUpTheCap() {
        var counters = DailyCounters(day)
        // Two shown, then silent ones (the caller does not call afterShown for them): the cap is exactly two.
        repeat(2) { counters = counters.afterShown(NotificationType.N1) }
        repeat(5) { assertEquals(Delivery.Silent, decide(counters = counters)) }
        assertEquals(2, counters.count(NotificationType.N1))
    }

    // --- The quiet night ----------------------------------------------------------------------------------------------------------------------

    @Test
    fun nightCalm_n1N3N6QuietAtTheBounds_n2N4NormalAllNight() {
        fun quiet(
            type: NotificationType,
            hour: Int,
        ) = (decide(type = type, hour = hour) as Delivery.Show).quiet
        listOf(NotificationType.N1, NotificationType.N3, NotificationType.N6).forEach { type ->
            assertFalse("$type 21h", quiet(type, 21))
            assertTrue("$type 22h", quiet(type, 22))
            assertTrue("$type 02h", quiet(type, 2))
            assertTrue("$type 06h", quiet(type, 6))
            assertFalse("$type 07h", quiet(type, 7))
        }
        listOf(NotificationType.N2, NotificationType.N4).forEach { type ->
            listOf(22, 0, 3, 6).forEach { assertFalse("$type ${it}h", quiet(type, it)) }
        }
    }

    @Test
    fun nightCalmSwitchedOff_nothingIsQuiet() {
        val d = decide(preferences = NotificationPreferences(nightCalm = false), hour = 2)
        assertTrue(d is Delivery.Show && !d.quiet)
    }

    // --- Keys already notified -----------------------------------------------------------------------------------------------------------------

    @Test
    fun aKeyIsRemembered48Hours() {
        val h = 60L * 60L * 1000L
        val now = 1_800_000_000_000L
        val keys = NotifiedKeys.add(emptyMap(), "k", now)
        assertTrue(NotifiedKeys.contains(keys, "k", now))
        assertTrue(NotifiedKeys.contains(keys, "k", now + 48 * h))
        assertFalse(NotifiedKeys.contains(keys, "k", now + 48 * h + 1))
        assertFalse(NotifiedKeys.contains(keys, "other", now))
    }

    @Test
    fun pruningForgetsOnlyTheOldKeys() {
        val h = 60L * 60L * 1000L
        val now = 1_800_000_000_000L
        val keys = mapOf("old" to now - 49 * h, "recent" to now - 47 * h)
        assertEquals(mapOf("recent" to now - 47 * h), NotifiedKeys.prune(keys, now))
        assertEquals(setOf("recent", "new"), NotifiedKeys.add(keys, "new", now).keys)
    }

    @Test
    fun deliveryPrintsNothingPersonal() {
        assertEquals("Show(quiet=true)", Delivery.Show(true).toString())
    }
}
