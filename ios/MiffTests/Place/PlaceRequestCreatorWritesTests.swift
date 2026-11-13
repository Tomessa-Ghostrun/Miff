import XCTest
@testable import Miff

/// Les lots du CRÉATEUR (ticket LIEU-REQ-C, contrat `docs/dev/demande-lieu-clients.md` §2.3 à §2.6, §2.8) en données, et ce que le Profil en
/// dit : accepter copie la demande en un lieu (règles de création d'un lieu inchangées), refuser ne touche qu'à la demande et au compteur, le
/// compteur est toujours une valeur ABSOLUE, et l'onglet Profil porte un point sans nombre.
final class PlaceRequestCreatorWritesTests: XCTestCase {
    private let uid = testCreatorUid

    func testAcceptingCopiesTheRequestIntoAPlaceWithTheRulesOfACreationByTheCreator() {
        let request = testRequest(name: "Chez Mamie", type: .risk, category: .relative)

        let lot = PlaceRequestWrites.accept(uid: uid, request: request, placeId: "p2", remaining: 4)

        XCTAssertEqual(lot.map { $0.kind }, [.set, .delete, .set])
        XCTAssertEqual(lot[0].path, "miffs/\(testMiffId)/places/p2")
        let place = lot[0].data
        // Chaque clé d'un lieu, comme la création par le créateur.
        let keys: Set<String> = [
            "name", "type", "category", "address", "lat", "lng", "radiusM",
            "priority", "createdBy", "createdAt", "updatedAt"
        ]
        XCTAssertEqual(Set(place.keys), keys)
        XCTAssertEqual(place["name"], .string("Chez Mamie"))
        XCTAssertEqual(place["type"], .string("risk"))
        XCTAssertEqual(place["category"], .string("relative"))
        XCTAssertEqual(place["radiusM"], .integer(100))
        XCTAssertEqual(place["createdBy"], .string(uid), "le lieu accepté n'indique pas qui l'a demandé")
        XCTAssertEqual(lot[2].data["pending"], .integer(4))
        XCTAssertEqual(lot[2].data["lastReqId"], .null)
    }

    func testDecliningTouchesOnlyTheRequestAndTheCounter() {
        let lot = PlaceRequestWrites.decline(miffId: testMiffId, requestId: "\(testMemberUid)_1", remaining: 0)

        XCTAssertEqual(lot.map { $0.path }, [
            "miffs/\(testMiffId)/placeRequests/\(testMemberUid)_1",
            "miffs/\(testMiffId)/meta/placeRequests"
        ])
        XCTAssertEqual(lot.map { $0.kind }, [.delete, .set])
        XCTAssertEqual(Set(lot[1].data.keys), ["pending", "lastReqId", "updatedAt"])
    }

    func testTheCreatorCounterIsAlwaysAnAbsoluteValueInsideTheBounds() {
        XCTAssertEqual(PlaceRequestWrites.creatorCounter(pending: 3)["pending"], .integer(3))
        // Jamais négatif (un lot refusé), jamais 11 (refusé par les règles).
        XCTAssertEqual(PlaceRequestWrites.creatorCounter(pending: -1)["pending"], .integer(0))
        XCTAssertEqual(PlaceRequestWrites.creatorCounter(pending: 99)["pending"], .integer(10))
        for lot in [
            PlaceRequestWrites.decline(miffId: testMiffId, requestId: "a_1", remaining: 2),
            PlaceRequestWrites.purge(miffId: testMiffId, requestIds: ["a_1"], remaining: 2)
        ] {
            XCTAssertFalse(lot.contains { write in
                if case .increment? = write.data["pending"] {
                    return true
                }
                return false
            })
        }
    }

    func testPurgingDeletesTheRequestsThenSetsTheCounter() {
        let lot = PlaceRequestWrites.purge(miffId: testMiffId, requestIds: ["a_1", "b_2"], remaining: 1)

        XCTAssertEqual(lot.map { $0.kind }, [.delete, .delete, .set])
        XCTAssertEqual(lot.last?.path, "miffs/\(testMiffId)/meta/placeRequests")
    }

    func testDeletingEverythingOfAMiffIsNeverAnEmptyLot() {
        let withCounter = PlaceRequestWrites.deleteAll(miffId: testMiffId, requestIds: [], withCounter: true)
        let requestsOnly = PlaceRequestWrites.deleteAll(miffId: testMiffId, requestIds: ["a_1"], withCounter: false)

        XCTAssertEqual(withCounter.map { $0.kind }, [.delete])
        XCTAssertEqual(requestsOnly.count, 1)
        XCTAssertTrue(SessionWriteGuard.isSendable(withCounter) && SessionWriteGuard.isSendable(requestsOnly))
    }

    // MARK: - Le point de l'onglet Profil

    func testTheProfileTabSaysWhyItHasADotAndNeverShowsANumber() {
        XCTAssertEqual(TabBarNames.key(for: .profile), "tabbar_profile_a11y")
        XCTAssertEqual(TabBarNames.key(for: .profile, profileDot: true), "tabbar_profile_requests_a11y")
        XCTAssertEqual(TabBarNames.key(for: .feed, profileDot: true), "tabbar_feed_a11y")
        XCTAssertNotEqual(L10n.string("tabbar_profile_requests_a11y"), "tabbar_profile_requests_a11y")
    }

    func testTheSharedTextsOfTheCreatorSideExistInBothLanguages() {
        let keys = [
            "place_request_section_title", "place_request_by", "place_request_by_unknown", "place_request_date",
            "place_request_detail_banner_creator", "place_request_accept", "place_request_decline",
            "place_request_accept_a11y", "place_request_decline_a11y", "place_request_accepted_toast",
            "place_request_declined_toast", "place_request_paywall_limit", "place_request_paywall_danger",
            "place_request_subscription_unknown", "place_request_a11y_pending_card",
            "place_request_a11y_pending_card_unknown"
        ]

        for key in keys {
            XCTAssertNotEqual(L10n.string(key), key, "texte manquant : \(key)")
        }
    }

    // MARK: - Les routes

    /// L'offre ouverte par « Accepter » n'est plus une destination de la pile : elle s'ouvre en plein écran (décision du PM, offre v2).
    func testTheOfferIsNoMoreARouteOfTheStack() throws {
        let routes = try String(contentsOf: URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("Miff/Views/Miff/MiffRoute.swift"), encoding: .utf8)

        XCTAssertFalse(routes.contains("Paywall"))
    }

    // MARK: - Ce que la liste reçoit

    func testTheReceivedRequestsComeBeforeThePlacesForTheCreatorOnly() {
        var list = PlaceListUi(places: [testPlace(id: "p1", name: "Maison")], isCreator: true)
        list.offersRequests = true
        list.received = [ReceivedRequest(request: testRequest(), requester: nil)]

        XCTAssertEqual(list.received.count, 1)
        XCTAssertFalse(list.atRequestLimit)
        XCTAssertEqual(list.received.first?.firstName, "")
    }

    func testTheNoticesOfACreatorGestureAreCalmAndNameNoOne() {
        XCTAssertFalse(RequestNoticeKind.accepted(placeName: "Chez Mamie").isProblem)
        XCTAssertFalse(RequestNoticeKind.declined.isProblem)
        XCTAssertTrue(RequestNoticeKind.subscriptionUnknown.isProblem)
        XCTAssertTrue(RequestNoticeKind.failed(offline: true).isProblem)
        XCTAssertEqual(RequestNoticeKind.accepted(placeName: "Chez Mamie").description, "RequestNoticeKind")
        XCTAssertFalse(RequestNoticeKind.declined.textKey.contains("refus"))
    }
}
