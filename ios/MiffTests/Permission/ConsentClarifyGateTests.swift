import XCTest
@testable import Miff

// CONS-4-I (option D du PM, ticket docs/tickets/consentement-localisation-historique.md, critères CA-6 à CA-10) : la carte calme « Une
// précision sur ce que tu partages » pour les comptes déjà en version 3, face à l'écran bloquant des versions 1 et 2 et aux comptes neufs.
// Tout est en mémoire : le système, le serveur et le compteur sont des doubles. Rien ici ne lit une position.

@MainActor
private final class ClarifyPermissions: DevicePermissions {
    var current: PermissionSnapshot
    var onLocationChange: (() -> Void)?

    init(_ location: LocationAuthorization = .always) {
        current = PermissionSnapshot(location: location, notifications: .authorized)
    }

    func snapshot() async -> PermissionSnapshot {
        current
    }

    func requestLocationAuthorization() {
        // Jamais appelé ici.
    }

    func requestNotifications() async {
        // Jamais appelé ici.
    }

    func openSettings() {
        // Jamais appelé ici.
    }
}

private final class ClarifyAsks: NotificationAskStore {
    func wasShown(uid: String) -> Bool {
        true
    }

    func markShown(uid: String) {
        // Rien : les notifications sont déjà autorisées.
    }
}

private final class ClarifyRenewals: ConsentRenewals {
    var checkResult: ConsentRenewalCheck = .nothingToRenew
    var renewResult = MiffWriteResult.done
    private(set) var renewCalls: [[String]] = []

    func check(uid: String) async -> ConsentRenewalCheck {
        checkResult
    }

    func renew(uid: String, miffIds: [String]) async -> MiffWriteResult {
        renewCalls.append(miffIds)
        return renewResult
    }
}

@MainActor
final class ConsentClarifyGateTests: XCTestCase {
    private let uid = "uid-1"
    private let miffId = testMiffId

    private struct Rig {
        let permissions: ClarifyPermissions
        let renewals: ClarifyRenewals
        let awaiting: AwaitingAgreement
        let asks: InMemoryClarifyAskStore
        let model: PermissionGateViewModel
    }

    /// Un « lancement » de l'app : une nouvelle porte, avec le MÊME compteur (il est sur le téléphone) quand on le donne.
    private func launch(
        _ check: ConsentRenewalCheck,
        location: LocationAuthorization = .always,
        asks: InMemoryClarifyAskStore = InMemoryClarifyAskStore(),
        awaiting shared: AwaitingAgreement? = nil,
        uid signedIn: String? = "uid-1"
    ) -> Rig {
        let awaiting = shared ?? AwaitingAgreement()
        let permissions = ClarifyPermissions(location)
        let renewals = ClarifyRenewals()
        renewals.checkResult = check
        let model = PermissionGateViewModel(
            dependencies: PermissionGateDependencies(
                permissions: permissions,
                asked: ClarifyAsks(),
                renewals: renewals,
                currentUid: { signedIn },
                leaver: { _, _ in .done },
                onLeft: { _ in },
                awaiting: awaiting,
                clarifyAsks: asks
            ),
            settings: PermissionGateSettings(locationBlocking: true)
        )
        return Rig(permissions: permissions, renewals: renewals, awaiting: awaiting, asks: asks, model: model)
    }

    private func version3(name: String = "Chez nous") -> ConsentRenewalCheck {
        .due([], clarify: [RenewalMiff(miffId: miffId, name: name)])
    }

    private func versions1And2() -> ConsentRenewalCheck {
        .due([RenewalMiff(miffId: miffId, name: "Chez nous")])
    }

    // MARK: - Compte neuf ou à jour : rien

    func testANewAccountOrAnUpToDateOneSeesNoCardAndNothingIsCounted() async {
        let rig = launch(.nothingToRenew)

        await rig.model.start()

        XCTAssertEqual(rig.model.state.screen, .none)
        XCTAssertNil(rig.model.state.clarify)
        XCTAssertTrue(rig.awaiting.clarifying.isEmpty)
        XCTAssertEqual(rig.asks.shownCount(uid: uid), 0)
    }

    func testWhenTheServerCannotBeAskedNoCardIsShown() async {
        let rig = launch(.unknown)

        await rig.model.start()

        XCTAssertEqual(rig.model.state.screen, .none)
        XCTAssertEqual(rig.asks.shownCount(uid: uid), 0)
    }

    func testNoSessionNoCardAndNothingIsCounted() async {
        let rig = launch(version3(), uid: nil)

        await rig.model.start()

        XCTAssertEqual(rig.model.state.screen, .none)
        XCTAssertEqual(rig.asks.shownCount(uid: uid), 0)
    }

    // MARK: - Compte en version 3 : la carte calme, rien n'est en pause

