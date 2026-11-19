package com.tomessa.miff.domain.alert

import com.google.firebase.Timestamp
import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.domain.notification.CircleAlertEvent
import com.tomessa.miff.domain.notification.CircleAlertKeys
import com.tomessa.miff.domain.notification.CircleAlertParser
import com.tomessa.miff.testsupport.DEVICE_A
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules of the alert that need no screen: the contract of the writes with the rules of the server, the hold, the numbers of emergency, who is called, the push. */
class AlertDomainTest {
    private val hour = 3_600_000L
    private val day = 24 * hour
    private val now = 1_800_000_000_000L

    // ---- contract with backend/firestore.rules (alertExpiryWindow, alertShape, alertStart, alertEnd) --------------------------------

    @Test
    fun startWritesTheSevenKeysExactly_andNothingElse() {
        val data = AlertWrites.start(DEVICE_A, now, seq = 1)
        assertEquals(AlertWrites.START_KEYS, data.keys)
        assertEquals("active", data["status"])
        assertNull(data["endedAt"])
        assertEquals(1L, data["seq"])
        assertEquals(DEVICE_A, data["deviceId"])
    }

    @Test
    fun theDocumentHoldsNoPositionNoTextNoPhotoNoName() {
        val forbidden =
            setOf(
                "lat",
                "lng",
                "latitude",
                "longitude",
                "position",
                "text",
                "message",
                "photo",
                "name",
                "firstName",
                "miffId",
                "uid",
            )
        assertTrue(AlertWrites.start(DEVICE_A, now, 2).keys.none { it in forbidden })
        assertTrue(AlertWrites.end(DEVICE_A, now).keys.none { it in forbidden })
    }

    @Test
    fun expiresAt_isThePhoneClockPlus7DaysMinus2Hours_andFitsTheWindowOfTheRules() {
        val expires = AlertRules.expiresAtMillis(now)
        assertEquals(now + 7 * day - 2 * hour, expires)

        // the rules accept (6 d, 7 d] after the SERVER time: a phone 2 h ahead and one 22 h behind both fit, 2 h 1 min ahead and 22 h 1 min behind do not
        fun fits(clockSkew: Long) = (expires - (now - clockSkew)).let { it > 6 * day && it <= 7 * day }
        assertTrue(fits(2 * hour))
        assertTrue(fits(-22 * hour + 1_000))
        assertTrue(fits(0))
        assertFalse(fits(2 * hour + 60_000))
        assertFalse(fits(-22 * hour - 60_000))
    }

    @Test
    fun purgeAt_isExpiresPlus2Days_atTheStart_andPhoneNowPlus2Days_atTheEnd() {
        val data = AlertWrites.start(DEVICE_A, now, 1)
        val expires = (data["expiresAt"] as Timestamp).toDate().time
        assertEquals(expires + 2 * day, (data["purgeAt"] as Timestamp).toDate().time)
        val end = AlertWrites.end(DEVICE_A, now)
        assertEquals(AlertWrites.END_KEYS, end.keys)
        assertEquals("ended", end["status"])
        assertEquals(now + 2 * day, (end["purgeAt"] as Timestamp).toDate().time)
    }

    @Test
    fun theDeviceIdIsMandatoryAndChecked() {
        runCatching { AlertWrites.start("x", now, 1) }.let { assertTrue(it.isFailure) }
        runCatching { AlertWrites.start(DEVICE_A, now, 0) }.let { assertTrue(it.isFailure) }
    }

    @Test
    fun thePathIsTheKey_noFieldRepeatsIt() {
        assertEquals("miffs/m1/alerts/u1", AlertWrites.path("m1", "u1"))
    }

    // ---- reading ---------------------------------------------------------------------------------------------------------------------

    @Test
    fun aDocumentIsLiveWhileActiveAndNotPastItsDate() {
        val doc = AlertDoc.parse("u", "active", now - 1000, now + 1000, 1)!!
        assertTrue(doc.isLive(now))
        assertFalse(doc.isLive(now + 1000))
        assertFalse(AlertDoc.parse("u", "ended", now, now + 1000, 1)!!.isLive(now))
    }

    @Test
    fun aDocumentThatIsNotOneOfOurs_isIgnored() {
        assertNull(AlertDoc.parse("u", "weird", now, now + 1, 1))
        assertNull(AlertDoc.parse("u", "active", now, null, 1))
        assertNull(AlertDoc.parse("bad uid!", "active", now, now + 1, 1))
        assertNotNull(AlertDoc.parse("u", "active", null, now + 1, "x"))
    }

