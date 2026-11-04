package com.tomessa.miff.domain.permission

/*
 * LOC-2a: the permissions of the phone (location, notifications) as the screens of consent need to reason about them.
 * PURE: nothing here asks the system for anything, and above all NOTHING READS A POSITION (criterion AUT7: no position is read before
 * the person agreed, and this ticket reads none at all). The Android calls are in data/permission.
 */

/** Android versions the rules below depend on, as API levels (this file stays free of the Android SDK so that it is tested on the JVM). */
object ApiLevel {
    /** Android 10: the background location permission appears, and it can still be asked together with the precise one. */
    const val Q = 29

    /** Android 11: "all the time" can no longer be granted from the system window, only from the settings of the app. */
    const val R = 30

    /** Android 13: notifications become a permission asked at run time. */
    const val TIRAMISU = 33
}

/** The system names of the permissions this ticket declares and asks for (the manifest holds the same four). */
object PermissionNames {
    const val FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
    const val COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
    const val BACKGROUND_LOCATION = "android.permission.ACCESS_BACKGROUND_LOCATION"
    const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
}

/** What the phone says right now about the permissions of Miff. */
data class PermissionSnapshot(
    /** The Android API level of the phone. */
    val sdk: Int,
    val fineLocation: Boolean,
    val coarseLocation: Boolean,
    /** Before Android 10 there is no such permission: the precise one already covers the background, so this is true with it. */
    val backgroundLocation: Boolean,
    /** Before Android 13 there is no such permission (notifications are on unless the person turned them off in the settings). */
    val notifications: Boolean,
) {
    /**
     * Miff works only with the precise location ALL THE TIME (decision of the PM): both are needed. On Android 12+ the person may give
     * the approximate position only: that is not enough (a geofence needs the precise one), so [coarseLocation] alone never counts.
     */
    val locationComplete: Boolean get() = fineLocation && (sdk < ApiLevel.Q || backgroundLocation)

    /** Is there a notification permission to ask at all on this phone? (Android 13+.) */
    val notificationsApplicable: Boolean get() = sdk >= ApiLevel.TIRAMISU

    /** The notifications are asked (or can be) and are not granted yet. */
    val notificationsMissing: Boolean get() = notificationsApplicable && !notifications
}