    func testAVersion3AccountSeesTheCalmCardAndNothingIsPaused() async {
        let rig = launch(version3())

        await rig.model.start()

        XCTAssertEqual(rig.model.state.screen, .clarify)
        XCTAssertNil(rig.model.state.renewal, "pas l'écran bloquant")
        XCTAssertEqual(rig.model.state.clarify?.miffs.map { $0.miffId }, [miffId])
        XCTAssertTrue(rig.awaiting.waiting.isEmpty, "aucune pastille « En attente » : rien n'est en pause (CA-7)")
        XCTAssertEqual(rig.awaiting.clarifying, [miffId])
    }

    func testLaterWritesNothingPausesNothingAndTheCardDoesNotComeBackInThisLaunch() async {
        let rig = launch(version3())
        await rig.model.start()

        rig.model.onClarifyPutOff()
        await rig.model.onResumed()

        XCTAssertEqual(rig.model.state.screen, .none)
        XCTAssertTrue(rig.renewals.renewCalls.isEmpty, "« Plus tard » n'écrit rien")
        XCTAssertTrue(rig.awaiting.waiting.isEmpty)
        XCTAssertEqual(rig.asks.shownCount(uid: uid), 1, "une seule fois par lancement, même après un retour")
    }

    func testGotItWritesTheCurrentVersionOnceForTheMiffToClarifyAndAnExtraTapIsIgnored() async {
        let rig = launch(version3())
        await rig.model.start()

        await rig.model.onClarifyAccepted()
        await rig.model.onClarifyAccepted()

        XCTAssertEqual(rig.renewals.renewCalls, [[miffId]], "le même renouvellement (R-LIEU-3), une seule fois")
        XCTAssertEqual(rig.model.state.screen, .none)
        XCTAssertNil(rig.model.state.clarify)
        XCTAssertTrue(rig.awaiting.clarifying.isEmpty)
        XCTAssertTrue(rig.awaiting.waiting.isEmpty)
    }

    func testAFailedWriteKeepsTheCardWithACalmMessageAndRetriesNothingByItself() async {
        let rig = launch(version3())
        rig.renewals.renewResult = .failed(.offline)
        await rig.model.start()

        await rig.model.onClarifyAccepted()

        XCTAssertEqual(rig.model.state.screen, .clarify)
        XCTAssertEqual(rig.model.state.clarify?.message, .offline)
        XCTAssertEqual(rig.model.state.clarify?.busy, false)
        XCTAssertEqual(rig.renewals.renewCalls.count, 1, "jamais rejoué tout seul")
        XCTAssertTrue(rig.awaiting.waiting.isEmpty, "même en échec, rien n'est en pause")

        rig.renewals.renewResult = .done
        await rig.model.onClarifyAccepted()
        XCTAssertEqual(rig.renewals.renewCalls.count, 2, "réessayer est un geste de la personne")
        XCTAssertEqual(rig.model.state.screen, .none)
    }

    func testTheCardComesAfterTheLocationScreenNeverBefore() async {
        let rig = launch(version3(), location: .notDetermined)

        await rig.model.start()
        XCTAssertEqual(rig.model.state.screen, .location, "l'écran « Toujours » d'abord")
        XCTAssertEqual(rig.asks.shownCount(uid: uid), 0, "pas montrée, donc pas comptée")

        rig.permissions.current = PermissionSnapshot(location: .always, notifications: .authorized)
        await rig.model.onResumed()
        XCTAssertEqual(rig.model.state.screen, .clarify)
        XCTAssertEqual(rig.asks.shownCount(uid: uid), 1)
    }

    // MARK: - Trois fois au plus, puis Réglages

    func testTheCardComesBackAtMostThreeTimesThenOnlySettingsOffersIt() async {
        let asks = InMemoryClarifyAskStore()
        for launchNumber in 1...3 {
            let rig = launch(version3(), asks: asks)
            await rig.model.start()
            XCTAssertEqual(rig.model.state.screen, .clarify, "lancement \(launchNumber)")
            XCTAssertEqual(asks.shownCount(uid: uid), launchNumber)
            rig.model.onClarifyPutOff()
        }

        let fourth = launch(version3(), asks: asks)
        await fourth.model.start()

        XCTAssertEqual(fourth.model.state.screen, .none, "au 4e lancement, la carte ne revient plus d'elle-même")
        XCTAssertEqual(asks.shownCount(uid: uid), ClarifyPolicy.maxShows)
        XCTAssertEqual(fourth.awaiting.clarifying, [miffId], "mais « Revoir et accepter » de Réglages reste possible")
        XCTAssertTrue(fourth.awaiting.waiting.isEmpty)
        XCTAssertTrue(fourth.renewals.renewCalls.isEmpty)
    }

