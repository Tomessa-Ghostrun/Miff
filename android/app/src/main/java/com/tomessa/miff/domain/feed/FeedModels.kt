package com.tomessa.miff.domain.feed

import com.tomessa.miff.domain.miff.MiffMember
import com.tomessa.miff.domain.tracking.StatusLevel

/*
 * PRIVACY, for this file: first names, names of places, the code of the Miff, where somebody is and how their phone is: every class that
 * carries one has a toString() that shows nothing (a log line, an analytics parameter or a crash report can never leak it). The Feed itself
 * logs nothing and sends no analytics event (ticket FEED, D16).
 *
 * These classes are the WHOLE picture the screens draw, computed by [FeedBuilder] from what the listeners and the clock say. They hold facts
 * (a kind of place, a moment, a level), never a sentence: the sentences are texts `feed_*` (French and English, same keys on iOS).
 */

/** The four categories whose NAME is replaced by the category in the sentences ("À la maison"): Maison, Travail, École, Sport (ticket 5.4). */
enum class PlaceKind { HOME, WORK, SCHOOL, SPORT }

/** Where a status says somebody is, as far as this phone can tell (the list of places of THIS Miff is the only source). */
sealed interface PlaceRef {
    data class Category(
        val kind: PlaceKind,
    ) : PlaceRef

    data class Named(
        val name: String,
    ) : PlaceRef {
        override fun toString(): String = "Named(redacted)"
    }

    /** The place was deleted, or the list of places does not hold it: a generic wording, never a guess. */
    data object Unknown : PlaceRef
}

/** The label of a card: the state of a person in a few words (a pictogram goes with it; the colour is never alone). */
sealed interface StateLabel {
    /** In a trusted zone ("À la maison"). */
    data class At(
        val place: PlaceRef,
    ) : StateLabel

    /** Left a trusted zone ("A quitté la maison"), in the colour of the level. */
    data class Left(
        val place: PlaceRef,
    ) : StateLabel

    /** In a danger zone (the wording of the PM, never "zone à risque"). */
    data object Danger : StateLabel

    data object NoNews : StateLabel

    /** The person stopped sharing (calm, nothing wrong). */
    data object SharingStopped : StateLabel

    /** I stopped sharing: the news of the others is not read (and so not shown). */
    data object NewsHidden : StateLabel

    /** My own card while the renewal of my agreement is waiting: nothing is shared with that Miff. */
    data object WaitingAgreement : StateLabel
}

/** The line under the label. ONE time reference per card (ticket A7). */
sealed interface TimeLine {
    data object None : TimeLine

    /** "Depuis 12 min", "Depuis 3 h", "Depuis hier à 08 h 12", "Depuis le 3 nov. à 08 h 12". */
    data class Since(
        val age: Age,
    ) : TimeLine

    /** "Départ il y a 12 min": green, a trusted zone left less than an hour ago (relative while it is green). */
    data class DepartedAgo(
        val age: Age,
    ) : TimeLine

    /** Orange: "Dernière nouvelle à 16 h 02" (an ABSOLUTE time from here on). */
    data class LatestNews(
        val at: Moment,
    ) : TimeLine

    /** Red, no news for a long time: "Pas de nouvelles depuis 16 h 02". */
    data class NoNewsSince(
        val since: Moment,
    ) : TimeLine

    /** Red, in a danger zone: "Depuis 16 h 02". */
    data class InDangerSince(
        val since: Moment,
    ) : TimeLine
}

/** The battery chip: "85 %" alone, never the time it was measured (decision of the PM, 2026-11-07). Hidden after 24 h or when absent. */
data class Battery(
    val pct: Int,
)

/**
 * The place of the photo of the last update on a card (FEED-4, not delivered here): the slot exists in the layout and the state "no photo" draws
 * NOTHING (no hole, no message), the same as an expired or refused photo will.
 */
sealed interface PhotoSlot {
    data object None : PhotoSlot
}

/** One card: a member, me included. MY card is the LAST of the list (no Localiser, no Message, no Appeler on it). */
data class MemberCard(
    val member: MiffMember,
    val isMe: Boolean,
    val level: StatusLevel,
    val label: StateLabel,
    val time: TimeLine,
    val battery: Battery?,
    /** "Localisation limitée": a soft badge, no effect on the bubble. */
    val limitedLocation: Boolean,
    /** The number of the card (E.164), only for the OTHER members: "Écrire à" and "Appeler" exist only with it. */
    val phone: String?,
    /** "Localiser": another member whose status is known, while I share too. */
    val canLocate: Boolean,
    val photo: PhotoSlot = PhotoSlot.None,
) {
    override fun toString(): String = "MemberCard(redacted)"
}

