// Les assertions citent des lignes de code : elles dépassent la limite.
// swiftlint:disable line_length
#if ALERT_DANGER
import XCTest
@testable import Miff

/// Le geste de l'obturateur lit ce que l'Update sait faire AU MOMENT où il agit (revue indépendante de #276, MAJEUR-1) : si la Miff sélectionnée change pendant que
/// l'Update est affiché, le maintien envoie vers la NOUVELLE Miff (celle que le tracé rouge nomme), jamais vers celle de l'apparition du viseur : sinon une fausse sécurité
/// (l'alerte partirait ailleurs que dans la Miff annoncée).
@MainActor
final class DangerViewfinderBindingTests: XCTestCase {
    private var triggered: [String?] = []
    private var already: [String?] = []

    private func hooks(miffId: String?, inProgress: Bool = false) -> DangerUpdateHooks {
        DangerUpdateHooks(
            hintVisible: false,
            onHintDone: {},
            activeMiffId: miffId,
            targetMiffName: miffId,
            several: true,
            activeAlertMiffName: nil,
            alertInProgress: inProgress,
            onTrigger: { [unowned self] in self.triggered.append(miffId) },
            onAlreadyActive: { [unowned self] name in self.already.append(name) },
            onOpenEmergency: { _ in }
        )
    }

    func testTheHoldGoesToTheMiffSelectedNowNotTheOneOfWhenTheViewfinderAppeared() {
        let state = DangerViewfinderState()
        state.bind(hooks: hooks(miffId: "A"), onPhoto: {})

        // Le Feed bascule sur une autre Miff (ou la liste se recharge après une perte) pendant que l'Update est affiché : le geste est rebranché sur l'identifiant.
        state.bind(hooks: hooks(miffId: "B"), onPhoto: {})
        state.hold.onDone()

        XCTAssertEqual(triggered, ["B"], "le tracé rouge dit la Miff B : l'alerte part dans la Miff B")
    }

    func testAnEmptyListAtTheStartThenAKnownMiffNoLongerSaysUnavailable() {
        let state = DangerViewfinderState()
        state.bind(hooks: hooks(miffId: nil), onPhoto: {})

        state.bind(hooks: hooks(miffId: "B"), onPhoto: {})
        state.hold.onDone()

        XCTAssertEqual(triggered, ["B"])
    }

    func testAnAlertInProgressNeverStartsASecondOneAndSaysItCalmly() {
        let state = DangerViewfinderState()
        state.bind(hooks: hooks(miffId: "A"), onPhoto: {})
        state.bind(hooks: hooks(miffId: "A", inProgress: true), onPhoto: {})

        state.hold.onArmed()
        state.hold.onDone()

        XCTAssertTrue(triggered.isEmpty)
        XCTAssertEqual(already.count, 1)
    }

    func testAShortTouchStillTakesThePhotoWithTheLatestHandler() {
        let state = DangerViewfinderState()
        var photos: [String] = []
        state.bind(hooks: hooks(miffId: "A"), onPhoto: { photos.append("old") })
        state.bind(hooks: hooks(miffId: "A"), onPhoto: { photos.append("new") })

        state.hold.onTap()

        XCTAssertEqual(photos, ["new"])
    }

    /// MAJEUR-2 : posé par-dessus la coquille, l'écran « avertis » est modal pour VoiceOver (comme les autres surcouches de l'app) et annonce le changement d'écran.
    func testTheSentScreenIsModalForVoiceOverAndAnnouncesTheScreenChange() throws {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Alert
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff/Views/Alert/DangerSentView.swift")
        let code = try String(contentsOf: url, encoding: .utf8)

        XCTAssertTrue(code.contains(".accessibilityAddTraits(.isModal)"), "VoiceOver ne parcourt pas le Feed caché avant « Appeler »")
        XCTAssertTrue(code.contains("UIAccessibility.post(notification: .screenChanged, argument: nil)"))
    }

    /// MINEUR-3 : le dialogue « Supprimer la Miff » prévient qu'une alerte en cours sera retirée, comme « Quitter » et l'arrêt du partage.
    func testTheDeleteDialogWarnsThatAnAlertInProgressWillBeRemoved() throws {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Alert
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff/Views/Miff/MiffScreenSheets.swift")
        let code = try String(contentsOf: url, encoding: .utf8)

        XCTAssertTrue(code.contains("message: dangerAlertWarning(body, miffId: state.miffId, signal: dangerSignal)"))
    }

    func testTheViewRebindsOnTheIdentifierNotTheName() throws {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Alert
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff/Views/Alert/DangerViewfinderSupport.swift")
        let code = try String(contentsOf: url, encoding: .utf8)

        XCTAssertTrue(code.contains(".onChange(of: hooks.activeMiffId) { _ in bind() }"), "deux Miffs peuvent avoir le même nom : l'identifiant déclenche le rebranchement")
        XCTAssertFalse(code.contains("let hooks = self.hooks"), "aucune copie des branchements prise à l'apparition du viseur")
    }
}
#endif
// swiftlint:enable line_length
