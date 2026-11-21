import XCTest
@testable import Miff

/// Le dépôt du choix dans le compte, scripté : une réponse par appel (la dernière se répète), chaque appel noté.
final class FakeConsentRepository: AnalyticsConsentRepository {
    var results: [ProfileWriteResult] = [.done]
    private(set) var calls: [(uid: String, enable: Bool)] = []
    /// Tourne à chaque appel, AVANT la réponse : pour regarder ce qui était collecté à cet instant précis.
    var onSet: (_ uid: String, _ enable: Bool) -> Void = { _, _ in }

    func set(uid: String, enable: Bool) async -> ProfileWriteResult {
        calls.append((uid, enable))
        onSet(uid, enable)
        if results.count > 1 {
            return results.removeFirst()
        }
        return results.first ?? .done
    }

    var withdrawalCalls: Int {
        calls.filter { !$0.enable }.count
    }
}

/// Ce qui arrive à un retrait du consentement « statistiques et rapports de plantage » (ONB-3b) : marqué sur l'appareil d'abord,
/// enregistré dans le compte dès que possible, jamais appliqué à un autre compte, jamais en boucle quand le serveur dit non. Mêmes cas
/// qu'Android (`ConsentWithdrawalsTest`). Aucune attente réelle : les pauses sont commandées par le test.
@MainActor
final class ConsentWithdrawalsTests: XCTestCase {
    private let storage = MemoryWithdrawalStorage()
    private let repository = FakeConsentRepository()
    private let network = FakeNetwork()
    private let sleeper = ManualSleeper()
    private var signedIn: String? = "uid-1"

    private lazy var pending = PendingWithdrawals(storage: storage, nowMillis: { 1 })

    private lazy var withdrawals = ConsentWithdrawals(
        pending: pending,
        consents: repository,
        network: network,
        currentUid: { [unowned self] in self.signedIn },
        sleep: { [sleeper] millis in await sleeper.sleep(millis) },
        retryDelaysMillis: [10, 20, 30]
    )

    /// Une tâche de fond encore en attente d'une pause ne survit pas au test.
    override func tearDown() async throws {
        await withdrawals.stop()
    }

    func testThePausesBetweenTwoTriesAreTheOnesOfAndroid() {
        XCTAssertEqual(ConsentWithdrawals.defaultDelaysMillis, [5_000, 15_000, 60_000, 300_000])
    }

    func testAWithdrawalIsRecordedAtOnceWhenTheAccountIsReachable() async {
        let result = await withdrawals.withdraw(uid: "uid-1")

        XCTAssertEqual(result, .done)
        XCTAssertFalse(pending.isPending(uid: "uid-1"))
        XCTAssertEqual(repository.calls.count, 1)
        XCTAssertEqual(repository.calls.first?.uid, "uid-1")
        XCTAssertEqual(repository.calls.first?.enable, false)
    }

    func testWithoutANetworkTheWithdrawalIsMarkedAndNoDoomedRequestIsSent() async {
        network.online = false

        let result = await withdrawals.withdraw(uid: "uid-1")

        XCTAssertEqual(result, .offline)
        XCTAssertTrue(pending.isPending(uid: "uid-1"))
        XCTAssertEqual(repository.calls.count, 0)
        // La marque est déjà sur le disque : elle survit à une app tuée juste après le toucher.
        XCTAssertFalse(storage.entries.isEmpty)
    }

    func testItIsRecordedInTheBackgroundAsSoonAsTheNetworkIsBack() async {
        network.online = false
        _ = await withdrawals.withdraw(uid: "uid-1")

        await sleeper.elapse(10)
        network.online = true
        await waitUntil { !pending.isPending(uid: "uid-1") }

        XCTAssertFalse(pending.isPending(uid: "uid-1"))
        XCTAssertEqual(repository.withdrawalCalls, 1)
        XCTAssertTrue(storage.entries.isEmpty)
    }

    func testAnAnswerOfflineFromTheServerKeepsTryingWithGrowingPauses() async {
        repository.results = [.offline, .offline, .done]

        let first = await withdrawals.withdraw(uid: "uid-1")
        XCTAssertEqual(first, .offline)
        XCTAssertTrue(pending.isPending(uid: "uid-1"))

        await sleeper.elapse(10)
        await sleeper.elapse(20)
        await waitUntil { !pending.isPending(uid: "uid-1") }

        XCTAssertFalse(pending.isPending(uid: "uid-1"))
        XCTAssertEqual(repository.calls.count, 3)
        XCTAssertEqual(sleeper.requested, [10, 20])
    }

