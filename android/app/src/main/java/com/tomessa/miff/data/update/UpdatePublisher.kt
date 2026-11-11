package com.tomessa.miff.data.update

import com.google.firebase.firestore.FirebaseFirestoreException
import com.tomessa.miff.data.auth.NetworkMonitor
import com.tomessa.miff.data.feed.PhotoCache
import com.tomessa.miff.data.feed.PhotoLookup
import com.tomessa.miff.data.feed.PhotoReader
import com.tomessa.miff.data.session.ActiveDevice
import com.tomessa.miff.data.session.Clearance
import com.tomessa.miff.data.tracking.PhotoPosition
import com.tomessa.miff.data.tracking.PhotoPositionSource
import com.tomessa.miff.data.tracking.PublishedPositions
import com.tomessa.miff.domain.feed.MyPhotoFacts
import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.miff.interpretMiffError
import com.tomessa.miff.domain.tracking.DetectionJournal
import com.tomessa.miff.domain.tracking.Fix
import com.tomessa.miff.domain.tracking.JournalKind
import com.tomessa.miff.domain.tracking.NoDetectionJournal
import com.tomessa.miff.domain.update.PhotoLocation
import com.tomessa.miff.domain.update.PhotoPlace
import com.tomessa.miff.domain.update.PublishGuard
import com.tomessa.miff.domain.update.SharedPhoto
import com.tomessa.miff.domain.update.UpdatePhoto
import com.tomessa.miff.domain.update.UpdateWrites
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId

/*
 * The PUBLICATION of an update (ticket docs/tickets/update.md 5.7, decision of the PM of 2026-11-06 "une photo doit être attachée à une position").
 *
 * What it guarantees, each point covered by a unit test:
 * - NOTHING is written without a photo (D16) AND without a recent position (the document of the photo always carries lat, lng and measuredAt together);
 * - NOTHING is written when the person is offline, when this phone is no longer the one of the account (SESS-2), when the guard-rail says "later"
 *   (20 a day, 30 s apart), or when the photo has waited more than 5 minutes: the photo stays on the screen, nothing is lost, nothing is queued to leave
 *   later without the person knowing;
 * - the photo AND the status are in the SAME transaction per lot of 5 Miffs at most (a status that says `hasPhoto` without a photo never exists), through the
 *   builders that carry the `deviceId` of the session ([UpdateWrites], TrackingWrites.photoStatus);
 * - the position is measured ONCE, at the press of "Publier" (never in a loop, never in the background), kept in memory until the lot is sent, and written only in
 *   the document of the photo. It is never logged nor sent as an event: the only trace is the local journal of the debug build (codes, no coordinate).
 * - the Update NEVER writes `positions/{moi}` (criterion U8). Where the detection has not published my position yet, the measure taken at "Publier" is HANDED OVER to the
 *   detection, which publishes it itself with all its guarantees ("Toujours" + precise location, sharing under the consent of version 3, session, frequency guard,
 *   breaker, rules); the photo then only leaves where the position is published afterwards.
 */

/** What "Publier" gave, reduced to what the screen can say (never a technical message). */
sealed interface PublishResult {
    /** The photo is shared in [sharedIn] Miff(s). */
    data class Published(
        val sharedIn: Int,
    ) : PublishResult

    /**
     * Premium, several lots: some Miffs have the photo, [remaining] do not (a lot failed). "Réessayer" targets [remaining] only. Nothing is rolled back: a photo
     * published in a part of the circle stays.
     */
    data class Partial(
        val sharedIn: Int,
        val total: Int,
        val remaining: List<String>,
    ) : PublishResult

    /** No recent position (a slow signal): not published, the photo stays, "Réessayer" measures again. */
    data object PositionNotFound : PublishResult

    /** The permission of the location was taken back, or the location of the phone is off (risk R15): not published, "Ouvrir les réglages" is the way. */
    data object LocationOff : PublishResult

    /**
     * The server did not answer in time: the photo MAY have left (a transaction that was sent can finish after the screen gave up). The sentence says so honestly
     * ("On n'a pas pu confirmer. Regarde dans ton Feed.") and the date of the sending is kept for the clean-up (review C2, criterion U3 nuanced).
     */
    data object Unconfirmed : PublishResult

