package com.tomessa.miff.ui.diagnostic

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.tomessa.miff.BuildConfig
import com.tomessa.miff.R
import com.tomessa.miff.data.review.SharedPreferencesReviewStateStore
import com.tomessa.miff.domain.review.FeedbackForm
import com.tomessa.miff.domain.review.ReviewState
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.textBodyRegular14
import java.text.DateFormat
import java.util.Date

/**
 * DEBUG BUILD ONLY. Lets the PM see the review window without waiting 7 or 90 days (docs/qa/checklists/avis-android-recette-pm.md): "show at the next return"
 * (the window then ignores the 7 / 30 / 90 days, but never covers the Update, the keyboard or a message of the app) and "reset" (the state starts from zero).
 * The state is shown in clear: dates and counters, nothing personal. Neither exists in a release build (this file is in the debug source set).
 */
@Composable
fun ReviewDebugCard() {
    val context = LocalContext.current
    val store = remember { SharedPreferencesReviewStateStore(context) }
    var state by remember { mutableStateOf(store.read()) }
    var armed by remember { mutableStateOf(store.forceNext()) }
    var resetDone by remember { mutableStateOf(false) }
    SectionCard(stringResource(R.string.android_diag_review_title)) {
        Text(describe(state), style = textBodyRegular14, color = colorTextPrimary)
        MiffButton(
            text = stringResource(R.string.android_diag_review_force),
            onClick = {
                store.setForceNext(true)
                armed = true
                resetDone = false
            },
        )
        if (armed) {
            Text(
                stringResource(R.string.android_diag_review_force_armed),
                style = textBodyRegular14,
                color = colorTextPrimary,
            )
        }
        MiffButton(
            text = stringResource(R.string.android_diag_review_reset),
            onClick = {
                store.write(ReviewState())
                store.setForceNext(false)
                state = store.read()
                armed = false
                resetDone = true
            },
        )
        if (resetDone) {
            Text(
                stringResource(R.string.android_diag_review_reset_done),
                style = textBodyRegular14,
                color = colorTextPrimary,
            )
        }
    }
}

// Debug text, French only, never translated (the diagnostic screen is for the team).
private fun form(): String =
    if (FeedbackForm.openableUrl(BuildConfig.FEEDBACK_FORM_URL) !=
        null
    ) {
        "renseigné (ouvert dans le navigateur)"
    } else {
        "non renseigné (écran « Merci de nous le dire ! »)"
    }

private fun describe(state: ReviewState): String {
    fun date(millis: Long?): String = millis?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "jamais"
    return "première Miff vue : ${date(
        state.firstMiffSeenAtMillis,
    )} ; dernière apparition : ${date(state.lastShownAtMillis)} ; " +
        "issue : ${state.lastOutcome ?: "aucune"} ; formulaire externe : ${form()}"
}
