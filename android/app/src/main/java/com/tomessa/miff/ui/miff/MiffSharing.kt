package com.tomessa.miff.ui.miff

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import com.tomessa.miff.BuildConfig
import com.tomessa.miff.R
import com.tomessa.miff.domain.auth.legalPageUrl
import com.tomessa.miff.domain.miff.JoinCode
import com.tomessa.miff.domain.miff.MiffName

/*
 * SECURITY, for this file: the code is a secret that the person chooses to hand over. It goes only into the share sheet of the
 * system (the person picks who gets it) and into the clipboard (marked sensitive on Android 13+). It is never logged. The app
 * never reads the clipboard by itself: only [pastedText], on a tap of "Coller".
 */
object MiffSharing {
    /** The page people land on to get the app (decision QM14): the public site until a dedicated page exists. */
    fun downloadUrl(): String = legalPageUrl(BuildConfig.LEGAL_BASE_URL, "")

    /** The message of the share sheet, in the language of the sender (ticket 10, case 24): name, code, link. Nothing else. */
    fun message(
        context: Context,
        miffName: String,
        code: String,
    ): String =
        context.getString(R.string.miff_share_message, MiffName.clean(miffName), JoinCode.grouped(code), downloadUrl())

    /** Opens the share sheet. If nothing can share, nothing happens (no crash, no technical message). */
    fun share(
        context: Context,
        miffName: String,
        code: String,
    ) {
        val send =
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, message(context, miffName, code))
        try {
            context.startActivity(
                Intent
                    .createChooser(
                        send,
                        context.getString(R.string.miff_share_chooser_title),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: ActivityNotFoundException) {
            // No app can take the text: the person can still copy the code.
        }
    }

    /**
     * "Prévenir mes proches" (decision QM9): opens the share sheet with a short message that the creator sends THEMSELVES, to whom
     * they choose. The app sends nothing and cannot know who received it. The message holds the name of the Miff and nothing else:
     * never the code, never an identifier.
     */
    fun shareClosingNotice(
        context: Context,
        miffName: String,
    ) {
        val send =
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(
                    Intent.EXTRA_TEXT,
                    context.getString(R.string.miff_delete_notice_message, MiffName.clean(miffName)),
                )
        try {
            context.startActivity(
                Intent
                    .createChooser(send, context.getString(R.string.miff_delete_notify_action))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: ActivityNotFoundException) {
            // No app can take the text: nothing happens, and nothing is lost (the deletion does not depend on this).
        }
    }

    /**
     * Copies the code alone. From Android 13 the system shows its own confirmation and hides the content (marked sensitive):
     * returns true when the app has to confirm itself ("Code copié", older versions).
     */
    fun copy(
        context: Context,
        code: String,
    ): Boolean {
        val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
        // The label never holds the code: it is shown by some keyboards.
        val clip = ClipData.newPlainText(context.getString(R.string.android_app_name), code)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        }
        manager.setPrimaryClip(clip)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    }

    /** What the clipboard holds, read ONLY when the person taps "Coller" (criterion J3). Null if empty. */
    fun pastedText(context: Context): String? {
        val manager = context.getSystemService(ClipboardManager::class.java) ?: return null
        val clip = manager.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()
    }
}