    func testReviewFromSettingsShowsTheCardWhateverTheCountAndDoesNotCountIt() async {
        let asks = InMemoryClarifyAskStore()
        for _ in 1...ClarifyPolicy.maxShows {
            asks.markShown(uid: uid)
        }
        let rig = launch(version3(), asks: asks)
        await rig.model.start()
        XCTAssertEqual(rig.model.state.screen, .none)

        rig.awaiting.requestReview()

        XCTAssertEqual(rig.model.state.screen, .clarify)
        XCTAssertEqual(asks.shownCount(uid: uid), ClarifyPolicy.maxShows, "depuis les Réglages : jamais comptée")

        // « Plus tard » : retour, rien d'écrit ; « Revoir et accepter » la rouvre encore.
        rig.model.onClarifyPutOff()
        XCTAssertEqual(rig.model.state.screen, .none)
        XCTAssertTrue(rig.renewals.renewCalls.isEmpty)
        rig.awaiting.requestReview()
        XCTAssertEqual(rig.model.state.screen, .clarify)
    }

    func testTheCounterIsPerAccount() {
        let asks = InMemoryClarifyAskStore()
        asks.markShown(uid: "uid-1")
        asks.markShown(uid: "uid-1")
        asks.markShown(uid: "uid-2")

        XCTAssertEqual(asks.shownCount(uid: "uid-1"), 2)
        XCTAssertEqual(asks.shownCount(uid: "uid-2"), 1)
        asks.forget(uid: "uid-1")
        XCTAssertEqual(asks.shownCount(uid: "uid-1"), 0)
        XCTAssertEqual(asks.shownCount(uid: "uid-2"), 1)
        XCTAssertTrue(ClarifyPolicy.mayShowByItself(shownCount: 2))
        XCTAssertFalse(ClarifyPolicy.mayShowByItself(shownCount: 3))
    }

    // MARK: - Versions 1 et 2 : l'écran bloquant, inchangé ; la pause est la seule à dire « En attente »

    func testVersions1And2KeepTheBlockingScreenAndOnlyThemAreSaidToWait() async {
        let rig = launch(versions1And2())
        await rig.model.start()
        XCTAssertEqual(rig.model.state.screen, .renewal)
        XCTAssertNil(rig.model.state.clarify)

        rig.model.onRenewalPutOff()

        XCTAssertEqual(rig.awaiting.waiting, [miffId], "ce compte est réellement en pause : « En attente » est exact")
        XCTAssertTrue(rig.awaiting.clarifying.isEmpty)
        XCTAssertEqual(rig.asks.shownCount(uid: uid), 0, "la carte calme n'est jamais comptée pour l'écran bloquant")
    }

    func testBothKindsAtOnceTheBlockingScreenComesFirstThenTheCalmCardForTheOtherMiff() async {
        let other = testOtherMiffId
        let check = ConsentRenewalCheck.due(
            [RenewalMiff(miffId: miffId, name: "Une")],
            clarify: [RenewalMiff(miffId: other, name: "Deux")]
        )
        let rig = launch(check)

        await rig.model.start()
        XCTAssertEqual(rig.model.state.screen, .renewal)
        XCTAssertEqual(rig.asks.shownCount(uid: uid), 0)

        await rig.model.onRenewalAccepted()
        XCTAssertEqual(rig.renewals.renewCalls, [[miffId]], "l'accord bloquant ne touche que la Miff en pause")
        XCTAssertEqual(rig.model.state.screen, .clarify)

        await rig.model.onClarifyAccepted()
        XCTAssertEqual(rig.renewals.renewCalls, [[miffId], [other]])
        XCTAssertEqual(rig.model.state.screen, .none)
    }

    // MARK: - L'ordre des écrans, en fonction pure

    func testTheGateNeverPutsTheCalmCardBeforeAnotherScreen() {
        let always = PermissionSnapshot(location: .always, notifications: .authorized)
        let missing = PermissionSnapshot(location: .notDetermined, notifications: .authorized)
        let askable = PermissionSnapshot(location: .always, notifications: .notDetermined)

        func screen(_ snapshot: PermissionSnapshot, renewal: RenewalState, clarify: Bool) -> GateScreen {
            resolveGate(
                GateInput(
                    snapshot: snapshot,
                    notificationsHandled: false,
                    locationBlocking: true,
                    locationDeferred: false,
                    renewal: renewal,
                    renewalDeferred: false,
                    clarify: clarify
                )
            )
        }

        XCTAssertEqual(screen(always, renewal: .nothingDue, clarify: true), .clarify)
        XCTAssertEqual(screen(always, renewal: .nothingDue, clarify: false), .none)
        XCTAssertEqual(screen(askable, renewal: .nothingDue, clarify: true), .notifications)
        XCTAssertEqual(screen(missing, renewal: .nothingDue, clarify: true), .location)
        XCTAssertEqual(screen(always, renewal: .due, clarify: true), .renewal)
    }

    // MARK: - Aucune description ne cite un nom

    func testTheCalmCardNeverDescribesAName() {
        let state = ClarifyUiState(miffs: [RenewalMiff(miffId: miffId, name: "Zorglub")])

        XCTAssertFalse(String(describing: state).contains("Zorglub"))
        let check = ConsentRenewalCheck.due([], clarify: [RenewalMiff(miffId: miffId, name: "Zorglub")])
        XCTAssertFalse(String(describing: check).contains("Zorglub"))
    }
}
