package com.tomessa.miff.domain.feed

import java.time.ZoneId
import java.util.Locale

/*
 * PRIVACY, for this file: a position is among the most sensitive data of the app. Every class that carries one has a toString() that shows
 * nothing (criterion L4: no coordinate in a log, a crash report, an analytics event, a share message or the saved state of a screen). The
 * coordinates exist, in the memory of the screen, only between the reading of `positions/{uid}` and the closing of the sheet "Localiser": the Feed never stores them
 * (the copy that the Firestore SDK keeps in its own local cache is a known limit, see data/feed/PositionReader.kt).
 */

/** What the single reading of `positions/{uid}` (from the SERVER, never the cache, no listener) gave. Anything else is one of the calm answers. */
sealed interface PositionLookup {
    class Found(
        val lat: Double,
        val lng: Double,
        val measuredAtMillis: Long,
    ) : PositionLookup {
        override fun toString(): String = "Found(redacted)"
    }

    /** The document does not exist: "n'a pas encore de position connue". */
    data object NotFound : PositionLookup

    /** The rules refused (reciprocity, sharing stopped, removal): ONE neutral message for all of them, nobody is told who stopped. */
    data object Denied : PositionLookup

    /** There is no network: "Pas de connexion pour le moment". Only a real absence of network. */
    data object Offline : PositionLookup

    /**
     * Something unexpected that is neither a refusal nor a missing network (quota of the plan reached, time exceeded, sign-in expired, server
     * trouble): "Impossible d'afficher la position pour le moment. Réessaie dans un moment." Never a technical word, never "quota".
     */
    data object Failed : PositionLookup
}

/** The coordinates of a recent position, kept only while the sheet is open. */
class PositionFix(
    val lat: Double,
    val lng: Double,
) {
    override fun toString(): String = "PositionFix(redacted)"
}

/** What the sheet "Dernière position connue de {Prénom}" says (six calm answers: ticket 5.6, criteria L1 to L3, and the unexpected failure decided by the PM on 2026-11-06). */
sealed interface LocateResult {
    /** Measured less than 24 h ago: the time of the measure, "ce n'est pas une position en direct", and the way to the maps of the system. */
    class Recent(
        val fix: PositionFix,
        val measuredAt: Moment,
        val age: Age,
    ) : LocateResult {
        override fun toString(): String = "Recent(redacted)"
    }

    /** Measured more than 24 h ago: the DATE only, no coordinate, no button of map. */
    data class Old(
        val measuredAt: Moment,
    ) : LocateResult

    data object NotFound : LocateResult

    data object Denied : LocateResult

    data object Offline : LocateResult

    /** The sixth calm answer: something unexpected happened; try again in a moment (distinct from "Pas de connexion"). */
    data object Unavailable : LocateResult

    /** MODE VOYAGE: my trip is on, the positions of the others come back when it ends (nothing is read). */
    data object PausedByTrip : LocateResult
}

object Locate {
    /** A position older than this is not shown any more (decision of the PM, 2026-11-01): the date stays, the coordinates go. */
    const val SHOWN_FOR_MILLIS = DAY_MILLIS

    /** Decimals kept for the map of the system: 5 (about one metre), the precision the position was written with. */
    private const val DECIMALS = 5

    /**
     * "Pas de connexion" is ONLY for a real absence of network: an "unreachable" answer while the phone HAS a network is something unexpected
     * ([PositionLookup.Failed]), and an unexpected failure while the phone has NO network is the absence of network.
     */
    fun reconcile(
        lookup: PositionLookup,
        networkOnline: Boolean,
    ): PositionLookup =
        when {
            lookup == PositionLookup.Offline && networkOnline -> PositionLookup.Failed
            lookup == PositionLookup.Failed && !networkOnline -> PositionLookup.Offline
            else -> lookup
        }

    fun interpret(
        lookup: PositionLookup,
        nowMillis: Long,
        zone: ZoneId,
    ): LocateResult =
        when (lookup) {
            PositionLookup.NotFound -> {
                LocateResult.NotFound
            }

            PositionLookup.Denied -> {
                LocateResult.Denied
            }

            PositionLookup.Offline -> {
                LocateResult.Offline
            }

            PositionLookup.Failed -> {
                LocateResult.Unavailable
            }

            is PositionLookup.Found -> {
                val elapsed = nowMillis - lookup.measuredAtMillis
                val moment = Moment.of(lookup.measuredAtMillis, nowMillis, zone)
                // Strictly more than 24 h: the coordinates are dropped HERE, they are not even carried to the screen.
                if (elapsed > SHOWN_FOR_MILLIS) {
                    LocateResult.Old(moment)
                } else {
                    LocateResult.Recent(
                        PositionFix(lookup.lat, lookup.lng),
                        moment,
                        Age.of(lookup.measuredAtMillis, nowMillis, zone),
                    )
                }
            }
        }

    private fun coordinate(value: Double): String = String.format(Locale.ROOT, "%.${DECIMALS}f", value)

    /**
     * The `geo:` address handed to the maps application of the system: the coordinates ALONE (the pin sits on them), no name, no label, no
     * person (criterion L2). Written with [Locale.ROOT] and 5 decimals: a phone in French would otherwise write "48,85661" and break the address.
     */
    fun geoUri(fix: PositionFix): String {
        val point = "${coordinate(fix.lat)},${coordinate(fix.lng)}"
        return "geo:$point?q=$point"
    }

    /** The page of Google Maps, used when no application answers the `geo:` address: the same coordinates, nothing else. */
    fun webMapUrl(fix: PositionFix): String =
        "https://www.google.com/maps/search/?api=1&query=${coordinate(fix.lat)},${coordinate(fix.lng)}"
}
