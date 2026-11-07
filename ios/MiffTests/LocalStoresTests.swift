import XCTest
@testable import Miff

/// Ce que l'appareil garde entre l'inscription et la création du profil : l'acceptation (liée au compte) et le brouillon
/// (stockage privé, jamais d'un autre compte, effacé pour de vrai).
final class LocalStoresTests: XCTestCase {
    private func defaults() throws -> UserDefaults {
        let suiteName = "miff.tests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suiteName))
        addTeardownBlock {
            defaults.removePersistentDomain(forName: suiteName)
        }
        return defaults
    }

    private func temporaryDirectory() -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("miff-tests-\(UUID().uuidString)")
        addTeardownBlock {
            try? FileManager.default.removeItem(at: url)
        }
        return url
    }

    private func draft(uid: String = "uid-1", firstName: String = "Camille") -> ProfileDraft {
        ProfileDraft(
            uid: uid, step: .phone, firstName: firstName, lastName: "Dupont",
            regionCode: "FR", phoneText: "6 12 34", avatar: Data(repeating: 9, count: 500))
    }

    // MARK: - Acceptation en attente

    func testTheAcceptanceIsTiedToTheAccountThatGaveIt() throws {
        let store = UserDefaultsPendingConsentStore(defaults: try defaults())
        let consent = PendingConsent(uid: "uid-1", termsVersion: "0.1", analyticsAccepted: true)

        store.save(consent)

        XCTAssertEqual(store.read(uid: "uid-1"), consent)
        XCTAssertNil(store.read(uid: "uid-2"))
    }

    func testClearingForgetsTheAcceptance() throws {
        let store = UserDefaultsPendingConsentStore(defaults: try defaults())
        store.save(PendingConsent(uid: "uid-1", termsVersion: "0.1", analyticsAccepted: false))

        store.clear()

        XCTAssertNil(store.read(uid: "uid-1"))
    }

    func testTheCarouselFlagIsSetOnce() throws {
        let flags = OnboardingFlags(store: UserDefaultsBooleanStore(defaults: try defaults()))

        XCTAssertFalse(flags.carouselSeen)
        flags.markCarouselSeen()
        XCTAssertTrue(flags.carouselSeen)
    }

    // MARK: - Brouillon

    func testTheDraftComesBackForTheSameAccountOnly() throws {
        let store = ProfileDraftFileStore(directory: temporaryDirectory())
        store.save(draft())

        XCTAssertEqual(store.read(uid: "uid-1"), draft())
        // Un autre compte ne le voit jamais, et l'efface au passage.
        XCTAssertNil(store.read(uid: "uid-2"))
        XCTAssertNil(store.read(uid: "uid-1"))
    }

    func testClearingDeletesTheFileForReal() throws {
        let directory = temporaryDirectory()
        let store = ProfileDraftFileStore(directory: directory)
        store.save(draft())
        XCTAssertNotNil(store.read(uid: "uid-1"))

        store.clear()

        XCTAssertNil(store.read(uid: "uid-1"))
        let remaining = (try? FileManager.default.contentsOfDirectory(atPath: directory.path)) ?? []
        XCTAssertTrue(remaining.isEmpty, "fichiers restants : \(remaining)")
    }

    func testTheLastSaveWins() throws {
        let store = ProfileDraftFileStore(directory: temporaryDirectory())

        store.save(draft(firstName: "A"))
        store.save(draft(firstName: "B"))
        store.save(draft(firstName: "C"))

        XCTAssertEqual(store.read(uid: "uid-1")?.firstName, "C")
    }

    func testACorruptedDraftIsIgnoredAndRemoved() throws {
        let directory = temporaryDirectory()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let file = directory.appendingPathComponent("profile-draft.json")
        try Data("pas du json".utf8).write(to: file)
        let store = ProfileDraftFileStore(directory: directory)

        XCTAssertNil(store.read(uid: "uid-1"))
        XCTAssertFalse(FileManager.default.fileExists(atPath: file.path))
    }

    func testTheDraftFolderIsExcludedFromBackups() throws {
        let directory = temporaryDirectory()
        let store = ProfileDraftFileStore(directory: directory)
        store.save(draft())
        _ = store.read(uid: "uid-1") // attend la fin de l'écriture en arrière-plan

        let values = try directory.resourceValues(forKeys: [.isExcludedFromBackupKey])

        XCTAssertEqual(values.isExcludedFromBackup, true)
    }

    func testTheDraftNeverContainsAPasswordATokenOrAnEmail() throws {
        let directory = temporaryDirectory()
        let store = ProfileDraftFileStore(directory: directory)
        store.save(draft())
        _ = store.read(uid: "uid-1")

        let text = try String(contentsOf: directory.appendingPathComponent("profile-draft.json"), encoding: .utf8)

        for forbidden in ["password", "token", "email", "@"] {
            XCTAssertFalse(text.lowercased().contains(forbidden), forbidden)
        }
    }
}
