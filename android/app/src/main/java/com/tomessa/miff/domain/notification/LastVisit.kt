package com.tomessa.miff.domain.notification

import com.tomessa.miff.domain.feed.FeedBuilder
import com.tomessa.miff.domain.feed.MemberStatus
import com.tomessa.miff.domain.feed.PlaceKind
import com.tomessa.miff.domain.feed.StatusesView
import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.place.Place
import com.tomessa.miff.domain.tracking.MemberStatusFacts
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.domain.tracking.StatusColor
import com.tomessa.miff.domain.tracking.StatusLevel
import com.tomessa.miff.domain.tracking.StatusThresholds
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/*
 * NOTIF-P1A (ticket docs/tickets/notifications-p1-derniere-visite.md, "Depuis ta dernière visite", criteria C1-T1 to C1-T20): PURE, no Android class, tested on the JVM,
 * and the contract the iOS app copies. Nothing is read from or written to the server: the comparison is made between two pictures the Feed already holds.
 *
 * PRIVACY (criterion C1-7): a picture holds, per OTHER member, an identifier, the KEYWORD of his presence, the CATEGORY of his place (a kind, never the name or the
 * address of a place) and two times. NO position, NO name of a place, NO name of a person, NO photo. A line of the centre holds the same things, in its key.
 */

/** What the Feed showed about ONE other member of the active Miff, as facts. [muted]: nothing is ever said about him (see [LastVisit.sample]). */
class VisitMember(
    val uid: String,
    val muted: Boolean,
    val presence: PresenceValue?,
    /** The category of the place of the presence (Maison, Travail, École, Sport, Chez un proche); null for Loisir, Autre, no category or a place the list does not hold. */
    val place: PlaceKind?,
    val presenceAtMillis: Long?,
    val lastUpdateAtMillis: Long?,
    /** "Silence": out of every trusted zone and red (more than 4 h without news) when the picture was taken. */
    val silent: Boolean,
) {
    override fun equals(other: Any?): Boolean =
        other is VisitMember &&
            uid == other.uid && muted == other.muted && presence == other.presence && place == other.place &&
            presenceAtMillis == other.presenceAtMillis && lastUpdateAtMillis == other.lastUpdateAtMillis &&
            silent == other.silent

    override fun hashCode(): Int =
        listOf(uid, muted, presence, place, presenceAtMillis, lastUpdateAtMillis, silent).hashCode()

    override fun toString(): String = "VisitMember(redacted)"
}

/** The picture of ONE Miff (the others only: never me). */
class VisitState(
    val miffId: String,
    val members: List<VisitMember>,
) {
    override fun equals(other: Any?): Boolean =
        other is VisitState && miffId == other.miffId && members == other.members

    override fun hashCode(): Int = miffId.hashCode() * 31 + members.hashCode()

    override fun toString(): String = "VisitState(redacted)"
}

/** The four sorts of line (ticket 3.3). [code] is the letter kept in the key of the line. */
enum class VisitKind(
    val code: Char,
) {
    ARRIVAL('a'),
    DEPARTURE('d'),
    UPDATE('u'),
    SILENCE('s'),
    ;

    companion object {
        fun fromCode(code: Char?): VisitKind? = entries.firstOrNull { it.code == code }
    }
}

/** One line "Depuis ta dernière visite", before it becomes a line of the centre. */
class VisitLine(
    val uid: String,
    val kind: VisitKind,
    /** The category of the place for an arrival or a departure; null says "un lieu de confiance". */
    val place: PlaceKind?,
    /** The instant of the CHANGE (for a silence: when he became red). It orders the lines. */
    val atMillis: Long,
    /** The time the sentence says (for a silence: the time of his latest news). */
    val shownAtMillis: Long,
) {
    override fun toString(): String = "VisitLine(redacted)"
}

/** How a time is said in a line (ticket 3.3 and decisions of 2026-11-13, point 2). */
enum class VisitDay { TODAY, YESTERDAY, WEEKDAY, DATE }

object LastVisit {
    /** Coming back to the front after less than this does not compare (decision Q3 of the PM): a quick back-and-forth makes no noise. */
    const val MIN_ABSENCE_MILLIS = 30L * 60L * 1000L

    /** At most this many lines (decision Q7). */
    const val MAX_LINES = 3

    /** An arrival also writes the last update: an update this close to the arrival is the same event, not a second news. */
    const val SAME_EVENT_MILLIS = 5L * 60L * 1000L

    /** The server accepts a time up to 5 minutes ahead of its own clock: a phone with a wrong clock may see one in its future. Beyond it, no line. */
    const val CLOCK_TOLERANCE_MILLIS = 5L * 60L * 1000L

    private const val RECENT_DAYS = 6L
    private const val PRIORITY_UPDATE = 1
    private const val PRIORITY_MOVEMENT = 2
    private const val PRIORITY_SILENCE = 3

