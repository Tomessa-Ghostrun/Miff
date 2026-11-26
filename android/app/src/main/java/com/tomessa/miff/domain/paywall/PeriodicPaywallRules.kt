package com.tomessa.miff.domain.paywall

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/*
 * The periodic offer (ticket docs/tickets/paywall-periodique.md, PAY-PER-A; decisions of the PM of 2026-11-25): a person WITHOUT Miff+ sees the existing offer screen once every
 * 10 openings of the app, after her first tap of the session, once per session. PURE LOGIC: no Android class here, so that every rule is tested on the JVM.
 *
 * PRIVACY: the only thing kept on the phone is ONE whole number (the openings counted since the offer was last shown, see [PeriodicPaywallCounterStore]). No date, no identifier, no name,
 * no position; it is never sent anywhere and no analytics event carries it. THERE IS NO SWITCH: the rule is on in every build (decision of the PM, P1); the only way to turn it off is
 * [PeriodicPaywallDefaults.OPENINGS_BETWEEN] = 0 (a one-line change, tested), and the only way to try it faster is the DEBUG-only reduction of the threshold (see [openingsFor]).
 */

/** THE constants of the periodic offer, in one place (the 3 seconds of the close cross are [PaywallCloseDelay.MILLIS]). Changing one is a one-line pull request. */
object PeriodicPaywallDefaults {
    /** Openings of the app between two appearances of the offer. 0 (or less) switches the whole rule off, counting included. */
    const val OPENINGS_BETWEEN = 10

    /** An "opening" is a cold start, or a return to the foreground after at least this long in the background (same threshold as the "last visit" of the notifications). */
    const val BACKGROUND_MILLIS: Long = 30L * 60_000L

    /** The offer only comes when the person has not touched the screen for this long (same value as the settling time of the review window). */
    const val QUIET_AFTER_TOUCH_MILLIS: Long = 1_500L

    /**
     * The threshold really used. [debugOverride] (the build property `miff.periodicPaywallOpenings`, DEBUG BUILD ONLY: 0 in every other build) can only LOWER it, to try the rule
     * without opening the app 10 times; a release build always passes 0 here, so it always uses [OPENINGS_BETWEEN].
     */
    fun openingsFor(debugOverride: Int): Int =
        if (OPENINGS_BETWEEN > 0 && debugOverride in 1 until OPENINGS_BETWEEN) debugOverride else OPENINGS_BETWEEN
}

/** The one whole number kept on the phone: the openings counted since the offer was last shown (private file, outside every backup). */
interface PeriodicPaywallCounterStore {
    fun read(): Int

    fun write(count: Int)

    /** Forgets it (sign-out, deletion of the account: the next account starts from zero). */
    fun clear()
}

/** What the screen can tell at the moment of the decision. All of it is state, none is personal. */
data class PeriodicPaywallSituation(
    /** The person has Miff+ (trial, grace and a manual grant included): true; no Miff+: false; not known yet (not read, or only a guess from the cache): null. */
    val miffPlus: Boolean?,
    /** The tabs are the screen in front and the app is in the foreground (nothing pushed over them, no window of the system, no other app). */
    val tabsInFront: Boolean,
    /** The viewfinder of the Update is the screen in use (photo, camera, preview). */
    val updateScreenOpen: Boolean,
    val keyboardOpen: Boolean,
    /** A message of the app is on screen or waits (lost access, agreement, "Localiser", "Pas de connexion", the Travel windows...). The windows that are dialogs, sheets or menus (the review window too) say it themselves: see [OverlayPresence]. */
    val messageOpen: Boolean,
    /** MY alert of danger is on its way or active, whatever the Miff. */
    val ownAlertInProgress: Boolean,
    /** An alert of a loved one is on the Feed, or its line waits unread in the centre of notifications. */
    val lovedOneAlertUnseen: Boolean,
    /** False only when the purchase is on in this build and the phone is offline (the offer would have no price): then nothing is shown by surprise. */
    val offerReachable: Boolean = true,
) {
    /** One of the situations where the offer never comes ([PeriodicPaywallRules.evaluate] leaves the counter alone in all of them). */
    val forbidden: Boolean
        get() =
            !tabsInFront || updateScreenOpen || keyboardOpen || messageOpen ||
                ownAlertInProgress || lovedOneAlertUnseen || !offerReachable
}

/** What the rules decide at one moment. */
sealed interface PeriodicPaywallDecision {
    /** Open the offer now. */
    data object Show : PeriodicPaywallDecision

    /** Not now, and nothing to wait for (the next change of situation, touch or session asks again). */
    data object No : PeriodicPaywallDecision

    /** Everything is right except the person touched the screen a moment ago: ask again in [millis]. */
    data class Wait(
        val millis: Long,
    ) : PeriodicPaywallDecision
}

