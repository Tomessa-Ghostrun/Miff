package com.tomessa.miff.data.notification

import com.tomessa.miff.domain.notification.VisitState

/**
 * What the Feed tells "Depuis ta dernière visite" (NOTIF-P1A): the picture of the active Miff it is showing, when that picture is FRESH (confirmed by the server, online, my sharing on).
 * Local: the callee compares it with the picture of the last visit and keeps it on the phone; nothing is read or written on the server because of this call. [firstNames] are
 * the first names of the members of the picture by identifier, for the sentences of the centre (kept in memory only, for the session; the centre itself stores no name).
 */
interface FeedVisitLink {
    fun onFreshPicture(
        myUid: String,
        state: VisitState,
        firstNames: Map<String, String>,
    )

    companion object {
        val None =
            object : FeedVisitLink {
                override fun onFreshPicture(
                    myUid: String,
                    state: VisitState,
                    firstNames: Map<String, String>,
                ) = Unit
            }
    }
}
