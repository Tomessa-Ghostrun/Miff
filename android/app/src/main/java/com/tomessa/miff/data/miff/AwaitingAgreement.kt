package com.tomessa.miff.data.miff

import com.tomessa.miff.data.session.SessionTeardown
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * PRIVACY, for this file: holds the IDENTIFIERS of the Miffs where the person shares under an older text of consent and has not agreed yet
 * ("En attente de ton accord", design screen 23). In memory only, for the session; no name, no position. Nothing is read, nothing is written.
 */

/**
 * Which Miffs wait for the agreement of the person (LOC-2b, design 23). Written by the look at the consents that the screens of the permissions make once
 * per launch ([com.tomessa.miff.ui.permission.PermissionGateViewModel]); read by the Feed, the screen of a Miff and Settings > Confidentialité, so that
 * they say the truth while "Pas maintenant" was chosen: nothing is shared, and nothing is detected, for those Miffs. [requestReview] brings the
 * renewal screen back ("Revoir et accepter"): the gate listens to it.
 */
class AwaitingAgreement : SessionTeardown {
    private val mutableWaiting = MutableStateFlow<Set<String>>(emptySet())
    val waiting: StateFlow<Set<String>> = mutableWaiting.asStateFlow()

    private val mutableReviews = MutableStateFlow(0)

    /** Goes up each time the person asks to see the renewal screen again. */
    val reviewRequests: StateFlow<Int> = mutableReviews.asStateFlow()

    fun replace(miffIds: Set<String>) {
        mutableWaiting.value = miffIds
    }

    fun remove(miffId: String) {
        mutableWaiting.value = mutableWaiting.value - miffId
    }

    fun requestReview() {
        mutableReviews.value = mutableReviews.value + 1
    }

    /** End of the session: nobody is waiting for anything any more. */
    override suspend fun stop() {
        mutableWaiting.value = emptySet()
    }
}
