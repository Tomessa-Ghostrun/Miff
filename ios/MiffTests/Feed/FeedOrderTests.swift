// Les assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

// L'ordre des cartes du Feed par date de la dernière photo publiée (ticket FEED-ORDRE-PHOTO, critères B1 à B15 ; cas T1 à T19 du tableau partagé,
// T20 est dans `PhotoDateWritesTests`) : la fonction d'ordre, son application aux cartes, et le report pendant un geste. Mêmes cas que
// `FeedOrderTest` d'Android : les tests sont le contrat.
final class FeedOrderTests: XCTestCase {
    /// Les membres du tableau du ticket (§9), dans l'ordre d'arrivée : moi, a, b, c, d.
    private let five = [feedMember("me"), feedMember("a"), feedMember("b"), feedMember("c"), feedMember("d")]

    private func entry(_ uid: String, _ date: Int?) -> FeedOrderEntry {
        FeedOrderEntry(uid: uid, photoMillis: date)
    }

    /// Une heure du jour, comme dans le tableau du ticket : plus grand = plus récent ; 15 = « maintenant » (`feedNow`).
    private func at(_ hour: Int) -> Int {
        feedNow - feedHours(15) + feedHours(hour)
    }

    /// Un statut. `photo` : `photoAt` (nil : la clé manque) ; `update` : `lastUpdateAt` ; ils sont indépendants, comme dans les données.
    private func status(
        _ presence: PresenceValue? = .inTrusted,
        photo: Int? = nil,
        update: Int? = nil,
        presenceAt: Int? = nil,
        hasPhoto: Bool = true
    ) -> MemberStatus {
        MemberStatus(
            presence: presence,
            placeId: presence == .unknown || presence == .travel ? nil : "p1",
            presenceAtMillis: presenceAt ?? at(9),
            lastUpdateAtMillis: update,
            hasPhoto: hasPhoto,
            batteryPct: 80,
            locationAccess: .background,
            updatedAtMillis: feedNow,
            photoAtMillis: photo
        )
    }

    /// Un statut dont la dernière photo remonte à `ago` (et dont la dernière update est la même heure).
    private func withPhoto(_ ago: Int) -> MemberStatus {
        status(photo: feedNow - ago, update: feedNow - ago)
    }

    private func order(_ members: [MiffMember], _ statuses: [String: MemberStatus]) -> [String] {
        buildFeed(feedInput(members: members, statuses: statuses)).cards.map { $0.member.uid }
    }

    private func orderOfFive(_ statuses: [String: MemberStatus]) -> [String] {
        order(five, statuses)
    }

    // MARK: - La fonction d'ordre (B1, B3, B4)

    func testTheMostRecentPhotoComesFirst() {
        let sorted = FeedOrder.sorted([entry("a", 100), entry("b", 300), entry("c", 200)])

        XCTAssertEqual(sorted, ["b", "c", "a"])
    }

    func testMembersWithoutADateComeAfterEveryoneInArrivalOrder() {
        let sorted = FeedOrder.sorted([entry("a", nil), entry("b", 100), entry("c", nil), entry("d", 50)])

        XCTAssertEqual(sorted, ["b", "d", "a", "c"])
    }

    func testEqualDatesKeepTheArrivalOrder() {
        let sorted = FeedOrder.sorted([entry("c", 100), entry("a", 100), entry("b", 100)])

        XCTAssertEqual(sorted, ["c", "a", "b"], "l'ordre d'arrivée, jamais l'ordre alphabétique")
    }

    func testT5TheSameArrivalRankAndTheSameDateEndsOnTheIdentifier() {
        // Sur iOS le rang d'arrivée est la position dans la liste : deux membres n'ont jamais le même rang. L'identifiant reste la
        // dernière clé du tri (même contrat qu'Android) : le même identifiant deux fois donne un ordre qui ne change jamais.
        XCTAssertEqual(FeedOrder.sorted([entry("a", 100), entry("a", 100)]), ["a", "a"])
        XCTAssertEqual(FeedOrder.sorted([entry("b", 100), entry("a", 100)]), ["b", "a"], "l'arrivée prime sur l'identifiant")
    }