    /**
     * The picture of the Miff the Feed shows, or null when it is NOT a fresh one (criterion C1-T14): the statuses are not read, or come from the cache of the
     * phone, or the phone is offline, or my sharing is stopped or my agreement waited for (the news of the others is then hidden). A line is never built on a
     * stale picture, and a stale picture never becomes "what was seen".
     */
    fun sample(
        miffId: String,
        members: List<MiffMember>,
        places: List<Place>,
        myUid: String,
        statuses: StatusesView,
        waitingAgreement: Boolean,
        offline: Boolean,
        nowMillis: Long,
    ): VisitState? {
        val loaded = statuses as? StatusesView.Loaded ?: return null
        if (loaded.fromCache || offline || waitingAgreement) return null
        if (members.firstOrNull { it.uid == myUid }?.sharingEnabled == false) return null
        val placesById = places.associateBy { it.id }
        return VisitState(
            miffId,
            members.filter { it.uid != myUid }.map { member ->
                val status = loaded.byUid[member.uid]
                member(member, status, placesById, nowMillis)
            },
        )
    }

    private fun member(
        member: MiffMember,
        status: MemberStatus?,
        places: Map<String, Place>,
        nowMillis: Long,
    ): VisitMember {
        val presence = status?.presence
        // Nothing is said about somebody who stopped sharing, is on a trip, or has no state a person could read (no status, unknown).
        val muted =
            !member.sharingEnabled || status == null ||
                presence == null || presence == PresenceValue.UNKNOWN || presence == PresenceValue.TRAVEL
        val facts =
            status?.let {
                MemberStatusFacts(
                    member.sharingEnabled,
                    it.presence,
                    it.presenceAtMillis,
                    it.lastUpdateAtMillis,
                    it.travelUntilMillis,
                )
            }
        val silent = !muted && presence == PresenceValue.OUT && StatusColor.level(facts, nowMillis) == StatusLevel.RED
        return VisitMember(
            uid = member.uid,
            muted = muted,
            presence = presence,
            place =
                if (muted) {
                    null
                } else {
                    status
                        ?.placeId
                        ?.let { places[it] }
                        ?.category
                        ?.let(FeedBuilder::sentenceKind)
                },
            presenceAtMillis = status?.presenceAtMillis,
            lastUpdateAtMillis = status?.lastUpdateAtMillis,
            silent = silent,
        )
    }

    /**
     * `compare(seen, current, now) -> lines`. [seen] is the picture of the last visit: null (first visit, reconnection, access lost) gives no line, and so does a picture of
     * another Miff (criterion C1-T17). At most [MAX_LINES], the most recent change first.
     */
    fun compare(
        seen: VisitState?,
        current: VisitState,
        nowMillis: Long,
    ): List<VisitLine> {
        if (seen == null || seen.miffId != current.miffId) return emptyList()
        val before = seen.members.associateBy { it.uid }
        return current.members
            .mapNotNull { member -> before[member.uid]?.let { line(it, member, nowMillis) } }
            .sortedWith(compareByDescending<VisitLine> { it.atMillis }.thenBy { it.uid })
            .take(MAX_LINES)
    }

    private class Candidate(
        val atMillis: Long,
        val priority: Int,
        val kind: VisitKind,
        val place: PlaceKind?,
        val shownAtMillis: Long,
    )

    private fun line(
        seen: VisitMember,
        now: VisitMember,
        nowMillis: Long,
    ): VisitLine? {
        // Nobody stopped sharing, travelling or unknown is spoken of, before or after (C1-T10), and a danger zone is the business of the Feed and N6 (C1-T11, Q6).
        if (seen.muted || now.muted || now.presence == PresenceValue.IN_RISK) return null
        val candidates = mutableListOf<Candidate>()
        val movement = movement(seen, now)
        movement?.let(candidates::add)
        update(seen, now, movement)?.let(candidates::add)
        silence(seen, now)?.let(candidates::add)
        // A time in the future (a phone whose clock is wrong) makes no absurd line: a few minutes ahead count as now, further than that nothing is said.
        val usable =
            candidates
                .filter { it.atMillis <= nowMillis + CLOCK_TOLERANCE_MILLIS }
                .map {
                    Candidate(
                        minOf(it.atMillis, nowMillis),
                        it.priority,
                        it.kind,
                        it.place,
                        minOf(it.shownAtMillis, nowMillis),
                    )
                }
        val best = usable.maxWithOrNull(compareBy<Candidate>({ it.atMillis }, { it.priority })) ?: return null
        return VisitLine(now.uid, best.kind, best.place, best.atMillis, best.shownAtMillis)
    }

