import XCTest
@testable import Miff

/// CONS-4-I : ce qui doit rester vrai du CODE derrière les textes (critères CA-8, CA-12, CA-13), vérifié sur les sources pour qu'un
/// changement ultérieur ne puisse pas le casser sans que personne le voie, et le compteur de la carte « Une précision » (fichier privé,
/// exclu des sauvegardes, jamais envoyé, effacé avec le compte). Miroir de ce que `ConsentV4TextsTest` d'Android vérifie du code.
final class ConsentV4SourceTests: XCTestCase {
    private var iosRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Permission
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
    }

    /// Les lignes de code d'une source de l'app (ni commentaires ni lignes vides).
    private func code(_ relativePath: String) throws -> String {
        let text = try String(contentsOf: iosRoot.appendingPathComponent("Miff/\(relativePath)"), encoding: .utf8)
        return text.components(separatedBy: "\n")
            .filter { !$0.trimmingCharacters(in: .whitespaces).hasPrefix("//") }
            .joined(separator: "\n")
    }

    private func temporaryDirectory() -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("miff-clarify-\(UUID().uuidString)")
        addTeardownBlock {
            try? FileManager.default.removeItem(at: url)
        }
        return url
    }

    // MARK: - CA-8 et CA-12 : la photo et le suivi ne sont gardés que par `coversPosition`, jamais par la version courante

    func testThePhotoAndThePositionAreStillGatedByCoversPositionNeverByTheCurrentVersion() throws {
        // Décision Q1 du PM : un compte resté en version 3 publie toujours une photo avec son endroit ; les règles (03 à 99) ne bougent pas.
        for path in [
            "Services/Firebase/FirestorePhotoStore.swift", "Services/Tracking/TrackingEngine+Refresh.swift",
            "Services/Tracking/TrackingEngine+Travel.swift"
        ] {
            let source = try code(path)
            XCTAssertTrue(source.contains("coversPosition"), "\(path) doit garder `coversPosition`")
            for forbidden in ["MiffSharingConsent.version", "isRenewalDue", "pausesSharing", "isClarificationDue"] {
                XCTAssertFalse(source.contains(forbidden), "\(path) n'exige pas la version courante (\(forbidden))")
            }
        }
        // Le seuil de ce que les règles acceptent n'a pas bougé : 03 à 99.
        XCTAssertTrue(try code("Models/Miff/MiffModels.swift").contains("v(0[3-9]|[1-9][0-9])"))
    }

    func testTheBackendIsNotTouchedByThisTicketSoTheRulesStillAcceptVersion03Writes() throws {
        let rules = try String(
            contentsOf: iosRoot.deletingLastPathComponent().appendingPathComponent("backend/firestore.rules"),
            encoding: .utf8
        )
        // Les seuils de la position et de la photo : « 03 à 99 » (fonctions `isPositionConsentVersion` et `isPhotoLocationConsentVersion`).
        XCTAssertTrue(rules.contains("isPositionConsentVersion"))
        XCTAssertTrue(rules.contains("isPhotoLocationConsentVersion"))
        XCTAssertFalse(rules.contains("2026-11-v04"), "aucune règle ne cite la version 4 : la version 3 écrit toujours")
    }

    // MARK: - Le branchement

    func testTheEnvironmentGivesTheGateTheFileCounterNotTheMemoryOne() throws {
        let environment = try code("App/AppEnvironment.swift")

        XCTAssertTrue(environment.contains("let clarifyAsks: ClarifyAskStore = ClarifyAskFileStore()"))
        XCTAssertTrue(environment.contains("clarifyAsks: clarifyAsks"))
        // Effacé avec le compte.
        let deletion = try code("App/AppEnvironment+AccountDeletion.swift")
        XCTAssertTrue(deletion.contains("clarifyAsks: ClarifyAskFileStore()"))
    }

    func testTheSettingsOfferTheCardOnlyWhenNothingIsReallyPaused() throws {
        let view = try code("Views/Settings/AccountAndPrivacyViews.swift")

        XCTAssertTrue(view.contains("!awaiting.clarifying.isEmpty && awaiting.waiting.isEmpty"))
        XCTAssertTrue(view.contains("awaiting.requestReview()"))
    }

    // MARK: - CA-13 : le compteur reste sur le téléphone

    func testTheCounterOfTheCalmCardUsesNoNetworkNoEventNoLog() throws {
        let source = try code("Services/Miff/ClarifyAskStore.swift")

        let forbidden = [
            "Firebase", "Firestore", "Analytics", "Crashlytics", "print(", "NSLog", "os_log", "Logger", "URLSession"
        ]
        for word in forbidden {
            XCTAssertFalse(source.contains(word), "le compteur de la carte « Une précision » n'utilise pas « \(word) »")
        }
        let screen = try code("Views/Permission/ClarifyScreen.swift")
        for forbidden in ["Firebase", "Analytics", "print(", "NSLog", "CLLocation", "CoreLocation"] {
            XCTAssertFalse(screen.contains(forbidden), forbidden)
        }
    }

    func testTheCounterIsKeptAcrossLaunchesCappedAtThreeAndPerAccount() {
        let directory = temporaryDirectory()
        let store = ClarifyAskFileStore(directory: directory)
        XCTAssertEqual(store.shownCount(uid: "uid-1"), 0)

        for _ in 1...5 {
            store.markShown(uid: "uid-1")
        }
        store.markShown(uid: "uid-2")

        // Un autre « lancement » : une autre instance, le même fichier.
        let again = ClarifyAskFileStore(directory: directory)
        XCTAssertEqual(again.shownCount(uid: "uid-1"), ClarifyPolicy.maxShows, "jamais plus de 3")
        XCTAssertEqual(again.shownCount(uid: "uid-2"), 1)
        XCTAssertFalse(ClarifyPolicy.mayShowByItself(shownCount: again.shownCount(uid: "uid-1")))
    }

    func testTheCounterIsErasedWithTheAccountAndOtherAccountsAreKept() throws {
        let directory = temporaryDirectory()
        let store = ClarifyAskFileStore(directory: directory)
        store.markShown(uid: "uid-1")
        store.markShown(uid: "uid-2")

        store.forget(uid: "uid-1")

        XCTAssertEqual(store.shownCount(uid: "uid-1"), 0)
        XCTAssertEqual(store.shownCount(uid: "uid-2"), 1)
        store.forget(uid: "uid-2")
        XCTAssertFalse(
            FileManager.default.fileExists(atPath: directory.appendingPathComponent("clarify-asks.json").path),
            "plus aucun compte : plus aucun fichier"
        )
    }

    func testTheCounterFileIsExcludedFromBackupsAndHoldsNoNameNoPosition() throws {
        let directory = temporaryDirectory()
        ClarifyAskFileStore(directory: directory).markShown(uid: "uid-1")

        let values = try directory.resourceValues(forKeys: [.isExcludedFromBackupKey])
        XCTAssertEqual(values.isExcludedFromBackup, true, "exclu des sauvegardes iCloud et ordinateur")
        let content = try String(contentsOf: directory.appendingPathComponent("clarify-asks.json"), encoding: .utf8)
        XCTAssertEqual(content, "{\"uid-1\":1}", "un identifiant de compte opaque et un entier, rien d'autre")
    }

    func testAnUnreadableCounterFileIsZeroNotACrash() throws {
        let directory = temporaryDirectory()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try Data("pas du json".utf8).write(to: directory.appendingPathComponent("clarify-asks.json"))

        XCTAssertEqual(ClarifyAskFileStore(directory: directory).shownCount(uid: "uid-1"), 0)
    }
}
