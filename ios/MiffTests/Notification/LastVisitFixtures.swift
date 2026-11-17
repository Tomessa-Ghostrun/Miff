import Foundation
@testable import Miff

// Des valeurs de démonstration pour les tests de « Depuis ta dernière visite » (NOTIF-P1I). Aucun nom, aucune position réels. L'instant
// « maintenant » est celui du Feed (`feedNow` : 2026-11-07, 16 h 00 à Paris).

enum VisitFixtures {
    static let minute = 60_000
    static let hour = 3_600_000
    static let now = feedNow
    static let miff = "miff-1"

    /// Un membre vu. `presenceAt` vaut « maintenant - 10 h » sans autre précision ; `lastUpdateAt` vaut `presenceAt` sans autre précision.
    static func member(
        _ uid: String,
        _ presence: PresenceValue? = .inTrusted,
        _ place: PlaceKind? = .school,
        presenceAt: Int? = nil,
        lastUpdateAt: Int? = nil,
        silent: Bool = false,
        muted: Bool = false
    ) -> VisitMember {
        let at = presenceAt ?? now - 10 * hour
        return VisitMember(
            uid: uid,
            muted: muted,
            presence: presence,
            place: place,
            presenceAtMillis: at,
            lastUpdateAtMillis: lastUpdateAt ?? at,
            silent: silent
        )
    }

    static func state(_ members: [VisitMember], miffId: String = miff) -> VisitState {
        VisitState(miffId: miffId, members: members)
    }

    static func compare(_ seen: VisitState?, _ current: VisitState, at nowMillis: Int = now) -> [VisitLine] {
        LastVisit.compare(seen: seen, current: current, nowMillis: nowMillis)
    }

    static func line(
        _ uid: String,
        _ kind: VisitKind = .update,
        place: PlaceKind? = nil,
        at: Int = now
    ) -> VisitLine {
        VisitLine(uid: uid, kind: kind, place: place, atMillis: at, shownAtMillis: at)
    }

    /// La ligne du centre d'un seul changement.
    static func item(_ uid: String, at: Int = now, miffId: String = miff) -> NotificationItem {
        LastVisit.items(miffId: miffId, lines: [line(uid, at: at)])[0]
    }

    // MARK: - Pour `LastVisit.sample`

    static func loaded(_ byUid: [String: MemberStatus], fromCache: Bool = false) -> StatusesView {
        .loaded(byUid: byUid, fromCache: fromCache)
    }

    static func sampleInput(
        members: [MiffMember],
        places: [Place] = [],
        statuses: StatusesView,
        waiting: Bool = false,
        offline: Bool = false
    ) -> VisitSampleInput {
        VisitSampleInput(
            miffId: miff,
            members: members,
            places: places,
            myUid: "me",
            statuses: statuses,
            waitingAgreement: waiting,
            offline: offline
        )
    }

    static func sample(_ input: VisitSampleInput) -> VisitState? {
        LastVisit.sample(input, nowMillis: now)
    }
}
