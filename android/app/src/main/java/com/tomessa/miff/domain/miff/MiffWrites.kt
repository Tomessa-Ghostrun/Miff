package com.tomessa.miff.domain.miff

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.tomessa.miff.domain.auth.ProfileDocument

/*
 * SECURITY, for this file: the writes below are the contract with backend/firestore.rules. Every key of a document is always
 * written (the rules refuse a missing key and an unknown one); the dates are the server's (the rules refuse a date of the
 * phone); no coordinate, no address, no place name is ever written by a Miff screen (ticket Miff criterion V1).
 */

enum class WriteKind { SET, UPDATE, DELETE }

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
    fun initialStatus(access: LocationAccess): Map<String, Any?> =
        mapOf(
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
        )

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
            DocWrite("miffs/$miffId/status/$uid", WriteKind.SET, initialStatus(access)),
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
    ): List<DocWrite> {
        require(JoinCode.isValid(code)) { "join code shape" }
        return listOf(
            DocWrite("miffs/$miffId/members/$uid", WriteKind.SET, card(MiffRole.MEMBER, profile, joinCode = code)),
            DocWrite("users/$uid/memberships/$membershipKey", WriteKind.SET, index(miffId)),
            DocWrite("miffs/$miffId/status/$uid", WriteKind.SET, initialStatus(access)),
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
     * Stop sharing (ticket K5): the card says "no" with the date, and the status and the photo are deleted in the same lot (the
     * rules refuse the stop otherwise: no trace of a position survives a withdrawn consent). Only two sub-fields change, so
     * the date and the version of the first consent stay as they were.
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
        )

    /** Resume sharing (ticket K6): a new consent (date and version) and the first status again. */
    fun resumeSharing(
        miffId: String,
        uid: String,
        access: LocationAccess,
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
            DocWrite("miffs/$miffId/status/$uid", WriteKind.SET, initialStatus(access)),
        )

    /**
     * "Quitter" lot (4 deletions): the card, the status, the photo and the index. Used to clean up after a loss of access
     * (ticket 5.7); the "Quitter" button of MIFF-2 uses the same lot.
     */
    fun leave(
        miffId: String,
        uid: String,
        membershipKey: String,
    ): List<DocWrite> =
        listOf(
            DocWrite("miffs/$miffId/members/$uid", WriteKind.DELETE),
            DocWrite("miffs/$miffId/status/$uid", WriteKind.DELETE),
            DocWrite("miffs/$miffId/photos/$uid", WriteKind.DELETE),
            DocWrite("users/$uid/memberships/$membershipKey", WriteKind.DELETE),
        )

    /** Fallback of the clean-up: the card is already gone (removed), only the index is left; the rules allow deleting it then. */
    fun dropIndex(
        uid: String,
        membershipKey: String,
    ): List<DocWrite> = listOf(DocWrite("users/$uid/memberships/$membershipKey", WriteKind.DELETE))
}
