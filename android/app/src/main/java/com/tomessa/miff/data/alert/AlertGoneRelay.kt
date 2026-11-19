package com.tomessa.miff.data.alert

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Tells the alert controller that a Miff is gone for me (left, removed, deleted, my sharing stopped), from wherever the news comes: the repository, the directory that cleans up a lost
 * Miff, the coordinator that deletes a Miff on a background thread. The controller is built for ONE thread (the main one): this relay posts every call to [scope] (the main
 * dispatcher in the app), so nothing touches the state from another thread (review of #275 v4, 2). Small and injectable on purpose, so that the wiring of the deletion of a Miff can be tested
 * with a real controller.
 */
class AlertGoneRelay(
    private val scope: CoroutineScope,
    private val remove: (miffId: String) -> Unit,
) {
    fun miffGone(miffId: String) {
        scope.launch { remove(miffId) }
    }
}
