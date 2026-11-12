package com.tomessa.miff.data.push

import com.tomessa.miff.domain.notification.CircleNewsParser
import com.tomessa.miff.domain.notification.CircleNewsPayload

/*
 * What happens to a DATA message of the server (ticket docs/tickets/notification-update-membres.md, 10 and 11). No class of the system here, so that it is tested on the JVM.
 *
 * SECURITY: the message comes from the network, so NOTHING of it is trusted: [CircleNewsParser] accepts one type, checks every field (shape, length, range) and ignores
 * everything else, including the keys FCM adds itself. A message of another type, a malformed one, one that arrives with nobody signed in, is ignored without a trace
 * (no log, no event, no error). COST: no read of Firestore at the arrival of a push; everything needed is in the message.
 */
class PushMessageHandler(
    /** The account signed in on this phone, or null. */
    private val currentUid: () -> String?,
    /** Hands a valid "a loved one gave news" to the notifications (they decide: caps, switches, foreground, night). */
    private val onCircleNews: (uid: String, news: CircleNewsPayload) -> Unit,
) {
    /** Returns true when the message was understood and handed on (it may still show nothing: the notifications decide). */
    fun handle(data: Map<String, String>): Boolean {
        // A message with many keys is not ours (the real one has five); no need to look at it.
        if (data.isEmpty() || data.size > MAX_KEYS) return false
        val news = CircleNewsParser.parse(data) ?: return false
        val uid = currentUid() ?: return false
        onCircleNews(uid, news)
        return true
    }

    private companion object {
        const val MAX_KEYS = 20
    }
}
