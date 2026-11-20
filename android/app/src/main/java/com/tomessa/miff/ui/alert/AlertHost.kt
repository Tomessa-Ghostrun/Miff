package com.tomessa.miff.ui.alert

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tomessa.miff.R
import com.tomessa.miff.data.alert.AlertEvent
import com.tomessa.miff.data.alert.MyAlert
import com.tomessa.miff.data.alert.SendStep
import com.tomessa.miff.domain.alert.AlertDialTarget
import com.tomessa.miff.domain.alert.AlertFailure
import com.tomessa.miff.ui.feed.FeedIntents
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** What the Update needs for the alert, in one piece (see UpdateTab): null while the function is switched off. */
class AlertUpdateHooks(
    /** The blue bubble of the onboarding is shown (the indicator `alertHintDone` is false). */
    val hintVisible: Boolean,
    val onHintDone: () -> Unit,
    /** The Miff selected in the Feed: the one the alert would go to. */
    val targetMiffName: String?,
    /** The person has several Miffs: the hold screen names the Miff before the alert leaves, and the sentence "Seule cette Miff est prévenue" exists. */
    val several: Boolean,
    /** The Miff of an alert already in progress (in ANY Miff) or null: a hold never starts a second one (D14). */
    val activeAlertMiffName: String?,
    /** The height the banner takes above the tab bar (the shutter sits above it). */
    val bannerInset: Dp,
    /** The 5 seconds were held (or the accessible confirmation was given). */
    val onTrigger: () -> Unit,
    /** "Une alerte est déjà en cours pour la Miff {nom}" ([miffName] is empty when the name is not known). */
    val onAlreadyActive: (miffName: String) -> Unit,
    /** The emergency numbers from a screen without a viewfinder; [stopped]: my sharing in the Miff is stopped (the screen then offers to resume). */
    val onOpenEmergency: (stopped: Boolean) -> Unit = {},
)

/**
 * "Voir la Miff {nom}" on the banner: it names the Miff of origin now that the title does not. Offered when the Feed shows ANOTHER Miff, and as soon as the person has SEVERAL Miffs
 * (review of #306: she always sees which circle was alerted).
 */
fun shouldOfferSeeMiff(
    miffCount: Int,
    shownMiffId: String?,
    originId: String,
): Boolean = miffCount > 1 || (shownMiffId != null && shownMiffId != originId)

/** What the tabs need to host the banner and to open the screen of the call. */
class AlertHost(
    val viewModel: AlertViewModel,
    /** Opens the screen of the call; [stopped]: I do not share with that Miff, the alert did not leave (ticket 12.2); [unavailable]: no alert can be sent now, only the numbers. */
    val onOpenSent: (miffId: String, stopped: Boolean, unavailable: Boolean) -> Unit,
)

/** The screen "avertis" (maquettes 03 and 06): the Miff of origin, and whether it is the calm variant of a stopped sharing. */
@Serializable
data class AlertSentRoute(
    val miffId: String,
    val stopped: Boolean = false,
    /** No alert can be sent now (the Miffs are not known yet, the camera is not available): only the emergency numbers, with a calm sentence (review of #275, M3). */
    val unavailable: Boolean = false,
)

/**
 * The host of the screen of the call: it reads MY alert and the list of who to call, and says only what is true (the title "viennent d'être avertis" only once the server accepted the
 * alert). Calls go through the dialler of the system (never a call placed by Miff). The way back leaves the alert in progress: the banner takes over.
 */
