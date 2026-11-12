// Scénarios de test longs par nature (un cas par méthode) : les limites de longueur ne s'appliquent pas ici.
// swiftlint:disable file_length type_body_length
import XCTest
@testable import Miff

/// Les demandes reçues par le CRÉATEUR sous la liste des lieux du Profil (ticket LIEU-REQ-C, miroir d'Android
/// `ProfileRequestsCreatorViewModelTest`, critères C2 à C11) : UN écouteur sur UNE Miff, le point de l'onglet, qui a demandé, accepter et
/// refuser, le nettoyage des demandes périmées ou d'un ex-membre.
@MainActor
final class ProfileRequestsCreatorViewModelTests: XCTestCase {
    private let repository = FakePlaceRequestRepository()
    private let network = FakeNetwork()
    private let now = TestBox(10_000_000)
    private let lea = RequesterCard.of(uid: testMemberUid, firstName: "Léa", lastName: "Martin", avatarJpeg: nil)

    private func viewModel() -> ProfileRequestsViewModel {
        let clock = now
        return ProfileRequestsViewModel(
            repository: repository,
            network: network,
            myUid: { testCreatorUid },
            nowMillis: { clock.value }
        )
    }

    /// Le créateur écoute `testMiffId` : l'écouteur est attaché et a donné `list`.
    private func listening(
        _ model: ProfileRequestsViewModel,
        list: [PlaceRequest]
    ) async -> Task<Void, Never> {
        model.onCardShown(miffId: testMiffId, creator: true)
        let task = Task { await model.watch(miffId: testMiffId) }
        await waitUntil { !self.repository.observedMiffs.isEmpty }
        repository.emit(.data(requests: list, fromCache: false))
        await waitUntil { model.state.views[testMiffId]?.requests.count == list.count }
        return task
    }

    // MARK: - Quelle Miff est suivie

    func testTheTrackedMiffIsTheCardShownWhenTheyCreatedItElseTheActiveOne() {
        let model = viewModel()
        model.onActiveMiff(miffId: testOtherMiffId, creator: true)
        XCTAssertEqual(model.tracked, testOtherMiffId, "tant qu'aucune carte n'est montrée : la Miff active du Feed")

        model.onCardShown(miffId: testMiffId, creator: true)
        XCTAssertEqual(model.tracked, testMiffId, "la carte regardée, si la personne l'a créée")

        model.onCardShown(miffId: testThirdMiffId, creator: false)
        // Une Miff dont on est membre n'est pas suivie : retour à la Miff active.
        XCTAssertEqual(model.tracked, testOtherMiffId)
    }

    func testAMemberOnlyIsNeverTrackedSoNoListenerExistsForIt() {
        let model = viewModel()
        model.onActiveMiff(miffId: testMiffId, creator: false)
        model.onCardShown(miffId: testMiffId, creator: false)

        XCTAssertNil(model.tracked)
    }

    func testLeavingTheTrackedMiffStopsTrackingIt() {
        let model = viewModel()
        model.onActiveMiff(miffId: testMiffId, creator: true)

        model.retainOnly(miffIds: [testOtherMiffId])

        XCTAssertNil(model.tracked)
    }

    // MARK: - L'écouteur, le point, qui a demandé

    func testAListenerAnswerShowsTheRequestsOldestFirstAndLightsTheDot() async {
        let first = testRequest(slot: 1, createdAtMillis: now.value - 2_000)
        let second = testRequest(slot: 2, createdAtMillis: now.value - 1_000)
        let model = viewModel()

        let task = await listening(model, list: [second, first])

        XCTAssertEqual(model.state.views[testMiffId]?.requests.map { $0.id }, [first.id, second.id])
        XCTAssertTrue(model.state.profileDot, "critère C1 : un point sur l'onglet Profil")
        XCTAssertEqual(repository.readMineCalls, [], "le créateur ne lit pas ses demandes : l'écouteur les donne")
        task.cancel()
    }

