package com.tomessa.miff.ui.notification

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.domain.feed.PlaceKind
import com.tomessa.miff.domain.notification.LastVisit
import com.tomessa.miff.domain.notification.VisitKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset

private const val NBSP = " "

/**
 * The sentences of "Depuis ta dernière visite" in French (decision Q1 of the PM: option A for the silence; no gender agreement in any line; a fact and an hour, never a duration).
 * Friday 15 January 2027, 08:00 UTC, in UTC.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "fr")
class LastVisitTextsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val texts get() = LastVisitTexts(context, ZoneOffset.UTC)
    private val now = Instant.parse("2027-01-15T08:00:00Z").toEpochMilli()

    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun line(
        kind: VisitKind,
        place: PlaceKind? = null,
        shownAt: String = "2027-01-15T07:10:00Z",
    ) = LastVisit.Parsed("uid", kind, place, at(shownAt))

    private fun h(
        hh: String,
        mm: String,
    ) = "$hh${NBSP}h$NBSP$mm"

    @Test
    fun aDeparture_isTheExampleOfTheTicket() {
        assertEquals(
            "Thomas a quitté l'école à ${h("07", "10")}",
            texts.sentence(line(VisitKind.DEPARTURE, PlaceKind.SCHOOL), "Thomas", now),
        )
    }

    @Test
    fun anArrival_hasNoGenderAgreement() {
        assertEquals(
            "Thomas a rejoint l'école à ${h("07", "10")}",
            texts.sentence(line(VisitKind.ARRIVAL, PlaceKind.SCHOOL), "Thomas", now),
        )
        assertEquals(
            "Amandine a rejoint la maison à ${h("07", "10")}",
            texts.sentence(line(VisitKind.ARRIVAL, PlaceKind.HOME), "Amandine", now),
        )
    }

    @Test
    fun aPlaceWithoutACategory_isATrustedPlace_neverItsName_decisionQ5() {
        assertEquals(
            "Thomas a quitté un lieu de confiance à ${h("07", "10")}",
            texts.sentence(line(VisitKind.DEPARTURE, null), "Thomas", now),
        )
    }

    @Test
    fun anUpdate_isAFact() {
        assertEquals(
            "Amandine a donné de ses nouvelles à ${h("07", "05")}",
            texts.sentence(line(VisitKind.UPDATE, shownAt = "2027-01-15T07:05:00Z"), "Amandine", now),
        )
    }

    @Test
    fun theSilence_isOptionA_aFactWithoutAVerdict_andTheElisionIsRight() {
        val silence = line(VisitKind.SILENCE, shownAt = "2027-01-14T13:40:00Z")
        assertEquals("Dernière nouvelle d'Amandine : hier à ${h("13", "40")}", texts.sentence(silence, "Amandine", now))
        assertEquals("Dernière nouvelle de Thomas : hier à ${h("13", "40")}", texts.sentence(silence, "Thomas", now))
        val today = line(VisitKind.SILENCE, shownAt = "2027-01-15T03:40:00Z")
        assertEquals("Dernière nouvelle d'Amandine : ${h("03", "40")}", texts.sentence(today, "Amandine", now))
        assertEquals("Dernière nouvelle d'Élodie : ${h("03", "40")}", texts.sentence(today, "Élodie", now))
        assertEquals("Dernière nouvelle de Léa : ${h("03", "40")}", texts.sentence(today, "Léa", now))
        // Nothing of the vocabulary of an alarm, no duration, no "pas de nouvelles".
        val all = listOf(texts.sentence(today, "Amandine", now), texts.sentence(silence, "Thomas", now))
        all.forEach { sentence ->
            listOf("pas de", "depuis", "inquiét", "toujours", "urgent", "!").forEach {
                assertFalse("$sentence / $it", sentence.contains(it, ignoreCase = true))
            }
        }
    }

    @Test
    fun theDay_isToday_yesterday_theWeekday_orTheDate() {
        fun departure(iso: String) = texts.sentence(line(VisitKind.DEPARTURE, PlaceKind.WORK, iso), "Thomas", now)
        assertEquals("Thomas a quitté le travail à ${h("07", "10")}", departure("2027-01-15T07:10:00Z"))
        assertEquals("Thomas a quitté le travail hier à ${h("22", "15")}", departure("2027-01-14T22:15:00Z"))
        // Friday 15: three days back is Tuesday 12.
        assertEquals("Thomas a quitté le travail mardi à ${h("09", "30")}", departure("2027-01-12T09:30:00Z"))
        // A week back or more: the short date of the Feed (a weekday would be ambiguous).
        assertEquals("Thomas a quitté le travail le 3 janv. à ${h("09", "30")}", departure("2027-01-03T09:30:00Z"))
    }

    @Test
    fun aNameNotKnownYet_isAnyoneNotABlank() {
        assertEquals(
            "Un proche a quitté l'école à ${h("07", "10")}",
            texts.sentence(line(VisitKind.DEPARTURE, PlaceKind.SCHOOL), null, now),
        )
        assertEquals(
            "Un proche a quitté l'école à ${h("07", "10")}",
            texts.sentence(line(VisitKind.DEPARTURE, PlaceKind.SCHOOL), "  ", now),
        )
        assertEquals(
            "Dernière nouvelle d'un proche : ${h("07", "10")}",
            texts.sentence(line(VisitKind.SILENCE), null, now),
        )
    }

    @Test
    fun noSentenceHoldsAGenderAgreement() {
        // The four lines for a person of either gender are the same words: none of the participles agrees.
        val all = VisitKind.entries.map { texts.sentence(line(it, PlaceKind.HOME), "Camille", now) }
        all.forEach { assertTrue(it, !it.contains("arrivé") && !it.contains("parti") && !it.contains("(e)")) }
    }
}

/** The same lines in English (the fallback language). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en")
class LastVisitTextsEnglishTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val texts get() = LastVisitTexts(context, ZoneOffset.UTC)
    private val now = Instant.parse("2027-01-15T08:00:00Z").toEpochMilli()

    private fun line(
        kind: VisitKind,
        place: PlaceKind? = null,
        shownAt: String = "2027-01-15T07:10:00Z",
    ) = LastVisit.Parsed("uid", kind, place, Instant.parse(shownAt).toEpochMilli())

    @Test
    fun theFourLines() {
        assertTrue(
            texts
                .sentence(
                    line(VisitKind.ARRIVAL, PlaceKind.SCHOOL),
                    "Thomas",
                    now,
                ).startsWith("Thomas arrived at school at 7:10"),
        )
        assertTrue(
            texts
                .sentence(
                    line(VisitKind.DEPARTURE, PlaceKind.SCHOOL),
                    "Thomas",
                    now,
                ).startsWith("Thomas left school at 7:10"),
        )
        assertTrue(
            texts
                .sentence(
                    line(VisitKind.UPDATE, shownAt = "2027-01-15T07:05:00Z"),
                    "Amandine",
                    now,
                ).startsWith("Amandine shared an update at 7:05"),
        )
        assertTrue(
            texts
                .sentence(
                    line(VisitKind.SILENCE, shownAt = "2027-01-15T03:40:00Z"),
                    "Amandine",
                    now,
                ).startsWith("Amandine's latest news: 3:40"),
        )
    }

    @Test
    fun theDay_andAnUnknownName() {
        assertTrue(
            texts
                .sentence(
                    line(VisitKind.DEPARTURE, PlaceKind.HOME, "2027-01-14T22:15:00Z"),
                    "Thomas",
                    now,
                ).startsWith("Thomas left home yesterday at 10:15"),
        )
        assertTrue(
            texts
                .sentence(
                    line(VisitKind.DEPARTURE, PlaceKind.HOME, "2027-01-12T09:30:00Z"),
                    "Thomas",
                    now,
                ).startsWith("Thomas left home on Tuesday at 9:30"),
        )
        assertTrue(
            texts
                .sentence(
                    line(VisitKind.SILENCE, shownAt = "2027-01-12T09:30:00Z"),
                    "Thomas",
                    now,
                ).startsWith("Thomas's latest news: Tuesday at 9:30"),
        )
        assertTrue(
            texts
                .sentence(
                    line(VisitKind.ARRIVAL, null),
                    null,
                    now,
                ).startsWith("Someone arrived at a trusted place at 7:10"),
        )
        assertTrue(texts.sentence(line(VisitKind.SILENCE), null, now).startsWith("Someone's latest news: 7:10"))
    }
}
