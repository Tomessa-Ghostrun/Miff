package com.tomessa.miff.data.feed

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.tomessa.miff.data.session.SessionTeardown
import com.tomessa.miff.domain.update.SharedPhoto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/*
 * PRIVACY and COST, for this file (FEED-4):
 * - The Feed READS a photo and never writes, nor deletes one (FeedPrivacyRuleTest). A refusal of the rules is NEVER taken for "expired": it also means
 *   "this photo has a place and you have not published yours yet" (R-PHOTO-LOC, reciprocity), so nothing is ever deleted on a permission-denied
 *   (backend/data-model.md, section 8, Feed).
 * - ONE `get` of a document (never a list), when the card comes on screen and only if the status says `hasPhoto` and the last update is less than 25 h old
 *   (the caller decides). A photo already read is NOT downloaded again: it is kept in memory (at most [MAX_ENTRIES] photos, 150 KB each at the most) and
 *   shown again as long as the status has not changed. Nothing is saved on the disk by this code (the SDK of Firestore keeps its own copy, as for every
 *   document: the purge of the cache at sign-out covers it, a known limit of the data model).
 * - What is read is cleaned before use ([SharedPhoto.fromServer]). The photo, its place and its times are never logged nor sent anywhere.
 */

/** What reading the photo of a member said. */
sealed interface PhotoLookup {
    class Found(
        val photo: SharedPhoto,
    ) : PhotoLookup {
        override fun toString(): String = "Found(redacted)"
    }

    /** No photo (not there, expired, not valid): the card shows nothing, no message. */
    data object Missing : PhotoLookup

    /** The rules refused: a photo with a place is only read by a member who published their own position. A calm sentence, never "error". */
    data object NeedsOwnPosition : PhotoLookup

    /** The server could not be reached (and the phone had no copy). Nothing is shown, no message; the next appearance of the card tries again. */
    data object Offline : PhotoLookup
}

interface PhotoReader {
    /**
     * The photo of [uid] in [miffId]. [lastUpdateAtMillis] is the last update of the status the Feed has just read: a copy made for the same value is
     * shown again with no reading.
     */
    suspend fun read(
        miffId: String,
        uid: String,
        lastUpdateAtMillis: Long,
    ): PhotoLookup
}

/** The photos read, in memory, for the session. The photo of the author is put here at the moment of the publication, so that no one re-reads it. */
class PhotoCache(
    private val maxEntries: Int = MAX_ENTRIES,
) : SessionTeardown {
    private class Entry(
        val photo: SharedPhoto,
        /** The `lastUpdateAt` of the status this copy was read for; null for the photo of the author, valid until it expires. */
        val forLastUpdateMillis: Long?,
    )

    private val entries =
        object : LinkedHashMap<String, Entry>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > maxEntries
        }

    private val missing =
        object : LinkedHashMap<String, Long>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
                size > maxEntries * MISSING_FACTOR
        }

    @Synchronized
    fun get(
        miffId: String,
        uid: String,
        lastUpdateAtMillis: Long?,
        nowMillis: Long,
    ): SharedPhoto? {
        val key = key(miffId, uid)
        val entry = entries[key] ?: return null
        if (entry.photo.isExpired(nowMillis)) {
            entries.remove(key)
            return null
        }
        val current = entry.forLastUpdateMillis == null || entry.forLastUpdateMillis == lastUpdateAtMillis
        return entry.photo.takeIf { current }
    }

    /** The server said there is nothing to show for this status (absent, expired, not valid): not asked again for the same `lastUpdateAt` (review C8). */
    @Synchronized
    fun putMissing(
        miffId: String,
        uid: String,
        lastUpdateAtMillis: Long,
    ) {
        missing[key(miffId, uid)] = lastUpdateAtMillis
    }

    @Synchronized
    fun isMissing(
        miffId: String,
        uid: String,
        lastUpdateAtMillis: Long,
    ): Boolean = missing[key(miffId, uid)] == lastUpdateAtMillis

    @Synchronized
    fun putRead(
        miffId: String,
        uid: String,
        photo: SharedPhoto,
        lastUpdateAtMillis: Long,
    ) {
        missing.remove(key(miffId, uid))
        entries[key(miffId, uid)] = Entry(photo, lastUpdateAtMillis)
    }

    /** The photo I just published: shown on my card with no reading of the server. */
    @Synchronized
    fun putOwn(
        miffId: String,
        uid: String,
        photo: SharedPhoto,
    ) {
        missing.remove(key(miffId, uid))
        entries[key(miffId, uid)] = Entry(photo, null)
    }

    @Synchronized
    fun remove(
        miffId: String,
        uid: String,
    ) {
        entries.remove(key(miffId, uid))
        missing.remove(key(miffId, uid))
    }

    @Synchronized
    fun clear() {
        entries.clear()
        missing.clear()
    }

    /** End of the session: nothing of the photos stays in memory for the next person. */
    override suspend fun stop() = clear()

    private fun key(
        miffId: String,
        uid: String,
    ) = "$miffId/$uid"

    private companion object {
        const val MAX_ENTRIES = 16
        const val MISSING_FACTOR = 4
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
    }
}

