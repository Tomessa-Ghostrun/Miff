package com.tomessa.miff.testsupport

import com.google.firebase.firestore.FirebaseFirestoreException
import com.tomessa.miff.data.account.AccountDataStore
import com.tomessa.miff.data.account.AccountDeletionMark
import com.tomessa.miff.data.account.AccountDeletionMarker
import com.tomessa.miff.data.account.AccountDeletionProcedure
import com.tomessa.miff.data.account.AccountDeletionSession
import com.tomessa.miff.data.account.DeletionRun
import com.tomessa.miff.data.account.Inventory

/** The marker of a deletion under way, in memory. */
class MemoryDeletionMarker(
    var stored: AccountDeletionMark? = null,
    private val timeline: Timeline? = null,
) : AccountDeletionMarker {
    var clears = 0

    override fun read(): AccountDeletionMark? = stored

    override fun write(mark: AccountDeletionMark) {
        timeline?.plusAssign("marker.write")
        stored = mark
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
    var inventory: Inventory = Inventory.NoMiffs,
) : AccountDeletionProcedure {
    val results = ArrayDeque<DeletionRun>()
    var runs = 0

    /** Runs when a run starts (to look at what the controller did BEFORE it). */
    var onRun: () -> Unit = {}

    override suspend fun inventory(uid: String): Inventory = inventory

    override suspend fun run(uid: String): DeletionRun {
        runs++
        onRun()
        return if (results.size > 1) results.removeFirst() else results.firstOrNull() ?: DeletionRun.Completed
    }
}

class FakeDeletionSession(
    private val timeline: Timeline,
) : AccountDeletionSession {
    var begins = 0
    var erased = mutableListOf<String>()

    override suspend fun beginAccountDeletion() {
        begins++
        timeline += "begin"
    }

    override suspend fun eraseAfterAccountDeletion(uid: String) {
        erased += uid
        timeline += "erase"
    }
}
