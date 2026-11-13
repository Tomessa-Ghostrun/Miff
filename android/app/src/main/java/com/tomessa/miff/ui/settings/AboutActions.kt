package com.tomessa.miff.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.tomessa.miff.BuildConfig
import com.tomessa.miff.data.review.FeedbackFormLauncher
import com.tomessa.miff.data.review.SupportMail
import com.tomessa.miff.domain.review.FeedbackForm
import com.tomessa.miff.ui.auth.LegalLinks

/**
 * What the screen Settings > "À propos et aide" does OUTSIDE the app, each one a single explicit touch of the person (ticket reglages-a-propos.md). A seam so that
 * the screen is tested without a browser or an e-mail application.
 */
interface AboutActions {
    /** The address of the external feedback form, or null when there is none (empty or not HTTPS): then the line "Un retour sur l'app ?" is not drawn (AP8). */
    val feedbackFormUrl: String?

    /** The support address (the provisional contact of CLAUDE.md, from `BuildConfig`), shown only when no e-mail application can be opened (AP6). */
    val supportEmail: String

    /** Opens the e-mail application on the support address, with NO subject and NO text. False when nothing could be opened. */
    fun openContact(): Boolean

    /** Opens the form in the browser, the address AS IT IS (AP9): nothing is added to it. False when nothing could be opened. */
    fun openFeedbackForm(): Boolean

    fun openTerms()

    fun openPrivacyPolicy()

    /** Puts [text] in the clipboard (the version, or the support address) and nothing else. False when the system refused (the screen then does not say "Copié"). */
    fun copy(text: String): Boolean
}

class AndroidAboutActions(
    private val context: Context,
    /** The configured address: `BuildConfig.FEEDBACK_FORM_URL`, the only place it is read from (AP19). */
    configuredFormUrl: String = BuildConfig.FEEDBACK_FORM_URL,
) : AboutActions {
    override val feedbackFormUrl: String? = FeedbackForm.openableUrl(configuredFormUrl)
    override val supportEmail: String = BuildConfig.SUPPORT_EMAIL

    override fun openContact(): Boolean = SupportMail.open(context)

    override fun openFeedbackForm(): Boolean = feedbackFormUrl?.let { FeedbackFormLauncher.open(context, it) } ?: false

    override fun openTerms() = LegalLinks.open(context, LegalLinks.termsUrl(context))

    override fun openPrivacyPolicy() = LegalLinks.open(context, LegalLinks.privacyUrl(context))

    override fun copy(text: String): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        return try {
            clipboard.setPrimaryClip(ClipData.newPlainText("Miff", text))
            true
        } catch (e: SecurityException) {
            // The system refused (a restriction of some phones): nothing was copied, and the screen must not say so.
            false
        }
    }
}
