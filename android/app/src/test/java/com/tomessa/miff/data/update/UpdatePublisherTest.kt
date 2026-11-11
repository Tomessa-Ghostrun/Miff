package com.tomessa.miff.data.update

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import com.tomessa.miff.data.feed.PhotoCache
import com.tomessa.miff.data.feed.PhotoLookup
import com.tomessa.miff.data.feed.PhotoReader
import com.tomessa.miff.data.session.Clearance
import com.tomessa.miff.data.tracking.PhotoPosition
import com.tomessa.miff.data.tracking.PhotoPositionSource
import com.tomessa.miff.domain.feed.MyPhotoFacts
import com.tomessa.miff.domain.miff.DocWrite
import com.tomessa.miff.domain.miff.WriteKind
import com.tomessa.miff.domain.tracking.Fix
import com.tomessa.miff.domain.tracking.JournalKind
import com.tomessa.miff.domain.tracking.TrackingLimits
import com.tomessa.miff.domain.update.PublishGuard
import com.tomessa.miff.domain.update.SharedPhoto
import com.tomessa.miff.domain.update.UpdatePhoto
import com.tomessa.miff.domain.update.UpdateWrites
import com.tomessa.miff.testsupport.DEVICE_A
import com.tomessa.miff.testsupport.FakeActiveDevice
import com.tomessa.miff.testsupport.FakeNetwork
import com.tomessa.miff.testsupport.MemoryPublishLedger
import com.tomessa.miff.testsupport.RecordingJournal
import com.tomessa.miff.testsupport.T0
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * The publication of an update (ticket Update, criteria U1 to U9, L1, and the decision of the PM of 2026-11-06 "une photo doit être attachée à une position"):
 * nothing is written without a photo and a recent position, nothing offline, nothing from a phone that was replaced; the photo and the status go in ONE lot per 5 Miffs;
 * the three keys of the place are always together. The doors (Firestore, the session, the position, the network) are doubles.
 */
class UpdatePublisherTest {
    private class Commit(
        val miffIds: List<String>,
        val requireConsent: Boolean,
        val writes: List<DocWrite>,
    )

    private class FakeStore : PhotoStore {
        val commits = mutableListOf<Commit>()
        var notSharing = emptySet<String>()
        var needsAgreement = emptySet<String>()

        /** The call (0-based) that fails, and with what. */
        var failCall: Int? = null
        var failure: Exception = FirebaseFirestoreException("down", FirebaseFirestoreException.Code.UNAVAILABLE)
        var beforeCommit: () -> Unit = {}
        var delayMillis = 0L
        private var calls = 0

        override suspend fun commit(
            uid: String,
            miffIds: List<String>,
            requireConsent: Boolean,
            build: (List<String>) -> List<DocWrite>,
        ): PhotoCommit {
            val call = calls++
            beforeCommit()
            if (delayMillis > 0) delay(delayMillis)
            if (call == failCall) throw failure
            val sharingStopped = miffIds.filter { it in notSharing }
            val noAgreement = miffIds.filter { it in needsAgreement && it !in notSharing && requireConsent }
            val allowed = miffIds - sharingStopped.toSet() - noAgreement.toSet()
            val writes = if (allowed.isEmpty()) emptyList() else build(allowed)
            commits += Commit(miffIds, requireConsent, writes)
            return PhotoCommit(allowed, sharingStopped, noAgreement)
        }
    }

    private class FakePosition(
        var fix: Fix? = Fix(48.8566, 2.3522, T0 - 30_000L),
    ) : PhotoPositionSource {
        var calls = 0

        /** The permission was taken back, or the location of the phone is off. */
        var off = false

        override suspend fun measure(): PhotoPosition {
            calls++
            if (off) return PhotoPosition.LocationOff
            return fix?.let { PhotoPosition.Found(it) } ?: PhotoPosition.NotFound
        }
    }

    private val uid = "uid-1"
    private val zone = ZoneId.of("Europe/Paris")
    private val miffIds = List(7) { "Miff${it}AbCdEfGhIjKlMnOp".take(20) }
    private val store = FakeStore()
    private val device = FakeActiveDevice()
    private val position = FakePosition()
    private val network = FakeNetwork(true)
    private val ledger = MemoryPublishLedger()
    private val cache = PhotoCache()
    private var now = T0
    private var myMiffs = listOf(miffIds[0])
    private val photo = UpdatePhoto(ByteArray(80_000) { it.toByte() }, 461, 1024, takenAtMillis = T0 - 20_000L)