    @Test
    fun liveAt_keepsTheOnesInProgress_theOldestFirst() {
        val docs =
            listOf(
                AlertDoc("b", true, now - 10, now + 5, 1),
                AlertDoc("a", true, now - 50, now + 5, 1),
                AlertDoc(
                    "c",
                    true,
                    now - 70,
                    now - 1,
                    1,
                ),
            )
        assertEquals(listOf("a", "b"), docs.liveAt(now).map { it.uid })
    }

    // ---- the daily limit (decision of the PM: 3 per 24 h, a counter written in the same lot as the alert)

    @Test
    fun quota_aFreeSlotIsUsedFirst() {
        assertEquals(0, AlertQuota.NONE.slotToReplace(now))
        assertEquals(1, AlertQuota(listOf(now - hour, null, null)).slotToReplace(now))
        assertEquals(2, AlertQuota(listOf(now - hour, now - 2 * hour, null)).slotToReplace(now))
    }

    @Test
    fun quota_threeStartsInTheLast24Hours_noSlot_theStartIsNotTried() {
        assertNull(AlertQuota(listOf(now - hour, now - 2 * hour, now - 23 * hour)).slotToReplace(now))
    }

    @Test
    fun quota_aStartOlderThan24Hours_isReplaced_withTheSafetyMarginOfThePhoneClock() {
        assertEquals(2, AlertQuota(listOf(now - hour, now - 2 * hour, now - 25 * hour)).slotToReplace(now))
        // 24 h and 1 minute: the server would accept, the phone waits 2 more minutes (its clock is not the server's)
        assertNull(AlertQuota(listOf(now - hour, now - 2 * hour, now - 24 * hour - 60_000)).slotToReplace(now))
    }

    @Test
    fun quota_theFirstStartCreatesTheCounter_sixKeys_firstSlotServerTime_othersNull() {
        val data = AlertWrites.quotaCreate("m1", now)
        assertEquals(AlertWrites.QUOTA_CREATE_KEYS, data.keys)
        assertNull(data["s1"])
        assertNull(data["s2"])
        assertEquals("m1", data["lastMiffId"])
        val expire = (data["expireAt"] as Timestamp).toDate().time
        // the rules accept 1 to 3 days after the server time: phone time + 2 days
        assertEquals(now + 2 * day, expire)
        assertEquals("users/u/quota/alerts", AlertWrites.quotaPath("u"))
    }

    @Test
    fun quota_aLaterStartChangesOneSlotOnly_andTheRulesSeeNothingElse() {
        val data = AlertWrites.quotaUpdate(1, "m2", now)
        assertEquals(setOf("s1", "lastMiffId", "updatedAt", "expireAt"), data.keys)
        runCatching { AlertWrites.quotaUpdate(3, "m2", now) }.let { assertTrue(it.isFailure) }
    }

    // ---- the order of the checks of a start (review of #275, M1)

    private fun plan(
        alert: AlertDoc?,
        quota: AlertQuota,
        counterExists: Boolean = true,
        sharing: Boolean = true,
        card: Boolean = true,
    ) = AlertStartPlan.decide(card, sharing, alert != null, alert, counterExists, quota, now)

    private val full = AlertQuota(listOf(now - hour, now - 2 * hour, now - 3 * hour))

    @Test
    fun start_aLiveAlertIsAdopted_evenWhenTheCounterIsFull_theLostAnswerOfTheThirdStart() {
        val live = AlertDoc("u", true, now - 20_000, now + 6 * day, 3)
        assertTrue(plan(live, full) is StartDecision.Adopt)
    }

    @Test
    fun start_withoutALiveAlert_aFullCounterIsTheLimit_nothingIsTried() {
        assertTrue(plan(null, full) is StartDecision.QuotaFull)
        val ended = AlertDoc("u", false, now - 10 * hour, now + 5 * day, 2)
        assertTrue(plan(ended, full) is StartDecision.QuotaFull)
    }

    @Test
    fun start_aReArmingUnder60Seconds_waits_beforeTheLimitIsJudged_andConsumesNothing() {
        val justEnded = AlertDoc("u", false, now - 20_000, now + 5 * day, 1)
        val decision = plan(justEnded, full)
        assertTrue(decision is StartDecision.Wait)
        assertEquals(40_000 + AlertRules.REARM_SAFETY_MILLIS, (decision as StartDecision.Wait).waitMillis)
    }

