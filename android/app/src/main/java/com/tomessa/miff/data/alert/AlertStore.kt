package com.tomessa.miff.data.alert

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
import com.tomessa.miff.domain.alert.AlertDoc
import com.tomessa.miff.domain.alert.AlertFailure
import com.tomessa.miff.domain.alert.AlertRules
import com.tomessa.miff.domain.alert.AlertWrites
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.tasks.await

/*
 * SECURITY, PRIVACY and COST, for this file (ticket docs/tickets/alerte-danger.md, backend/data-model.md 2.18, backend/firestore.rules `match /alerts/{uid}`, NOT DEPLOYED):
 * - The start and the end of an alert are TRANSACTIONS, never batches: a batch made without network is queued by Firestore and leaves LATER, by itself, which would be a
 *   "ghost alert" sent hours after the fact. A transaction only exists online: "no confirmation" always means "nothing was written". The waiting of an alert that could not
 *   leave is held by the APP ([AlertController]), where the person can see it and cancel it.
 * - ONE read of MY card (is my sharing on in that Miff?) and ONE read of my alert document inside the start (create or re-arm); the rules then read the session and the card
 *   (3 reads of rules). Nothing is written in any other Miff: an alert lives in ONE Miff (decision of the PM, D9).
 * - The document holds NO position, NO text, NO photo. No identifier, no name and no date of an alert is ever logged; no exception message leaves this file (it can quote a path).
 */

/** What starting an alert gave. */
sealed interface StartResult {
    /** Written; [startedAtMillis] is the clock of the phone (the server's date is read back by the listeners). */
    class Started(
        val startedAtMillis: Long,
        val expiresAtMillis: Long,
    ) : StartResult

    /** An alert of mine is already in progress in that Miff (another phone, or an answer that was lost): it is adopted, nothing is written. */
    class Adopted(
        val doc: AlertDoc,
    ) : StartResult

    /** The previous alert of mine in that Miff started less than 60 s ago: wait [waitMillis] (the rules refuse a re-arming sooner), then try again. */
    class TooSoon(
        val waitMillis: Long,
    ) : StartResult

    class Failed(
        val failure: AlertFailure,
    ) : StartResult
}

sealed interface EndResult {
    /** The alert is ended on the server (by this write, or it already was, or the document is gone). */
    data object Ended : EndResult

    class Failed(
        val failure: AlertFailure,
    ) : EndResult
}

/** What reading my own alert of one Miff from the SERVER gave. */
sealed interface MineRead {
    /** [doc] is null when there is no alert document for me there. */
    class Value(
        val doc: AlertDoc?,
    ) : MineRead

    data object Unavailable : MineRead
}

/** The alerts of one Miff as the Feed listener hears them. */
class AlertsHeard(
    val docs: List<AlertDoc>,
    /** True while what is held comes from the cache of the phone and the server has not confirmed it yet. */
    val fromCache: Boolean,
) {
    override fun toString(): String = "AlertsHeard(redacted)"
}

/** What the Feed needs of the alerts: the alerts of ONE Miff, live, while it is on screen. */
fun interface AlertFeed {
    /** The alerts of one Miff, live. Ends with the collector; an error (rules not up to date, access lost) ends the flow silently: "no alert", never an error on the Feed. */
    fun observe(miffId: String): Flow<AlertsHeard>

    companion object {
        /** The function is off (BuildConfig.ALERT_DANGER false), or a test: nothing is read. */
        val None = AlertFeed { emptyFlow() }
    }
}

interface AlertStore : AlertFeed {
    /** Starts (creates, or re-arms after a fin or an échéance) MY alert in [miffId]. Never throws. */
    suspend fun start(
        uid: String,
        miffId: String,
        deviceId: String,
        nowMillis: Long,
    ): StartResult

    /** Ends MY alert in [miffId]. Never throws. */
    suspend fun end(
        uid: String,
        miffId: String,
        deviceId: String,
        nowMillis: Long,
    ): EndResult

    /** ONE reading from the server of my alert of [miffId] (the new phone that adopts an alert, the return to the front). Never throws. */
    suspend fun readMine(
        uid: String,
        miffId: String,
    ): MineRead
}

