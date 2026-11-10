package com.tomessa.miff.domain.profile

import com.tomessa.miff.domain.auth.ProfileDocument
import com.tomessa.miff.domain.miff.MiffMember

/*
 * SECURITY, PRIVACY and COST, for this file (carousel of Miffs of the Profile tab, decision of the PM of 2026-11-10):
 * - the faces (photo or initials) of the members are personal data: they stay in memory, for the session of the view model, never logged,
 *   never in an analytics event; every class that carries one hides it in its toString();
 * - only what the card draws is kept (the uid, the initials, the stored photo of 20 000 bytes at most): no phone number, no name in full;
 * - COST: the faces of a Miff cost ONE reading of `miffs/{id}/members` (one Firestore reading per member card, so 15 at most). [MiffFacesBook]
 *   decides when a reading is needed: never for the Miff the Feed already read (reused as of the last visit to the Feed), at most once per Miff and per 5 minutes for the
 *   others, never in a loop after a failure. No listener is ever attached for the faces.
 */

/** The face of one member on a card of a Miff: the stored photo when there is one, else the initials. */
class MemberFace(
    val uid: String,
    val initials: String,
    val avatarJpeg: ByteArray?,
) {
    override fun toString(): String = "MemberFace(redacted)"

    companion object {
        /** What the card keeps of a member. A photo larger than the rules allow is never trusted (it would not have been written by the app). */
        fun of(member: MiffMember): MemberFace = of(member.uid, member.firstName, member.lastName, member.avatarJpeg)

        fun of(
            uid: String,
            firstName: String,
            lastName: String,
            avatarJpeg: ByteArray?,
        ): MemberFace =
            MemberFace(
                uid = uid,
                initials = Initials.of(firstName, lastName),
                avatarJpeg = avatarJpeg?.takeIf { it.isNotEmpty() && it.size <= ProfileDocument.MAX_AVATAR_BYTES },
            )
    }
}

/** How many faces a card draws, and the "+N" that stands for the others. */
data class FacesLayout(
    val shown: Int,
    /** The "+N" of the others; 0 when everybody is drawn. */
    val more: Int,
) {
    companion object {
        /** Up to this many circles in a row, the "+N" one included. */
        const val MAX_CIRCLES = 5

        /**
         * Up to 5 members: everybody, no "+N". More than 5: 4 faces, then "+N" in the 5th circle (so N is at least 2: "+1" would hide one
         * person to save one circle, which is never worth it).
         */
        fun of(memberCount: Int): FacesLayout {
            val count = memberCount.coerceAtLeast(0)
            return if (count <=
                MAX_CIRCLES
            ) {
                FacesLayout(count, 0)
            } else {
                FacesLayout(MAX_CIRCLES - 1, count - (MAX_CIRCLES - 1))
            }
        }
    }
}

/** What the card of one Miff knows about its members. */
sealed interface CardFaces {
    /** Nothing is known yet, a reading is under way or about to start: the card shows its name and calm placeholders. */
    data object Loading : CardFaces

    /** The members could not be read (offline, slow): the card shows its name only, no count, no error text; it tries again later. */
    data object Unavailable : CardFaces

    /** [count] members in all; [faces] are the first of them, in the order of the Miff screen (the creator first, then by arrival). */
    class Ready(
        val count: Int,
        val faces: List<MemberFace>,
    ) : CardFaces {
        override fun toString(): String = "Ready(redacted)"
    }
}

/** The members the Feed has read for a Miff: what the Profile reuses for the card of that Miff (no second reading). */
class ReadMembers(
    val miffId: String,
    val members: List<MiffMember>,
) {
    override fun toString(): String = "ReadMembers(redacted)"
}

/**
 * Who knows what about the faces of each Miff, and when a reading is needed. Plain Kotlin, no Android, no clock but the one it is given:
 * the rules of the cost live here and are tested here. Used from one thread (the main one) by the view model.
 */
