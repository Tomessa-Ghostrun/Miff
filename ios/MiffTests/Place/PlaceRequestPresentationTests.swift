import XCTest
@testable import Miff

/// Ce que la liste des lieux du Profil et la navigation savent des demandes (ticket LIEU-REQ-B) : qui voit quoi, le plafond de 3, les
/// messages calmes, les écrans qui partent avec leur Miff, et le départ qui reprend les demandes.
@MainActor
final class PlaceRequestPresentationTests: XCTestCase {
    private func ui(
        creator: Bool,
        offersRequests: Bool = true,
        requests: [PlaceRequest] = []
    ) -> PlaceListUi {
        var list = PlaceListUi(places: [], isCreator: creator)
        list.offersRequests = offersRequests
        list.requests = requests
        return list
    }

    // MARK: - Le plafond de 3

    func testAMemberAtThreeRequestsIsAtTheLimitAndACreatorNever() {
        let three = [1, 2, 3].map { testRequest(slot: $0) }

        XCTAssertTrue(ui(creator: false, requests: three).atRequestLimit)
        XCTAssertFalse(ui(creator: false, requests: Array(three.prefix(2))).atRequestLimit)
        XCTAssertFalse(ui(creator: true, requests: three).atRequestLimit)
    }

    // MARK: - Ce que reçoit la liste

    func testAMemberReceivesItsOwnRequestsAndTheSentNotice() {
        var state = ProfileRequestsState()
        state.views[testMiffId] = .ready([testRequest()])

        let content = ProfileRequestsContent.make(
            state: state,
            miffId: testMiffId,
            isCreator: false,
            sentNotice: RequestNotice(queued: true),
            kind: .withdrawn
        )

        XCTAssertEqual(content.requests.count, 1)
        XCTAssertEqual(content.sentNotice, RequestNotice(queued: true))
        XCTAssertEqual(content.kind, .withdrawn)
    }

    func testACreatorNeverReceivesTheMembersRequestsNorTheSentNotice() {
        var state = ProfileRequestsState()
        state.views[testMiffId] = .ready([testRequest()])

        let content = ProfileRequestsContent.make(
            state: state, miffId: testMiffId, isCreator: true, sentNotice: RequestNotice(queued: false), kind: nil
        )

        XCTAssertTrue(content.requests.isEmpty)
        XCTAssertNil(content.sentNotice)
    }

    func testAMiffWithNothingKnownGivesAnEmptyContent() {
        let content = ProfileRequestsContent.make(
            state: ProfileRequestsState(), miffId: testMiffId, isCreator: false, sentNotice: nil, kind: nil
        )

        XCTAssertEqual(content, .none)
    }

    func testThePresentationOfThePlacesCarriesTheRequestsIntoTheListUi() {
        let miffs = [MiffSummary(id: testMiffId, name: "Famille", ownerUid: testCreatorUid)]
        var placesState = ProfilePlacesState()
        placesState.selectedMiffId = testMiffId
        placesState.view = .ready([testPlace(id: "p1", name: "Maison")])
        let presentation = ProfilePlacesPresentation.make(state: placesState, miffs: miffs, myUid: testMemberUid)
        let content = ProfileRequestsContent(
            requests: [testRequest()], sentNotice: nil, kind: nil, busyRequestId: "\(testMemberUid)_1"
        )

        let places = [testPlace(id: "p1", name: "Maison")]
        let list = presentation?.listUi(places: places, premium: true, notice: nil, requests: content)

        XCTAssertEqual(list?.offersRequests, true)
        XCTAssertEqual(list?.isCreator, false)
        XCTAssertEqual(list?.requests.count, 1)
        XCTAssertEqual(list?.busyRequestId, "\(testMemberUid)_1")
        XCTAssertNil(list?.premium, "un membre n'apprend rien de la formule")
    }

    func testTheScreenOfAMiffNeverOffersRequestsByDefault() {
        let list = PlaceListUi(places: [], isCreator: false)

        XCTAssertFalse(list.offersRequests, "sur l'écran de la Miff, un membre lit seulement")
        XCTAssertTrue(list.requests.isEmpty)
    }

    // MARK: - La navigation

    func testTheRequestScreensDependOnTheirMiffAndLeaveWithIt() {
        let form = MiffRoute.placeRequestForm(miffId: testMiffId)
        let detail = MiffRoute.placeRequestDetail(miffId: testMiffId, requestId: "\(testMemberUid)_1")

        XCTAssertTrue(form.belongs(to: testMiffId))
        XCTAssertTrue(detail.belongs(to: testMiffId))
        XCTAssertFalse(form.belongs(to: testOtherMiffId))
        XCTAssertFalse(detail.belongs(to: testOtherMiffId))
    }

    func testTheFormOfARequestEndsLikeTheFormOfAPlace() {
        XCTAssertTrue(MiffRoute.placeForm(placeId: nil, miffId: nil).isPlaceFormScreen)
        XCTAssertTrue(MiffRoute.placeRequestForm(miffId: testMiffId).isPlaceFormScreen)
        XCTAssertFalse(MiffRoute.placeRequestDetail(miffId: testMiffId, requestId: "x_1").isPlaceFormScreen)
        XCTAssertFalse(MiffRoute.placeDetail(miffId: testMiffId, placeId: "p1").isPlaceFormScreen)
    }

    func testOnlyTheRequestScreensAreRequestScreens() {
        XCTAssertTrue(MiffRoute.placeRequestForm(miffId: testMiffId).isRequestScreen)
        XCTAssertTrue(MiffRoute.placeRequestDetail(miffId: testMiffId, requestId: "x_1").isRequestScreen)
        XCTAssertFalse(MiffRoute.placeForm(placeId: nil, miffId: nil).isRequestScreen)
        XCTAssertFalse(MiffRoute.miff(testMiffId).isRequestScreen)
    }

    // MARK: - Les messages

    func testTheNoticeOfASentRequestIsRemovedWhenThePersonLeavesTheList() {
        let notices = PlaceNotices()
        notices.requestSent(queued: true)
        XCTAssertEqual(notices.requestNotice, RequestNotice(queued: true))

        notices.consume()

        XCTAssertNil(notices.requestNotice)
    }

    func testTheNoticeOfASentRequestDoesNotSurviveTheSession() {
        let notices = PlaceNotices()
        notices.requestSent(queued: false)

        notices.stop()

        XCTAssertNil(notices.requestNotice)
    }

    func testTheSharedTextsOfTheMemberSideExistInBothLanguages() {
        let keys = [
            "place_request_cta_member", "place_request_form_title", "place_request_form_submit",
            "place_request_sending", "place_request_form_hint", "place_request_sent",
            "place_request_sent_offline", "place_request_badge_pending",
            "place_request_detail_banner_member", "place_request_withdraw", "place_request_withdrawn",
            "place_request_limit_member", "place_request_limit_miff", "place_request_unavailable", "place_request_gone",
            "place_request_a11y_pending_mine", "place_list_empty_member"
        ]

        for key in keys {
            XCTAssertNotEqual(L10n.string(key), key, "texte manquant : \(key)")
        }
    }
}
