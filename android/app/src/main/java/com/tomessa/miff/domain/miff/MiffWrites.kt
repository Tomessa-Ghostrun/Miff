package com.tomessa.miff.domain.miff

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.tomessa.miff.domain.auth.ProfileDocument
import com.tomessa.miff.domain.session.DeviceIds

/*
 * SECURITY, for this file: the writes below are the contract with backend/firestore.rules. Every key of a document is always
 * written (the rules refuse a missing key and an unknown one); the dates are the server's (the rules refuse a date of the
 * phone); no coordinate, no address, no place name is ever written by a Miff screen (ticket Miff criterion V1).
 *
 * LOC-2a, the contract of the rules for the LAST KNOWN LOCATION (backend/firestore.rules, `positions/{uid}`; data-model.md section 8):
 * as soon as a position exists, the rules REFUSE the stop of sharing, the departure, the removal and the final deletion unless the same
 * lot deletes `miffs/{id}/positions/{uid}` too. This build writes no position (that is LOC-2b), but every one of these lots already carries
 * the deletion (it is accepted when there is nothing to delete), so that a person is never locked in by this build once LOC-2b writes a
 * position ("avant ou avec" the first build that writes one).
 */

/** [MERGE] is a set that keeps the other fields of an existing document (needed for `FieldValue.increment` on a counter that may not exist yet). */
enum class WriteKind { SET, UPDATE, DELETE, MERGE }

/** One write of a lot: the path of the document, what to do, the fields (empty for a deletion). */
class DocWrite(
    val path: String,
    val kind: WriteKind,
    val data: Map<String, Any?> = emptyMap(),
) {
    override fun toString(): String = "DocWrite($kind)"
}

/**
 * The names, phone number and photo copied into the card of a member (`members/{uid}`), read from `users/{uid}` INSIDE the
 * transaction that writes the card: the rules require the copy to be equal to the profile after the lot, so it is read at the
 * very moment it is copied (backend/data-model.md section 2.6).
 */
class ProfileCopy(
    val firstName: String,
    val lastName: String,
    val phone: String,
    val avatarJpeg: ByteArray?,
) {
    override fun toString(): String = "ProfileCopy(redacted)"

    companion object {
        /** Null when the profile cannot give a valid card (missing, no phone number): the person goes back to the profile step. */
        fun from(fields: Map<String, Any?>?): ProfileCopy? {
            if (fields == null) return null
            val first = fields["firstName"] as? String ?: return null
            val last = fields["lastName"] as? String ?: return null
            val phone = (fields["phone"] as? String)?.takeIf { ProfileDocument.E164.matches(it) } ?: return null
            val avatar =
                when (val raw = fields["avatar"]) {
                    is Blob -> raw.toBytes()
                    is ByteArray -> raw
                    else -> null
                }?.takeIf { it.size in 1..ProfileDocument.MAX_AVATAR_BYTES }
            return ProfileCopy(first, last, phone, avatar)
        }
    }
}

/** The lots of ticket Miff 5.4, 5.7 and 5.13, as plain data: tested without Firestore, applied by one transaction each. */
object MiffWrites {
    /** `members/{uid}.sharing` of a person who just accepted the text (explicit consent, decision QM2). */
    private fun sharingOn(): Map<String, Any?> =
        mapOf(
            "enabled" to true,
            "consentedAt" to FieldValue.serverTimestamp(),
            "consentVersion" to MiffSharingConsent.VERSION,
            "revokedAt" to null,
        )

    /**
     * The first status, written in the same lot as the consent: "unknown" (never "all is well" before a real signal), the
     * permission of the phone as it is, no coordinate. Without it the person could not read the others (backend/data-model.md
     * section 8, reciprocity ATK3).
     */
    fun initialStatus(
        access: LocationAccess,
        deviceId: String,
    ): Map<String, Any?> {
        // "Un compte, un seul appareil" (SESS-2): the first status carries the identifier of the session like every other write of a status.
        require(DeviceIds.isValid(deviceId)) { "device id shape" }
        return mapOf(
            "presence" to "unknown",
            "placeId" to null,
            "presenceAt" to FieldValue.serverTimestamp(),
            "lastUpdateAt" to FieldValue.serverTimestamp(),
            "updateText" to null,
            "hasPhoto" to false,
            "batteryPct" to null,
            "locationAccess" to access.value,
            "escalationDueAt" to null,
            "updatedAt" to FieldValue.serverTimestamp(),
            "deviceId" to deviceId,
        )
    }

