package com.tomessa.miff.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tomessa.miff.data.auth.NetworkMonitor
import com.tomessa.miff.data.feed.PositionReader
import com.tomessa.miff.data.feed.StatusFeed
import com.tomessa.miff.data.feed.StatusObserver
import com.tomessa.miff.data.miff.DirectoryState
import com.tomessa.miff.data.miff.MiffDirectory
import com.tomessa.miff.data.miff.MiffRepository
import com.tomessa.miff.domain.feed.FeedBuilder
import com.tomessa.miff.domain.feed.FeedContent
import com.tomessa.miff.domain.feed.FeedInput
import com.tomessa.miff.domain.feed.Locate
import com.tomessa.miff.domain.feed.LocateResult
import com.tomessa.miff.domain.feed.PositionLookup
import com.tomessa.miff.domain.feed.StatusesView
import com.tomessa.miff.domain.miff.MiffObservation
import com.tomessa.miff.domain.miff.MiffSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

/*
 * SECURITY, PRIVACY and COST, for this file:
 * - the names, the places, the statuses and above all the POSITIONS are in memory only while the screen needs them (the sheet "Localiser" is
 *   emptied as soon as the Feed leaves the screen), never in a log, an analytics event (the Feed sends none) or a crash report; every class that
 *   carries one hides it in its toString();
 * - KNOWN LIMIT, accepted and already written on the backend side (backend/data-model.md, point 27): the Firestore SDK keeps in its local cache
 *   the documents it reads, a position included, even for a read with Source.SERVER (which only means "never READ from the cache"). That copy
 *   stays on the phone until the purge of the cache at sign-out or at the loss of access to a Miff (CachePurger). The Feed shows, keeps and logs
 *   nothing of it;
 * - the Feed never writes (0 write, 0 deletion): it listens to the Miff (members, places, as the Miff screen does) and to the statuses, and
 *   reads one position per tap on "Localiser";
 * - the listeners exist ONLY while the Feed is on screen (the screen calls [observe] inside repeatOnLifecycle(STARTED), and only while the tab
 *   is the one in use): nothing listens to a Miff nobody is looking at, and only for the Miff shown;
 * - the colour is computed at the time of the reading, again each minute, from the clock: no reading of the server for it.
 */

/** What the Feed shows for the active Miff. */
sealed interface FeedUiState {
    /** Not known yet: the skeleton (no colour, no phrase of safety). */
    data object Loading : FeedUiState

    /** The Miff could not be read and nothing is known: a calm sentence and "Réessayer". */
    data object Unavailable : FeedUiState

    data class Ready(
        val content: FeedContent,
    ) : FeedUiState
}

/** The sheet "Dernière position connue de {Prénom}" (criteria L1 to L4). Nothing of it is saved: it closes when the Feed leaves the screen. */
sealed interface LocateUiState {
    data object Idle : LocateUiState

    /** One reading is under way for this member: the button shows an indicator. */
    data class Loading(
        val uid: String,
    ) : LocateUiState

    class Shown(
        val uid: String,
        /** The first name for the title of the sheet. */
        val firstName: String,
        val result: LocateResult,
    ) : LocateUiState {
        override fun toString(): String = "Shown(redacted)"
    }
}

/** What the listener of the Miff last said, for the Miff it was attached to. */
private sealed interface MiffPiece {
    val miffId: String

    class Loading(
        override val miffId: String,
    ) : MiffPiece

    class Data(
        override val miffId: String,
        val snapshot: MiffSnapshot,
    ) : MiffPiece {
        override fun toString(): String = "Data(redacted)"
    }

    class Failed(
        override val miffId: String,
    ) : MiffPiece

    class Lost(
        override val miffId: String,
    ) : MiffPiece
}