    private fun publisher(slowDetection: Boolean = false) =
        UpdatePublisher(
            store = store,
            device = device,
            position = position,
            network = network,
            ledger = ledger,
            photos = cache,
            currentUid = { uid },
            miffIds = { myMiffs },
            battery = { 77 },
            nowMillis = { now },
            zone = { zone },
            timeoutMillis = 1_000L,
            published = { published(it) },
            handOver = {
                handedOver += it
                if (slowDetection) delay(60_000L)
                onHandOver(it)
            },
            trackingAllowed = { trackingAllowed },
            journal = journal,
            travelOn = { traveling },
        )

    // Contract of backend/data-model.md: a photo is only written where my own position is published (by default it is, everywhere).
    private var published: (String) -> Boolean = { true }

    // What the Update gave to the detection (the detection publishes ITSELF: the double below stands for its pass), and what that pass does.
    private val handedOver = mutableListOf<Fix>()
    private var onHandOver: (Fix) -> Unit = {}
    private var trackingAllowed = true
    private val journal = RecordingJournal()

    // MODE VOYAGE: a trip is on.
    private var traveling = false

    private fun assertNothingLeft() {
        assertTrue("something was written: ${store.commits.size} commit(s)", store.commits.isEmpty())
        assertNull(ledger.last)
        assertNull(cache.get(miffIds[0], uid, null, now))
    }

    private fun photoWrites() = store.commits.flatMap { it.writes }.filter { "/photos/" in it.path }

    // --- MODE VOYAGE -------------------------------------------------------------------------------------------------------------------

    @Test
    fun duringATrip_thePhotoIsPublishedAsUsual_withItsOwnMeasure_andTheStatusCarriesNoBattery() =
        runTest {
            traveling = true
            // The position of the person keeps being published during a trip: the photo is written as outside a trip, with its own measure.
            val result = publisher().publish(photo)
            assertEquals(PublishResult.Published(1), result)
            assertTrue(handedOver.isEmpty())
            val writes = store.commits.single().writes
            assertEquals(
                listOf("miffs/${miffIds[0]}/photos/$uid", "miffs/${miffIds[0]}/status/$uid"),
                writes.map { it.path },
            )
            val status = writes[1].data
            // The rules refuse a battery level next to a trip: it is null, and the status does not touch the presence or the keys of the trip.
            assertNull(status["batteryPct"])
            assertTrue("batteryPct" in status)
            assertFalse("presence" in status)
            assertFalse("travelUntil" in status)
            assertFalse("travelUntilDay" in status)
        }

    @Test
    fun outsideATrip_aPhotoStillWaitsForAPublishedPosition_whenTheDetectionDoesNotPublishIt() =
        runTest {
            published = { false }
            assertEquals(PublishResult.PositionNotFound, publisher().publish(photo))
            assertEquals(1, handedOver.size)
            assertTrue(store.commits.isEmpty())
        }

    // --- The nominal publication ---------------------------------------------------------------------------------------------------

    @Test
    fun publishing_writesThePhotoAndTheStatus_inOneLot_withThePlaceAndTheSession() =
        runTest {
            val result = publisher().publish(photo)
            assertEquals(PublishResult.Published(1), result)
            assertEquals(1, store.commits.size)
            val commit = store.commits.single()
            assertTrue("the photo is written with the consent of the position required", commit.requireConsent)
            assertEquals(
                listOf(
                    "miffs/${miffIds[0]}/photos/$uid",
                    "miffs/${miffIds[0]}/status/$uid",
                ),
                commit.writes.map {
                    it.path
                },
            )
            val photoDoc = commit.writes[0]
            assertEquals(setOf("jpeg", "createdAt", "expiresAt", "lat", "lng", "measuredAt"), photoDoc.data.keys)
            assertEquals(Timestamp(java.util.Date(T0 - 30_000L)), photoDoc.data["measuredAt"])
            assertEquals(DEVICE_A, commit.writes[1].data["deviceId"])
            assertEquals(77, commit.writes[1].data["batteryPct"])
            assertEquals(1, position.calls)
        }

    @Test
    fun aSuccess_isCounted_andMyCardShowsMyPhotoWithoutReadingTheServer() =
        runTest {
            publisher().publish(photo)
            assertEquals(now, ledger.last)
            assertEquals(1, ledger.count)
            assertEquals(now, ledger.photoAt)
            val own = cache.get(miffIds[0], uid, lastUpdateAtMillis = null, nowMillis = now)
            assertNotNull(own)
            assertEquals(photo.jpeg.size, own!!.jpeg.size)
            // The place is kept with it (the full screen shows where it was taken).
            assertNotNull(own.location)
            // The same value whatever the last update the Feed says: it is the author's own copy, valid until it expires.
            assertNotNull(cache.get(miffIds[0], uid, 12345L, now))
            assertNull(cache.get(miffIds[0], uid, 12345L, now + UpdateWrites.PHOTO_LIFETIME_MILLIS))
        }

