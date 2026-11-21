import XCTest
@testable import Miff

/// Un SDK de collecte qui note ses appels, dans l'ordre.
final class RecordingCollectionSwitch: CollectionSwitch {
    private(set) var log: [String] = []

    func setEnabled(_ enabled: Bool) {
        log.append(enabled ? "on" : "off")
    }

    func pause() {
        log.append("pause")
    }
}

/// Réglages > Confidentialité, l'interrupteur « statistiques et rapports de plantage » (ONB-3b) : retirer coupe la collecte TOUT DE SUITE
/// (statistiques ET rapports de plantage), même sans réseau, et le choix survit à un redémarrage ; accepter n'allume la collecte qu'une fois
/// le compte d'accord. Mêmes cas qu'Android (`PrivacyViewModelTest`). Aucune attente réelle.
@MainActor
final class PrivacyViewModelTests: XCTestCase {
    private let store = MemoryBooleanStore()
    private let analytics = RecordingCollectionSwitch()
    private let crash = RecordingCollectionSwitch()
    private let repository = FakeConsentRepository()
    private let network = FakeNetwork()
    private let sleeper = ManualSleeper()
    private let storage = MemoryWithdrawalStorage()
    private var signedIn: String? = "uid-1"
    private var announcements: [String] = []

    private lazy var privacy = PrivacySettings(store: store, analytics: analytics, crashReports: crash)
    private lazy var pending = PendingWithdrawals(storage: storage, nowMillis: { 1 })

    private lazy var withdrawals = ConsentWithdrawals(
        pending: pending,
        consents: repository,
        network: network,
        currentUid: { [unowned self] in self.signedIn },
        sleep: { [sleeper] millis in await sleeper.sleep(millis) },
        retryDelaysMillis: [10, 20, 30]
    )

    override func tearDown() async throws {
        await withdrawals.stop()
    }

    private func makeModel(
        privacy: PrivacySettings? = nil,
        pending: PendingWithdrawals? = nil,
        withdrawals: ConsentWithdrawals? = nil
    ) -> PrivacyViewModel {
        PrivacyViewModel(
            dependencies: PrivacyDependencies(
                privacy: privacy ?? self.privacy,
                withdrawals: withdrawals ?? self.withdrawals,
                pending: pending ?? self.pending,
                network: network,
                currentUid: { [unowned self] in self.signedIn },
                announce: { [unowned self] in self.announcements.append($0) }
            )
        )
    }

    /// Le compte a le consentement : la collecte tourne (comme après la lecture du compte au démarrage).
    private func startCollecting() {
        privacy.applyAccountConsent(true)
    }

    // MARK: - État de départ

    func testTheSwitchShowsWhatIsCollected() {
        startCollecting()

        let model = makeModel()

        XCTAssertTrue(model.state.analyticsEnabled)
        XCTAssertFalse(model.state.withdrawalPending)
        XCTAssertFalse(model.state.isWriting)
        XCTAssertNil(model.state.message)
    }

    func testTheSwitchFollowsTheCollectionWhenTheSessionCutsIt() {
        startCollecting()
        let model = makeModel()

        // La fin de session coupe la collecte : l'interrupteur suit sans que personne le touche.
        privacy.applyAccountConsent(false)

        XCTAssertFalse(model.state.analyticsEnabled)
    }

    // MARK: - Retirer

    func testWithdrawingCutsBothCollectionsAtOnceBeforeAnythingIsRecorded() {
        startCollecting()
        let model = makeModel()

        model.toggleAnalytics()

        // Tout de suite, sans attendre le compte : statistiques ET rapports de plantage, et le choix est gardé sur l'appareil.
        XCTAssertFalse(model.state.analyticsEnabled)
        XCTAssertFalse(privacy.analyticsEnabled)
        XCTAssertFalse(privacy.crashReportsEnabled)
        XCTAssertEqual(analytics.log.last, "off")
        XCTAssertEqual(crash.log.last, "off")
        XCTAssertEqual(store.value(forKey: PrivacySettings.analyticsKey, default: true), false)
        XCTAssertEqual(store.value(forKey: PrivacySettings.crashReportsKey, default: true), false)
        XCTAssertTrue(model.state.isWriting)
    }

    func testTheMarkIsOnTheDiskBeforeWithdrawReturnsTheHand() {
        startCollecting()
        let model = makeModel()

        model.toggleAnalytics()

        // Avant toute attente (réseau, tâche de fond) : une app tuée juste après le toucher ne reprend pas la collecte au démarrage.
        XCTAssertTrue(pending.isPending(uid: "uid-1"))
        XCTAssertFalse(storage.entries.isEmpty)
        XCTAssertEqual(repository.calls.count, 0)
    }

