package com.tomessa.miff.data.miff

import com.tomessa.miff.domain.miff.MiffFailure
import com.tomessa.miff.domain.miff.MiffName
import com.tomessa.miff.domain.miff.MiffSharingConsent
import com.tomessa.miff.domain.miff.MiffWrites

/*
 * SECURITY, for this file: a name of Miff is personal data. It reaches the screen that asks for the renewal and nothing else: no log, no
 * event, no message. Every failure leaves this file as a MiffFailure. NOTHING is written without an explicit tap of the person on
 * "J'accepte" (the caller is that screen), and nothing is written at all by the check.
 */

/** A Miff where the person shares under an older text of consent: what the renewal screen names. */
class RenewalMiff(
    val miffId: String,
    /** The name as the phone knows it; "" when it could not be read (the screen then says it without the name). */
    val name: String,
    /** The creator cannot leave a Miff (only delete it): the "Quitter la Miff" link of the renewal screen is for members. */
    val isOwner: Boolean = false,
) {
    override fun toString(): String = "RenewalMiff(redacted)"
}

/** What the look at the consents of the person found. */
sealed interface ConsentRenewalCheck {
    /** Every Miff where the person shares carries the current text (or the person shares nowhere). */
    data object NothingToRenew : ConsentRenewalCheck

    /** The server could not be asked (offline, slow): nothing is claimed, the Feed is shown, the next opening asks again. */
    data object Unknown : ConsentRenewalCheck

    /**
     * Something older than this build's text: [miffs] = the Miffs whose text does NOT cover the location (versions 1 and 2: the sharing is really
     * PAUSED until the person agrees, blocking screen); [clarify] = the Miffs whose text still covers it (version 3: NOTHING is paused, only the calm card
     * "Une précision sur ce que tu partages", CONS-4-A). A Miff is in one list only. [miffs] may be empty when only [clarify] is due.
     */
    class Due(
        val miffs: List<RenewalMiff>,
        val clarify: List<RenewalMiff> = emptyList(),
    ) : ConsentRenewalCheck {
        override fun toString(): String = "Due(redacted)"
    }
}

/**
 * The renewal of the consent (LOC-2a, rule R-LIEU-3): members who share under a text that does NOT cover the location (versions 1 and 2) must accept
 * the current one before any position can be written (LOC-2b). Members under version 3 (which covers it) are only offered a calm clarification
 * (CONS-4-A): nothing stops for them; "J'ai compris" is the SAME renewal write. The check reads, from the server, the index of the person (1 read) and the card of each Miff
 * (1 document each, 2 reads of rules each): at most one Miff for a free account. The renewal is ONE lot (0 reads of rules).
 */
interface ConsentRenewals {
    suspend fun check(uid: String): ConsentRenewalCheck

    /** Writes the current version in the card of every one of [miffIds], in ONE lot, after the person said yes. */
    suspend fun renew(
        uid: String,
        miffIds: List<String>,
    ): MiffWriteResult
}

class FirestoreConsentRenewals(
    private val store: MiffStore,
    private val timeoutMillis: Long = FirestoreMiffRepository.TIMEOUT_MS,
    /** Called once the agreement is recorded: the detection (LOC-2b) looks at its Miffs again (they now carry the current consent). */
    private val onRenewed: () -> Unit = {},
) : ConsentRenewals {
    override suspend fun check(uid: String): ConsentRenewalCheck {
        val refs =
            when (val read = guardedCall(timeoutMillis) { store.readMemberships(uid, fromServer = true) }) {
                is Guarded.Value -> read.value
                is Guarded.Failed -> return ConsentRenewalCheck.Unknown
            }
        val due = mutableListOf<RenewalMiff>()
        val clarify = mutableListOf<RenewalMiff>()
        var incomplete = false
        for (ref in refs.distinctBy { it.miffId }) {
            when (val read = guardedCall(timeoutMillis) { store.readSharingConsent(ref.miffId, uid) }) {
                is Guarded.Value -> {
                    val consent = read.value
                    if (consent != null && consent.enabled) {
                        // Two different things (CONS-4-A, criterion CA-10): a text that does not cover the location PAUSES the sharing (blocking
                        // renewal); a text that still covers it (version 3) pauses nothing and is only clarified.
                        when {
                            MiffSharingConsent.pausesSharing(consent.version) -> {
                                due += describe(ref.miffId, uid)
                            }

                            MiffSharingConsent.isClarificationDue(consent.version) -> {
                                clarify +=
                                    describe(ref.miffId, uid)
                            }
                        }
                    }
                }

                // A refusal of the rules means the person is no longer in that Miff (the loss of access is dealt with elsewhere):
                // nothing to renew there. Anything else (no network...) leaves the answer unknown.
                is Guarded.Failed -> {
                    if (!read.lostAccess) incomplete = true
                }
            }
        }
        return when {
            due.isNotEmpty() || clarify.isNotEmpty() -> ConsentRenewalCheck.Due(due, clarify)
            incomplete -> ConsentRenewalCheck.Unknown
            else -> ConsentRenewalCheck.NothingToRenew
        }
    }

    override suspend fun renew(
        uid: String,
        miffIds: List<String>,
    ): MiffWriteResult {
        val ids = miffIds.distinct()
        if (ids.isEmpty() ||
            ids.any { !FirestoreMiffStore.isMiffId(it) }
        ) {
            return MiffWriteResult.Failed(MiffFailure.UNKNOWN)
        }
        val result =
            when (
                val outcome =
                    guardedCall(timeoutMillis) {
                        // The card is read INSIDE the transaction: only the Miffs where the person still shares are renewed.
                        store.commitWhereSharing(
                            uid,
                            ids,
                        ) { active -> active.flatMap { MiffWrites.renewConsent(it, uid) } }
                    }
            ) {
                is Guarded.Value -> MiffWriteResult.Done
                is Guarded.Failed -> MiffWriteResult.Failed(outcome.failure)
            }
        if (result == MiffWriteResult.Done) onRenewed()
        // A lot whose answer was lost may have left, and a refusal can mean "already renewed" (another phone did it: the rules want a
        // strictly newer version): the server is asked before the person is told it failed.
        if (result is MiffWriteResult.Failed &&
            check(uid) == ConsentRenewalCheck.NothingToRenew
        ) {
            onRenewed()
            return MiffWriteResult.Done
        }
        return result
    }

    private suspend fun describe(
        miffId: String,
        uid: String,
    ): RenewalMiff {
        val read = guardedCall(timeoutMillis) { store.readMiff(miffId, fromServer = false) }
        val details = ((read as? Guarded.Value)?.value as? MiffRead.Exists)?.details
        return RenewalMiff(miffId, details?.name?.let(MiffName::clean).orEmpty(), isOwner = details?.ownerUid == uid)
    }
}
