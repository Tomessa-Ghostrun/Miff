package com.tomessa.miff.data.permission

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
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

/** "The notification screen was shown to this account on this phone": it is shown once, never again (it never blocks). */
interface NotificationAskStore {
    fun wasShown(uid: String): Boolean

    fun markShown(uid: String)

    /** The account was deleted (AUTH-5): nothing is kept for it. */
    fun forget(uid: String) {}
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
        prefs.edit().remove(uid).apply()
    }
}
