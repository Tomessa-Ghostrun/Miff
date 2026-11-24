package com.tomessa.miff.domain

import com.tomessa.miff.domain.subscription.EntitlementDocument
import com.tomessa.miff.domain.subscription.StoreState
import com.tomessa.miff.domain.subscription.SubscriptionPlan
import com.tomessa.miff.domain.subscription.SubscriptionSource
import com.tomessa.miff.domain.subscription.SubscriptionStatus
import com.tomessa.miff.domain.subscription.formatLongDate
import com.tomessa.miff.domain.subscription.interpretSubscription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Tickets ABO-1 and premium-achat-reel.md section 8 (criteria AN6, AN7, AN8): what the app knows of the Miff+ of the account, from `entitlements/{uid}`. The ACCESS is decided by
 * `active == true` and `expiresAt` in the future (like the security rules); the state written by the server only chooses the sentence.
 */
class SubscriptionLogicTest {
    private val now = 1_000_000L

    private fun doc(
        active: Boolean? = true,
        end: Long? = now + 1,
        source: String? = "google_play",
        state: String? = null,
        plan: String? = null,
        trial: Boolean? = null,
        test: Boolean? = null,
    ) = EntitlementDocument(active, end, source, state, plan, trial, willRenew = null, test = test)

    private fun status(document: EntitlementDocument?) = interpretSubscription(document, now)

    // ---- the access: the same reading as the rules ----------------------------------------------------------------------------------------

    @Test
    fun noDocument_isFree() {
        assertEquals(SubscriptionStatus.Free, status(null))
        assertFalse(status(null).hasAccess)
    }

    @Test
    fun anActiveManualGrant_isManual_withItsDate() {
        assertEquals(SubscriptionStatus.Manual(now + 1), status(doc(source = "manual")))
        assertTrue(status(doc(source = "manual")).hasAccess)
    }

    @Test
    fun aManualGrantThatIsOver_orIncomplete_isFree_noTraceOfASubscription() {
        assertEquals(SubscriptionStatus.Free, status(doc(source = "manual", active = false)))
        assertEquals(SubscriptionStatus.Free, status(doc(source = "manual", end = now - 1)))
        assertEquals(SubscriptionStatus.Free, status(doc(source = "manual", end = now)))
        assertEquals(SubscriptionStatus.Free, status(doc(source = "manual", end = null)))
        assertEquals(SubscriptionStatus.Free, status(doc(source = "manual", active = null)))
    }

    @Test
    fun anOldDocument_withoutTheNewFields_readsAsBefore_criterionAN7() {
        // The first documents of the team (ABO-1): active, expiresAt, source: none of the new fields.
        assertEquals(
            SubscriptionStatus.Manual(now + 5),
            status(
                EntitlementDocument(
                    active = true,
                    expiresAtMillis =
                        now + 5,
                    source = "manual",
                ),
            ),
        )
        val store = status(EntitlementDocument(active = true, expiresAtMillis = now + 5, source = "google_play"))
        assertEquals(StoreState.ACTIVE, (store as SubscriptionStatus.Store).state)
    }

    // ---- the store states of section 8 -----------------------------------------------------------------------------------------------------

    private fun store(
        state: String?,
        active: Boolean = true,
        end: Long? = now + 1,
        plan: String? = "yearly",
        trial: Boolean? = null,
    ) = status(doc(state = state, active = active, end = end, plan = plan, trial = trial)) as SubscriptionStatus.Store

    @Test
    fun theStatesThatKeepTheAccess() {
        assertEquals(StoreState.TRIAL, store("trial", trial = true).state)
        assertEquals(StoreState.ACTIVE, store("active").state)
        assertEquals(StoreState.CANCELED, store("canceled").state)
        assertEquals(StoreState.GRACE, store("grace").state)
        listOf("trial", "active", "canceled", "grace").forEach {
            assertTrue(it, status(doc(state = it)).hasAccess)
        }
    }

    @Test
    fun theStatesThatCutTheAccess_areShownAnyway() {
        assertEquals(StoreState.ON_HOLD, store("on_hold", active = false, end = now - 5).state)
        assertEquals(StoreState.EXPIRED, store("expired", active = false, end = now - 5).state)
        assertEquals(StoreState.REVOKED, store("revoked", active = false, end = now).state)
        listOf("on_hold", "expired", "revoked").forEach {
            assertFalse(it, status(doc(state = it, active = false, end = now - 5)).hasAccess)
        }
    }

    @Test
    fun theDatesWin_aDocumentThatContradictsItselfNeverGrantsMore() {
        // "active" but the end is over: no access, shown as ended.
        assertEquals(StoreState.EXPIRED, store("active", end = now - 1).state)
        assertFalse(status(doc(state = "active", end = now - 1)).hasAccess)
        // "active" but `active` is false (the server cut it): no access.
        assertFalse(status(doc(state = "active", active = false)).hasAccess)
        // "expired" with a future end and active: the dates say there IS access: it is shown as active, never as lost.
        assertEquals(StoreState.ACTIVE, store("expired").state)
        assertTrue(status(doc(state = "expired")).hasAccess)
        // "revoked" cannot keep an access either way.
        assertFalse(status(doc(state = "revoked", active = false, end = now + 100)).hasAccess)
    }

    @Test
    fun anUnknownOrMissingState_isDeducedFromTheDates() {
        assertEquals(StoreState.ACTIVE, store(null).state)
        assertEquals(StoreState.TRIAL, store("something-new", trial = true).state)
        assertEquals(StoreState.EXPIRED, store(null, end = now - 1).state)
        assertEquals(StoreState.EXPIRED, store("something-new", active = false).state)
    }

    @Test
    fun thePlanAndTheTestMarkAreRead_anythingElseIsNull() {
        assertEquals(SubscriptionPlan.YEARLY, store("active", plan = "yearly").plan)
        assertEquals(SubscriptionPlan.MONTHLY, store("active", plan = "monthly").plan)
        assertNull(store("active", plan = "weekly").plan)
        assertNull(store("active", plan = null).plan)
        assertTrue((status(doc(test = true)) as SubscriptionStatus.Store).test)
        assertFalse((status(doc(test = null)) as SubscriptionStatus.Store).test)
    }

    @Test
    fun theSourceIsKept_anUnknownSourceKeepsTheNeutralWording_orIsFreeWhenOver() {
        assertEquals(
            SubscriptionSource.GOOGLE_PLAY,
            (status(doc(source = "google_play")) as SubscriptionStatus.Store).source,
        )
        assertEquals(
            SubscriptionSource.APP_STORE,
            (status(doc(source = "app_store")) as SubscriptionStatus.Store).source,
        )
        listOf(null, "", "Manual", "MANUAL", " manual", "stripe").forEach {
            val read = status(doc(source = it))
            assertEquals("source=$it", SubscriptionSource.OTHER, (read as SubscriptionStatus.Store).source)
            assertTrue(read.hasAccess)
            assertEquals("source=$it", SubscriptionStatus.Free, status(doc(source = it, end = now - 1)))
        }
    }

    @Test
    fun aStorePurchaseThatIsOver_isStillShown_thePersonMayWantToResumeIt() {
        val expired = status(doc(state = "expired", active = false, end = now - 10))
        assertTrue(expired is SubscriptionStatus.Store)
        assertFalse(expired.hasAccess)
    }

    // ---- the date in the long format of the language (F4: never 02/18/12) -------------------------------------------------------------------

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
