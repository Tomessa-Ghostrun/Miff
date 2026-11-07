package com.tomessa.miff.testsupport

import com.google.firebase.firestore.FirebaseFirestoreException
import com.tomessa.miff.data.account.AccountDataStore
import com.tomessa.miff.data.account.AccountDeletionMark
import com.tomessa.miff.data.account.AccountDeletionMarker
import com.tomessa.miff.data.account.AccountDeletionProcedure
import com.tomessa.miff.data.account.AccountDeletionSession
import com.tomessa.miff.data.account.AccountMiffs
import com.tomessa.miff.data.account.AccountMiffsSummary
import com.tomessa.miff.data.account.DeletionRun
import com.tomessa.miff.data.account.DeletionStep
import com.tomessa.miff.data.account.Inventory
import com.tomessa.miff.data.account.MiffStep
import com.tomessa.miff.data.account.MiffsInventory
import com.tomessa.miff.data.account.MiffsRead
import com.tomessa.miff.domain.miff.MembershipRef

/** The marker of a deletion under way, in memory. */
class MemoryDeletionMarker(
    var stored: AccountDeletionMark? = null,
    private val timeline: Timeline? = null,
) : AccountDeletionMarker {
    var clears = 0

    override fun read(): AccountDeletionMark? = stored

    /** False = the disk refuses: nothing is written and the result says so. */
    var writeSucceeds = true

    override fun write(mark: AccountDeletionMark): Boolean {
        timeline?.plusAssign("marker.write")
        if (!writeSucceeds) return false
        stored = mark
        return true
    }

    override fun clear() {
        clears++
        timeline?.plusAssign("marker.clear")
        stored = null
    }
}

/**
 * `users/{uid}` as the server holds it: the number of indexes, the devices, the profile. Every call (read or write) takes the next
 * entry of [failures] first: null = it works, an exception = it fails and NOTHING is applied (a transaction that did not confirm).
 */
class FakeAccountDataStore(
    private val timeline: Timeline = Timeline(),
) : AccountDataStore {
    var memberships = 0
    val devices = mutableListOf<String>()
    var profileExists = true
    val failures = ArrayDeque<Exception?>()

    /** Runs when a call starts, with its name (to make something happen in the middle: a Miff joined, the session ended...). */
    var onCall: (String) -> Unit = {}
    val calls = mutableListOf<String>()

    private fun step(name: String) {
        calls += name
        timeline += name
        onCall(name)
        failures.removeFirstOrNull()?.let { throw it }
    }

    override suspend fun membershipCount(uid: String): Int {
        step("memberships")
        return memberships
    }

    override suspend fun deviceIds(uid: String): List<String> {
        step("devices.read")
        return devices.toList()
    }

    override suspend fun deleteDevice(
        uid: String,
        deviceId: String,
    ) {
        step("device.delete")
        devices.remove(deviceId)
    }

    override suspend fun deleteProfile(uid: String) {
        step("profile.delete")
        profileExists = false
    }

    companion object {
        fun offline() = FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)

        fun refused() = FirebaseFirestoreException("refused", FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }
}

/** The procedure, scripted: one answer per run (the last one is repeated). */
class FakeProcedure(
    var inventory: Inventory = Inventory.Loaded(AccountMiffsSummary.NONE),
) : AccountDeletionProcedure {
    val results = ArrayDeque<DeletionRun>()
    var runs = 0

    /** The steps the procedure announces during each run, in order (to drive the progress screen). */
    var steps: List<DeletionStep> = emptyList()

    /** The steps of the Nth run (from 0) when they differ from one run to the next (a deletion that makes progress); else [steps]. */
    var stepsByRun: List<List<DeletionStep>> = emptyList()

    /** Runs when a run starts (to look at what the controller did BEFORE it). */
    var onRun: () -> Unit = {}

    override suspend fun inventory(uid: String): Inventory = inventory

    override suspend fun run(
        uid: String,
        onStep: (DeletionStep) -> Unit,
    ): DeletionRun {
        runs++
        (stepsByRun.getOrNull(runs - 1) ?: steps).forEach(onStep)
        onRun()
        return if (results.size > 1) results.removeFirst() else results.firstOrNull() ?: DeletionRun.Completed
    }
}

class FakeDeletionSession(
    private val timeline: Timeline,
) : AccountDeletionSession {
    var begins = 0
    var cancels = 0
    var keptMarkerSignOuts = 0
    var erased = mutableListOf<String>()

    /** Runs when the local cleaning starts (to look at the state of the marker at that moment). */
    var onErase: () -> Unit = {}

    override suspend fun beginAccountDeletion() {
        begins++
        timeline += "begin"
    }

    override suspend fun eraseAfterAccountDeletion(uid: String) {
        onErase()
        erased += uid
        timeline += "erase"
    }

    override suspend fun cancelAccountDeletion() {
        cancels++
        timeline += "cancel"
    }

    override suspend fun endSessionKeepingDeletionMarker() {
        keptMarkerSignOuts++
        timeline += "endSessionKeepingMarker"
    }
}

/** The session "un compte, un seul appareil" of the account that is deleted (SESS-2): the deletion is recorded in the same timeline as the other steps. */
class FakeAccountSession(
    private val timeline: Timeline = Timeline(),
) : com.tomessa.miff.data.account.AccountSession {
    val deleted = mutableListOf<String>()

    /** Makes the next call fail (no acknowledgement of the server). */
    val failures = ArrayDeque<Exception?>()

    override suspend fun deleteSession(uid: String) {
        timeline += "session.delete"
        failures.removeFirstOrNull()?.let { throw it }
        deleted += uid
    }
}

/** The Miffs of the account, scripted (the orchestration of the procedure is tested without a server). Each call is recorded in the timeline. */
class FakeAccountMiffs(
    private val timeline: Timeline = Timeline(),
) : AccountMiffs {
    var inventory: MiffsRead = MiffsRead.Read(MiffsInventory(emptyList(), emptyList(), emptyList()))
    val leaves = mutableListOf<String>()
    val dropped = mutableListOf<String>()
    val deleted = mutableListOf<String>()

    /** One answer per call of leave / dropStale / deleteOwned (null = done). */
    val failures = ArrayDeque<com.tomessa.miff.domain.miff.MiffFailure?>()

    private fun step(): MiffStep = failures.removeFirstOrNull()?.let { MiffStep.Failed(it) } ?: MiffStep.Done

    override suspend fun inventory(uid: String): MiffsRead {
        timeline += "miffs.inventory"
        return inventory
    }

    override suspend fun dropStale(
        uid: String,
        ref: MembershipRef,
    ): MiffStep {
        timeline += "miffs.dropStale"
        dropped += ref.miffId
        return step()
    }

    override suspend fun leave(
        uid: String,
        ref: MembershipRef,
    ): MiffStep {
        timeline += "miffs.leave"
        leaves += ref.miffId
        return step()
    }

    override suspend fun deleteOwned(
        uid: String,
        miffId: String,
    ): MiffStep {
        timeline += "miffs.deleteOwned"
        deleted += miffId
        return step()
    }
}
