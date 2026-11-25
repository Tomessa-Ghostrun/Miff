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

    /**
     * Free plan: 3 places (decision of the PM, 2026-11-10, which replaces the 2 places of QL9), in the fixed slots `p1`, `p2` and `p3` of
     * the rules (`backend/firestore.rules`, branch `backend/lieux-gratuits-3`). USABLE ONLY ONCE THE PM HAS DEPLOYED THOSE RULES: until then
     * the server refuses `p3` for a free account (the person sees the calm "impossible d'enregistrer" message, nothing is lost).
     */
    const val FREE_PLACES = 3
    val FREE_IDS = listOf("p1", "p2", "p3")

    /**
     * Free plan: ONE Miff at a time (the membership key "free", `backend/firestore.rules`). The offer says it in words ("Limite d'une seule Miff sinon"): a test ties that sentence to this
     * number, and [com.tomessa.miff.domain.miff.MiffSelector] opens the offer from it.
     */
    const val FREE_MIFFS = 1

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

/**
 * Which limit asks for Miff+. The danger zone is Miff+ only (decision of the ticket 6.2); the 3rd place is the limit; MIFF is a 2nd Miff,
 * asked from the selector of the Feed (decision of the PM, 2026-11-08). SUBSCRIPTION: the offer opened on purpose from Settings > Abonnement (SRV-8A), no limit was hit.
 */
enum class PaywallReason { LIMIT, DANGER, MIFF, SUBSCRIPTION }

/**
 * Who may do what with a danger zone (type `risk`), in ONE place (decision of the PM, 2026-11-17, ticket docs/tickets/lieu-danger-demande-gratuite.md):
 *
 * | | ASK for one (member) | ACCEPT one (creator) | DECLARE one directly (creator) |
 * |---|---|---|---|
 * | free | yes | no (the offer opens, the request stays pending) | no (the offer opens) |
 * | Miff+ | yes | yes | yes |
 *
 * Only the plan of the CREATOR matters: only the creator writes a place, and the rules read HIS plan. Nothing here ever looks at the plan of the person who
 * asks, and a member cannot know the plan of the creator (it is private): the client never blocks a request on a guess.
 */
object DangerZoneRights {
    /** Any member who is not the creator, free or Miff+: no offer, no lock. */
    fun canAsk(): Boolean = true

    /** The creator accepts a danger zone only with Miff+ (the free creator may still decline it). */
    fun canAccept(creatorPremium: Boolean): Boolean = creatorPremium

    /** The creator declares a danger zone directly (new place, or an existing place changed to a danger zone) only with Miff+. */
    fun canDeclare(creatorPremium: Boolean): Boolean = creatorPremium

    /**
     * Whether "Zone de danger" is shown as Miff+ (the pill, the offer at the tap) in a form: never in the form of a REQUEST ([asRequest]); in the other
     * forms a free account sees it as Miff+ unless the place already is a danger zone (a free creator who lost Miff+ keeps and edits the ones he has).
     */
    fun isLockedInForm(
        asRequest: Boolean,
        premium: Boolean,
        current: PlaceType,
    ): Boolean = !asRequest && !canDeclare(premium) && current != PlaceType.RISK
}

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
    /** Write with this identifier (`p1` / `p2` / `p3` on the free plan, a fresh one with Miff+). */
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
 * Free: 3 places of trust in the fixed slots `p1`, `p2` and `p3` of the rules; no danger zone. Miff+: any number, any type, a fresh
 * identifier drawn once on the phone.
 */
object PlaceQuota {
    /**
     * @param existingIdsByMiff the identifiers already used in each Miff the person created (EVERY Miff, not only the chosen ones)
     * @param distinctPlaces how many different places the person has (a place in 3 Miffs counts once): what the free plan limits
     */
    fun forCreate(
        premium: Boolean,
        type: PlaceType,
        existingIdsByMiff: Map<String, Set<String>>,
        distinctPlaces: Int,
        newId: () -> String,
    ): PlaceSlot {
        if (premium) return PlaceSlot.Use(newId())
        if (type == PlaceType.RISK &&
            !DangerZoneRights.canAccept(premium)
        ) {
            return PlaceSlot.NeedsPremium(PaywallReason.DANGER)
        }
        if (distinctPlaces >= PlaceLimits.FREE_PLACES) return PlaceSlot.NeedsPremium(PaywallReason.LIMIT)
        // The slot must be unused in EVERY Miff the person created, not only in the chosen ones: two different places that
        // shared an identifier would be read as ONE place in two Miffs (the count of the limit and the edit would be wrong).
        val taken = existingIdsByMiff.values.flatten().toSet()
        val free =
            PlaceLimits.FREE_IDS.firstOrNull { it !in taken } ?: return PlaceSlot.NeedsPremium(PaywallReason.LIMIT)
        return PlaceSlot.Use(free)
    }

    /**
     * The free plan cannot make a place a danger zone (R-LIEU-6); going back to "trusted" is always allowed. [asRequest]: the form of a REQUEST (a member
     * asks, the creator decides): the choice is free for everybody, the plan that counts is the one of the creator, at the acceptance.
     */
    fun forTypeChange(
        premium: Boolean,
        from: PlaceType,
        to: PlaceType,
        asRequest: Boolean = false,
    ): PaywallReason? {
        val allowed = if (asRequest) DangerZoneRights.canAsk() else DangerZoneRights.canDeclare(premium)
        return if (to == PlaceType.RISK && from != PlaceType.RISK && !allowed) PaywallReason.DANGER else null
    }

    /** Whether the "Ajouter un lieu" of a creator opens the limit screen instead of the form (free plan, 3 places already). */
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

    /**
     * The Miff the form of a NEW place opened from: ticked first when the person created it (the list they were looking at names that Miff, so a place
     * must not go to another circle by surprise: review of the PR, 2026-11-11). A Miff they did not create, or none, falls back on [of].
     */
    fun of(
        owned: List<OwnedMiff>,
        lastJoinedId: String?,
        openedFromId: String?,
    ): String? = owned.firstOrNull { it.id == openedFromId }?.id ?: of(owned, lastJoinedId)
}
