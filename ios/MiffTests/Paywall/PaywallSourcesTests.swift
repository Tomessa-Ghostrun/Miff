import XCTest
@testable import Miff

/// Ce que l'écran d'offre doit TOUJOURS garantir, vérifié sur les SOURCES pour qu'un changement ultérieur ne le casse pas sans que
/// personne le voie (ticket `paywall-v2.md` : CR1, CR7, CR10, CR12, PW2, PW8, PW14). Les comportements, eux, sont testés par
/// `PaywallCloseGateTests` et `PaywallViewModelTests`.
final class PaywallSourcesTests: XCTestCase {
    private var iosRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Paywall
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
    }

    private func read(_ relative: String) throws -> String {
        let url = iosRoot.appendingPathComponent(relative)
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw XCTSkip("Fichier introuvable (\(url.path)).")
        }
        return try String(contentsOf: url, encoding: .utf8)
    }

    /// Toutes les sources de l'app (hors Diagnostic : aperçus de la CI), avec leur chemin relatif.
    private func appSources() throws -> [(path: String, text: String)] {
        let app = iosRoot.appendingPathComponent("Miff")
        guard let found = FileManager.default.enumerator(at: app, includingPropertiesForKeys: nil) else {
            throw XCTSkip("Sources introuvables (\(app.path)).")
        }
        var sources: [(path: String, text: String)] = []
        for case let file as URL in found where file.pathExtension == "swift" {
            let path = file.path.replacingOccurrences(of: "\\", with: "/")
            if !path.contains("/Miff/Diagnostic/") {
                sources.append((path: path, text: try String(contentsOf: file, encoding: .utf8)))
            }
        }
        return sources
    }

    private func paywallSources() throws -> [(path: String, text: String)] {
        try appSources().filter { $0.path.contains("/Paywall") || $0.path.hasSuffix("PlacePaywallView.swift") }
    }

    private func count(of needle: String, in text: String) -> Int {
        text.components(separatedBy: needle).count - 1
    }

    // MARK: - Un seul composant pour tous les points d'entrée (CR10)

    func testEveryEntryPointOpensTheSameFullScreenComponent() throws {
        let hosts = try read("Miff/Views/Miff/MiffHosts.swift")

        XCTAssertEqual(count(of: "PlacePaywallView(", in: hosts), 1, "un seul endroit crée l'écran d'offre")
        XCTAssertEqual(count(of: ".fullScreenCover(item: $paywall)", in: hosts), 1, "en plein écran (CR7)")
        // La définition de `presentPaywall`, puis ses deux appelants : le sélecteur de Miff du Feed (2e Miff) et `openPlacePaywall`
        // (4e lieu et zone de danger, depuis le formulaire d'un lieu).
        XCTAssertEqual(count(of: "presentPaywall(", in: hosts), 3)
        XCTAssertTrue(hosts.contains("onOpenOffer: { presentPaywall(.extraMiff) }"))
        XCTAssertTrue(hosts.contains("        presentPaywall(reason)\n"))
        XCTAssertFalse(hosts.contains("placePaywall"), "l'offre n'est plus une page empilée (pas de geste retour)")
    }

    func testTheOfferScreenIsBuiltInOnePlaceOnly() throws {
        for source in try appSources() where !source.path.hasSuffix("PaywallScreen.swift") {
            if source.path.hasSuffix("PlacePaywallView.swift") {
                XCTAssertEqual(count(of: "PaywallScreen(", in: source.text), 1)
            } else {
                XCTAssertEqual(count(of: "PaywallScreen(", in: source.text), 0, "\(source.path) : un second écran ?")
            }
        }
    }

    func testEachDisplayCreatesItsOwnModelWithTheProductionVariant() throws {
        let view = try read("Miff/Views/Place/PlacePaywallView.swift")

        XCTAssertTrue(view.contains("PaywallViewModel(reason: reason, announcer: SystemPaywallAnnouncer())"))
        XCTAssertFalse(view.contains("state:"), "en production : l'état par défaut, « Miff+ arrive bientôt »")
    }

    // MARK: - La croix (CR1, CR7)

    func testTheCrossIsOnlyDrawnWhenTheModelAllowsItAndTheSheetCannotBeDraggedBefore() throws {
        let screen = try read("Miff/Views/Paywall/PaywallScreen.swift")

        XCTAssertTrue(screen.contains("if isCloseAvailable {\n                PaywallCloseButton(action: onClose)"))
        XCTAssertTrue(screen.contains(".interactiveDismissDisabled(!model.isCloseAvailable)"))
        XCTAssertTrue(screen.contains("accessibilityHidden(true)"), "l'emplacement réservé est caché de VoiceOver")
        XCTAssertTrue(screen.contains("accessibilityReduceMotion"), "fondu instantané si les animations sont réduites")
        XCTAssertTrue(screen.contains("Spacing.minTouchTarget"), "zone tactile d'au moins 44 pt")
    }

    func testNoOtherExitThanTheCrossAndTheSubscribedContinue() throws {
        var exits = 0
        for source in try paywallSources() {
            exits += count(of: "action: onClose", in: source.text)
            XCTAssertFalse(source.text.contains("place_paywall_later"), "« Plus tard » n'existe plus (ticket E9)")
            XCTAssertFalse(source.text.contains("dismiss()"), "aucune autre sortie que la croix")
        }

        XCTAssertEqual(exits, 2, "la croix (après 3 s) et « Continuer » d'un compte abonné")
    }

    func testTheDelayIsAssignedInOnePlaceOnly() throws {
        let pattern = try NSRegularExpression(pattern: "closeDelay[^\\n]*=\\s*[0-9]")
        for source in try appSources() where !source.path.hasSuffix("PaywallCloseGate.swift") {
            let range = NSRange(source.text.startIndex..., in: source.text)
            XCTAssertNil(pattern.firstMatch(in: source.text, range: range), "\(source.path) : un second délai ?")
        }
        let gate = try read("Miff/Models/Paywall/PaywallCloseGate.swift")
        XCTAssertEqual(count(of: "static let closeDelay: TimeInterval = 3", in: gate), 1)
    }

    func testTheDelayCannotBeChangedFromTheNetworkOrTheStore() throws {
        for source in try paywallSources() {
            for forbidden in ["RemoteConfig", "Firestore", "UserDefaults", "URLSession"] {
                XCTAssertFalse(source.text.contains(forbidden), "\(source.path) : \(forbidden), réglage distant (CR12)")
            }
        }
    }

    // MARK: - Contenu (PW1, PW2, PW8)

    func testNoPriceNorCurrencyIsWrittenInTheProductionSources() throws {
        let price = try NSRegularExpression(pattern: "€|\\bEUR\\b|\\bUSD\\b")
        for source in try paywallSources() {
            let lines = source.text.components(separatedBy: "\n")
            for line in lines where !line.trimmingCharacters(in: .whitespaces).hasPrefix("//") {
                let range = NSRange(line.startIndex..., in: line)
                XCTAssertNil(price.firstMatch(in: line, range: range), "\(source.path) : un prix en dur ? \(line)")
            }
        }
    }

    func testTheOfferListsThreeFeaturesAndNoSmartNotifications() throws {
        let screen = try read("Miff/Views/Paywall/PaywallScreen.swift")

        XCTAssertEqual(count(of: "PlacePaywallFeature(", in: screen), 3, "lieux, Miffs, zones de danger (E1)")
        XCTAssertFalse(screen.lowercased().contains("notification"))
    }
}
