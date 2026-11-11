package com.tomessa.miff.domain.place

import com.google.firebase.firestore.FieldValue
import com.tomessa.miff.domain.miff.WriteKind
import com.tomessa.miff.testsupport.MIFF_ID
import com.tomessa.miff.testsupport.requestFields
import com.tomessa.miff.testsupport.testRequest
import com.tomessa.miff.testsupport.testRequestDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The request to add a place (ticket LIEU-REQ, sections 5 and 6): what is accepted from the server, the limits, the lots. No Firestore. */
class PlaceRequestsTest {
    private val day = 24L * 60L * 60L * 1000L

    /** The operand of a `FieldValue.increment(n)` (the SDK keeps it in a private field: read it to prove the SIGN of the move). */
    private fun incrementOf(value: Any?): Long {
        assertTrue("not an increment", value is FieldValue)
        val field =
            value!!.javaClass.declaredFields.first {
                Number::class.java.isAssignableFrom(it.type) ||
                    it.type == Long::class.javaPrimitiveType
            }
        field.isAccessible = true
        return (field.get(value) as Number).toLong()
    }

    // --- What is read from the server -------------------------------------------------------------------------------------------

    @Test
    fun fromServer_aValidRequest_isRead_andCleaned() {
        val request = PlaceRequest.fromServer("u2_1", MIFF_ID, requestFields(name = "  Chez   Mamie  "))
        assertNotNull(request)
        assertEquals("u2", request!!.requestedBy)
        assertEquals(1, request.slot)
        assertEquals(PlaceType.TRUSTED, request.type)
        assertEquals(1_000L, request.createdAtMillis)
    }

    @Test
    fun fromServer_aRequestThatSaysItIsSomebodyElses_isIgnored() {
        // The identifier says u2, the field says u9: a forged document is never shown (the creator would read a wrong "requested by").
        assertNull(PlaceRequest.fromServer("u2_1", MIFF_ID, requestFields(requestedBy = "u9")))
    }

    @Test
    fun fromServer_badIdentifiers_areIgnored() {
        assertNull(PlaceRequest.fromServer("u2_4", MIFF_ID, requestFields()))
        assertNull(PlaceRequest.fromServer("u2_0", MIFF_ID, requestFields()))
        assertNull(PlaceRequest.fromServer("u2", MIFF_ID, requestFields()))
        assertNull(PlaceRequest.fromServer("_1", MIFF_ID, requestFields(uid = "")))
        assertNull(PlaceRequest.fromServer("u2_1/x", MIFF_ID, requestFields()))
    }

    @Test
    fun fromServer_badValues_areIgnored_neverACrash() {
        assertNull(PlaceRequest.fromServer("u2_1", MIFF_ID, null))
        assertNull(PlaceRequest.fromServer("u2_1", MIFF_ID, requestFields(name = "   ")))
        assertNull(PlaceRequest.fromServer("u2_1", MIFF_ID, requestFields(name = 12)))
        assertNull(PlaceRequest.fromServer("u2_1", MIFF_ID, requestFields(type = "party")))
        assertNull(PlaceRequest.fromServer("u2_1", MIFF_ID, requestFields(lat = 91.0)))
        assertNull(PlaceRequest.fromServer("u2_1", MIFF_ID, requestFields(lng = Double.NaN)))
        assertNull(PlaceRequest.fromServer("u2_1", MIFF_ID, requestFields(radiusM = 49L)))
        assertNull(PlaceRequest.fromServer("u2_1", MIFF_ID, requestFields(radiusM = 2001L)))
    }

    @Test
    fun fromServer_aDangerZone_isAKnownType_andNoAddressIsAllowed() {
        val risk = PlaceRequest.fromServer("u2_2", MIFF_ID, requestFields(type = "risk", address = null))
        assertEquals(PlaceType.RISK, risk!!.type)
        assertNull(risk.address)
    }

    @Test
    fun toString_showsNothing() {
        assertEquals("PlaceRequest(redacted)", testRequest(name = "Chez Mamie").toString())
        assertEquals("PlaceRequestDraft(redacted)", testRequestDraft().toString())
    }

    // --- The 30 days -----------------------------------------------------------------------------------------------------

    @Test
    fun expiry_exactlyThirtyDaysIsStillThere_oneMillisecondMoreIsGone() {
        val request = testRequest(createdAtMillis = 0L)
        assertFalse(request.isExpired(30 * day))
        assertTrue(request.isExpired(30 * day + 1))
    }

    @Test
    fun expiry_aRequestNotDatedYet_isNeverExpired() {
        assertFalse(testRequest(createdAtMillis = null).isExpired(Long.MAX_VALUE))
    }

    // --- Order and slots -------------------------------------------------------------------------------------------------------

    @Test
    fun order_creatorOldestFirst_memberNewestFirst_aRequestNotDatedYetIsTheNewest() {
        val old = testRequest(slot = 1, createdAtMillis = 10)
        val recent = testRequest(slot = 2, createdAtMillis = 20)
        val fresh = testRequest(slot = 3, createdAtMillis = null)
        assertEquals(
            listOf("u2_1", "u2_2", "u2_3"),
            PlaceRequestOrder.forCreator(listOf(fresh, recent, old)).map { it.id },
        )
        assertEquals(
            listOf("u2_3", "u2_2", "u2_1"),
            PlaceRequestOrder.forMember(listOf(old, fresh, recent)).map { it.id },
        )
    }

