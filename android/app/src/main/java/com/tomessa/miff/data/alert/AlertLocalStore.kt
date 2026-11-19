package com.tomessa.miff.data.alert

import android.content.Context

/*
 * PRIVACY, for this file: what the phone remembers about MY alert, and nothing else. A pointer (the identifier of the Miff of origin, the phase, three moments): no name, no
 * position, no text. It lets the alert survive a relaunch of the app and lets the app listen to the right Miff while the Feed shows another one (ticket 6). It is erased at
 * sign-out ([clearAccount]). The indicator `alertHintDone` (the blue bubble of the onboarding) is about the INSTALLATION, not the account: kept at sign-out, erased with a
 * reinstallation (ticket 7.1). A private file, excluded from the backups (res/xml/backup_rules.xml and data_extraction_rules.xml exclude every shared preference).
 */

/** The phase of the pointer: the alert is on its way, or it is in progress. */
enum class PointerPhase {
    SENDING,
    ACTIVE,

    /**
     * The person closed an alert that was waiting or refused: nothing is shown any more, but the pointer stays until the SERVER has said that no alert of mine is in progress there (a write
     * that timed out may still have landed): see [AlertController.cancelPending].
     */
    ABANDONED,
}

/** What is kept. [startedAtMillis] and [expiresAtMillis] only exist once the alert is active. */
class AlertPointer(
    val miffId: String,
    val phase: PointerPhase,
    /** When the 5 s were reached (the phone's clock): the 15 min after which the send is confirmed again start here. */
    val armedAtMillis: Long,
    val startedAtMillis: Long? = null,
    val expiresAtMillis: Long? = null,
    /** The person confirmed an alert older than 15 min: it may leave. */
    val staleConfirmed: Boolean = false,
) {
    override fun toString(): String = "AlertPointer(redacted)"
}

interface AlertLocalStore {
    fun pointer(): AlertPointer?

    fun save(pointer: AlertPointer)

    fun clearPointer()

    /** The blue bubble of the onboarding has done its job (ticket 7.1, AD8). */
    fun hintDone(): Boolean

    fun markHintDone()

    /**
     * The account whose alerts were read ONCE from the server at the connection of a new phone ([adopted]): the next launch of the same account reads nothing (ticket 6, AD42).
     */
    fun adoptedFor(): String?

    fun markAdoptedFor(uid: String)

    /** Sign-out: the pointer and the note of the adoption go; the hint (installation) stays. */
    fun clearAccount()
}

class SharedPreferencesAlertLocalStore(
    context: Context,
) : AlertLocalStore {
    private val prefs = context.applicationContext.getSharedPreferences("miff_alert", Context.MODE_PRIVATE)

    override fun pointer(): AlertPointer? {
        val miffId = prefs.getString(KEY_MIFF, null) ?: return null
        val phase = PointerPhase.entries.firstOrNull { it.name == prefs.getString(KEY_PHASE, null) } ?: return null
        val armed = prefs.getLong(KEY_ARMED, NONE).takeIf { it != NONE } ?: return null
        return AlertPointer(
            miffId = miffId,
            phase = phase,
            armedAtMillis = armed,
            startedAtMillis = prefs.getLong(KEY_STARTED, NONE).takeIf { it != NONE },
            expiresAtMillis = prefs.getLong(KEY_EXPIRES, NONE).takeIf { it != NONE },
            staleConfirmed = prefs.getBoolean(KEY_CONFIRMED, false),
        )
    }

    // apply(): called from the main thread, a write to the disk must not block it.
    override fun save(pointer: AlertPointer) {
        val edit =
            prefs
                .edit()
                .putString(KEY_MIFF, pointer.miffId)
                .putString(KEY_PHASE, pointer.phase.name)
                .putLong(KEY_ARMED, pointer.armedAtMillis)
                .putBoolean(KEY_CONFIRMED, pointer.staleConfirmed)
        if (pointer.startedAtMillis !=
            null
        ) {
            edit.putLong(KEY_STARTED, pointer.startedAtMillis)
        } else {
            edit.remove(KEY_STARTED)
        }
        if (pointer.expiresAtMillis !=
            null
        ) {
            edit.putLong(KEY_EXPIRES, pointer.expiresAtMillis)
        } else {
            edit.remove(KEY_EXPIRES)
        }
        edit.apply()
    }

    override fun clearPointer() {
        prefs
            .edit()
            .remove(KEY_MIFF)
            .remove(KEY_PHASE)
            .remove(KEY_ARMED)
            .remove(KEY_STARTED)
            .remove(KEY_EXPIRES)
            .remove(KEY_CONFIRMED)
            .apply()
    }

    override fun hintDone(): Boolean = prefs.getBoolean(KEY_HINT, false)

    override fun markHintDone() {
        prefs.edit().putBoolean(KEY_HINT, true).apply()
    }

    override fun adoptedFor(): String? = prefs.getString(KEY_ADOPTED, null)

    override fun markAdoptedFor(uid: String) {
        prefs.edit().putString(KEY_ADOPTED, uid).apply()
    }

    override fun clearAccount() {
        clearPointer()
        prefs
            .edit()
            .remove(KEY_ADOPTED)
            .apply()
    }

    private companion object {
        const val NONE = -1L
        const val KEY_MIFF = "miff"
        const val KEY_PHASE = "phase"
        const val KEY_ARMED = "armed"
        const val KEY_STARTED = "started"
        const val KEY_EXPIRES = "expires"
        const val KEY_CONFIRMED = "confirmed"
        const val KEY_HINT = "hint_done"
        const val KEY_ADOPTED = "adopted_for"
    }
}

/** In memory, for tests and previews. */
class MemoryAlertLocalStore : AlertLocalStore {
    private var saved: AlertPointer? = null
    private var hint = false
    private var adopted: String? = null

    override fun pointer(): AlertPointer? = saved

    override fun save(pointer: AlertPointer) {
        saved = pointer
    }

    override fun clearPointer() {
        saved = null
    }

    override fun hintDone(): Boolean = hint

    override fun markHintDone() {
        hint = true
    }

    override fun adoptedFor(): String? = adopted

    override fun markAdoptedFor(uid: String) {
        adopted = uid
    }

    override fun clearAccount() {
        saved = null
        adopted = null
    }
}
