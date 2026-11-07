package com.tomessa.miff.ui.feed

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.tomessa.miff.domain.feed.Locate
import com.tomessa.miff.domain.feed.PositionFix
import com.tomessa.miff.domain.miff.MiffMember

/*
 * SECURITY and PRIVACY, for this file: the Feed hands three things to the applications of the SYSTEM and does nothing else with them.
 * - "Écrire à": the messaging application, with the number of the card prepared; Miff sends nothing.
 * - "Appeler": the DIALLER with the number prepared (ACTION_DIAL, never ACTION_CALL: no call is placed by Miff). No permission "appel",
 *   "SMS" or "contacts" exists in the manifest, and none is needed.
 * - "Ouvrir dans les cartes": the maps application, on the COORDINATES ALONE (no name, no label, no person).
 * A number is checked AGAIN here against the shape E.164 (a "+" and digits): nothing else can reach an address of an intent. A position
 * is never written to a log, the clipboard or a share message. When no application answers, nothing happens and the caller says so calmly.
 */
object FeedIntents {
    /** True when an application took it. */
    fun message(
        context: Context,
        phone: String,
    ): Boolean {
        val number = MiffMember.phoneFromServer(phone) ?: return false
        return start(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")))
    }

    fun call(
        context: Context,
        phone: String,
    ): Boolean {
        val number = MiffMember.phoneFromServer(phone) ?: return false
        return start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
    }

    /** The `geo:` address first; if no maps application answers, the page of Google Maps in the browser. */
    fun openMap(
        context: Context,
        fix: PositionFix,
    ): Boolean =
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse(Locate.geoUri(fix)))) ||
            start(context, Intent(Intent.ACTION_VIEW, Uri.parse(Locate.webMapUrl(fix))))

    private fun start(
        context: Context,
        intent: Intent,
    ): Boolean =
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
}