class FirestoreAlertStore(
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
) : AlertStore {
    override suspend fun start(
        uid: String,
        miffId: String,
        deviceId: String,
        nowMillis: Long,
    ): StartResult =
        try {
            val db = firestore()
            val miffRef = db.collection("miffs").document(miffId)
            val card = miffRef.collection("members").document(uid)
            val alert = miffRef.collection("alerts").document(uid)
            db
                .runTransaction { tx ->
                    // All the reads come before the first write (the rule of Firestore transactions).
                    val mine = tx.get(card)
                    val existing = tx.get(alert)
                    when {
                        !mine.exists() -> {
                            StartResult.Failed(AlertFailure.NotMember)
                        }

                        mine.getBoolean("sharing.enabled") != true -> {
                            StartResult.Failed(AlertFailure.SharingStopped)
                        }

                        !existing.exists() -> {
                            tx.set(alert, AlertWrites.start(deviceId, nowMillis, seq = 1L))
                            StartResult.Started(nowMillis, AlertRules.expiresAtMillis(nowMillis))
                        }

                        else -> {
                            val doc = parse(existing)
                            val startedAt = doc?.startedAtMillis
                            when {
                                doc != null && doc.isLive(nowMillis) -> {
                                    StartResult.Adopted(doc)
                                }

                                startedAt != null && nowMillis - startedAt < AlertRules.REARM_MIN_GAP_MILLIS -> {
                                    StartResult.TooSoon(
                                        AlertRules.REARM_MIN_GAP_MILLIS - (nowMillis - startedAt) +
                                            AlertRules.REARM_SAFETY_MILLIS,
                                    )
                                }

                                else -> {
                                    tx.set(alert, AlertWrites.start(deviceId, nowMillis, seq = (doc?.seq ?: 0L) + 1L))
                                    StartResult.Started(nowMillis, AlertRules.expiresAtMillis(nowMillis))
                                }
                            }
                        }
                    }
                }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StartResult.Failed(failureOf(e))
        }

    override suspend fun end(
        uid: String,
        miffId: String,
        deviceId: String,
        nowMillis: Long,
    ): EndResult =
        try {
            val db = firestore()
            val alert =
                db
                    .collection("miffs")
                    .document(miffId)
                    .collection("alerts")
                    .document(uid)
            db
                .runTransaction { tx ->
                    val existing = tx.get(alert)
                    val doc = if (existing.exists()) parse(existing) else null
                    // Gone, or already ended: nothing to write, and the person's wish ("it is over") is a fact.
                    if (doc != null && doc.active) tx.update(alert, AlertWrites.end(deviceId, nowMillis))
                }.await()
            EndResult.Ended
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            EndResult.Failed(failureOf(e))
        }

    override suspend fun readMine(
        uid: String,
        miffId: String,
    ): MineRead =
        try {
            val snapshot =
                firestore()
                    .collection("miffs")
                    .document(miffId)
                    .collection("alerts")
                    .document(uid)
                    .get(Source.SERVER)
                    .await()
            MineRead.Value(if (snapshot.exists()) parse(snapshot) else null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MineRead.Unavailable
        }

    override fun observe(miffId: String): Flow<AlertsHeard> =
        callbackFlow {
            var registration: ListenerRegistration? = null
            try {
                registration =
                    firestore()
                        .collection("miffs")
                        .document(miffId)
                        .collection("alerts")
                        .addSnapshotListener { snapshot, error ->
                            // A refusal (the rules are not deployed yet, the person left the Miff...) is "no alert", never an error on the Feed.
                            if (error != null || snapshot == null) {
                                close()
                            } else {
                                val docs = snapshot.documents.mapNotNull { parse(it) }
                                trySend(AlertsHeard(docs, snapshot.metadata.isFromCache))
                            }
                        }
            } catch (e: Exception) {
                close()
            }
            awaitClose { registration?.remove() }
        }

    private fun failureOf(error: Exception): AlertFailure {
        val code = (error as? FirebaseFirestoreException)?.code
        return when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> AlertFailure.Refused
            else -> AlertFailure.Offline
        }
    }

    companion object {
        /** The document, read like everything from the network: every field checked, a document that is not one of ours is ignored. */
        internal fun parse(snapshot: DocumentSnapshot): AlertDoc? =
            AlertDoc.parse(
                uid = snapshot.id,
                status = snapshot.get("status"),
                startedAtMillis = millis(snapshot, "startedAt"),
                expiresAtMillis = millis(snapshot, "expiresAt"),
                seq = snapshot.get("seq"),
            )

        private fun millis(
            snapshot: DocumentSnapshot,
            field: String,
        ): Long? =
            try {
                // A date the server has not filled in yet (a write of this phone still pending) is estimated by the SDK instead of being null.
                (snapshot.get(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) as? Timestamp)?.toDate()?.time
            } catch (e: RuntimeException) {
                null
            }
    }
}
