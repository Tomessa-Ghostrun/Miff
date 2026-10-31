package com.tomessa.miff.domain.miff

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

    override fun toString(): String = "MiffMember(redacted)"
}

/** A place of the Miff, by its name only (creation and editing belong to the Places ticket). */
class MiffPlace(
    val id: String,
    val name: String,
) {
    override fun toString(): String = "MiffPlace(redacted)"
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
    val places: List<MiffPlace>,
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
     * Shared constant with iOS: change it together with the text of `miff_consent_*` (criterion K8). The PM's sentence plus the
     * short list of what the members see (first name, last name, photo, phone number), the reminder about the phone's own
     * permission. A new wording = a new version.
     */
    const val VERSION = "2026-10-v1"

    /** Maximum number of members, creator included (decision QM5): a soft ceiling, shown by the app only. */
    const val SOFT_MEMBER_LIMIT = 15
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