    @Test
    fun start_theFirstAlert_createsTheCounter_theNextOnesReplaceASlot_andTheSeqGoesUp() {
        val first = plan(null, AlertQuota.NONE, counterExists = false) as StartDecision.Write
        assertNull(first.slot)
        assertEquals(1L, first.seq)
        val later =
            plan(
                AlertDoc("u", false, now - 2 * hour, now + 5 * day, 4),
                AlertQuota(listOf(now - hour, null, null)),
            ) as StartDecision.Write
        assertEquals(1, later.slot)
        assertEquals(5L, later.seq)
    }

    @Test
    fun start_noCard_orSharingStopped_comeFirst() {
        assertTrue(plan(null, full, card = false) is StartDecision.NotMember)
        assertTrue(plan(null, full, sharing = false) is StartDecision.SharingStopped)
    }

    // ---- the hold ----------------------------------------------------------------------------------------------------------------------

    @Test
    fun hold_pressingThenHoldingThenDone() {
        assertEquals(HoldPhase.Pressing, AlertHold.phaseAt(0))
        assertEquals(HoldPhase.Pressing, AlertHold.phaseAt(399))
        assertEquals(0f, (AlertHold.phaseAt(400) as HoldPhase.Holding).fraction, 0.0001f)
        assertEquals(0.5f, (AlertHold.phaseAt(400 + 2_500) as HoldPhase.Holding).fraction, 0.0001f)
        assertTrue(AlertHold.phaseAt(400 + 4_999) is HoldPhase.Holding)
        assertEquals(HoldPhase.Done, AlertHold.phaseAt(400 + 5_000))
    }

    @Test
    fun hold_theFingerMayDriftLessThan48dp() {
        assertFalse(AlertHold.movedTooFar(30f, 30f, 48f))
        assertTrue(AlertHold.movedTooFar(40f, 40f, 48f))
    }

    // ---- emergency numbers -------------------------------------------------------------------------------------------------------------

    private fun numbers(region: String?) = EmergencyCatalog.forRegion(region).map { it.number }

    @Test
    fun france_17_15_18_112() {
        assertEquals(listOf("17", "15", "18", "112"), numbers("FR"))
        assertEquals(listOf("17", "15", "18", "112"), numbers("fr"))
        assertEquals(listOf("17", "15", "18", "112"), numbers("RE"))
    }

    @Test
    fun otherEuropeanCountries_112() {
        listOf("DE", "ES", "IT", "PT", "BE", "NO", "IS").forEach { assertEquals(it, listOf("112"), numbers(it)) }
        assertEquals(EmergencyLabel.EUROPEAN_112, EmergencyCatalog.forRegion("DE").single().label)
    }

    @Test
    fun unknownRegion_112_withTheHonestInternationalLabel() {
        listOf(null, "", "ZZ", "US").forEach { assertEquals(listOf("112"), numbers(it)) }
        assertEquals(EmergencyLabel.INTERNATIONAL_112, EmergencyCatalog.forRegion(null).single().label)
    }

    @Test
    fun everyNumberOfTheTableCanReachTheDialler_andAnythingElseCannot() {
        EmergencyCatalog.all().forEach { assertTrue(it.number, EmergencyCatalog.isDialable(it.number)) }
        listOf("", "1", "+33612345678", "17;", "tel:17", "12345", "1 7").forEach {
            assertFalse(it, EmergencyCatalog.isDialable(it))
        }
    }

    // ---- who is called -----------------------------------------------------------------------------------------------------------------

    private fun m(
        uid: String,
        first: String,
        owner: Boolean = false,
        phone: String? = "+3360000000${uid.length}",
        last: String = "X",
    ) = MiffMember(uid, first, last, null, if (owner) MiffRole.OWNER else MiffRole.MEMBER, true, phone)

    @Test
    fun theCreatorIsPreselected_whenItIsNotMe() {
        // the list is already in the order of the app: the creator first, then the order of arrival
        val list =
            AlertCallList.build(
                listOf(m("c", "Emmanuelle", owner = true), m("me", "Moi"), m("z", "Zoé")),
                "me",
                "FR",
            )
        assertEquals(listOf("Emmanuelle", "Zoé"), list.people.map { it.name })
        assertEquals("p:c", list.preselected.id)
    }