    @Test
    fun everyPhotoEverWritten_carriesTheThreeKeysOfThePlaceTogether_neverOneWithoutTheOthers_neverTheSession() =
        runTest {
            myMiffs = miffIds
            publisher().publish(photo)
            val photos = photoWrites()
            assertEquals(7, photos.size)
            photos.forEach { write ->
                assertEquals(WriteKind.SET, write.kind)
                val keys = write.data.keys
                assertTrue("lat" in keys && "lng" in keys && "measuredAt" in keys)
                assertFalse("deviceId" in keys)
                assertEquals(setOf("jpeg", "createdAt", "expiresAt", "lat", "lng", "measuredAt"), keys)
            }
        }

    @Test
    fun theStatusAndThePhotoAreAlwaysInTheSameCommit_neverOneAlone() =
        runTest {
            myMiffs = miffIds
            publisher().publish(photo)
            store.commits.forEach { commit ->
                val photosOf =
                    commit.writes
                        .filter {
                            "/photos/" in it.path
                        }.map { it.path.substringAfter("miffs/").substringBefore("/") }
                val statusesOf =
                    commit.writes
                        .filter {
                            "/status/" in it.path
                        }.map { it.path.substringAfter("miffs/").substringBefore("/") }
                assertEquals(photosOf, statusesOf)
            }
            assertTrue(store.commits.flatMap { it.writes }.none { "/positions/" in it.path })
        }

    // --- No position, no photo (decision of the PM, 2026-11-06) ---------------------------------------------------------------------

    @Test
    fun withoutAPosition_nothingIsPublished_thePhotoStays_theSecondTryMeasuresAgain() =
        runTest {
            position.fix = null
            val publisher = publisher()
            assertEquals(PublishResult.PositionNotFound, publisher.publish(photo))
            assertNothingLeft()
            position.fix = Fix(1.0, 1.0, T0)
            assertEquals(PublishResult.Published(1), publisher.publish(photo))
            assertEquals(2, position.calls)
        }

    @Test
    fun aPositionOfMoreThan10Minutes_isNotUsed_whateverTheSourceSays() =
        runTest {
            position.fix = Fix(1.0, 1.0, T0 - TrackingLimits.FIX_MAX_AGE_MILLIS - 1)
            assertEquals(PublishResult.PositionNotFound, publisher().publish(photo))
            assertNothingLeft()
        }

    @Test
    fun aPositionOutOfRange_isNotUsed_theRulesWouldRefuseTheWholePhoto() =
        runTest {
            position.fix = Fix(123.0, 2.0, T0)
            assertEquals(PublishResult.PositionNotFound, publisher().publish(photo))
            assertNothingLeft()
        }

    @Test
    fun thePositionIsMeasuredOnce_atThePressOfPublier_andNotWhenSomethingElseStopsTheWay() =
        runTest {
            network.online = false
            publisher().publish(photo)
            device.answer = Clearance.Replaced
            network.online = true
            publisher().publish(photo)
            assertEquals("nobody is located for a photo that cannot leave", 0, position.calls)
        }

    // --- Offline, replaced phone, guard-rail, old photo -------------------------------------------------------------------------------

    @Test
    fun offline_nothingIsWrittenNorQueued_thePhotoStaysOnTheScreen() =
        runTest {
            network.online = false
            assertEquals(PublishResult.Offline, publisher().publish(photo))
            assertNothingLeft()
            assertEquals(0, position.calls)
            // Back online: nothing leaves by itself afterwards (a publication is only ever a press of the button).
            network.online = true
            assertTrue(store.commits.isEmpty())
        }

    @Test
    fun aServerThatDoesNotAnswer_isUnconfirmed_notNotSent_theCountIsNotMade_butTheDateOfTheSendingIsKept() =
        runTest {
            store.delayMillis = 60_000L
            // The photo MAY have left (a transaction that was sent can finish after the screen gave up): the sentence says "on n'a pas pu confirmer".
            assertEquals(PublishResult.Unconfirmed, publisher().publish(photo))
            assertNull(ledger.last)
            assertNull(cache.get(miffIds[0], uid, null, now))
            assertEquals("the date of the sending is kept for the clean-up", now, ledger.photoAt)
        }

    @Test
    fun theDateOfThePhotoIsNotedBeforeTheSending_notAfterTheSuccess() =
        runTest {
            var noteAtCommit: Long? = null
            store.beforeCommit = { noteAtCommit = ledger.photoAt }
            publisher().publish(photo)
            assertEquals("the note was already there when the lot was sent", now, noteAtCommit)
        }

