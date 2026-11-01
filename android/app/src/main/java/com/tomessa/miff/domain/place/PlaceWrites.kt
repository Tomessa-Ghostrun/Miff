package com.tomessa.miff.domain.place

import com.google.firebase.firestore.FieldValue
import com.tomessa.miff.domain.miff.DocWrite
import com.tomessa.miff.domain.miff.WriteKind

/*
 * SECURITY, for this file: the writes below are the contract with backend/firestore.rules (branch backend/regles-lieux, rules
 * R-LIEU-5, R-LIEU-2, R-LIEU-6). A creation writes EVERY key (the rules refuse a missing and an unknown one); the dates are the
 * server's; `createdBy` is the uid of the person; the coordinates have 5 decimals. The name, the address and the coordinates are
 * data of a place: they leave this file only inside the documents of Firestore, never in a log or an event.
 */

/** The lots of ticket LOC-1, as plain data: tested without Firestore, applied by one transaction each. */
object PlaceWrites {
    fun path(
        miffId: String,
        placeId: String,
    ): String = "miffs/$miffId/places/$placeId"

    /** The fields the person can change: the ones of an update, and (with the identity fields) of a creation. */
    private fun editable(draft: PlaceDraft): Map<String, Any?> =
        mapOf(
            "name" to PlaceName.stored(draft.name),
            "type" to draft.type.value,
            "category" to draft.category.value,
            "address" to PlaceAddressText.stored(draft.address),
            "lat" to PlaceCoordinates.round5(draft.lat),
            "lng" to PlaceCoordinates.round5(draft.lng),
            "radiusM" to draft.radiusM,
        )

    /** A whole new document: every key, as `validPlaceShape` and `validPlaceCreate` want it. `priority` stays null (no use yet). */
    fun createDocument(
        uid: String,
        draft: PlaceDraft,
    ): Map<String, Any?> =
        editable(draft) +
            mapOf(
                "priority" to null,
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )

    /** The changed fields of an existing document: `createdBy`, `createdAt` and `priority` are never touched (immutable / unused). */
    fun updateFields(draft: PlaceDraft): Map<String, Any?> =
        editable(draft) + mapOf("updatedAt" to FieldValue.serverTimestamp())

    /** ONE lot: the same document in each chosen Miff, with the same identifier (decision Q4). */
    fun create(
        uid: String,
        placeId: String,
        draft: PlaceDraft,
    ): List<DocWrite> = draft.miffIds.map { DocWrite(path(it, placeId), WriteKind.SET, createDocument(uid, draft)) }

    fun delete(
        placeId: String,
        miffIds: List<String>,
    ): List<DocWrite> = miffIds.map { DocWrite(path(it, placeId), WriteKind.DELETE) }

    /**
     * The lots that change an existing place attached to [currentMiffIds] so that it is attached to [draft].miffIds: the Miffs kept
     * are updated, the Miffs added get a new document with the same identifier, the Miffs removed lose theirs (decision Q4).
     * A new document of a Miff added is written like a creation (same identifier, `createdBy` = the person, same `createdAt` rule).
     *
     * The rules read documents for every write (backend/data-model.md section 12: 2 for the owner check, +1 when Miff+ is
     * checked): if the lot would go over [PlaceLimits.RULE_READ_BUDGET] it is cut in two, the additions and changes first, the
     * removals after. Each lot is atomic; between the two, the place simply sits in both the old and the new Miffs (valid), and a
     * second attempt finishes the job (every write is idempotent).
     */
    fun change(
        uid: String,
        placeId: String,
        currentMiffIds: List<String>,
        draft: PlaceDraft,
        typeChangedToRisk: Boolean,
        addedNeedPremium: Boolean,
    ): List<List<DocWrite>> {
        val kept = draft.miffIds.filter { it in currentMiffIds }
        val added = draft.miffIds.filter { it !in currentMiffIds }
        val removed = currentMiffIds.filter { it !in draft.miffIds }
        val changes =
            kept.map { DocWrite(path(it, placeId), WriteKind.UPDATE, updateFields(draft)) } +
                added.map { DocWrite(path(it, placeId), WriteKind.SET, createDocument(uid, draft)) }
        val deletions = delete(placeId, removed)
        val cost =
            kept.size * (OWNER_READS + if (typeChangedToRisk) PREMIUM_READS else 0) +
                added.size * (OWNER_READS + if (addedNeedPremium) PREMIUM_READS else 0) +
                removed.size * OWNER_READS
        val lots =
            if (cost <= PlaceLimits.RULE_READ_BUDGET) {
                listOf(changes + deletions)
            } else {
                listOf(changes, deletions)
            }
        return lots.filter { it.isNotEmpty() }
    }

    /** Documents read by the rules for the check "owner of the Miff" (`exists` and `get` of the Miff). */
    const val OWNER_READS = 2

    /** Documents read by the rules to know whether the account is Miff+ (`isPremiumOneRead`). */
    const val PREMIUM_READS = 1

    /** The worst number of documents the rules read for a lot of creations (the budget is 20: 5 Miffs at 3 = 15). */
    fun createReads(
        miffCount: Int,
        needsPremium: Boolean,
    ): Int = miffCount * (OWNER_READS + if (needsPremium) PREMIUM_READS else 0)
}
