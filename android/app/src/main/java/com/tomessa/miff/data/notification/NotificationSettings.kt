package com.tomessa.miff.data.notification

import android.content.Context
import androidx.core.content.edit
import com.tomessa.miff.domain.notification.NotificationPreferences
import com.tomessa.miff.domain.notification.NotificationType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/*
 * PRIVACY, for this file: the choices of Settings > Notifications stay on the phone (private preferences, outside every backup: see backup_rules.xml and
 * data_extraction_rules.xml). Nothing is written to the account (decision Q16 of the ticket, phase 0: no server), nothing is sent as an event.
 */

/** Where the choices are kept (a double in tests). */
interface NotificationPreferencesStore {
    fun load(): NotificationPreferences

    fun save(preferences: NotificationPreferences)

    /** The sign-out: back to the default values, and nothing left on the disk. */
    fun clear()
}

class SharedPreferencesNotificationPreferencesStore(
    context: Context,
) : NotificationPreferencesStore {
    private val prefs = context.applicationContext.getSharedPreferences("miff_notification_prefs", Context.MODE_PRIVATE)

    override fun load(): NotificationPreferences {
        val defaults = NotificationPreferences()
        return NotificationPreferences(
            enabled = prefs.getBoolean(KEY_ENABLED, defaults.enabled),
            n1 = prefs.getBoolean(NotificationType.N1.code, defaults.n1),
            n2 = prefs.getBoolean(NotificationType.N2.code, defaults.n2),
            n3 = prefs.getBoolean(NotificationType.N3.code, defaults.n3),
            n4 = prefs.getBoolean(NotificationType.N4.code, defaults.n4),
            n6 = prefs.getBoolean(NotificationType.N6.code, defaults.n6),
            n7 = prefs.getBoolean(NotificationType.N7.code, defaults.n7),
            n9 = prefs.getBoolean(NotificationType.N9.code, defaults.n9),
            nightCalm = prefs.getBoolean(KEY_NIGHT, defaults.nightCalm),
        )
    }

    // commit(): a switch turned off must be on the disk when a receiver wakes a cold process a moment later (nothing may be shown after "off").
    override fun save(preferences: NotificationPreferences) {
        prefs.edit(commit = true) {
            putBoolean(KEY_ENABLED, preferences.enabled)
            NotificationType.PERIMETER.forEach { putBoolean(it.code, preferences.switchOf(it)) }
            putBoolean(KEY_NIGHT, preferences.nightCalm)
        }
    }

    override fun clear() {
        prefs.edit(commit = true) { clear() }
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_NIGHT = "night_calm"
    }
}

/**
 * The choices of Settings > Notifications, in memory and on the disk. Whoever plans notifications observes [preferences]: a change is the signal to
 * look again at what is waiting (a switch turned off cancels, within the second, what it covered).
 */
class NotificationSettings(
    private val store: NotificationPreferencesStore,
) {
    private val state = MutableStateFlow(store.load())

    val preferences: StateFlow<NotificationPreferences> = state.asStateFlow()

    /** The value NOW (a receiver that wakes a cold process reads the disk, not a stale copy). */
    fun current(): NotificationPreferences = store.load().also { state.value = it }

    fun setEnabled(enabled: Boolean) = change { it.copy(enabled = enabled) }

    fun setType(
        type: NotificationType,
        on: Boolean,
    ) = change { it.with(type, on) }

    fun setNightCalm(on: Boolean) = change { it.copy(nightCalm = on) }

    /** The sign-out and the deletion of the account: the next person starts from the default values. */
    fun reset() {
        store.clear()
        state.value = NotificationPreferences()
    }

    private fun change(transform: (NotificationPreferences) -> NotificationPreferences) {
        state.update { old ->
            val updated = transform(old)
            if (updated != old) store.save(updated)
            updated
        }
    }
}
