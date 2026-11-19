package com.tomessa.miff.data.notification

import com.tomessa.miff.data.session.SessionTeardown
import com.tomessa.miff.domain.auth.ProfileDocument
import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.notification.ActorFace
import com.tomessa.miff.domain.notification.CircleNewsParser
import com.tomessa.miff.domain.notification.FaceLookup
import com.tomessa.miff.domain.profile.Initials
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/*
 * CNC-A (ticket docs/tickets/centre-notifications-complet.md, 5.1, 6 and 8.5): the faces the centre of notifications draws in its lines. A face is the photo of profile of a member (the one
 * the other members of the Miff already see), or his initials, and his first name; they come from the SAME card, so a name and a photo never disagree.
 *
 * PRIVACY: IN MEMORY ONLY, for the session (this class is part of the teardown): never written in a file (the centre itself stores no photo), never logged, never in an event. Only what a
 * line draws is kept: the first name (20 characters at most), the initials, the photo of 20 000 bytes at most.
 * COST: 0 reading for the Miff the Feed already read (its members are handed over by [seedFromFeed]) and for the person himself; otherwise ONE `get` of `miffs/{miffId}/members/{uid}` per
 * distinct member and per session (re-read after 5 minutes at most), a dozen at the most each time the centre opens, and NEVER in a loop after a failure (a failed card is not asked again
 * for 30 seconds, and only when the centre is opened again). No listener, ever.
 */

/** What one reading of the card of a member gave. */
sealed interface FaceRead {
    class Found(
        val face: ActorFace,
    ) : FaceRead {
        override fun toString(): String = "Found(redacted)"
    }

    /** The card does not exist (the member left, was removed, deleted his account), or the rules refuse it (the Miff is gone for this person). */
    data object Gone : FaceRead

    /** Offline, too slow, anything else: not found for now. */
    data object Failed : FaceRead
}

/** The one reading behind a face (a double in tests). */
interface MemberFaceReader {
    suspend fun read(
        miffId: String,
        uid: String,
    ): FaceRead
}

/** The face of a member as the centre writes it: the first name (cleaned, 20 characters at most), the initials, the photo when it is within the rules. */
internal fun actorFaceOf(
    firstName: String,
    lastName: String,
    avatarJpeg: ByteArray?,
): ActorFace =
    ActorFace(
        firstName = CircleNewsParser.cleanFirstName(firstName.ifEmpty { lastName }).orEmpty(),
        initials = Initials.of(firstName, lastName),
        avatarJpeg = avatarJpeg?.takeIf { it.isNotEmpty() && it.size <= ProfileDocument.MAX_AVATAR_BYTES },
    )

/**
 * The faces known to the centre, by Miff and member, in memory for the session. [lookup] never reads: it answers from memory; [ensure] reads what is missing or too old (a dozen at the
 * most per call) through [reader]. The Feed hands over the members of the Miff it shows ([seedFromFeed]), which also tells who is NOT in that Miff any more.
 */
