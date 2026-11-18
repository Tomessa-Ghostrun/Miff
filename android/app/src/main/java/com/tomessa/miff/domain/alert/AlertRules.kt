package com.tomessa.miff.domain.alert

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.tomessa.miff.domain.session.DeviceIds
import java.util.Date

/*
 * ALERTE « Je suis en danger » (ticket docs/tickets/alerte-danger.md, AD-A1 / AD-A2). Pure rules and the contract with backend/firestore.rules (`match /alerts/{uid}`,
 * backend/data-model.md section 2.18, branch backend/alerte-danger, NOT DEPLOYED).
 *
 * PRIVACY and SECURITY, for this file: an alert says that a person declared herself in danger. The document holds NO position, NO free text, NO photo, NO name: seven
 * keys, exactly (the rules refuse a missing key and an unknown one). Nothing here is logged, sent as an event or shown in a message that quotes it.
 */

/** The numbers of the gesture and of the contract (one place: the tests and the screens read them here). */
object AlertRules {
    /** The finger must stay down this long before the screen turns into the alert (a simple long press must not flash red): ticket 7.2. */
    const val ARM_MILLIS = 400L

    /** Then the person holds this long (ticket: "maintien 5 s"; to be tuned at the acceptance test). */
    const val HOLD_MILLIS = 5_000L

    /** How far the finger may drift from the button before the gesture is cancelled (48 dp, ticket 7.2). */
    const val HOLD_SLOP_DP = 48

    private const val HOUR = 3_600_000L
    private const val DAY = 24 * HOUR

    /** An alert lasts 7 days at most: the rules accept an `expiresAt` in the window (6 days, 7 days] after the SERVER time. */
    const val DURATION_MILLIS = 7 * DAY

    /**
     * Contract of the client (data-model.md 2.18): `expiresAt` = time of the PHONE + 7 days - 2 hours. The rules cannot be given "server time + 7 days", so they accept a window:
     * a phone whose clock is up to 2 hours ahead, or up to 22 hours behind, still writes a valid alert; beyond that the start is refused (see [AlertFailure.Refused]).
     */
    const val CLOCK_MARGIN_MILLIS = 2 * HOUR

    /** `purgeAt` = `expiresAt` + 2 days at the start (TTL rule to be set by the PM). */
    const val PURGE_AFTER_EXPIRY_MILLIS = 2 * DAY

    /** `purgeAt` = time of the phone + 2 days at the end (the rules accept 1 to 3 days). */
    const val PURGE_AFTER_END_MILLIS = 2 * DAY

    /** The rules refuse a re-arming less than 60 s after the previous start (D20, silent anti-abuse): the app WAITS the remaining seconds calmly, it never shows a refusal. */
    const val REARM_MIN_GAP_MILLIS = 60_000L

    /** Seconds of safety added to that wait (the clock of the phone is not the server's). */
    const val REARM_SAFETY_MILLIS = 3_000L

    /** An alert that could not leave for this long is only sent after the person confirms again (ticket 12.3: an alert sent hours late would worry the circle for nothing). */
    const val STALE_SEND_MILLIS = 15 * 60_000L

    /** How long the app waits for the server before it considers that nothing was sent (the write is a transaction: no answer = nothing left). */
    const val SEND_TIMEOUT_MILLIS = 20_000L

    /** After the person leaves the alert screen, a calm message tells the end of an alert for this long. */
    const val ENDED_NOTICE_MILLIS = 4_000L

    /**
     * Decision of the PM (2026-11-19): AT MOST 3 alerts per person and per 24 hours (sliding window, every Miff together), imposed by the RULES: the 4th start is refused with
     * `permission-denied`, like a wrong clock or a re-arming under 60 s. The server gives no distinct code, so the phone keeps the moments of its OWN last starts to tell this case apart
     * ([quotaReached]); it is a guess made for the SENTENCE only, never a decision: the app always tries the start and believes the server.
     */
    const val MAX_STARTS_PER_DAY = 3

    const val QUOTA_WINDOW_MILLIS = 24 * HOUR

    /** True when [recentStartsMillis] (the starts this phone made) already holds [MAX_STARTS_PER_DAY] in the 24 hours before [nowMillis]. */
    fun quotaReached(
        recentStartsMillis: List<Long>,
        nowMillis: Long,
    ): Boolean = recentStartsMillis.count { nowMillis - it in 0 until QUOTA_WINDOW_MILLIS } >= MAX_STARTS_PER_DAY

    fun expiresAtMillis(deviceNowMillis: Long): Long = deviceNowMillis + DURATION_MILLIS - CLOCK_MARGIN_MILLIS

    fun purgeAtOnStartMillis(expiresAtMillis: Long): Long = expiresAtMillis + PURGE_AFTER_EXPIRY_MILLIS

    fun purgeAtOnEndMillis(deviceNowMillis: Long): Long = deviceNowMillis + PURGE_AFTER_END_MILLIS
}

