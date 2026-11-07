package com.tomessa.miff.data.privacy

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

/** Where the pending withdrawals are kept on the device: `key -> time` pairs. Not personal data (see [PendingWithdrawals]). */
interface WithdrawalStorage {
    fun read(): Map<String, Long>

    /** Written to disk BEFORE returning: the mark must survive a process killed right after the tap. */
    fun write(entries: Map<String, Long>)
}

class SharedPreferencesWithdrawalStorage(
    context: Context,
) : WithdrawalStorage {
    private val prefs = context.applicationContext.getSharedPreferences("miff_consent_pending", Context.MODE_PRIVATE)

    override fun read(): Map<String, Long> =
        prefs
            .getStringSet(KEY, emptySet())
            .orEmpty()
            .mapNotNull { entry ->
                val parts = entry.split(SEPARATOR)
                val time = parts.getOrNull(1)?.toLongOrNull()
                if (parts.size == 2 && parts[0].isNotEmpty() && time != null) parts[0] to time else null
            }.toMap()

    override fun write(entries: Map<String, Long>) {
        prefs.edit(commit = true) { putStringSet(KEY, entries.map { (key, time) -> "$key$SEPARATOR$time" }.toSet()) }
    }

    private companion object {
        const val KEY = "pending"
        const val SEPARATOR = ":"
    }
}

/**
 * The withdrawals of the "statistiques et rapports de plantage" consent that the account does not know about yet (the person
 * switched it off without a network). Kept on the device so that the choice survives a restart: while a withdrawal is pending,
 * the collection stays OFF at every start, whatever the account still says.
 *
 * Only `key + time` is kept, never a name, an e-mail, a position or the user id itself: the key is a one-way hash of the id
 * of the account (cut to 32 characters), enough to follow the right account and to never apply a withdrawal to another one.
 * The file is private to the app and excluded from every backup and transfer (`backup_rules.xml`,
 * `data_extraction_rules.xml`). At most [MAX_ENTRIES] accounts are remembered (the oldest is dropped).
 */
class PendingWithdrawals(
    private val storage: WithdrawalStorage,
    private val nowMillis: () -> Long,
) {
    private val state = MutableStateFlow(storage.read())

    /** Is a withdrawal waiting for THIS account? */
    fun isPending(uid: String): Boolean = keyOf(uid) in state.value

    /** The same, followed over time (the Settings screen shows it). */
    fun pendingFlow(uid: String): Flow<Boolean> {
        val key = keyOf(uid)
        return state.map { key in it }.distinctUntilChanged()
    }

    /** When the withdrawal of this account was made, or null. */
    fun pendingSince(uid: String): Long? = state.value[keyOf(uid)]

    val entries: StateFlow<Map<String, Long>> get() = state.asStateFlow()

    fun mark(uid: String) {
        val key = keyOf(uid)
        if (key in state.value) return
        val next =
            (state.value + (key to nowMillis())).entries.sortedBy { it.value }.takeLast(MAX_ENTRIES).associate {
                it.key to
                    it.value
            }
        storage.write(next)
        state.value = next
    }

    fun clear(uid: String) {
        val key = keyOf(uid)
        if (key !in state.value) return
        val next = state.value - key
        storage.write(next)
        state.value = next
    }

    companion object {
        const val MAX_ENTRIES = 20

        fun keyOf(uid: String): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(uid.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
                .take(32)
    }
}
