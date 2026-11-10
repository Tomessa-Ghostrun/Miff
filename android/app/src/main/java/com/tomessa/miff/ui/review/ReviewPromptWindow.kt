package com.tomessa.miff.ui.review

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.tomessa.miff.R
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.theme.ShadowDefault
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.StrokeAccent
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorBorderAction
import com.tomessa.miff.ui.theme.colorBrandPrimary
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.miffShadow
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textTitleBold20
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The review window "Que penses-tu de Miff ?" (mockup `design/exports/pop-ins/03-avis-store.png`, built by decision of the PM of 2026-11-07, risk assumed:
 * `docs/tickets/pop-ins-avis.md`). Stateless: [ReviewPromptHost] feeds it.
 *
 * Differences from the mockup, all listed in the pull request: NO STARS and two buttons "J'adore !" and "Peux faire mieux..." (decision of the PM of
 * 2026-11-08), informal "tu" (decision of 2026-11-01), a small cross at the top (the mockup has no way
 * out except the back gesture, which nobody can see), a thin frame like the other dialogs of the app, the page behind blurred where the phone allows it
 * (Android 12 and later, if the system's window blur is on) and dimmed a little by the window everywhere else.
 */
@Composable
fun ReviewPromptWindow(
    stage: ReviewStage,
    onLove: () -> Unit,
    onBetter: () -> Unit,
    onClose: () -> Unit,
    onWrite: () -> Unit,
    onLater: () -> Unit,
) {
    if (stage == ReviewStage.Hidden) return
    Dialog(
        onDismissRequest = onClose,
        properties =
            DialogProperties(
                // "Merci !" lasts a moment and moves on by itself: the back gesture does nothing there. A tap beside the card never closes (it would count as a closure).
                dismissOnBackPress = stage != ReviewStage.ThanksForLove,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
    ) {
        BlurBehindWindow()
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ReviewCard {
                when (stage) {
                    ReviewStage.Question -> QuestionContent(onLove, onBetter, onClose)
                    ReviewStage.ThanksForLove -> ThanksContent()
                    ReviewStage.ThanksForFeedback -> FeedbackContent(onWrite, onLater)
                    ReviewStage.Hidden -> Unit
                }
            }
        }
    }
}

/**
 * What is behind the window: dimmed a little by the window itself (all of the screen, system bars included: the insets stay the business of ui/components/ScreenInsets.kt)
 * and, on Android 12 and later when the phone allows it, blurred by the system, like the mockup.
 */
@Composable
private fun BlurBehindWindow() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(DIM_AMOUNT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.also { it.blurBehindRadius = BLUR_RADIUS_PX }
            }
        }
    }
}

@Composable
private fun ReviewCard(content: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .padding(Spacing.m)
                .widthIn(max = Spacing.dialogWidth)
                .fillMaxWidth()
                .miffShadow(ShadowDefault, ShapeMd)
                .clip(ShapeMd)
                .background(colorBgSurface)
                .border(StrokeAccent, colorBorderAction, ShapeMd)
                // Reading order for the screen reader: the title, the buttons, and the cross last.
                .semantics { isTraversalGroup = true }
                // The marker of this window: its code is absent from a release build (BuildConfig.REVIEW_PROMPT is false there), and the check of the
                // build looks for this very text in it (ReviewPromptReleaseTest, android.yml). Also the handle of the UI tests.
                .testTag(REVIEW_PROMPT_PROBE),
    ) {
        content()
    }
}

@Composable
private fun QuestionContent(
    onLove: () -> Unit,
    onBetter: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // A row of its own for the cross, so that it never covers the title (large text, other languages).
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            val closeName = stringResource(R.string.review_close_a11y)
            Box(
                modifier =
                    Modifier
                        .padding(top = Spacing.xs, end = Spacing.xs)
                        .size(Spacing.minTouchTarget)
                        .clip(ShapeMd)
                        .clickable(role = Role.Button, onClick = onClose)
                        .semantics {
                            contentDescription = closeName
                            traversalIndex = CLOSE_ORDER
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_loc_close),
                    contentDescription = null,
                    tint = colorTextDescription,
                    modifier = Modifier.size(Spacing.l),
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m + Spacing.xs).padding(bottom = Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.review_title),
                style = textTitleBold20,
                color = colorActionPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Spacing.l))
            MiffButton(
                text = stringResource(R.string.review_love_action),
                onClick = onLove,
                modifier = Modifier.fillMaxWidth().semantics { traversalIndex = ACTION_ORDER },
            )
            MiffButton(
                text = stringResource(R.string.review_better_action),
                onClick = onBetter,
                modifier = Modifier.fillMaxWidth().semantics { traversalIndex = ACTION_ORDER + 1f },
            )
        }
    }
}

@Composable
private fun ThanksContent() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.m + Spacing.xs, vertical = Spacing.l)
                // Said once by the screen reader when it appears; there is nothing to press.
                .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.review_thanks_title),
            style = textTitleBold20,
            color = colorActionPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.review_thanks_body),
            style = textBodyRegular,
            color = colorTextDescription,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FeedbackContent(
    onWrite: () -> Unit,
    onLater: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.m + Spacing.xs, vertical = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.review_feedback_title),
            style = textTitleBold20,
            color = colorActionPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.review_feedback_body),
            style = textBodyRegular,
            color = colorTextDescription,
            textAlign = TextAlign.Center,
        )
        MiffButton(
            text = stringResource(R.string.review_feedback_write),
            onClick = onWrite,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = onLater,
            modifier = Modifier.heightIn(min = Spacing.minTouchTarget),
        ) {
            Text(stringResource(R.string.review_feedback_later), style = textBodyBold16, color = colorActionPrimary)
        }
    }
}

/**
 * The text that marks this window inside the code of the app: a release build must not contain it (the window is behind `BuildConfig.REVIEW_PROMPT`, false
 * there, so the code is removed). Do not rename without changing `android.yml` and `ReviewPromptReleaseTest`.
 */
const val REVIEW_PROMPT_PROBE = "REVIEWPROMPTPROBE"

private const val DIM_AMOUNT = 0.3f
private const val BLUR_RADIUS_PX = 40
private const val CLOSE_ORDER = 10f
private const val ACTION_ORDER = 5f
