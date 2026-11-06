package com.tomessa.miff.domain.tracking

import com.tomessa.miff.domain.place.PlaceType

/*
 * PRIVACY, for this file: a coordinate, a zone or a position is among the most sensitive data of the app. Every class that carries one has a
 * toString() that shows nothing, so that a log line, an analytics parameter or a crash report can never leak it (criterion VIE1).
 */

/**
 * The identity of a ZONE on the ground (review B1): the identifier of the place AND its geometry and type. A free account can only use the fixed
 * identifiers p1 and p2 (the rules), so two creators' different places can both be "p1": the identifier alone must never name a zone. The same place
 * attached to several Miffs (same identifier, same geometry) has the same key: one zone. The key holds coordinates: it never leaves the phone
 * (local copy, system geofencing) and is NEVER written to a log or a journal; [placeId] is what is shown or written.
 */
object ZoneKey {
    fun of(fact: PlaceFact): String = "${fact.id}|${fact.lat}|${fact.lng}|${fact.radiusM}|${fact.type.value}"

    /** The place identifier of a key (the part that is safe to write in a status or a journal). */
    fun placeId(key: String): String = key.substringBefore('|')

    private val SHAPE =
        Regex(
            "^[A-Za-z0-9]{1,20}\\|-?[0-9]+(\\.[0-9]+)?(E-?[0-9]+)?\\|-?[0-9]+(\\.[0-9]+)?(E-?[0-9]+)?\\|[0-9]{1,4}\\|(trusted|risk)$",
        )

    /** Is this string the shape of a key we made (the receiver ignores any other identifier coming back from the system)? */
    fun isValid(value: String): Boolean = value.length <= 100 && SHAPE.matches(value)
}

/** What the local list of places keeps of one place of one Miff: what is needed to watch it, and nothing else (no name, no address). */
class PlaceFact(
    val id: String,
    val lat: Double,
    val lng: Double,
    val radiusM: Int,
    val type: PlaceType,
) {
    /** Same zone on the ground (a place changed by its creator is another zone: the presence cannot be confirmed any more). */
    val key: String get() = ZoneKey.of(this)

    fun sameGeometry(other: PlaceFact): Boolean =
        lat == other.lat && lng == other.lng && radiusM == other.radiusM && type == other.type

    override fun toString(): String = "PlaceFact(redacted)"
}

/**
 * ONE physical zone watched by the phone: the copies of a place in the Miffs where the person shares are the same place (decision Q4, criterion
 * MIF3): one zone, one status per Miff. [miffIds] are the Miffs it belongs to (never empty).
 */
class TrackedZone(
    /** [ZoneKey]: the identity of the zone (identifier + geometry + type). */
    val key: String,
    val placeId: String,
    val lat: Double,
    val lng: Double,
    val radiusM: Int,
    val type: PlaceType,
    val miffIds: Set<String>,
) {
    override fun toString(): String = "TrackedZone(redacted)"
}

/** A position of the person, with the time of the measure (never the time of the delivery). */
class Fix(
    val lat: Double,
    val lng: Double,
    val measuredAtMillis: Long,
) {
    override fun toString(): String = "Fix(redacted)"
}

/** What the geofencing of the system says: the person came into a zone, or went out of it. */
enum class TransitionKind { ENTER, EXIT }

/** The values of `presence` in `status/{moi}` (backend/data-model.md section 2.7). `in_risk` is the "zone de danger" of the texts. */
enum class PresenceValue(
    val wire: String,
) {
    IN_TRUSTED("in_trusted"),
    OUT("out"),
    IN_RISK("in_risk"),
    UNKNOWN("unknown"),
    ;

    companion object {
        /** An unknown or missing value is null: the colour of such a status is neutral, never a guess. */
        fun fromWire(value: String?): PresenceValue? = entries.firstOrNull { it.wire == value }
    }
}

/** The state of the person in ONE Miff, as the engine derives it from the zones of that Miff. */
data class DerivedPresence(
    val presence: PresenceValue,
    val placeId: String?,
)

/**
 * What to write in `status/{moi}` of one Miff after an event (ticket 6.6): the facts only, the time of the REAL event. The colour is never
 * written. [setLastUpdate] is true for an arrival in a trusted zone (`lastUpdateAt` = the event time); otherwise `lastUpdateAt` is not touched.
 */
data class StatusFact(
    val presence: PresenceValue,
    val placeId: String?,
    val presenceAtMillis: Long,
    val setLastUpdate: Boolean,
)