    @Test
    fun aSendingKnownNotToHaveLeft_putsThePreviousNoteBack() =
        runTest {
            ledger.photoUid = uid
            ledger.photoAt = T0 - 3_600_000L
            store.failure = FirebaseFirestoreException("no", FirebaseFirestoreException.Code.PERMISSION_DENIED)
            store.failCall = 0
            assertEquals(PublishResult.Refused, publisher().publish(photo))
            assertEquals(T0 - 3_600_000L, ledger.photoAt)
            // And without any previous note: no note.
            val other = MemoryPublishLedger()
            val again =
                UpdatePublisher(
                    store,
                    device,
                    position,
                    network,
                    other,
                    cache,
                    { uid },
                    { myMiffs },
                    { 77 },
                    { now },
                    { zone },
                )
            store.failCall = 1
            assertEquals(PublishResult.Refused, again.publish(photo))
            assertNull(other.photoAt)
        }

    @Test
    fun aDeadlineOfTheServer_isUnconfirmedToo() =
        runTest {
            store.failure = FirebaseFirestoreException("slow", FirebaseFirestoreException.Code.DEADLINE_EXCEEDED)
            store.failCall = 0
            assertEquals(PublishResult.Unconfirmed, publisher().publish(photo))
            assertEquals(now, ledger.photoAt)
        }

    // --- The location off is not a slow signal (review C3, risk R15) ----------------------------------------------------------------------

    @Test
    fun aLocationThatIsOffOrNotAllowed_saysSo_notTheSentenceOfASlowSignal_nothingIsWritten() =
        runTest {
            position.off = true
            assertEquals(PublishResult.LocationOff, publisher().publish(photo))
            assertNothingLeft()
            assertEquals(1, position.calls)
        }

    @Test
    fun aSlowSignal_isNotTheLocationOff() =
        runTest {
            position.fix = null
            assertEquals(PublishResult.PositionNotFound, publisher().publish(photo))
        }

    @Test
    fun aPhoneThatWasReplaced_writesNothing_andItIsAskedBeforeAnyWrite() =
        runTest {
            device.answer = Clearance.Replaced
            assertEquals(PublishResult.Refused, publisher().publish(photo))
            assertNothingLeft()
            assertEquals(listOf(true), device.rechecks)
        }

    @Test
    fun aSessionThatCannotBeVerified_isTreatedAsNoConnection() =
        runTest {
            device.answer = Clearance.Unverified
            assertEquals(PublishResult.Offline, publisher().publish(photo))
            assertNothingLeft()
        }

    @Test
    fun aPhotoOfMoreThan5Minutes_isNotPublished() =
        runTest {
            now = T0 + UpdatePhoto.STALE_MILLIS + 21_000L
            assertEquals(PublishResult.Stale, publisher().publish(photo))
            assertTrue(store.commits.isEmpty())
            assertEquals(0, position.calls)
        }

    @Test
    fun theTwentyFirstPublicationOfTheDay_andOneLessThan30SecondsAfterTheLast_areRefused() =
        runTest {
            val (day, _) = PublishGuard.next(now, zone, null, 0)
            ledger.day = day
            ledger.count = 20
            assertEquals(PublishResult.TooMany, publisher().publish(photo))
            ledger.count = 3
            ledger.last = now - 10_000L
            assertEquals(PublishResult.TooSoon, publisher().publish(photo))
            assertNothingLeftExceptLedger()
            ledger.last = now - 31_000L
            assertEquals(PublishResult.Published(1), publisher().publish(photo))
            assertEquals(4, ledger.count)
        }

    private fun assertNothingLeftExceptLedger() {
        assertTrue(store.commits.isEmpty())
        assertEquals(0, position.calls)
    }

    // --- Sharing, agreement -----------------------------------------------------------------------------------------------------------

    @Test
    fun withNoMiffWhereIShare_theAnswerIsSharingStopped_nothingIsMeasured() =
        runTest {
            myMiffs = emptyList()
            assertEquals(PublishResult.SharingStopped, publisher().publish(photo))
            assertEquals(0, position.calls)
            assertTrue(store.commits.isEmpty())
        }

    @Test
    fun whenTheCardSaysIStoppedSharing_nothingIsWritten_andTheAnswerIsSharingStopped() =
        runTest {
            store.notSharing = setOf(miffIds[0])
            assertEquals(PublishResult.SharingStopped, publisher().publish(photo))
            assertTrue(photoWrites().isEmpty())
            assertNull(ledger.last)
        }

    @Test
    fun whenTheTextOfConsentDoesNotCoverThePosition_nothingIsWritten_andTheWayBackIsTheAgreement() =
        runTest {
            store.needsAgreement = setOf(miffIds[0])
            assertEquals(PublishResult.NeedsAgreement, publisher().publish(photo))
            assertTrue(photoWrites().isEmpty())
            assertNull(ledger.last)
        }