/** Have I published my own position in this Miff? The truth is the document `positions/{moi}` on the server (it is what the rules of the photos look at). */
enum class OwnPosition {
    Published,
    NotPublished,

    /** Not known (offline, refused, not signed in, still being read): NEVER taken for "not published". */
    Unknown,
}

/**
 * What a refusal of the rules means for the card (backend/data-model.md: "un refus de lecture est ambigu"). It is NEVER a reason to delete anything. The refusal
 * has several causes (the photo expired, the sharing stopped, or the reciprocity of the place), so the calm sentence "Partage ta position pour voir cette photo."
 * is shown ONLY when it is KNOWN that I have not published my own position. Published, or not known (loading, offline): nothing is shown, never the sentence.
 */
internal fun refusalLookup(ownPosition: OwnPosition): PhotoLookup =
    if (ownPosition == OwnPosition.NotPublished) PhotoLookup.NeedsOwnPosition else PhotoLookup.Missing

/** What ONE reading of `photos/{uid}` said, before the card draws anything. */
sealed interface PhotoFetch {
    /** The document is not there (the author has none, deleted it, or sharing stopped): for the rules this is "introuvable", not a refusal. */
    data object Absent : PhotoFetch

    /** The document is there and valid. */
    class Valid(
        val photo: SharedPhoto,
    ) : PhotoFetch {
        override fun toString(): String = "Valid(redacted)"
    }

    /** The document is there but is not a valid photo (half a place, too big, expired by its own date). */
    data object Invalid : PhotoFetch

    /** The rules refused. Ambiguous (expired photo, stopped sharing, reciprocity): see [refusalLookup]. */
    data object Denied : PhotoFetch

    /** The server could not be reached. */
    data object Unreachable : PhotoFetch

    /** Anything else (quota, expired sign-in, error of the server or of the phone). */
    data object Failed : PhotoFetch
}

fun interface PhotoSource {
    suspend fun fetch(
        miffId: String,
        uid: String,
    ): PhotoFetch
}

fun interface OwnPositionProbe {
    /** True: my `positions/{moi}` exists in this Miff. False: it does not. Null: not known. */
    suspend fun exists(miffId: String): Boolean?
}

/**
 * Has this phone published its own position in [miffId]? First the local note of the detection (free: a "yes" is enough to answer), then, only when the local
 * note says nothing, ONE read of my own position on the server (a document I can always read; at most one per Miff per [FRESH_MILLIS] when the answer is known,
 * so a list of cards does not read it again and again). The local note alone was the cause of a wrong sentence (it is forgotten at a resume of the sharing...).
 */