    @Test
    fun iAmTheCreator_theFirstOtherMemberByArrival() {
        val list =
            AlertCallList.build(
                listOf(m("me", "Moi", owner = true), m("b", "Bastien"), m("z", "Zoé")),
                "me",
                "FR",
            )
        assertEquals("p:b", list.preselected.id)
    }

    @Test
    fun theCreatorWithoutANumber_isReplacedByTheNextOne_andNeverListed() {
        val list =
            AlertCallList.build(
                listOf(m("c", "Emmanuelle", owner = true, phone = null), m("me", "Moi"), m("z", "Zoé")),
                "me",
                "FR",
            )
        assertEquals(listOf("Zoé"), list.people.map { it.name })
        assertEquals("p:z", list.preselected.id)
    }

    @Test
    fun aloneOrNobodyWithANumber_onlyTheEmergencyServices_112Preselected() {
        val alone = AlertCallList.build(listOf(m("me", "Moi", owner = true)), "me", "FR")
        assertTrue(alone.people.isEmpty())
        assertEquals("112", alone.preselected.number)
        val noNumbers = AlertCallList.build(listOf(m("me", "Moi"), m("x", "Xavier", phone = null)), "me", "DE")
        assertTrue(noNumbers.people.isEmpty())
        assertEquals("112", noNumbers.preselected.number)
    }

    @Test
    fun theListHoldsThePeopleThenTheServices_andNobodyElse() {
        val list = AlertCallList.build(listOf(m("c", "Emmanuelle", owner = true), m("me", "Moi")), "me", "FR")
        assertEquals(
            listOf(
                "p:c",
                "e:17:POLICE",
                "e:15:AMBULANCE",
                "e:18:FIRE",
                "e:112:EUROPEAN_112",
            ),
            list.targets.map {
                it.id
            },
        )
        assertTrue(list.targets.none { it.id == "p:me" })
    }

    @Test
    fun twoSameFirstNames_getTheirLastNameToo() {
        val list =
            AlertCallList.build(
                listOf(m("c", "Emma", owner = true, last = "Durand"), m("d", "Emma", last = "Martin"), m("me", "Moi")),
                "me",
                "FR",
            )
        assertEquals(listOf("Emma Durand", "Emma Martin"), list.people.map { it.name })
    }

    // ---- the push -------------------------------------------------------------------------------------------------------------------

    private fun push(
        event: String = "start",
        name: String = "Thomas",
    ) = mapOf("type" to "circle_alert", "event" to event, "miffId" to "m1", "authorUid" to "u1", "firstName" to name)

    @Test
    fun theStartAndTheEndAreUnderstood() {
        assertEquals(CircleAlertEvent.START, CircleAlertParser.parse(push("start"), now)!!.event)
        assertEquals(CircleAlertEvent.END, CircleAlertParser.parse(push("end"), now)!!.event)
    }

    @Test
    fun aMessageThatDoesNotLookExactlyRight_isIgnored() {
        assertNull(CircleAlertParser.parse(push("expired"), now))
        assertNull(CircleAlertParser.parse(push() + ("miffId" to "../x"), now))
        assertNull(CircleAlertParser.parse(push() - "authorUid", now))
        assertNull(CircleAlertParser.parse(push(name = "\u0000"), now))
        assertNull(CircleAlertParser.parse(push() + ("type" to "circle_update"), now))
        assertNull(CircleAlertParser.parse(push(), 0))
    }

    @Test
    fun theFirstNameIsCleanedAndCut_beforeItReachesALockedScreen() {
        val long = CircleAlertParser.parse(push(name = "A".repeat(80)), now)!!
        assertTrue(long.authorFirstName.length <= 20)
        assertEquals("Thomas", CircleAlertParser.parse(push(name = "  Thomas‮ "), now)!!.authorFirstName)
    }

    @Test
    fun theEndReplacesTheStartInTheTray_perAuthor() {
        val start = CircleAlertParser.parse(push("start"), now)!!
        val end = CircleAlertParser.parse(push("end"), now)!!
        assertEquals(CircleAlertKeys.trayTag(start), CircleAlertKeys.trayTag(end))
        assertTrue(CircleAlertKeys.centerId(start) != CircleAlertKeys.centerId(end))
        assertEquals("CircleAlertPayload(redacted)", start.toString())
    }
}
