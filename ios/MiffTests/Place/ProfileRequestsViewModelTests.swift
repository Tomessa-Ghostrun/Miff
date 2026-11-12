import XCTest
@testable import Miff

/// Les demandes sous la liste des lieux du Profil, côté MEMBRE (ticket LIEU-REQ-B, miroir d'Android `ProfileRequestsViewModelTest`, critères
/// M4 à M6) : une lecture de ses propres demandes à l'apparition d'une carte, jamais plus d'une par Miff et par 5 minutes, et la reprise d'une
/// demande.
@MainActor
final class ProfileRequestsViewModelTests: XCTestCase {
    private let repository = FakePlaceRequestRepository()
    private let network = FakeNetwork()
    private let now = TestBox(1_000_000)

    private func viewModel() -> ProfileRequestsViewModel {
        let clock = now
        return ProfileRequestsViewModel(
            repository: repository,
            network: network,
            myUid: { testMemberUid },
            nowMillis: { clock.value }
        )
    }

    // MARK: - Lire

    func testNothingIsDrawnBeforeTheFirstReadingEnds() async {
        let gate = TestGate()
        repository.gate = gate
        let model = viewModel()

        model.onCardShown(miffId: testMiffId, creator: false)

        XCTAssertEqual(model.state.selectedMiffId, testMiffId)
        XCTAssertEqual(model.state.selected, .loading, "ni « aucune demande » inventé ni liste vide")
        gate.open()
        await waitUntil { model.state.selected != .loading }
        XCTAssertEqual(model.state.selected, .ready([]))
    }

    func testAMemberReadsOnlyItsOwnRequestsAndTheListShowsTheNewestFirst() async {
        let old = testRequest(slot: 1, createdAtMillis: now.value - 5_000)
        let recent = testRequest(slot: 2, createdAtMillis: now.value - 1_000)
        repository.mineResult = .loaded([old, recent])
        let model = viewModel()

        model.onCardShown(miffId: testMiffId, creator: false)
        await waitUntil { model.state.selected != .loading }

        XCTAssertEqual(model.state.selected.requests.map { $0.id }, [recent.id, old.id])
        XCTAssertEqual(repository.readMineCalls, [testMiffId])
    }

    func testTheSameMiffIsReadOncePerFiveMinutes() async {
        let model = viewModel()
        model.onCardShown(miffId: testMiffId, creator: false)
        await waitUntil { model.state.selected != .loading }

        model.onCardShown(miffId: testMiffId, creator: false)
        model.onProfileAppeared()
        now.value += PlaceRequestBook.ttlMillis - 1
        model.onCardShown(miffId: testMiffId, creator: false)

        XCTAssertEqual(repository.readMineCalls.count, 1, "revenir sur la carte ou sur le Profil ne coûte rien")

        now.value += 1
        model.onCardShown(miffId: testMiffId, creator: false)
        await waitUntil { self.repository.readMineCalls.count == 2 }
        XCTAssertEqual(repository.readMineCalls.count, 2)
    }

    func testTwoCardsAreTwoReadingsAndANewReadingDoesNotStartWhileOneIsUnderWay() async {
        let gate = TestGate()
        repository.gate = gate
        let model = viewModel()

        model.onCardShown(miffId: testMiffId, creator: false)
        model.onCardShown(miffId: testMiffId, creator: false)
        model.onCardShown(miffId: testOtherMiffId, creator: false)
        await waitUntil { gate.arrivals == 2 }
        gate.open()

        XCTAssertEqual(Set(repository.readMineCalls), [testMiffId, testOtherMiffId])
        XCTAssertEqual(repository.readMineCalls.count, 2)
        XCTAssertEqual(model.state.selectedMiffId, testOtherMiffId)
    }

    func testAFailedReadingSaysNothingAlarmingAndWaitsBeforeTryingAgain() async {
        repository.mineResult = .failed(.offline)
        let model = viewModel()

        model.onCardShown(miffId: testMiffId, creator: false)
        await waitUntil { model.state.selected != .loading }

        XCTAssertEqual(model.state.selected, .unavailable)
        XCTAssertNil(model.notice, "aucun message : la liste des lieux se dessine sans les demandes")
        model.onCardShown(miffId: testMiffId, creator: false)
        XCTAssertEqual(repository.readMineCalls.count, 1, "jamais de boucle après un échec")
    }

    func testRetryReadsAgainAtOnce() async {
        repository.mineResult = .failed(.offline)
        let model = viewModel()
        model.onCardShown(miffId: testMiffId, creator: false)
        await waitUntil { model.state.selected == .unavailable }
        repository.mineResult = .loaded([testRequest()])

        model.onRetry()
        await waitUntil { model.state.selected != .unavailable }

        XCTAssertEqual(model.state.selected.requests.count, 1)
        XCTAssertEqual(repository.readMineCalls.count, 2)
    }

    func testAnAnswerThatArrivesAfterLeavingTheMiffIsThrownAway() async {
        let gate = TestGate()
        repository.gate = gate
        repository.mineResult = .loaded([testRequest()])
        let model = viewModel()
        model.onCardShown(miffId: testMiffId, creator: false)
        await waitUntil { gate.arrivals == 1 }

        model.retainOnly(miffIds: [testOtherMiffId])
        gate.open()
        await Task.yield()
        try? await Task.sleep(nanoseconds: 50_000_000)

        XCTAssertNil(model.state.views[testMiffId], "rien d'une Miff quittée ne revient")
    }

