package com.tomessa.miff.ui.feed

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.tomessa.miff.domain.feed.Age
import com.tomessa.miff.domain.feed.AlertKind
import com.tomessa.miff.domain.feed.AlertPage
import com.tomessa.miff.domain.feed.Battery
import com.tomessa.miff.domain.feed.Day
import com.tomessa.miff.domain.feed.Moment
import com.tomessa.miff.domain.feed.PlaceKind
import com.tomessa.miff.domain.feed.PlaceRef
import com.tomessa.miff.domain.feed.StateLabel
import com.tomessa.miff.domain.feed.TimeLine
import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffRole
import com.tomessa.miff.domain.tracking.StatusLevel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset
import java.util.TimeZone

/** The times are written with non-breaking spaces ('16 h 02', '4:40 PM'): the expected sentences are given with plain ones. */
fun String.t(): String =
    replace(Regex("(\\d) h (\\d)"), "$1\u00A0h\u00A0$2").replace(Regex("(\\d) (AM|PM)"), "$1\u00A0$2")

private val AT_16_02 = Instant.parse("2026-11-03T16:02:00Z").toEpochMilli()
private val AT_16_40 = Instant.parse("2026-11-05T16:40:00Z").toEpochMilli()

private fun member(
    first: String = "Thomas",
    phone: String? = "+33612345678",
) = MiffMember("t", first, "X", null, MiffRole.MEMBER, true, phone)

private fun page(
    kind: AlertKind,
    isMe: Boolean = false,
    level: StatusLevel = StatusLevel.ORANGE,
) = AlertPage(member(), isMe, level, kind, canMessage = !isMe, offlineSince = null)