    private fun card(
        role: MiffRole,
        profile: ProfileCopy,
        joinCode: String?,
    ): Map<String, Any?> {
        val fields =
            mutableMapOf<String, Any?>(
                "role" to if (role == MiffRole.OWNER) "owner" else "member",
                "firstName" to profile.firstName,
                "lastName" to profile.lastName,
                "phone" to profile.phone,
                "joinedAt" to FieldValue.serverTimestamp(),
                "sharing" to sharingOn(),
            )
        if (profile.avatarJpeg != null) fields["avatar"] = Blob.fromBytes(profile.avatarJpeg)
        // Only a member who comes in with a code carries it (the rules read it as the proof of entry).
        if (joinCode != null) fields["joinCode"] = joinCode
        return fields
    }

    private fun index(miffId: String): Map<String, Any?> =
        mapOf("miffId" to miffId, "joinedAt" to FieldValue.serverTimestamp())

    /** "Créer et partager": 5 writes in ONE lot (ticket C4). [membershipKey] is "free", or the Miff's id for a premium account. */
    fun create(
        uid: String,
        miffId: String,
        code: String,
        name: String,
        profile: ProfileCopy,
        membershipKey: String,
        access: LocationAccess,
        deviceId: String,
    ): List<DocWrite> {
        require(JoinCode.isValid(code)) { "join code shape" }
        val stored = MiffName.stored(name)
        require(MiffName.isValid(stored)) { "name length" }
        return listOf(
            DocWrite(
                "miffs/$miffId",
                WriteKind.SET,
                mapOf(
                    "ownerUid" to uid,
                    "name" to stored,
                    "joinCode" to code,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            ),
            DocWrite(
                "joinCodes/$code",
                WriteKind.SET,
                mapOf("miffId" to miffId, "miffName" to stored, "createdAt" to FieldValue.serverTimestamp()),
            ),
            DocWrite("miffs/$miffId/members/$uid", WriteKind.SET, card(MiffRole.OWNER, profile, joinCode = null)),
            DocWrite("users/$uid/memberships/$membershipKey", WriteKind.SET, index(miffId)),
            DocWrite("miffs/$miffId/status/$uid", WriteKind.SET, initialStatus(access, deviceId)),
        )
    }

    /** "Rejoindre et partager": 3 writes in ONE lot (ticket K2). */
    fun join(
        uid: String,
        miffId: String,
        code: String,
        profile: ProfileCopy,
        membershipKey: String,
        access: LocationAccess,
        deviceId: String,
    ): List<DocWrite> {
        require(JoinCode.isValid(code)) { "join code shape" }
        return listOf(
            DocWrite("miffs/$miffId/members/$uid", WriteKind.SET, card(MiffRole.MEMBER, profile, joinCode = code)),
            DocWrite("users/$uid/memberships/$membershipKey", WriteKind.SET, index(miffId)),
            DocWrite("miffs/$miffId/status/$uid", WriteKind.SET, initialStatus(access, deviceId)),
        )
    }

    /** Rename: the Miff AND the name kept with the code, in the same lot (ticket G3). */
    fun rename(
        miffId: String,
        code: String,
        name: String,
    ): List<DocWrite> {
        require(JoinCode.isValid(code)) { "join code shape" }
        val stored = MiffName.stored(name)
        require(MiffName.isValid(stored)) { "name length" }
        return listOf(
            DocWrite("miffs/$miffId", WriteKind.UPDATE, mapOf("name" to stored)),
            DocWrite("joinCodes/$code", WriteKind.UPDATE, mapOf("miffName" to stored)),
        )
    }

    /**
     * Stop sharing (ticket K5): the card says "no" with the date, and the status, the photo and the last known location are deleted in
     * the same lot (4 writes; the rules refuse the stop otherwise: no trace of a position survives a withdrawn consent). The rules read
     * 3 documents for it, so 6 Miffs at most would fit in one lot (18 of the 20 allowed): the app writes one lot per Miff. Only two
     * sub-fields change, so the date and the version of the consent stay as they were.
     */
    fun stopSharing(
        miffId: String,
        uid: String,
    ): List<DocWrite> =
        listOf(
            DocWrite(
                "miffs/$miffId/members/$uid",
                WriteKind.UPDATE,
                mapOf("sharing.enabled" to false, "sharing.revokedAt" to FieldValue.serverTimestamp()),
            ),
            DocWrite("miffs/$miffId/status/$uid", WriteKind.DELETE),
            DocWrite("miffs/$miffId/photos/$uid", WriteKind.DELETE),
            DocWrite("miffs/$miffId/positions/$uid", WriteKind.DELETE),
        )

    /**
     * Resume sharing (ticket K6): a new consent (date and the same [MiffSharingConsent.VERSION] as at the entry, so version 3 from
     * LOC-2a on; the screen shows the same card, with its own opening sentence) and the first status again.
     */
    fun resumeSharing(
        miffId: String,
        uid: String,
        access: LocationAccess,
        deviceId: String,
    ): List<DocWrite> =
        listOf(
            DocWrite(
                "miffs/$miffId/members/$uid",
                WriteKind.UPDATE,
                mapOf(
                    "sharing.enabled" to true,
                    "sharing.consentedAt" to FieldValue.serverTimestamp(),
                    "sharing.consentVersion" to MiffSharingConsent.VERSION,
                    "sharing.revokedAt" to null,
                ),
            ),
            DocWrite("miffs/$miffId/status/$uid", WriteKind.SET, initialStatus(access, deviceId)),
        )

    /**
     * Renewal of an ACTIVE consent under a newer text (rule R-LIEU-3, LOC-2a): the person already shares, nothing stops, nothing is
     * deleted and no status is rewritten. ONE write per Miff, with the four keys of `sharing` exactly (the rules refuse a missing or an
     * unknown key): enabled stays true, the date is the server's, the version is the new one (strictly greater than the old one, fixed
     * width), revokedAt stays empty. The rules read no document for it (0 reads of rules), so one lot can hold every Miff of the person.
     * The other fields of the card (names, photo, phone number, role) are not touched.
     */
    fun renewConsent(
        miffId: String,
        uid: String,
    ): List<DocWrite> =
        listOf(
            DocWrite(
                "miffs/$miffId/members/$uid",
                WriteKind.UPDATE,
                mapOf("sharing" to sharingOn()),
            ),
        )

    /**
     * "Quitter" lot (5 deletions): the card, the status, the photo, the last known location and the index. Used to clean up after a loss of access
     * (ticket 5.7) and by the "Quitter la Miff" button of a member (ticket Q1, MIFF-2). The index is left out only when it was not
     * found ([membershipKey] null): the rules refuse the deletion of an index that does not exist.
     */
    fun leave(
        miffId: String,
        uid: String,
        membershipKey: String?,
    ): List<DocWrite> {
        val writes =
            mutableListOf(
                DocWrite("miffs/$miffId/members/$uid", WriteKind.DELETE),
                DocWrite("miffs/$miffId/status/$uid", WriteKind.DELETE),
                DocWrite("miffs/$miffId/photos/$uid", WriteKind.DELETE),
                DocWrite("miffs/$miffId/positions/$uid", WriteKind.DELETE),
            )
        if (membershipKey != null) writes += DocWrite("users/$uid/memberships/$membershipKey", WriteKind.DELETE)
        return writes
    }

    /**
     * Fallback of the clean-up: the card is already gone (removed), only the index is left; the rules allow deleting it then. A last
     * known location left behind (a removal by an older build) goes with it: its author may always delete it (data-model.md, order of
     * delivery, point 4), and nobody else can once the person is no longer a member.
     */
    fun dropIndex(
        miffId: String,
        uid: String,
        membershipKey: String,
    ): List<DocWrite> =
        listOf(
            DocWrite("miffs/$miffId/positions/$uid", WriteKind.DELETE),
            DocWrite("users/$uid/memberships/$membershipKey", WriteKind.DELETE),
        )

    // --- MIFF-2: renew the code, remove a member, delete the Miff ----------------------------------------------------------

    /**
     * The three writes that change the code of a Miff (ticket N1, data-model.md section 8): the Miff points to [newCode], the new
     * code document exists, the old one is gone. The rules refuse the lot unless all three are there (an old code that still
     * works would let a removed person come back). [name] is the name the Miff has NOW (the code document must carry the same).
     */
    private fun codeChange(
        miffId: String,
        name: String,
        oldCode: String,
        newCode: String,
        oldCodeExists: Boolean,
    ): List<DocWrite> {
        require(JoinCode.isValid(oldCode) && JoinCode.isValid(newCode)) { "join code shape" }
        require(oldCode != newCode) { "the code must change" }
        val writes =
            mutableListOf(
                DocWrite("miffs/$miffId", WriteKind.UPDATE, mapOf("joinCode" to newCode)),
                DocWrite(
                    "joinCodes/$newCode",
                    WriteKind.SET,
                    mapOf("miffId" to miffId, "miffName" to name, "createdAt" to FieldValue.serverTimestamp()),
                ),
            )
        // The rules need the old code gone after the lot; a code document that is already missing cannot be deleted again
        // (its deletion is refused), and it is gone anyway.
        if (oldCodeExists) writes += DocWrite("joinCodes/$oldCode", WriteKind.DELETE)
        return writes
    }

    /** "Créer un nouveau code" (ticket N1): 3 writes in ONE lot. */
    fun renewCode(
        miffId: String,
        name: String,
        oldCode: String,
        newCode: String,
        oldCodeExists: Boolean = true,
    ): List<DocWrite> = codeChange(miffId, name, oldCode, newCode, oldCodeExists)

    /**
     * Removing a member (ticket R2): 7 writes in ONE lot, in this order: the card, the status, the photo and the last known location of
     * the person, then the three writes of the new code. NOTHING else goes in this lot: the rules read up to 18 documents out of the 20
     * Firestore allows (backend/rules-test-report.md, MIFF-2; deleting a position costs no read of its own), and one more document
     * to check could cross the line. The index
     * of the person is not touched (the creator cannot delete it; the person cleans it at their next start).
     */
    fun removeMember(
        miffId: String,
        targetUid: String,
        name: String,
        oldCode: String,
        newCode: String,
        oldCodeExists: Boolean = true,
    ): List<DocWrite> =
        listOf(
            DocWrite("miffs/$miffId/members/$targetUid", WriteKind.DELETE),
            DocWrite("miffs/$miffId/status/$targetUid", WriteKind.DELETE),
            DocWrite("miffs/$miffId/photos/$targetUid", WriteKind.DELETE),
            DocWrite("miffs/$miffId/positions/$targetUid", WriteKind.DELETE),
        ) + codeChange(miffId, name, oldCode, newCode, oldCodeExists)

    /**
     * SESS-1 contract (backend/data-model.md, "Un compte, un seul appareil", cost table): the rules may read 20 documents per lot. With the identifier of the
     * session an "activate sharing + status + position" lot costs 5 reads PER MIFF, a lot of positions 3 per Miff. This app writes ONE Miff per lot for both
     * ([resumeSharing], a status, a position: each its own transaction), far below these limits; they are kept here so that nobody groups more without
     * changing the contract.
     */
    const val MAX_MIFFS_PER_ACTIVATION_LOT = 3
    const val MAX_MIFFS_PER_POSITION_LOT = 6

    /** Places per lot when a Miff is deleted: each deletion costs the rules 2 reads of its own, so 5 stay far from the 20. */
    const val PLACES_PER_LOT = 5

    /**
     * Deleting requests to add a place and, with the last lot, the counter of requests (step 2b of the deletion of a Miff, ticket LIEU-REQ, criterion L3): from
     * 0 to [PLACES_PER_LOT] requests per lot (each deletion costs the rules 2 reads, so 5 stay far from the 20). Never empty.
     */
    fun deletePlaceRequests(
        miffId: String,
        requestIds: List<String>,
        withCounter: Boolean,
    ): List<DocWrite> {
        require(requestIds.size <= PLACES_PER_LOT) { "requests per lot" }
        val writes =
            requestIds.map { DocWrite("miffs/$miffId/placeRequests/$it", WriteKind.DELETE) } +
                if (withCounter) listOf(DocWrite("miffs/$miffId/meta/placeRequests", WriteKind.DELETE)) else emptyList()
        require(writes.isNotEmpty()) { "an empty lot" }
        return writes
    }

    /** Deleting places (step 2 of the deletion of a Miff): from 1 to [PLACES_PER_LOT] per lot. */
    fun deletePlaces(
        miffId: String,
        placeIds: List<String>,
    ): List<DocWrite> {
        require(placeIds.size in 1..PLACES_PER_LOT) { "places per lot" }
        return placeIds.map { DocWrite("miffs/$miffId/places/$it", WriteKind.DELETE) }
    }

    /**
     * The last lot of the deletion of a Miff (ticket S2), up to 8 writes in ONE lot (the contract of the rules counts 7: it leaves the photo
     * out, which this app deletes too; neither costs a read of rules): the Miff, its tombstone `miffsDeleted/{id}` (the id can never be
     * reused), the CURRENT code, and the creator's own status, photo, last known location, card and index. The rules refuse it
     * without the tombstone or without deleting the code. [membershipKey] is null when the index was not found; [codeExists] is
     * false when the code document is already gone (it is then left out: the rules refuse the deletion of a missing code).
     */
    fun finalDelete(
        miffId: String,
        uid: String,
        code: String,
        codeExists: Boolean,
        membershipKey: String?,
    ): List<DocWrite> {
        require(JoinCode.isValid(code)) { "join code shape" }
        val writes =
            mutableListOf(
                DocWrite("miffs/$miffId", WriteKind.DELETE),
                DocWrite("miffsDeleted/$miffId", WriteKind.SET, mapOf("deletedAt" to FieldValue.serverTimestamp())),
            )
        if (codeExists) writes += DocWrite("joinCodes/$code", WriteKind.DELETE)
        writes += DocWrite("miffs/$miffId/status/$uid", WriteKind.DELETE)
        writes += DocWrite("miffs/$miffId/photos/$uid", WriteKind.DELETE)
        writes += DocWrite("miffs/$miffId/positions/$uid", WriteKind.DELETE)
        writes += DocWrite("miffs/$miffId/members/$uid", WriteKind.DELETE)
        if (membershipKey != null) writes += DocWrite("users/$uid/memberships/$membershipKey", WriteKind.DELETE)
        return writes
    }
}