    @Test
    fun slots_theFirstFreeOneOfThreeIsChosen_noFourthExists() {
        assertEquals(1, PlaceRequestSlots.firstFree("u2", emptyList()))
        assertEquals(2, PlaceRequestSlots.firstFree("u2", listOf(testRequest(slot = 1))))
        assertEquals(1, PlaceRequestSlots.firstFree("u2", listOf(testRequest(slot = 2), testRequest(slot = 3))))
        assertNull(PlaceRequestSlots.firstFree("u2", listOf(1, 2, 3).map { testRequest(slot = it) }))
        // The requests of another person never take a slot of this one.
        assertEquals(1, PlaceRequestSlots.firstFree("u2", listOf(testRequest(uid = "u3", slot = 1))))
    }

    // --- The lots (the contract with the rules) --------------------------------------------------------------------------------

    @Test
    fun create_isOneLotOfTwo_theRequestAndTheCounterPlusOne() {
        val lot = PlaceRequestWrites.create("u2", MIFF_ID, 2, testRequestDraft())
        assertEquals(2, lot.size)
        assertEquals("miffs/$MIFF_ID/placeRequests/u2_2", lot[0].path)
        assertEquals(WriteKind.SET, lot[0].kind)
        assertEquals("miffs/$MIFF_ID/meta/placeRequests", lot[1].path)
        // A merge: the increment adds to a counter that exists and creates it at 1 otherwise.
        assertEquals(WriteKind.MERGE, lot[1].kind)
    }

    @Test
    fun create_writesEveryKeyOfTheRequest_andOnlyThose_withTheCoordinatesOnFiveDecimals() {
        val doc = PlaceRequestWrites.requestDocument("u2", testRequestDraft(name = "  Chez Mamie "))
        assertEquals(
            setOf("requestedBy", "name", "type", "category", "address", "lat", "lng", "radiusM", "createdAt"),
            doc.keys,
        )
        assertEquals("u2", doc["requestedBy"])
        assertEquals("Chez Mamie", doc["name"])
        assertEquals("trusted", doc["type"])
        assertEquals(48.85661, doc["lat"])
        assertEquals(2.35222, doc["lng"])
        // The date is the server's, never the phone's.
        assertEquals(FieldValue.serverTimestamp(), doc["createdAt"])
    }

    @Test
    fun create_aRequestCarriesTheCategoryChosen_noPriority_noCreatedBy() {
        val doc = PlaceRequestWrites.requestDocument("u2", testRequestDraft(category = PlaceCategory.SCHOOL))
        assertEquals("school", doc["category"])
        val keys = doc.keys
        assertFalse("priority" in keys)
        assertFalse("createdBy" in keys)
        assertFalse("status" in keys)
    }

    @Test
    fun counter_ofAMember_isTiedToTheRequest_andMovesByTheDeltaOnly() {
        val up = PlaceRequestWrites.memberCounter("u2_1", 1)
        assertEquals(setOf("pending", "lastReqId", "updatedAt"), up.keys)
        assertEquals("u2_1", up["lastReqId"])
        assertEquals(1L, incrementOf(up["pending"]))
        assertEquals(-1L, incrementOf(PlaceRequestWrites.memberCounter("u2_1", -1)["pending"]))
    }

    @Test
    fun counter_ofTheCreator_isTheRealNumber_tiedToNoRequest_andStaysBetweenZeroAndTen() {
        val doc = PlaceRequestWrites.creatorCounter(4)
        assertEquals(4, doc["pending"])
        assertNull(doc["lastReqId"])
        assertTrue(doc.containsKey("lastReqId"))
        assertEquals(0, PlaceRequestWrites.creatorCounter(-3)["pending"])
        assertEquals(PlaceRequestLimits.PER_MIFF, PlaceRequestWrites.creatorCounter(99)["pending"])
    }

    @Test
    fun withdraw_isOneLotOfTwo_theDeletionAndTheCounterMinusOne() {
        val lot = PlaceRequestWrites.withdraw(MIFF_ID, "u2_3")
        assertEquals(WriteKind.DELETE, lot[0].kind)
        assertEquals("miffs/$MIFF_ID/placeRequests/u2_3", lot[0].path)
        assertEquals(WriteKind.MERGE, lot[1].kind)
        assertEquals(-1L, incrementOf(lot[1].data["pending"]))
        assertEquals("u2_3", lot[1].data["lastReqId"])
    }

    // --- The draft ------------------------------------------------------------------------------------------------------------

    @Test
    fun draft_followsTheSameChecksAsAPlace() {
        assertNull(testRequestDraft().problem)
        assertEquals(DraftProblem.NAME, testRequestDraft(name = "A").problem)
        assertEquals(DraftProblem.RADIUS, testRequestDraft(radiusM = 75).problem)
        assertEquals(
            DraftProblem.ADDRESS,
            PlaceRequestDraft("Maison", "  ", 48.0, 2.0, 100, PlaceType.TRUSTED, PlaceCategory.HOME).problem,
        )
    }

    @Test
    fun limits_areThoseOfTheTicket() {
        assertEquals(3, PlaceRequestLimits.PER_MEMBER)
        assertEquals(10, PlaceRequestLimits.PER_MIFF)
        assertEquals(30 * day, PlaceRequestLimits.EXPIRY_MILLIS)
        assertEquals("u2_3", PlaceRequestLimits.idOf("u2", 3))
    }
}
