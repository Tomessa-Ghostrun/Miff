package com.tomessa.miff.domain.travel

import com.tomessa.miff.domain.feed.MemberStatus
import com.tomessa.miff.domain.tracking.MemberStatusFacts
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.domain.tracking.StatusColor
import com.tomessa.miff.domain.tracking.StatusLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * MODE VOYAGE, the pure rules (VOY-1A; the cases are the contract with iOS): the bounds of the day, the instant of the end (00:00 local of the NEXT day), the day text
 * and its reading, what a loved one sees of a trip, and the colour (always neutral).
 */
class TravelModeTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val today = LocalDate.of(2026, 11, 10)

    // --- The day chosen -----------------------------------------------------------------------------------------------------------

    @Test
    fun today_isTheFirstDayThatCanBeChosen_andTodayPlus90TheLast() {
        assertTrue(TravelPlan.choose(today, today, paris) is TravelChoice.Valid)
        assertTrue(TravelPlan.choose(today.plusDays(90), today, paris) is TravelChoice.Valid)
        assertEquals(TravelChoice.TooEarly, TravelPlan.choose(today.minusDays(1), today, paris))
        assertEquals(TravelChoice.TooLate, TravelPlan.choose(today.plusDays(91), today, paris))
    }

    @Test
    fun theBoundsOfThePicker_areTheOnesOfTheRule_andTheDefaultIsInside() {
        assertEquals(today, TravelPlan.minDay(today))
        assertEquals(today.plusDays(90), TravelPlan.maxDay(today))
        assertEquals(today.plusDays(7), TravelPlan.defaultDay(today))
        assertTrue(TravelPlan.choose(TravelPlan.defaultDay(today), today, paris) is TravelChoice.Valid)
    }

    @Test
    fun theTripEnds_atMidnightOfTheDayAfterTheOneChosen_soTheLastDayIsWhole() {
        val choice = TravelPlan.choose(LocalDate.of(2026, 11, 15), today, paris) as TravelChoice.Valid
        // 16 Nov 2026 00:00 in Paris (UTC+1 in November) = 15 Nov 23:00 UTC.
        assertEquals(
            LocalDate
                .of(2026, 11, 16)
                .atStartOfDay(paris)
                .toInstant()
                .toEpochMilli(),
            choice.period.untilMillis,
        )
        assertEquals("2026-11-15", choice.period.untilDay)
        // The whole last day is still "on a trip", the first instant of the next day is not.
        assertFalse(choice.period.isOver(choice.period.untilMillis - 1))
        assertTrue(choice.period.isOver(choice.period.untilMillis))
    }

    @Test
    fun theEnd_followsTheDaylightSavingChange_itIsMidnightLocalNotTwentyFourHoursAfterNoon() {
        // 26 Oct 2026: the clocks go back in Paris (that day has 25 hours).
        val choice =
            TravelPlan.choose(
                LocalDate.of(2026, 10, 26),
                LocalDate.of(2026, 10, 20),
                paris,
            ) as TravelChoice.Valid
        val expected =
            LocalDate
                .of(2026, 10, 27)
                .atStartOfDay(paris)
                .toInstant()
                .toEpochMilli()
        assertEquals(expected, choice.period.untilMillis)
    }

    // --- The text of the day -----------------------------------------------------------------------------------------------------

    @Test
    fun theDayText_isTheIsoDate_andOnlyAnIsoDateIsRead() {
        assertEquals("2026-11-05", TravelPlan.dayText(LocalDate.of(2026, 11, 5)))
        assertEquals(LocalDate.of(2026, 11, 5), TravelPlan.parseDay("2026-11-05"))
        listOf(
            null,
            "",
            "2026-1-5",
            "2026-13-01",
            "2026-02-30",
            "05/11/2026",
            "2026-11-05T00:00",
            " 2026-11-05",
            "2026-11-05\n",
            "abcd-ef-gh",
        ).forEach {
            assertNull("«$it» is not a day", TravelPlan.parseDay(it))
        }
    }

    @Test
    fun theYearIsShown_onlyWhenItIsNotTheCurrentOne() {
        assertFalse(TravelDay.of("2026-12-31", null, today, paris)!!.showYear)
        assertTrue(TravelDay.of("2027-01-02", null, today, paris)!!.showYear)
        assertTrue(TravelDay.of("2025-12-31", null, today, paris)!!.showYear)
    }

    @Test
    fun withoutAUsableDay_theDayBeforeTheEndIsUsed_inTheZoneOfTheReader_andNothingElseGivesNull() {
        val until =
            LocalDate
                .of(2026, 11, 16)
                .atStartOfDay(paris)
                .toInstant()
                .toEpochMilli()
        assertEquals(LocalDate.of(2026, 11, 15), TravelDay.of("garbage", until, today, paris)!!.date)
        assertEquals(LocalDate.of(2026, 11, 15), TravelDay.of(null, until, today, paris)!!.date)
        assertNull(TravelDay.of("garbage", null, today, paris))
    }

    // --- What a loved one sees ---------------------------------------------------------------------------------------------------

    private val until =
        LocalDate
            .of(2026, 11, 16)
            .atStartOfDay(paris)
            .toInstant()
            .toEpochMilli()
    private val now =
        LocalDate
            .of(2026, 11, 10)
            .atTime(12, 0)
            .atZone(paris)
            .toInstant()
            .toEpochMilli()

    private fun view(
        presence: PresenceValue? = PresenceValue.TRAVEL,
        untilMillis: Long? = until,
        day: String? = "2026-11-15",
        at: Long = now,
    ) = TravelView.of(presence, untilMillis, day, at, paris)

    @Test
    fun beforeTheEnd_theTripIsActive_withTheDayChosen() {
        val active = view() as TravelView.Active
        assertEquals(LocalDate.of(2026, 11, 15), active.until.date)
        assertFalse(active.until.showYear)
    }

    @Test
    fun fromTheEnd_itIsOver_neverAFalseTrip() {
        assertEquals(TravelView.Ended, view(at = until))
        assertEquals(TravelView.Ended, view(at = until + 10 * 24 * 3_600_000L))
        assertTrue(view(at = until - 1) is TravelView.Active)
    }

    @Test
    fun whateverIsNotATrip_isNull() {
        assertNull(view(presence = PresenceValue.OUT))
        assertNull(view(presence = PresenceValue.UNKNOWN))
        assertNull(view(presence = null))
        // A trip with no end (a modified client) is no trip: neutral, no label.
        assertNull(view(untilMillis = null))
    }

    @Test
    fun aTripWhoseDayIsGarbage_stillShowsTheDayDerivedFromTheEnd() {
        val active = view(day = "not a day") as TravelView.Active
        assertEquals(LocalDate.of(2026, 11, 15), active.until.date)
    }

    // --- The colour -----------------------------------------------------------------------------------------------------------------

    @Test
    fun aTraveller_isAlwaysNeutral_beforeAndAfterTheEnd() {
        val facts = MemberStatusFacts(true, PresenceValue.TRAVEL, now - 1_000L, now - 10 * 3_600_000L, until)
        assertEquals(StatusLevel.NEUTRAL, StatusColor.level(facts, now))
        assertEquals(StatusLevel.NEUTRAL, StatusColor.level(facts, until + 1))
        assertEquals(StatusLevel.NEUTRAL, StatusColor.level(facts, until + 30 * 24 * 3_600_000L))
        // And with no times at all.
        assertEquals(
            StatusLevel.NEUTRAL,
            StatusColor.level(MemberStatusFacts(true, PresenceValue.TRAVEL, null, null), now),
        )
    }

    @Test
    fun theWireValue_isTravel_andAnOlderApplicationReadsItAsUnknown() {
        assertEquals("travel", PresenceValue.TRAVEL.wire)
        assertEquals(PresenceValue.TRAVEL, PresenceValue.fromWire("travel"))
        // F6: a version of the app that does not know the value reads null, which the colour treats as neutral (the same function, with null).
        assertEquals(StatusLevel.NEUTRAL, StatusColor.level(MemberStatusFacts(true, null, now, now), now))
    }

    @Test
    fun aStatusKeepsTheEndOfTheTrip_onlyWhenItIsATrip() {
        val trip =
            MemberStatus.fromServer(
                "travel",
                null,
                now,
                now,
                false,
                null,
                "background",
                now,
                until,
                "2026-11-15",
            )
        assertEquals(until, trip.travelUntilMillis)
        assertEquals("2026-11-15", trip.travelUntilDay)
        // Somebody else wrote an end next to another presence: dropped.
        val other = MemberStatus.fromServer("out", "home", now, now, false, 50, "background", now, until, "2026-11-15")
        assertNull(other.travelUntilMillis)
        assertNull(other.travelUntilDay)
        // The status of an application that did not know the trip keeps working.
        assertNull(MemberStatus.fromServer("out", "home", now, now, false, 50, "background", now).travelUntilMillis)
    }
}
