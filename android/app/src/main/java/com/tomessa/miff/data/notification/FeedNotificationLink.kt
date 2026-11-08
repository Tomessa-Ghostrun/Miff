package com.tomessa.miff.data.notification

import com.tomessa.miff.domain.notification.SnapshotMember

/**
 * What the Feed tells the notifications (NOTIF-4A): the statuses it just read for the Miff it shows, and the choice of another Miff. Local: the callee keeps a snapshot
 * on the phone and plans the reminders from it; nothing is read or written on the server because of this call. It carries identifiers, the keyword of the presence and
 * two times per member, never a place, a position, an address or a name.
 */
interface FeedNotificationLink {
    fun onStatuses(
        miffId: String,
        myUid: String,
        members: List<SnapshotMember>,
    )

    /** Another Miff was chosen in the selector (Premium): nothing of the old one stays. */
    fun onActiveMiff(miffId: String)

    companion object {
        val None =
            object : FeedNotificationLink {
                override fun onStatuses(
                    miffId: String,
                    myUid: String,
                    members: List<SnapshotMember>,
                ) = Unit

                override fun onActiveMiff(miffId: String) = Unit
            }
    }
}
