package com.tomessa.miff.ui.notification

import android.content.Context
import android.text.format.DateFormat
import com.tomessa.miff.R
import com.tomessa.miff.domain.feed.PlaceRef
import com.tomessa.miff.domain.notification.LastVisit
import com.tomessa.miff.domain.notification.VisitDay
import com.tomessa.miff.domain.notification.VisitKind
import com.tomessa.miff.ui.feed.FeedTexts
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val NBSP = ' '
private const val VOWELS = "aeiouyàâäéèêëîïôöùûüœæh"

/*
 * The sentences of "Depuis ta dernière visite" (NOTIF-P1A), made when a line is DRAWN from what the line holds (a kind, a category, a time) and the first name the app knows
 * NOW: the centre itself keeps no name. French and English, same keys as iOS (`notif_c1_*`). Times follow the language and the 12 h / 24 h choice of the phone.
 * No sentence carries a duration ("depuis 4 h"), a verdict or a promise: a fact and an hour (decision Q1 of the PM, 2026-11-13).
 */
class LastVisitTexts(
    private val context: Context,
    /** For the tests only: null = the zone of the phone, read at EACH use. */
    private val zoneOverride: ZoneId? = null,
) {
    private val zone: ZoneId get() = zoneOverride ?: ZoneId.systemDefault()
    private val locale: Locale get() = context.resources.configuration.locales[0]
    private val is24Hour: Boolean get() = DateFormat.is24HourFormat(context)
    private val feedTexts = FeedTexts(context, zoneOverride)

    private fun s(
        id: Int,
        vararg args: Any,
    ): String = context.getString(id, *args)

    private fun clock(millis: Long): String =
        DateTimeFormatter
            .ofPattern(s(if (is24Hour) R.string.feed_time_pattern_24 else R.string.feed_time_pattern_12), locale)
            .format(Instant.ofEpochMilli(millis).atZone(zone))
            .replace(' ', NBSP)

    private fun date(millis: Long): String =
        DateTimeFormatter
            .ofPattern(
                s(R.string.feed_date_pattern),
                locale,
            ).format(Instant.ofEpochMilli(millis).atZone(zone))

    private fun weekday(millis: Long): String =
        DateTimeFormatter.ofPattern("EEEE", locale).format(Instant.ofEpochMilli(millis).atZone(zone))

    /** "à 17 h 10", "hier à 17 h 10", "lundi à 9 h 30", "le 3 nov. à 9 h 30". */
    fun at(
        millis: Long,
        nowMillis: Long,
    ): String =
        when (LastVisit.dayOf(millis, nowMillis, zone)) {
            VisitDay.TODAY -> s(R.string.feed_moment_at_today, clock(millis))
            VisitDay.YESTERDAY -> s(R.string.feed_moment_at_yesterday, clock(millis))
            VisitDay.WEEKDAY -> s(R.string.notif_c1_moment_at_weekday, weekday(millis), clock(millis))
            VisitDay.DATE -> s(R.string.feed_moment_at_date, date(millis), clock(millis))
        }

    /** "17 h 10" (after a colon), "hier à 17 h 10", "lundi à 9 h 30", "le 3 nov. à 9 h 30". */
    fun since(
        millis: Long,
        nowMillis: Long,
    ): String =
        when (LastVisit.dayOf(millis, nowMillis, zone)) {
            VisitDay.TODAY -> s(R.string.feed_moment_since_today, clock(millis))
            VisitDay.YESTERDAY -> s(R.string.feed_moment_since_yesterday, clock(millis))
            VisitDay.WEEKDAY -> s(R.string.notif_c1_moment_since_weekday, weekday(millis), clock(millis))
            VisitDay.DATE -> s(R.string.feed_moment_since_date, date(millis), clock(millis))
        }

    /**
     * The sentence of a line. [firstName] is the first name the app knows now, or null (a cold start before the Feed read the members): "Un proche" / "Someone" then,
     * never a blank. No gender agreement in any of the four lines.
     */
    fun sentence(
        line: LastVisit.Parsed,
        firstName: String?,
        nowMillis: Long,
    ): String {
        val name = firstName?.takeIf { it.isNotBlank() } ?: s(R.string.notif_c1_someone)
        val place = feedTexts.placeNoun(line.place?.let { PlaceRef.Category(it) } ?: PlaceRef.Unknown)
        val text =
            when (line.kind) {
                VisitKind.ARRIVAL -> {
                    s(R.string.notif_c1_arrival, name, place, at(line.shownAtMillis, nowMillis))
                }

                VisitKind.DEPARTURE -> {
                    s(R.string.notif_c1_departure, name, place, at(line.shownAtMillis, nowMillis))
                }

                VisitKind.UPDATE -> {
                    s(R.string.notif_c1_update, name, at(line.shownAtMillis, nowMillis))
                }

                // "de Thomas", "d'Amandine": the elision is chosen by the first letter (the English texts of the two keys are the same).
                VisitKind.SILENCE -> {
                    s(
                        if (name.first().lowercaseChar() in
                            VOWELS
                        ) {
                            R.string.notif_c1_silence_vowel
                        } else {
                            R.string.notif_c1_silence
                        },
                        name,
                        since(line.shownAtMillis, nowMillis),
                    )
                }
            }
        // "un proche" opens a sentence ("Un proche a quitté l'école…"): the first letter is a capital, whatever the language.
        return text.replaceFirstChar { it.titlecase(locale) }
    }
}