/** The sentences of the Feed in French (ticket 7): the times follow the language ("16 h 02".t()), the places read well in a sentence. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "fr-w360dp-h800dp-port-xxhdpi")
class FeedTextsFrenchTest {
    private val texts = FeedTexts(ApplicationProvider.getApplicationContext<Context>(), ZoneOffset.UTC)

    @Test
    fun theThreeFamiliesOfMoments_inTheThreeCases() {
        assertEquals("à 16 h 40".t(), texts.at(Moment(AT_16_40, Day.TODAY)))
        assertEquals("hier à 16 h 40".t(), texts.at(Moment(AT_16_40, Day.YESTERDAY)))
        assertEquals("le 3 nov. à 16 h 02".t(), texts.at(Moment(AT_16_02, Day.OTHER)))
        assertEquals("16 h 40".t(), texts.since(Moment(AT_16_40, Day.TODAY)))
        assertEquals("hier à 16 h 40".t(), texts.since(Moment(AT_16_40, Day.YESTERDAY)))
        assertEquals("de 16 h 40".t(), texts.from(Moment(AT_16_40, Day.TODAY)))
        assertEquals("d'hier à 16 h 40".t(), texts.from(Moment(AT_16_40, Day.YESTERDAY)))
        assertEquals("du 3 nov. à 16 h 02".t(), texts.from(Moment(AT_16_02, Day.OTHER)))
    }

    @Test
    fun theZoneOfThePhone_isReadAtEachUse_notFrozenAtTheConstruction() {
        val live = FeedTexts(ApplicationProvider.getApplicationContext<Context>())
        val saved = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertEquals("16 h 40".t(), live.since(Moment(AT_16_40, Day.TODAY)))
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            assertEquals("01 h 40".t(), live.since(Moment(AT_16_40, Day.TODAY)))
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    @Test
    fun labels_readLikeTheMockups() {
        assertEquals("À la maison", texts.label(StateLabel.At(PlaceRef.Category(PlaceKind.HOME))))
        assertEquals("Au travail", texts.label(StateLabel.At(PlaceRef.Category(PlaceKind.WORK))))
        assertEquals("À l'école", texts.label(StateLabel.At(PlaceRef.Category(PlaceKind.SCHOOL))))
        assertEquals("Au sport", texts.label(StateLabel.At(PlaceRef.Category(PlaceKind.SPORT))))
        // Chez un proche has its own words; Loisir and Autre show the NAME of the place alone (decision of the PM, 2026-11-08).
        assertEquals("Chez un proche", texts.label(StateLabel.At(PlaceRef.Category(PlaceKind.RELATIVE))))
        assertEquals(
            "A quitté le domicile d'un proche",
            texts.label(StateLabel.Left(PlaceRef.Category(PlaceKind.RELATIVE))),
        )
        assertEquals("Cinéma Gaumont", texts.label(StateLabel.At(PlaceRef.Named("Cinéma Gaumont"))))
        assertEquals("A quitté le travail", texts.label(StateLabel.Left(PlaceRef.Category(PlaceKind.WORK))))
        assertEquals("A quitté Chez Léa", texts.label(StateLabel.Left(PlaceRef.Named("Chez Léa"))))
        assertEquals("Dans un lieu de confiance", texts.label(StateLabel.At(PlaceRef.Unknown)))
        assertEquals("A quitté un lieu de confiance", texts.label(StateLabel.Left(PlaceRef.Unknown)))
        assertEquals("Dans une zone de danger", texts.label(StateLabel.Danger))
        assertEquals("Pas encore de nouvelles", texts.label(StateLabel.NoNews))
        assertEquals("Partage arrêté", texts.label(StateLabel.SharingStopped))
        assertEquals("Nouvelles masquées", texts.label(StateLabel.NewsHidden))
    }

    @Test
    fun timeLines() {
        // Always a TIME (decision of the PM, 2026-11-10): today, yesterday, the short date.
        assertEquals("Depuis 16 h 40".t(), texts.timeLine(TimeLine.Since(Moment(AT_16_40, Day.TODAY))))
        assertEquals("Depuis hier à 16 h 40".t(), texts.timeLine(TimeLine.Since(Moment(AT_16_40, Day.YESTERDAY))))
        assertEquals("Depuis le 3 nov. à 16 h 02".t(), texts.timeLine(TimeLine.Since(Moment(AT_16_02, Day.OTHER))))
        assertEquals(
            "Dernière nouvelle à 16 h 40".t(),
            texts.timeLine(TimeLine.LatestNews(Moment(AT_16_40, Day.TODAY))),
        )
        assertEquals(
            "Pas de nouvelles depuis 16 h 40".t(),
            texts.timeLine(TimeLine.NoNewsSince(Moment(AT_16_40, Day.TODAY))),
        )
        assertEquals(null, texts.timeLine(TimeLine.None))
    }

    @Test
    fun battery() {
        // The percentage alone: no time of measure, on the chip nor in the sentence of the screen reader (decision of the PM, 2026-11-07).
        assertEquals("85 %", texts.battery(Battery(85)))
        assertEquals("12 %", texts.battery(Battery(12)))
        assertEquals("Batterie à 85 %", texts.batteryA11y(Battery(85)))
        assertEquals("Batterie à 12 %", texts.batteryA11y(Battery(12)))
    }

    @Test
    fun latestNews() {
        assertEquals("Dernière nouvelle il y a 2 min", texts.latestNews(Age.Minutes(2)))
        assertEquals("Dernière nouvelle il y a 3 h", texts.latestNews(Age.Hours(3)))
        assertEquals("Dernière nouvelle hier à 16 h 40".t(), texts.latestNews(Age.Yesterday(AT_16_40)))
    }

    @Test
    fun alertSentences_areTheOnesOfThePmAndTheTicket() {
        val left = AlertKind.Left(PlaceRef.Category(PlaceKind.HOME), Moment(AT_16_40, Day.TODAY))
        assertEquals("Thomas a quitté la maison à 16 h 40".t(), texts.alertTitle(page(left)))
        assertEquals("Tu as quitté la maison à 16 h 40".t(), texts.alertTitle(page(left, isMe = true)))
        assertEquals(
            "Thomas a quitté un lieu de confiance à 16 h 40".t(),
            texts.alertTitle(page(AlertKind.Left(PlaceRef.Unknown, Moment(AT_16_40, Day.TODAY)))),
        )
        val stale = AlertKind.NoNewsSince(Moment(AT_16_02, Day.TODAY))
        assertEquals(
            "Pas de nouvelles de Thomas depuis 16 h 02".t(),
            texts.alertTitle(page(stale, level = StatusLevel.RED)),
        )
        assertEquals(
            "Tes proches n'ont pas eu de tes nouvelles depuis 16 h 02".t(),
            texts.alertTitle(page(stale, isMe = true, level = StatusLevel.RED)),
        )
        val danger = AlertKind.Danger(PlaceRef.Named("Zone chantier"), Moment(AT_16_02, Day.TODAY))
        assertEquals("Thomas est à Zone chantier", texts.alertTitle(page(danger, level = StatusLevel.RED)))
        assertEquals(
            "Tu es dans une zone de danger de ta Miff",
            texts.alertTitle(page(danger, isMe = true, level = StatusLevel.RED)),
        )
        // Without a name for the zone, the wording of the PM for "danger" is the fallback.
        assertEquals(
            "Thomas est dans une zone de danger",
            texts.alertTitle(
                page(AlertKind.Danger(PlaceRef.Unknown, Moment(AT_16_02, Day.TODAY)), level = StatusLevel.RED),
            ),
        )
        assertEquals("Depuis 16 h 02".t(), texts.alertDetail(page(danger, level = StatusLevel.RED)))
        assertEquals(null, texts.alertDetail(page(left)))
    }
}

/** The same in English: 12-hour clock in English ("4:40 PM".t()), "left home", "No news from Thomas since 4:02 PM".t(). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w360dp-h800dp-port-xxhdpi")
class FeedTextsEnglishTest {
    private val texts = FeedTexts(ApplicationProvider.getApplicationContext<Context>(), ZoneOffset.UTC)

    @Test
    fun moments() {
        assertEquals("at 4:40 PM".t(), texts.at(Moment(AT_16_40, Day.TODAY)))
        assertEquals("yesterday at 4:40 PM".t(), texts.at(Moment(AT_16_40, Day.YESTERDAY)))
        assertEquals("on Nov 3 at 4:02 PM".t(), texts.at(Moment(AT_16_02, Day.OTHER)))
        assertEquals("4:40 PM".t(), texts.since(Moment(AT_16_40, Day.TODAY)))
        assertEquals("from 4:40 PM".t(), texts.from(Moment(AT_16_40, Day.TODAY)))
    }

    @Test
    fun theChoiceOf12Or24Hours_isReadAtEachUse() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val live = FeedTexts(context, ZoneOffset.UTC)
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "12")
        assertEquals("4:40 PM".t(), live.since(Moment(AT_16_40, Day.TODAY)))
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
        assertEquals("16:40", live.since(Moment(AT_16_40, Day.TODAY)))
    }

    @Test
    fun theSportPlaceReadsWellInASentence() {
        assertEquals("At the gym", texts.label(StateLabel.At(PlaceRef.Category(PlaceKind.SPORT))))
        assertEquals("Left the gym", texts.label(StateLabel.Left(PlaceRef.Category(PlaceKind.SPORT))))
    }

    @Test
    fun labelsFollowTheCategoryOfThePlace_decisionOfThePm_2026_11_08() {
        assertEquals("At home", texts.label(StateLabel.At(PlaceRef.Category(PlaceKind.HOME))))
        assertEquals("At work", texts.label(StateLabel.At(PlaceRef.Category(PlaceKind.WORK))))
        assertEquals("At school", texts.label(StateLabel.At(PlaceRef.Category(PlaceKind.SCHOOL))))
        assertEquals("At the gym", texts.label(StateLabel.At(PlaceRef.Category(PlaceKind.SPORT))))
        assertEquals("At a loved one's", texts.label(StateLabel.At(PlaceRef.Category(PlaceKind.RELATIVE))))
        assertEquals("Left a loved one's place", texts.label(StateLabel.Left(PlaceRef.Category(PlaceKind.RELATIVE))))
        // Leisure, Other, no category: the NAME of the place alone.
        assertEquals("Cinéma Gaumont", texts.label(StateLabel.At(PlaceRef.Named("Cinéma Gaumont"))))
        assertEquals("In a trusted place", texts.label(StateLabel.At(PlaceRef.Unknown)))
    }

    @Test
    fun alertSentences() {
        val left = AlertKind.Left(PlaceRef.Category(PlaceKind.HOME), Moment(AT_16_40, Day.TODAY))
        assertEquals("Thomas left home at 4:40 PM".t(), texts.alertTitle(page(left)))
        assertEquals("You left home at 4:40 PM".t(), texts.alertTitle(page(left, isMe = true)))
        val stale = AlertKind.NoNewsSince(Moment(AT_16_02, Day.TODAY))
        assertEquals("No news from Thomas since 4:02 PM".t(), texts.alertTitle(page(stale, level = StatusLevel.RED)))
        val danger = AlertKind.Danger(PlaceRef.Named("Zone chantier"), Moment(AT_16_02, Day.TODAY))
        assertEquals("Thomas is at Zone chantier", texts.alertTitle(page(danger, level = StatusLevel.RED)))
        assertEquals(
            "You're in a danger zone of your Miff",
            texts.alertTitle(page(danger, isMe = true, level = StatusLevel.RED)),
        )
    }

    @Test
    fun labelsAndTimes() {
        assertEquals("At home", texts.label(StateLabel.At(PlaceRef.Category(PlaceKind.HOME))))
        assertEquals("Left home", texts.label(StateLabel.Left(PlaceRef.Category(PlaceKind.HOME))))
        assertEquals("In a danger zone", texts.label(StateLabel.Danger))
        assertEquals("Since 4:40 PM".t(), texts.timeLine(TimeLine.Since(Moment(AT_16_40, Day.TODAY))))
        assertEquals("Since yesterday at 4:40 PM".t(), texts.timeLine(TimeLine.Since(Moment(AT_16_40, Day.YESTERDAY))))
        assertEquals("Latest news at 4:40 PM".t(), texts.timeLine(TimeLine.LatestNews(Moment(AT_16_40, Day.TODAY))))
        assertEquals("No news since 4:02 PM".t(), texts.timeLine(TimeLine.NoNewsSince(Moment(AT_16_02, Day.TODAY))))
        assertEquals("Latest news 2 min ago", texts.latestNews(Age.Minutes(2)))
        assertEquals("85 %", texts.battery(Battery(85)))
        assertEquals("Battery at 85 %", texts.batteryA11y(Battery(85)))
    }
}
