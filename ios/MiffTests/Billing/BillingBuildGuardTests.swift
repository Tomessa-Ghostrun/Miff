// Quelques assertions portent un message en clair : elles dépassent la limite de ligne.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// Ce que l'achat de Miff+ doit TOUJOURS garantir, vérifié sur les SOURCES et sur `project.yml` pour qu'un changement ultérieur ne le casse pas sans que personne le voie (ticket
/// `docs/tickets/premium-achat-reel.md` §11 et critères AN15, IO2, IO6) : le faux magasin n'existe pas hors des builds de test, le vrai achat est éteint par défaut, rien ne se
/// journalise, aucune clé n'est dans l'app, et seul le serveur accorde. La preuve sur le BINAIRE de publication est faite par la CI (`ci/check-binaries.sh`).
final class BillingBuildGuardTests: XCTestCase {
    private var iosRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Billing
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

    /// Les lignes de code (commentaires exclus) des sources de l'achat.
    private func billingCode() throws -> [(path: String, lines: [String])] {
        let folders = ["Miff/Services/Billing", "Miff/Models/Billing", "Miff/Models/Subscription"]
        let singles = [
            "Miff/Services/Firebase/FirestoreSubscriptionStore.swift", "Miff/ViewModels/PaywallStoreDriver.swift",
            "Miff/ViewModels/SubscriptionViewModel.swift", "Miff/App/PremiumServices.swift"
        ]
        var paths = singles
        for folder in folders {
            let url = iosRoot.appendingPathComponent(folder)
            let names = (try? FileManager.default.contentsOfDirectory(atPath: url.path)) ?? []
            paths += names.filter { $0.hasSuffix(".swift") }.map { "\(folder)/\($0)" }
        }
        XCTAssertGreaterThan(paths.count, 10, "presque aucune source trouvée : chemin cassé ?")
        return try paths.map { path in
            let lines = try read(path).components(separatedBy: "\n")
            return (path, lines.filter { !$0.trimmingCharacters(in: .whitespaces).hasPrefix("//") })
        }
    }

    // MARK: - Le faux magasin n'existe que dans les builds de test

    func testTheFakeStoreIsCompiledOnlyUnderItsFlagAndRefusesToCompileInARelease() throws {
        let fake = try read("Miff/Services/Billing/FakeBilling.swift")

        XCTAssertTrue(fake.hasPrefix("#if BILLING_FAKE"), "tout le fichier est derrière le drapeau")
        XCTAssertTrue(fake.contains("#if !(DEBUG || INTERNAL)"), "garde-fou de compilation")
        XCTAssertTrue(fake.contains("#error("), "une configuration de publication qui poserait le drapeau ne compile pas")
        XCTAssertTrue(fake.contains("FAKEBILLINGPROBE"), "le marqueur que la CI cherche dans le binaire de publication")
        XCTAssertTrue(fake.hasSuffix("#endif\n"))
    }

    func testTheFakeStoreIsChosenOnlyThroughTheFlag() throws {
        let services = try read("Miff/App/PremiumServices.swift")

        XCTAssertTrue(services.contains("#if BILLING_FAKE\n        return FakeBilling.stack()"))
        XCTAssertTrue(services.contains("#elseif PREMIUM_PURCHASE"))
        for (path, lines) in try billingCode() where !path.hasSuffix("FakeBilling.swift") && !path.hasSuffix("PremiumServices.swift") {
            XCTAssertFalse(lines.contains { $0.contains("FakeBilling") }, "\(path) : un chemin vers le faux magasin ?")
        }
    }

    // MARK: - Le vrai achat est éteint par défaut

    func testTheRealStoreAndTheServerCallsAreCompiledOnlyUnderThePurchaseFlag() throws {
        for path in ["Miff/Services/Billing/StoreKitBillingGateway.swift", "Miff/Services/Billing/FirebasePurchaseBackend.swift"] {
            let text = try read(path)

            XCTAssertTrue(text.hasPrefix("#if PREMIUM_PURCHASE"), path)
            XCTAssertTrue(text.hasSuffix("#endif\n"), path)
            XCTAssertTrue(text.contains("PREMIUMPURCHASEPROBE"), "\(path) : le marqueur de la CI")
        }
    }

