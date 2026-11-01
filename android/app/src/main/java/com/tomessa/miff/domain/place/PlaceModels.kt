package com.tomessa.miff.domain.place

import com.tomessa.miff.domain.miff.MiffName

/*
 * SECURITY, for this file: the name, the address and the coordinates of a place are sensitive (they can be a home, a school, the
 * home of somebody who does not use Miff). Every class that carries one has a toString() that shows nothing, so that a log
 * line, an analytics parameter or a crash report can never leak it. Nothing here is ever logged.
 */

/** The two kinds of zone. The technical values are the ones of the rules and of the data model (`risk` is the danger zone). */
enum class PlaceType(
    val value: String,
) {
    TRUSTED("trusted"),

    /** "Zone de danger" in every text shown to the person; the technical value stays `risk` (decision of the ticket). */
    RISK("risk"),
    ;

    companion object {
        fun fromValue(value: String?): PlaceType? = entries.firstOrNull { it.value == value }
    }
}

/** The seven categories, a closed list (rule R-LIEU-2). The technical values are written in the document of the place. */
enum class PlaceCategory(
    val value: String,
) {
    HOME("home"),
    WORK("work"),
    SCHOOL("school"),
    SPORT("sport"),
    RELATIVE("relative"),
    LEISURE("leisure"),
    OTHER("other"),
    ;

    companion object {
        /** "Maison" is selected when a new place is made (ticket 6.1); the person can change it, never leave it empty. */
        val DEFAULT = HOME

        fun fromValue(value: String?): PlaceCategory? = entries.firstOrNull { it.value == value }
    }
}

/**
 * One document `miffs/{miffId}/places/{placeId}`, as the server describes it. A place attached to several Miffs is several of
 * these with the same [id] (decision Q4): [PlaceGroup] puts them back together.
 *
 * [category] is null for a place written before the category existed (it can only be edited by choosing one).
 */
class Place(
    val id: String,
    val miffId: String,
    val name: String,
    val type: PlaceType,
    val category: PlaceCategory?,
    val address: String?,
    val lat: Double,
    val lng: Double,
    val radiusM: Int,
    val createdBy: String,
) {
    override fun toString(): String = "Place(redacted)"

    companion object {
        private val ID = Regex("^[A-Za-z0-9]{1,20}$")

        /**
         * A place as the server describes it, cleaned before it is shown (names and addresses are written by somebody else: the
         * creator). Null when the document cannot be a place of this app (bad id, no visible name, unknown type, coordinates or
         * radius out of range): it is simply not shown, never a crash.
         */
        fun fromServer(
            id: String,
            miffId: String,
            fields: Map<String, Any?>?,
        ): Place? {
            if (fields == null || !ID.matches(id)) return null
            val name = (fields["name"] as? String)?.let(MiffName::fromServer) ?: return null
            val type = PlaceType.fromValue(fields["type"] as? String) ?: return null
            val lat =
                (fields["lat"] as? Number)?.toDouble()?.takeIf { it.isFinite() && it in -90.0..90.0 } ?: return null
            val lng =
                (fields["lng"] as? Number)?.toDouble()?.takeIf { it.isFinite() && it in -180.0..180.0 } ?: return null
            val radius =
                (fields["radiusM"] as? Number)?.toInt()?.takeIf { it in RULE_RADIUS_MIN..RULE_RADIUS_MAX }
                    ?: return null
            val address = (fields["address"] as? String)?.let(MiffName::fromServer)
            return Place(
                id = id,
                miffId = miffId,
                name = name,
                type = type,
                category = PlaceCategory.fromValue(fields["category"] as? String),
                address = address,
                lat = lat,
                lng = lng,
                radiusM = radius,
                createdBy = fields["createdBy"] as? String ?: "",
            )
        }

        // The bounds of the rules (validPlaceShape): wider than the ones of the screen (50 to 800).
        private const val RULE_RADIUS_MIN = 50
        private const val RULE_RADIUS_MAX = 2000
    }
}

/** One place and every Miff it is attached to: the copies of one identifier, in the order of the Miffs given. */
class PlaceGroup(
    val id: String,
    val copies: List<Place>,
) {
    /** The copy that is shown and edited (the copies are written together, so they hold the same fields). */
    val main: Place get() = copies.first()

    val miffIds: List<String> get() = copies.map { it.miffId }

    override fun toString(): String = "PlaceGroup(redacted)"

    companion object {
        /** The places of several Miffs grouped by identifier; a group keeps the order in which its identifier first appears. */
        fun groupById(places: List<Place>): List<PlaceGroup> =
            places
                .groupBy { it.id }
                .map { (id, copies) -> PlaceGroup(id, copies) }
    }
}

/** A Miff the person created: the only ones a place can be attached to (decision of the ticket, 6.1). */
class OwnedMiff(
    val id: String,
    val name: String,
    /** When the person joined (for the creator: when they created it). Orders "the first created" and "the last joined". */
    val joinedAtMillis: Long,
) {
    override fun toString(): String = "OwnedMiff(redacted)"
}
