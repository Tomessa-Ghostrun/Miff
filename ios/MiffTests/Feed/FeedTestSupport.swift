import Foundation
@testable import Miff

// Des valeurs de démonstration pour les tests du Feed. Aucun nom, aucun numéro ni aucune position réels. Du Swift pur.

/// 2026-11-07, 16 h 00 à Paris (15 h 00 UTC) : l'instant « maintenant » de tous les tests du Feed.
let feedNow = 1_794_063_600_000
let feedZone = TimeZone(identifier: "Europe/Paris") ?? TimeZone.current

func feedMinutes(_ count: Int) -> Int {
    count * FeedMillis.minute
}

func feedHours(_ count: Int) -> Int {
    count * FeedMillis.hour
}

func feedMember(
    _ uid: String,
    first: String? = nil,
    role: MiffRole = .member,
    sharing: Bool = true,
    phone: String? = "+33612345678"
) -> MiffMember {
    MiffMember(
        uid: uid,
        firstName: first ?? "Prénom-\(uid)",
        lastName: "Nom",
        avatarJpeg: nil,
        role: role,
        sharingEnabled: sharing,
        phone: phone
    )
}

func feedPlace(_ id: String, name: String = "Chez Léa", category: PlaceCategory? = .home) -> Place {
    Place(
        id: id,
        miffId: testMiffId,
        name: name,
        type: .trusted,
        category: category,
        address: nil,
        lat: 48.85,
        lng: 2.35,
        radiusM: 100,
        createdBy: "uid-owner"
    )
}

/// Un statut. Les heures sont données en « il y a N millisecondes » (nil : absente).
func feedStatus(
    _ presence: PresenceValue? = .inTrusted,
    placeId: String? = "p1",
    departedAgo: Int? = nil,
    updatedAgo: Int? = nil,
    battery: Int? = 80,
    writtenAgo: Int? = 0,
    access: MemberLocationAccess? = .background
) -> MemberStatus {
    MemberStatus(
        presence: presence,
        placeId: placeId,
        presenceAtMillis: departedAgo.map { feedNow - $0 },
        lastUpdateAtMillis: updatedAgo.map { feedNow - $0 },
        hasPhoto: false,
        batteryPct: battery,
        locationAccess: access,
        updatedAtMillis: writtenAgo.map { feedNow - $0 }
    )
}

func feedInput(
    members: [MiffMember],
    statuses: [String: MemberStatus] = [:],
    places: [Place] = [],
    me: String = "me",
    view: StatusesView? = nil,
    waiting: Bool = false,
    offline: Bool = false
) -> FeedInput {
    FeedInput(
        miffId: testMiffId,
        miffName: "Chez nous",
        joinCode: testCode,
        members: members,
        places: places,
        myUid: me,
        statuses: view ?? .loaded(byUid: statuses, fromCache: false),
        waitingAgreement: waiting,
        offline: offline
    )
}

func buildFeed(
    _ input: FeedInput,
    now: Int = feedNow
) -> FeedContent {
    FeedBuilder.build(input: input, nowMillis: now, zone: feedZone)
}

extension FeedContent {
    func card(_ uid: String) -> MemberCard? {
        cards.first { $0.member.uid == uid }
    }
}