    func testTheProjectTurnsThePurchaseOffByDefaultAndTheFakeStoreNeverInARelease() throws {
        let project = try read("project.yml")
        let conditions = project.components(separatedBy: "\n").filter { line in
            !line.trimmingCharacters(in: .whitespaces).hasPrefix("#") && line.contains("SWIFT_ACTIVE_COMPILATION_CONDITIONS")
        }

        XCTAssertEqual(conditions.count, 3, "Debug, Internal, Release")
        XCTAssertTrue(conditions[0].contains("PREMIUM_PURCHASE BILLING_FAKE"), "Debug : l'achat et le faux magasin")
        XCTAssertFalse(conditions[1].contains("BILLING_FAKE") || conditions[1].contains("PREMIUM_PURCHASE"), "Internal : rien par défaut")
        XCTAssertTrue(conditions[1].contains("$(MIFF_PREMIUM_CONDITIONS)"))
        XCTAssertFalse(conditions[2].contains("BILLING_FAKE") || conditions[2].contains("PREMIUM_PURCHASE"), "Release : rien par défaut")
        XCTAssertTrue(conditions[2].contains("$(MIFF_PREMIUM_CONDITIONS)"))
        XCTAssertTrue(project.contains("MIFF_PREMIUM_CONDITIONS: \"\""), "vide par défaut : éteint")
        XCTAssertTrue(project.contains("product: FirebaseFunctions"))
    }

    func testTheTestFlightWorkflowTurnsThePurchaseOnOnlyOnAnExplicitChoiceAndOffByDefault() throws {
        let workflow = try read("../.github/workflows/ios-testflight.yml")

        XCTAssertTrue(workflow.contains("premium_purchase:"))
        XCTAssertTrue(workflow.contains("default: \"off\""), "éteint par défaut")
        XCTAssertTrue(workflow.contains("MIFF_PREMIUM_CONDITIONS"))
    }

    // MARK: - Rien ne se journalise, aucune clé, seul le serveur accorde

    func testNothingOfThePurchaseIsLoggedOrSentAsAnEventOrKept() throws {
        let forbidden = [
            "print(", "debugPrint(", "NSLog", "os_log", "Logger(", "dump(", "AnalyticsEvent", "tracker", "UserDefaults",
            "Keychain", "SecItem", "applicationSupportDirectory", "documentDirectory", "cachesDirectory", "write(to"
        ]
        for (path, lines) in try billingCode() {
            for line in lines {
                for word in forbidden {
                    XCTAssertFalse(line.contains(word), "\(path) : « \(word) » (jeton, transaction et identifiant ne sont ni journalisés ni gardés)")
                }
            }
        }
    }

    func testNoKeyNorSecretIsInTheApp() throws {
        let forbidden = ["BEGIN PRIVATE KEY", ".p8", "sharedSecret", "shared_secret", "AuthKey", "serviceAccount", "client_secret"]
        for (path, lines) in try billingCode() {
            for line in lines {
                for word in forbidden {
                    XCTAssertFalse(line.contains(word), "\(path) : « \(word) » (aucune clé dans l'app, ticket §11)")
                }
            }
        }
    }

    func testTheAppNeverWritesTheRightsAndOnlyReadsThem() throws {
        for (path, lines) in try billingCode() {
            for line in lines {
                for call in ["setData(", "updateData(", ".deleteDocument(", "runTransaction", "DocWrite("] {
                    XCTAssertFalse(line.contains(call), "\(path) : \(call) (seul le serveur écrit entitlements/{uid})")
                }
            }
        }
        let store = try read("Miff/Services/Firebase/FirestoreSubscriptionStore.swift")
        XCTAssertTrue(store.contains("getDocument(source: .server)"), "auprès du serveur, jamais du cache")
    }