    @Test
    fun withTwoMiffs_theOneWhereIStoppedSharingIsSkipped_theOtherReceivesThePhoto() =
        runTest {
            myMiffs = miffIds.take(2)
            store.notSharing = setOf(miffIds[1])
            assertEquals(PublishResult.Published(1), publisher().publish(photo))
            assertEquals(listOf("miffs/${miffIds[0]}/photos/$uid"), photoWrites().map { it.path })
        }

    // --- Several Miffs (Premium): lots of 5, partial success -----------------------------------------------------------------------------

    @Test
    fun sevenMiffs_goInTwoLots_5AndThen2_neverMoreThan5InALot() =
        runTest {
            myMiffs = miffIds
            assertEquals(PublishResult.Published(7), publisher().publish(photo))
            assertEquals(listOf(5, 2), store.commits.map { it.miffIds.size })
            assertTrue(store.commits.all { it.miffIds.size <= UpdatePublisher.LOT_SIZE })
        }

    @Test
    fun whenTheSecondLotFails_thePhotoStaysWhereItWent_andTheNewTryTargetsTheOthersOnly() =
        runTest {
            myMiffs = miffIds
            store.failCall = 1
            val publisher = publisher()
            val first = publisher.publish(photo)
            assertEquals(PublishResult.Partial(5, 7, miffIds.drop(5)), first)
            // The photo reached 5 Miffs: it is counted, and my card shows it.
            assertEquals(1, ledger.count)
            assertNotNull(cache.get(miffIds[0], uid, null, now))
            // Not rolled back, and not in the Miffs that were not reached.
            assertNull(cache.get(miffIds[6], uid, null, now))

            now += 31_000L
            store.failCall = null
            val retry = publisher.publish(photo.fresh(now), only = (first as PublishResult.Partial).remaining)
            assertEquals(PublishResult.Published(2), retry)
            assertEquals(miffIds.drop(5), store.commits.last().miffIds)
        }

    private fun UpdatePhoto.fresh(at: Long) = UpdatePhoto(jpeg, width, height, at)

    // --- Refusals and failures, in calm categories -------------------------------------------------------------------------------------

    @Test
    fun aRefusalOfTheRules_isRefused_aNetworkErrorIsOffline_anythingElseIsFailed_nothingIsCounted() =
        runTest {
            store.failure = FirebaseFirestoreException("no", FirebaseFirestoreException.Code.PERMISSION_DENIED)
            store.failCall = 0
            assertEquals(PublishResult.Refused, publisher().publish(photo))
            store.failure = FirebaseFirestoreException("down", FirebaseFirestoreException.Code.UNAVAILABLE)
            store.failCall = 1
            assertEquals(PublishResult.Offline, publisher().publish(photo))
            store.failure = IllegalStateException("boom")
            store.failCall = 2
            assertEquals(PublishResult.Failed, publisher().publish(photo))
            assertNull(ledger.last)
            assertNull(cache.get(miffIds[0], uid, null, now))
        }

    @Test
    fun noOneSignedIn_nothingIsPublished() =
        runTest {
            val noOne =
                UpdatePublisher(
                    store,
                    device,
                    position,
                    network,
                    ledger,
                    cache,
                    { null },
                    { myMiffs },
                    { 50 },
                    { now },
                    { zone },
                )
            assertEquals(PublishResult.Failed, noOne.publish(photo))
            assertTrue(store.commits.isEmpty())
        }

    // --- My own position must be published where the photo goes (contract of the data model, decision D27) ----------------------------------------

    @Test
    fun whereMyPositionIsNotPublished_andTheDetectionCannotPublishIt_noPhotoIsWritten_theMeasureWasHandedOver() =
        runTest {
            published = { false }
            assertEquals(PublishResult.PositionNotFound, publisher().publish(photo))
            assertNothingLeft()
            assertEquals("one measure, taken at the press of Publier", 1, position.calls)
            assertEquals(listOf(position.fix), handedOver)
            assertTrue(JournalKind.UPDATE_POSITION_NOT_PUBLISHED in journal.kinds)
            assertTrue(JournalKind.UPDATE_HAND_OVER_FAILED in journal.kinds)
        }

    @Test
    fun whereMyPositionIsNotPublished_theMeasureIsHandedOver_andThePhotoLeavesInTheSameTry() =
        runTest {
            var done = false
            published = { done }
            onHandOver = { done = true }
            assertEquals(PublishResult.Published(1), publisher().publish(photo))
            assertEquals(1, position.calls)
            assertEquals(listOf(position.fix), handedOver)
            assertEquals(listOf("miffs/${miffIds[0]}/photos/$uid"), photoWrites().map { it.path })
            assertTrue(JournalKind.UPDATE_HAND_OVER_WORKED in journal.kinds)
        }