class MemberFaceDirectory(
    private val reader: MemberFaceReader,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : SessionTeardown {
    private class Entry(
        val result: FaceLookup,
        val source: Source,
        val atMillis: Long,
    )

    private enum class Source { FEED, READ, FAILED }

    private val mutex = Mutex()

    /** Changes each time something is learned: the screen that draws the faces recomputes. Holds only an opaque version. */
    private val mutableVersion = MutableStateFlow(0)
    val version: StateFlow<Int> = mutableVersion.asStateFlow()

    private var entries: Map<String, Entry> = emptyMap()

    /** The members of each Miff the Feed read, complete: a member who is not in the list of a Miff the Feed read is gone from it. */
    private var feedRosters: Map<String, Set<String>> = emptyMap()

    /** Goes up at each end of a session: an answer awaited across it belongs to the previous person. */
    @Volatile private var epoch = 0

    /** What memory knows about [uid] in [miffId], right now. Never reads. */
    @Synchronized
    fun lookup(
        miffId: String,
        uid: String,
    ): FaceLookup {
        val entry = entries[key(miffId, uid)]
        return when {
            entry != null -> entry.result

            // A Miff the Feed read completely: somebody who is not in it is not a member (any more).
            feedRosters[miffId]?.contains(uid) == false -> FaceLookup.Gone

            else -> FaceLookup.Pending
        }
    }

    /**
     * The Feed read the members of [miffId]: their faces are known, FREE (no reading). As of the last visit to the Feed (the same accepted limit as the carousel of Miffs of the Profile:
     * somebody who left meanwhile shows until the Feed listens again).
     */
    @Synchronized
    fun seedFromFeed(
        miffId: String,
        members: List<MiffMember>,
    ) {
        val now = nowMillis()
        val seeded =
            members.associate {
                key(miffId, it.uid) to
                    Entry(
                        FaceLookup.Known(actorFaceOf(it.firstName, it.lastName, it.avatarJpeg)),
                        Source.FEED,
                        now,
                    )
            }
        entries = entries.filterKeys { it.substringBefore('|') != miffId || it in seeded } + seeded
        feedRosters = feedRosters + (miffId to members.map { it.uid }.toSet())
        mutableVersion.value++
    }

    /**
     * Reads the faces of [wanted] (Miff, member) that memory does not have or that are too old, at most [MAX_READS_PER_CALL] of them (the rest waits for the next opening). One at a time:
     * no burst. Returns when everything asked is settled. Never throws.
     */
    suspend fun ensure(wanted: Collection<Pair<String, String>>) {
        val startedIn = epoch
        val todo = synchronized(this) { wanted.distinct().filter { needsReading(it.first, it.second) }.take(MAX_READS_PER_CALL) }
        for ((miffId, uid) in todo) {
            mutex.withLock {
                if (epoch != startedIn) return
                val read = reader.read(miffId, uid)
                if (epoch != startedIn) return
                record(miffId, uid, read)
            }
        }
    }

    private fun needsReading(
        miffId: String,
        uid: String,
    ): Boolean {
        val entry = entries[key(miffId, uid)]
        if (entry == null) {
            // Somebody who is not in a Miff the Feed read needs no reading: he is gone.
            return feedRosters[miffId]?.contains(uid) != false
        }
        val age = nowMillis() - entry.atMillis
        return when (entry.source) {
            Source.FEED -> false
            Source.READ -> age >= TTL_MILLIS
            Source.FAILED -> age >= RETRY_MILLIS
        }
    }

    @Synchronized
    private fun record(
        miffId: String,
        uid: String,
        read: FaceRead,
    ) {
        val now = nowMillis()
        val entry =
            when (read) {
                is FaceRead.Found -> Entry(FaceLookup.Known(read.face), Source.READ, now)
                FaceRead.Gone -> Entry(FaceLookup.Gone, Source.READ, now)
                // What was drawn stays drawn when a second reading fails (a photo does not vanish because the network blinked).
                FaceRead.Failed -> {
                    val previous = entries[key(miffId, uid)]
                    if (previous != null && previous.result is FaceLookup.Known) {
                        Entry(previous.result, Source.FAILED, now)
                    } else {
                        Entry(FaceLookup.Failed, Source.FAILED, now)
                    }
                }
            }
        entries = entries + (key(miffId, uid) to entry)
        mutableVersion.value++
    }

    /** A Miff that is gone (left, deleted, access lost) takes its faces with it. */
    @Synchronized
    fun retainOnly(miffIds: Set<String>) {
        entries = entries.filterKeys { it.substringBefore('|') in miffIds }
        feedRosters = feedRosters.filterKeys { it in miffIds }
    }

    override suspend fun stop() {
        synchronized(this) {
            epoch++
            entries = emptyMap()
            feedRosters = emptyMap()
            mutableVersion.value++
        }
    }

    companion object {
        /** What was read is reused this long (the same as the faces of the carousel of the Profile). */
        const val TTL_MILLIS = 5 * 60 * 1000L

        /** After a failure, the next attempt waits at least this long, and only at a new opening of the centre. */
        const val RETRY_MILLIS = 30 * 1000L

        /** "A dozen at the most" each time the centre opens (ticket 8.5, criterion CNC-19). */
        const val MAX_READS_PER_CALL = 12

        fun key(
            miffId: String,
            uid: String,
        ): String = "$miffId|$uid"
    }
}
