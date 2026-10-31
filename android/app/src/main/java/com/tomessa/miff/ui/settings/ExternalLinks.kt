package com.tomessa.miff.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.tomessa.miff.BuildConfig

/**
 * What Settings > Confidentialité opens OUTSIDE the app: the settings of the phone for Miff, and the e-mail application.
 * Each one is one explicit tap of the person; if nothing can open it, nothing happens (no crash, no technical message).
 * The addresses of the legal pages live in ONE place, [com.tomessa.miff.ui.auth.LegalLinks] (base address in `BuildConfig`,
 * path per language in the resources, checked against `web/src/pages.json` by a test).
 */
object ExternalLinks {
    /** The page of Miff in the settings of the phone: permissions (location, notifications...) are changed there. */
    fun openPhoneSettings(context: Context) {
        val intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        start(context, intent)
    }

    /** The notification settings of Miff in the phone (Android 8 and later). */
    fun openNotificationSettings(context: Context) {
        val intent =
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        start(context, intent)
    }

    /**
     * Opens the e-mail application with the support address and nothing else: no subject that names the person, no
     * diagnostic data, no attachment. The address is the provisional contact of CLAUDE.md (`BuildConfig.SUPPORT_EMAIL`).
     */
    fun contactSupport(context: Context) {
        start(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + BuildConfig.SUPPORT_EMAIL)))
    }

    private fun start(
        context: Context,
        intent: Intent,
    ) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            // Nothing can open it: the person can still reach the same thing by hand.
        }
    }
}
