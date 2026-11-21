// Les assertions citent des lignes de code et des textes : elles dépassent la limite.
// swiftlint:disable line_length
#if ALERT_DANGER
import XCTest
@testable import Miff

/// Retour du PM (recette) : le maintien s'engage en posant le doigt SUR le bouton, puis CONTINUE même quand le doigt en sort ; il s'arrête seulement au
/// relâchement ou à une annulation du système (arrière-plan, appel entrant, toucher repris). Un doigt posé hors du bouton ne déclenche rien.
///
/// Le modèle ne connaît PAS la position du doigt : le geste (`DragGesture(minimumDistance: 0)`) appelle `touchBegan()` à chaque mouvement, qu'il soit dans
/// ou hors du bouton. « Le doigt sort du bouton » se joue donc ici par des `touchBegan()` répétés (c'est exactement ce qui arrive) ; un test de source
/// garantit que rien ne lit la position. Le vrai doigt se vérifie sur l'iPhone (recette).
@MainActor
final class DangerHoldFingerTests: XCTestCase {
    private var clock = 0
    private var taps = 0
    private var armedCount = 0
    private var doneCount = 0
    private var touchDowns = 0

    private func makeModel() -> DangerHoldModel {
        let model = DangerHoldModel(armMillis: 400, holdMillis: 5_000, nowMillis: { [unowned self] in self.clock })
        model.onTap = { [unowned self] in self.taps += 1 }
        model.onArmed = { [unowned self] in self.armedCount += 1 }
        model.onDone = { [unowned self] in self.doneCount += 1 }
        model.onTouchDown = { [unowned self] in self.touchDowns += 1 }
        return model
    }

    /// Le doigt est posé sur le bouton et le geste est armé (400 ms passées).
    private func armedModel() async -> DangerHoldModel {
        let model = makeModel()
        clock = 0
        model.touchBegan()
        clock = 450
        await eventually("le geste s'arme") { model.armed }
        return model
    }

    // MARK: - Le doigt sort du bouton : le chargement continue

    func testTheFingerLeavingTheButtonDoesNotStopTheLoading() async {
        let model = await armedModel()

        // Le doigt glisse hors du bouton : le geste continue d'envoyer des mouvements, chacun rappelle `touchBegan()`. Rien ne s'annule.
        for second in 1...3 {
            clock = 450 + second * 1_000
            model.touchBegan()
        }
        await eventually("la barre avance même hors du bouton") { model.progress > 0.5 }

        XCTAssertTrue(model.armed)
        XCTAssertEqual(doneCount, 0)
        XCTAssertEqual(armedCount, 1)
    }

    func testTheLoadingGoesAllTheWayToTheEndWithTheFingerOutsideTheButton() async {
        let model = await armedModel()

        clock = 400 + 5_000 + 10
        model.touchBegan()
        await eventually("les 5 secondes sont tenues") { self.doneCount == 1 }

        XCTAssertEqual(doneCount, 1, "une seule fin")
        XCTAssertFalse(model.armed)
        model.touchEnded()
        XCTAssertEqual(doneCount, 1)
        XCTAssertEqual(taps, 0, "relâcher après la fin n'est pas une photo")
    }

    // MARK: - Ce qui arrête le chargement

    func testReleasingStopsTheLoadingAndNothingIsSent() async {
        let model = await armedModel()

        model.touchEnded()
        clock = 400 + 5_000 + 500
        try? await Task.sleep(nanoseconds: 120_000_000)

        XCTAssertFalse(model.armed)
        XCTAssertEqual(model.progress, 0)
        XCTAssertEqual(doneCount, 0, "relâché avant 5 s : rien ne part")
        XCTAssertEqual(taps, 0, "un maintien relâché n'est pas une photo")
    }

    func testTheSystemTakingTheTouchStopsTheLoadingEvenWithoutARelease() async {
        let model = await armedModel()

        // Le système reprend le toucher (geste du bas de l'écran, appel...) : `touchEnded` ne viendra jamais.
        model.touchLost()
        clock = 400 + 5_000 + 500
        try? await Task.sleep(nanoseconds: 120_000_000)

        XCTAssertFalse(model.armed)
        XCTAssertEqual(doneCount, 0, "jamais 5 secondes sans doigt")
        XCTAssertEqual(taps, 0)
    }

