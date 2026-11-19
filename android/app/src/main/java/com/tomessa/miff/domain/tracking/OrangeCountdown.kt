package com.tomessa.miff.domain.tracking

/*
 * COUNTDOWN-A (docs/tickets/compte-a-rebours-orange.md): the countdown before MY status turns orange, as pure functions. Nothing is read, written or logged
 * here: it is computed on the phone from what the Feed already knows of MY status (the reference of the colour) and the clock. The end of the green is the
 * constant of [StatusThresholds] and the "is it still green" question is asked to [StatusColor] (the very function that colours my card), so the encart and
 * the card can never disagree about the level; no hour count is written here.
 */

/**
 * What the countdown needs to know about MY status, and nothing else: the reference of the colour (the most recent of my departure and my last update).
 * It exists only when the STATIC conditions hold (I share, I am out of every trusted place, a reference is known); the clock decides the rest.
 * A value type: the screen restarts its minute clock only when the reference really changes (an update that puts me back in green).
 */
data class CountdownAnchor(
    val referenceMillis: Long,
) {
    // A time is not a secret, but it is a fact about a person's day: nothing of it in a log line.
    override fun toString(): String = "CountdownAnchor(redacted)"
}

/** The duration as the encart writes it (ticket section 2.3): the screen picks the catalogue string of each shape, so that Android and iOS read the same. */
sealed interface CountdownDuration {
    /** "1 h 12 min": one hour or more, minutes not zero. */
    data class HoursMinutes(
        val hours: Int,
        val minutes: Int,
    ) : CountdownDuration

    /** "1 h", "2 h": whole hours. */
    data class Hours(
        val hours: Int,
    ) : CountdownDuration

    /** "47 min": from 2 to 59 minutes. */
    data class Minutes(
        val minutes: Int,
    ) : CountdownDuration

    /** "moins d'1 min": a minute or less. Never "0 min". */
    data object LessThanMinute : CountdownDuration
}

/** What the encart shows now. */
sealed interface CountdownState {
    /** Not shown (not out, orange, red, trip, sharing stopped, no reference, deadline reached...): nothing is drawn. */
    data object Hidden : CountdownState

    /** [minutes] is the time left rounded UP to the minute (1 to 120): the text AND the border read it, so that they always say the same thing. */
    data class Visible(
        val minutes: Int,
    ) : CountdownState {
        val duration: CountdownDuration get() = OrangeCountdown.duration(minutes)

        /** The part of the border still drawn: 1.0 = full, close to 0 = almost empty. Measured along the outline. */
        val fraction: Float get() = OrangeCountdown.fraction(minutes)

        /** 0.0 while the border is in `status/safe`, up to 1.0 in `status/warning` over the last quarter: never red. */
        val warmth: Float get() = OrangeCountdown.warmth(fraction)
    }
}

object OrangeCountdown {
    /** The whole window: the end of the green, read from [StatusThresholds] (never written again). A fresh start always has exactly this. */
    const val WINDOW_MILLIS = StatusThresholds.ORANGE_AFTER_MILLIS
    const val MINUTE_MILLIS = 60_000L
    private const val WINDOW_MINUTES = (WINDOW_MILLIS / MINUTE_MILLIS).toInt()

    /** The border starts to turn from green towards warm over the last quarter of the window (the last 30 minutes). */
    const val WARMTH_FROM_FRACTION = 0.25f

    /**
     * The static conditions of the ticket (section 2.1), from the facts of MY status: I share (C5), I am OUT of every trusted place and not in a danger
     * zone, not on a trip, not unknown (C2, C4, C6) and a reference exists (C7). Anything else: null, so nothing is drawn. WHOSE facts these are (C1:
     * only the person concerned, on her own phone) is the caller's business: the Feed passes MY status and nobody else's.
     */
    fun anchorOf(facts: MemberStatusFacts?): CountdownAnchor? {
        if (facts == null || !facts.sharingEnabled || facts.presence != PresenceValue.OUT) return null
        val reference = StatusColor.reference(facts) ?: return null
        return CountdownAnchor(reference)
    }

    /**
     * What to show at [nowMillis]. Visible only while the colour computed by [StatusColor] is GREEN and the time left is strictly positive: at the very
     * deadline it is hidden (one millisecond before the colour turns orange, since the rule is "more than 2 h"): never "0 min", never an encart over an orange.
     * A reference in the future (a clock set back) is capped: the time left is never more than the whole window.
     */
    fun at(
        anchor: CountdownAnchor?,
        nowMillis: Long,
    ): CountdownState {
        if (anchor == null || !isSane(anchor.referenceMillis)) return CountdownState.Hidden
        if (StatusColor.levelByAge(anchor.referenceMillis, nowMillis) != StatusLevel.GREEN) return CountdownState.Hidden
        val remaining = remainingMillis(anchor, nowMillis)
        if (remaining <= 0L) return CountdownState.Hidden
        return CountdownState.Visible(minutesOf(remaining))
    }

    /**
     * The wait before the display can change (the next minute boundary of the DEADLINE, not of the wall clock), or null when nothing will change any more
     * (hidden). Always at least 1 ms.
     */
    fun millisUntilNextChange(
        anchor: CountdownAnchor?,
        nowMillis: Long,
    ): Long? {
        val state = at(anchor, nowMillis)
        if (anchor == null || state !is CountdownState.Visible) return null
        // Not capped: a reference in the future makes the first change come only when the clock reaches it.
        val uncapped = anchor.referenceMillis + WINDOW_MILLIS - nowMillis
        return (uncapped - (state.minutes - 1) * MINUTE_MILLIS).coerceAtLeast(1L)
    }

    /** The time left, capped at the whole window (a reference in the future). */
    private fun remainingMillis(
        anchor: CountdownAnchor,
        nowMillis: Long,
    ): Long = minOf(anchor.referenceMillis + WINDOW_MILLIS - nowMillis, WINDOW_MILLIS)

    /** Rounded UP to the minute, so that "0" is never shown before the deadline; 120 at the most. */
    fun minutesOf(remainingMillis: Long): Int =
        ((remainingMillis + MINUTE_MILLIS - 1) / MINUTE_MILLIS).coerceIn(1L, WINDOW_MINUTES.toLong()).toInt()

    /** Ticket section 2.3: 2 h, 1 h 59 min, 1 h 01 min, 1 h, 59 min, 2 min, then "moins d'1 min" (a minute or less). */
    fun duration(minutes: Int): CountdownDuration =
        when {
            minutes <= 1 -> CountdownDuration.LessThanMinute
            minutes < 60 -> CountdownDuration.Minutes(minutes)
            minutes % 60 == 0 -> CountdownDuration.Hours(minutes / 60)
            else -> CountdownDuration.HoursMinutes(minutes / 60, minutes % 60)
        }

    /** f = minutes x 60 s / the window: 1.0 for the whole window, 0.008 for "less than a minute". */
    fun fraction(minutes: Int): Float = (minutes.toLong() * MINUTE_MILLIS).toFloat() / WINDOW_MILLIS.toFloat()

    /** 0 while f is above a quarter; then (0.25 - f) / 0.25, up to 1: the border "warms up" towards orange and never goes further. */
    fun warmth(fraction: Float): Float = ((WARMTH_FROM_FRACTION - fraction) / WARMTH_FROM_FRACTION).coerceIn(0f, 1f)

    // Epoch times only: a negative or absurd reference (a corrupted field) is no reference, like the colour (see StatusColor.safeAfter).
    private fun isSane(reference: Long): Boolean = reference in 0..(Long.MAX_VALUE / 4)
}
