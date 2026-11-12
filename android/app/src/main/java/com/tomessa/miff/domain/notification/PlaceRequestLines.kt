package com.tomessa.miff.domain.notification

import com.tomessa.miff.domain.place.PlaceRequest

/*
 * The lines of the centre of notifications about the requests to add a place (ticket LIEU-REQ, section 7.1), PURE: no Android class, tested on the JVM.
 * PRIVACY: a line holds the SAME things as every other line of the centre (a type, a Miff identifier, a time, a key): NO name of a person, NO name or
 * address of a place. The key carries the identifier of the request (`{uid}_{slot}`: an identifier, not a name); the words ("Léa propose un lieu pour
 * « Famille »") are made when the line is DRAWN, from what the app knows then, and are never stored.
 */
object PlaceRequestLines {
    private const val SEPARATOR = '|'

    /** The key of deduplication: the same request is never written twice in the centre. */
    fun key(
        miffId: String,
        requestId: String,
    ): String = "${NotificationType.N8.code}$SEPARATOR$miffId$SEPARATOR$requestId"

    /** The request a line is about, or null when it is not a line of a request. */
    fun requestIdOf(item: NotificationItem): String? = parts(item)?.second

    /** The person who asked, read in the identifier of the request (`{uid}_{slot}`). */
    fun requesterOf(item: NotificationItem): String? = requestIdOf(item)?.substringBeforeLast('_', "")?.ifEmpty { null }

    private fun parts(item: NotificationItem): Pair<String, String>? {
        if (item.type != NotificationType.N8) return null
        val pieces = item.id.split(SEPARATOR, limit = 3)
        if (pieces.size != 3 || pieces[0] != NotificationType.N8.code) return null
        return pieces[1] to pieces[2]
    }

    /**
     * One line per request the creator now knows. Written WITHOUT a notification of the system (the app is in front: the line is the information), dated
     * when the request was made (never in the future), so that a request of two weeks ago is not "today".
     */
    fun linesFor(
        miffId: String,
        requests: List<PlaceRequest>,
        nowMillis: Long,
    ): List<NotificationItem> =
        requests.map {
            NotificationItem(
                id = key(miffId, it.id),
                type = NotificationType.N8,
                miffId = miffId,
                createdAtMillis = (it.createdAtMillis ?: nowMillis).coerceAtMost(nowMillis),
                state = ItemState.SILENT,
            )
        }

    /**
     * One thing the centre draws for these requests: [ids] are the keys of the lines it stands for (one line, or several of the same person in the same
     * Miff that are all still unread: "Léa a proposé 3 lieux").
     */
    class Entry(
        val ids: List<String>,
        val miffId: String?,
        val requesterUid: String?,
        val createdAtMillis: Long,
        val unread: Boolean,
    ) {
        val count: Int get() = ids.size

        override fun toString(): String = "Entry(redacted)"
    }

    /**
     * The lines of the requests as the centre draws them: the UNREAD ones of one person in one Miff are ONE entry (the newest time), every read one is its
     * own entry. A line that is not about a request is never touched here.
     */
    fun entriesOf(items: List<NotificationItem>): List<Entry> {
        val requestLines = items.filter { it.type == NotificationType.N8 }
        val (unread, read) = requestLines.partition { it.unread }
        val grouped =
            unread
                .groupBy { Pair(it.miffId, requesterOf(it)) }
                .map { (who, lines) ->
                    Entry(
                        ids = lines.map { it.id },
                        miffId = who.first,
                        requesterUid = who.second,
                        createdAtMillis = lines.maxOf { it.createdAtMillis },
                        unread = true,
                    )
                }
        val single =
            read.map {
                Entry(listOf(it.id), it.miffId, requesterOf(it), it.createdAtMillis, unread = false)
            }
        return grouped + single
    }
}
