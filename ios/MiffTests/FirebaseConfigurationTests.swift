import XCTest
@testable import Miff

/// La configuration Firebase embarquée dans le build de CI (recréée depuis le secret GitHub) vise le bon projet.
final class FirebaseConfigurationTests: XCTestCase {
    private func embeddedConfiguration() throws -> [String: Any] {
        guard let url = Bundle.main.url(forResource: "GoogleService-Info", withExtension: "plist") else {
            throw XCTSkip("GoogleService-Info.plist absent de ce build (poste sans configuration Firebase).")
        }
        let data = try Data(contentsOf: url)
        return try XCTUnwrap(PropertyListSerialization.propertyList(from: data, format: nil) as? [String: Any])
    }

    func testEmbeddedConfigurationTargetsTheMiffProject() throws {
        let configuration = try embeddedConfiguration()
        let projectId = configuration["PROJECT_ID"] as? String
        if projectId == "fake-ci-config-do-not-use" {
            throw XCTSkip("Configuration factice (exécution de Dependabot) : aucun vrai projet à vérifier.")
        }

        XCTAssertEqual(projectId, expectedFirebaseProjectId)
        XCTAssertEqual(configuration["BUNDLE_ID"] as? String, "com.tomessa.miff")
    }

    func testEmbeddedConfigurationNeverTargetsTheOtherProject() throws {
        let configuration = try embeddedConfiguration()
        let identifiers = ["PROJECT_ID", "STORAGE_BUCKET", "GOOGLE_APP_ID"].compactMap { configuration[$0] as? String }

        XCTAssertFalse(identifiers.contains { $0.lowercased().contains("ghostrun") })
    }

    func testUnitTestsDoNotStartFirebase() {
        // Les tests n'ont besoin d'aucun réseau et n'envoient rien : Firebase n'est pas configuré dans ce processus.
        XCTAssertTrue(FirebaseBootstrap.isRunningUnitTests)
        XCTAssertFalse(FirebaseBootstrap.isConfigured)
    }
}
