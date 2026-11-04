package com.tomessa.miff.domain.miff

import com.tomessa.miff.domain.place.Place

/*
 * SECURITY, for this file: names, code, photos and phone numbers are personal data or secrets. Every class that carries one
 * has a toString() that shows nothing, so that a log line or a crash report can never leak it.
 */

enum class MiffRole { OWNER, MEMBER }

/** The Miff as `miffs/{id}` holds it. */
class MiffDetails(
    val id: String,
    val name: String,
    val joinCode: String,
    val ownerUid: String,
) {
    override fun toString(): String = "MiffDetails(redacted)"
}

/** A line of the list of loved ones (copy of the profile kept in `members/{uid}`). No phone number: it is not shown here. */
class MiffMember(
    val uid: String,
    val firstName: String,
    val lastName: String,
    val avatarJpeg: ByteArray?,
    val role: MiffRole,
    val sharingEnabled: Boolean,
) {
    val displayName: String get() = "$firstName $lastName".trim()

    /** How the creator's questions name this person ("Retirer {prénom}..."): the first name, or the last name if the first has nothing visible. */
    val callName: String get() = firstName.ifEmpty { lastName }

    override fun toString(): String = "MiffMember(redacted)"
}

/** One document of `users/{uid}/memberships`: [key] is the id of the document, [miffId] the field that says which Miff. */
data class MembershipRef(
    val key: String,
    val miffId: String,
)

/** A Miff of the person, for the Feed pastille and the Profile card. */
class MiffSummary(
    val id: String,
    val name: String,
) {
    override fun toString(): String = "MiffSummary(redacted)"
}

/** Everything the Miff screen shows, kept together so that the three listeners give one coherent picture. */
class MiffSnapshot(
    val miff: MiffDetails,
    val members: List<MiffMember>,
    val places: List<Place>,
    /** True while what is shown comes from the phone's cache only (no answer of the server yet, or offline). */
    val fromCache: Boolean,
) {
    override fun toString(): String = "MiffSnapshot(redacted)"
}

/** What the observation of a Miff tells the screen. A network problem is NEVER [AccessLost] (ticket 5.7, criterion D3). */
sealed interface MiffObservation {
    data object Loading : MiffObservation

    data class Data(
        val snapshot: MiffSnapshot,
    ) : MiffObservation

    /** The server said "no" (removed) or "this Miff does not exist" (deleted). */
    data object AccessLost : MiffObservation

    /** Something else went wrong (not a network cut: the listeners wait for the network by themselves). */
    data object Failed : MiffObservation
}

/** Version of the text shown before "Rejoindre et partager" / "Créer et partager" / "Reprendre le partage" (rules: 1 to 20 chars). */
object MiffSharingConsent {
    /**
     * Shared constant with iOS: change it together with the text of `miff_consent3_*` (criterion K8). Version "2026-11-v03" (LOC-2a):
     * the text of docs/legal/consentement-localisation-v3.md, option A (decision of the PM of 2026-11-04), which says that Miff keeps
     * the LAST KNOWN LOCATION with the time of the measure, that the members who share too can see it, that Miff uses the phone's
     * location even when the app is closed, and that it is erased when the person stops. A new wording = a new version; the number NN
     * never starts again from zero and the format is `AAAA-MM-vNN` of FIXED WIDTH (the rules compare versions as strings: backend/
     * firestore.rules, isConsentVersion and isPositionConsentVersion). ("2026-10-v1" and "2026-10-v2" are the older texts, which say
     * nothing about a position.)
     */
    const val VERSION = "2026-11-v03"

    /** Maximum number of members, creator included (decision QM5): a soft ceiling, shown by the app only. */
    const val SOFT_MEMBER_LIMIT = 15

    /** The format the rules require of a version they are asked to renew to: year, month, "v", two digits (leading zero). */
    private val FIXED_WIDTH = Regex("^20[0-9]{2}-(0[1-9]|1[0-2])-v[0-9]{2}$")

    /** The two texts written before the fixed width existed (October 2026): still read as OLD versions, never written again. */
    private val LEGACY = Regex("^20[0-9]{2}-(0[1-9]|1[0-2])-v[0-9]+$")

    /** What `isPositionConsentVersion` of the rules accepts: NN from 03 to 99. Only such a text covers the last known location. */
    private val COVERS_POSITION = Regex("^20[0-9]{2}-(0[1-9]|1[0-2])-v(0[3-9]|[1-9][0-9])$")

    fun isFixedWidth(version: String): Boolean = FIXED_WIDTH.matches(version)

    /** Does the text of this version cover the last known location? (LOC-2b writes a position only under such a version.) */
    fun coversPosition(version: String?): Boolean = version != null && COVERS_POSITION.matches(version)

    /**
     * A member who shares under an OLDER text has to see the renewal screen (R-LIEU-3 of the rules: active to active, the new
     * version strictly greater). A version that has none of the known shapes is not touched (the rules would refuse the renewal:
     * the comparison is made on strings of the same shape), and a version newer than this build's is never "renewed" backwards.
     */
    fun isRenewalDue(stored: String?): Boolean =
        stored != null && (FIXED_WIDTH.matches(stored) || LEGACY.matches(stored)) && stored < VERSION
}

/** What the phone's permission to locate says, written in the first status (ticket 5.4, step 7). */
enum class LocationAccess(
    val value: String,
) {
    BACKGROUND("background"),
    FOREGROUND("foreground"),
    DENIED("denied"),
    UNKNOWN("unknown"),
}
