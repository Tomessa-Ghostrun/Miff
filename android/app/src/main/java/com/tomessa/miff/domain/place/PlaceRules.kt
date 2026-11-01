package com.tomessa.miff.domain.place

import com.tomessa.miff.domain.miff.MiffName
import com.tomessa.miff.domain.miff.NameInput
import kotlin.math.roundToInt

/*
 * SECURITY, for this file: the client applies the same rules as backend/firestore.rules (and the stricter ones of the ticket), so
 * that a refusal of the server stays a safety net and never the first time a rule is met. The coordinates, the address and the
 * name pass through here and nowhere else: nothing is logged.
 */

/** The numbers of the ticket (section 6.1, 6.2, 6.3, 12) and of the rules, in ONE place. */
object PlaceLimits {
    const val NAME_MIN = 2
    const val NAME_MAX = 50
    const val ADDRESS_MAX = 200

    const val RADIUS_MIN = 50
    const val RADIUS_MAX = 800
    const val RADIUS_STEP = 50
    const val RADIUS_DEFAULT = 100

    /** Under this radius the note "la détection peut manquer un passage" shows (decision Q12). */
    const val RADIUS_RECOMMENDED = 100

    /** Free plan: 2 places (decision QL9), in the fixed slots of the rules. */
    const val FREE_PLACES = 2
    val FREE_IDS = listOf("p1", "p2")

    /** A place is written in at most 5 Miffs at a time (decision Q4): the rules read at most 20 documents per lot. */
    const val MAX_MIFFS = 5

    /** The rules read at most 20 documents for one lot (backend/data-model.md section 12). */
    const val RULE_READ_BUDGET = 20

    /** The suggestions of address start at 3 characters, 300 ms after the last key (ticket ADR1). */
    const val SUGGESTION_MIN_CHARS = 3
    const val SUGGESTION_DEBOUNCE_MS = 300L
    const val SUGGESTION_COUNT = 5
}

/** The name of a place: 2 to 50 characters once the spaces at both ends are removed (the rules accept 1 to 60). */
object PlaceName {
    /** What the field keeps of what was typed: cleaned, at most 50 units, cut between two whole characters. */
    fun limit(typed: String): NameInput = MiffName.limit(typed, PlaceLimits.NAME_MAX)

    /** The name as it is stored: the typed text without the spaces at both ends. */
    fun stored(typed: String): String = limit(typed).text.trim()

    /** "2 caractères minimum" is shown when this is false and something was typed (the button works from 1 character). */
    fun isValid(typed: String): Boolean = stored(typed).length in PlaceLimits.NAME_MIN..PlaceLimits.NAME_MAX

    /** The button "Enregistrer" is active from ONE visible character (decision Q10): the minimum is told at the tap. */
    fun hasSomething(typed: String): Boolean = stored(typed).isNotEmpty()
}

/** The radius of the perimeter: 50 to 800 m by steps of 50. */
object PlaceRadius {
    val values: List<Int> =
        (PlaceLimits.RADIUS_MIN..PlaceLimits.RADIUS_MAX step PlaceLimits.RADIUS_STEP).toList()

    /** The allowed value nearest to [raw] (a slider position, or a value read from somewhere). */
    fun snap(raw: Float): Int {
        val stepped = (raw / PlaceLimits.RADIUS_STEP).roundToInt() * PlaceLimits.RADIUS_STEP
        return stepped.coerceIn(PlaceLimits.RADIUS_MIN, PlaceLimits.RADIUS_MAX)
    }

    fun isAllowed(radiusM: Int): Boolean = radiusM in values

    /** Under 100 m: the discreet note (never a red error: the value stays allowed). */
    fun isBelowRecommended(radiusM: Int): Boolean = radiusM < PlaceLimits.RADIUS_RECOMMENDED
}

/** Latitude and longitude as the rules and the ticket want them: 5 decimals (about 1 m), inside the planet. */
object PlaceCoordinates {
    fun round5(value: Double): Double = Math.round(value * 100_000.0) / 100_000.0

    fun isValid(
        lat: Double,
        lng: Double,
    ): Boolean = lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0
}

/** The address of a place, as a text of the rules: cleaned and at most 200 characters, cut between two whole characters. */
object PlaceAddressText {
    fun stored(label: String): String = MiffName.limit(label, PlaceLimits.ADDRESS_MAX).text.trim()
}

/** Which of the two limits asks for Miff+. The danger zone is Miff+ only (decision of the ticket 6.2); the 3rd place is the limit. */
enum class PaywallReason { LIMIT, DANGER }

