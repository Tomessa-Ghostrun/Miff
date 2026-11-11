package com.tomessa.miff.ui.components

import com.tomessa.miff.data.auth.NetworkMonitor
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/*
 * COST, for this file (bug of 2026-11-10, "le Feed se charge à l'infini sans connexion"):
 * - the pop-in only exists while a screen shows NOTHING (no content, no copy of the cache): it is never drawn over information;
 * - the automatic attempts re-read ONLY when a network is there. With no network the timer turns and reads nothing (0 read, 0 cost);
 * - when a network is there but nothing arrives (a slow or captive network), at most [maxAttemptsWhileOnline] automatic attempts are made, then
 *   only the button "Réessayer" and the return of the network start a new reading: never a loop of readings;
 * - the return of the network starts ONE attempt at once (not one per second), then the pop-in leaves by itself.
 * Known limit and signal: an attempt re-attaches the listeners of the screen (a few dozen reads of Firestore at most); if the quota of reads of the
 * Spark plan gets close (docs/dev/surveillance-quotas.md), lower [maxAttemptsWhileOnline] first.
 */

/**
 * What the pop-in "Pas de connexion" shows. [active]: the screen has nothing to show and no network (or no answer for a while).
 * [dismissed]: the person closed the pop-in (back): the screen then tells it in its page, the attempts go on.
 * [secondsLeft]: the discreet countdown before the next automatic attempt (null: none is planned).
 */
data class ConnectionPopInState(
    val active: Boolean = false,
    val dismissed: Boolean = false,
    val secondsLeft: Int? = null,
) {
    val popInVisible: Boolean get() = active && !dismissed

    /** The screen says it in its page (the pop-in was closed). */
    val inlineVisible: Boolean get() = active && dismissed
}

/**
 * Decides when a screen that waits for its content says "Pas de connexion" and tries again by itself. One instance per screen (view model); [run] lives
 * as long as the screen is looked at (it is cancelled when the screen is left, and then nothing is left running or shown).
 *
 * Rules:
 * - no network: the pop-in appears after [offlineAfterMillis] (not at once: the copy of the cache usually answers within a moment);
 * - a network but no content after [slowAfterMillis]: the pop-in appears as well;
 * - every [retryEveryMillis] the screen tries again by itself, but only if a network is there ([maxAttemptsWhileOnline] times at most in that case);
 * - the network comes back: ONE attempt at once, the pop-in leaves, and comes back only if the content still does not arrive after [slowAfterMillis];
 * - content arrives: everything stops and the pop-in leaves by itself.
 */