    func testTheDotGoesOutWhenTheListWithItsRequestsIsSeenAndComesBackForANewOne() async {
        let first = testRequest(slot: 1)
        let model = viewModel()
        let task = await listening(model, list: [first])
        XCTAssertTrue(model.state.profileDot)

        model.onListSeen(miffId: testMiffId)
        XCTAssertFalse(model.state.profileDot)

        repository.emit(.data(requests: [first, testRequest(slot: 2)], fromCache: false))
        await waitUntil { model.state.profileDot }
        XCTAssertTrue(model.state.profileDot, "une demande nouvelle rallume le point")
        task.cancel()
    }

    func testTheCardOfTheRequesterIsReadOncePerPersonAndShownWithTheFirstName() async {
        repository.requesterResults[testMemberUid] = .known(lea)
        let model = viewModel()

        let task = await listening(model, list: [testRequest(slot: 1), testRequest(slot: 2)])
        await waitUntil { model.state.requesters.count == 1 }
        repository.emit(.data(requests: [testRequest(slot: 1), testRequest(slot: 2)], fromCache: false))

        XCTAssertEqual(repository.requesterCalls, [testMemberUid], "une fois par personne et par session")
        XCTAssertEqual(model.state.requester(of: testRequest()), .known(lea))
        task.cancel()
    }

    func testAnUnreadableCardShowsTheRequestWithoutANameAndIsTriedAgainAfterAMinuteOnly() async {
        let model = viewModel()
        let task = await listening(model, list: [testRequest()])
        await waitUntil { model.state.requesters.count == 1 }

        XCTAssertEqual(model.state.requester(of: testRequest()), .unknown)
        XCTAssertEqual(model.state.views[testMiffId]?.requests.count, 1, "jamais cachée sur un doute")
        repository.emit(.data(requests: [testRequest()], fromCache: false))
        await Task.yield()
        XCTAssertEqual(repository.requesterCalls.count, 1, "pas avant une minute")

        now.value += ProfileRequestsViewModel.unknownRetryMillis
        repository.emit(.data(requests: [testRequest()], fromCache: false))
        await waitUntil { self.repository.requesterCalls.count == 2 }
        XCTAssertEqual(repository.requesterCalls.count, 2)
        task.cancel()
    }

    func testAccessLostForgetsTheRequestsOfThatMiff() async {
        let model = viewModel()
        let task = await listening(model, list: [testRequest()])

        repository.emit(.accessLost)
        await waitUntil { model.state.views[testMiffId] == .loading }

        XCTAssertEqual(model.state.views[testMiffId], .loading)
        task.cancel()
    }

    func testWhenTheListenerStopsTheRequestsStayButAgeLikeAReading() async {
        let model = viewModel()
        let task = await listening(model, list: [testRequest()])

        task.cancel()
        await task.value

        XCTAssertEqual(model.state.views[testMiffId]?.requests.count, 1)
        model.onRequestsMayChange()
        XCTAssertEqual(repository.observedMiffs, [testMiffId])
    }

    func testOpeningARequestWithNothingKnownReadsAllOfTheMiffOnceForTheCreator() async {
        repository.readAllResult = .loaded([testRequest()])
        repository.requesterResults[testMemberUid] = .known(lea)
        let model = viewModel()

        model.onRequestOpened(miffId: testMiffId, creator: true)
        await waitUntil { model.state.request(miffId: testMiffId, requestId: "\(testMemberUid)_1") != nil }
        model.onRequestOpened(miffId: testMiffId, creator: true)

        XCTAssertEqual(repository.readAllCalls, [testMiffId])
        XCTAssertEqual(repository.readMineCalls, [])
    }

    // MARK: - Accepter (critères C3, C5 à C8)

    func testAcceptingRemovesTheRequestAtOnceSaysSoAndRereadsThePlacesOfThatMiff() async {
        let request = testRequest()
        let model = viewModel()
        let reread = TestBox<[String]>([])
        model.onAccepted = { reread.value.append($0) }
        let task = await listening(model, list: [request, testRequest(slot: 2)])

        model.onAccept(request)
        await waitUntil { model.notice != nil }

        XCTAssertEqual(model.notice, .accepted(placeName: "Club de judo"))
        XCTAssertTrue(model.notice?.text.contains("Club de judo") == true, "le texte nomme le lieu accepté")
        XCTAssertEqual(repository.accepted.map { $0.id }, [request.id])
        XCTAssertEqual(repository.acceptedCounts, [2], "le compteur est posé au nombre réel que le créateur voit")
        XCTAssertEqual(model.state.views[testMiffId]?.requests.map { $0.slot }, [2])
        XCTAssertEqual(reread.value, [testMiffId])
        XCTAssertNil(model.paywall)
        XCTAssertNil(model.state.busyRequestId)
        task.cancel()
    }

