// Quelques assertions portent un message en clair : elles dépassent la limite de ligne.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// Ce que l'offre périodique doit TOUJOURS garantir, vérifié sur les SOURCES pour qu'un changement ultérieur ne le casse pas sans que personne le voie (ticket
/// `docs/tickets/paywall-periodique.md`, décisions du PM des 2026-11-25 et 2026-11-26) : aucun drapeau de build (P1), les constantes écrites une seule fois, chaque surcouche
/// des onglets qui se déclare, chaque entrée de l'offre qui signale son affichage, rien envoyé, rien journalisé, aucune écriture sur le serveur, aucun achat ni restauration
/// déclenchés tout seuls, et les outils d'essai dans les builds de test seulement. Miroir de `PeriodicPaywallBuildTest` d'Android.
final class PeriodicPaywallGuardTests: XCTestCase {
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

    /// Les lignes de code (commentaires et lignes vides exclus) d'un fichier.
    private func code(_ relative: String) throws -> [String] {
        try read(relative).components(separatedBy: "\n").filter {
            let line = $0.trimmingCharacters(in: .whitespaces)
            return !line.isEmpty && !line.hasPrefix("//")
        }
    }

    private func swiftFiles(under folder: String) throws -> [String] {
        let base = iosRoot.appendingPathComponent(folder)
        guard let files = FileManager.default.enumerator(at: base, includingPropertiesForKeys: nil) else {
            throw XCTSkip("Sources introuvables (\(base.path)).")
        }
        var paths: [String] = []
        for case let url as URL in files where url.pathExtension == "swift" {
            paths.append("\(folder)/" + String(url.path.dropFirst(base.path.count + 1)).replacingOccurrences(of: "\\", with: "/"))
        }
        return paths.sorted()
    }

    /// Les fichiers de l'offre périodique (tout ce qui est écrit pour elle, rien d'autre).
    private let periodicFiles = [
        "Miff/Models/Paywall/PeriodicPaywallRules.swift",
        "Miff/Models/Paywall/PeriodicPaywallSituation+Builders.swift",
        "Miff/Models/Paywall/PeriodicPaywallClock.swift",
        "Miff/Models/Paywall/PeriodicTapTracker.swift",
        "Miff/Services/Paywall/PeriodicPaywallFileStore.swift",
        "Miff/Services/Paywall/PeriodicPaywallServices.swift",
        "Miff/ViewModels/PeriodicPaywallWatcher.swift",
        "Miff/Views/Paywall/PeriodicPaywallEnvironment.swift",
        "Miff/Views/Paywall/PeriodicPaywallHost.swift",
        "Miff/Views/Paywall/PeriodicTouchProbe.swift"
    ]

    // MARK: - Aucun drapeau de build (décision du PM, P1)

    func testThereIsNoSwitchForThePeriodicOffer() throws {
        let project = try read("project.yml").components(separatedBy: "\n")
            .filter { !$0.trimmingCharacters(in: .whitespaces).hasPrefix("#") }.joined(separator: "\n")
        XCTAssertFalse(project.uppercased().contains("PERIODIC"), "aucune condition de compilation ni réglage de build pour l'offre périodique")

        let flavor = try read("Miff/Models/BuildInfo.swift")
        XCTAssertFalse(flavor.lowercased().contains("periodic"), "aucun drapeau dans BuildFlavor : la règle est active dans tous les builds")

        for workflow in ["../.github/workflows/ios-testflight.yml", "../.github/workflows/ios.yml"] {
            XCTAssertFalse(try read(workflow).lowercased().contains("periodic"), "\(workflow) : aucune entrée pour l'offre périodique")
        }
        for path in try swiftFiles(under: "Miff") {
            let lines = try code(path)
            XCTAssertFalse(lines.contains { $0.contains("#if") && $0.uppercased().contains("PERIODIC") }, "\(path) : un drapeau ?")
        }
    }

    func testTheConstantsOfThePmAreWrittenInOnePlaceOnly() throws {
        var threshold = 0
        var absence = 0
        var quiet = 0
        for path in try swiftFiles(under: "Miff") {
            for line in try code(path) {
                threshold += line.contains("static let openingsBetween = 10") ? 1 : 0
                absence += line.contains("static let backgroundMillis = 30 * 60_000") ? 1 : 0
                quiet += line.contains("static let quietAfterTouchMillis = 1_500") ? 1 : 0
            }
        }

        XCTAssertEqual(threshold, 1, "10 ouvertures (décision du PM du 2026-11-26)")
        XCTAssertEqual(absence, 1, "30 minutes d'absence")
        XCTAssertEqual(quiet, 1, "1,5 s de calme après un toucher")
    }

