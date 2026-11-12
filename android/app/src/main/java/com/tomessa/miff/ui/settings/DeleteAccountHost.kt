package com.tomessa.miff.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tomessa.miff.AppContainer
import com.tomessa.miff.BuildConfig
import com.tomessa.miff.R
import com.tomessa.miff.data.account.AccountDeletionControls
import com.tomessa.miff.data.account.AccountDeletionState
import com.tomessa.miff.data.account.DeletionStep
import com.tomessa.miff.data.session.SessionManager
import com.tomessa.miff.ui.auth.LoadingIndicator
import com.tomessa.miff.ui.components.MiffButton
import com.tomessa.miff.ui.components.miffScreenInsets
import com.tomessa.miff.ui.components.miffTopInsets
import com.tomessa.miff.ui.navigation.googleSourceFor
import com.tomessa.miff.ui.navigation.holdForDiagnostic
import com.tomessa.miff.ui.theme.GradientAuthBackground
import com.tomessa.miff.ui.theme.ShadowDefault
import com.tomessa.miff.ui.theme.ShapeMd
import com.tomessa.miff.ui.theme.Spacing
import com.tomessa.miff.ui.theme.colorActionPrimary
import com.tomessa.miff.ui.theme.colorBgSurface
import com.tomessa.miff.ui.theme.colorStatusSafeTint
import com.tomessa.miff.ui.theme.colorTextDescription
import com.tomessa.miff.ui.theme.colorTextForm
import com.tomessa.miff.ui.theme.colorTextPrimary
import com.tomessa.miff.ui.theme.miffShadow
import com.tomessa.miff.ui.theme.textBodyBold16
import com.tomessa.miff.ui.theme.textBodyRegular
import com.tomessa.miff.ui.theme.textDisplayHeadline
import kotlinx.coroutines.delay

/*
 * The screens of the deletion once it is under way (ticket AUTH-5, mockups PNG 07 to 09): progress, pause without network, resumption
 * at launch, failure, "Dernière vérification", and the end. They have NO back arrow and NO "Annuler" (the point of no return is
 * behind us): the system back does nothing, except on the end screen where it is "Continuer". The screen stays on ONLY while the
 * procedure runs. Tone: informs calmly, never "erreur", "échec" or "quota", never a count of what is left.
 */

/** What the host shows, decided from the state of the controller. */
@Composable
fun AccountDeletionHost(container: AppContainer) {
    val controls: AccountDeletionControls = container.accountDeletion
    AccountDeletionHostFor(
        controls = controls,
        reauthHost = { ReauthLast(container, controls) },
        onLeave = {
            controls.acknowledge()
            container.sessionManager.leaveAfterAccountDeletion()
        },
    )
}

/**
 * The host without the container (what the tests drive): every time it ENTERS the composition (launch, or a new sign-in after "Me reconnecter")
 * it takes the deletion up again from the marker, but only from [AccountDeletionState.Idle] (a run under way or a stop is never overwritten).
 */
@Composable
internal fun AccountDeletionHostFor(
    controls: AccountDeletionControls,
    reauthHost: @Composable () -> Unit,
    onLeave: () -> Unit,
) {
    val state by controls.state.collectAsStateWithLifecycle()
    // Launch with the marker: the session opened this host and the deletion is taken up again, with no new re-authentication.
    LaunchedEffect(Unit) { if (controls.state.value == AccountDeletionState.Idle) controls.resume() }
    AccountDeletionContent(
        state = state,
        controls = controls,
        reauthHost = reauthHost,
        onLeave = onLeave,
    )
}

