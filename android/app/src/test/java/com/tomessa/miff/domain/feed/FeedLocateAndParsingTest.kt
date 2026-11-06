package com.tomessa.miff.domain.feed

import com.google.firebase.firestore.FirebaseFirestoreException
import com.tomessa.miff.data.feed.found
import com.tomessa.miff.data.feed.lookupForError
import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.tracking.PresenceValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

/**
 * "Localiser" (criteria L1 to L4): the five answers, the limit of 24 h, the address handed to the maps of the system (coordinates ALONE, written
 * with Locale.ROOT), and the cleaning of what comes from the server (a status, a number, a position).
 */
class FeedLocateAndParsingTest {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-11-05T17:00:00Z").toEpochMilli()
    private val minute = 60_000L
    private val hour = 60 * minute

    private fun found(measuredAgo: Long) = PositionLookup.Found(48.856614, 2.352222, now - measuredAgo)

    // --- the five answers ---------------------------------------------------------------------------------------------

    @Test
    fun aRecentMeasure_isRecent_withItsTimeAndItsAge() {
        val result = Locate.interpret(found(20 * minute), now, zone) as LocateResult.Recent
        assertEquals(Moment(now - 20 * minute, Day.TODAY), result.measuredAt)
        assertEquals(Age.Minutes(20), result.age)
    }

    @Test
    fun theLimitOf24Hours_isStrict_exactlyADayIsStillShown_aMinuteMoreIsNot() {
        assertTrue(Locate.interpret(found(Locate.SHOWN_FOR_MILLIS), now, zone) is LocateResult.Recent)
        val old = Locate.interpret(found(Locate.SHOWN_FOR_MILLIS + minute), now, zone)
        assertTrue(old is LocateResult.Old)
    }

    @Test
    fun anOldMeasure_keepsTheDateOnly_andCarriesNoCoordinate() {
        val old = Locate.interpret(found(26 * hour), now, zone) as LocateResult.Old
        assertEquals(Day.YESTERDAY, old.measuredAt.day)
        // Nothing of the position is in the result: not even in its text.
        assertFalse("48" in old.toString() || "2.35" in old.toString())
    }

    @Test
    fun aMeasureInTheNearFuture_isRecent() {
        val result = Locate.interpret(PositionLookup.Found(1.0, 2.0, now + 3 * minute), now, zone)
        assertTrue(result is LocateResult.Recent)
    }

    @Test
    fun notFound_denied_offline_unexpected_areTheOtherFourAnswers() {
        assertEquals(LocateResult.NotFound, Locate.interpret(PositionLookup.NotFound, now, zone))
        assertEquals(LocateResult.Denied, Locate.interpret(PositionLookup.Denied, now, zone))
        assertEquals(LocateResult.Offline, Locate.interpret(PositionLookup.Offline, now, zone))
        // The sixth answer, distinct from "no connection".
        assertEquals(LocateResult.Unavailable, Locate.interpret(PositionLookup.Failed, now, zone))
    }

    @Test
    fun noConnection_isOnlyARealAbsenceOfNetwork() {
        // Unreachable while the phone HAS a network: unexpected. Unexpected while it has none: no connection.
        assertEquals(PositionLookup.Failed, Locate.reconcile(PositionLookup.Offline, networkOnline = true))
        assertEquals(PositionLookup.Offline, Locate.reconcile(PositionLookup.Failed, networkOnline = false))
        assertEquals(PositionLookup.Offline, Locate.reconcile(PositionLookup.Offline, networkOnline = false))
        assertEquals(PositionLookup.Failed, Locate.reconcile(PositionLookup.Failed, networkOnline = true))
        assertEquals(PositionLookup.Denied, Locate.reconcile(PositionLookup.Denied, networkOnline = true))
        assertEquals(PositionLookup.NotFound, Locate.reconcile(PositionLookup.NotFound, networkOnline = false))
    }

    @Test
    fun whatTheSdkSays_isSortedIntoTheCalmAnswers() {
        fun code(c: FirebaseFirestoreException.Code) = lookupForError(FirebaseFirestoreException("never read", c))
        assertEquals(PositionLookup.Denied, code(FirebaseFirestoreException.Code.PERMISSION_DENIED))
        assertEquals(PositionLookup.Offline, code(FirebaseFirestoreException.Code.UNAVAILABLE))
        // Quota of the plan reached, time exceeded, sign-in expired, anything else: the sixth answer.
        listOf(
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            FirebaseFirestoreException.Code.UNAUTHENTICATED,
            FirebaseFirestoreException.Code.INTERNAL,
            FirebaseFirestoreException.Code.ABORTED,
        ).forEach { assertEquals("$it", PositionLookup.Failed, code(it)) }
        assertEquals(PositionLookup.Failed, lookupForError(IllegalStateException("boom")))
    }