    func testNoMemberGivesNothingAndOneMemberGivesHimself() {
        XCTAssertEqual(FeedOrder.sorted([]), [])
        XCTAssertEqual(FeedOrder.sorted([entry("me", nil)]), ["me"])
        XCTAssertEqual(FeedOrder.sorted([entry("me", 5)]), ["me"])
    }

    func testT4TheOrderIsTheSameOnAHundredShuffledRuns() {
        let dated = (0..<12).map { entry("d\($0)", 1_000 + $0 * 7) }
        let undated = (0..<4).map { entry("u\($0)", nil) }
        let expectedDated = dated.reversed().map { $0.uid }
        for _ in 0..<100 {
            let shuffled = (dated + undated).shuffled()
            let result = FeedOrder.sorted(shuffled)

            XCTAssertEqual(Array(result.prefix(12)), expectedDated, "les dates distinctes ne dépendent pas de l'arrivée")
            XCTAssertEqual(Array(result.suffix(4)), shuffled.filter { $0.photoMillis == nil }.map { $0.uid })
            XCTAssertEqual(FeedOrder.sorted(shuffled), result, "le même contenu donne toujours le même ordre")
        }
    }

    // MARK: - Le tableau partagé T1 à T19 (cartes)

    func testT1SimpleOrderMeWithoutADate() {
        let statuses = ["a": status(photo: at(10)), "b": status(photo: at(12)), "c": status(photo: at(11)), "me": status(), "d": status()]

        XCTAssertEqual(orderOfFive(statuses), ["b", "c", "a", "me", "d"])
    }

    func testT2MyCardWithTheMostRecentPhotoIsFirst() {
        let statuses = ["me": status(photo: at(13)), "a": status(photo: at(12)), "b": status(photo: at(10))]

        XCTAssertEqual(orderOfFive(statuses), ["me", "a", "b", "c", "d"])
    }

    func testT3MyCardIsSortedLikeTheOthersEvenInTheMiddle() {
        let statuses = ["me": status(photo: at(11)), "a": status(photo: at(12)), "b": status(photo: at(10))]

        XCTAssertEqual(orderOfFive(statuses), ["a", "me", "b", "c", "d"], "B2 : ma carte n'est plus forcée en première position")
    }

    func testT4EqualDatesKeepTheArrivalOrderWhateverTheOrderTheStatusesAreBuiltIn() {
        let pairs = [("a", status(photo: at(12))), ("b", status(photo: at(12)))]
        for _ in 0..<100 {
            let statuses = Dictionary(uniqueKeysWithValues: pairs.shuffled())

            XCTAssertEqual(orderOfFive(statuses), ["a", "b", "me", "c", "d"])
        }
    }

    func testT6NobodyHasADateGivesTheArrivalOrder() {
        XCTAssertEqual(orderOfFive([:]), ["me", "a", "b", "c", "d"])
        XCTAssertEqual(orderOfFive(["me": status(), "a": status(), "b": status(), "c": status(), "d": status()]), ["me", "a", "b", "c", "d"])
    }

    func testT7AnExpiredPhotoKeepsItsRank() {
        let members = [feedMember("a"), feedMember("b"), feedMember("c"), feedMember("me")]
        let withPhoto = ["a": status(photo: at(10)), "b": status(photo: at(9))]
        let photoGone = ["a": status(photo: at(10), hasPhoto: false), "b": status(photo: at(9), hasPhoto: false)]

        XCTAssertEqual(order(members, withPhoto), ["a", "b", "c", "me"])
        XCTAssertEqual(order(members, photoGone), order(members, withPhoto), "la date reste ; seule la vignette disparaît")
        let late = buildFeed(feedInput(members: members, statuses: photoGone), now: feedNow + feedHours(30))
        XCTAssertEqual(late.cards.map { $0.member.uid }, ["a", "b", "c", "me"], "30 h plus tard : même ordre")
    }

