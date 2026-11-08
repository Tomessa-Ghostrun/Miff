package com.tomessa.miff.ui.review

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tomessa.miff.AppContainer
import com.tomessa.miff.BuildConfig
import com.tomessa.miff.R
import com.tomessa.miff.data.review.FeedbackFormLauncher
import com.tomessa.miff.data.review.PlayStoreReviewRequester
import com.tomessa.miff.data.review.StoreListing
import com.tomessa.miff.data.review.StoreReviewRequester
import com.tomessa.miff.data.review.SupportMail
import com.tomessa.miff.domain.review.ReviewPromptDefaults
import com.tomessa.miff.domain.review.ReviewSituation
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
    /** Opens the e-mail application on the support address, with nothing else in the message ([SupportMail]); false when nothing can open it. */
    val writeToSupport: (Context) -> Boolean = SupportMail::open,
    /** Opens the feedback form in the browser (HTTPS only, as it is); false when nothing on the phone can open it. */
    val openForm: (Context, String) -> Boolean = FeedbackFormLauncher::open,
)

/** The host of the window, or null when this build has no review window (`BuildConfig.REVIEW_PROMPT` is false: release and releaseLike by default). */
@Composable
fun rememberReviewHost(
    container: AppContainer,
    scopeKey: String,
): ReviewHost? {
    if (!BuildConfig.REVIEW_PROMPT) return null
    val viewModel: ReviewPromptViewModel =
        viewModel(
            key = "review-$scopeKey",
            factory =
                viewModelFactory {
                    initializer {
                        ReviewPromptViewModel(
                            store = container.reviewStateStore,
                            nowMillis = System::currentTimeMillis,
                            debugForceAllowed = BuildConfig.DEBUG,
                            feedbackFormUrl = BuildConfig.FEEDBACK_FORM_URL,
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
    val notOpened = stringResource(R.string.review_open_failed)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        // Only while the app is on screen: "Merci !" may end while the person is already elsewhere, and a store card or a browser must not pop up from the background.
        // The effect waits in the channel until the person is back.
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                if (!host.perform(effect, context)) {
                    // Never a silent gesture: a short calm sentence when nothing could be opened.
                    Toast.makeText(context, notOpened, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    ReviewPromptWindow(
        stage = stage,
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

/** Does what the effect asks and says whether something opened (false = nothing could be, the screen tells the person calmly). */
internal suspend fun ReviewHost.perform(
    effect: ReviewEffect,
    context: Context,
): Boolean =
    when (effect) {
        ReviewEffect.RequestStoreReview -> {
            val activity = context.findActivity()
            if (activity != null) requester.request(activity) else StoreListing.open(context)
        }

        ReviewEffect.OpenMail -> {
            writeToSupport(context)
        }

        // No browser for the address: the e-mail to the support, so that "Peux faire mieux..." never leads nowhere.
        is ReviewEffect.OpenFeedbackForm -> {
            openForm(context, effect.url) || writeToSupport(context)
        }
    }