class FeedViewModel(
    private val directory: MiffDirectory,
    private val repository: MiffRepository,
    private val statuses: StatusObserver,
    private val positions: PositionReader,
    private val network: NetworkMonitor,
    private val currentUid: () -> String?,
    /** The Miffs that wait for the agreement of the person (LOC-2b, design 23): nothing is shared for them, the statuses are not read. */
    private val waitingAgreement: StateFlow<Set<String>>,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    /** Pauses before a new attempt when the rules refuse the list of statuses (mine is not published yet): few, spaced, then it waits for the screen to come back. */
    private val statusRetryDelaysMillis: List<Long> = DEFAULT_RETRY_DELAYS_MILLIS,
    /**
     * After this time without any answer of the statuses the cards stop waiting (neutral, never a colour nobody knows). Long on purpose: the
     * skeleton says "Chargement des nouvelles…" first, and only then the sentence "Pas encore de nouvelles", which a person who worries can
     * take for a bad sign.
     */
    private val firstStatusAnswerMillis: Long = FIRST_STATUS_ANSWER_MILLIS,
    /**
     * Online, what the cache says is not shown as the present: the Feed waits this long for the server to confirm it (a copy of the last time may
     * say "orange" for somebody who has just come home: a false alert). After it, the copy is shown WITH its date ("Dernières informations de…").
     */
    private val cacheGraceMillis: Long = CACHE_GRACE_MILLIS,
) : ViewModel() {
    val directoryState: StateFlow<DirectoryState> = directory.state

    private val piece = MutableStateFlow<MiffPiece?>(null)
    private val statusView = MutableStateFlow<StatusesView>(StatusesView.Waiting)
    private val offline = MutableStateFlow(false)
    private val now = MutableStateFlow(nowMillis())
    private val restarts = MutableStateFlow(0)

    /** The wait for the server's confirmation of a cached answer is over (see [cacheGraceMillis]). */
    private val cacheGraceOver = MutableStateFlow(false)

    val uiState: StateFlow<FeedUiState> =
        combine(directory.state, piece, statusView, offline, now, waitingAgreement, cacheGraceOver) { values ->
            @Suppress("UNCHECKED_CAST")
            compute(
                values[0] as DirectoryState,
                values[1] as MiffPiece?,
                values[2] as StatusesView,
                values[3] as Boolean,
                values[4] as Long,
                values[5] as Set<String>,
                values[6] as Boolean,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, FeedUiState.Loading)

    private val mutableLocate = MutableStateFlow<LocateUiState>(LocateUiState.Idle)
    val locate: StateFlow<LocateUiState> = mutableLocate.asStateFlow()
    private var locateJob: Job? = null

    private var lostReported = false

    init {
        refresh()
    }

    /** At the opening of the tab, and "Réessayer". */
    fun refresh() {
        viewModelScope.launch { directory.refresh() }
    }

    /** "Réessayer" on the Feed itself: the Miffs are read again and the listeners attached again. */
    fun retry() {
        lostReported = false
        restarts.value = restarts.value + 1
        refresh()
    }

    /** Premium: another Miff was chosen in the selector. */
    fun onSelect(miffId: String) = directory.select(miffId)

    private fun compute(
        dir: DirectoryState,
        piece: MiffPiece?,
        statuses: StatusesView,
        offline: Boolean,
        now: Long,
        waiting: Set<String>,
        cacheGraceOver: Boolean,
    ): FeedUiState {
        val ready = dir as? DirectoryState.Ready ?: return FeedUiState.Loading
        val activeId = ready.activeId ?: return FeedUiState.Loading
        if (piece == null || piece.miffId != activeId) return FeedUiState.Loading
        return when (piece) {
            is MiffPiece.Data -> {
                // The skeleton stays until the statuses answered: a colour nobody knows is never drawn.
                if (statuses == StatusesView.Waiting) return FeedUiState.Loading
                // Online, a copy that the server has not confirmed yet is not the present: no alert (and no green) is drawn from it for a moment.
                val unconfirmed = (statuses as? StatusesView.Loaded)?.fromCache == true
                if (unconfirmed && !offline && !cacheGraceOver) return FeedUiState.Loading
                val snapshot = piece.snapshot
                FeedUiState.Ready(
                    FeedBuilder.build(
                        FeedInput(
                            miffId = snapshot.miff.id,
                            miffName = snapshot.miff.name,
                            joinCode = snapshot.miff.joinCode,
                            members = snapshot.members,
                            places = snapshot.places,
                            myUid = currentUid().orEmpty(),
                            statuses = statuses,
                            waitingAgreement = activeId in waiting,
                            offline = offline,
                        ),
                        nowMillis = now,
                        zone = zone(),
                    ),
                )
            }

            is MiffPiece.Failed -> {
                FeedUiState.Unavailable
            }

            is MiffPiece.Loading, is MiffPiece.Lost -> {
                FeedUiState.Loading
            }
        }
    }

    /**
     * Reads what the Feed shows while the caller stays (the screen calls it from `repeatOnLifecycle(STARTED)` and only while the Feed is the tab
     * in use): the Miff shown (members, places), its statuses, the network and the minute. Everything is cancelled when the app goes to the
     * background or the tab changes; the sheet "Localiser" is closed then (the screen keeps no position).
     */
    suspend fun observe() {
        try {
            coroutineScope {
                launch { network.onlineChanges().collect { online -> offline.value = !online } }
                launch { tickEachMinute() }
                launch {
                    combine(
                        directory.state.map { (it as? DirectoryState.Ready)?.activeId }.distinctUntilChanged(),
                        restarts,
                    ) { id, _ -> id }.collectLatest { id ->
                        if (id == null) {
                            piece.value = null
                        } else {
                            observeMiff(id)
                        }
                    }
                }
            }
        } finally {
            locateJob?.cancel()
            mutableLocate.value = LocateUiState.Idle
        }
    }

    /** The colour is recomputed with the clock, once per minute, aligned on the minute: no reading of the server. */
    private suspend fun tickEachMinute() {
        while (true) {
            val current = nowMillis()
            now.value = current
            delay(MINUTE_MILLIS - current % MINUTE_MILLIS)
        }
    }

    private suspend fun observeMiff(miffId: String) {
        piece.value = MiffPiece.Loading(miffId)
        // What was said about another Miff is never drawn on this one.
        statusView.value = StatusesView.Waiting
        coroutineScope {
            launch {
                repository.observe(miffId).collect { observation -> handle(miffId, observation) }
            }
            launch { watchStatuses(miffId) }
        }
    }

    private suspend fun handle(
        miffId: String,
        observation: MiffObservation,
    ) {
        when (observation) {
            MiffObservation.Loading -> {}

            is MiffObservation.Data -> {
                lostReported = false
                piece.value = MiffPiece.Data(miffId, observation.snapshot)
            }

            MiffObservation.AccessLost -> {
                if (lostReported) return
                lostReported = true
                val name = (piece.value as? MiffPiece.Data)?.snapshot?.miff?.name
                piece.value = MiffPiece.Lost(miffId)
                // The single message and the clean-up are the directory's (ticket Miff 5.7); only the server's word counts. The directory
                // then drops this Miff from the list, which ends THIS listener: the clean-up must not be cut by it.
                val confirmed = withContext(NonCancellable) { directory.onAccessLost(miffId, name) }
                if (!confirmed) {
                    lostReported = false
                    piece.value = MiffPiece.Failed(miffId)
                }
            }

            MiffObservation.Failed -> {
                if (piece.value !is MiffPiece.Data) piece.value = MiffPiece.Failed(miffId)
            }
        }
    }

    /**
     * The statuses are read only when I share and my agreement is not waited for (else the rules refuse and nothing is wanted); the listener is
     * attached only after the Miff is known (so that my own sharing is known), and attached again when it ended on a refusal (my own status
     * is not published yet): a few spaced attempts, then it waits for the screen to come back, never a loop.
     */
    private suspend fun watchStatuses(miffId: String) {
        combine(
            piece.mapNotNull { (it as? MiffPiece.Data)?.takeIf { data -> data.miffId == miffId } }.map { data ->
                data.snapshot.members
                    .firstOrNull { it.uid == currentUid() }
                    ?.sharingEnabled ?: true
            },
            waitingAgreement.map { miffId in it },
        ) { sharing, waiting -> sharing && !waiting }
            .distinctUntilChanged()
            .collectLatest { shouldRead ->
                if (!shouldRead) {
                    statusView.value = StatusesView.Off
                } else {
                    statusView.value = StatusesView.Waiting
                    readStatuses(miffId)
                }
            }
    }

    private suspend fun readStatuses(miffId: String) {
        coroutineScope {
            cacheGraceOver.value = false
            val waitingTimeout =
                launch {
                    delay(firstStatusAnswerMillis)
                    if (statusView.value == StatusesView.Waiting) statusView.value = StatusesView.Unknown
                }
            var cacheGrace: Job? = null
            var attempt = 0
            while (true) {
                statuses.observe(miffId).collect { feed ->
                    when (feed) {
                        is StatusFeed.Data -> {
                            waitingTimeout.cancel()
                            if (feed.fromCache) {
                                // A copy of the cache is NOT an answer of the server: it does not give the attempts back (a listener that
                                // keeps ending right after its cached answer must not be attached again every 10 s, for ever).
                                if (cacheGrace == null) {
                                    cacheGrace =
                                        launch {
                                            delay(cacheGraceMillis)
                                            cacheGraceOver.value = true
                                        }
                                }
                            } else {
                                attempt = 0
                                cacheGrace?.cancel()
                                cacheGrace = null
                            }
                            statusView.value = StatusesView.Loaded(feed.byUid, feed.fromCache)
                        }

                        StatusFeed.Ended -> {}
                    }
                }
                // The listener ended (a refusal: the rules wait for my own status, or my sharing stopped elsewhere).
                waitingTimeout.cancel()
                statusView.value = StatusesView.Pending
                val pause = statusRetryDelaysMillis.getOrNull(attempt) ?: break
                attempt++
                delay(pause)
            }
        }
    }

    /** A tap on "Localiser {Prénom}": ONE reading from the server, then the sheet (criterion L1). A second tap while it reads does nothing. */
    fun onLocate(uid: String) {
        if (mutableLocate.value is LocateUiState.Loading) return
        val content = (uiState.value as? FeedUiState.Ready)?.content ?: return
        val card = content.cards.firstOrNull { it.member.uid == uid && it.canLocate } ?: return
        val firstName = card.member.callName
        mutableLocate.value = LocateUiState.Loading(uid)
        locateJob =
            viewModelScope.launch {
                val lookup =
                    if (network.isOnline()) {
                        try {
                            positions.read(content.miffId, uid)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            PositionLookup.Offline
                        }
                    } else {
                        PositionLookup.Offline
                    }
                mutableLocate.value = LocateUiState.Shown(uid, firstName, Locate.interpret(lookup, nowMillis(), zone()))
            }
    }

    /** The sheet was closed (button, back, swipe, outside): the screen keeps nothing of the position. */
    fun onLocateDismissed() {
        locateJob?.cancel()
        mutableLocate.value = LocateUiState.Idle
    }

    private companion object {
        const val MINUTE_MILLIS = 60_000L
        val DEFAULT_RETRY_DELAYS_MILLIS = listOf(10_000L, 30_000L, 90_000L, 300_000L)
        const val FIRST_STATUS_ANSWER_MILLIS = 15_000L
        const val CACHE_GRACE_MILLIS = 5_000L
    }
}