    func testTheServerSayingNoStopsEverythingAndNeverLoops() async {
        repository.results = [.refused]

        let result = await withdrawals.withdraw(uid: "uid-1")
        await sleeper.settle()

        XCTAssertEqual(result, .refused)
        // La collecte reste éteinte : la marque reste, aucune pause demandée, aucune boucle.
        XCTAssertTrue(pending.isPending(uid: "uid-1"))
        XCTAssertEqual(repository.calls.count, 1)
        XCTAssertTrue(sleeper.requested.isEmpty)
        // Même quand le compte est relu : pas d'essai automatique avant le démarrage suivant.
        withdrawals.resume(uid: "uid-1")
        await sleeper.settle()
        XCTAssertEqual(repository.calls.count, 1)
    }

    func testRetryNowAfterARefusalTriesOnceMore() async {
        repository.results = [.failed, .done]
        _ = await withdrawals.withdraw(uid: "uid-1")
        XCTAssertTrue(pending.isPending(uid: "uid-1"))

        let result = await withdrawals.retryNow(uid: "uid-1")

        XCTAssertEqual(result, .done)
        XCTAssertFalse(pending.isPending(uid: "uid-1"))
        XCTAssertEqual(repository.calls.count, 2)
    }

    func testRetryNowWithoutAMarkHasNothingToRecord() async {
        let result = await withdrawals.retryNow(uid: "uid-1")

        XCTAssertEqual(result, .done)
        XCTAssertEqual(repository.calls.count, 0)
    }

    func testAcceptingAgainCancelsTheMarkOnlyOnceTheServerTookIt() async {
        network.online = false
        _ = await withdrawals.withdraw(uid: "uid-1")
        repository.results = [.offline, .done]

        let refused = await withdrawals.accept(uid: "uid-1")
        XCTAssertEqual(refused, .offline)
        XCTAssertTrue(pending.isPending(uid: "uid-1"))

        let accepted = await withdrawals.accept(uid: "uid-1")
        XCTAssertEqual(accepted, .done)
        XCTAssertFalse(pending.isPending(uid: "uid-1"))
        XCTAssertEqual(repository.calls.map { $0.enable }, [true, true])
    }

    func testAMarkOfAnotherAccountIsNeverRecordedForTheOneSignedIn() async {
        pending.mark(uid: "uid-1")
        signedIn = "uid-2"

        withdrawals.resume(uid: "uid-1")
        await sleeper.settle()

        // Le compte connecté n'est pas celui de la marque : rien n'est écrit, la marque attend son compte.
        XCTAssertEqual(repository.calls.count, 0)
        XCTAssertTrue(pending.isPending(uid: "uid-1"))
        XCTAssertFalse(pending.isPending(uid: "uid-2"))
        // Le bon compte se reconnecte : le retrait est enregistré.
        signedIn = "uid-1"
        withdrawals.resume(uid: "uid-1")
        await waitUntil { !pending.isPending(uid: "uid-1") }
        XCTAssertEqual(repository.withdrawalCalls, 1)
        XCTAssertEqual(repository.calls.first?.uid, "uid-1")
    }

    func testReadingTheAccountResumesAWaitingWithdrawal() async {
        pending.mark(uid: "uid-1")

        withdrawals.resume(uid: "uid-1")
        await waitUntil { !pending.isPending(uid: "uid-1") }

        XCTAssertEqual(repository.withdrawalCalls, 1)
    }

    func testReadingTheAccountDoesNothingWhenNoWithdrawalWaits() async {
        withdrawals.resume(uid: "uid-1")
        await sleeper.settle()

        XCTAssertEqual(repository.calls.count, 0)
    }

    func testTheEndOfTheSessionStopsTheBackgroundTaskAndKeepsTheMark() async {
        network.online = false
        _ = await withdrawals.withdraw(uid: "uid-1")

        await withdrawals.stop()
        network.online = true
        await sleeper.settle()

        XCTAssertEqual(repository.calls.count, 0)
        XCTAssertTrue(pending.isPending(uid: "uid-1"))
        // Le compte revient : la marque est reprise.
        withdrawals.resume(uid: "uid-1")
        await waitUntil { !pending.isPending(uid: "uid-1") }
        XCTAssertEqual(repository.withdrawalCalls, 1)
    }

    func testTheMarkOfASignedOutAccountIsKeptAcrossTheSignOut() async {
        network.online = false
        _ = await withdrawals.withdraw(uid: "uid-1")

        signedIn = nil
        await withdrawals.stop()
        network.online = true
        await sleeper.settle()

        XCTAssertEqual(repository.calls.count, 0)
        XCTAssertTrue(pending.isPending(uid: "uid-1"))
        XCTAssertFalse(withdrawals.isPending(uid: "uid-2"))
    }
}