    // MARK: - Chaque surcouche des onglets se déclare (équivalent de PeriodicPaywallBuildTest d'Android)

    /// Les écrans qui ne sont PAS des onglets ouverts (l'onboarding du profil et ma propre alerte) : l'offre n'y vient de toute façon jamais.
    private let exempted: [String: String] = [
        "Miff/Views/Profile/PhoneStepView.swift": "onboarding : la coquille n'est pas à l'écran",
        "Miff/Views/Profile/ProfileFlowView.swift": "onboarding : la coquille n'est pas à l'écran",
        "Miff/Views/Profile/ProfileStepView.swift": "onboarding : la coquille n'est pas à l'écran",
        "Miff/Views/Alert/DangerSentView.swift": "ma propre alerte : `ownAlertInProgress` retient déjà l'offre"
    ]

    private func presentations(in lines: [String]) -> Int {
        // Les appels sont écrits avec leur parenthèse à part : la règle SwiftLint du texte en dur lirait la fin de la chaîne comme un texte de boîte de dialogue.
        let calls = [".sheet", ".fullScreenCover", ".confirmationDialog", ".alert", ".popover", ".photosPicker"]
        let tokens = calls.map { $0 + "(" } + ["Menu {"]
        return lines.reduce(0) { total, line in
            total + (tokens.contains { line.contains($0) } ? 1 : 0)
        }
    }

    func testEverySheetDialogAlertAndMenuOverTheTabsDeclaresItself() throws {
        for folder in ["Miff/Views/Feed", "Miff/Views/Profile", "Miff/Views/Main"] {
            for path in try swiftFiles(under: folder) where exempted[path] == nil {
                let lines = try code(path)
                let declared = lines.filter { $0.contains(".overlayPresent(") || $0.contains(".reportsMenuArea(") }.count

                XCTAssertGreaterThanOrEqual(
                    declared, presentations(in: lines), "\(path) : une surcouche sur les onglets ne dit pas qu'elle est ouverte"
                )
            }
        }
    }

    func testTheExemptionsAreStillReal() throws {
        for (path, reason) in exempted {
            let lines = try code(path)

            XCTAssertGreaterThan(presentations(in: lines), 0, "\(path) : plus aucune surcouche, retirer l'exemption (\(reason))")
        }
    }

    func testTheReviewWindowAndTheMenusDeclareThemselves() throws {
        let review = try code("Miff/Views/Review/ReviewPromptHost.swift")
        let card = try code("Miff/Views/Profile/TimeShareCard.swift")

        XCTAssertTrue(review.contains { $0.contains(".overlayPresent(coordinator.stage != .hidden)") }, "la fenêtre d'avis")
        XCTAssertTrue(card.contains { $0.contains(".reportsMenuArea()") }, "le menu de « Répartition de ton temps »")
    }

    func testTheHostIsTheLastModifierOfTheHomeSoItsSignalsReachEveryScreen() throws {
        let hosts = try read("Miff/Views/Miff/MiffHosts.swift")
        let review = try XCTUnwrap(hosts.range(of: ".reviewPromptHost(")?.lowerBound)
        let periodic = try XCTUnwrap(hosts.range(of: ".periodicPaywallHost(")?.lowerBound)

        XCTAssertLessThan(review, periodic, "l'hôte de l'offre périodique enveloppe celui de la fenêtre d'avis")
    }

    // MARK: - Chaque entrée de l'offre signale son affichage (PP11)

    func testTheOneEntryPointOfTheOfferResetsTheCounterAtTheDisplay() throws {
        let hosts = try read("Miff/Views/Miff/MiffHosts.swift")
        let view = try code("Miff/Views/Place/PlacePaywallView.swift")

        let call = "onShown: { environment.periodicPaywall.rules.onPaywallDisplayed() }"
        XCTAssertEqual(hosts.components(separatedBy: call).count - 1, 1)
        let trimmed = view.map { $0.trimmingCharacters(in: .whitespaces) }
        XCTAssertEqual(trimmed.filter { $0 == "onShown()" }.count, 1, "à l'affichage effectif, une fois")
        let tracked = try XCTUnwrap(trimmed.firstIndex(of: "tracker.track(event)"))
        XCTAssertEqual(trimmed[tracked + 1], "onShown()", "au même endroit que paywall_shown")
    }

    func testThePeriodicReasonOpensTheSameOfferWithoutAnyContextSentence() throws {
        let screen = try code("Miff/Views/Paywall/PaywallScreen.swift")

        XCTAssertFalse(screen.contains { $0.contains("reason") }, "l'écran d'offre ne dépend d'aucune raison : pas de phrase de contexte")
        XCTAssertEqual(PlacePaywall.trigger(for: .periodic), .periodic)
        XCTAssertEqual(PlacePaywall.shown(for: .periodic)?.params as? [String: String], ["trigger": "periodic"])
        XCTAssertEqual(PlacePaywall.dismissed(for: .periodic), .paywallDismissed(trigger: .periodic))
    }

