package com.tomessa.miff.ui.feed

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.tomessa.miff.R
import com.tomessa.miff.domain.feed.Age
import com.tomessa.miff.domain.feed.AlertKind
import com.tomessa.miff.domain.feed.AlertPage
import com.tomessa.miff.domain.feed.Battery
import com.tomessa.miff.domain.feed.Bubble
import com.tomessa.miff.domain.feed.Day
import com.tomessa.miff.domain.feed.Moment
import com.tomessa.miff.domain.feed.PlaceKind
import com.tomessa.miff.domain.feed.PlaceRef
import com.tomessa.miff.domain.feed.StateLabel
import com.tomessa.miff.domain.feed.TimeLine
import com.tomessa.miff.domain.feed.TravelNote
import com.tomessa.miff.domain.travel.TravelDay
import com.tomessa.miff.ui.profile.TravelDayFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val NBSP = '\u00A0'

/*
 * The sentences of the Feed: what [com.tomessa.miff.domain.feed.FeedBuilder] decided (facts), written in the language of the phone from the
 * texts `feed_*` (French and English, the same keys on iOS). Times follow the language and the 12 h / 24 h choice of the phone ("16 h 02" in
 * French; "4:02 PM" or "16:02" in English). A sentence is a whole key: nothing is assembled from pieces except a time, a number or a name.
 * Nothing here is logged; the names it is given are shown, never kept.
 */