    @Test
    fun theUpdateNeverWritesMyPosition_itsOnlyWritesAreThePhotoAndTheStatus() =
        runTest {
            var done = false
            published = { done }
            onHandOver = { done = true }
            publisher().publish(photo)
            assertTrue(store.commits.flatMap { it.writes }.none { "/positions/" in it.path })
        }

    @Test
    fun whenEveryPositionIsPublished_nothingIsHandedOver() =
        runTest {
            assertEquals(PublishResult.Published(1), publisher().publish(photo))
            assertTrue(handedOver.isEmpty())
            assertTrue(JournalKind.UPDATE_POSITION_NOT_PUBLISHED !in journal.kinds)
        }

    @Test
    fun withoutAlwaysAndPrecise_theSentenceLeadsToTheSettings_andNothingIsMeasured() =
        runTest {
            published = { false }
            trackingAllowed = false
            assertEquals(PublishResult.LocationOff, publisher().publish(photo))
            assertNothingLeft()
            assertEquals(0, position.calls)
            assertTrue(handedOver.isEmpty())
            assertTrue(JournalKind.UPDATE_PERMISSION_BLOCKED in journal.kinds)
        }

    @Test
    fun whereMyPositionIsNotPublished_aSlowSignal_isNotHandedOver_andTheSecondTryCanWork() =
        runTest {
            var done = false
            published = { done }
            onHandOver = { done = true }
            position.fix = null
            val publisher = publisher()
            assertEquals(PublishResult.PositionNotFound, publisher.publish(photo))
            assertTrue(handedOver.isEmpty())
            assertTrue(JournalKind.UPDATE_MEASURE_FAILED in journal.kinds)
            position.fix = Fix(1.0, 1.0, T0)
            assertEquals(PublishResult.Published(1), publisher.publish(photo))
            assertEquals(2, position.calls)
        }

    @Test
    fun aMeasureOfMoreThan10Minutes_isNeverHandedOver() =
        runTest {
            published = { false }
            position.fix = Fix(1.0, 1.0, T0 - TrackingLimits.FIX_MAX_AGE_MILLIS - 1)
            assertEquals(PublishResult.PositionNotFound, publisher().publish(photo))
            assertTrue(handedOver.isEmpty())
        }

    @Test
    fun aDetectionThatFails_isNotAnError_thePhotoStaysAndTheSentenceIsTheCalmOne() =
        runTest {
            published = { false }
            onHandOver = { throw IllegalStateException("the detection failed") }
            assertEquals(PublishResult.PositionNotFound, publisher().publish(photo))
            assertNothingLeft()
        }

    @Test
    fun aDetectionThatDoesNotAnswerInTime_isGivenUpOn_theTryEndsCalmly() =
        runTest {
            published = { false }
            assertEquals(PublishResult.PositionNotFound, publisher(slowDetection = true).publish(photo))
            assertNothingLeft()
        }

    @Test
    fun withTwoMiffs_theDetectionPublishesOnlyOne_thePhotoGoesThere_theOtherIsLeftForTheNextTry() =
        runTest {
            myMiffs = miffIds.take(2)
            // The detection publishes in the first Miff only (e.g. the second one is not eligible for it).
            var done = false
            published = { id -> id == miffIds[0] && done }
            onHandOver = { done = true }
            val result = publisher().publish(photo)
            assertEquals(PublishResult.Partial(1, 2, listOf(miffIds[1])), result)
            assertEquals(listOf("miffs/${miffIds[0]}/photos/$uid"), photoWrites().map { it.path })
        }

    @Test
    fun withTwoMiffs_thePhotoOnlyGoesWhereMyPositionIsPublished_theOtherIsLeftForTheNextTry() =
        runTest {
            myMiffs = miffIds.take(2)
            published = { it == miffIds[0] }
            val result = publisher().publish(photo)
            assertEquals(PublishResult.Partial(1, 2, listOf(miffIds[1])), result)
            assertEquals(listOf("miffs/${miffIds[0]}/photos/$uid"), photoWrites().map { it.path })
            assertEquals("the second Miff lacks my position: the measure is handed over once", 1, handedOver.size)
        }

    // --- The clean-up of my expired photo (criterion U7) ----------------------------------------------------------------------------------

    private fun cleaner() =
        PhotoCleaner(store, device, network, ledger, cache, { uid }, { myMiffs }, { 61 }, { now }, 1_000L)