    func testTheNewStateIsAnnouncedToVoiceOverAfterAWithdrawalAndAfterAnAcceptance() async {
        startCollecting()
        let model = makeModel()
        let title = L10n.string("settings_privacy_analytics_title")

        model.toggleAnalytics()
        await waitUntil { !model.state.isWriting }
        XCTAssertEqual(announcements, [title + ", " + L10n.string("miff_sharing_state_off")])

        model.toggleAnalytics()
        await waitUntil { !model.state.isWriting }
        XCTAssertEqual(announcements.last, title + ", " + L10n.string("miff_sharing_state_on"))
        XCTAssertEqual(announcements.count, 2)
    }

    func testNothingIsAnnouncedWhenTheAcceptanceFails() async {
        repository.results = [.refused]
        let model = makeModel()

        model.toggleAnalytics()
        await waitUntil { !model.state.isWriting }

        XCTAssertTrue(announcements.isEmpty)
    }

    func testWithdrawingOnlineIsRecordedInTheAccountAndTheMarkGoesAway() async {
        startCollecting()
        let model = makeModel()

        model.toggleAnalytics()
        await waitUntil { !model.state.isWriting }

        XCTAssertEqual(repository.calls.count, 1)
        XCTAssertEqual(repository.calls.first?.uid, "uid-1")
        XCTAssertEqual(repository.calls.first?.enable, false)
        XCTAssertFalse(model.state.withdrawalPending)
        XCTAssertNil(model.state.message)
        XCTAssertFalse(model.state.analyticsEnabled)
    }

    func testWithdrawingWithoutNetworkStaysOffAndSaysSoCalmly() async {
        startCollecting()
        network.online = false
        let model = makeModel()

        model.toggleAnalytics()
        await waitUntil { !model.state.isWriting }

        XCTAssertFalse(model.state.analyticsEnabled)
        XCTAssertTrue(model.state.withdrawalPending)
        // Le retrait n'est pas une erreur : aucun message rouge, seulement la phrase calme « c'est coupé sur ce téléphone ».
        XCTAssertNil(model.state.message)
        XCTAssertEqual(repository.calls.count, 0)
    }

    func testAWithdrawalMadeOfflineIsRecordedByItselfWhenTheNetworkIsBack() async {
        startCollecting()
        network.online = false
        let model = makeModel()
        model.toggleAnalytics()
        await waitUntil { !model.state.isWriting }

        await sleeper.elapse(10)
        network.online = true
        await waitUntil { !model.state.withdrawalPending }

        // Sans que la personne touche rien : le message disparaît, le compte a le retrait, la collecte n'a jamais repris.
        XCTAssertFalse(model.state.withdrawalPending)
        XCTAssertEqual(repository.withdrawalCalls, 1)
        XCTAssertFalse(model.state.analyticsEnabled)
        XCTAssertEqual(analytics.log.last, "off")
        XCTAssertEqual(crash.log.last, "off")
    }

    func testAWithdrawalMadeOfflineSurvivesARestart() async {
        startCollecting()
        network.online = false
        let model = makeModel()
        model.toggleAnalytics()
        await waitUntil { !model.state.isWriting }
        await withdrawals.stop()

        // « Redémarrage » : tout est recréé à partir de ce que l'appareil a gardé ; le démarrage met la collecte en pause.
        let restartedPrivacy = PrivacySettings(store: store, analytics: analytics, crashReports: crash)
        restartedPrivacy.pauseCollection()
        let restartedPending = PendingWithdrawals(storage: storage, nowMillis: { 2 })
        let restarted = makeModel(privacy: restartedPrivacy, pending: restartedPending)

        XCTAssertFalse(restarted.state.analyticsEnabled)
        XCTAssertTrue(restarted.state.withdrawalPending)
        XCTAssertTrue(restartedPending.isPending(uid: "uid-1"))
    }

    // MARK: - Accepter

    func testAcceptingOfflineChangesNothingAndSaysSo() async {
        network.online = false
        let model = makeModel()

        model.toggleAnalytics()

        XCTAssertEqual(model.state.message, .network)
        XCTAssertFalse(model.state.analyticsEnabled)
        XCTAssertFalse(model.state.isWriting)
        XCTAssertEqual(repository.calls.count, 0)
        XCTAssertFalse(analytics.log.contains("on"))
        XCTAssertFalse(crash.log.contains("on"))
    }

