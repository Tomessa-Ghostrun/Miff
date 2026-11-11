import XCTest
@testable import Miff

/// Contrôles sur les SOURCES de « Mes informations » (comme `SessionWriteSitesTests`) : les garanties du ticket
/// `docs/tickets/modifier-infos-perso.md` qu'aucun test de comportement ne voit (aucune trace dans un journal ou une analyse, aucune
/// caméra, aucune nouvelle permission, aucune écriture hors des deux documents du profil).
final class MyInfoSourcesTests: XCTestCase {
    private let files = [
        "Views/Settings/MyInfoView.swift",
        "ViewModels/MyInfoViewModel.swift",
        "Models/Profile/MyInfoState.swift",
        "Services/Profile/StoreMyProfileRepository.swift",
        "Services/Contracts/MyProfileContracts.swift"
    ]

    private var appSources: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Main
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff")
    }

    /// Le code du fichier, sans les lignes de commentaire.
    private func code(_ relative: String) throws -> String {
        let url = appSources.appendingPathComponent(relative)
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw XCTSkip("Sources introuvables (\(url.path)).")
        }
        return try String(contentsOf: url, encoding: .utf8)
            .components(separatedBy: "\n")
            .filter { !$0.trimmingCharacters(in: .whitespaces).hasPrefix("//") }
            .joined(separator: "\n")
    }

    func testNoNameNumberOrPhotoCanReachALogOrAnAnalyticsEvent() throws {
        let forbidden = [
            "print(", "NSLog(", "os_log", "Logger(", "debugPrint(", "dump(", "Crashlytics", "Analytics",
            "tracker", "track(", "UserDefaults", "FileManager", "write(to"
        ]
        for file in files {
            let text = try code(file)
            for word in forbidden {
                XCTAssertFalse(text.contains(word), "\(file) contient « \(word) »")
            }
        }
    }

    func testThereIsNoCameraAndNoPhotoLibraryPermissionInThisScreen() throws {
        let screen = try code("Views/Settings/MyInfoView.swift")

        for word in ["UIImagePickerController", "AVCaptureDevice", "AVFoundation", "PHPhotoLibrary", "PHAsset"] {
            XCTAssertFalse(screen.contains(word), word)
        }
        // Le sélecteur de photos du SYSTÈME : l'app ne reçoit que la photo choisie, sans permission.
        XCTAssertTrue(screen.contains(".photosPicker("))
    }

    func testTheRepositoryOnlyWritesTheProfileAndTheCopyOfEachMiff() throws {
        let text = try code("Services/Profile/StoreMyProfileRepository.swift")

        XCTAssertTrue(text.contains("\"users/\\(uid)\""))
        XCTAssertTrue(text.contains("\"miffs/\\(miffId)/members/\\(uid)\""))
        let forbiddenPaths = ["/status/", "/positions/", "/photos/", "/memberships/"]
        let forbiddenKeys = ["\"email\"", "termsVersion", "createdAt", "analyticsConsent"]
        for forbidden in forbiddenPaths + forbiddenKeys {
            XCTAssertFalse(text.contains(forbidden), forbidden)
        }
        // Une transaction par lot (le magasin des Miffs), jamais une écriture simple qui resterait en file hors connexion.
        XCTAssertFalse(text.contains("setData("))
        XCTAssertFalse(text.contains("updateData("))
    }

    func testTheScreenAsksBeforeLeavingWithUnsavedChangesAndLocksTheSwipe() throws {
        let text = try code("Views/Settings/MyInfoView.swift")

        XCTAssertTrue(text.contains(".swipeBack(for: .myInfo("))
        XCTAssertTrue(text.contains("settings_my_info_discard_title"))
    }

    func testTheOldReadOnlySentenceIsGoneFromTheCatalog() throws {
        let catalog = appSources.appendingPathComponent("Resources/Localizable.xcstrings")
        guard FileManager.default.fileExists(atPath: catalog.path) else {
            throw XCTSkip("Catalogue introuvable.")
        }
        let text = try String(contentsOf: catalog, encoding: .utf8)

        XCTAssertFalse(text.contains("ios_myinfo_edit_soon"))
    }
}