    @Test
    fun aPhotoOlderThan24Hours_isDeleted_andTheStatusSaysHasPhotoFalse_inEachMiffWhereIShare() =
        runTest {
            ledger.photoAt = T0 - PhotoCleaner.EXPIRED_AFTER_MILLIS - 1
            myMiffs = miffIds.take(2)
            assertTrue(cleaner().cleanUp())
            val commit = store.commits.single()
            assertFalse("the clean-up does not need the consent of the position", commit.requireConsent)
            assertEquals(
                listOf(
                    "miffs/${miffIds[0]}/photos/$uid" to WriteKind.DELETE,
                    "miffs/${miffIds[0]}/status/$uid" to WriteKind.UPDATE,
                    "miffs/${miffIds[1]}/photos/$uid" to WriteKind.DELETE,
                    "miffs/${miffIds[1]}/status/$uid" to WriteKind.UPDATE,
                ),
                commit.writes.map { it.path to it.kind },
            )
            commit.writes.filter { "/status/" in it.path }.forEach {
                assertEquals(false, it.data["hasPhoto"])
                assertEquals(DEVICE_A, it.data["deviceId"])
                assertFalse("lastUpdateAt" in it.data)
            }
            assertNull(ledger.photoAt)
        }

    @Test
    fun aPhotoOf24HoursExactlyOrLess_isLeftAlone_noNote_nothingToDo() =
        runTest {
            ledger.photoAt = T0 - PhotoCleaner.EXPIRED_AFTER_MILLIS
            assertFalse(cleaner().cleanUp())
            ledger.photoAt = null
            assertFalse(cleaner().cleanUp())
            assertTrue(store.commits.isEmpty())
        }

    @Test
    fun theCleanUp_waitsWhenOffline_whenReplaced_orWhenItFails_theNoteStaysForNextTime() =
        runTest {
            ledger.photoAt = T0 - PhotoCleaner.EXPIRED_AFTER_MILLIS - 1
            network.online = false
            assertFalse(cleaner().cleanUp())
            network.online = true
            device.answer = Clearance.Unverified
            assertFalse(cleaner().cleanUp())
            device.answer = Clearance.Replaced
            assertFalse(cleaner().cleanUp())
            device.answer = Clearance.Cleared(DEVICE_A)
            store.failCall = 0
            assertFalse(cleaner().cleanUp())
            assertTrue(store.commits.isEmpty())
            assertNotNull("the note stays", ledger.photoAt)
            // And then it works, with no loop in between.
            store.failCall = null
            assertTrue(cleaner().cleanUp())
        }

    // --- The clean-up after a sign-out, a reinstall, a sending that left after the screen gave up (review C1, C2) --------------------------------

    private fun sinceHours(hours: Long) = T0 - hours * 3_600_000L

    @Test
    fun aSignOutDoesNotForgetThePhoto_theSameAccountSigningInAgainCleansItUp() =
        runTest {
            publisher().publish(photo)
            val sentAt = ledger.photoAt
            // Sign-out: the teardown clears the ledger.
            ledger.clear()
            assertEquals("the note of the photo survives the sign-out", sentAt, ledger.photoAt)
            // 25 hours later, the same account signs in again: the photo is removed.
            now += 25L * 3_600_000L
            assertTrue(cleaner().cleanUp())
            assertEquals(2, store.commits.size)
            assertNull(ledger.photoAt)
        }

    @Test
    fun theNoteOfAnotherAccountIsIgnored_aPhotoOfSomeoneElseIsNeverCleanedUpByThisPhone() =
        runTest {
            ledger.photoUid = "someone-else"
            ledger.photoAt = sinceHours(30)
            assertFalse(cleaner().cleanUp())
            assertTrue(store.commits.isEmpty())
        }

    @Test
    fun aSendingThatLeftAfterTheScreenGaveUp_isCleanedUp24HoursLater() =
        runTest {
            store.delayMillis = 60_000L
            assertEquals(PublishResult.Unconfirmed, publisher().publish(photo))
            store.delayMillis = 0L
            now += 24L * 3_600_000L + 1
            assertTrue(cleaner().cleanUp())
            // The deletion of a photo that may not exist is harmless: nothing is read, nothing refused.
            assertEquals(
                listOf(WriteKind.DELETE, WriteKind.UPDATE),
                store.commits
                    .last()
                    .writes
                    .map { it.kind },
            )
        }

    @Test
    fun afterAReinstall_withNoNote_aStatusThatSaysHasPhotoAndIsOlderThan25Hours_isCleanedUp_withoutReadingTheServer() =
        runTest {
            assertNull(ledger.photoAt)
            val removed = cleaner().cleanUp(MyPhotoFacts(hasPhoto = true, lastUpdateAtMillis = sinceHours(26)))
            assertTrue(removed)
            val writes = store.commits.single().writes
            assertEquals(listOf(WriteKind.DELETE, WriteKind.UPDATE), writes.map { it.kind })
            assertEquals(false, writes[1].data["hasPhoto"])
        }

