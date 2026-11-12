package com.tomessa.miff.data.permission

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tomessa.miff.domain.notification.SecondChance
import com.tomessa.miff.domain.permission.ApiLevel
import com.tomessa.miff.domain.permission.PermissionNames
import com.tomessa.miff.domain.permission.PermissionSnapshot

/*
 * SECURITY / PRIVACY, for this file: it only READS THE STATE of the permissions (checkSelfPermission). It never reads a position (no
 * LocationManager, no fused provider: PermissionPrivacyTest checks the sources), never logs, never sends an event (criterion AUT7, decision (i)).
 */

/** What the phone says now about the permissions of Miff (a double in tests). */
fun interface DevicePermissions {
    fun snapshot(): PermissionSnapshot
}

class AndroidDevicePermissions(
    context: Context,
    private val sdk: Int = Build.VERSION.SDK_INT,
) : DevicePermissions {
    private val appContext = context.applicationContext

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    override fun snapshot(): PermissionSnapshot {
        val fine = granted(PermissionNames.FINE_LOCATION)
        return PermissionSnapshot(
            sdk = sdk,
            fineLocation = fine,
            coarseLocation = granted(PermissionNames.COARSE_LOCATION),
            // Before Android 10 the precise permission already covers the background.
            backgroundLocation = if (sdk >= ApiLevel.Q) granted(PermissionNames.BACKGROUND_LOCATION) else fine,
            notifications =
                if (sdk >= ApiLevel.TIRAMISU) {
                    granted(PermissionNames.POST_NOTIFICATIONS) &&
                        NotificationManagerCompat.from(appContext).areNotificationsEnabled()
                } else {
                    true
                },
        )
    }
}

/**
 * What the phone remembers about the notification permission: the screen ("amorce") already shown to this account on this phone (it is shown once, never
 * again, and it never blocks), whether the system window was ever launched by Miff (a fact of the PHONE: the system does not tell "never asked" from
 * "refused for good"), and the state of the "second chance" card of the Feed (NOTIF-1A).
 */
interface NotificationAskStore {
    fun wasShown(uid: String): Boolean

    fun markShown(uid: String)

    /** The account was deleted (AUTH-5): nothing is kept for it. */
    fun forget(uid: String) {}

    /** The system window of POST_NOTIFICATIONS was launched at least once on this phone. */
    fun systemAsked(): Boolean = false

    fun markSystemAsked() {}

    fun secondChance(uid: String): SecondChance = SecondChance.NONE

    fun setSecondChance(
        uid: String,
        value: SecondChance,
    ) {}
}

/** A flag per account in private preferences (no secret, no position). The preference file is not part of any backup (see backup_rules.xml). */
class SharedPreferencesNotificationAskStore(
    context: Context,
) : NotificationAskStore {
    private val prefs =
        context.applicationContext.getSharedPreferences(
            "miff_notification_screen",
            Context.MODE_PRIVATE,
        )

    override fun wasShown(uid: String): Boolean = prefs.getBoolean(uid, false)

    override fun markShown(uid: String) {
        prefs.edit().putBoolean(uid, true).apply()
    }

    override fun forget(uid: String) {
        prefs
            .edit()
            .remove(uid)
            .remove(secondChanceKey(uid))
            .apply()
    }

    override fun systemAsked(): Boolean = prefs.getBoolean(KEY_SYSTEM_ASKED, false)

    override fun markSystemAsked() {
        prefs.edit().putBoolean(KEY_SYSTEM_ASKED, true).apply()
    }

    override fun secondChance(uid: String): SecondChance =
        prefs.getString(secondChanceKey(uid), null)?.let { name ->
            SecondChance.entries.firstOrNull { it.name == name }
        }
            ?: SecondChance.NONE

    override fun setSecondChance(
        uid: String,
        value: SecondChance,
    ) {
        prefs.edit().putString(secondChanceKey(uid), value.name).apply()
    }

    private fun secondChanceKey(uid: String) = "second_chance:$uid"

    private companion object {
        /** Not a uid: an account identifier never starts with "__". */
        const val KEY_SYSTEM_ASKED = "__system_asked"
    }
}