    /** No network (or the server did not answer in time): nothing left. */
    data object Offline : PublishResult

    /** I share in none of my Miffs: no viewfinder, no publication; the way back is the screen of the Miff. */
    data object SharingStopped : PublishResult

    /** I share, but under a text of consent that does not cover the position: nothing is shared until I agree (the way back is the review of the agreement). */
    data object NeedsAgreement : PublishResult

    data object TooMany : PublishResult

    data object TooSoon : PublishResult

    /** The photo waited more than 5 minutes. */
    data object Stale : PublishResult

    /** The server said no (rules, App Check, another phone holds the session): a calm "try again in a moment". */
    data object Refused : PublishResult

    /** Anything else. */
    data object Failed : PublishResult
}

class UpdatePublisher(
    private val store: PhotoStore,
    private val device: ActiveDevice,
    private val position: PhotoPositionSource,
    private val network: NetworkMonitor,
    private val ledger: PublishLedger,
    private val photos: PhotoCache,
    private val currentUid: () -> String?,
    /** The ids of the Miffs of the person (the directory). A Miff where the sharing is stopped is filtered by the transaction, not here. */
    private val miffIds: () -> List<String>,
    private val battery: () -> Int?,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val timeoutMillis: Long = TIMEOUT_MS,
    /** Has my position been published in this Miff? (contract of data-model.md: no photo where it was not; the reading of the photos of the others depends on it.) */
    private val published: PublishedPositions = PublishedPositions { true },
    /**
     * Where my position is not published yet: hands the measure taken at "Publier" (less than 10 minutes old) to the detection, which publishes it ITSELF (criterion U8:
     * the Update never writes `positions/{moi}`) and returns when its pass is over (the wait is bounded here by [handOverTimeoutMillis]; the pass itself is NOT cut by it:
     * the implementation runs it outside the screen, so that the engine always finishes its write and its local copy).
     */
    private val handOver: suspend (Fix) -> Unit = {},
    /** The wait for the pass of the detection (own cap, shorter than [timeoutMillis]: the person has already waited for the measure; a detection busy elsewhere is not waited for long). */
    private val handOverTimeoutMillis: Long = HANDOVER_TIMEOUT_MS,
    /** Are the permissions the detection needs ("Toujours" + precise location) there? Read from the system, never a position. Without them it can never publish mine. */
    private val trackingAllowed: () -> Boolean = { true },
    /** The local journal of the debug build: CODES of cause, never a coordinate. */
    private val journal: DetectionJournal = NoDetectionJournal,
    /** NOTIF-4A: told, locally, when an update left for [miffIds] at [atMillis] (it moves my last update: the reminders waiting for me are cancelled and planned again). */
    private val onUpdatePublished: (miffIds: List<String>, atMillis: Long) -> Unit = { _, _ -> },
    /**
     * MODE VOYAGE: a trip is on. The position of the person is still published by the tracking and the photo keeps its own measure, as outside a trip; only the status of the update
     * differs: it carries NO battery level (the rules refuse a level next to a trip).
     */
    private val travelOn: () -> Boolean = { false },
) {
    /**
     * Publishes [photo]. [only]: the Miffs a previous try did not reach (null = all). Never throws except for the cancellation of the caller.
     */
    suspend fun publish(
        photo: UpdatePhoto,
        only: List<String>? = null,
    ): PublishResult {
        val uid = currentUid() ?: return PublishResult.Failed
        val now = nowMillis()
        if (photo.isStale(now)) return PublishResult.Stale
        when (PublishGuard.check(now, zone(), ledger.countedDay(), ledger.countToday(), ledger.lastPublishMillis())) {
            PublishGuard.Verdict.TOO_MANY -> {
                return PublishResult.TooMany
            }

            PublishGuard.Verdict.TOO_SOON -> {
                return PublishResult.TooSoon
            }

            PublishGuard.Verdict.GO -> {}
        }
        val targets = (only ?: miffIds()).distinct()
        if (targets.isEmpty()) return PublishResult.SharingStopped
        // Offline: nothing is attempted (a transaction would fail anyway, after a long wait). The photo stays on the screen.
        if (!network.isOnline()) return PublishResult.Offline
        val deviceId =
            when (val clearance = device.clearance(recheck = true)) {
                is Clearance.Cleared -> clearance.deviceId
                Clearance.Replaced -> return PublishResult.Refused
                Clearance.Unverified -> return PublishResult.Offline
            }
        // Contract of the data model: a photo is only written where my own position is published. Where the detection has not published it yet (its local copy has no
        // trace of a write), the Update does NOT give up: it measures, then hands the measure to the detection, which publishes it itself (see below).
        // Before, this case ended in "position introuvable" without anything being measured, at every try, and the detection alone (3 s of waiting) could never fix it.
        val traveling = travelOn()
        val missing = targets.filter { !published.isPublished(it) }
        // Without "Toujours" + the precise location the detection never publishes: "réessaie dans un instant" would be false and endless, the right sentence leads to the settings.
        val canHandOver = missing.isEmpty() || trackingAllowed()
        if (missing.isNotEmpty()) {
            journal.record(JournalKind.UPDATE_POSITION_NOT_PUBLISHED)
            if (!canHandOver) {
                journal.record(JournalKind.UPDATE_PERMISSION_BLOCKED)
                // Nowhere to go: the settings. In some Miffs only: the photo goes where my position is already published, the others are "Partielle" (nothing is handed over).
                if (missing.size == targets.size) return PublishResult.LocationOff
            }
        }
        // ONE measure of the position, here and not before: nobody is located for a photo that is never published.
        var measuredFix: Fix? = null
        val place =
            when (val measured = position.measure()) {
                PhotoPosition.LocationOff -> {
                    journal.record(JournalKind.UPDATE_PERMISSION_BLOCKED)
                    return PublishResult.LocationOff
                }

                PhotoPosition.NotFound -> {
                    journal.record(JournalKind.UPDATE_MEASURE_FAILED)
                    return PublishResult.PositionNotFound
                }

                is PhotoPosition.Found -> {
                    measuredFix = measured.fix
                    PhotoPlace.from(measured.fix, nowMillis())
                        ?: run {
                            journal.record(JournalKind.UPDATE_MEASURE_FAILED)
                            return PublishResult.PositionNotFound
                        }
                }
            }
        var withPosition = targets.filter { it !in missing }
        if (missing.isNotEmpty() && canHandOver) {
            handOverPosition(measuredFix)
            // The local copy of the detection now says where my position is published (read again, never assumed).
            withPosition = targets.filter { published.isPublished(it) }
            if (withPosition.isEmpty()) {
                // The detection could not publish it (the Miff is not eligible for it, a refusal, offline, session): its own journal lines say which.
                journal.record(JournalKind.UPDATE_HAND_OVER_FAILED)
                return PublishResult.PositionNotFound
            }
            journal.record(JournalKind.UPDATE_HAND_OVER_WORKED)
        }
        // The date of the sending is written BEFORE it starts (review C2): a transaction that leaves after the screen gave up is still cleaned up 24 h later.
        val previousNote = ledger.photoSentMillis(uid)
        ledger.notePhotoSent(uid, nowMillis())
        val result = writeLots(uid, photo, place, withPosition, targets, deviceId, traveling)
        // The sending is KNOWN not to have left (a refusal, no connection, nothing shared): the previous note comes back. Unconfirmed keeps the new one.
        val leftNothing =
            result == PublishResult.Offline || result == PublishResult.Refused || result == PublishResult.Failed ||
                result == PublishResult.SharingStopped || result == PublishResult.NeedsAgreement
        if (leftNothing) {
            if (previousNote != null) ledger.notePhotoSent(uid, previousNote) else ledger.clearPhotoSent()
        }
        return result
    }

    /**
     * Gives the measure taken at "Publier" to the detection and waits for the end of its pass (bounded). The measure is less than 10 minutes old, so the detection
     * does not ask for another one and publishes nothing more than it would have published itself. Never throws except for the cancellation of the caller.
     */
    private suspend fun handOverPosition(fix: Fix?) {
        if (fix == null) return
        try {
            withTimeoutOrNull(handOverTimeoutMillis) { handOver(fix) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The detection failed: the local copy tells whether the position got published, which is all that matters here.
        }
    }

    private suspend fun writeLots(
        uid: String,
        photo: UpdatePhoto,
        place: PhotoPlace,
        targets: List<String>,
        all: List<String>,
        deviceId: String,
        traveling: Boolean,
    ): PublishResult {
        val written = mutableListOf<String>()
        val notSharing = mutableListOf<String>()
        val needsAgreement = mutableListOf<String>()
        var failure: PublishResult? = null
        for (lot in targets.chunked(LOT_SIZE)) {
            val outcome: PhotoCommit? =
                try {
                    // The time of the lot is taken when the lot is built (a retry by Firestore builds it again with a fresh clock).
                    withTimeout(timeoutMillis) {
                        store.commit(uid, lot, requireConsent = true) { allowed ->
                            val moment = nowMillis()
                            allowed.flatMap { miffId ->
                                UpdateWrites.publish(
                                    miffId,
                                    uid,
                                    photo,
                                    place,
                                    moment,
                                    if (traveling) null else battery(),
                                    deviceId,
                                )
                            }
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    // No answer: it may or may not have left.
                    failure = PublishResult.Unconfirmed
                    null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failure =
                        when (interpretMiffError(e)) {
                            MiffFailure.OFFLINE -> {
                                if (isDeadline(
                                        e,
                                    )
                                ) {
                                    PublishResult.Unconfirmed
                                } else {
                                    PublishResult.Offline
                                }
                            }

                            MiffFailure.REFUSED -> {
                                PublishResult.Refused
                            }

                            else -> {
                                PublishResult.Failed
                            }
                        }
                    null
                }
            if (outcome == null) break
            written += outcome.written
            notSharing += outcome.notSharing
            needsAgreement += outcome.needsAgreement
        }
        if (written.isNotEmpty()) afterSuccess(uid, photo, place, written)
        val untried = all.filter { it !in written && it !in notSharing && it !in needsAgreement }
        return when {
            written.isEmpty() && failure != null -> {
                failure
            }

            written.isEmpty() && needsAgreement.isNotEmpty() -> {
                PublishResult.NeedsAgreement
            }

            written.isEmpty() -> {
                PublishResult.SharingStopped
            }

            failure != null || untried.isNotEmpty() -> {
                PublishResult.Partial(written.size, written.size + untried.size, untried)
            }

            else -> {
                PublishResult.Published(written.size)
            }
        }
    }

    private fun isDeadline(e: Exception): Boolean =
        (e as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED

    private fun afterSuccess(
        uid: String,
        photo: UpdatePhoto,
        place: PhotoPlace,
        written: List<String>,
    ) {
        val now = nowMillis()
        val (day, count) = PublishGuard.next(now, zone(), ledger.countedDay(), ledger.countToday())
        ledger.recordPublication(now, day, count)
        // My card shows my photo with no reading of the server; the server time is the phone's, give or take the clock (displayed as the time of the photo).
        val shared =
            SharedPhoto(
                jpeg = photo.jpeg,
                createdAtMillis = now,
                expiresAtMillis = now + UpdateWrites.PHOTO_LIFETIME_MILLIS,
                location = PhotoLocation(place.lat, place.lng, place.measuredAtMillis),
            )
        written.forEach { photos.putOwn(it, uid, shared) }
        try {
            onUpdatePublished(written, now)
        } catch (e: Exception) {
            // A reminder that cannot be brought up to date must never fail a publication that went through.
        }
    }

    companion object {
        /** 5 Miffs a lot: 3 reads of the rules each = 15 of the 20 allowed (backend/data-model.md, section 8). */
        const val LOT_SIZE = 5
        const val TIMEOUT_MS = 20_000L

        /** The wait for the detection's pass after a hand-over: about the time the measure itself may take (a pass is a few reads and one write). */
        const val HANDOVER_TIMEOUT_MS = 10_000L
    }
}

/**
 * The clean-up by the author (ticket 6.4): a photo published more than 24 h ago is deleted from the database and the status says `hasPhoto = false`, in
 * each Miff where I share. At the launch and at the return to the foreground. NO reading of the server (the date is the phone's own note); nothing is done
 * offline, or when this phone is no longer the one of the account (the next time). The only writes: `photos/{moi}` (delete) and the status builder.
 *
 * The note of the date is tied to the account and survives a sign-out (review C1). WITHOUT a note (a reinstall, a new phone) the date of the last update of my own
 * status stands in for it (the repli of the ticket): `hasPhoto` and a last update of more than 25 h = the photo is expired, it is deleted (a read of an expired photo
 * is refused by the rules even for its author, so the date of the update is what tells; NEVER a deletion on a `permission-denied`); `hasPhoto` and a last update
 * of less than 25 h: the date is adopted as the note, so the clean-up comes later.
 */
class PhotoCleaner(
    private val store: PhotoStore,
    private val device: ActiveDevice,
    private val network: NetworkMonitor,
    private val ledger: PublishLedger,
    private val photos: PhotoCache,
    private val currentUid: () -> String?,
    private val miffIds: () -> List<String>,
    private val battery: () -> Int?,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val timeoutMillis: Long = UpdatePublisher.TIMEOUT_MS,
    /** Reads MY OWN photo when the date is lost (decision D35 of the PM, 2026-11-06): one `get`, 2 reads of rules. */
    private val reader: PhotoReader? = null,
) {
    /** True when a photo was removed. Never throws except for the cancellation of the caller. */
    suspend fun cleanUp(mine: MyPhotoFacts? = null): Boolean {
        val uid = currentUid() ?: return false
        val now = nowMillis()
        val sentAt = ledger.photoSentMillis(uid)
        if (sentAt != null) {
            if (now - sentAt <= EXPIRED_AFTER_MILLIS) return false
        } else {
            // No note (reinstall, new phone): my own status tells, no reading of the photo.
            val update = mine?.lastUpdateAtMillis
            if (mine == null || !mine.hasPhoto || update == null) return false
            if (now - minOf(update, now) <= UNREADABLE_AFTER_MILLIS) {
                // D35: ONE `get` of my own photo decides on its `expiresAt` (2 reads of rules); never a deletion on a `permission-denied` (nothing is read then).
                val own = readOwnPhoto(uid, update)
                if (own == null || !own.isExpired(now)) {
                    // Readable: the date of the photo itself is the note; not readable (refused, absent, offline): the date of the last update stands in.
                    ledger.notePhotoSent(uid, own?.createdAtMillis ?: update)
                    return false
                }
            }
        }
        val ids = miffIds().distinct()
        if (ids.isEmpty() || !network.isOnline()) return false
        val deviceId = (device.clearance(recheck = false) as? Clearance.Cleared)?.deviceId ?: return false
        for (lot in ids.chunked(UpdatePublisher.LOT_SIZE)) {
            try {
                withTimeout(timeoutMillis) {
                    store.commit(uid, lot, requireConsent = false) { allowed ->
                        allowed.flatMap { UpdateWrites.removeExpired(it, uid, battery(), deviceId) }
                    }
                }
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                return false
            } catch (e: Exception) {
                // A refusal or a failure: the note stays, the next launch tries again (no loop).
                return false
            }
        }
        ids.forEach { photos.remove(it, uid) }
        ledger.clearPhotoSent()
        return true
    }

    private suspend fun readOwnPhoto(
        uid: String,
        lastUpdateAtMillis: Long,
    ): SharedPhoto? {
        val miffId = miffIds().firstOrNull() ?: return null
        val lookup =
            try {
                withTimeout(timeoutMillis) { reader?.read(miffId, uid, lastUpdateAtMillis) }
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                null
            }
        return (lookup as? PhotoLookup.Found)?.photo
    }

    companion object {
        /** The photo is readable 23 h; the clean-up starts at 24 h as the data model says ("illisible après 24 h"). */
        const val EXPIRED_AFTER_MILLIS = 24L * 60L * 60L * 1000L

        /** Without a note: the rules refuse the reading of a photo from 25 h on (24 h + 1 h of margin), so a status that old cannot have a readable photo. */
        const val UNREADABLE_AFTER_MILLIS = 25L * 60L * 60L * 1000L
    }
}
