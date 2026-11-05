package com.tomessa.miff.domain.feed

import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.miff.MiffSharingConsent
import com.tomessa.miff.domain.place.Place
import com.tomessa.miff.domain.place.PlaceCategory
import com.tomessa.miff.domain.tracking.MemberStatusFacts
import com.tomessa.miff.domain.tracking.PresenceValue
import com.tomessa.miff.domain.tracking.StatusColor
import com.tomessa.miff.domain.tracking.StatusLevel
import java.time.ZoneId

/*
 * The whole logic of the Feed, as ONE pure function (ticket FEED, criteria B1 to B6, C1 to C7): from what the listeners said and the time of
 * the moment, to what the screens draw. Nothing is read, written or logged here. The colour of a member is ONLY ever computed by
 * [StatusColor.level] (the 1 h and 2 h constants live in StatusThresholds, they are never written again here).
 */

/** What the status listener says, as the Feed sees it. */
sealed interface StatusesView {
    /** Not read at all: I stopped sharing, or my agreement is waited for (the rules would refuse, and nothing is wanted: zero reads). */
    data object Off : StatusesView

    /** Attached, no answer yet: the screen shows its skeleton, never a colour nobody knows. */
    data object Waiting : StatusesView

    /** The rules refuse the reading until my own status is published: "Partage de ton statut en cours…", never an error. */
    data object Pending : StatusesView

    /** The listener could not tell anything for a while (and nothing says why): neutral cards, no phrase of safety. */
    data object Unknown : StatusesView

    class Loaded(
        val byUid: Map<String, MemberStatus>,
    ) : StatusesView {
        override fun toString(): String = "Loaded(redacted)"
    }
}

/** Everything the Feed needs about the active Miff. */
class FeedInput(
    val miffId: String,
    val miffName: String,
    val joinCode: String,
    val members: List<MiffMember>,
    val places: List<Place>,
    val myUid: String,
    val statuses: StatusesView,
    /** The renewal of my agreement is waited for (design 23): nothing is shared, the statuses of the others are not read. */
    val waitingAgreement: Boolean,
    val offline: Boolean,
) {
    override fun toString(): String = "FeedInput(redacted)"
}

object FeedBuilder {
    private val LIMITED_ACCESS = setOf(MemberLocationAccess.DENIED, MemberLocationAccess.FOREGROUND)
    private const val BATTERY_DATED_AFTER_MILLIS = HOUR_MILLIS
    private const val BATTERY_HIDDEN_AFTER_MILLIS = DAY_MILLIS

    fun build(
        input: FeedInput,
        nowMillis: Long,
        zone: ZoneId,
    ): FeedContent {
        val mySharing = input.members.firstOrNull { it.uid == input.myUid }?.sharingEnabled ?: true
        // Nothing of the others is read (and nothing is shown) while I do not share or my agreement is waited for.
        val hidden = !mySharing || input.waitingAgreement
        val statuses = (input.statuses as? StatusesView.Loaded)?.byUid.orEmpty()
        // Stable: me first, the others in the order of the list (never reordered by urgency).
        val ordered = input.members.sortedBy { it.uid != input.myUid }
        val places = input.places.associateBy { it.id }

        val cards =
            ordered.map { member ->
                val isMe = member.uid == input.myUid
                val status = if (hidden && !isMe) null else statuses[member.uid]
                card(member, isMe, status, places, input, hidden, mySharing, nowMillis, zone)
            }
        val mine = cards.firstOrNull { it.isMe }
        val miffLevel = miffLevel(cards.map { it.level })
        val latestUpdate = statuses.values.mapNotNull { it.updatedAtMillis }.maxOrNull()
        val banners =
            buildList {
                if (input.offline) add(FeedBanner.Offline(latestUpdate?.let { Moment.of(it, nowMillis, zone) }))
                if (input.statuses == StatusesView.Pending && mySharing &&
                    !input.waitingAgreement
                ) {
                    add(FeedBanner.Publishing)
                }
            }
        val bubble = bubble(input, cards, hidden, mySharing, latestUpdate, nowMillis, zone)
        return FeedContent(
            miffId = input.miffId,
            miffName = input.miffName,
            joinCode = input.joinCode,
            isFull = input.members.size >= MiffSharingConsent.SOFT_MEMBER_LIMIT,
            myLevel = mine?.level ?: StatusLevel.NEUTRAL,
            myLabel = mine?.label ?: StateLabel.NoNews,
            miffLevel = miffLevel,
            bubble = bubble,
            banners = banners,
            cards = cards,
            announceKey = announceKey(bubble),
        )
    }