/**
 * The state machine of the periodic offer. One per process (lives in the app container). Thread-safe (the lifecycle, the touches and the screens call from the main thread, the
 * erasers from anywhere).
 *
 * Vocabulary (ticket section 4): an OPENING is a cold start or a return after [PeriodicPaywallDefaults.BACKGROUND_MILLIS] or more; it starts a SESSION and is COUNTED the first time the
 * signed-in app (the shell, the onboarding being over) is on screen in that session. A session shows the offer at most once.
 */
class PeriodicPaywallRules(
    private val store: PeriodicPaywallCounterStore,
    private val clock: MonotonicClock,
    private val openingsBetween: Int = PeriodicPaywallDefaults.OPENINGS_BETWEEN,
    private val backgroundMillis: Long = PeriodicPaywallDefaults.BACKGROUND_MILLIS,
    private val quietMillis: Long = PeriodicPaywallDefaults.QUIET_AFTER_TOUCH_MILLIS,
) : OverlayPresence {
    private val lock = Any()

    private var started = false
    private var backgroundedAt: Long? = null
    private var openingPending = false
    private var shellVisible = false
    private var tapped = false
    private var shownThisSession = false
    private var excluded = false
    private var excludeRequestedAt: Long? = null
    private var lastTouchAt: Long? = null
    private var fingerDown = false
    private var openOverlays = 0

    private val mutableChanges = MutableStateFlow(0)

    /** Moves every time something that can change the decision happens (a touch, a session, the first tap): the host asks the rules again. */
    val changes: StateFlow<Int> = mutableChanges.asStateFlow()

    /** True when the rule is on (a threshold of 0 or less switches it off: nothing is counted, nothing is shown). */
    val isOn: Boolean get() = openingsBetween > 0

    // ---- the life of the app ------------------------------------------------------------------------------------------------------------

    /** The app came to the front WITH a window (never a wake-up without a screen). Not called for a rotation. */
    fun onForeground() {
        if (!isOn) return
        synchronized(lock) {
            val now = clock.nowMillis()
            val away = backgroundedAt?.let { now - it }
            backgroundedAt = null
            // Never in the background before = a cold start (the process was just born). A second call without a background in between changes nothing.
            if (!started || (away != null && away >= backgroundMillis)) startSession(now)
            started = true
        }
        bump()
    }

    /** The app went to the background (not a rotation). */
    fun onBackground() {
        if (!isOn) return
        synchronized(lock) {
            backgroundedAt = clock.nowMillis()
            fingerDown = false
        }
    }

    /** The signed-in app (onboarding over) is on screen: the opening of this session is counted, once. */
    fun onShellShown() {
        if (!isOn) return
        synchronized(lock) {
            shellVisible = true
            if (openingPending) countOpening()
        }
        bump()
    }

    /**
     * The signed-in app left the screen (sign-out, or the screen being built again by a rotation): nothing is counted or decided until it comes back. The first tap and the offer already shown
     * of this session are kept (a rotation must not ask for a new first tap); the sign-out erases them through [clear].
     */
    fun onShellGone() {
        if (!isOn) return
        synchronized(lock) { shellVisible = false }
    }

    /**
     * The app was opened by the tap on a notification of an ALERT of a loved one: no offer in the WHOLE session, however calm it gets later (ticket P11). Called before or after the
     * session starts (the system calls `onNewIntent` BEFORE the return to the front): a session that starts within a few seconds of the call is the one it is about.
     */
    fun excludeSession() {
        if (!isOn) return
        synchronized(lock) {
            excluded = true
            excludeRequestedAt = clock.nowMillis()
        }
        bump()
    }

    // ---- the touches ------------------------------------------------------------------------------------------------------------------

    /** Any touch on the screen (a finger down, moving, up): the offer waits for a quiet moment. [pressed] = a finger is still on the screen (a long press makes no event). */
    fun onTouch(pressed: Boolean) {
        if (!isOn) return
        synchronized(lock) {
            lastTouchAt = clock.nowMillis()
            fingerDown = pressed
        }
        bump()
    }

    /**
     * A TAP that activated something (a button, a card, a tab): the first one of the session makes the offer possible. A scroll, a swipe or a touch on nothing never calls this. Not counted
     * while the app is not the signed-in app (the sign-in, the onboarding, the consent).
     */
    fun onActivation() {
        if (!isOn) return
        synchronized(lock) {
            if (!shellVisible) return
            tapped = true
        }
        bump()
    }

    // ---- the overlays over the tabs ----------------------------------------------------------------------------------------------------

    override fun onOverlayOpened() {
        if (!isOn) return
        synchronized(lock) { openOverlays++ }
        bump()
    }

    override fun onOverlayClosed() {
        if (!isOn) return
        synchronized(lock) {
            if (openOverlays > 0) openOverlays--
            // The tap that closed it happened in another window: the calm moment is counted from now.
            lastTouchAt = clock.nowMillis()
            fingerDown = false
        }
        bump()
    }

    // ---- the offer --------------------------------------------------------------------------------------------------------------------

    /**
     * Decides at one moment. Shows only if EVERYTHING is true: the rule is on; the signed-in app is there; the person has no Miff+ (known); the session is not excluded and has not shown the
     * offer yet; she tapped something; the counter reached the threshold; the situation allows it; and she has not touched the screen for [PeriodicPaywallDefaults.QUIET_AFTER_TOUCH_MILLIS].
     * A person who HAS Miff+ resets the counter here (so that the end of a subscription starts from zero, never from "10 already reached"). In every "no", the counter is left alone.
     */
    fun evaluate(situation: PeriodicPaywallSituation): PeriodicPaywallDecision {
        if (!isOn) return PeriodicPaywallDecision.No
        synchronized(lock) {
            if (!shellVisible) return PeriodicPaywallDecision.No
            when (situation.miffPlus) {
                null -> {
                    return PeriodicPaywallDecision.No
                }

                true -> {
                    if (store.read() != 0) store.write(0)
                    return PeriodicPaywallDecision.No
                }

                false -> {
                    Unit
                }
            }
            if (excluded || shownThisSession || !tapped) return PeriodicPaywallDecision.No
            if (store.read() < openingsBetween) return PeriodicPaywallDecision.No
            if (situation.forbidden) return PeriodicPaywallDecision.No
            // A dialog, a sheet, a menu or the full-screen photo is open over the tabs (its touches never reach the root).
            if (openOverlays > 0) return PeriodicPaywallDecision.No
            val now = clock.nowMillis()
            val sinceTouch = lastTouchAt?.let { now - it }
            if (fingerDown) return PeriodicPaywallDecision.Wait(quietMillis)
            if (sinceTouch != null &&
                sinceTouch < quietMillis
            ) {
                return PeriodicPaywallDecision.Wait(quietMillis - sinceTouch)
            }
            return PeriodicPaywallDecision.Show
        }
    }

    /** The host is opening the offer: nothing else opens it in this session, even while the screen is not yet on view (no double opening). The counter is NOT reset yet. */
    fun onLaunched() {
        if (!isOn) return
        synchronized(lock) { shownThisSession = true }
    }

    /**
     * The offer is REALLY on view, whatever opened it (a 4th place, a 2nd Miff, a danger zone, the Abonnement screen, or this rule): the counter goes back to zero at that instant, even if the
     * person closes the screen a second later or the app is killed during the 3 seconds, and this session shows no other periodic offer.
     */
    fun onPaywallDisplayed() {
        if (!isOn) return
        synchronized(lock) {
            shownThisSession = true
            if (store.read() != 0) store.write(0)
        }
    }

    /** Sign-out or deletion of the account: the counter and the session go (the next person starts from zero). */
    fun clear() {
        synchronized(lock) {
            store.clear()
            tapped = false
            shownThisSession = false
        }
        bump()
    }

    // ---- DEBUG tools (the diagnostic screen of the debug build only) ----------------------------------------------------------------------

    /** The number of openings counted, for the diagnostic screen. */
    fun counterForDiagnostic(): Int = synchronized(lock) { store.read() }

    /** DEBUG BUILD ONLY: puts the counter at [count] (clamped to the threshold) to try the rule without opening the app many times. */
    fun setCounterForDiagnostic(count: Int) {
        synchronized(lock) { store.write(count.coerceIn(0, maxOf(openingsBetween, 0))) }
        bump()
    }

    /** The threshold in use, for the diagnostic screen. */
    val thresholdForDiagnostic: Int get() = openingsBetween

    /** The state of the session, in words, for the diagnostic screen (nothing personal). */
    fun sessionForDiagnostic(): String =
        synchronized(lock) {
            val where = if (shellVisible) "app ouverte" else "hors app"
            val tap = if (tapped) "fait" else "pas encore"
            val shown = if (shownThisSession) "oui" else "non"
            val blocked = if (excluded) "oui" else "non"
            "session : $where ; premier appui : $tap ; déjà montrée : $shown ; exclue : $blocked ; surcouches ouvertes : $openOverlays"
        }

    // ---- private ------------------------------------------------------------------------------------------------------------------------

    private fun startSession(now: Long) {
        openingPending = true
        tapped = false
        shownThisSession = false
        lastTouchAt = null
        fingerDown = false
        excluded = excludeRequestedAt?.let { now - it < EXCLUDE_WINDOW_MILLIS } == true
        if (shellVisible) countOpening()
    }

    private fun countOpening() {
        openingPending = false
        // Capped at the threshold: an offer that is due stays due, the number never grows without end.
        store.write((store.read() + 1).coerceAtMost(openingsBetween))
    }

    private fun bump() {
        mutableChanges.update { it + 1 }
    }

    private companion object {
        /** A tap on an alert notification calls [excludeSession] a moment before or after the session it opens starts: this is how close the two must be. */
        const val EXCLUDE_WINDOW_MILLIS = 10_000L
    }
}