class OwnPositionResolver(
    private val myUid: () -> String?,
    private val localPublished: (miffId: String) -> Boolean,
    private val probe: OwnPositionProbe,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private class Answer(
        val published: Boolean,
        val atMillis: Long,
    )

    private val answers = LinkedHashMap<String, Answer>()

    suspend fun state(miffId: String): OwnPosition {
        // Not signed in: nothing is known, so never the sentence.
        val uid = myUid() ?: return OwnPosition.Unknown
        if (localPublished(miffId)) return OwnPosition.Published
        val key = "$uid/$miffId"
        val now = nowMillis()
        val known = synchronized(answers) { answers[key] }
        if (known != null && now - known.atMillis < FRESH_MILLIS) {
            return if (known.published) OwnPosition.Published else OwnPosition.NotPublished
        }
        val exists = probe.exists(miffId) ?: return OwnPosition.Unknown
        synchronized(answers) { answers[key] = Answer(exists, now) }
        return if (exists) OwnPosition.Published else OwnPosition.NotPublished
    }

    /** End of the session: the answers are forgotten. */
    fun clear() = synchronized(answers) { answers.clear() }

    companion object {
        const val FRESH_MILLIS = 30_000L
    }
}

/** The reader of the Feed: the cache, ONE source, and what a refusal means ([refusalLookup]). It never writes nor deletes. */
class CachedPhotoReader(
    private val cache: PhotoCache,
    private val source: PhotoSource,
    private val ownPosition: suspend (miffId: String) -> OwnPosition,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : PhotoReader {
    override suspend fun read(
        miffId: String,
        uid: String,
        lastUpdateAtMillis: Long,
    ): PhotoLookup {
        cache.get(miffId, uid, lastUpdateAtMillis, nowMillis())?.let { return PhotoLookup.Found(it) }
        // Nothing to show for this very status (an expired photo, for instance): the server is not asked again at each opening (review C8).
        if (cache.isMissing(miffId, uid, lastUpdateAtMillis)) return PhotoLookup.Missing
        return when (val fetched = source.fetch(miffId, uid)) {
            PhotoFetch.Absent, PhotoFetch.Invalid -> {
                cache.putMissing(miffId, uid, lastUpdateAtMillis)
                PhotoLookup.Missing
            }

            is PhotoFetch.Valid -> {
                cache.putRead(miffId, uid, fetched.photo, lastUpdateAtMillis)
                PhotoLookup.Found(fetched.photo)
            }

            PhotoFetch.Denied -> {
                refusalLookup(ownPosition(miffId))
            }

            PhotoFetch.Unreachable -> {
                PhotoLookup.Offline
            }

            PhotoFetch.Failed -> {
                PhotoLookup.Missing
            }
        }
    }
}

class FirestorePhotoSource(
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : PhotoSource {
    override suspend fun fetch(
        miffId: String,
        uid: String,
    ): PhotoFetch =
        try {
            val snapshot =
                firestore()
                    .collection("miffs")
                    .document(miffId)
                    .collection("photos")
                    .document(uid)
                    .get()
                    .await()
            if (!snapshot.exists()) {
                PhotoFetch.Absent
            } else {
                SharedPhoto
                    .fromServer(
                        jpeg = snapshot.getBlob("jpeg")?.toBytes(),
                        createdAtMillis = (snapshot.get("createdAt") as? Timestamp)?.toDate()?.time,
                        expiresAtMillis = (snapshot.get("expiresAt") as? Timestamp)?.toDate()?.time,
                        lat = snapshot.get("lat") as? Number,
                        lng = snapshot.get("lng") as? Number,
                        measuredAtMillis = (snapshot.get("measuredAt") as? Timestamp)?.toDate()?.time,
                        nowMillis = nowMillis(),
                    )?.let { PhotoFetch.Valid(it) } ?: PhotoFetch.Invalid
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            when (e.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> PhotoFetch.Denied

                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                -> PhotoFetch.Unreachable

                else -> PhotoFetch.Failed
            }
        } catch (e: Exception) {
            PhotoFetch.Failed
        }
}

/** ONE read of my own `positions/{moi}` from the server, only when a photo was refused and the local note does not say "published". Never listed, never written. */
class FirestoreOwnPositionProbe(
    private val myUid: () -> String?,
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
) : OwnPositionProbe {
    override suspend fun exists(miffId: String): Boolean? {
        val uid = myUid() ?: return null
        return try {
            firestore()
                .collection("miffs")
                .document(miffId)
                .collection("positions")
                .document(uid)
                .get(Source.SERVER)
                .await()
                .exists()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}
