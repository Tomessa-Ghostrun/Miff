import XCTest
@testable import Miff

/// Les Réglages RESTREINTS d'un compte sans Miff (décision du PM du 2026-11-07, qui révise la décision Q13) : seulement « Mon compte » et
/// « Confidentialité », et la suppression du compte reste atteignable, et seulement par Réglages > Mon compte.
final class RestrictedSettingsTests: XCTestCase {
    func testAnAccountWithoutMiffSeesOnlyMyAccountAndPrivacy() {
        XCTAssertEqual(SettingsAccess.rows(restricted: true), [.account, .privacy])
    }

    func testAnAccountWithMiffsSeesEveryRow() {
        XCTAssertEqual(
            SettingsAccess.rows(restricted: false),
            [.myInfo, .account, .notifications, .language, .privacy]
        )
    }

    func testMyInformationTheLanguageAndTheNotificationScreensAreTheOnlyOnesARestrictedAccountCannotOpen() {
        for route in SettingsRoute.allCases {
            let forbidden = route == .myInfo || route == .language || route.isNotificationScreen
            XCTAssertEqual(SettingsAccess.isAllowed(route, restricted: true), !forbidden, "\(route)")
            XCTAssertTrue(SettingsAccess.isAllowed(route, restricted: false), "\(route)")
        }
        XCTAssertEqual(
            SettingsRoute.allCases.filter { $0.isNotificationScreen },
            [.notifications, .notificationCenter, .notificationsHelp]
        )
    }

    func testTheNotificationsRowIsAbsentFromTheRestrictedSettings() {
        XCTAssertFalse(SettingsAccess.rows(restricted: true).contains(.notifications))
        XCTAssertFalse(SettingsAccess.reachable(restricted: true).contains(.notifications))
        XCTAssertFalse(SettingsAccess.reachable(restricted: true).contains(.notificationCenter))
        XCTAssertFalse(SettingsAccess.reachable(restricted: true).contains(.notificationsHelp))
    }

    func testTheNotificationScreensAreReachedThroughTheSettingsListAndLeaveWithTheLastMiff() {
        XCTAssertTrue(SettingsAccess.children(of: .settings).contains(.notifications))
        XCTAssertEqual(SettingsAccess.children(of: .notifications), [.notificationCenter, .notificationsHelp])
        var path: [MiffRoute] = [
            .settings(.settings), .settings(.notifications), .settings(.notificationCenter), .miff("abc")
        ]

        path.removeAll { $0.isMyInfoScreen || $0.isNotificationScreen }

        XCTAssertEqual(path, [.settings(.settings), .miff("abc")])
    }

    func testAnAccountWithoutMiffReachesTheDeletionThroughMyAccountOnly() {
        let reachable = SettingsAccess.reachable(restricted: true)

        XCTAssertEqual(reachable, [.settings, .account, .deleteAccount, .privacy, .detectionHelp])
        XCTAssertFalse(reachable.contains(.myInfo))
        // Un seul écran ouvre la suppression : Mon compte.
        let parents = SettingsRoute.allCases.filter { SettingsAccess.children(of: $0).contains(.deleteAccount) }
        XCTAssertEqual(parents, [.account])
    }

    func testAnAccountWithMiffsReachesEveryScreen() {
        XCTAssertEqual(SettingsAccess.reachable(restricted: false), Set(SettingsRoute.allCases))
    }

    func testOnlyMyInformationIsRemovedWhenTheLastMiffDisappears() {
        var path: [MiffRoute] = [.settings(.settings), .settings(.myInfo), .settings(.account), .miff("abc")]

        path.removeAll { $0.isMyInfoScreen }

        XCTAssertEqual(path, [.settings(.settings), .settings(.account), .miff("abc")])
    }

    // MARK: - Les sources

    private var app: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Main
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff")
    }

    private func source(_ relative: String) throws -> String {
        let url = app.appendingPathComponent(relative)
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw XCTSkip("Fichier introuvable (\(url.path)).")
        }
        return try String(contentsOf: url, encoding: .utf8)
    }

    /// La roue crantée n'est posée que sur les écrans SANS Miff (« Pas encore de Miff » et le choix créer ou rejoindre), jamais sur un
    /// écran de compte qui a des Miffs : ceux-là ont la barre d'onglets et le Profil.
    func testTheDiscreetEntryIsOnTheScreensWithoutMiffOnly() throws {
        guard let files = FileManager.default.enumerator(at: app, includingPropertiesForKeys: nil) else {
            throw XCTSkip("Sources introuvables.")
        }
        var users: [String] = []
        for case let file as URL in files where file.pathExtension == "swift" {
            let relative = String(file.path.dropFirst(app.path.count + 1))
            let text = try String(contentsOf: file, encoding: .utf8)
            if text.contains(".restrictedSettingsGear(") && relative != "Views/Settings/RestrictedSettingsLink.swift" {
                users.append(relative)
            }
        }

        // Le Feed (FEED-5) remplace l'accueil provisoire dans la coquille : son état « Pas encore de Miff » porte la même roue crantée.
        XCTAssertEqual(
            users.sorted(),
            ["Views/Feed/FeedScreen.swift", "Views/Miff/MiffChoiceView.swift", "Views/Miff/MiffHomeView.swift"]
        )
    }

    /// Les Réglages restreints se montrent dans la pile EXISTANTE de l'hôte (jamais une pile imbriquée), sans profil lu : aucune lecture de
    /// plus pour un compte qui n'a rien à montrer.
    func testTheOnboardingHostShowsSettingsWithoutProfileAndInItsOwnSingleStack() throws {
        let hosts = try source("Views/Miff/MiffHosts.swift")

        XCTAssertTrue(hosts.contains("profile: nil"))
        XCTAssertTrue(hosts.contains("restricted: true"))
        let stacks = hosts.components(separatedBy: "NavigationStack(path").count - 1
        XCTAssertEqual(stacks, 2, "une pile par hôte, pas de plus")
        let folder = app.appendingPathComponent("Views/Settings")
        let names = (try? FileManager.default.contentsOfDirectory(atPath: folder.path)) ?? []
        for name in names where name.hasSuffix(".swift") {
            XCTAssertFalse(try source("Views/Settings/\(name)").contains("NavigationStack"), name)
        }
    }
}