    func testT8AnUpdateWithoutAPhotoNeverMovesACardLastUpdateAtIsIgnored() {
        let statuses = ["a": status(photo: at(10), update: at(14)), "b": status(photo: at(11), update: at(11))]

        XCTAssertEqual(orderOfFive(statuses), ["b", "a", "me", "c", "d"])
    }

    func testT9AnArrivalInATrustedPlaceWithoutAPhotoNeverMovesACard() {
        let arrival = status(.inTrusted, photo: nil, update: at(15), presenceAt: at(15), hasPhoto: false)
        let statuses = ["a": arrival, "b": status(photo: at(9))]

        XCTAssertEqual(orderOfFive(statuses), ["b", "me", "a", "c", "d"])
    }

    func testT10NoNewsYetWithAPhotoIsRankedByItsPhoto() {
        let statuses = ["a": status(.unknown, photo: at(12)), "b": status(photo: at(10))]
        let content = buildFeed(feedInput(members: five, statuses: statuses))

        XCTAssertEqual(content.card("a")?.label, .noNews, "le libellé ne change pas")
        XCTAssertEqual(content.cards.map { $0.member.uid }, ["a", "b", "me", "c", "d"])
    }

    func testT11NoNewsYetWithoutAPhotoIsUndatedEvenWithALastUpdateAfterThePresence() {
        // L'ancienne exception de FEED-ORDRE (`lastUpdateAt > presenceAt`) n'existe plus.
        let manual = status(.unknown, photo: nil, update: at(14), presenceAt: at(9), hasPhoto: false)
        let statuses = ["a": manual, "b": status(photo: at(10))]

        XCTAssertEqual(orderOfFive(statuses), ["b", "me", "a", "c", "d"])
    }

    func testT12AStoppedSharingHasNoDateEvenWithAPhotoAtThatWasKept() {
        let members = [feedMember("me"), feedMember("a", sharing: false), feedMember("b"), feedMember("c"), feedMember("d")]
        let statuses = ["a": status(photo: at(14)), "d": status(photo: at(9))]

        XCTAssertEqual(order(members, statuses), ["d", "me", "a", "b", "c"])
    }

    func testT12NewsHiddenWhenIDoNotShareHasNoDateEvenWithAPhotoAt() {
        // Je ne partage plus : les statuts des autres ne sont pas lus (« Informations masquées »), leur `photoAt` n'a aucun poids.
        let members = [feedMember("me", sharing: false), feedMember("a"), feedMember("b"), feedMember("c"), feedMember("d")]
        let statuses = ["a": status(photo: at(14)), "b": status(photo: at(13)), "d": status(photo: at(9))]

        XCTAssertEqual(order(members, statuses), ["me", "a", "b", "c", "d"])
    }

    func testT12WaitingForMyAgreementHasNoDateEvenWithAPhotoAt() {
        let statuses = ["me": status(photo: at(15)), "a": status(photo: at(14)), "d": status(photo: at(9))]

        let content = buildFeed(feedInput(members: five, statuses: statuses, waiting: true))

        XCTAssertEqual(content.cards.map { $0.member.uid }, ["me", "a", "b", "c", "d"])
    }

    func testT13AMemberWithoutAStatusDocumentIsUndated() {
        let statuses = ["b": status(photo: at(10))]

        XCTAssertEqual(orderOfFive(statuses), ["b", "me", "a", "c", "d"])
    }

