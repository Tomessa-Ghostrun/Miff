package com.tomessa.miff.ui.profile

import android.text.format.DateFormat
import com.tomessa.miff.domain.travel.TravelDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * A day of a trip, written in the language of the reader by the formatter of the system, never by hand: "15 nov." / "Nov 15" (the year is added only when it is not the
 * current one, "15 nov. 2027"). Shared by the Profile and the Feed.
 */
object TravelDayFormat {
    /** "15 nov." (with the year when [TravelDay.showYear]). */
    fun short(
        day: TravelDay,
        locale: Locale,
    ): String = format(day.date, if (day.showYear) "yMMMd" else "MMMd", locale)

    /** "15 nov. 2026": the date of the line "Date de fin", always with its year. */
    fun withYear(
        date: LocalDate,
        locale: Locale,
    ): String = format(date, "yMMMd", locale)

    /** The same day in full words ("15 novembre 2026"), for the screen reader. */
    fun spoken(
        date: LocalDate,
        locale: Locale,
    ): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(date)

    private fun format(
        date: LocalDate,
        skeleton: String,
        locale: Locale,
    ): String =
        try {
            DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(date)
        } catch (e: IllegalArgumentException) {
            // A pattern of the system that java.time does not read: the medium date of the language, still the system's own.
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)
        }
}