    @Test
    fun afterAReinstall_aRecentStatus_isAdoptedAsTheNote_nothingIsDeletedYet() =
        runTest {
            assertFalse(cleaner().cleanUp(MyPhotoFacts(hasPhoto = true, lastUpdateAtMillis = sinceHours(10))))
            assertTrue(store.commits.isEmpty())
            assertEquals("the date of the last update stands in for the note", sinceHours(10), ledger.photoAt)
            // Fourteen hours later the note says 24 h and a bit: the photo is removed.
            now += 15L * 3_600_000L
            assertTrue(cleaner().cleanUp())
        }

    @Test
    fun withNoNoteAndNoPhotoInTheStatus_orNoDate_nothingIsDone() =
        runTest {
            assertFalse(cleaner().cleanUp(MyPhotoFacts(hasPhoto = false, lastUpdateAtMillis = sinceHours(40))))
            assertFalse(cleaner().cleanUp(MyPhotoFacts(hasPhoto = true, lastUpdateAtMillis = null)))
            assertFalse(cleaner().cleanUp(null))
            assertTrue(store.commits.isEmpty())
        }

    @Test
    fun theCleanUpNeverDeletesOnARefusal_theNoteStays() =
        runTest {
            store.failure = FirebaseFirestoreException("no", FirebaseFirestoreException.Code.PERMISSION_DENIED)
            store.failCall = 0
            assertFalse(cleaner().cleanUp(MyPhotoFacts(hasPhoto = true, lastUpdateAtMillis = sinceHours(30))))
            assertTrue("a refused lot wrote nothing", store.commits.isEmpty())
        }

    // --- Decision D35: with no date, ONE get of my own photo decides ---------------------------------------------------------------------------

    private class FakeOwnPhoto(
        var answer: PhotoLookup,
    ) : PhotoReader {
        var reads = 0

        override suspend fun read(
            miffId: String,
            uid: String,
            lastUpdateAtMillis: Long,
        ): PhotoLookup {
            reads++
            return answer
        }
    }

    private fun cleanerWith(reader: PhotoReader) =
        PhotoCleaner(store, device, network, ledger, cache, {
            uid
        }, { myMiffs }, { 61 }, { now }, 1_000L, reader)

    private fun ownPhoto(
        createdAt: Long,
        expiresAt: Long,
    ) = PhotoLookup.Found(SharedPhoto(ByteArray(10) { 1 }, createdAt, expiresAt, null))

    @Test
    fun withNoDate_aReadablePhotoGivesItsOwnDate_oneRead_nothingDeletedYet() =
        runTest {
            val reader = FakeOwnPhoto(ownPhoto(createdAt = sinceHours(5), expiresAt = sinceHours(5) + 23L * 3_600_000L))
            assertFalse(cleanerWith(reader).cleanUp(MyPhotoFacts(hasPhoto = true, lastUpdateAtMillis = sinceHours(2))))
            assertEquals(1, reader.reads)
            assertEquals("the date of the photo itself, not of the last update", sinceHours(5), ledger.photoAt)
            assertTrue(store.commits.isEmpty())
        }

    @Test
    fun withNoDate_aRefusedRead_neverDeletes_theDateOfTheLastUpdateStandsIn() =
        runTest {
            val reader = FakeOwnPhoto(PhotoLookup.NeedsOwnPosition)
            assertFalse(cleanerWith(reader).cleanUp(MyPhotoFacts(hasPhoto = true, lastUpdateAtMillis = sinceHours(3))))
            assertTrue("a refusal is never a reason to delete", store.commits.isEmpty())
            assertEquals(sinceHours(3), ledger.photoAt)
        }

    @Test
    fun withNoDate_aPhotoThatTheGetShowsExpired_isDeleted() =
        runTest {
            val reader = FakeOwnPhoto(ownPhoto(createdAt = sinceHours(24), expiresAt = sinceHours(1)))
            assertTrue(cleanerWith(reader).cleanUp(MyPhotoFacts(hasPhoto = true, lastUpdateAtMillis = sinceHours(24))))
            assertEquals(1, store.commits.size)
        }

    @Test
    fun withADate_nothingIsReadAtAll() =
        runTest {
            ledger.photoAt = sinceHours(2)
            val reader = FakeOwnPhoto(PhotoLookup.Missing)
            assertFalse(cleanerWith(reader).cleanUp(MyPhotoFacts(hasPhoto = true, lastUpdateAtMillis = sinceHours(2))))
            assertEquals(0, reader.reads)
        }
}