    func testT14AnUnreadableDateIsUndatedAndNeverCrashes() {
        // La valeur illisible (nulle, texte, nombre) est déjà nil à la lecture (`FeedFirestoreMapping`, voir `PhotoDateMappingTests`) : ici le tri.
        let unreadable = MemberStatus.fromServer(RawStatus(presence: "in_trusted", hasPhoto: true, photoAtMillis: nil))
        let statuses = ["a": unreadable, "b": status(photo: at(10))]

        XCTAssertNil(unreadable.photoAtMillis)
        XCTAssertEqual(orderOfFive(statuses), ["b", "me", "a", "c", "d"])
    }

    func testT15OneMemberOnlyMe() {
        XCTAssertEqual(order([feedMember("me")], ["me": status(photo: at(10))]), ["me"])
        XCTAssertEqual(order([feedMember("me")], [:]), ["me"])
    }

    func testT16ATravelKeepsItsPhotoAtBeforeAndAfterItsEnd() {
        var trip = status(.travel, photo: at(12))
        trip.travelUntilMillis = feedNow + feedHours(48)
        trip.travelUntilDay = "2026-11-09"
        var ended = trip
        ended.travelUntilMillis = feedNow - feedHours(2)
        ended.travelUntilDay = "2026-11-07"

        let during = buildFeed(feedInput(members: five, statuses: ["a": trip, "b": status(photo: at(10))]))
        let after = buildFeed(feedInput(members: five, statuses: ["a": ended, "b": status(photo: at(10))]))

        XCTAssertEqual(during.cards.map { $0.member.uid }, ["a", "b", "me", "c", "d"])
        XCTAssertEqual(after.card("a")?.label, .travelEnded)
        XCTAssertEqual(after.cards.map { $0.member.uid }, ["a", "b", "me", "c", "d"], "plus aucune exception « voyage terminé »")
    }

    func testT17TwoDatesThatOnlyDifferByTheMicrosecondsAreEqualAndKeepTheArrivalOrder() {
        // La troncature à la milliseconde est dans la lecture (`PhotoDateMappingTests`) ; ici deux dates égales à la milliseconde.
        let statuses = ["b": status(photo: at(12)), "a": status(photo: at(12))]

        XCTAssertEqual(orderOfFive(statuses), ["a", "b", "me", "c", "d"])
    }

    func testT18ACardInAlertIsFirstWhateverItsPhoto() throws {
        let alert = try XCTUnwrap(DangerAlertDoc.parse(
            uid: "c", status: "active", startedAtMillis: feedNow - feedMinutes(5), expiresAtMillis: feedNow + feedHours(24), seq: 1
        ))
        var input = feedInput(members: five, statuses: ["a": status(photo: at(12)), "c": status(photo: nil)])
        input.alerts = [alert]

        XCTAssertEqual(buildFeed(input).cards.map { $0.member.uid }, ["c", "a", "me", "b", "d"], "B15 : l'alerte d'abord, puis l'ordre photo")
        input = feedInput(members: five, statuses: ["a": status(photo: at(12)), "c": status(photo: at(1))])
        input.alerts = [alert]
        XCTAssertEqual(buildFeed(input).cards.map { $0.member.uid }, ["c", "a", "me", "b", "d"], "même avec une photo plus ancienne")
    }

    func testT19MyPhotoWaitingForTheServerIsEstimatedAtNowThenTheServerValueKeepsTheOrder() {
        let waiting = ["me": status(photo: feedNow), "a": status(photo: at(12))]
        let confirmed = ["me": status(photo: feedNow - 2_000), "a": status(photo: at(12))]

        XCTAssertEqual(orderOfFive(waiting), ["me", "a", "b", "c", "d"])
        XCTAssertEqual(orderOfFive(confirmed), orderOfFive(waiting), "la valeur du serveur remplace l'estimation sans changer l'ordre")
    }

    // MARK: - Les cartes (B1, B5, B13)

