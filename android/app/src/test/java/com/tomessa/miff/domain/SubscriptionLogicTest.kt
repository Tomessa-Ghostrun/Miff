package com.tomessa.miff.domain

import com.tomessa.miff.domain.subscription.SubscriptionInfo
import com.tomessa.miff.domain.subscription.formatLongDate
import com.tomessa.miff.domain.subscription.interpretSubscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** Ticket ABO-1 (criteria F1, F2, F4): when the "Abonnement" line exists, and how its date is written. */
class SubscriptionLogicTest {
    private val now = 1_000_000L

    // ---- visible only if active AND expiresAt in the future (same reading as the rules) ----

    @Test
    fun activeWithAFutureDate_isShown_withItsDate() {
        assertEquals(
            SubscriptionInfo(now + 1, grantedByTeam = true),
            interpretSubscription(true, true, now + 1, "manual", now),
        )
    }

    @Test
    fun noDocument_showsNothing() {
        assertNull(interpretSubscription(false, null, null, null, now))
    }

    @Test
    fun inactive_showsNothing() {
        assertNull(interpretSubscription(true, false, now + 1, "manual", now))
    }

    @Test
    fun expired_orExpiringRightNow_showsNothing() {
        assertNull(interpretSubscription(true, true, now - 1, "manual", now))
        assertNull(interpretSubscription(true, true, now, "manual", now))
    }

    @Test
    fun missingFields_showNothing() {
        assertNull(interpretSubscription(true, null, now + 1, "manual", now))
        assertNull(interpretSubscription(true, true, null, "manual", now))
    }

    // ---- the source: only "manual" says the access was granted by the team ----

    @Test
    fun onlyTheExactValueManual_countsAsGrantedByTheTeam() {
        assertEquals(true, interpretSubscription(true, true, now + 1, "manual", now)?.grantedByTeam)
        listOf(null, "", "Manual", "MANUAL", " manual", "google_play", "app_store").forEach {
            assertEquals("source=$it", false, interpretSubscription(true, true, now + 1, it, now)?.grantedByTeam)
        }
    }

    @Test
    fun anotherSource_stillShowsTheSubscription_withTheNeutralLabel() {
        assertEquals(
            SubscriptionInfo(now + 1, grantedByTeam = false),
            interpretSubscription(true, true, now + 1, "google_play", now),
        )
    }

    // ---- the date in the long format of the language (F4: never 02/18/12) ----

    private val paris = ZoneId.of("Europe/Paris")
    private val millis = ZonedDateTime.of(2027, 1, 12, 10, 0, 0, 0, paris).toInstant().toEpochMilli()

    @Test
    fun frenchDate_isWrittenInFull() {
        assertEquals("12 janvier 2027", formatLongDate(millis, Locale.FRANCE, paris))
        assertEquals("12 janvier 2027", formatLongDate(millis, Locale.forLanguageTag("fr"), paris))
    }

    @Test
    fun englishDate_followsTheEnglishOrder() {
        assertEquals("January 12, 2027", formatLongDate(millis, Locale.US, paris))
        assertEquals("12 January 2027", formatLongDate(millis, Locale.UK, paris))
    }

    @Test
    fun theDay_isTheOneOfThePhonesTimeZone() {
        // 23:30 in Paris is already the next day in Auckland: the person sees the day of THEIR phone.
        val lateEvening = ZonedDateTime.of(2027, 1, 12, 23, 30, 0, 0, paris).toInstant().toEpochMilli()
        assertEquals("12 janvier 2027", formatLongDate(lateEvening, Locale.FRANCE, paris))
        assertEquals("13 janvier 2027", formatLongDate(lateEvening, Locale.FRANCE, ZoneId.of("Pacific/Auckland")))
    }

    @Test
    fun theDate_isNeverMadeOfDigitsAndSlashes() {
        listOf(Locale.FRANCE, Locale.US, Locale.UK).forEach {
            assertFalse(formatLongDate(millis, it, paris).contains("/"))
        }
    }
}
