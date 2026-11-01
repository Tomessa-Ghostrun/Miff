package com.tomessa.miff.data.place

import com.tomessa.miff.data.miff.Guarded
import com.tomessa.miff.data.miff.MiffRead
import com.tomessa.miff.data.miff.guardedCall
import com.tomessa.miff.domain.miff.DocWrite
import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.place.OwnedMiff
import com.tomessa.miff.domain.place.PaywallReason
import com.tomessa.miff.domain.place.Place
import com.tomessa.miff.domain.place.PlaceCoordinates
import com.tomessa.miff.domain.place.PlaceDraft
import com.tomessa.miff.domain.place.PlaceGroup
import com.tomessa.miff.domain.place.PlaceLimits
import com.tomessa.miff.domain.place.PlaceName
import com.tomessa.miff.domain.place.PlaceQuota
import com.tomessa.miff.domain.place.PlaceSlot
import com.tomessa.miff.domain.place.PlaceType
import com.tomessa.miff.domain.place.PlaceWrites

/*
 * SECURITY, for this file: the name, the address and the coordinates of a place are sensitive. They are never logged, never in an
 * analytics event, never in an exception message. Every failure leaves this file as a [MiffFailure] (no message, no path).
 */

/** What the form needs to know about the person before it opens (everything read from the SERVER: a limit is never decided on a copy). */
class PlaceContext(
    /** Miff+ (an active entitlement that has not expired). The rules decide for real: this only chooses what to show. */
    val premium: Boolean,
    /** The Miffs the person CREATED, the first created first. Only these can hold a place (ticket 6.1). */
    val ownedMiffs: List<OwnedMiff>,
    /** The Miff the person joined most recently, created by them or not (the default of the form, decision Q4). */
    val lastJoinedMiffId: String?,
    /** Every place of the Miffs the person created: one copy per Miff. */
    val places: List<Place>,
) {
    val groups: List<PlaceGroup> get() = PlaceGroup.groupById(places)

    /** The identifiers in use, Miff by Miff. */
    val idsByMiff: Map<String, Set<String>>
        get() = ownedMiffs.associate { miff -> miff.id to places.filter { it.miffId == miff.id }.map { it.id }.toSet() }

    /** How many different places the person has (a place in 3 Miffs counts once): what the free plan limits. */
    val distinctPlaces: Int get() = places.map { it.id }.toSet().size

    override fun toString(): String = "PlaceContext(redacted)"
}

sealed interface PlaceContextLoad {
    class Loaded(
        val context: PlaceContext,
    ) : PlaceContextLoad {
        override fun toString(): String = "Loaded(redacted)"
    }

    data class Failed(
        val failure: MiffFailure,
    ) : PlaceContextLoad
}

/** How a write of a place ended. [Done] only after the SERVER has accepted the lot (criterion PL9). */
sealed interface PlaceWriteResult {
    class Done(
        val placeId: String,
    ) : PlaceWriteResult {
        override fun toString(): String = "Done(redacted)"
    }

    /** Nothing was sent: the free plan has 2 places and no danger zone (criteria LIM1, LIM3). */
    data class NeedsPremium(
        val reason: PaywallReason,
    ) : PlaceWriteResult

    /** One of the chosen Miffs no longer exists, or is no longer the person's (ticket case 14): the whole lot was refused. */
    data object MiffUnavailable : PlaceWriteResult

    /** The place was deleted from another phone while it was open. */
    data object PlaceGone : PlaceWriteResult

    data class Failed(
        val failure: MiffFailure,
    ) : PlaceWriteResult
}

/** What the screens do with places, as they need it (ticket LOC-1). The reading of the list is live: see `MiffRepository.observe`. */
interface PlaceRepository {
    /** Reads who the person is for the form: Miff+ or not, the Miffs they created, the places they already have. */
    suspend fun prepare(uid: String): PlaceContextLoad

    suspend fun create(
        uid: String,
        draft: PlaceDraft,
        context: PlaceContext,
    ): PlaceWriteResult

    /** Changes [original] (all its copies) into [draft]: fields, type, and the Miffs it is attached to. */
    suspend fun update(
        uid: String,
        original: PlaceGroup,
        draft: PlaceDraft,
        context: PlaceContext,
    ): PlaceWriteResult

    /**
     * Deletes the place from EVERY Miff it is attached to, in ONE lot (decision Q4). Headless on purpose: the Premium ticket (end
     * of a subscription) calls it with no screen (criterion PL8). [miffIds] are the Miffs of the person that hold a copy.
     */
    suspend fun delete(
        placeId: String,
        miffIds: List<String>,
    ): PlaceWriteResult
}