    func testTheCardsFollowThePhotoAndNotTheUrgency() {
        let members = [feedMember("me"), feedMember("a"), feedMember("b")]
        // `a` est rouge (silence de 5 h) mais a publié sa photo AVANT `b`, vert : la couleur ne change pas l'ordre.
        var red = feedStatus(.out, departedAgo: feedHours(5), updatedAgo: feedHours(5))
        red.photoAtMillis = feedNow - feedHours(5)
        var green = feedStatus(.inTrusted, departedAgo: feedMinutes(30), updatedAgo: feedMinutes(30))
        green.photoAtMillis = feedNow - feedMinutes(30)

        XCTAssertEqual(order(members, ["a": red, "b": green]), ["b", "a", "me"])
        XCTAssertEqual(buildFeed(feedInput(members: members, statuses: ["a": red, "b": green])).card("a")?.level, .red)
    }

    func testOnlyANewPhotoMovesACardNotAnUpdateNorAnArrivalNorAnExpiry() {
        let before = ["a": status(photo: at(10), update: at(10)), "b": status(photo: at(11), update: at(11))]
        let noPhotoUpdate = ["a": status(photo: at(10), update: at(15)), "b": before["b"] ?? status()]
        let arrival = ["a": status(.inTrusted, photo: at(10), update: at(15), presenceAt: at(15)), "b": before["b"] ?? status()]
        let expired = ["a": status(photo: at(10), update: at(10), hasPhoto: false), "b": before["b"] ?? status()]
        let newPhoto = ["a": status(photo: at(15), update: at(15)), "b": before["b"] ?? status()]

        for unchanged in [before, noPhotoUpdate, arrival, expired] {
            XCTAssertEqual(orderOfFive(unchanged), ["b", "a", "me", "c", "d"])
        }
        XCTAssertEqual(orderOfFive(newPhoto), ["a", "b", "me", "c", "d"], "B6 : seule une nouvelle photo fait monter la carte")
    }

    func testStatusesNotReadYetGiveTheArrivalOrder() {
        let members = [feedMember("a"), feedMember("me"), feedMember("b")]
        for view in [StatusesView.pending, .unknown, .off] {
            let content = buildFeed(feedInput(members: members, view: view))

            XCTAssertEqual(content.cards.map { $0.member.uid }, ["a", "me", "b"])
        }
    }

    func testTheReaderClockNeverChangesTheOrder() {
        let statuses = ["a": withPhoto(feedHours(1)), "b": withPhoto(feedMinutes(20))]
        let members = [feedMember("a"), feedMember("b"), feedMember("me")]

        for shift in [-feedHours(10), 0, feedHours(10)] {
            let content = buildFeed(feedInput(members: members, statuses: statuses), now: feedNow + shift)

            XCTAssertEqual(content.cards.map { $0.member.uid }, ["b", "a", "me"])
        }
    }

    // MARK: - Le bandeau et les alertes ne changent pas (B10)

    func testTheAlertsKeepTheirOwnOrderWhateverTheOrderOfTheCards() {
        let members = [feedMember("me"), feedMember("a"), feedMember("b")]
        // `a` : silence de 5 h ; `b` : silence de 4 h 30. Les cartes : `b` d'abord (photo la plus récente) ; les alertes : le plus ancien silence d'abord.
        var older = feedStatus(.out, departedAgo: feedHours(6), updatedAgo: feedHours(5))
        older.photoAtMillis = feedNow - feedHours(5)
        var newer = feedStatus(.out, departedAgo: feedHours(5), updatedAgo: feedMinutes(270))
        newer.photoAtMillis = feedNow - feedMinutes(270)

        let content = buildFeed(feedInput(members: members, statuses: ["a": older, "b": newer]))

        XCTAssertEqual(content.cards.map { $0.member.uid }, ["b", "a", "me"])
        guard case .alerts(let pages, _) = content.bubble else {
            return XCTFail("deux rouges : une page par membre")
        }
        XCTAssertEqual(pages.map { $0.member.uid }, ["a", "b"])
    }
}
// swiftlint:enable line_length