    /**
     * The colour of the Miff (decision of the PM): red if at least one member is red; green if every member whose status is known is green;
     * otherwise orange. Neutral members (no status, sharing stopped, `unknown`) are IGNORED; nobody known = neutral.
     */
    fun miffLevel(levels: List<StatusLevel>): StatusLevel {
        val known = levels.filter { it != StatusLevel.NEUTRAL }
        return when {
            known.isEmpty() -> StatusLevel.NEUTRAL
            known.any { it == StatusLevel.RED } -> StatusLevel.RED
            known.all { it == StatusLevel.GREEN } -> StatusLevel.GREEN
            else -> StatusLevel.ORANGE
        }
    }

    @Suppress("LongParameterList")
    private fun card(
        member: MiffMember,
        isMe: Boolean,
        status: MemberStatus?,
        places: Map<String, Place>,
        input: FeedInput,
        hidden: Boolean,
        mySharing: Boolean,
        now: Long,
        zone: ZoneId,
    ): MemberCard {
        val facts =
            status?.let {
                MemberStatusFacts(member.sharingEnabled, it.presence, it.presenceAtMillis, it.lastUpdateAtMillis)
            }
        val level = StatusColor.level(facts, now)
        val label =
            when {
                isMe && input.waitingAgreement -> {
                    StateLabel.WaitingAgreement
                }

                !member.sharingEnabled -> {
                    StateLabel.SharingStopped
                }

                hidden && !isMe -> {
                    StateLabel.NewsHidden
                }

                level == StatusLevel.NEUTRAL || status == null -> {
                    StateLabel.NoNews
                }

                status.presence == PresenceValue.IN_TRUSTED -> {
                    StateLabel.At(
                        placeRef(status.placeId, places, trusted = true),
                    )
                }

                status.presence == PresenceValue.IN_RISK -> {
                    StateLabel.Danger
                }

                else -> {
                    StateLabel.Left(placeRef(status.placeId, places, trusted = true))
                }
            }
        return MemberCard(
            member = member,
            isMe = isMe,
            level = level,
            label = label,
            time = timeLine(level, status, now, zone),
            battery = status?.let { battery(it, now, zone) },
            limitedLocation =
                status?.locationAccess in LIMITED_ACCESS,
            phone = member.phone.takeIf { !isMe },
            canLocate = !isMe && mySharing && !hidden && member.sharingEnabled && level != StatusLevel.NEUTRAL,
        )
    }

    private fun placeRef(
        placeId: String?,
        places: Map<String, Place>,
        trusted: Boolean,
    ): PlaceRef {
        val place = placeId?.let { places[it] } ?: return PlaceRef.Unknown
        if (!trusted) return PlaceRef.Named(place.name)
        val kind =
            when (place.category) {
                PlaceCategory.HOME -> PlaceKind.HOME
                PlaceCategory.WORK -> PlaceKind.WORK
                PlaceCategory.SCHOOL -> PlaceKind.SCHOOL
                PlaceCategory.SPORT -> PlaceKind.SPORT
                else -> null
            }
        return if (kind != null) PlaceRef.Category(kind) else PlaceRef.Named(place.name)
    }

    private fun timeLine(
        level: StatusLevel,
        status: MemberStatus?,
        now: Long,
        zone: ZoneId,
    ): TimeLine {
        if (status == null || level == StatusLevel.NEUTRAL) return TimeLine.None
        val presenceAt = status.presenceAtMillis
        return when (status.presence) {
            PresenceValue.IN_TRUSTED -> {
                presenceAt?.let { TimeLine.Since(Age.of(it, now, zone)) } ?: TimeLine.None
            }

            PresenceValue.IN_RISK -> {
                presenceAt?.let { TimeLine.InDangerSince(Moment.of(it, now, zone)) } ?: TimeLine.None
            }

            PresenceValue.OUT -> {
                val reference = status.referenceMillis ?: return TimeLine.None
                when (level) {
                    StatusLevel.GREEN -> {
                        val departure = presenceAt ?: reference
                        val age = Age.of(departure, now, zone)
                        // A departure of a day or more that is still green (a recent update): only the news is told.
                        if (age is Age.Minutes || age is Age.Hours) {
                            TimeLine.DepartedAgo(age)
                        } else {
                            TimeLine.LatestNews(Moment.of(reference, now, zone))
                        }
                    }

                    StatusLevel.ORANGE -> {
                        TimeLine.LatestNews(Moment.of(reference, now, zone))
                    }

                    else -> {
                        TimeLine.NoNewsSince(Moment.of(reference, now, zone))
                    }
                }
            }

            else -> {
                TimeLine.None
            }
        }
    }

