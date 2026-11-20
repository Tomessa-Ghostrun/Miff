// Les assertions citent des lignes de code et des textes : elles dépassent la limite.
// swiftlint:disable line_length
#if ALERT_DANGER
import XCTest
@testable import Miff

/// Retour du PM (recette) : la bulle bleue d'onboarding de l'Update n'a AUCUNE minuterie ; elle disparaît à la PREMIÈRE interaction avec le bouton de capture
/// (clic OU maintien, ou l'action équivalente d'un lecteur d'écran) et ne revient plus. La mémorisation reste l'indicateur LOCAL `alertHintDone`
/// (aucune donnée personnelle, aucun serveur).
@MainActor
final class DangerHintBubbleTests: XCTestCase {
    private var hintDone = 0
    private var photos = 0
    private let directory = FileManager.default.temporaryDirectory
        .appendingPathComponent("hint-\(UUID().uuidString)", isDirectory: true)

    override func tearDownWithError() throws {
        try? FileManager.default.removeItem(at: directory)
    }

    private func hooks() -> DangerUpdateHooks {
        DangerUpdateHooks(
            hintVisible: true,
            onHintDone: { [unowned self] in self.hintDone += 1 },
            activeMiffId: "A",
            targetMiffName: "A",
            several: false,
            activeAlertMiffName: nil,
            alertInProgress: false,
            onTrigger: {},
            onAlreadyActive: { _ in },
            onOpenEmergency: { _ in }
        )
    }

    private func boundState() -> DangerViewfinderState {
        let state = DangerViewfinderState()
        state.bind(hooks: hooks(), onPhoto: { [unowned self] in self.photos += 1 })
        return state
    }

    // MARK: - Disparition à la première interaction

    func testTheHintStaysUntilTheShutterIsTouched() {
        let state = boundState()

        XCTAssertTrue(state.hintShown, "affichée à l'ouverture")
        XCTAssertEqual(hintDone, 0, "rien n'est mémorisé sans interaction")
    }

    func testAClickOnTheShutterRemovesTheHintForGood() {
        let state = boundState()

        state.hold.touchBegan()
        state.hold.touchEnded()

        XCTAssertFalse(state.hintShown)
        XCTAssertEqual(hintDone, 1, "mémorisé : elle ne revient plus")
        XCTAssertEqual(photos, 1, "le clic prend toujours la photo")
    }

    func testAHoldOnTheShutterRemovesTheHintAsSoonAsTheFingerLands() {
        let state = boundState()

        state.hold.touchBegan()

        XCTAssertFalse(state.hintShown, "dès la première interaction, sans attendre l'armement ni le relâchement")
        XCTAssertEqual(hintDone, 1)
        state.hold.cancel()
    }

    func testTheScreenReaderActionsRemoveTheHintToo() {
        let state = boundState()

        state.interacted()

        XCTAssertFalse(state.hintShown)
        XCTAssertEqual(hintDone, 1)
    }

    // MARK: - Mémorisation locale

    func testTheHintDoesNotComeBackOnceTheInteractionIsRemembered() {
        let store = DangerAlertFileStore(directory: directory)
        XCTAssertFalse(store.hintDone())

        store.markHintDone()

        XCTAssertTrue(DangerAlertFileStore(directory: directory).hintDone(), "relu depuis l'appareil au relancement")
    }

    /// Le drapeau est celui de l'INSTALLATION (ticket 7.1, comme le fait d'avoir vu le carrousel) : il ne contient AUCUNE donnée de la personne (un fichier d'un
    /// octet), donc la déconnexion et la suppression de compte (`clearAccount`) ne l'effacent pas ; elles effacent le pointeur de l'alerte et l'adoption. Il part
    /// avec la désinstallation de l'app.
    func testTheInstallationFlagHoldsNoPersonalDataAndIsKeptByTheAccountWipe() throws {
        let store = DangerAlertFileStore(directory: directory)
        store.markHintDone()
        store.markAdoptedFor("uid-de-test")

        store.clearAccount()

        XCTAssertTrue(store.hintDone(), "drapeau d'installation : conservé")
        XCTAssertNil(store.adoptedFor(), "ce qui touche au compte est effacé")
        let flag = try Data(contentsOf: directory.appendingPathComponent("hint-done"))
        XCTAssertEqual(flag.count, 1, "un seul octet : rien de personnel")
    }

    // MARK: - Aucune minuterie

    func testTheBubbleHasNoTimer() throws {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Alert
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff/Views/Alert/DangerAlertComponents.swift")
        let code = try String(contentsOf: url, encoding: .utf8)
        let start = try XCTUnwrap(code.range(of: "struct DangerHintBubble"))
        let end = try XCTUnwrap(code.range(of: "// MARK: - Le mode rouge"))
        let bubble = String(code[start.lowerBound..<end.lowerBound])

        for forbidden in ["sleep", "Timer", "asyncAfter", "onGone", "visibleSeconds", "hintSeconds"] {
            XCTAssertFalse(bubble.contains(forbidden), "la bulle ne se retire pas toute seule : \(forbidden)")
        }
        XCTAssertTrue(bubble.contains("UIAccessibility.post(notification: .announcement"), "elle reste annoncée au lecteur d'écran")
    }

    func testTheSlotOnlyDependsOnTheInteractionNotOnTheArming() throws {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("Miff/Views/Alert/DangerViewfinderSupport.swift")
        let code = try String(contentsOf: url, encoding: .utf8)

        XCTAssertTrue(code.contains("if hooks.hintVisible && state.hintShown {"))
        XCTAssertTrue(code.contains("hold.onTouchDown = { [weak self] in"))
    }
}
#endif
// swiftlint:enable line_length