    func testTheOnlyIdentifierGivenToTheStoreIsTheRandomIdOfTheServer() throws {
        let gateway = try read("Miff/Services/Billing/StoreKitBillingGateway.swift")

        XCTAssertEqual(gateway.components(separatedBy: ".appAccountToken(").count - 1, 1)
        XCTAssertTrue(gateway.contains("UUID(uuidString: purchaseId)"))
        let code = gateway.components(separatedBy: "\n").filter { !$0.trimmingCharacters(in: .whitespaces).hasPrefix("//") }
        for line in code {
            XCTAssertNil(line.range(of: "\\b(currentUid|email|uid)\\b", options: .regularExpression), "un identifiant de compte donné au store ? \(line)")
        }
    }

    func testTheSignedTransactionLeavesOnlyThroughTheSynchronisationFunction() throws {
        let backend = try read("Miff/Services/Billing/FirebasePurchaseBackend.swift")

        XCTAssertEqual(backend.components(separatedBy: "\"signedTransaction\"").count - 1, 1)
        XCTAssertEqual(backend.components(separatedBy: "\"app_store\"").count - 1, 1)
        XCTAssertTrue(backend.contains("static let region = \"europe-west9\""))
        XCTAssertTrue(backend.contains("static let prepare = \"preparerAchat\""))
        XCTAssertTrue(backend.contains("static let synchronize = \"synchroniserAchat\""))
        XCTAssertTrue(backend.contains("static let purge = \"supprimerDonneesAbonnement\""))
    }

    func testTheCoordinatorFinishesATransactionOnlyAfterTheServerAnswered() throws {
        let coordinator = try read("Miff/Services/Billing/PremiumCoordinator.swift")

        let code = coordinator.components(separatedBy: "\n")
            .filter { !$0.trimmingCharacters(in: .whitespaces).hasPrefix("//") }.joined(separator: "\n")
        XCTAssertEqual(code.components(separatedBy: "await transaction.finish()").count - 1, 3, "après `.synced`, jamais ailleurs")
        XCTAssertFalse(code.contains("Transaction.currentEntitlements"), "StoreKit n'est touché que par la passerelle")
        XCTAssertFalse(code.contains("AppStore.sync"))
    }

    // MARK: - La suppression du compte

    func testTheAccountDeletionPurgesThePurchaseDataFirstAndBoundedTo8SecondsAndToleratesAFailure() throws {
        let store = try read("Miff/Services/Firebase/FirestoreAccountDataStore.swift")

        let purge = try XCTUnwrap(store.range(of: "subscriptionData {"))
        let journal = try XCTUnwrap(store.range(of: "await journal?.deleteAll"))
        XCTAssertLessThan(purge.lowerBound, journal.lowerBound, "avant le profil (la fonction exige la connexion)")
        XCTAssertTrue(store.contains("static let subscriptionPurgeTimeout: TimeInterval = 8"))
        XCTAssertTrue(store.contains("_ = try? await timedOperation(seconds: Self.subscriptionPurgeTimeout)"), "échec toléré")
        let wiring = try read("Miff/App/AppEnvironment+AccountDeletion.swift")
        XCTAssertTrue(wiring.contains("subscriptionData: environment.premium.purgeSubscriptionData"))
        let texts = try read("Miff/Resources/Localizable.xcstrings")
        XCTAssertFalse(texts.contains("settings_delete_sub_"), "aucun rappel d'abonnement à la suppression (décision du PM du 2026-11-21)")
    }

    // MARK: - L'écoute des transactions dès le lancement (IO2)

    func testTheListenerIsStartedWhenThePurchaseServicesAreBuiltAtLaunch() throws {
        let services = try read("Miff/App/PremiumServices.swift")
        let environment = try read("Miff/App/AppEnvironment.swift")

        XCTAssertTrue(services.contains("coordinator.startListening()"))
        XCTAssertTrue(environment.contains("let premiumServices = PremiumServices.make("), "créé dans l'init de l'environnement, avant tout écran")
    }

    func testTheRestorationNeverStartsFromTheLifecycle() throws {
        let lifecycle = try read("Miff/Views/PremiumLifecycle.swift")

        XCTAssertTrue(lifecycle.contains("coordinator.reconcile()"))
        XCTAssertFalse(lifecycle.contains("startRestore"), "AppStore.sync demande l'identification Apple : jamais tout seul (IO3)")
    }
}
// swiftlint:enable line_length
