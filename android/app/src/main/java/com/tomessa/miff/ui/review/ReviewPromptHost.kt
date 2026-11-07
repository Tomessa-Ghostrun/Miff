package com.tomessa.miff.ui.review

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tomessa.miff.AppContainer
import com.tomessa.miff.BuildConfig
import com.tomessa.miff.data.review.PlayStoreReviewRequester
import com.tomessa.miff.data.review.SharedPreferencesReviewStateStore
import com.tomessa.miff.data.review.StoreListing
import com.tomessa.miff.data.review.StoreReviewRequester
import com.tomessa.miff.domain.review.ReviewPromptDefaults
import com.tomessa.miff.domain.review.ReviewSituation
import com.tomessa.miff.ui.settings.ExternalLinks
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/**
 * What the review window needs from the outside: its view model, the count of returns to the foreground, the request of the store and the way to write to the
 * support. Only built when `BuildConfig.REVIEW_PROMPT` is true (see [rememberReviewHost]): a release build has none.
 */
class ReviewHost(
    val viewModel: ReviewPromptViewModel,
    val foregroundTicks: StateFlow<Int>,
    val requester: StoreReviewRequester,
    /** Opens the e-mail application on the support address, with nothing else in the message ([ExternalLinks.contactSupport]). */
    val writeToSupport: (Context) -> Unit = ExternalLinks::contactSupport,
)

/** The host of the window, or null when this build has no review window (`BuildConfig.REVIEW_PROMPT` is false: release and releaseLike by default). */
@Composable
fun rememberReviewHost(
    container: AppContainer,
    scopeKey: String,
): ReviewHost? {
    if (!BuildConfig.REVIEW_PROMPT) return null
    val appContext = LocalContext.current.applicationContext
    val viewModel: ReviewPromptViewModel =
        viewModel(
            key = "review-$scopeKey",
            factory =
                viewModelFactory {
                    initializer {
                        ReviewPromptViewModel(
                            store = SharedPreferencesReviewStateStore(appContext),
                            nowMillis = System::currentTimeMillis,
                            debugForceAllowed = BuildConfig.DEBUG,
                        )
                    }
                },
        )
    return remember(viewModel, container) {
        ReviewHost(viewModel, container.foregroundTicks.ticks, PlayStoreReviewRequester())
    }
}

/**
 * Puts the review window over the tabs. At each return to the foreground (not a rotation, not a trip to the Settings), after a short moment for the Feed
 * to settle, it asks the rules ([com.tomessa.miff.domain.review.ReviewPromptRules]) whether the window may appear NOW.
 */
@Composable
fun ReviewPromptHost(
    host: ReviewHost,
    situation: ReviewSituation,
) {
    val viewModel = host.viewModel
    val context = LocalContext.current
    val stage by viewModel.stage.collectAsStateWithLifecycle()
    val tick by host.foregroundTicks.collectAsStateWithLifecycle()
    // Read when the moment comes, not when the return happened: the Feed has settled, the person may have changed tab.
    val currentSituation by rememberUpdatedState(situation)

    LaunchedEffect(situation.hasMiff) {
        if (situation.hasMiff) viewModel.onMiffSeen()
    }
    LaunchedEffect(tick) {
        if (viewModel.hasHandled(tick)) return@LaunchedEffect
        delay(ReviewPromptDefaults.SETTLE_MILLIS)
        viewModel.onForeground(tick, currentSituation)
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ReviewEffect.RequestStoreReview -> {
                    val activity = context.findActivity()
                    if (activity != null) host.requester.request(activity) else StoreListing.open(context)
                }

                ReviewEffect.OpenMail -> {
                    host.writeToSupport(context)
                }
            }
        }
    }
    ReviewPromptWindow(
        stage = stage,
        onStar = viewModel::onStarTouched,
        onLove = viewModel::onLove,
        onBetter = viewModel::onCanDoBetter,
        onClose = viewModel::onClose,
        onWrite = viewModel::onWrite,
        onLater = viewModel::onLater,
    )
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
