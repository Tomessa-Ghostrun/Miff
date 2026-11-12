import XCTest
@testable import Miff

/// Ce que les notifications ne font JAMAIS (ticket §12 et §20, critère N-P0-10) : aucune position, aucune adresse, aucun nom de lieu,
/// aucune écriture sur le serveur, aucun événement Analytics, aucun journal ; aucune capacité de plus (pas de push, pas de pastille,
/// pas d'autorisation provisoire, pas de Time Sensitive). Lit les sources.
final class NotificationPrivacyTests: XCTestCase {
    private var iosRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Notification
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
    }

    private func sources(where isPart: (String) -> Bool) throws -> [(path: String, code: String)] {
        let app = iosRoot.appendingPathComponent("Miff")
        guard let files = FileManager.default.enumerator(at: app, includingPropertiesForKeys: nil) else {
            throw XCTSkip("Sources introuvables.")
        }
        var result: [(path: String, code: String)] = []
        for case let file as URL in files where file.pathExtension == "swift" {
            let relative = String(file.path.dropFirst(app.path.count + 1))
            guard isPart(relative) else {
                continue
            }
            let text = try String(contentsOf: file, encoding: .utf8)
            let code = text.components(separatedBy: "\n")
                .filter { !$0.trimmingCharacters(in: .whitespaces).hasPrefix("//") }
                .joined(separator: "\n")
            result.append((relative, code))
        }
        return result
    }

    private func notificationSources() throws -> [(path: String, code: String)] {
        try sources { path in
            path.hasPrefix("Models/Notification/") || path.hasPrefix("Services/Notification/")
                || path.hasPrefix("Views/Notification/") || path.hasPrefix("ViewModels/Notification")
                || path == "App/NotificationServices.swift" || path == "App/AppEnvironment+Notifications.swift"
        }
    }

    private func offenders(in sources: [(path: String, code: String)], matching words: [String]) -> [String] {
        sources.flatMap { source in
            words.filter { source.code.contains($0) }.map { "\(source.path) : \($0)" }
        }
    }

    func testTheNotificationFilesAreFoundAndLogNothingSendNoEventAndNeverTouchTheServer() throws {
        let found = try notificationSources()
        XCTAssertGreaterThanOrEqual(found.count, 15)
        let forbidden = [
            "print(", "NSLog", "os_log", "Logger(", "debugPrint", "dump(", "Crashlytics", "recordError",
            "FirebaseAnalytics", "logEvent", "AnalyticsEvent", "AnalyticsTracker", "tracker.", "Analytics.log",
            "import FirebaseFirestore", "Firestore.firestore", "DocWrite", "import CoreLocation", "import MapKit"
        ]

        XCTAssertEqual(offenders(in: found, matching: forbidden), [])
    }

    func testNoPositionNoAddressNoPlaceNameNoPhotoInTheNotificationCode() throws {
        let found = try notificationSources()
        let forbidden = [
            "latitude", "longitude", "coordinate", "CLLocation", "address", "adresse", "placeName", "placeLabel",
            "avatarJpeg", "jpeg", "phoneNumber", "E164"
        ]

        XCTAssertEqual(offenders(in: found, matching: forbidden), [])
    }

    func testTheSystemWindowAsksForBannerAndSoundOnly() throws {
        let permissions = try sources { $0 == "Services/Permission/SystemDevicePermissions.swift" }
        let code = try XCTUnwrap(permissions.first?.code)

        XCTAssertTrue(code.contains("requestAuthorization(options: [.alert, .sound])"))
        // (`.provisional` figure dans la lecture de l'ÉTAT du système, pas dans la demande : la demande ci-dessus est exacte.)
        for forbidden in [".badge", ".timeSensitive", ".criticalAlert", ".providesAppNotificationSettings"] {
            XCTAssertFalse(code.contains(forbidden), forbidden)
        }
        let everything = try sources { _ in true }
        XCTAssertEqual(offenders(in: everything, matching: ["timeSensitive", "criticalAlert", "setBadgeCount"]), [])
        XCTAssertEqual(offenders(in: everything, matching: ["registerForRemoteNotifications"]), [])
    }

    func testNoPushCapabilityIsDeclared() throws {
        let entitlements = try String(
            contentsOf: iosRoot.appendingPathComponent("Config/Miff.entitlements"),
            encoding: .utf8
        )
        let project = try String(contentsOf: iosRoot.appendingPathComponent("project.yml"), encoding: .utf8)

        XCTAssertFalse(entitlements.contains("<key>aps-environment</key>"))
        XCTAssertFalse(entitlements.contains("com.apple.developer.usernotifications"))
        XCTAssertFalse(project.contains("remote-notification"))
        XCTAssertFalse(project.contains("aps-environment"))
    }
}