    func testAcceptingIsRecordedFirstAndOnlyThenDoesTheCollectionStart() async {
        let model = makeModel()
        var collectedWhenWritten: Bool?
        repository.onSet = { [unowned self] _, _ in
            collectedWhenWritten = self.privacy.analyticsEnabled
        }

        model.toggleAnalytics()
        await waitUntil { !model.state.isWriting }

        XCTAssertEqual(collectedWhenWritten, false)
        XCTAssertEqual(repository.calls.first?.enable, true)
        XCTAssertTrue(model.state.analyticsEnabled)
        XCTAssertTrue(privacy.analyticsEnabled)
        XCTAssertTrue(privacy.crashReportsEnabled)
        XCTAssertEqual(analytics.log.last, "on")
        XCTAssertEqual(crash.log.last, "on")
        XCTAssertNil(model.state.message)
    }

    func testAcceptingWhenTheServerSaysNoStaysOffAndNeverCollects() async {
        repository.results = [.refused]
        let model = makeModel()

        model.toggleAnalytics()
        await waitUntil { !model.state.isWriting }

        XCTAssertEqual(model.state.message, .saveFailed)
        XCTAssertFalse(model.state.analyticsEnabled)
        XCTAssertFalse(analytics.log.contains("on"))
        XCTAssertFalse(crash.log.contains("on"))
    }

    func testAcceptingWhenTheServerCannotBeReachedStaysOffWithACalmNetworkMessage() async {
        repository.results = [.offline]
        let model = makeModel()

        model.toggleAnalytics()
        await waitUntil { !model.state.isWriting }

        XCTAssertEqual(model.state.message, .network)
        XCTAssertFalse(model.state.analyticsEnabled)
        XCTAssertFalse(analytics.log.contains("on"))
    }

    func testAcceptingAgainAfterAPendingWithdrawalReplacesTheMark() async {
        startCollecting()
        network.online = false
        let model = makeModel()
        model.toggleAnalytics()
        await waitUntil { !model.state.isWriting }
        XCTAssertTrue(model.state.withdrawalPending)

        network.online = true
        model.toggleAnalytics()
        await waitUntil { !model.state.isWriting }

        XCTAssertTrue(model.state.analyticsEnabled)
        XCTAssertFalse(model.state.withdrawalPending)
        XCTAssertEqual(repository.calls.last?.enable, true)
    }

    // MARK: - Garde-fous

    func testASecondTouchWhileWritingIsIgnored() async {
        let model = makeModel()

        model.toggleAnalytics()
        model.toggleAnalytics()
        await waitUntil { !model.state.isWriting }

        XCTAssertEqual(repository.calls.count, 1)
    }

    func testNothingHappensWhenNobodyIsSignedIn() {
        signedIn = nil
        let model = makeModel()

        model.toggleAnalytics()

        XCTAssertEqual(repository.calls.count, 0)
        XCTAssertFalse(model.state.isWriting)
        XCTAssertNil(model.state.message)
    }

    func testNothingIsWrittenInTheNameOfAnotherAccount() {
        startCollecting()
        let model = makeModel()

        // Un autre compte est maintenant connecté : l'écran ouvert pour le premier ne touche à rien.
        signedIn = "uid-2"
        model.toggleAnalytics()

        XCTAssertEqual(repository.calls.count, 0)
        XCTAssertTrue(privacy.analyticsEnabled)
    }

    // MARK: - « Enregistrer mon choix »

    func testSaveMyChoiceTriesRightNowAndTheMessageGoesAway() async {
        startCollecting()
        repository.results = [.refused, .done]
        let model = makeModel()
        model.toggleAnalytics()
        await waitUntil { !model.state.isWriting }
        XCTAssertTrue(model.state.withdrawalPending)

        model.retryWithdrawal()
        await waitUntil { !model.state.isWriting }

        XCTAssertFalse(model.state.withdrawalPending)
        XCTAssertNil(model.state.message)
        XCTAssertEqual(repository.withdrawalCalls, 2)
        XCTAssertFalse(model.state.analyticsEnabled)
    }

    func testSaveMyChoiceThatFailsAgainSaysSoAndKeepsTheCollectionOff() async {
        startCollecting()
        repository.results = [.refused]
        let model = makeModel()
        model.toggleAnalytics()
        await waitUntil { !model.state.isWriting }

        model.retryWithdrawal()
        await waitUntil { !model.state.isWriting }

        XCTAssertTrue(model.state.withdrawalPending)
        XCTAssertEqual(model.state.message, .saveFailed)
        XCTAssertFalse(model.state.analyticsEnabled)
    }

    func testSaveMyChoiceDoesNothingWithoutAPendingWithdrawal() {
        startCollecting()
        let model = makeModel()

        model.retryWithdrawal()

        XCTAssertEqual(repository.calls.count, 0)
        XCTAssertFalse(model.state.isWriting)
    }
}
