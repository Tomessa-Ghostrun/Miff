package com.tomessa.miff.domain.notification

import com.tomessa.miff.domain.auth.ProfileDocument
import com.tomessa.miff.domain.profile.Initials
import com.tomessa.miff.domain.profile.MyProfile

/*
 * CNC-L and CNC-A (ticket docs/tickets/centre-notifications-complet.md, 5.3 and 11, decisions of the PM of 2026-11-19): WHO a line of the centre is about, and so what its round picture is.
 * "The photo says who, the logo says Miff":
 * - a PERSON is her profile photo (the one her loved ones already see in their Miff), her initials when she has none;
 * - the whole Miff (N3, N4) is the LOGO of Miff;
 * - MY OWN reminders (N1, N2, N6, N7) are MY photo (my initials when I have none);
 * - somebody who cannot be found (left the Miff, deleted her account, not read yet and unreachable) is the LOGO with the words "Un proche": never an empty round, never a "?".
 * Pure rules, no Android class, tested on the JVM. PRIVACY: a photo is never stored by the centre: it is read from memory when the line is drawn (the Feed's members, the person's own
 * profile, or one reading of the member's card), and goes with the session. A photo larger than the rules allow is never trusted (the initials stand in, it is not decoded).
 */

/** The round picture of a line. */
sealed interface CenterAvatar {
    /** The logo of Miff on its blue round: the whole Miff, or nobody who can be named. */
    data object Logo : CenterAvatar

    /** The photo is not known YET (a reading is under way): a neutral round, no letters, no logo (so that nothing flickers). */
    data object Placeholder : CenterAvatar

    /** The initials of a person without a photo (or whose photo cannot be read). */
    class Initials(
        val text: String,
    ) : CenterAvatar {
        override fun toString(): String = "Initials(redacted)"
    }

    /** The photo (square JPEG of 20 000 bytes at most); [initials] stand in if it cannot be decoded. */
    class Photo(
        val jpeg: ByteArray,
        val initials: String,
    ) : CenterAvatar {
        override fun toString(): String = "Photo(redacted)"
    }
}

/** Who a line is about. */
sealed interface CenterSubject {
    /** The whole Miff (N3, N4): the logo. */
    data object WholeMiff : CenterSubject

    /** Me (N1, N2, N6, N7): my photo. */
    data object Myself : CenterSubject

    /** A loved one of the Miff [miffId]: [uid]. */
    class Person(
        val miffId: String,
        val uid: String,
    ) : CenterSubject {
        override fun toString(): String = "Person(redacted)"
    }

    /** Nobody who can be named (a line keyed by a Miff and not by a person): the logo. */
    data object Nobody : CenterSubject
}

/** What the app knows of a member of a Miff, from memory (never from the centre). */
class ActorFace(
    val firstName: String,
    val initials: String,
    val avatarJpeg: ByteArray?,
) {
    override fun toString(): String = "ActorFace(redacted)"
}

/** The answer of the memory about one member. */
sealed interface FaceLookup {
    /** The card of the member is known. */
    class Known(
        val face: ActorFace,
    ) : FaceLookup {
        override fun toString(): String = "Known(redacted)"
    }

    /** The member is not in the Miff (left, removed, account deleted, access lost): the logo and "Un proche". */
    data object Gone : FaceLookup

    /** A reading is under way or has not started: the neutral round. */
    data object Pending : FaceLookup

    /** The reading failed (offline, too slow): not found; tried again later, never in a loop. */
    data object Failed : FaceLookup
}

/** What is drawn and written for a line: the picture, and the first name to write ("Un proche" when null). */
class CenterLook(
    val avatar: CenterAvatar,
    /** The first name of the person the line is about, resolved NOW from the same card as the picture; null = say "Un proche". */
    val name: String?,
) {
    override fun toString(): String = "CenterLook(redacted)"
}

object CenterAvatars {
    /** The photo of a card, or its initials: a photo larger than the rules allow, or empty, is never used. */
    private fun faceAvatar(
        jpeg: ByteArray?,
        initials: String,
    ): CenterAvatar {
        val usable = jpeg?.takeIf { it.isNotEmpty() && it.size <= ProfileDocument.MAX_AVATAR_BYTES }
        return if (usable != null) CenterAvatar.Photo(usable, initials) else CenterAvatar.Initials(initials)
    }

    /**
     * The picture and the name of a line about [subject].
     * [lookup] answers about a member of a Miff; [me] is the profile of the person signed in (null while it is not read yet); [storedName] is the first name the push wrote in the line
     * (N9, N10, N11 from a push; null for a line the journal brought).
     * Name and picture come from the SAME card: a profile changed meanwhile changes both together (CNC-10). The stored name only stands in while the card is not known YET or cannot
     * be read: never for somebody who is gone.
     */
    fun look(
        subject: CenterSubject,
        lookup: (miffId: String, uid: String) -> FaceLookup,
        me: MyProfile?,
        storedName: String? = null,
    ): CenterLook =
        when (subject) {
            CenterSubject.WholeMiff -> {
                CenterLook(CenterAvatar.Logo, null)
            }

            CenterSubject.Nobody -> {
                // No identity to resolve (a line keyed by the Miff, no author): the logo; the name the push wrote, if any, is the only one there is.
                CenterLook(CenterAvatar.Logo, storedName)
            }

            CenterSubject.Myself -> {
                CenterLook(
                    if (me == null) {
                        CenterAvatar.Placeholder
                    } else {
                        faceAvatar(me.avatarJpeg, Initials.of(me.firstName, me.lastName))
                    },
                    null,
                )
            }

            is CenterSubject.Person -> {
                when (val found = lookup(subject.miffId, subject.uid)) {
                    is FaceLookup.Known -> {
                        CenterLook(
                            faceAvatar(found.face.avatarJpeg, found.face.initials),
                            found.face.firstName.ifBlank { null },
                        )
                    }

                    FaceLookup.Gone -> {
                        CenterLook(CenterAvatar.Logo, null)
                    }

                    FaceLookup.Pending -> {
                        CenterLook(CenterAvatar.Placeholder, storedName)
                    }

                    FaceLookup.Failed -> {
                        // Not found for now: the name the push wrote still tells who it is (its first letter, no photo); without it, the logo and "Un proche".
                        if (storedName != null) {
                            CenterLook(CenterAvatar.Initials(Initials.of(storedName, "")), storedName)
                        } else {
                            CenterLook(CenterAvatar.Logo, null)
                        }
                    }
                }
            }
        }

    /**
     * Who a line of the centre is about, from its type and its key. N8 and C1 are decided by the caller (the requester, the member the sentence is about): here they are "nobody"
     * when it does not know.
     */
    fun subjectOf(item: NotificationItem): CenterSubject =
        when (item.type) {
            NotificationType.N1, NotificationType.N2, NotificationType.N6, NotificationType.N7 -> {
                CenterSubject.Myself
            }

            NotificationType.N3, NotificationType.N4 -> {
                CenterSubject.WholeMiff
            }

            NotificationType.N9 -> {
                person(item.miffId, CircleNewsKeys.actorOf(item.id))
            }

            NotificationType.N10, NotificationType.N11 -> {
                person(item.miffId, CircleAlertKeys.actorOf(item.id))
            }

            NotificationType.N8, NotificationType.C1 -> {
                CenterSubject.Nobody
            }
        }

    /** A person of a Miff, or nobody when the line does not say. */
    fun person(
        miffId: String?,
        uid: String?,
    ): CenterSubject = if (miffId != null && uid != null) CenterSubject.Person(miffId, uid) else CenterSubject.Nobody
}