@Composable
fun AlertSentHost(
    viewModel: AlertViewModel,
    route: AlertSentRoute,
    miffName: String?,
    several: Boolean,
    onBack: () -> Unit,
    onResumeSharing: (miffId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val unavailable = stringResource(R.string.feed_action_unavailable)
    val alert by viewModel.alert.collectAsStateWithLifecycle()
    val call by viewModel.call.collectAsStateWithLifecycle()
    var notSent by remember { mutableStateOf(route.stopped) }
    LaunchedEffect(route.miffId) { viewModel.loadCallList(route.miffId) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val failure = (event as? AlertEvent.NotSent)?.failure
            if (failure == AlertFailure.SharingStopped || failure == AlertFailure.NotMember) notSent = true
        }
    }
    // The alert is gone (cancelled while waiting, or its phone was replaced) and nothing explains it: back to where the person was, without a word.
    LaunchedEffect(alert is MyAlert.None, notSent) {
        if (alert is MyAlert.None && !notSent && !route.unavailable) onBack()
    }
    val sending = alert as? MyAlert.Sending
    val phase =
        when {
            route.unavailable -> SentPhase.UNAVAILABLE
            notSent -> SentPhase.SHARING_STOPPED
            alert is MyAlert.Active -> SentPhase.SENT
            sending?.step == SendStep.WAITING_NETWORK -> SentPhase.WAITING
            sending?.step == SendStep.NEEDS_CONFIRMATION -> SentPhase.NEEDS_CONFIRMATION
            sending?.step == SendStep.REFUSED -> SentPhase.REFUSED
            sending?.step == SendStep.QUOTA -> SentPhase.QUOTA
            else -> SentPhase.SENDING
        }
    // The daily limit is reached: the emergency services are the choice already made (never left without a way out).
    // Keyed by the PHASE and by "the list exists" only: [AlertViewModel.selectEmergency] changes nothing when an emergency service is already chosen, so this runs once, never in a loop
    // (review of #275 v2, 1).
    val listReady = call is CallState.Ready
    LaunchedEffect(phase, listReady) { if (phase == SentPhase.QUOTA && listReady) viewModel.selectEmergency() }
    // After the daily limit nothing is left to retry: leaving the screen leaves nothing behind either (no "déjà en cours" that is not true), review of #275, m3.
    val leave = {
        if (phase == SentPhase.QUOTA) viewModel.onCancelPending()
        onBack()
    }
    BackHandler(enabled = phase == SentPhase.QUOTA) { leave() }
    AlertSentScreen(
        phase = phase,
        // A blank name is no name: the title says "ta Miff" / no name, never an empty line.
        miffName = miffName?.takeIf { it.isNotBlank() },
        several = several,
        call = call,
        modifier = modifier,
        actions =
            AlertSentActions(
                onBack = leave,
                onSelect = viewModel::select,
                onCall = { target ->
                    if (!dial(
                            context,
                            viewModel,
                            target,
                        )
                    ) {
                        Toast.makeText(context, unavailable, Toast.LENGTH_SHORT).show()
                    }
                },
                onShowEmergency = viewModel::selectEmergency,
                onRetry = viewModel::onRetry,
                onCancelPending = viewModel::onCancelPending,
                onConfirmStale = viewModel::onConfirmStale,
                onResumeSharing = { onResumeSharing(route.miffId) },
            ),
    )
}

/** The dialler of the system with the number prepared (ACTION_DIAL, never ACTION_CALL: Miff places no call). True when an application took it. */
private fun dial(
    context: android.content.Context,
    viewModel: AlertViewModel,
    target: AlertDialTarget,
): Boolean {
    if (!viewModel.dialable(target)) return false
    return when (target) {
        is AlertDialTarget.Person -> FeedIntents.call(context, target.contact.phone)
        is AlertDialTarget.Emergency -> FeedIntents.callEmergency(context, target.entry.number)
    }
}

/**
 * What keeps the alert true while the tabs are on screen: the pointer of the phone is picked up at the opening, the alert is checked at each return to the front (1 read, only
 * while an alert is in progress), the clock ends an alert past its 7 days (no write, no notification). A list of Miffs NEVER ends an alert (a Miff missing from a list read while the
 * network jumps is not a lost Miff): the alert goes when the leaving, the removal or the deletion is confirmed (the repository tells the controller), review of #275 v2, 2.
 */
@Composable
fun AlertLifecycle(
    viewModel: AlertViewModel,
    miffIds: List<String>,
) {
    LaunchedEffect(viewModel) { viewModel.restore() }
    LaunchedEffect(miffIds) { if (miffIds.isNotEmpty()) viewModel.onMiffsKnown(miffIds) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Each return to the front (and the first opening): an alert past its date goes, an active one is checked against the server once.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        scope.launch { viewModel.onForeground() }
    }
    LaunchedEffect(viewModel) {
        while (true) {
            kotlinx.coroutines.delay(MINUTE_MILLIS)
            viewModel.tick()
        }
    }
}

private const val MINUTE_MILLIS = 60_000L