    func testRetainOnlyForgetsTheRequestsOfAMiffThePersonLeft() async {
        let model = viewModel()
        model.onCardShown(miffId: testMiffId, creator: false)
        await waitUntil { model.state.selected != .loading }
        model.onCardShown(miffId: testOtherMiffId, creator: false)
        await waitUntil { model.state.views[testOtherMiffId] != nil && model.state.views[testOtherMiffId] != .loading }

        model.retainOnly(miffIds: [testOtherMiffId])

        XCTAssertNil(model.state.views[testMiffId])
        XCTAssertNotNil(model.state.views[testOtherMiffId])
        model.retainOnly(miffIds: [])
        XCTAssertNil(model.state.selectedMiffId)
    }

    func testAnExpiredRequestIsNotShownWhenTheClockMoves() async {
        let day = 24 * 60 * 60 * 1000
        repository.mineResult = .loaded([testRequest(createdAtMillis: now.value)])
        let model = viewModel()
        model.onCardShown(miffId: testMiffId, creator: false)
        await waitUntil { model.state.selected != .loading }
        XCTAssertEqual(model.state.selected.requests.count, 1)

        now.value += 31 * day
        model.onProfileAppeared()

        XCTAssertEqual(model.state.selected.requests.count, 0, "critère C10 : plus de 30 jours, plus affichée")
    }

    func testOpeningAnUnknownRequestReadsTheMiffOnce() async {
        repository.mineResult = .loaded([testRequest()])
        let model = viewModel()

        model.onRequestOpened(miffId: testMiffId, creator: false)
        await waitUntil { model.state.request(miffId: testMiffId, requestId: "\(testMemberUid)_1") != nil }
        model.onRequestOpened(miffId: testMiffId, creator: false)

        XCTAssertEqual(repository.readMineCalls.count, 1)
    }

    func testAFormOrADetailMakesEveryListReadAgainWhenItComesBack() async {
        let model = viewModel()
        model.onCardShown(miffId: testMiffId, creator: false)
        await waitUntil { model.state.selected != .loading }

        model.onRequestsMayChange()
        model.onProfileAppeared()
        await waitUntil { self.repository.readMineCalls.count == 2 }

        XCTAssertEqual(repository.readMineCalls.count, 2)
    }

    // MARK: - Reprendre sa demande (critère M6)

    func testWithdrawingRemovesTheRequestAtOnceAndSaysSoCalmly() async {
        let request = testRequest()
        repository.mineResult = .loaded([request, testRequest(slot: 2)])
        let model = viewModel()
        model.onCardShown(miffId: testMiffId, creator: false)
        await waitUntil { model.state.selected != .loading }

        model.onWithdraw(request)
        await waitUntil { model.notice != nil }

        XCTAssertEqual(model.notice, .withdrawn)
        XCTAssertEqual(model.state.selected.requests.map { $0.slot }, [2])
        XCTAssertEqual(repository.withdrawn.map { $0.id }, [request.id])
        XCTAssertNil(model.state.busyRequestId)
    }

    func testWhileOneAnswerIsUnderWayAnotherDoesNothing() async {
        let gate = TestGate()
        repository.mineResult = .loaded([testRequest(), testRequest(slot: 2)])
        let model = viewModel()
        model.onCardShown(miffId: testMiffId, creator: false)
        await waitUntil { model.state.selected != .loading }
        repository.gate = gate

        model.onWithdraw(testRequest())
        model.onWithdraw(testRequest(slot: 2))
        await waitUntil { gate.arrivals == 1 }

        XCTAssertEqual(model.state.busyRequestId, "\(testMemberUid)_1")
        gate.open()
        await waitUntil { model.notice != nil }
        XCTAssertEqual(repository.withdrawn.count, 1)
    }

    func testWithdrawingOfflineSaysSoWithoutTryingAnything() async {
        network.online = false
        let model = viewModel()

        model.onWithdraw(testRequest())

        XCTAssertEqual(model.notice, .failed(offline: true))
        XCTAssertTrue(repository.withdrawn.isEmpty)
        XCTAssertNil(model.state.busyRequestId)
    }

    func testAFailedWithdrawalKeepsTheRequestAndSaysTheGenericSentence() async {
        let request = testRequest()
        repository.mineResult = .loaded([request])
        repository.withdrawResult = .failed(.refused)
        let model = viewModel()
        model.onCardShown(miffId: testMiffId, creator: false)
        await waitUntil { model.state.selected != .loading }

        model.onWithdraw(request)
        await waitUntil { model.notice != nil }

        XCTAssertEqual(model.notice, .failed(offline: false))
        XCTAssertEqual(model.state.selected.requests.count, 1)
        XCTAssertNil(model.state.busyRequestId)
    }

    func testTheNoticeGoesWhenThePersonLeavesTheList() async {
        let model = viewModel()
        model.onWithdraw(testRequest())
        await waitUntil { model.notice != nil }

        model.consumeNotice()

        XCTAssertNil(model.notice)
    }

    func testTheTextsOfTheCalmMessagesAreTheSharedKeys() {
        XCTAssertEqual(RequestNoticeKind.withdrawn.textKey, "place_request_withdrawn")
        XCTAssertEqual(RequestNoticeKind.gone.textKey, "place_request_gone")
        XCTAssertEqual(RequestNoticeKind.failed(offline: true).textKey, "miff_message_offline")
        XCTAssertEqual(RequestNoticeKind.failed(offline: false).textKey, "miff_message_generic")
    }
}
