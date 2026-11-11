// Les assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

// La pop-in « Pas de connexion » : quand elle vient, le retour du réseau, « Réessayer » et la sortie de l'écran, sans réseau ni horloge
// réels. Les attentes (1,5 s de grâce hors connexion, 8 s « aucune donnée », 0,8 s de « Réessayer ») sont commandées par le test
// (`ManualSleeper`, via `OfflineRetryTestCase`) : aucun temps réel n'est attendu. Le compte à rebours, le plafond et la fermeture sont
// dans `OfflineRetryLoopTests`.
@MainActor
final class OfflineRetryModelTests: OfflineRetryTestCase {
    // MARK: - Quand la pop-in vient

    func testWithoutNetworkAndNothingToShowThePopinComesAfterAShortGraceNotBefore() async {
        let model = makeModel()
        model.setNeed(.loading)
        _ = show(model)
        await waitUntil { self.sleeper.pendingFor(1_500) == 1 }
        XCTAssertFalse(model.isShown, "un lancement hors connexion qui trouve vite ses informations ne voit jamais la pop-in")
        XCTAssertEqual(sleeper.pendingFor(8_000), 0)

        sleeper.fire(1_500)
        await waitUntil { model.isShown }

        XCTAssertTrue(model.isShown)
        XCTAssertTrue(model.isPopinVisible)
    }

    func testInformationFoundDuringTheGraceMeansNoPopinAtAll() async {
        let model = makeModel()
        model.setNeed(.loading)
        _ = show(model)
        await waitUntil { self.sleeper.pendingFor(1_500) == 1 }

        model.setNeed(.none)
        sleeper.fire(1_500)
        await sleeper.settle()

        XCTAssertFalse(model.isShown)
    }

    func testWithCachedContentThereIsNoPopinEvenOffline() async {
        let model = makeModel()
        model.setNeed(.none)
        _ = show(model)
        await sleeper.settle()

        XCTAssertFalse(model.isShown)
        XCTAssertEqual(sleeper.pendingTotal, 0, "aucune minuterie quand il y a quelque chose à montrer")
    }

    func testAFailedReadOnlyShowsThePopinWhenTheNetworkIsMissingAndAtOnce() async {
        let model = makeModel()
        model.setNeed(.failed)
        _ = show(model)
        await waitUntil { model.isShown }
        XCTAssertTrue(model.isShown, "il n'y a rien à attendre : pas de grâce")

        // Le réseau revient : l'échec garde son propre message (et ses boutons), la pop-in s'en va.
        network.online = true
        await waitUntil { !model.isShown }
        XCTAssertFalse(model.isShown)
    }

    func testOnlineButNothingArrivesTheNoDataDelayBringsThePopin() async {
        network.online = true
        let model = makeModel()
        model.setNeed(.loading)
        _ = show(model)
        await waitUntil { self.sleeper.pendingFor(8_000) == 1 }
        XCTAssertFalse(model.isShown, "pas avant le délai : un chargement normal n'affiche rien")

        sleeper.fire(8_000)
        await waitUntil { model.isShown }
        XCTAssertTrue(model.isShown)
    }

    func testTheNoDataDelayIsCancelledWhenContentArrives() async {
        network.online = true
        let model = makeModel()
        model.setNeed(.loading)
        _ = show(model)
        await waitUntil { self.sleeper.pendingFor(8_000) == 1 }

        model.setNeed(.none)
        sleeper.fire(8_000)
        await sleeper.settle()

        XCTAssertFalse(model.isShown)
    }

    func testContentArrivingMakesThePopinDisappear() async {
        let model = makeModel()
        _ = await offlineAndShown(model)

        model.setNeed(.none)

        XCTAssertFalse(model.isShown)
        XCTAssertNil(model.secondsLeft)
    }

    // MARK: - Le retour du réseau

