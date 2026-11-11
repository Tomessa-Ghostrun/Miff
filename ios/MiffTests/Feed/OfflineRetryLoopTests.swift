// Les assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

// Le compte à rebours « Nouvel essai dans N s », l'essai automatique de 10 s (dix pas d'une seconde), son plafond de 3 essais quand le
// réseau est annoncé mais muet, et la fermeture de la pop-in par la personne. Aucun temps réel : `ManualSleeper` commande chaque seconde.
@MainActor
final class OfflineRetryLoopTests: OfflineRetryTestCase {
    // MARK: - Le compte à rebours et l'essai automatique

    func testTheCountdownStartsAtTenAndGoesDownOneSecondAtATime() async {
        let model = makeModel()
        _ = await offlineAndShown(model)
        await waitUntil { model.secondsLeft == 10 }
        XCTAssertEqual(model.secondsLeft, 10)

        await sleeper.elapse(1_000)
        await waitUntil { model.secondsLeft == 9 }
        XCTAssertEqual(model.secondsLeft, 9)

        await sleeper.elapse(1_000, times: 8)
        await waitUntil { model.secondsLeft == 1 }
        XCTAssertEqual(model.secondsLeft, 1)
    }

    func testOfflineTheTenSecondTryNeverReadsAnythingAndHasNoCap() async {
        let model = makeModel()
        _ = await offlineAndShown(model)

        // Plus de trois tours : sans réseau, la minuterie tourne sans rien lire et ne s'arrête pas.
        for _ in 1...4 {
            await oneInterval()
        }
        await waitUntil { model.secondsLeft == 10 }

        XCTAssertEqual(retries, [], "pas de réseau : aucune lecture, jamais de boucle contre un serveur injoignable")
        XCTAssertTrue(model.isShown)
        XCTAssertEqual(model.secondsLeft, 10, "la minuterie tourne encore")
    }

    func testOnlineWithoutDataTheTryHappensEveryTenSecondsWhileThePopinIsShown() async {
        let model = makeModel()
        _ = await onlineAndShown(model)

        await sleeper.elapse(1_000, times: 9)
        XCTAssertEqual(retries, [], "le premier essai est dans 10 s, pas avant")
        await sleeper.elapse(1_000)
        await waitUntil { self.retries.count == 1 }
        XCTAssertEqual(retries, [.automatic])

        await oneInterval()
        await waitUntil { self.retries.count == 2 }
        XCTAssertEqual(retries, [.automatic, .automatic])
    }

    func testOnlineButSilentTheAutomaticTriesStopAtThreeThenOnlyTheButtonRestartsThem() async {
        let model = makeModel()
        _ = await onlineAndShown(model)

        await reachTheCap(model)

        XCTAssertEqual(retries, [.automatic, .automatic, .automatic])
        XCTAssertNil(model.secondsLeft, "plus de compte à rebours : aucun essai n'est prévu")
        XCTAssertEqual(sleeper.pendingFor(1_000), 0, "plus aucune minuterie")
        XCTAssertTrue(model.isPopinVisible, "la pop-in reste, avec « Réessayer »")

        // Même après longtemps : rien de plus.
        sleeper.fire()
        await sleeper.settle()
        XCTAssertEqual(retries.count, 3)

        // « Réessayer » relance : un geste, puis le compte et le plafond repartent de zéro.
        model.retryNow()
        XCTAssertEqual(retries.last, .manual)
        await waitUntil { model.secondsLeft == 10 }
        await oneInterval()
        await waitUntil { self.retries.count == 5 }
        XCTAssertEqual(retries, [.automatic, .automatic, .automatic, .manual, .automatic])
    }

    func testTheNetworkGoingAwayAfterTheCapRestartsTheTimerWithoutAnyReading() async {
        let model = makeModel()
        _ = await onlineAndShown(model)
        await reachTheCap(model)

        network.online = false
        await waitUntil { model.secondsLeft == 10 }
        await oneInterval()
        await oneInterval()

        XCTAssertEqual(retries.count, 3, "sans réseau, rien n'est lu")
        XCTAssertTrue(model.isShown)
    }