/**
 * What the person filled in, ready to be written. Built by the form only when [problem] is null. The Miffs are the ids of Miffs
 * that THE PERSON CREATED (the repository checks it again against the server).
 */
class PlaceDraft(
    val name: String,
    val miffIds: List<String>,
    val address: String,
    val lat: Double,
    val lng: Double,
    val radiusM: Int,
    val type: PlaceType,
    val category: PlaceCategory,
) {
    override fun toString(): String = "PlaceDraft(redacted)"

    /** What is wrong with this draft, or null. Mirrors the rules (the form shows it before, the server checks it after). */
    val problem: DraftProblem?
        get() =
            when {
                !PlaceName.isValid(name) -> DraftProblem.NAME
                miffIds.isEmpty() || miffIds.size > PlaceLimits.MAX_MIFFS -> DraftProblem.MIFFS
                miffIds.toSet().size != miffIds.size -> DraftProblem.MIFFS
                address.isBlank() || address.length > PlaceLimits.ADDRESS_MAX -> DraftProblem.ADDRESS
                !PlaceCoordinates.isValid(lat, lng) -> DraftProblem.ADDRESS
                !PlaceRadius.isAllowed(radiusM) -> DraftProblem.RADIUS
                else -> null
            }
}

enum class DraftProblem { NAME, MIFFS, ADDRESS, RADIUS }

/** The answer of [PlaceQuota]: which identifier to write, or why Miff+ is needed. */
sealed interface PlaceSlot {
    /** Write with this identifier (`p1` / `p2` on the free plan, a fresh one with Miff+). */
    class Use(
        val placeId: String,
    ) : PlaceSlot

    class NeedsPremium(
        val reason: PaywallReason,
    ) : PlaceSlot
}

/**
 * The limits of the free plan, decided BEFORE anything is sent (the rules are only the safety net, ticket 6.2).
 *
 * Free: 2 places of trust in the fixed slots `p1` and `p2` of the rules; no danger zone. Miff+: any number, any type, a fresh
 * identifier drawn once on the phone.
 */
object PlaceQuota {
    /**
     * @param existingIdsByMiff the identifiers already used in each Miff the person created (every Miff, not only the chosen ones:
     *   the limit counts the places of the person)
     */
    fun forCreate(
        premium: Boolean,
        type: PlaceType,
        targetMiffIds: List<String>,
        existingIdsByMiff: Map<String, Set<String>>,
        newId: () -> String,
    ): PlaceSlot {
        if (premium) return PlaceSlot.Use(newId())
        if (type == PlaceType.RISK) return PlaceSlot.NeedsPremium(PaywallReason.DANGER)
        val all = existingIdsByMiff.values.flatten().toSet()
        if (all.size >= PlaceLimits.FREE_PLACES) return PlaceSlot.NeedsPremium(PaywallReason.LIMIT)
        // The slot must be free in EVERY chosen Miff: writing an existing document would be an update, which the rules refuse.
        val taken = targetMiffIds.flatMap { existingIdsByMiff[it].orEmpty() }.toSet()
        val free =
            PlaceLimits.FREE_IDS.firstOrNull { it !in taken } ?: return PlaceSlot.NeedsPremium(PaywallReason.LIMIT)
        return PlaceSlot.Use(free)
    }

    /** The free plan cannot make a place a danger zone (R-LIEU-6); going back to "trusted" is always allowed. */
    fun forTypeChange(
        premium: Boolean,
        from: PlaceType,
        to: PlaceType,
    ): PaywallReason? = if (!premium && to == PlaceType.RISK && from != PlaceType.RISK) PaywallReason.DANGER else null

    /** Whether the "Ajouter un lieu" of a creator opens the limit screen instead of the form (free plan, 2 places already). */
    fun isAtFreeLimit(
        premium: Boolean,
        distinctPlaces: Int,
    ): Boolean = !premium && distinctPlaces >= PlaceLimits.FREE_PLACES
}

/** The Miffs ticked when the form opens (decision Q4). */
object DefaultMiffChoice {
    /**
     * The last Miff joined IF the person created it, else the first one they created; null when they created none.
     * @param owned the Miffs the person created, in any order
     * @param lastJoinedId the Miff the person joined most recently, created by them or not
     */
    fun of(
        owned: List<OwnedMiff>,
        lastJoinedId: String?,
    ): String? =
        owned.firstOrNull { it.id == lastJoinedId }?.id
            ?: owned.minByOrNull { it.joinedAtMillis }?.id
}
