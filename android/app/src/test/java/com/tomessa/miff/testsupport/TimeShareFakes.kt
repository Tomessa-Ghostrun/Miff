package com.tomessa.miff.testsupport

import com.tomessa.miff.data.timeshare.PresenceLogStorage
import com.tomessa.miff.data.timeshare.TimeShareTrackingSource
import com.tomessa.miff.data.timeshare.TimeShareWindowStore
import com.tomessa.miff.domain.timeshare.LoggedPresence
import com.tomessa.miff.domain.timeshare.PresenceLog
import com.tomessa.miff.domain.timeshare.TimeShareTracking
import com.tomessa.miff.domain.tracking.PresenceValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The text of the journal of the presence in memory (a double of the private preference file). */
class MemoryPresenceLogStorage : PresenceLogStorage {
    var text: String? = null

    override fun read() = text

    override fun write(text: String): Boolean {
        this.text = text
        return true
    }

    override fun clear() {
        text = null
    }
}

/** The window of the card in memory. */
class MemoryTimeShareWindowStore(
    var days: Int? = null,
) : TimeShareWindowStore {
    override fun read(): Int = days ?: 7

    override fun write(days: Int) {
        this.days = days
    }

    override fun clear() {
        days = null
    }
}

/** What the card is told about the tracking, as set by the test. */
class FakeTimeShareTracking(
    var tracking: TimeShareTracking = TimeShareTracking(),
) : TimeShareTrackingSource {
    override fun of(miffId: String): TimeShareTracking = tracking
}

/** A journal that keeps every call as it is made (to check what the engine tells it), without the rules of the real one. */
class RecordingPresenceLog : PresenceLog {
    class Call(
        val miffId: String,
        val presence: PresenceValue,
        val placeId: String?,
        val atMillis: Long,
    )

    val calls = mutableListOf<Call>()
    val forgottenPlaces = mutableListOf<Pair<String, String>>()
    var cleared = 0
    private val mutableRevision = MutableStateFlow(0)
    override val revision: StateFlow<Int> = mutableRevision

    override fun record(
        miffId: String,
        presence: PresenceValue,
        placeId: String?,
        atMillis: Long,
    ) {
        calls += Call(miffId, presence, placeId, atMillis)
    }

    override fun facts(miffId: String): List<LoggedPresence> =
        calls.filter { it.miffId == miffId }.map { LoggedPresence(it.atMillis, it.presence, it.placeId) }

    override fun forgetPlace(
        miffId: String,
        placeId: String,
    ) {
        forgottenPlaces += miffId to placeId
    }

    override fun forgetMiff(miffId: String) = Unit

    override fun retainOnly(miffIds: Set<String>) = Unit

    override fun clear() {
        cleared++
        calls.clear()
    }
}

/** A journal that fails at every call: the detection must not notice. */
class FailingPresenceLog : PresenceLog {
    override val revision: StateFlow<Int> = MutableStateFlow(0)

    override fun record(
        miffId: String,
        presence: PresenceValue,
        placeId: String?,
        atMillis: Long,
    ): Unit = throw IllegalStateException("disk full")

    override fun begin(
        miffId: String,
        atMillis: Long,
    ): Unit = throw IllegalStateException("disk full")

    override fun startedAt(miffId: String): Long? = throw IllegalStateException("disk full")

    override fun facts(miffId: String): List<LoggedPresence> = throw IllegalStateException("disk full")

    override fun forgetPlace(
        miffId: String,
        placeId: String,
    ): Unit = throw IllegalStateException("disk full")

    override fun forgetMiff(miffId: String): Unit = throw IllegalStateException("disk full")

    override fun retainOnly(miffIds: Set<String>): Unit = throw IllegalStateException("disk full")

    override fun clear(): Unit = throw IllegalStateException("disk full")
}