    private fun battery(
        status: MemberStatus,
        now: Long,
        zone: ZoneId,
    ): Battery? {
        val pct = status.batteryPct ?: return null
        // A time the server has not filled in yet (a write still pending) is "now".
        val written = status.updatedAtMillis ?: return Battery(pct, since = null)
        val age = now - written
        return when {
            age > BATTERY_HIDDEN_AFTER_MILLIS -> null
            age > BATTERY_DATED_AFTER_MILLIS -> Battery(pct, Moment.of(written, now, zone))
            else -> Battery(pct, since = null)
        }
    }

    @Suppress("LongParameterList")
    private fun bubble(
        input: FeedInput,
        cards: List<MemberCard>,
        hidden: Boolean,
        mySharing: Boolean,
        latestUpdate: Long?,
        now: Long,
        zone: ZoneId,
    ): Bubble {
        if (input.waitingAgreement) return Bubble.AwaitingAgreement
        if (!mySharing) return Bubble.SharingStopped
        val statuses = (input.statuses as? StatusesView.Loaded)?.byUid.orEmpty()
        val pages = alertPages(input, cards, statuses, latestUpdate, now, zone)
        if (pages.isNotEmpty()) return Bubble.Alerts(pages)
        val others = cards.filter { !it.isMe }
        if (others.isEmpty()) return Bubble.Alone
        val knownOthers = others.filter { it.level != StatusLevel.NEUTRAL }
        if (hidden || knownOthers.isEmpty()) return Bubble.NoNews
        // No alert, so every known member is green. The "latest news" is the most recent of the OTHERS (my own update never reassures).
        val latest =
            knownOthers
                .mapNotNull { statuses[it.member.uid]?.referenceMillis }
                .maxOrNull()
                ?.let { Age.of(it, now, zone) }
        val known = cards.count { it.level != StatusLevel.NEUTRAL }
        val someSilent = known < cards.size
        return Bubble.AllGood(
            latestNews = latest,
            answered = if (someSilent) known else null,
            total = if (someSilent) cards.size else null,
        )
    }

    /** Reds first, then oranges; at the same level the oldest reference first (the person who has been silent the longest). */
    private fun alertPages(
        input: FeedInput,
        cards: List<MemberCard>,
        statuses: Map<String, MemberStatus>,
        latestUpdate: Long?,
        now: Long,
        zone: ZoneId,
    ): List<AlertPage> {
        val places = input.places.associateBy { it.id }
        val offlineSince = if (input.offline) latestUpdate?.let { Moment.of(it, now, zone) } else null
        return cards
            .filter { it.level == StatusLevel.RED || it.level == StatusLevel.ORANGE }
            .mapNotNull { card ->
                val status = statuses[card.member.uid] ?: return@mapNotNull null
                val reference = status.referenceMillis ?: return@mapNotNull null
                val kind =
                    when (status.presence) {
                        PresenceValue.IN_RISK -> {
                            AlertKind.Danger(
                                placeRef(status.placeId, places, trusted = false),
                                Moment.of(status.presenceAtMillis ?: reference, now, zone),
                            )
                        }

                        else -> {
                            if (card.level == StatusLevel.RED) {
                                AlertKind.NoNewsSince(Moment.of(reference, now, zone))
                            } else {
                                AlertKind.Left(
                                    placeRef(status.placeId, places, trusted = true),
                                    Moment.of(status.presenceAtMillis ?: reference, now, zone),
                                )
                            }
                        }
                    }
                SortableAlert(
                    page =
                        AlertPage(
                            member = card.member,
                            isMe = card.isMe,
                            level = card.level,
                            kind = kind,
                            canMessage = !card.isMe && card.phone != null,
                            // Offline: "Informations de 16 h 02 : reconnecte-toi pour les mettre à jour."
                            offlineSince = offlineSince,
                        ),
                    reference =
                        status.presenceAtMillis.takeIf {
                            status.presence == PresenceValue.IN_RISK
                        } ?: reference,
                )
            }.sortedWith(
                compareBy<SortableAlert> { if (it.page.level == StatusLevel.RED) 0 else 1 }.thenBy { it.reference },
            ).map { it.page }
    }

    private class SortableAlert(
        val page: AlertPage,
        val reference: Long,
    )

    private fun announceKey(bubble: Bubble): String =
        when (bubble) {
            is Bubble.Alerts -> {
                "alerts:" +
                    bubble.pages.joinToString(",") { "${it.member.uid}/${it.level}/${it.kind::class.simpleName}" }
            }

            else -> {
                bubble::class.simpleName.orEmpty()
            }
        }
}