    /** An arrival in, or a departure from, a trusted zone: the time of the presence moved FORWARD since the last visit. */
    private fun movement(
        seen: VisitMember,
        now: VisitMember,
    ): Candidate? {
        val at = now.presenceAtMillis ?: return null
        if (seen.presenceAtMillis != null && at <= seen.presenceAtMillis) return null
        return when (now.presence) {
            PresenceValue.IN_TRUSTED -> {
                Candidate(at, PRIORITY_MOVEMENT, VisitKind.ARRIVAL, now.place, at)
            }

            // Leaving a danger zone is not "leaving a trusted place" (Q6).
            PresenceValue.OUT -> {
                if (seen.presence ==
                    PresenceValue.IN_RISK
                ) {
                    null
                } else {
                    Candidate(at, PRIORITY_MOVEMENT, VisitKind.DEPARTURE, now.place, at)
                }
            }

            else -> {
                null
            }
        }
    }

    private fun update(
        seen: VisitMember,
        now: VisitMember,
        movement: Candidate?,
    ): Candidate? {
        val at = now.lastUpdateAtMillis ?: return null
        if (seen.lastUpdateAtMillis != null && at <= seen.lastUpdateAtMillis) return null
        // An arrival writes the last update too: that one is the arrival, not a second piece of news.
        if (movement != null && movement.kind == VisitKind.ARRIVAL &&
            abs(at - movement.atMillis) <= SAME_EVENT_MILLIS
        ) {
            return null
        }
        return Candidate(at, PRIORITY_UPDATE, VisitKind.UPDATE, null, at)
    }

    /** The person just became red (more than 4 h without news) and was not at the last visit (C1-T6, C1-T7); orange is never a silence (C1-T8). */
    private fun silence(
        seen: VisitMember,
        now: VisitMember,
    ): Candidate? {
        if (!now.silent || seen.silent) return null
        val reference = listOfNotNull(now.presenceAtMillis, now.lastUpdateAtMillis).maxOrNull() ?: return null
        return Candidate(
            reference + StatusThresholds.RED_AFTER_MILLIS + 1,
            PRIORITY_SILENCE,
            VisitKind.SILENCE,
            null,
            reference,
        )
    }

    /** True when the app was away long enough for a new comparison (ticket 3.1, Q3). */
    fun isReopening(absenceMillis: Long): Boolean = absenceMillis < 0 || absenceMillis >= MIN_ABSENCE_MILLIS

    // ---- the lines of the centre ---------------------------------------------------------------------------------------------------------------------

    private const val SEPARATOR = '|'

    /** The key of a line (`c1|miff|uid|kind|place|shownAt`): identifiers, a letter, a category code and a time, never a name. */
    fun key(
        miffId: String,
        line: VisitLine,
    ): String =
        listOf(
            NotificationType.C1.code,
            miffId,
            line.uid,
            line.kind.code.toString(),
            line.place?.name.orEmpty(),
            line.shownAtMillis.toString(),
        ).joinToString(SEPARATOR.toString())

    /** The lines of the centre for these lines: written WITHOUT a notification of the system (the app is in front: the line is the information), unread (Q2). */
    fun items(
        miffId: String,
        lines: List<VisitLine>,
    ): List<NotificationItem> =
        lines.map {
            NotificationItem(
                id = key(miffId, it),
                type = NotificationType.C1,
                miffId = miffId,
                createdAtMillis = it.atMillis,
                state = ItemState.SILENT,
            )
        }

    /** What a line of the centre says, read back from its key; null when the item is not a well-formed line of this sort. */
    class Parsed(
        val uid: String,
        val kind: VisitKind,
        val place: PlaceKind?,
        val shownAtMillis: Long,
    ) {
        override fun toString(): String = "Parsed(redacted)"
    }

    fun parse(item: NotificationItem): Parsed? {
        if (item.type != NotificationType.C1) return null
        val pieces = item.id.split(SEPARATOR)
        if (pieces.size != KEY_PARTS || pieces[0] != NotificationType.C1.code) return null
        val kind = VisitKind.fromCode(pieces[3].singleOrNull()) ?: return null
        val place =
            if (pieces[4].isEmpty()) {
                null
            } else {
                PlaceKind.entries.firstOrNull { it.name == pieces[4] }
                    ?: return null
            }
        val shownAt = pieces[5].toLongOrNull() ?: return null
        return Parsed(pieces[2], kind, place, shownAt)
    }

    private const val KEY_PARTS = 6

    /** The member a line of the centre is about, or null. */
    fun uidOf(item: NotificationItem): String? = parse(item)?.uid

    // ---- days ----------------------------------------------------------------------------------------------------------------------------------------

    /** Which way a time is said: today, yesterday, the day of the week (2 to 6 days back), or the short date. A time ahead of today counts as today. */
    fun dayOf(
        atMillis: Long,
        nowMillis: Long,
        zone: ZoneId,
    ): VisitDay {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val day = Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate()
        val back = today.toEpochDay() - day.toEpochDay()
        return when {
            back <= 0L -> VisitDay.TODAY
            back == 1L -> VisitDay.YESTERDAY
            back <= RECENT_DAYS -> VisitDay.WEEKDAY
            else -> VisitDay.DATE
        }
    }
}