    // MARK: - Rien ne sort de l'appareil, rien n'est acheté tout seul

    func testThePeriodicOfferSendsNothingLogsNothingAndAsksForNoPermission() throws {
        let forbidden = [
            "Firestore", "Firebase", "URLSession", "Analytics", "tracker", "UserDefaults", "Keychain", "SecItem", "print(", "NSLog",
            "Logger", "os_log", "Crashlytics", "CLLocation", "requestAuthorization", "AVCaptureDevice", "UNUserNotificationCenter"
        ]
        for path in periodicFiles {
            for line in try code(path) {
                for word in forbidden {
                    XCTAssertFalse(line.contains(word), "\(path) : « \(word) » (rien n'est envoyé, journalisé ni demandé) : \(line)")
                }
            }
        }
    }

    func testThePeriodicOfferNeverStartsAPurchaseOrARestoration() throws {
        for path in periodicFiles {
            for line in try code(path) {
                for word in ["startPurchase", "startRestore", "PremiumCoordinator", "StoreKit", "AppStore"] {
                    XCTAssertFalse(line.contains(word), "\(path) : « \(word) » : l'offre périodique ne lance jamais d'achat ni de restauration")
                }
            }
        }
    }

    func testTheCounterIsOneWholeNumberWithNoDateInTheFileStore() throws {
        let lines = try code("Miff/Services/Paywall/PeriodicPaywallFileStore.swift").joined(separator: "\n")

        XCTAssertTrue(lines.contains("private struct Stored: Codable {\n        var openings: Int\n    }"), "un seul entier")
        XCTAssertFalse(lines.contains("Date"), "aucune date")
        XCTAssertTrue(lines.contains("isExcludedFromBackup = true"), "hors de toute sauvegarde")
        XCTAssertTrue(lines.contains("completeFileProtectionUntilFirstUserAuthentication"))
    }

    func testTheMonotonicClockIsTheOneOfTheApp() throws {
        let services = try code("Miff/Services/Paywall/PeriodicPaywallServices.swift").joined(separator: "\n")
        let clock = try code("Miff/Models/Paywall/PeriodicPaywallClock.swift").joined(separator: "\n")

        XCTAssertTrue(services.contains("PeriodicPaywallClock.monotonicMillis"))
        XCTAssertTrue(clock.contains("CLOCK_MONOTONIC"))
        XCTAssertFalse(clock.contains("Date()"), "jamais l'heure murale (PP3)")
        XCTAssertFalse(clock.contains("systemUptime"), "systemUptime s'arrête pendant le sommeil du téléphone")
    }

    // MARK: - Les outils d'essai : Debug et Internal seulement

    func testTheDiagnosticCardAndTheRulesToolsAreInTheTestBuildsOnly() throws {
        let card = try read("Miff/Diagnostic/PeriodicPaywallDebugCard.swift")
        let rules = try read("Miff/Models/Paywall/PeriodicPaywallRules.swift")

        XCTAssertTrue(card.hasPrefix("#if DEBUG || INTERNAL"))
        XCTAssertTrue(card.hasSuffix("#endif\n"))
        XCTAssertTrue(rules.contains("#if DEBUG || INTERNAL\n/// Ce que la carte « Offre périodique (essai) »"))
        XCTAssertTrue(rules.hasSuffix("#endif\n"))
        XCTAssertFalse(rules.components(separatedBy: "#if DEBUG || INTERNAL")[0].contains("setCounterForDiagnostic"), "pas en publication")
    }

    func testTheThresholdCanNotBeLoweredByAnyBuildSetting() throws {
        // Choix de la PR : seul le compteur se pose (carte du diagnostic), jamais le seuil : aucun réglage de build ne peut le baisser (donc rien à neutraliser en publication).
        let services = try code("Miff/Services/Paywall/PeriodicPaywallServices.swift").joined(separator: "\n")

        XCTAssertTrue(services.contains("openingsBetween: Int = PeriodicPaywallDefaults.openingsBetween"))
        XCTAssertFalse(services.contains("Bundle.main"), "le seuil ne se lit nulle part")
        XCTAssertFalse(services.contains("ProcessInfo"), "ni dans l'environnement ni dans les arguments de lancement")
        XCTAssertEqual(PeriodicPaywallDefaults.openingsBetween, 10)
    }
}
// swiftlint:enable line_length