class FeedTexts(
    private val context: Context,
    /** For the tests only: null = the zone of the phone, read at EACH use (a change of zone or of language is followed at once). */
    private val zoneOverride: ZoneId? = null,
) {
    private val zone: ZoneId get() = zoneOverride ?: ZoneId.systemDefault()
    private val locale: Locale get() = context.resources.configuration.locales[0]

    // Read at each use, not once at the construction: the phone's 12 h / 24 h choice can change while the screen is on.
    private val is24Hour: Boolean get() = DateFormat.is24HourFormat(context)

    private fun s(
        id: Int,
        vararg args: Any,
    ): String = context.getString(id, *args)

    private fun clock(millis: Long): String {
        val pattern = s(if (is24Hour) R.string.feed_time_pattern_24 else R.string.feed_time_pattern_12)
        // Non-breaking spaces: "16 h 02" and "4:40 PM" never break across two lines of a headline.
        return DateTimeFormatter
            .ofPattern(pattern, locale)
            .format(Instant.ofEpochMilli(millis).atZone(zone))
            .replace(' ', NBSP)
    }

    private fun date(millis: Long): String =
        DateTimeFormatter
            .ofPattern(
                s(R.string.feed_date_pattern),
                locale,
            ).format(Instant.ofEpochMilli(millis).atZone(zone))

    /** "à 16 h 02", "hier à 16 h 02", "le 3 nov. à 16 h 02". */
    fun at(moment: Moment): String =
        when (moment.day) {
            Day.TODAY -> s(R.string.feed_moment_at_today, clock(moment.atMillis))
            Day.YESTERDAY -> s(R.string.feed_moment_at_yesterday, clock(moment.atMillis))
            Day.OTHER -> s(R.string.feed_moment_at_date, date(moment.atMillis), clock(moment.atMillis))
        }

    /** "16 h 02" (after "depuis"), "hier à 16 h 02", "le 3 nov. à 16 h 02". */
    fun since(moment: Moment): String =
        when (moment.day) {
            Day.TODAY -> s(R.string.feed_moment_since_today, clock(moment.atMillis))
            Day.YESTERDAY -> s(R.string.feed_moment_since_yesterday, clock(moment.atMillis))
            Day.OTHER -> s(R.string.feed_moment_since_date, date(moment.atMillis), clock(moment.atMillis))
        }

    /** "de 16 h 02", "d'hier à 16 h 02", "du 3 nov. à 16 h 02". */
    fun from(moment: Moment): String =
        when (moment.day) {
            Day.TODAY -> s(R.string.feed_moment_from_today, clock(moment.atMillis))
            Day.YESTERDAY -> s(R.string.feed_moment_from_yesterday, clock(moment.atMillis))
            Day.OTHER -> s(R.string.feed_moment_from_date, date(moment.atMillis), clock(moment.atMillis))
        }

    private fun momentOf(age: Age): Moment? =
        when (age) {
            is Age.Yesterday -> Moment(age.atMillis, Day.YESTERDAY)
            is Age.Date -> Moment(age.atMillis, Day.OTHER)
            else -> null
        }

    /** "il y a 20 min" / "il y a 3 h": the sheet "Localiser" only (a position of less than a day). */
    fun ago(age: Age): String =
        when (age) {
            is Age.Minutes -> s(R.string.feed_ago_minutes, age.minutes)
            is Age.Hours -> s(R.string.feed_ago_hours, age.hours)
            else -> momentOf(age)?.let { at(it) }.orEmpty()
        }

    /** The place in the middle of a sentence ("A quitté la maison", "Thomas a quitté le travail à 16 h 40"). */
    fun placeNoun(place: PlaceRef): String =
        when (place) {
            is PlaceRef.Category -> {
                s(
                    when (place.kind) {
                        PlaceKind.HOME -> R.string.feed_place_noun_home
                        PlaceKind.WORK -> R.string.feed_place_noun_work
                        PlaceKind.SCHOOL -> R.string.feed_place_noun_school
                        PlaceKind.SPORT -> R.string.feed_place_noun_sport
                        PlaceKind.RELATIVE -> R.string.feed_place_noun_relative
                    },
                )
            }

            is PlaceRef.Named -> {
                place.name
            }

            PlaceRef.Unknown -> {
                s(R.string.feed_place_noun_generic)
            }
        }

    fun label(label: StateLabel): String =
        when (label) {
            is StateLabel.At -> {
                when (val place = label.place) {
                    is PlaceRef.Category -> {
                        s(
                            when (place.kind) {
                                PlaceKind.HOME -> R.string.feed_label_at_home
                                PlaceKind.WORK -> R.string.feed_label_at_work
                                PlaceKind.SCHOOL -> R.string.feed_label_at_school
                                PlaceKind.SPORT -> R.string.feed_label_at_sport
                                PlaceKind.RELATIVE -> R.string.feed_label_at_relative
                            },
                        )
                    }

                    is PlaceRef.Named -> {
                        // Loisir, Autre, no category: the NAME of the trusted place, nothing around it (decision of the PM, 2026-11-08).
                        s(R.string.feed_label_at_place, place.name)
                    }

                    PlaceRef.Unknown -> {
                        s(R.string.feed_label_trusted_generic)
                    }
                }
            }

            is StateLabel.Left -> {
                s(R.string.feed_label_left, placeNoun(label.place))
            }

            StateLabel.Danger -> {
                s(R.string.feed_label_danger)
            }

            StateLabel.NoNews -> {
                s(R.string.feed_label_no_news)
            }

            StateLabel.SharingStopped -> {
                s(R.string.feed_label_sharing_stopped)
            }

            StateLabel.NewsHidden -> {
                s(R.string.feed_label_news_hidden)
            }

            StateLabel.WaitingAgreement -> {
                s(R.string.miff_waiting_title)
            }

            is StateLabel.Traveling -> {
                s(if (label.mine) R.string.voyage_feed_mine else R.string.voyage_feed_label, travelDay(label.until))
            }

            StateLabel.TravelEnded -> {
                s(R.string.voyage_feed_label_ended)
            }
        }

    /** "15 nov." ("15 nov. 2027" when it is not this year), by the formatter of the system. */
    private fun travelDay(day: TravelDay): String = TravelDayFormat.short(day, locale)

    /** MODE VOYAGE: the sentence about the OTHER members on a trip, under the usual bubble or as the bubble of the travellers. */
    fun travelNote(note: TravelNote): String =
        when (note) {
            is TravelNote.One -> s(R.string.voyage_bubble_one, note.name, travelDay(note.until))
            is TravelNote.Many -> s(R.string.voyage_bubble_many, note.count)
        }

    /** The headline of the neutral bubble when every other member who is known is on a trip. */
    fun travelersHeadline(note: TravelNote): String =
        when (note) {
            is TravelNote.One -> travelNote(note)
            is TravelNote.Many -> s(R.string.voyage_bubble_all)
        }

    /** The line under the label of a card; null when the card has none. */
    fun timeLine(time: TimeLine): String? =
        when (time) {
            TimeLine.None -> {
                null
            }

            is TimeLine.Since -> {
                s(R.string.feed_time_since, since(time.since))
            }

            is TimeLine.LatestNews -> {
                s(R.string.feed_time_latest_news, at(time.at))
            }

            is TimeLine.NoNewsSince -> {
                s(R.string.feed_time_no_news_since, since(time.since))
            }

            is TimeLine.TravelSince -> {
                s(R.string.voyage_feed_since, date(time.atMillis))
            }
        }

    fun battery(battery: Battery): String = s(R.string.feed_battery, battery.pct)

    fun batteryA11y(battery: Battery): String = s(R.string.feed_battery_a11y, battery.pct)

    // ---- the bubble ----------------------------------------------------------------------------------------------

    fun latestNews(age: Age): String =
        when (age) {
            is Age.Minutes -> s(R.string.feed_bubble_latest_minutes, age.minutes)
            is Age.Hours -> s(R.string.feed_bubble_latest_hours, age.hours)
            else -> momentOf(age)?.let { s(R.string.feed_time_latest_news, at(it)) }.orEmpty()
        }

    /** The headline of a page of alerts. Factual and calm: heures absolues, never "alerte", never a point of exclamation. */
    fun alertTitle(page: AlertPage): String {
        val name = page.member.callName
        return when (val kind = page.kind) {
            is AlertKind.Left -> {
                if (page.isMe) {
                    s(R.string.feed_bubble_me_orange, placeNoun(kind.place), at(kind.at))
                } else {
                    s(R.string.feed_bubble_orange, name, placeNoun(kind.place), at(kind.at))
                }
            }

            is AlertKind.NoNewsSince -> {
                if (page.isMe) {
                    s(R.string.feed_bubble_me_red_stale, since(kind.since))
                } else {
                    s(R.string.feed_bubble_red_stale, name, since(kind.since))
                }
            }

            is AlertKind.Danger -> {
                when {
                    page.isMe -> s(R.string.feed_bubble_me_danger)

                    // The wording of the PM for the loved ones: factual and calm, "{Prénom} est à {lieu}".
                    kind.place is PlaceRef.Named -> s(R.string.feed_bubble_danger_named, name, kind.place.name)

                    else -> s(R.string.feed_bubble_danger, name)
                }
            }
        }
    }

    /** The second line of a page of alerts (only a danger zone has one: since when). */
    fun alertDetail(page: AlertPage): String? =
        (page.kind as? AlertKind.Danger)?.let { s(R.string.feed_time_since, since(it.since)) }

    fun alertNote(page: AlertPage): String? =
        (page.kind as? AlertKind.Danger)?.let {
            if (page.isMe) s(R.string.feed_bubble_me_danger_note) else s(R.string.feed_bubble_danger_note)
        }

    fun offlineAlert(page: AlertPage): String? =
        page.offlineSince?.let { s(R.string.feed_bubble_offline_alert, from(it)) }

    /** What the screen reader announces when the bubble changes (never at each minute): the headline and its time. */
    fun announcement(bubble: Bubble): String =
        when (bubble) {
            is Bubble.AllGood -> {
                s(R.string.feed_bubble_all_good)
            }

            Bubble.NoNews -> {
                s(R.string.feed_bubble_no_news_title)
            }

            Bubble.Alone -> {
                s(R.string.feed_bubble_alone)
            }

            Bubble.SharingStopped -> {
                s(R.string.feed_bubble_sharing_stopped_title)
            }

            Bubble.AwaitingAgreement -> {
                s(R.string.feed_bubble_no_news_title)
            }

            is Bubble.Travelers -> {
                travelersHeadline(bubble.note)
            }

            is Bubble.Alerts -> {
                bubble.pages.joinToString(". ") {
                    listOfNotNull(alertTitle(it), alertDetail(it)).joinToString(", ")
                }
            }
        }

    fun offlineBanner(latest: Moment?): String =
        latest?.let { s(R.string.feed_banner_offline, from(it)) } ?: s(R.string.feed_offline_note)
}

/** One [FeedTexts] per configuration (language, 12 h / 24 h): the texts follow a change of language at once. */
@Composable
fun rememberFeedTexts(): FeedTexts {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { FeedTexts(context) }
}
