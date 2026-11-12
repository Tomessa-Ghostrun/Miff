package com.tomessa.miff.data.notification

import androidx.annotation.StringRes
import com.tomessa.miff.R
import com.tomessa.miff.domain.notification.NotificationType

/**
 * The words of each notification: the ones the PM wrote (ticket section 20.1, mot pour mot, tutoiement). Used by the notification of the system AND by
 * the line of the centre, so that they can never differ. None holds a name, an hour, a place or a count: the text is true even when the
 * notification arrives late (criterion N-M7).
 */
object NotificationTexts {
    @StringRes
    fun title(type: NotificationType): Int =
        when (type) {
            NotificationType.N1 -> R.string.notif_n1_title
            NotificationType.N2 -> R.string.notif_n2_title
            NotificationType.N3 -> R.string.notif_n3_title
            NotificationType.N4 -> R.string.notif_n4_title
            NotificationType.N6 -> R.string.notif_n6a_title
            NotificationType.N7 -> R.string.notif_n7_title
            NotificationType.N9 -> R.string.notif_center_circle_update_title
        }

    @StringRes
    fun body(type: NotificationType): Int =
        when (type) {
            NotificationType.N1 -> R.string.notif_n1_body
            NotificationType.N2 -> R.string.notif_n2_body
            NotificationType.N3 -> R.string.notif_n3_body
            NotificationType.N4 -> R.string.notif_n4_body
            NotificationType.N6 -> R.string.notif_n6a_body
            NotificationType.N7 -> R.string.notif_n7_body
            NotificationType.N9 -> R.string.notif_circle_update_body
        }

    /** The label of the switch of the type in Settings > Notifications. */
    @StringRes
    fun switchLabel(type: NotificationType): Int =
        when (type) {
            NotificationType.N1 -> R.string.notif_settings_n1
            NotificationType.N2 -> R.string.notif_settings_n2
            NotificationType.N3 -> R.string.notif_settings_n3
            NotificationType.N4 -> R.string.notif_settings_n4
            NotificationType.N6 -> R.string.notif_settings_n6
            NotificationType.N7 -> R.string.notif_settings_n7
            NotificationType.N9 -> R.string.notif_settings_circle_update
        }
}
