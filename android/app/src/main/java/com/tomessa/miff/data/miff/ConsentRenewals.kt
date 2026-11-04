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

    class Due(
        val miffs: List<RenewalMiff>,
    ) : ConsentRenewalCheck {
        override fun toString(): String = "Due(redacted)"
    }
}

/**
 * The renewal of the consent (LOC-2a, rule R-LIEU-3): members who share under an OLDER text must accept the current one before any
 * position can be written (LOC-2b). The check reads, from the server, the index of the person (1 read) and the card of each Miff
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
) : ConsentRenewals {
    override suspend fun check(uid: String): ConsentRenewalCheck {
        val refs =
            when (val read = guardedCall(timeoutMillis) { store.readMemberships(uid, fromServer = true) }) {
                is Guarded.Value -> read.value
                is Guarded.Failed -> return ConsentRenewalCheck.Unknown
            }
        val due = mutableListOf<RenewalMiff>()
        var incomplete = false
        for (ref in refs.distinctBy { it.miffId }) {
            when (val read = guardedCall(timeoutMillis) { store.readSharingConsent(ref.miffId, uid) }) {
                is Guarded.Value -> {
                    val consent = read.value
                    if (consent != null && consent.enabled && MiffSharingConsent.isRenewalDue(consent.version)) {
                        due += describe(ref.miffId, uid)
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
            due.isNotEmpty() -> ConsentRenewalCheck.Due(due)
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
        // A lot whose answer was lost may have left, and a refusal can mean "already renewed" (another phone did it: the rules want a
        // strictly newer version): the server is asked before the person is told it failed.
        if (result is MiffWriteResult.Failed &&
            check(uid) == ConsentRenewalCheck.NothingToRenew
        ) {
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