class FirestorePlaceRepository(
    private val store: PlaceStore,
    private val newId: () -> String,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val timeoutMillis: Long = TIMEOUT_MS,
) : PlaceRepository {
    // --- Reading ------------------------------------------------------------------------------------------------

    override suspend fun prepare(uid: String): PlaceContextLoad {
        val premium = isPremium(uid)
        val memberships =
            when (val read = guarded { store.readMemberships(uid) }) {
                is Guarded.Value -> read.value
                is Guarded.Failed -> return PlaceContextLoad.Failed(read.failure)
            }
        val owned = mutableListOf<OwnedMiff>()
        for (membership in memberships) {
            when (val read = guarded { store.readMiff(membership.miffId) }) {
                is Guarded.Value -> {
                    val miff = read.value
                    if (miff is MiffRead.Exists && miff.details.ownerUid == uid) {
                        owned += OwnedMiff(membership.miffId, miff.details.name, membership.joinedAtMillis)
                    }
                }

                is Guarded.Failed -> {
                    // A Miff that cannot be read is not one the person can write to; any other failure stops here.
                    if (!read.lostAccess) return PlaceContextLoad.Failed(read.failure)
                }
            }
        }
        val places = mutableListOf<Place>()
        for (miff in owned) {
            when (val read = guarded { store.readPlaces(miff.id) }) {
                is Guarded.Value -> places += read.value
                is Guarded.Failed -> return PlaceContextLoad.Failed(read.failure)
            }
        }
        val lastJoined = memberships.maxByOrNull { it.joinedAtMillis }?.miffId
        return PlaceContextLoad.Loaded(PlaceContext(premium, owned.sortedBy { it.joinedAtMillis }, lastJoined, places))
    }

    /** Miff+ = an active entitlement that has not expired (the rules decide for real). A read that fails counts as "not Miff+". */
    private suspend fun isPremium(uid: String): Boolean {
        val read = (guarded { store.readEntitlement(uid) } as? Guarded.Value)?.value ?: return false
        val expires = read.expiresAtMillis ?: return false
        return read.active && expires > nowMillis()
    }

    // --- Writing ------------------------------------------------------------------------------------------------

    override suspend fun create(
        uid: String,
        draft: PlaceDraft,
        context: PlaceContext,
    ): PlaceWriteResult {
        if (draft.problem != null ||
            !draft.miffIds.allOwnedBy(context)
        ) {
            return PlaceWriteResult.Failed(MiffFailure.UNKNOWN)
        }
        val slot =
            PlaceQuota.forCreate(context.premium, draft.type, draft.miffIds, context.idsByMiff, newId)
        val placeId =
            when (slot) {
                is PlaceSlot.NeedsPremium -> return PlaceWriteResult.NeedsPremium(slot.reason)
                is PlaceSlot.Use -> slot.placeId
            }
        val failure =
            commitAll(listOf(PlaceWrites.create(uid, placeId, draft))) ?: return PlaceWriteResult.Done(placeId)
        return afterFailedWrite(uid, failure, draft.miffIds, PlaceWriteResult.Done(placeId)) { places ->
            places.matchesEverywhere(placeId, draft)
        }
    }

    override suspend fun update(
        uid: String,
        original: PlaceGroup,
        draft: PlaceDraft,
        context: PlaceContext,
    ): PlaceWriteResult {
        if (draft.problem != null ||
            !draft.miffIds.allOwnedBy(context)
        ) {
            return PlaceWriteResult.Failed(MiffFailure.UNKNOWN)
        }
        val from = original.main
        // Making a place a danger zone is Miff+ only (R-LIEU-6); going back to "trusted" is always allowed.
        PlaceQuota
            .forTypeChange(
                context.premium,
                from.type,
                draft.type,
            )?.let { return PlaceWriteResult.NeedsPremium(it) }
        val toRisk = draft.type == PlaceType.RISK && from.type != PlaceType.RISK
        val added = draft.miffIds.filter { it !in original.miffIds }
        // A Miff added gets a NEW document: free only in the fixed slots, and only as a place of trust (rules R-LIEU-5, R-LIEU-6).
        val addedNeedPremium = !(original.id in PlaceLimits.FREE_IDS && draft.type == PlaceType.TRUSTED)
        if (!context.premium && added.isNotEmpty() &&
            (addedNeedPremium || added.any { original.id in context.idsByMiff[it].orEmpty() })
        ) {
            return PlaceWriteResult.NeedsPremium(PaywallReason.LIMIT)
        }
        val lots =
            PlaceWrites.change(
                uid = uid,
                placeId = original.id,
                currentMiffIds = original.miffIds,
                draft = draft,
                typeChangedToRisk = toRisk,
                addedNeedPremium = addedNeedPremium,
            )
        val failure = commitAll(lots) ?: return PlaceWriteResult.Done(original.id)
        val removed = original.miffIds.filter { it !in draft.miffIds }
        val verdict =
            afterFailedWrite(
                uid,
                failure,
                (draft.miffIds + original.miffIds).distinct(),
                PlaceWriteResult.Done(original.id),
            ) { places ->
                // The change went through when every wanted Miff holds the new fields and no removed Miff holds a copy any more.
                places.matchesEverywhere(original.id, draft) &&
                    removed.none { miffId -> places.any { it.miffId == miffId && it.id == original.id } }
            }
        if (verdict is PlaceWriteResult.Failed && failure != MiffFailure.OFFLINE) {
            // Deleted from another phone while the form was open: no Miff holds the place any more.
            val left = placesOf(original.miffIds)
            if (left != null && left.none { it.id == original.id }) return PlaceWriteResult.PlaceGone
        }
        return verdict
    }

    override suspend fun delete(
        placeId: String,
        miffIds: List<String>,
    ): PlaceWriteResult {
        if (miffIds.isEmpty() ||
            miffIds.size > PlaceLimits.MAX_MIFFS
        ) {
            return PlaceWriteResult.Failed(MiffFailure.UNKNOWN)
        }
        val failure = commitAll(listOf(PlaceWrites.delete(placeId, miffIds))) ?: return PlaceWriteResult.Done(placeId)
        // A lot whose answer was lost, or a place already deleted elsewhere: it is gone from every Miff, which is what was asked.
        val left =
            if (failure == MiffFailure.REFUSED || failure == MiffFailure.OFFLINE ||
                failure == MiffFailure.UNKNOWN
            ) {
                placesOf(miffIds)
            } else {
                null
            }
        if (left != null && left.none { it.id == placeId }) return PlaceWriteResult.Done(placeId)
        return PlaceWriteResult.Failed(failure)
    }

    // --- Internals ----------------------------------------------------------------------------------------------

    private fun List<String>.allOwnedBy(context: PlaceContext): Boolean {
        val owned = context.ownedMiffs.map { it.id }.toSet()
        return all { it in owned }
    }

    /** Runs the lots one after the other; null when all of them were confirmed, else the failure of the first that was not. */
    private suspend fun commitAll(lots: List<List<DocWrite>>): MiffFailure? {
        for (lot in lots) {
            val outcome = guarded { store.commit(lot) }
            if (outcome is Guarded.Failed) return outcome.failure
        }
        return null
    }

    /**
     * A write that was not confirmed is looked at on the server before the person is told it failed: (1) did it land after all
     * (answer lost)? then it is [done]; (2) was it refused because a chosen Miff is gone? Otherwise it simply failed, with the
     * reason the person can read.
     */
    private suspend fun afterFailedWrite(
        uid: String,
        failure: MiffFailure,
        miffIds: List<String>,
        done: PlaceWriteResult.Done,
        landed: (List<Place>) -> Boolean,
    ): PlaceWriteResult {
        if (failure == MiffFailure.OFFLINE || failure == MiffFailure.UNKNOWN) {
            val places = placesOf(miffIds)
            if (places != null && landed(places)) return done
        }
        if (failure == MiffFailure.REFUSED || failure == MiffFailure.UNKNOWN) {
            for (miffId in miffIds) {
                val gone =
                    when (val read = guarded { store.readMiff(miffId) }) {
                        is Guarded.Value -> read.value.let { it !is MiffRead.Exists || it.details.ownerUid != uid }
                        is Guarded.Failed -> read.lostAccess
                    }
                if (gone) return PlaceWriteResult.MiffUnavailable
            }
        }
        return PlaceWriteResult.Failed(failure)
    }

    /** The places of these Miffs as the server has them, or null when they cannot be read. A Miff that cannot be read counts as empty. */
    private suspend fun placesOf(miffIds: List<String>): List<Place>? {
        val all = mutableListOf<Place>()
        for (miffId in miffIds) {
            when (val read = guarded { store.readPlaces(miffId) }) {
                is Guarded.Value -> all += read.value
                is Guarded.Failed -> if (!read.lostAccess) return null
            }
        }
        return all
    }

    private fun List<Place>.matchesEverywhere(
        placeId: String,
        draft: PlaceDraft,
    ): Boolean =
        draft.miffIds.all { miffId ->
            val copy = firstOrNull { it.miffId == miffId && it.id == placeId } ?: return@all false
            copy.name == PlaceName.stored(draft.name) && copy.type == draft.type && copy.category == draft.category &&
                copy.radiusM == draft.radiusM && copy.lat == PlaceCoordinates.round5(draft.lat) &&
                copy.lng == PlaceCoordinates.round5(draft.lng)
        }

    private suspend fun <T> guarded(call: suspend () -> T): Guarded<T> = guardedCall(timeoutMillis, call)

    companion object {
        const val TIMEOUT_MS = 20_000L
    }
}
