package com.tomessa.miff.domain.timeshare

import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.domain.tracking.PresenceValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

/**
 * The calculation of "Répartition de ton temps": the cases T1 to T23 of docs/tickets/profil-repartition-temps.md section 6.3 (the same cases pass on iOS), and the
 * invariants TS-10 and TS-11. A window of 7 days is 168 h; times below are in hours after the START of the window unless the name says otherwise.
 */
class TimeShareTest {
    private val hour = TimeShareRules.HOUR_MILLIS
    private val now = 1_800_000_000_000L
    private val window7 = now - 7 * TimeShareRules.DAY_MILLIS

    private val home = PlaceRef("home", PlaceType.TRUSTED)
    private val sport = PlaceRef("sport", PlaceType.TRUSTED)
    private val office = PlaceRef("office", PlaceType.TRUSTED)
    private val risk1 = PlaceRef("risk1", PlaceType.RISK)
    private val risk2 = PlaceRef("risk2", PlaceType.RISK)

    private fun at(hours: Double): Long = window7 + (hours * hour).toLong()

    private fun inPlace(
        hours: Double,
        place: String,
    ) = LoggedPresence(at(hours), PresenceValue.IN_TRUSTED, place)

    private fun inRisk(
        hours: Double,
        place: String,
    ) = LoggedPresence(at(hours), PresenceValue.IN_RISK, place)

    private fun out(hours: Double) = LoggedPresence(at(hours), PresenceValue.OUT, null)

    private fun unknown(hours: Double) = LoggedPresence(at(hours), PresenceValue.UNKNOWN, null)

    /**
     * A stay of [from] to [to] hours written the way a long stay is lived: a state is counted 48 h at most without a new fact (D9), so a longer one is cut by an unknown fact of
     * no duration (the app was opened, the engine looked again). The sum is exactly the stay.
     */
    private fun stay(
        from: Double,
        to: Double,
        make: (Double) -> LoggedPresence,
    ): List<LoggedPresence> {
        val facts = mutableListOf<LoggedPresence>()
        var t = from
        while (t < to) {
            facts += make(t)
            val next = minOf(t + 40.0, to)
            if (next < to) facts += unknown(next)
            t = next
        }
        return facts
    }

    private fun compute(
        facts: List<LoggedPresence>,
        places: List<PlaceRef>,
        days: Int = 7,
        clock: Long = now,
    ) = TimeShare.compute(facts, places, days, clock)

    private fun ready(result: TimeShareResult): TimeShareResult.Ready {
        assertTrue("expected a chart, got $result", result is TimeShareResult.Ready)
        return result as TimeShareResult.Ready
    }

    private fun TimeShareResult.Ready.percent(
        kind: SliceKind,
        placeId: String? = null,
    ): Int = slices.first { it.kind == kind && (placeId == null || it.placeId == placeId) }.percent

    private fun assertSumIs100(result: TimeShareResult.Ready) = assertEquals(100, result.slices.sumOf { it.percent })