    func testWhenTheNetworkComesBackThePopinDisappearsAndTheScreenReadsOnce() async {
        let model = makeModel()
        _ = await offlineAndShown(model)

        network.online = true
        await waitUntil { !model.isShown }

        XCTAssertFalse(model.isShown, "elle disparaît d'elle-même")
        XCTAssertEqual(retries, [.networkBack], "une seule relecture, tout de suite, sans attendre les 10 s")
        // Les 10 s ne comptent plus : l'essai automatique est arrêté.
        sleeper.fire(1_000)
        await sleeper.settle()
        XCTAssertEqual(retries, [.networkBack])
        XCTAssertNil(model.secondsLeft)
    }

    func testIfNothingArrivesAfterTheNetworkCameBackTheDelayStartsAgain() async {
        let model = makeModel()
        _ = await offlineAndShown(model)
        network.online = true
        await waitUntil { self.sleeper.pendingFor(8_000) == 1 }

        XCTAssertFalse(model.isShown)
        sleeper.fire(8_000)
        await waitUntil { model.isShown }
        XCTAssertTrue(model.isShown)
    }

    func testTheNetworkGoingAwayWhileWaitingBringsThePopinAfterTheShortGrace() async {
        network.online = true
        let model = makeModel()
        model.setNeed(.loading)
        _ = show(model)
        await waitUntil { self.sleeper.pendingFor(8_000) == 1 }

        network.online = false
        await waitUntil { self.sleeper.pendingFor(1_500) == 1 }
        XCTAssertFalse(model.isShown)
        sleeper.fire(1_500)
        await waitUntil { model.isShown }

        XCTAssertTrue(model.isShown)
        XCTAssertEqual(retries, [])
    }

    func testThePopinAlreadyShownStaysWhenTheNetworkGoesAwayToo() async {
        let model = makeModel()
        _ = await onlineAndShown(model)

        network.online = false
        await sleeper.settle()

        XCTAssertTrue(model.isShown, "pas de clignotement : elle ne s'efface pas pour revenir deux secondes plus tard")
    }

    // MARK: - « Réessayer »

    func testRetryNowReadsAtOnceWhenTheNetworkIsThere() async {
        let model = makeModel()
        _ = await onlineAndShown(model)

        model.retryNow()

        XCTAssertEqual(retries, [.manual])
        XCTAssertTrue(model.isRetrying, "le bouton montre qu'il a été pris en compte")
        await sleeper.elapse(800)
        await waitUntil { !model.isRetrying }
        XCTAssertFalse(model.isRetrying)
    }

    func testRetryNowWithoutNetworkReadsNothingButAnswers() async {
        let model = makeModel()
        _ = await offlineAndShown(model)

        model.retryNow()

        XCTAssertEqual(retries, [], "pas de réseau : aucune lecture")
        XCTAssertTrue(model.isRetrying)
        await sleeper.elapse(800)
        await waitUntil { !model.isRetrying }
        XCTAssertFalse(model.isRetrying)
        XCTAssertTrue(model.isShown, "la pop-in reste tant que la connexion n'est pas revenue")
    }

    func testTwoQuickTapsOnRetryReadOnce() async {
        let model = makeModel()
        _ = await onlineAndShown(model)

        model.retryNow()
        model.retryNow()

        XCTAssertEqual(retries, [.manual])
    }

    // MARK: - Quitter l'écran

    func testLeavingTheScreenRemovesThePopinAndStopsTheTries() async {
        let model = makeModel()
        let task = await onlineAndShown(model)
        await waitUntil { self.sleeper.pendingFor(1_000) == 1 }
        XCTAssertTrue(model.isShown)

        task.cancel()
        await task.value
        XCTAssertFalse(model.isShown, "l'onglet change ou l'app part : plus de pop-in")
        XCTAssertNil(model.secondsLeft)
        sleeper.fire()
        await sleeper.settle()

        XCTAssertEqual(retries, [], "plus aucun essai après la sortie de l'écran")
    }

    func testComingBackToTheScreenStartsFresh() async {
        let model = makeModel()
        let first = await offlineAndShown(model)
        model.dismiss()
        first.cancel()
        await first.value
        XCTAssertFalse(model.isShown)
        XCTAssertFalse(model.isDismissed)

        _ = show(model)
        await sleeper.elapse(1_500)
        await waitUntil { model.isShown }

        XCTAssertTrue(model.isPopinVisible)
    }
}