/** What an alert says. */
sealed interface AlertKind {
    /** Orange: "{Prénom} a quitté {lieu} à 16 h 40" (the time of the departure). */
    data class Left(
        val place: PlaceRef,
        val at: Moment,
    ) : AlertKind

    /** Red, no news: "Pas de nouvelles de {Prénom} depuis 16 h 02". */
    data class NoNewsSince(
        val since: Moment,
    ) : AlertKind

    /** Red, danger zone: factual for the loved ones ("{Prénom} est à {lieu}"), "Tu es dans une zone de danger de ta Miff" for the person. */
    data class Danger(
        val place: PlaceRef,
        val since: Moment,
    ) : AlertKind
}

/** One page of the carousel of alerts: one member who is orange or red. */
data class AlertPage(
    val member: MiffMember,
    val isMe: Boolean,
    val level: StatusLevel,
    val kind: AlertKind,
    /** "Envoyer un message": another member with a number (never on my own page). */
    val canMessage: Boolean,
    /** Offline: the time of the last information, said under the alert ("reconnecte-toi pour les mettre à jour"). */
    val offlineSince: Moment?,
) {
    override fun toString(): String = "AlertPage(redacted)"
}

/** The bubble of the Miff: ONE at a time, in the colour of the Miff (the cases of ticket 5.2). It informs, it never says "en sécurité". */
sealed interface Bubble {
    val level: StatusLevel

    /** Everybody known is green; "Dernière nouvelle il y a 2 min" and, if some did not speak, "3 sur 4 ont donné des nouvelles". */
    data class AllGood(
        val latestNews: Age?,
        val answered: Int?,
        val total: Int?,
    ) : Bubble {
        override val level: StatusLevel get() = StatusLevel.GREEN
    }

    /** No other member has a known status: "Pas encore de nouvelles", never a phrase of safety. */
    data object NoNews : Bubble {
        override val level: StatusLevel get() = StatusLevel.NEUTRAL
    }

    data object Alone : Bubble {
        override val level: StatusLevel get() = StatusLevel.NEUTRAL
    }

    /** I stopped sharing: "Tu as arrêté de partager. Reprends le partage pour voir les nouvelles de tes proches." */
    data object SharingStopped : Bubble {
        override val level: StatusLevel get() = StatusLevel.NEUTRAL
    }

    /** My agreement to the new text is waited for: the sentence of the existing card. */
    data object AwaitingAgreement : Bubble {
        override val level: StatusLevel get() = StatusLevel.NEUTRAL
    }

    /** Orange or red: one page per member concerned, reds first (never an automatic scroll). */
    data class Alerts(
        val pages: List<AlertPage>,
        /** The colour of the Miff (the ONE source: [com.tomessa.miff.domain.feed.FeedBuilder.miffLevel]): red if a page is red, else orange. */
        override val level: StatusLevel,
    ) : Bubble {
        override fun toString(): String = "Alerts(redacted)"
    }
}

/** A mention above the bubble. */
sealed interface FeedBanner {
    /** No network: "Dernières informations de 16 h 02" ([latest] null when nothing is known: the generic "Pas de connexion…"). */
    data class Offline(
        val latest: Moment?,
    ) : FeedBanner

    /** My status is not published yet (the rules refuse the reading of the others until it is): "Partage de ton statut en cours…". */
    data object Publishing : FeedBanner
}

/** What the Feed shows for the active Miff once it is read. */
data class FeedContent(
    val miffId: String,
    val miffName: String,
    /** The code to share ("Ajouter un proche"): a secret, only ever given to the share sheet of the system. */
    val joinCode: String,
    /** 15 members or more: "Cette Miff est bien remplie !" replaces the button (a soft ceiling). */
    val isFull: Boolean,
    /** MY colour: the ring of the avatar of the header. */
    val myLevel: StatusLevel,
    val myLabel: StateLabel,
    /** The colour of the whole Miff (ticket 5.2), for the tests and the pictogram; the bubble carries its own level. */
    val miffLevel: StatusLevel,
    val bubble: Bubble,
    val banners: List<FeedBanner>,
    val cards: List<MemberCard>,
    /** Changes only when a LEVEL or the set of alerts changes: the screen reader announces the bubble then, never at each minute. */
    val announceKey: String,
) {
    override fun toString(): String = "FeedContent(redacted)"
}