    func testThePopinNeverStacksOrDuplicatesItsTimer() async {
        let model = makeModel()
        _ = await offlineAndShown(model)
        await waitUntil { self.sleeper.pendingFor(1_000) == 1 }

        model.setNeed(.failed)
        await sleeper.settle()

        XCTAssertTrue(model.isShown)
        XCTAssertEqual(sleeper.pendingFor(1_000), 1, "une seule minuterie d'essai, même si l'écran change plusieurs fois d'état")
    }

    func testARetryReadingThatStartsWhileThePopinIsShownDoesNotMakeItBlink() async {
        let model = makeModel()
        _ = await offlineAndShown(model)
        await waitUntil { self.sleeper.pendingFor(1_000) == 1 }

        // « Réessayer » : l'écran repasse par « chargement » le temps de la relecture, puis par « échec ».
        model.setNeed(.failed)
        model.setNeed(.loading)
        XCTAssertTrue(model.isShown, "la pop-in reste pendant la relecture")
        model.setNeed(.failed)
        await sleeper.settle()

        XCTAssertTrue(model.isShown)
        XCTAssertEqual(sleeper.pendingFor(1_000), 1, "une seule minuterie d'essai")
    }

    func testRetryNowStartsTheCountdownAgainFromTen() async {
        let model = makeModel()
        _ = await onlineAndShown(model)
        await sleeper.elapse(1_000, times: 4)
        await waitUntil { model.secondsLeft == 6 }

        model.retryNow()

        await waitUntil { model.secondsLeft == 10 }
        XCTAssertEqual(model.secondsLeft, 10)
    }

    // MARK: - La fermer

    func testClosingThePopinKeepsTheStateShownButHidesTheCardAndTheTriesGoOn() async {
        let model = makeModel()
        _ = await onlineAndShown(model)

        model.dismiss()

        XCTAssertTrue(model.isShown)
        XCTAssertFalse(model.isPopinVisible, "la carte est fermée")
        XCTAssertTrue(model.isInlineVisible, "la page dit « Pas de connexion » à sa place")
        await oneInterval()
        await waitUntil { self.retries.count == 1 }
        XCTAssertEqual(retries, [.automatic], "les essais continuent")
    }

    func testRetryStillWorksOnceThePopinWasClosed() async {
        let model = makeModel()
        _ = await onlineAndShown(model)
        model.dismiss()

        model.retryNow()

        XCTAssertEqual(retries, [.manual])
        XCTAssertTrue(model.isInlineVisible)
    }

    func testClosingDoesNothingWhenThereIsNoPopin() async {
        let model = makeModel()
        model.setNeed(.none)
        _ = show(model)
        await sleeper.settle()

        model.dismiss()

        XCTAssertFalse(model.isDismissed)
        XCTAssertFalse(model.isInlineVisible)
    }

    func testContentArrivingAfterTheCloseClearsEverythingAndNextTimeThePopinIsBackOpen() async {
        let model = makeModel()
        _ = await onlineAndShown(model)
        model.dismiss()

        model.setNeed(.none)
        XCTAssertFalse(model.isShown)
        XCTAssertFalse(model.isInlineVisible)
        XCTAssertFalse(model.isDismissed)

        model.setNeed(.failed)
        network.online = false
        await waitUntil { model.isShown }
        XCTAssertTrue(model.isPopinVisible, "une nouvelle panne : la pop-in revient, ouverte")
    }

    func testTheNetworkComingBackAfterTheCloseClearsTheInlineNoteToo() async {
        let model = makeModel()
        _ = await offlineAndShown(model)
        model.dismiss()

        network.online = true
        await waitUntil { !model.isShown }

        XCTAssertFalse(model.isInlineVisible)
        XCTAssertEqual(retries, [.networkBack])
    }
}

// swiftlint:enable line_length