    func testTheAppGoingToTheBackgroundStopsTheLoading() async {
        let model = await armedModel()

        model.cancel()
        clock = 400 + 5_000 + 500
        try? await Task.sleep(nanoseconds: 120_000_000)

        XCTAssertFalse(model.armed)
        XCTAssertEqual(doneCount, 0)
    }

    func testALostTouchAfterANormalReleaseChangesNothing() {
        let model = makeModel()
        clock = 0
        model.touchBegan()
        clock = 100
        model.touchEnded()
        XCTAssertEqual(taps, 1, "un appui court est la photo")

        model.touchLost()

        XCTAssertEqual(taps, 1)
        XCTAssertEqual(doneCount, 0)
    }

    func testAfterALostTouchTheNextTouchWorks() async {
        let model = await armedModel()
        model.touchLost()

        clock = 10_000
        model.touchBegan()
        clock = 10_100
        model.touchEnded()

        XCTAssertEqual(taps, 1, "le bouton n'est pas resté bloqué « doigt posé »")
    }

    // MARK: - Un doigt posé hors du bouton ne déclenche rien

    func testAFingerPlacedOutsideTheButtonStartsNothing() async {
        let model = makeModel()

        // Hors de la cible, le geste n'est jamais appelé : aucun `touchBegan`. Même un relâchement ou une perte de toucher parasites ne font rien.
        clock = 6_000
        model.touchEnded()
        model.touchLost()
        try? await Task.sleep(nanoseconds: 100_000_000)

        XCTAssertFalse(model.armed)
        XCTAssertEqual(model.progress, 0)
        XCTAssertEqual(taps, 0)
        XCTAssertEqual(armedCount, 0)
        XCTAssertEqual(doneCount, 0)
        XCTAssertEqual(touchDowns, 0)
    }

    // MARK: - Une interaction compte une fois

    func testTouchDownIsReportedOncePerTouchEvenWhenTheFingerKeepsMoving() {
        let model = makeModel()
        clock = 0
        model.touchBegan()
        model.touchBegan()
        model.touchBegan()
        XCTAssertEqual(touchDowns, 1)

        clock = 100
        model.touchEnded()
        clock = 1_000
        model.touchBegan()
        XCTAssertEqual(touchDowns, 2, "un nouveau toucher")
        model.cancel()
    }

    // MARK: - Garde de source : rien ne lit la position du doigt

    func testTheGestureNeverReadsTheFingerPosition() throws {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Alert
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff/Views/Alert/DangerHoldGesture.swift")
        let code = try String(contentsOf: url, encoding: .utf8)
        let start = try XCTUnwrap(code.range(of: "struct DangerHoldGestureModifier"))
        let end = try XCTUnwrap(code.range(of: "extension View {"))
        let modifier = String(code[start.lowerBound..<end.lowerBound])

        XCTAssertTrue(modifier.contains("DragGesture(minimumDistance: 0)"))
        for forbidden in ["LongPressGesture", ".translation", ".location", ".startLocation", "movedTooFar", "touchMoved", "GeometryReader"] {
            XCTAssertFalse(modifier.contains(forbidden), "le geste ne doit rien lire de la position : \(forbidden)")
        }
        XCTAssertTrue(modifier.contains(".updating($touching)"), "un toucher repris par le système est vu (GestureState)")
    }

    /// La fin d'alerte (« Maintiens 5 secondes ») sur la bannière utilise le MÊME geste : la même règle y vaut.
    func testTheBannerEndsTheAlertWithTheSameGesture() throws {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("Miff/Views/Alert/DangerBannerView.swift")
        let code = try String(contentsOf: url, encoding: .utf8)

        XCTAssertTrue(code.contains("content.dangerHoldGesture(model)"))
        XCTAssertFalse(code.contains("DragGesture"), "pas de geste propre à la bannière")
        XCTAssertFalse(code.contains("LongPressGesture"))
    }
}
#endif
// swiftlint:enable line_length
