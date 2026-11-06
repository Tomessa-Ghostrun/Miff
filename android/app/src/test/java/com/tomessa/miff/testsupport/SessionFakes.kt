package com.tomessa.miff.testsupport

import com.tomessa.miff.data.session.ActiveDevice
import com.tomessa.miff.data.session.Clearance
import com.tomessa.miff.data.session.DeviceIdStore
import com.tomessa.miff.data.session.SessionDocuments
import com.tomessa.miff.domain.session.DeviceIds
import com.tomessa.miff.domain.session.RemoteSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.io.IOException

/** An identifier of session that has the shape the rules require. */
const val DEVICE_A = "device-A-0123456789abcdef"
const val DEVICE_B = "device-B-0123456789abcdef"

class MemoryDeviceIdStore(
    var id: String? = null,
) : DeviceIdStore {
    var clears = 0

    /** The disk refuses to keep the identifier (`commit()` answers false). */
    var failSave = false

    /** The marker "replaced", as it is on the disk (survives a new DeviceSession built on the same store: a restart of the process). */
    var replaced = false

    override fun read(): String? = id

    override fun save(id: String): Boolean {
        require(DeviceIds.isValid(id))
        if (failSave) return false
        this.id = id
        return true
    }

    override fun clear() {
        clears++
        id = null
    }

    override fun isReplaced(): Boolean = replaced

    override fun markReplaced() {
        replaced = true
    }

    override fun clearReplaced() {
        replaced = false
    }

    /** The marker "deletion of the account under way", as it is on the disk (survives a new DeviceSession built on the same store). */
    var closed = false

    override fun isClosed(): Boolean = closed

    override fun markClosed() {
        closed = true
    }

    override fun clearClosed() {
        closed = false
    }
}

/**
 * The "server" of the sessions, in memory: one document per account. [takeOver] is another phone signing in (its write wins). Every call that
 * reaches the server is counted; [offline] makes them all fail the way a missing network does.
 */
class FakeSessionDocuments(
    private val timeline: Timeline = Timeline(),
) : SessionDocuments {
    val sessions = mutableMapOf<String, String>()
    var offline = false

    /** Runs when a write reaches the server, before it is applied (to look at what the phone holds at that moment). */
    var beforeWrite: (String) -> Unit = {}

    /** The next listenings end with this error (the listener of Firestore dies), this many times. */
    var observeErrors = 0

    /** Every write / deletion that REACHED the server, in order: "write:uid:id", "delete:uid:id". */
    val changes = mutableListOf<String>()
    var reads = 0

    private val live = MutableSharedFlow<Pair<String, RemoteSession>>(extraBufferCapacity = 32)

    private fun check() {
        if (offline) throw IOException("offline")
    }

    private fun current(uid: String): RemoteSession =
        sessions[uid]?.let { RemoteSession.Present(it) } ?: RemoteSession.Missing

    /** Another phone signs in with the same account. */
    fun takeOver(
        uid: String,
        id: String = DEVICE_B,
    ) {
        sessions[uid] = id
        live.tryEmit(uid to current(uid))
    }

    /** The session document disappears (account deleted elsewhere). */
    fun remove(uid: String) {
        sessions.remove(uid)
        live.tryEmit(uid to RemoteSession.Missing)
    }

    override suspend fun write(
        uid: String,
        deviceId: String,
    ) {
        check()
        require(DeviceIds.isValid(deviceId))
        beforeWrite(deviceId)
        sessions[uid] = deviceId
        changes += "write:$uid:$deviceId"
        timeline += "session-write"
        live.tryEmit(uid to current(uid))
    }

    override suspend fun read(uid: String): RemoteSession {
        check()
        reads++
        return current(uid)
    }

    /** Deletion of the account: whoever holds the session (idempotent). */
    override suspend fun delete(uid: String) {
        check()
        sessions.remove(uid)
        changes += "delete-any:$uid"
        timeline += "session-delete-any"
        live.tryEmit(uid to current(uid))
    }

    override suspend fun deleteIfMine(
        uid: String,
        deviceId: String,
    ): Boolean {
        check()
        if (sessions[uid] != deviceId) return false
        sessions.remove(uid)
        changes += "delete:$uid:$deviceId"
        timeline += "session-delete"
        live.tryEmit(uid to current(uid))
        return true
    }

    override fun observe(uid: String): Flow<RemoteSession> =
        live
            .filter { it.first == uid }
            .map { it.second }
            .onStart {
                if (observeErrors > 0) {
                    observeErrors--
                    throw IOException("listener died")
                }
                emit(current(uid))
            }
}

/** The answer of the session to the writers of statuses and positions, set by the test; counts what it was asked. */
class FakeActiveDevice(
    var answer: Clearance = Clearance.Cleared(DEVICE_A),
) : ActiveDevice {
    val rechecks = mutableListOf<Boolean>()
    var refusalChecks = 0

    /** What [afterRefusal] answers; true switches the answer to [Clearance.Replaced] as the real session does. */
    var replacedAfterRefusal = false

    override suspend fun clearance(recheck: Boolean): Clearance {
        rechecks += recheck
        return answer
    }

    override suspend fun afterRefusal(): Boolean {
        refusalChecks++
        if (replacedAfterRefusal) answer = Clearance.Replaced
        return replacedAfterRefusal
    }
}