class MiffFacesBook(
    private val nowMillis: () -> Long,
) {
    private class Entry(
        val faces: CardFaces,
        val source: Source,
        val atMillis: Long,
        val stale: Boolean = false,
        val reading: Boolean = false,
    )

    private enum class Source { FEED, READ, FAILED }

    private val entries = mutableMapOf<String, Entry>()

    /** What each card shows now. */
    fun view(): Map<String, CardFaces> = entries.mapValues { it.value.faces }

    /**
     * The Feed has read these members: the card of this Miff draws them. FREE (no reading). It also clears a "stale" mark: what the Feed has just
     * heard (its listener is attached ONLY while the Feed is on screen) is at least as recent as anything we would read now.
     *
     * KNOWN LIMIT, accepted for the cost: once the Feed is left, nothing updates this entry, so the card shows the members AS THEY WERE at the last
     * visit to the Feed (somebody who joined or left meanwhile appears when the person goes back to the Feed, which listens again and seeds again). Two
     * cases are covered without any reading: the person's OWN face ([replaceFace], after "Mes informations") and the opening of the screen of the Miff
     * ([invalidate], which costs one reading at the next appearance of the card).
     */
    fun seedFromFeed(read: ReadMembers) {
        val reading = entries[read.miffId]?.reading == true
        entries[read.miffId] = Entry(ready(read.members), Source.FEED, nowMillis(), reading = reading)
    }

    /**
     * Does the card of [miffId], which has just appeared, need a reading of the server? Yes when nothing is known, when the Miff was opened since
     * (members may have changed: [invalidate]), when what we read is older than [TTL_MILLIS], or when the last attempt failed more than
     * [RETRY_MILLIS] ago. No while a reading is under way, and no for what the Feed gave (as of the last visit to the Feed, never aged: see the known limit of [seedFromFeed]).
     */
    fun needsReading(miffId: String): Boolean {
        val entry = entries[miffId] ?: return true
        if (entry.reading) return false
        val age = nowMillis() - entry.atMillis
        return when {
            entry.stale -> true
            entry.source == Source.FEED -> false
            entry.source == Source.FAILED -> age >= RETRY_MILLIS
            else -> age >= TTL_MILLIS
        }
    }

    /** A reading starts: the card keeps what it showed (or the placeholders if nothing), and no second reading starts for it. */
    fun readingStarted(miffId: String) {
        val entry = entries[miffId]
        entries[miffId] =
            if (entry == null) {
                Entry(CardFaces.Loading, Source.READ, nowMillis(), reading = true)
            } else {
                Entry(entry.faces, entry.source, entry.atMillis, entry.stale, reading = true)
            }
    }

    fun readingSucceeded(
        miffId: String,
        members: List<MiffMember>,
    ) {
        entries[miffId] = Entry(ready(members), Source.READ, nowMillis())
    }

    /** What was drawn stays drawn (a photo that was there does not vanish because the network blinked); only a card with nothing becomes "Unavailable". */
    fun readingFailed(miffId: String) {
        val previous = entries[miffId]
        val keep = previous?.faces?.takeIf { it is CardFaces.Ready } ?: CardFaces.Unavailable
        entries[miffId] = Entry(keep, Source.FAILED, nowMillis())
    }

    /** The person opened the screen of this Miff: they may have removed somebody, so the next appearance of the card reads again. */
    fun invalidate(miffId: String) {
        val entry = entries[miffId] ?: return
        entries[miffId] = Entry(entry.faces, entry.source, entry.atMillis, stale = true, reading = entry.reading)
    }

    /**
     * The person changed their own photo or name (Réglages > Mes informations): their face is replaced on every card that draws it. NO reading: the
     * new face is already on the phone. The count never changes (a face that is not drawn, beyond the first five, is left alone).
     */
    fun replaceFace(face: MemberFace) {
        for ((miffId, entry) in entries.toMap()) {
            val ready = entry.faces as? CardFaces.Ready ?: continue
            if (ready.faces.none { it.uid == face.uid }) continue
            val faces = ready.faces.map { if (it.uid == face.uid) face else it }
            entries[miffId] =
                Entry(CardFaces.Ready(ready.count, faces), entry.source, entry.atMillis, entry.stale, entry.reading)
        }
    }

    /** A Miff that is no longer in the list (left, deleted, access lost) takes its faces with it. */
    fun retainOnly(miffIds: Set<String>) {
        entries.keys.retainAll(miffIds)
    }

    private fun ready(members: List<MiffMember>): CardFaces =
        CardFaces.Ready(
            count = members.size,
            // The card never draws more than 5 circles: only those faces are kept.
            faces = members.take(FacesLayout.MAX_CIRCLES).map { MemberFace.of(it) },
        )

    companion object {
        /** What was read is reused this long: opening the Profile again, or swiping back to a card, reads nothing within it. */
        const val TTL_MILLIS = 5 * 60 * 1000L

        /** After a failure, the next attempt waits at least this long (never a loop; the card shows nothing alarming meanwhile). */
        const val RETRY_MILLIS = 30 * 1000L
    }
}