    // --- T1 to T8 -----------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun t1_homeSportAndOutside_giveFiftyTwelveAndThirtyEight_theTieGoesToTheLongestTime() {
        // Maison 84 h, Sport 21 h, dehors 63 h: 50 / 12.5 / 37.5. The point of the tie (0.5) goes to the longer real time, "Ailleurs".
        val facts =
            stay(0.0, 84.0) { inPlace(it, "home") } + stay(84.0, 105.0) { inPlace(it, "sport") } +
                stay(105.0, 168.0) { out(it) }
        val result = ready(compute(facts, listOf(home, sport)))
        assertEquals(50, result.percent(SliceKind.PLACE, "home"))
        assertEquals(12, result.percent(SliceKind.PLACE, "sport"))
        assertEquals(38, result.percent(SliceKind.ELSEWHERE))
        assertSumIs100(result)
        assertEquals(62, result.centerPercent)
    }

    @Test
    fun t2_aStateThatBeganBeforeTheWindow_isCountedFromTheStartOfTheWindow() {
        // "Maison" for 10 h before the window and until 20 h after its start: 20 h are counted (the 10 h before the window are cut off).
        val tally = TimeShare.tally(listOf(inPlace(-10.0, "home"), out(20.0)), listOf(home), 7, now)
        assertEquals(20 * hour, tally.trusted["home"])
    }

    @Test
    fun t3_aShortStateWithNoNextFact_runsUntilNow() {
        val tally =
            TimeShare.tally(
                listOf(LoggedPresence(now - 5 * hour, PresenceValue.IN_TRUSTED, "home")),
                listOf(home),
                7,
                now,
            )
        assertEquals(5 * hour, tally.trusted["home"])
    }

    @Test
    fun t4_aStateIsCountedFortyEightHoursAtMost_theRestIsNotMeasured() {
        val tally =
            TimeShare.tally(
                listOf(LoggedPresence(now - 60 * hour, PresenceValue.IN_TRUSTED, "home")),
                listOf(home),
                7,
                now,
            )
        assertEquals(48 * hour, tally.trusted["home"])
        assertEquals(0L, tally.elsewhere)
        // 12 h more are left out, never turned into "Ailleurs".
        assertEquals(48 * hour, tally.measured)
    }

    @Test
    fun t5_twoIdenticalFactsInARow_areOneState() {
        val tally =
            TimeShare.tally(
                listOf(inPlace(0.0, "home"), inPlace(30.0, "home"), out(40.0)),
                listOf(home),
                7,
                now,
            )
        assertEquals(40 * hour, tally.trusted["home"])
    }

    @Test
    fun t6_aFactDatedBeforeThePreviousOne_lasts0_neverANegativeTime() {
        // The clock moved back: "dehors" dated 5 h comes after "Maison" dated 10 h, so it starts at 10 h.
        val tally =
            TimeShare.tally(
                listOf(inPlace(10.0, "home"), out(5.0), inPlace(20.0, "sport")),
                listOf(home, sport),
                7,
                now,
            )
        assertNull("the state dated before the previous one lasts 0 for the previous", tally.trusted["home"])
        assertEquals(10 * hour, tally.elsewhere)
        assertTrue(tally.trusted.values.all { it >= 0 } && tally.elsewhere >= 0)
    }

    @Test
    fun t7_twoPlacesInOneAfterTheOther_nothingIsCountedTwice() {
        // A entered at 0 h, B at 2 h, B left at 3 h (back in A), A left at 6 h. The engine writes A, B, A, out.
        val tally =
            TimeShare.tally(
                listOf(inPlace(0.0, "home"), inPlace(2.0, "sport"), inPlace(3.0, "home"), out(6.0)),
                listOf(home, sport),
                7,
                now,
            )
        assertEquals(5 * hour, tally.trusted["home"])
        assertEquals(1 * hour, tally.trusted["sport"])
    }

    @Test
    fun t8_anUnknownStretch_isNotMeasured_andLeavesTheDenominator() {
        // 168 h, of which an unknown stretch from the 100th to the 130th hour: 138 h measured. Every state lasts under 48 h.
        val facts = mutableListOf<LoggedPresence>()
        var t = 0.0
        var homeNext = true
        while (t < 100.0) {
            facts += if (homeNext) inPlace(t, "home") else out(t)
            homeNext = !homeNext
            t += 10.0
        }
        facts += unknown(100.0)
        facts += out(130.0)
        val tally = TimeShare.tally(facts, listOf(home), 7, now)
        assertEquals(138 * hour, tally.measured)
        assertEquals(30 * hour, tally.notMeasured)
    }

    // --- T9 to T16 ----------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun t9_underSevenDaysOfHistory_isNotEnoughData_evenWithPlentyMeasured() {
        // A journal of 3 days (72 h, all measured): the chart waits for 7 days (decision of the PM, 2026-11-17; it used to be 24 h measured).
        val facts =
            listOf(
                LoggedPresence(now - 72 * hour, PresenceValue.IN_TRUSTED, "home"),
                LoggedPresence(now - 36 * hour, PresenceValue.OUT, null),
            )
        val result = compute(facts, listOf(home))
        assertTrue(result is TimeShareResult.NotEnoughData)
        result as TimeShareResult.NotEnoughData
        assertEquals(72 * hour, result.historyMillis)
        assertTrue(result.measuredMillis > 24 * hour)
    }

    @Test
    fun daysLeft_isSevenMinusTheDaysElapsed_roundedUp_neverUnderOne_andSevenWithNoJournal() {
        val day = TimeShareRules.DAY_MILLIS
        val minute = 60_000L
        assertEquals(7, TimeShare.daysLeft(0))
        assertEquals(7, TimeShare.daysLeft(1))
        assertEquals(7, TimeShare.daysLeft(hour))
        assertEquals(6, TimeShare.daysLeft(day))
        assertEquals(6, TimeShare.daysLeft(day + hour))
        assertEquals(4, TimeShare.daysLeft(3 * day))
        // To the hour: 5 days exactly leaves 2; one minute less leaves 3.
        assertEquals(2, TimeShare.daysLeft(5 * day))
        assertEquals(2, TimeShare.daysLeft(5 * day + minute))
        assertEquals(3, TimeShare.daysLeft(5 * day - minute))
        // Never under 1 while the chart cannot be shown: 6 days and 2 h elapsed is "1 jour".
        assertEquals(1, TimeShare.daysLeft(6 * day))
        assertEquals(1, TimeShare.daysLeft(6 * day + 2 * hour))
        assertEquals(1, TimeShare.daysLeft(7 * day - minute))
        assertEquals(1, TimeShare.daysLeft(7 * day))
        assertEquals(1, TimeShare.daysLeft(30 * day))
    }

    @Test
    fun theNotEnoughDataResult_carriesTheDaysLeft_fromTheFirstFact() {
        // No fact at all: 7.
        assertEquals(7, (compute(emptyList(), listOf(home)) as TimeShareResult.NotEnoughData).daysLeft)
        // First fact 3 days ago: 4.
        val three = listOf(LoggedPresence(now - 3 * TimeShareRules.DAY_MILLIS, PresenceValue.IN_TRUSTED, "home"))
        assertEquals(4, (compute(three, listOf(home)) as TimeShareResult.NotEnoughData).daysLeft)
        // First fact 6 days and 2 h ago: 1.
        val six =
            listOf(LoggedPresence(now - 6 * TimeShareRules.DAY_MILLIS - 2 * hour, PresenceValue.IN_TRUSTED, "home"))
        assertEquals(1, (compute(six, listOf(home)) as TimeShareResult.NotEnoughData).daysLeft)
    }

    @Test
    fun theThresholdIsExactlySevenDaysSinceTheFirstFact() {
        val week = 7 * TimeShareRules.DAY_MILLIS

        fun at(back: Long) =
            listOf(
                LoggedPresence(now - back, PresenceValue.IN_TRUSTED, "home"),
                LoggedPresence(now - back + 10 * hour, PresenceValue.OUT, null),
            )
        assertTrue(compute(at(week), listOf(home)) is TimeShareResult.Ready)
        assertTrue(compute(at(week - 1), listOf(home)) is TimeShareResult.NotEnoughData)
        // The window chosen does not change the rule: 30 days with 8 days of history show the chart.
        assertTrue(compute(at(8 * TimeShareRules.DAY_MILLIS), listOf(home), days = 30) is TimeShareResult.Ready)
    }

    @Test
    fun sevenDaysOfHistory_butNothingMeasuredInTheWindow_isNotEnoughData() {
        // Journal of 10 days, but everything after the first facts is unknown: nothing to share out.
        val facts =
            listOf(
                LoggedPresence(now - 10 * TimeShareRules.DAY_MILLIS, PresenceValue.OUT, null),
                LoggedPresence(now - 10 * TimeShareRules.DAY_MILLIS + hour, PresenceValue.UNKNOWN, null),
            )
        assertTrue(compute(facts, listOf(home)) is TimeShareResult.NotEnoughData)
    }

    @Test
    fun noFactAtAll_isNotEnoughData() {
        assertTrue(compute(emptyList(), listOf(home)) is TimeShareResult.NotEnoughData)
    }

    @Test
    fun t10_sevenPlaces_giveFiveNamed_plusTwoOthers_plusElsewhere() {
        val ids = (1..7).map { PlaceRef("p$it", PlaceType.TRUSTED) }
        val durations = listOf(30.0, 26.0, 22.0, 18.0, 14.0, 10.0, 6.0)
        val facts = mutableListOf<LoggedPresence>()
        var t = 0.0
        ids.forEachIndexed { i, place ->
            facts += inPlace(t, place.id)
            t += durations[i]
        }
        facts += out(t)
        val result = ready(compute(facts, ids))
        assertEquals(
            listOf(
                SliceKind.PLACE,
                SliceKind.PLACE,
                SliceKind.PLACE,
                SliceKind.PLACE,
                SliceKind.PLACE,
                SliceKind.OTHER_PLACES,
                SliceKind.ELSEWHERE,
            ),
            result.slices.map { it.kind },
        )
        assertEquals(2, result.slices.first { it.kind == SliceKind.OTHER_PLACES }.count)
        assertEquals(
            listOf("p1", "p2", "p3", "p4", "p5"),
            result.slices
                .filter {
                    it.kind == SliceKind.PLACE
                }.map { it.placeId },
        )
        assertSumIs100(result)
        // The centre counts the five places AND the others (every trusted place).
        assertEquals(100 - result.percent(SliceKind.ELSEWHERE), result.centerPercent)
    }

    @Test
    fun t11_aDeletedPlace_isNotMeasured_neverElsewhere() {
        // The place "gone" counted 10 h, but it is not in the current list any more.
        val tally =
            TimeShare.tally(
                listOf(inPlace(0.0, "gone"), out(10.0), inPlace(30.0, "home")),
                listOf(home),
                7,
                now,
            )
        assertNull(tally.trusted["gone"])
        // Only the 20 h of "dehors" and what follows are counted: the 10 h of the deleted place are in neither.
        assertEquals(20 * hour, tally.elsewhere)
    }

    @Test
    fun t12_aWindowThatCrossesAChangeOfClock_isExactly168HoursOfElapsedTime() {
        // The night of 28 to 29 March 2026 in Paris is 23 h long. A window of 7 "calendar" days would be 167 h; the window is 168 h of elapsed time.
        val zone = ZoneId.of("Europe/Paris")
        val clock = ZonedDateTime.of(2026, 3, 31, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val start = clock - 168 * hour
        val facts =
            listOf(
                LoggedPresence(start, PresenceValue.IN_TRUSTED, "home"),
                LoggedPresence(start + 48 * hour, PresenceValue.OUT, null),
                LoggedPresence(start + 96 * hour, PresenceValue.IN_TRUSTED, "home"),
                LoggedPresence(start + 144 * hour, PresenceValue.OUT, null),
            )
        val tally = TimeShare.tally(facts, listOf(home), 7, clock)
        assertEquals(168 * hour, tally.measured)
        assertEquals(0L, tally.notMeasured)
    }

    @Test
    fun t13_aShareUnderOnePercent_isTiny_andTheOthersStillMakeOneHundred() {
        // home 40 + 40, out 40 + 47.5, the tiny place 0.5 h: 0.3 %.
        val tiny = PlaceRef("tiny", PlaceType.TRUSTED)
        val facts = listOf(inPlace(0.0, "home"), out(40.0), inPlace(80.0, "home"), out(120.0), inPlace(167.5, "tiny"))
        val result = ready(compute(facts, listOf(home, tiny)))
        val slice = result.slices.first { it.placeId == "tiny" }
        assertTrue(slice.tiny)
        assertEquals(0, slice.percent)
        assertEquals(100, result.slices.filter { it.placeId != "tiny" }.sumOf { it.percent })
        assertSumIs100(result)
    }

    @Test
    fun t14_neverInAPlace_isZeroInTheCentre_andElsewhereIsOneHundred() {
        // 100 h of "dehors", written with the unknown stretches between them that a real journal has.
        val facts = listOf(out(0.0), unknown(48.0), out(48.0), unknown(96.0), out(96.0), unknown(100.0))
        val result = ready(compute(facts, listOf(home)))
        assertEquals(0, result.centerPercent)
        assertEquals(listOf(SliceKind.ELSEWHERE), result.slices.map { it.kind })
        assertEquals(100, result.percent(SliceKind.ELSEWHERE))
    }

    @Test
    fun t15_aSinglePlace_makesTwoSlices() {
        // home 15 + 15 = 30 h, out 35 + 35 = 70 h: 30 % and 70 %.
        val facts = listOf(inPlace(0.0, "home"), out(15.0), inPlace(50.0, "home"), out(65.0), unknown(100.0))
        val result = ready(compute(facts, listOf(home)))
        assertEquals(listOf(SliceKind.PLACE, SliceKind.ELSEWHERE), result.slices.map { it.kind })
        assertEquals(30, result.percent(SliceKind.PLACE))
        assertEquals(70, result.percent(SliceKind.ELSEWHERE))
        assertEquals(30, result.centerPercent)
    }

    @Test
    fun t16_aFactInTheFuture_isBroughtBackToNow() {
        val facts = listOf(out(0.0), LoggedPresence(now + 5 * hour, PresenceValue.IN_TRUSTED, "home"))
        val tally = TimeShare.tally(facts, listOf(home), 7, now)
        assertNull(tally.trusted["home"])
        // "Dehors" is counted up to now, with the cap of 48 h.
        assertEquals(48 * hour, tally.elsewhere)
    }

    // --- T17 to T23: the danger zone and the trip ----------------------------------------------------------------------------------------------------

    @Test
    fun t17_aTrustedZoneThatEnclosesADangerVisit_leavesNoDangerTime() {
        // What the engine writes (the trusted zone wins, so entering and leaving the danger zone inside it changes no presence): in A at 0 h, out at 6 h.
        val tally = TimeShare.tally(listOf(inPlace(0.0, "home"), out(6.0)), listOf(home, risk1), 7, now)
        assertEquals(6 * hour, tally.trusted["home"])
        assertTrue(tally.danger.isEmpty())
    }

    @Test
    fun t18_aDangerZoneAlone_isCounted() {
        val tally = TimeShare.tally(listOf(inRisk(1.0, "risk1"), out(3.0)), listOf(risk1), 7, now)
        assertEquals(2 * hour, tally.danger["risk1"])
    }

    @Test
    fun t19_twoDangerZones_makeOneLineOf12_withADetailThatAddsUpToIt() {
        // 14 days (336 h): 200 h measured. Maison 100 h (50 %), danger 10 h + 14 h (5 % and 7 %), elsewhere 76 h (38 %).
        val journal =
            listOf(
                LoggedPresence(now - 336 * hour, PresenceValue.IN_TRUSTED, "home"),
                LoggedPresence(now - 336 * hour + 40 * hour, PresenceValue.OUT, null),
                LoggedPresence(now - 336 * hour + 70 * hour, PresenceValue.IN_TRUSTED, "home"),
                LoggedPresence(now - 336 * hour + 110 * hour, PresenceValue.OUT, null),
                LoggedPresence(now - 336 * hour + 136 * hour, PresenceValue.IN_RISK, "risk1"),
                LoggedPresence(now - 336 * hour + 146 * hour, PresenceValue.OUT, null),
                LoggedPresence(now - 336 * hour + 166 * hour, PresenceValue.IN_RISK, "risk2"),
                LoggedPresence(now - 336 * hour + 180 * hour, PresenceValue.IN_TRUSTED, "home"),
                LoggedPresence(now - 336 * hour + 200 * hour, PresenceValue.UNKNOWN, null),
            )
        val result = ready(compute(journal, listOf(home, risk1, risk2), days = 14))
        val danger = result.slices.first { it.kind == SliceKind.DANGER }
        assertEquals(2, danger.count)
        assertEquals(12, danger.percent)
        // The longer zone first; the detail adds up to the line.
        assertEquals(listOf("risk2", "risk1"), danger.details.map { it.placeId })
        assertEquals(listOf(7, 5), danger.details.map { it.percent })
        assertEquals(danger.percent, danger.details.sumOf { it.percent })
        assertEquals(50, result.centerPercent)
        assertSumIs100(result)
    }

    @Test
    fun t20_noDangerLine_whenThereIsNoDangerZone_orNoTimeInIt() {
        val noZone = ready(compute(listOf(inPlace(0.0, "home"), out(30.0), unknown(60.0)), listOf(home)))
        assertTrue(noZone.slices.none { it.kind == SliceKind.DANGER })
        // A danger zone that exists, where no time was spent: still no line (never "0 %").
        val noTime = ready(compute(listOf(inPlace(0.0, "home"), out(30.0), unknown(60.0)), listOf(home, risk1)))
        assertTrue(noTime.slices.none { it.kind == SliceKind.DANGER })
    }

    @Test
    fun t22_aPlaceWhoseTypeChanged_isNotMeasuredAnyMore() {
        // The fact says "in a trusted place", the place is a danger zone now: neither a trusted time nor a danger time.
        val tally =
            TimeShare.tally(
                listOf(inPlace(0.0, "home"), out(20.0)),
                listOf(PlaceRef("home", PlaceType.RISK)),
                7,
                now,
            )
        assertTrue(tally.trusted.isEmpty())
        assertTrue(tally.danger.isEmpty())
        // The 20 h are in no category; "dehors" (from the 20th hour, up to the cap of 48 h) is the only time counted.
        assertEquals(48 * hour, tally.elsewhere)
        // And the other way round.
        val reverse =
            TimeShare.tally(
                listOf(inRisk(0.0, "risk1"), out(20.0)),
                listOf(PlaceRef("risk1", PlaceType.TRUSTED)),
                7,
                now,
            )
        assertTrue(reverse.danger.isEmpty() && reverse.trusted.isEmpty())
    }

    @Test
    fun t23_aTripOf60Hours_isNotMeasured() {
        // The trip begins at 40 h (unknown) and ends at 100 h: 60 h that are neither a place nor "Ailleurs".
        val facts = listOf(inPlace(0.0, "home"), unknown(40.0), out(100.0), unknown(148.0))
        val tally = TimeShare.tally(facts, listOf(home), 7, now)
        assertEquals(40 * hour, tally.trusted["home"])
        assertEquals(48 * hour, tally.elsewhere)
        assertEquals(88 * hour, tally.measured)
    }

    @Test
    fun aTripFactIsAnUnknown() {
        val tally =
            TimeShare.tally(
                listOf(inPlace(0.0, "home"), LoggedPresence(at(10.0), PresenceValue.TRAVEL, null), out(70.0)),
                listOf(home),
                7,
                now,
            )
        assertEquals(10 * hour, tally.trusted["home"])
        assertEquals(48 * hour, tally.elsewhere)
    }

    // --- TS-10, TS-11 and the windows ---------------------------------------------------------------------------------------------------------------

    @Test
    fun theWindowsAre7_14And30_andAnythingElseIs7() {
        assertEquals(listOf(7, 14, 30), TimeShareRules.WINDOWS_DAYS)
        assertEquals(7, TimeShareRules.windowOrDefault(null))
        assertEquals(7, TimeShareRules.windowOrDefault(9))
        assertEquals(30, TimeShareRules.windowOrDefault(30))
    }

    @Test
    fun aLongerWindow_seesFactsThatAShorterOneDoesNot() {
        val facts =
            listOf(
                LoggedPresence(now - 10 * TimeShareRules.DAY_MILLIS, PresenceValue.IN_TRUSTED, "home"),
                LoggedPresence(now - 10 * TimeShareRules.DAY_MILLIS + 40 * hour, PresenceValue.OUT, null),
                LoggedPresence(now - 10 * TimeShareRules.DAY_MILLIS + 60 * hour, PresenceValue.UNKNOWN, null),
            )
        assertTrue(compute(facts, listOf(home), days = 7) is TimeShareResult.NotEnoughData)
        assertTrue(compute(facts, listOf(home), days = 14) is TimeShareResult.Ready)
    }

    @Test
    fun theSumIsAlways100_noTimeIsNegative_andADetailAddsUpToItsLine_whateverTheJournal() {
        val random = Random(20261117)
        val places =
            listOf(
                home,
                sport,
                office,
                risk1,
                risk2,
                PlaceRef("p4", PlaceType.TRUSTED),
                PlaceRef("p5", PlaceType.TRUSTED),
                PlaceRef("p6", PlaceType.TRUSTED),
            )
        repeat(400) {
            val facts =
                (0 until random.nextInt(2, 40)).map {
                    val t = window7 + random.nextLong(-2 * TimeShareRules.DAY_MILLIS, 8 * TimeShareRules.DAY_MILLIS)
                    val place = places.random(random)
                    when (random.nextInt(4)) {
                        0 -> LoggedPresence(t, PresenceValue.OUT, null)
                        1 -> LoggedPresence(t, PresenceValue.UNKNOWN, null)
                        2 -> LoggedPresence(t, PresenceValue.IN_TRUSTED, place.id)
                        else -> LoggedPresence(t, PresenceValue.IN_RISK, place.id)
                    }
                }
            val tally = TimeShare.tally(facts, places, 7, now)
            assertTrue(
                tally.trusted.values.all { it > 0 } && tally.danger.values.all { it > 0 } && tally.elsewhere >= 0,
            )
            assertTrue("never more than the window", tally.measured <= 7 * TimeShareRules.DAY_MILLIS)
            val result = TimeShare.compute(facts, places, 7, now)
            if (result is TimeShareResult.Ready) {
                assertSumIs100(result)
                result.slices.filter { it.kind == SliceKind.DANGER }.forEach { line ->
                    assertEquals(line.percent, line.details.sumOf { it.percent })
                }
                assertTrue(result.slices.all { it.percent in 0..100 })
                assertFalse(result.slices.any { it.millis <= 0 })
            }
        }
    }

    @Test
    fun theUnmeasuredTime_isNeverElsewhere() {
        // TS-11: an unknown stretch does not appear as "Ailleurs".
        val facts = listOf(inPlace(0.0, "home"), unknown(30.0), inPlace(90.0, "home"), unknown(120.0))
        val result = ready(compute(facts, listOf(home)))
        assertTrue(result.slices.none { it.kind == SliceKind.ELSEWHERE })
        assertEquals(100, result.percent(SliceKind.PLACE))
    }
}