/** The part of the host that only depends on the state (and is therefore the one the screen tests drive). */
@Composable
fun AccountDeletionContent(
    state: AccountDeletionState,
    controls: AccountDeletionControls,
    reauthHost: @Composable () -> Unit,
    onLeave: () -> Unit,
) {
    val finished = state is AccountDeletionState.Done
    // On ONLY while the procedure runs: not on a pause (no network), a stop, a re-authentication or the end.
    KeepScreenOn(keepsScreenOn(state))
    // Nothing to go back to: the system back is ignored, except on the end screen where it means "Continuer".
    BackHandler(enabled = true) { if (finished) onLeave() }
    when (state) {
        AccountDeletionState.Idle -> {
            Progress(
                title = R.string.settings_delete_progress_title,
                line = null,
                hint = R.string.settings_delete_progress_hint,
            )
        }

        is AccountDeletionState.Running -> {
            val line = stepLine(state.step)
            val longWait = (state.step as? DeletionStep.Miff)?.longWait == true
            when {
                state.retrying -> {
                    Progress(
                        title = R.string.settings_delete_resume_title,
                        line = line ?: stringResource(R.string.settings_delete_retrying),
                        hint = null,
                        longWait = longWait,
                    )
                }

                state.resumed -> {
                    Progress(
                        title = R.string.settings_delete_resume_title,
                        line = line,
                        hint = R.string.settings_delete_resume,
                        longWait = longWait,
                    )
                }

                else -> {
                    Progress(
                        title = R.string.settings_delete_progress_title,
                        line = line,
                        hint = R.string.settings_delete_progress_hint,
                        longWait = longWait,
                    )
                }
            }
        }

        AccountDeletionState.WaitingForNetwork -> {
            Notice(
                icon = R.drawable.ic_loc_cloud_off,
                title = R.string.settings_delete_paused_title,
                body = R.string.settings_delete_paused,
                extra = R.string.settings_delete_paused_leave,
            )
        }

        AccountDeletionState.NeedsRecentLogin -> {
            reauthHost()
        }

        AccountDeletionState.Stuck -> {
            Notice(
                icon = R.drawable.ic_loc_info,
                title = R.string.settings_delete_failed_title,
                body = R.string.settings_delete_failed,
                extra = R.string.settings_delete_paused_leave,
                bodyHasContact = true,
                retry = R.string.settings_delete_retry to controls::retry,
                // The way out of the dead end (AUTH-5b review): the session is closed but the marker STAYS (it primes over the routing), so signing in again with
                // the same account takes the deletion up again. Never a way to abandon it half done.
                secondary = R.string.settings_delete_stuck_signout to controls::signInAgain,
            )
        }

        AccountDeletionState.NeedsSignIn -> {
            // The session cannot be used: NOT "deleted". The marker stays; signing in again with the same account resumes the deletion.
            Notice(
                icon = R.drawable.ic_loc_info,
                title = R.string.settings_delete_failed_title,
                body = R.string.settings_delete_signin_again,
                extra = null,
                retry = R.string.settings_delete_signin_again_action to controls::signInAgain,
            )
        }

        is AccountDeletionState.Done -> {
            DeletionDone(appleLinked = state.appleLinked, onContinue = onLeave)
        }
    }
}

/** On ONLY while the procedure runs: not on a pause (no network), a stop, a re-authentication, a sign-in request or the end. */
internal fun keepsScreenOn(state: AccountDeletionState): Boolean =
    state == AccountDeletionState.Idle || state is AccountDeletionState.Running

/** The screen stays on while the procedure runs, and only then (criterion of the ticket, section 3.2 D). */
@Composable
private fun KeepScreenOn(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun CenteredGradient(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GradientAuthBackground)
                .miffScreenInsets()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically),
    ) {
        Image(
            painter = painterResource(R.drawable.miff_wordmark_color),
            contentDescription = stringResource(R.string.home_logo_a11y),
            modifier = Modifier.width(Spacing.logoWidth / 2),
        )
        Spacer(Modifier.size(Spacing.l))
        content()
    }
}

/**
 * The ONE line of step (mockups D1, D2): "Miff 1 sur 2" while the Miffs go (joined first, then created), "On supprime ton profil…" once they are done,
 * nothing while the account is being read. Never a percentage, never the detail of the lots.
 */
@Composable
private fun stepLine(step: DeletionStep): String? =
    when (step) {
        DeletionStep.Checking -> null
        DeletionStep.Profile -> stringResource(R.string.settings_delete_progress_profile)
        is DeletionStep.Miff -> stringResource(R.string.settings_delete_progress_miff, step.index, step.total)
    }