    func testTheOfferOpensAtTheLimitAndTheRequestStaysPending() async {
        repository.acceptResult = .needsPremium(.limit)
        let request = testRequest()
        let model = viewModel()
        let task = await listening(model, list: [request])

        model.onAccept(request)
        await waitUntil { model.paywall != nil }

        XCTAssertEqual(model.paywall, .limit)
        XCTAssertEqual(model.state.views[testMiffId]?.requests.count, 1, "la demande reste en attente")
        XCTAssertNil(model.notice)
        XCTAssertNil(model.state.busyRequestId)
        model.consumePaywall()
        XCTAssertNil(model.paywall)
        task.cancel()
    }

    func testADangerZoneOffersTheDangerPaywall() async {
        repository.acceptResult = .needsPremium(.danger)
        let request = testRequest(type: .risk)
        let model = viewModel()
        let task = await listening(model, list: [request])

        model.onAccept(request)
        await waitUntil { model.paywall != nil }

        XCTAssertEqual(model.paywall, .danger)
        task.cancel()
    }

    func testAnUnknownPlanSaysSoCalmlyAndOpensNoOffer() async {
        repository.acceptResult = .subscriptionUnknown
        let request = testRequest()
        let model = viewModel()
        let task = await listening(model, list: [request])

        model.onAccept(request)
        await waitUntil { model.notice != nil }

        XCTAssertEqual(model.notice, .subscriptionUnknown)
        XCTAssertTrue(model.notice?.isProblem == true)
        XCTAssertNil(model.paywall, "critère C8 : jamais de paywall sur un doute")
        task.cancel()
    }

    func testARequestAlreadyHandledElsewhereLeavesTheListWithTheNeutralSentence() async {
        repository.acceptResult = .gone
        let request = testRequest()
        let model = viewModel()
        let task = await listening(model, list: [request])

        model.onAccept(request)
        await waitUntil { model.notice != nil }

        XCTAssertEqual(model.notice, .gone)
        XCTAssertEqual(model.state.views[testMiffId]?.requests.count, 0)
        task.cancel()
    }

    func testAFailedAcceptanceKeepsTheRequestAndSaysTheCalmFailure() async {
        repository.acceptResult = .failed(.offline)
        let request = testRequest()
        let model = viewModel()
        let task = await listening(model, list: [request])

        model.onAccept(request)
        await waitUntil { model.notice != nil }

        XCTAssertEqual(model.notice, .failed(offline: true))
        XCTAssertEqual(model.state.views[testMiffId]?.requests.count, 1)
        task.cancel()
    }

    func testWithoutANetworkNeitherAnswerTriesAnything() async {
        let request = testRequest()
        let model = viewModel()
        let task = await listening(model, list: [request])
        network.online = false

        model.onAccept(request)
        model.onDecline(request)

        XCTAssertEqual(model.notice, .failed(offline: true))
        XCTAssertTrue(repository.accepted.isEmpty && repository.declined.isEmpty)
        XCTAssertNil(model.state.busyRequestId)
        task.cancel()
    }

    func testOneAnswerAtATime() async {
        let gate = TestGate()
        repository.gate = gate
        let first = testRequest(slot: 1)
        let model = viewModel()
        let task = await listening(model, list: [first, testRequest(slot: 2)])

        model.onAccept(first)
        model.onDecline(testRequest(slot: 2))
        await waitUntil { gate.arrivals == 1 }

        XCTAssertEqual(model.state.busyRequestId, first.id)
        XCTAssertTrue(repository.declined.isEmpty)
        gate.open()
        await waitUntil { model.notice != nil }
        task.cancel()
    }

    // MARK: - Refuser (critère C4)