    // --- the address handed to the maps ---------------------------------------------------------------------------------

    @Test
    fun geoUri_hasTheCoordinatesAlone_withFiveDecimals() {
        val uri = Locate.geoUri(PositionFix(48.856614, 2.352222))
        assertEquals("geo:48.85661,2.35222?q=48.85661,2.35222", uri)
    }

    @Test
    fun geoUri_isNotBrokenByALanguageThatWritesACommaForTheDecimalPoint() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            assertEquals("geo:48.85661,2.35222?q=48.85661,2.35222", Locate.geoUri(PositionFix(48.856614, 2.352222)))
            assertEquals(
                "geo:-33.86882,151.20929?q=-33.86882,151.20929",
                Locate.geoUri(PositionFix(-33.868820, 151.209290)),
            )
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun theAddresses_carryNoNameNoLabelNoPerson() {
        val fix = PositionFix(48.856614, 2.352222)
        assertEquals("https://www.google.com/maps/search/?api=1&query=48.85661,2.35222", Locate.webMapUrl(fix))
        listOf(Locate.geoUri(fix), Locate.webMapUrl(fix)).forEach { address ->
            assertFalse(
                address,
                address.contains("label", ignoreCase = true) || address.contains("(") || address.contains("%"),
            )
        }
    }

    @Test
    fun theClassesThatCarryAPosition_hideItInToString() {
        listOf<Any>(
            PositionFix(48.856614, 2.352222),
            PositionLookup.Found(48.856614, 2.352222, 1L),
            Locate.interpret(found(1 * minute), now, zone),
        ).forEach {
            assertFalse(it.toString(), it.toString().contains("48") || it.toString().contains("2.35"))
        }
    }

    // --- reading of the document of a position (data layer) ---------------------------------------------------------------

    @Test
    fun aDocumentThatIsNotAPosition_isNotFound() {
        assertEquals(PositionLookup.NotFound, found(null, 2.0, 1L))
        assertEquals(PositionLookup.NotFound, found(48.0, null, 1L))
        assertEquals(PositionLookup.NotFound, found(48.0, 2.0, null))
        assertEquals(PositionLookup.NotFound, found(91.0, 2.0, 1L))
        assertEquals(PositionLookup.NotFound, found(48.0, 181.0, 1L))
        assertEquals(PositionLookup.NotFound, found(Double.NaN, 2.0, 1L))
        assertEquals(PositionLookup.NotFound, found(48.0, Double.POSITIVE_INFINITY, 1L))
        assertTrue(found(48.0, 2.0, 1L) is PositionLookup.Found)
    }

    // --- the cleaning of a status ------------------------------------------------------------------------------------------

    @Test
    fun aStatusFromTheServer_isCleaned() {
        val status =
            MemberStatus.fromServer(
                presence = "teleported",
                placeId = "../../admin",
                presenceAtMillis = 1L,
                lastUpdateAtMillis = null,
                hasPhoto = null,
                batteryPct = 250,
                locationAccess = "everything",
                updatedAtMillis = null,
            )
        assertNull(status.presence)
        assertNull(status.placeId)
        assertNull(status.batteryPct)
        assertNull(status.locationAccess)
        assertFalse(status.hasPhoto)
        val good =
            MemberStatus.fromServer("in_risk", "p1", 1L, 2L, true, 0, "background", 3L)
        assertEquals(PresenceValue.IN_RISK, good.presence)
        assertEquals("p1", good.placeId)
        assertEquals(0, good.batteryPct)
        assertEquals(MemberLocationAccess.BACKGROUND, good.locationAccess)
        assertEquals(2L, good.referenceMillis)
    }

    @Test
    fun aNumber_reachesAnIntentOnlyInTheShapeE164() {
        assertEquals("+33612345678", MiffMember.phoneFromServer("+33612345678"))
        assertNull(MiffMember.phoneFromServer(null))
        assertNull(MiffMember.phoneFromServer("0612345678"))
        assertNull(MiffMember.phoneFromServer("+33 6 12 34 56 78"))
        assertNull(MiffMember.phoneFromServer("+33612345678;ATD"))
        assertNull(MiffMember.phoneFromServer("tel:+33612345678"))
        assertNull(MiffMember.phoneFromServer("+33612345678,1"))
        assertNull(MiffMember.phoneFromServer("+123"))
        assertNull(MiffMember.phoneFromServer("+0612345678"))
    }
}
