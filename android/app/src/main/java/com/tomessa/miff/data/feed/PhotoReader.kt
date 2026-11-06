package com.tomessa.miff.data.feed

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
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

    @Synchronized
    fun putRead(
        miffId: String,
        uid: String,
        photo: SharedPhoto,
        lastUpdateAtMillis: Long,
    ) {
        entries[key(miffId, uid)] = Entry(photo, lastUpdateAtMillis)
    }

    /** The photo I just published: shown on my card with no reading of the server. */
    @Synchronized
    fun putOwn(
        miffId: String,
        uid: String,
        photo: SharedPhoto,
    ) {
        entries[key(miffId, uid)] = Entry(photo, null)
    }

    @Synchronized
    fun remove(
        miffId: String,
        uid: String,
    ) {
        entries.remove(key(miffId, uid))
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    /** End of the session: nothing of the photos stays in memory for the next person. */
    override suspend fun stop() = clear()

    private fun key(
        miffId: String,
        uid: String,
    ) = "$miffId/$uid"

    private companion object {
        const val MAX_ENTRIES = 16
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
    }
}

class FirestorePhotoReader(
    private val cache: PhotoCache,
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : PhotoReader {
    override suspend fun read(
        miffId: String,
        uid: String,
        lastUpdateAtMillis: Long,
    ): PhotoLookup {
        cache.get(miffId, uid, lastUpdateAtMillis, nowMillis())?.let { return PhotoLookup.Found(it) }
        return try {
            val snapshot =
                firestore()
                    .collection("miffs")
                    .document(miffId)
                    .collection("photos")
                    .document(uid)
                    .get()
                    .await()
            if (!snapshot.exists()) return PhotoLookup.Missing
            val photo =
                SharedPhoto.fromServer(
                    jpeg = snapshot.getBlob("jpeg")?.toBytes(),
                    createdAtMillis = (snapshot.get("createdAt") as? Timestamp)?.toDate()?.time,
                    expiresAtMillis = (snapshot.get("expiresAt") as? Timestamp)?.toDate()?.time,
                    lat = snapshot.get("lat") as? Number,
                    lng = snapshot.get("lng") as? Number,
                    measuredAtMillis = (snapshot.get("measuredAt") as? Timestamp)?.toDate()?.time,
                    nowMillis = nowMillis(),
                ) ?: return PhotoLookup.Missing
            cache.putRead(miffId, uid, photo, lastUpdateAtMillis)
            PhotoLookup.Found(photo)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            when (e.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> PhotoLookup.NeedsOwnPosition

                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                -> PhotoLookup.Offline

                else -> PhotoLookup.Missing
            }
        } catch (e: Exception) {
            PhotoLookup.Missing
        }
    }
}