    func testDecliningAlwaysWorksRemovesTheRequestAndSaysSoWithoutNamingAnyone() async {
        let request = testRequest()
        let model = viewModel()
        let task = await listening(model, list: [request, testRequest(slot: 3)])

        model.onDecline(request)
        await waitUntil { model.notice != nil }

        XCTAssertEqual(model.notice, .declined)
        XCTAssertNotEqual(model.notice?.text, RequestNoticeKind.declined.textKey, "le texte est traduit, pas la clé")
        XCTAssertEqual(repository.declined.map { $0.id }, [request.id])
        XCTAssertEqual(repository.declinedCounts, [2])
        XCTAssertEqual(model.state.views[testMiffId]?.requests.map { $0.slot }, [3])
        XCTAssertNil(model.paywall)
        task.cancel()
    }

    func testAFailedDeclineKeepsTheRequest() async {
        repository.declineResult = .failed(.refused)
        let request = testRequest()
        let model = viewModel()
        let task = await listening(model, list: [request])

        model.onDecline(request)
        await waitUntil { model.notice != nil }

        XCTAssertEqual(model.notice, .failed(offline: false))
        XCTAssertEqual(model.state.views[testMiffId]?.requests.count, 1)
        task.cancel()
    }

    // MARK: - Nettoyer (critères C10 et L2)

    func testARequestOfSomeoneWhoLeftTheMiffIsHiddenThenDeleted() async {
        repository.requesterResults[testMemberUid] = .notMember
        let stays = testRequest(slot: 1, uid: "uidstays01")
        repository.requesterResults["uidstays01"] = .known(lea)
        let gone = testRequest(slot: 1)
        let model = viewModel()

        let task = await listening(model, list: [gone, stays])
        await waitUntil { !self.repository.purged.isEmpty }

        XCTAssertEqual(repository.purged, [[gone.id]])
        XCTAssertEqual(repository.purgedRemaining, [1], "le compteur est posé au nombre qui reste")
        XCTAssertEqual(model.state.views[testMiffId]?.requests.map { $0.id }, [stays.id])
        task.cancel()
    }

    func testAnExpiredRequestIsDeletedOnlyWhenTheServerDatedRecentOnesMuchLater() async {
        let day = 24 * 60 * 60 * 1000
        let old = testRequest(slot: 1, createdAtMillis: now.value - 40 * day)
        let recent = testRequest(slot: 2, createdAtMillis: now.value - day)
        repository.requesterResults[testMemberUid] = .known(lea)
        let model = viewModel()

        let task = await listening(model, list: [old, recent])
        await waitUntil { !self.repository.purged.isEmpty }

        XCTAssertEqual(repository.purged, [[old.id]])
        // Critère C10 : plus de 30 jours, plus affichée.
        XCTAssertEqual(model.state.views[testMiffId]?.requests.map { $0.id }, [recent.id])
        task.cancel()
    }

    func testAPhoneWhoseClockRunsAheadHidesEverythingButDeletesNothing() async {
        let day = 24 * 60 * 60 * 1000
        let only = testRequest(slot: 1, createdAtMillis: 5_000_000)
        now.value = 5_000_000 + 40 * day
        repository.requesterResults[testMemberUid] = .known(lea)
        let model = viewModel()

        let task = await listening(model, list: [])
        repository.emit(.data(requests: [only], fromCache: false))
        await waitUntil { model.state.views[testMiffId] != nil }
        try? await Task.sleep(nanoseconds: 100_000_000)

        XCTAssertEqual(model.state.views[testMiffId]?.requests.count, 0, "masquée (réversible)")
        XCTAssertTrue(repository.purged.isEmpty, "mais jamais supprimée sur la seule foi de l'horloge du téléphone")
        task.cancel()
    }

    func testTheReceivedRequestsOfTheContentCarryWhoAsked() async {
        repository.requesterResults[testMemberUid] = .known(lea)
        let model = viewModel()
        let task = await listening(model, list: [testRequest()])
        await waitUntil { model.state.requesters.count == 1 }

        let content = ProfileRequestsContent.make(
            state: model.state, miffId: testMiffId, isCreator: true, sentNotice: nil, kind: nil
        )

        XCTAssertEqual(content.received.count, 1)
        XCTAssertEqual(content.received.first?.firstName, "Léa")
        XCTAssertTrue(content.requests.isEmpty, "les demandes reçues ne sont pas celles d'un membre")
        task.cancel()
    }
}