class ConnectionGate(
    private val network: NetworkMonitor,
    private val offlineAfterMillis: Long = OFFLINE_AFTER_MILLIS,
    private val slowAfterMillis: Long = SLOW_AFTER_MILLIS,
    private val retryEveryMillis: Long = RETRY_EVERY_MILLIS,
    private val maxAttemptsWhileOnline: Int = MAX_ATTEMPTS_WHILE_ONLINE,
) {
    private val mutableState = MutableStateFlow(ConnectionPopInState())
    val state: StateFlow<ConnectionPopInState> = mutableState.asStateFlow()

    /** Goes up at "Réessayer": the countdown starts again from the top. */
    private val restarts = MutableStateFlow(0)

    /** The person tapped "Réessayer": the countdown starts again (the caller starts the reading itself). */
    fun restartCountdown() {
        restarts.value = restarts.value + 1
    }

    /** The pop-in was closed (back): the page of the screen takes over, the attempts go on. */
    fun dismiss() {
        mutableState.update { if (it.active) it.copy(dismissed = true, secondsLeft = null) else it }
    }

    /**
     * Watches until cancelled. [loading]: the screen shows nothing yet, waiting for its content. [unavailable]: the screen could not load; it counts
     * as "stuck" only without network (with one, the screen has its own calm sentence and "Réessayer"). [retry] starts the reading again; the gate calls
     * it only when a network is there.
     */
    suspend fun run(
        loading: Flow<Boolean>,
        unavailable: Flow<Boolean> = flowOf(false),
        retry: () -> Unit,
    ) {
        try {
            coroutineScope {
                val online = MutableStateFlow(network.isOnline())
                launch { network.onlineChanges().collect { online.value = it } }
                var wasOffline = !online.value
                combine(loading, unavailable, online) { l, u, o -> Triple(l, u, o) }
                    .distinctUntilChanged()
                    .collectLatest { (isLoading, isUnavailable, isOnline) ->
                        val stuck = isLoading || (isUnavailable && !isOnline)
                        // Computed BEFORE the "not stuck" exit: with the network back and the screen "unavailable" (first installation, data cleared, then
                        // airplane mode), `stuck` is already false, yet ONE reading is still owed (the review of PR #149: the pop-in left without retry()).
                        val networkCameBack = wasOffline && isOnline
                        wasOffline = !isOnline
                        if (networkCameBack && (isLoading || isUnavailable)) {
                            // The network is back: ONE reading at once, and the pop-in leaves (it comes back only if nothing arrives).
                            clear()
                            retry()
                        }
                        if (!stuck) {
                            clear()
                            return@collectLatest
                        }
                        var firstCycle = true
                        restarts.collectLatest {
                            // "Réessayer" starts the countdown again at once: the first wait before the pop-in is for the first cycle only.
                            val waitFirst = firstCycle
                            firstCycle = false
                            cycle(isOnline, waitFirst, retry)
                        }
                    }
            }
        } finally {
            clear()
        }
    }

    private suspend fun cycle(
        online: Boolean,
        waitFirst: Boolean,
        retry: () -> Unit,
    ) {
        if (waitFirst) delay(if (online) slowAfterMillis else offlineAfterMillis)
        var attempts = 0
        while (true) {
            if (online && attempts >= maxAttemptsWhileOnline) {
                // Enough automatic readings for a network that answers nothing: only "Réessayer" or the return of the network go on.
                mutableState.update { it.copy(active = true, secondsLeft = null) }
                awaitCancellation()
            }
            var left = secondsOf(retryEveryMillis)
            while (left > 0) {
                mutableState.update { it.copy(active = true, secondsLeft = if (it.dismissed) null else left) }
                delay(ONE_SECOND_MILLIS)
                left--
            }
            // With no network the timer turns and reads nothing: the attempt is made the moment the network is there.
            if (network.isOnline()) {
                attempts++
                retry()
            }
        }
    }

    private fun clear() {
        mutableState.value = ConnectionPopInState()
    }

    private fun secondsOf(millis: Long): Int =
        ((millis + ONE_SECOND_MILLIS - 1) / ONE_SECOND_MILLIS).toInt().coerceAtLeast(1)

    companion object {
        /** Offline, the pop-in waits this long: the copy of the cache answers within a moment and then nothing is drawn. */
        const val OFFLINE_AFTER_MILLIS = 1_500L

        /** With a network but no content, the pop-in appears after this time (the first answer of the statuses is waited for 15 s elsewhere). */
        const val SLOW_AFTER_MILLIS = 8_000L

        /** The automatic attempt (PM, 2026-11-10: every 10 seconds). */
        const val RETRY_EVERY_MILLIS = 10_000L

        const val MAX_ATTEMPTS_WHILE_ONLINE = 3
        private const val ONE_SECOND_MILLIS = 1_000L
    }
}

/**
 * What a screen gives [com.tomessa.miff.ui.main.MainTabs] about its pop-in "Pas de connexion", in one piece: the state, the two buttons, and [observe], the
 * suspending watch that lives while the screen is looked at (null when the screen runs it itself, as the Feed does inside its own [observe]).
 */
data class ConnectionHost(
    val state: ConnectionPopInState = ConnectionPopInState(),
    val onRetry: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val observe: (suspend () -> Unit)? = null,
)