/**
 * An alert as `miffs/{miffId}/alerts/{uid}` says it (the key is the path: no `miffId` nor `uid` field). Only what the screens need is kept; read from the network, so
 * every field is checked by [parse] and a document that does not look exactly right is ignored (never an error).
 */
class AlertDoc(
    /** The author (the id of the document). */
    val uid: String,
    val active: Boolean,
    /** The server time of the start; null while the server has not filled it in yet (a write still pending): the caller then uses its own clock. */
    val startedAtMillis: Long?,
    val expiresAtMillis: Long,
    val seq: Long,
) {
    /** An alert in progress: `active` and not past its date. The date is the only thing that ends an alert nobody ended (no write, no notification, D19). */
    fun isLive(nowMillis: Long): Boolean = active && expiresAtMillis > nowMillis

    override fun toString(): String = "AlertDoc(redacted)"

    companion object {
        private val IDENTIFIER = Regex("^[A-Za-z0-9_-]{1,128}$")

        /** The document, or null when it is not one of ours (unknown status, no usable expiry date, odd identifier). */
        fun parse(
            uid: String,
            status: Any?,
            startedAtMillis: Long?,
            expiresAtMillis: Long?,
            seq: Any?,
        ): AlertDoc? {
            if (!IDENTIFIER.matches(uid)) return null
            val active =
                when (status) {
                    "active" -> true
                    "ended" -> false
                    else -> return null
                }
            if (expiresAtMillis == null || expiresAtMillis <= 0L) return null
            val number = (seq as? Number)?.toLong()?.takeIf { it >= 1L } ?: 1L
            return AlertDoc(uid, active, startedAtMillis?.takeIf { it > 0L }, expiresAtMillis, number)
        }
    }
}

/** The documents of the alerts of ONE Miff the Feed holds: in progress at [nowMillis], in the order of the start (the oldest first, stable). */
fun List<AlertDoc>.liveAt(nowMillis: Long): List<AlertDoc> =
    filter { it.isLive(nowMillis) }
        .sortedWith(compareBy<AlertDoc> { it.startedAtMillis ?: Long.MAX_VALUE }.thenBy { it.uid })

/**
 * The writes of the alert, as plain data (tested without Firestore). They are the contract with the rules: the seven keys, the server's dates for `startedAt` and
 * `endedAt` (the rules refuse a date of the phone there), the window of `expiresAt`, the identifier of the session.
 */
object AlertWrites {
    /**
     * A start (creation with `seq` = 1, or re-arming with the previous `seq` + 1). Seven keys exactly: `endedAt` is written as null while the alert is active.
     */
    fun start(
        deviceId: String,
        deviceNowMillis: Long,
        seq: Long,
    ): Map<String, Any?> {
        require(DeviceIds.isValid(deviceId)) { "device id shape" }
        require(seq >= 1L) { "seq" }
        val expires = AlertRules.expiresAtMillis(deviceNowMillis)
        return mapOf(
            "status" to "active",
            "startedAt" to FieldValue.serverTimestamp(),
            "expiresAt" to Timestamp(Date(expires)),
            "endedAt" to null,
            "seq" to seq,
            "deviceId" to deviceId,
            "purgeAt" to Timestamp(Date(AlertRules.purgeAtOnStartMillis(expires))),
        )
    }

    /** The manual end: `status`, `endedAt` (server time), `purgeAt` and `deviceId` change, nothing else (the rules refuse any other key). */
    fun end(
        deviceId: String,
        deviceNowMillis: Long,
    ): Map<String, Any?> {
        require(DeviceIds.isValid(deviceId)) { "device id shape" }
        return mapOf(
            "status" to "ended",
            "endedAt" to FieldValue.serverTimestamp(),
            "purgeAt" to Timestamp(Date(AlertRules.purgeAtOnEndMillis(deviceNowMillis))),
            "deviceId" to deviceId,
        )
    }

    fun path(
        miffId: String,
        uid: String,
    ): String = "miffs/$miffId/alerts/$uid"

    val START_KEYS = setOf("status", "startedAt", "expiresAt", "endedAt", "seq", "deviceId", "purgeAt")
    val END_KEYS = setOf("status", "endedAt", "purgeAt", "deviceId")
}

/** Why an alert did not leave or did not end, reduced to what the screen can say calmly. */
sealed interface AlertFailure {
    /** No network, or no answer in time: nothing was written (a transaction only exists online). The app keeps the alert waiting and tries again. */
    data object Offline : AlertFailure

    /** Another phone holds the session of the account: this phone is being signed out, nothing is written. */
    data object Replaced : AlertFailure

    /** I do not share with that Miff (any more): the rules want an active sharing. */
    data object SharingStopped : AlertFailure

    /** The Miff cannot be read for this person (removed, deleted). */
    data object NotMember : AlertFailure

    /** The server said no and nothing above explains it (a clock far off, rules not up to date...): one calm sentence. */
    data object Refused : AlertFailure
}
