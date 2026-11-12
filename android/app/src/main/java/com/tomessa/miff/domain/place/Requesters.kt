package com.tomessa.miff.domain.place

import com.tomessa.miff.domain.auth.ProfileDocument
import com.tomessa.miff.domain.profile.Initials

/*
 * PRIVACY, for this file (ticket LIEU-REQ, criterion C2): the creator sees WHO asked for a place (first name and photo or initials, as the card of the
 * member in the Miff shows them). Nothing else of the person is kept (no last name in full, no phone number). It stays in memory, never logged, never
 * in an analytics event; every class that carries it hides it in its toString().
 */

/** The person behind a request, as the creator sees them. */
class RequesterCard(
    val uid: String,
    val firstName: String,
    val initials: String,
    val avatarJpeg: ByteArray?,
) {
    override fun toString(): String = "RequesterCard(redacted)"

    companion object {
        fun of(
            uid: String,
            firstName: String,
            lastName: String,
            avatarJpeg: ByteArray?,
        ): RequesterCard =
            RequesterCard(
                uid = uid,
                firstName = firstName.ifEmpty { lastName },
                initials = Initials.of(firstName, lastName),
                // A photo larger than the rules allow is never trusted (the app would not have written it).
                avatarJpeg = avatarJpeg?.takeIf { it.isNotEmpty() && it.size <= ProfileDocument.MAX_AVATAR_BYTES },
            )
    }
}

/** What is known of the author of a request. */
sealed interface RequesterState {
    /** The card of the member was read: the creator sees who asked. */
    class Known(
        val card: RequesterCard,
    ) : RequesterState {
        override fun toString(): String = "Known(redacted)"
    }

    /** The server says this person is no longer in the Miff (left, removed): their requests are hidden and deleted by the creator (ticket 5.3, L2). */
    data object NotMember : RequesterState

    /** Could not be read (offline, slow): the request is shown without a name, never hidden on a doubt. */
    data object Unknown : RequesterState
}