/**
 * D. Progress: logo, an indeterminate indicator (never a percentage), the title, ONE line of step, the patience sentence; [longWait] adds the calm
 * sentence of mockup D3 (more than three people to take out of the Miffs created).
 */
@Composable
private fun Progress(
    title: Int,
    line: String?,
    hint: Int?,
    longWait: Boolean = false,
) {
    CenteredGradient {
        // One polite region: every change of line is announced by the screen reader; the indicator itself is decorative.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            LoadingIndicator()
            Text(
                text = stringResource(title),
                style = textDisplayHeadline,
                color = colorTextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            if (line != null) {
                Text(
                    text = line,
                    style = textBodyBold16,
                    color = colorActionPrimary,
                    textAlign = TextAlign.Center,
                )
            }
            if (hint != null) {
                Text(
                    text = stringResource(hint),
                    style = textBodyRegular,
                    color = colorTextDescription,
                    textAlign = TextAlign.Center,
                )
            }
            if (longWait) {
                Text(
                    text = stringResource(R.string.settings_delete_progress_long),
                    style = textBodyRegular,
                    color = colorTextDescription,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** E. A pause, or a stop after several tries: what happens, what the person can do, no alarm. */
@Composable
private fun Notice(
    icon: Int,
    title: Int,
    body: Int,
    extra: Int?,
    bodyHasContact: Boolean = false,
    retry: Pair<Int, () -> Unit>? = null,
    secondary: Pair<Int, () -> Unit>? = null,
) {
    CenteredGradient {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            IconTile(icon)
            Text(
                text = stringResource(title),
                style = textDisplayHeadline,
                color = colorTextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = if (bodyHasContact) stringResource(body, BuildConfig.SUPPORT_EMAIL) else stringResource(body),
                style = textBodyRegular,
                color = colorTextDescription,
                textAlign = TextAlign.Center,
            )
            if (extra != null) {
                Text(
                    text = stringResource(extra),
                    style = textBodyRegular,
                    color = colorTextDescription,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (retry != null) {
            MiffButton(
                text = stringResource(retry.first),
                onClick = retry.second,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (secondary != null) {
            TextButton(
                onClick = secondary.second,
                modifier = Modifier.heightIn(min = Spacing.minTouchTarget),
            ) {
                Text(text = stringResource(secondary.first), style = textBodyBold16, color = colorActionPrimary)
            }
        }
    }
}

/**
 * F. "Ton compte a été supprimé": one calm sentence and ONE button; for an account that was also linked to Apple, the encart and the
 * three steps to remove that authorization from the Apple settings (limit accepted by the PM, 2026-11-05: Android cannot do it).
 */
@Composable
fun DeletionDone(
    appleLinked: Boolean,
    onContinue: () -> Unit,
) {
    CenteredGradient {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Column(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(colorStatusSafeTint),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_circle),
                    contentDescription = null, // decorative: the title carries the meaning
                    tint = colorTextForm,
                    modifier = Modifier.size(Spacing.xl),
                )
            }
            Text(
                text = stringResource(R.string.settings_delete_done_title),
                style = textDisplayHeadline,
                color = colorTextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.settings_delete_done_body),
                style = textBodyRegular,
                color = colorTextDescription,
                textAlign = TextAlign.Center,
            )
        }
        if (appleLinked) AppleHowTo()
        MiffButton(
            text = stringResource(R.string.settings_delete_done_continue),
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** H2. How to remove the "Sign in with Apple" authorization by hand (the labels of the Apple menus are to be checked on an iPhone). */
@Composable
private fun AppleHowTo() {
    Column(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.settings_delete_done_apple_android),
            style = textBodyRegular,
            color = colorTextForm,
        )
        Text(
            text = stringResource(R.string.settings_delete_apple_howto_title),
            style = textBodyBold16,
            color = colorTextForm,
            modifier = Modifier.padding(top = Spacing.s),
        )
        listOf(
            R.string.settings_delete_apple_howto_step1,
            R.string.settings_delete_apple_howto_step2,
            R.string.settings_delete_apple_howto_step3,
        ).forEachIndexed { index, step ->
            Text(text = "${index + 1}. ${stringResource(step)}", style = textBodyRegular, color = colorTextDescription)
        }
    }
}

/** C11. "Dernière vérification avant de terminer": the same screen as C, asked by Firebase at the very end. */
@Composable
private fun ReauthLast(
    container: AppContainer,
    controls: AccountDeletionControls,
) {
    val context = LocalContext.current
    val googleSource = remember(context) { googleSourceFor(context) }
    val viewModel: DeleteReauthViewModel =
        viewModel(
            key = "delete-account-last",
            factory =
                viewModelFactory {
                    initializer {
                        DeleteReauthViewModel(
                            gateway = container.authGateway,
                            network = container.networkMonitor,
                            last = true,
                            onVerified = {
                                controls.reauthenticated()
                                true
                            },
                        )
                    }
                },
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The view model outlives this screen (it is kept by its key): when the state leaves "Dernière vérification", nothing stays busy.
    DisposableEffect(viewModel) { onDispose { viewModel.resetBusy() } }
    DeleteAccountReauthScreen(
        state = state,
        // No way back: the point of no return is behind us. "Plus tard" puts the app away; the deletion resumes at the next launch.
        onBack = { context.findActivity()?.moveTaskToBack(true) },
        onSubmitPassword = viewModel::submitPassword,
        onGoogle = { viewModel.submitGoogle(googleSource) },
        onForgotPassword = null,
        onCancelOrLater = { context.findActivity()?.moveTaskToBack(true) },
        onContact = { ExternalLinks.contactSupport(context) },
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

/**
 * G2. The banner above the welcome carousel when the deletion finished behind the screen (app relaunched): one calm sentence, announced
 * by the screen reader, gone by itself after [BANNER_MILLIS] (long enough to be read) or at a touch.
 */
@Composable
fun AccountDeletedBanner(onDismiss: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(BANNER_MILLIS)
        onDismiss()
    }
    Box(modifier = Modifier.fillMaxWidth().miffTopInsets().padding(horizontal = Spacing.l, vertical = Spacing.s)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .miffShadow(ShadowDefault, ShapeMd)
                    .clip(ShapeMd)
                    .background(colorBgSurface)
                    .clickable(onClick = onDismiss)
                    .padding(Spacing.m)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(Spacing.s + Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check_circle),
                contentDescription = null,
                tint = colorActionPrimary,
                modifier = Modifier.size(Spacing.l),
            )
            Text(
                text = stringResource(R.string.auth_account_deleted_notice),
                style = textBodyRegular,
                color = colorTextForm,
            )
        }
    }
}

/** Long enough to be read, short enough not to stay in the way (mockup notes: 6 seconds at least). */
private const val BANNER_MILLIS = 6_000L

/**
 * The deletion was CANCELLED because a Miff kept showing up while it ran (another phone of the account created or joined one, AUTH-5b): the profile and
 * the account were not deleted, the app is back on its normal screen, and this says so once, calmly, with the way forward. "Compris" closes it.
 */
@Composable
fun DeletionBlockedNotice(session: SessionManager) {
    val blocked by session.deletionBlockedNotice.collectAsStateWithLifecycle()
    if (blocked) DeletionBlockedDialog(onDismiss = session::dismissDeletionBlockedNotice)
}

/** The message itself (stateless, so that it can be looked at on its own). */
@Composable
fun DeletionBlockedDialog(onDismiss: () -> Unit) {
    AlertDialog(
        modifier = Modifier.holdForDiagnostic(),
        onDismissRequest = onDismiss,
        text = {
            Text(
                text = stringResource(R.string.settings_delete_miffs_blocked, BuildConfig.SUPPORT_EMAIL),
                style = textBodyRegular,
                color = colorTextForm,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Spacing.minTouchTarget)) {
                Text(stringResource(R.string.miff_lost_ok_action), style = textBodyBold16, color = colorActionPrimary)
            }
        },
        containerColor = colorBgSurface,
    )
}
