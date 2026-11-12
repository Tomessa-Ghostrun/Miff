// Les assertions citent des lignes entières de sources : elles dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// La cloche du Feed doit mener au centre de notifications. Le câblage passe par des écrans liés à l'environnement de l'app, que les tests
/// ne peuvent pas construire : ces contrôles LISENT LES SOURCES, pour qu'une fusion ou un remaniement qui retire une ligne échoue ICI
/// (sur Android, la cloche ne faisait rien parce que l'appel de l'écran du Feed avait oublié un paramètre). Miroir de
/// `NotificationWiringRuleTest.kt` d'Android.
final class NotificationWiringRuleTests: XCTestCase {
    private var iosRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Notification
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
    }

    private func source(_ relative: String) throws -> String {
        let url = iosRoot.appendingPathComponent("Miff").appendingPathComponent(relative)
        guard FileManager.default.fileExists(atPath: url.path) else {
            XCTFail("Fichier introuvable : \(relative)")
            return ""
        }
        let text = try String(contentsOf: url, encoding: .utf8)
        // Sans les commentaires : un commentaire ne câble rien.
        return UpdatePrivacyTests.codeLines(text).joined(separator: "\n")
    }

    /// Le texte d'un appel ou d'un initialiseur : de `opening` jusqu'à la parenthèse fermante qui lui correspond.
    private func call(_ opening: String, in code: String) throws -> String {
        let start = try XCTUnwrap(code.range(of: opening), "\(opening) introuvable")
        var index = start.upperBound
        var depth = 1
        while index < code.endIndex, depth > 0 {
            if code[index] == "(" {
                depth += 1
            } else if code[index] == ")" {
                depth -= 1
            }
            index = code.index(after: index)
        }
        return String(code[start.lowerBound..<index])
    }

    func testTheFeedTabGivesTheNotificationsToTheFeedScreenAndTheBellOpensTheCentre() throws {
        let tab = try source("Views/Feed/FeedTab.swift")
        let screenCall = try call("FeedScreen(", in: tab)

        XCTAssertTrue(screenCall.contains("notificationCenter: notificationCenter"), "le Feed reçoit le centre (le point de la cloche)")
        XCTAssertTrue(screenCall.contains("actions: screenActions"))
        XCTAssertTrue(tab.contains("onOpenNotifications: actions.onOpenNotifications"), "l'onglet transmet la cloche")
        XCTAssertTrue(tab.contains("environment.notifications.center"), "le centre lu est celui de l'application")
    }

    func testTheHostOpensTheCentreAndTheCentreIsInTheSettingsDestinations() throws {
        let hosts = try source("Views/Miff/MiffHosts.swift")
        let destination = try source("Views/Settings/SettingsDestinationView.swift")

        XCTAssertTrue(hosts.contains("onOpenNotifications: { open(.settings(.notificationCenter)) }"), "la cloche ouvre le centre")
        XCTAssertTrue(destination.contains("case .notificationCenter:"))
        XCTAssertTrue(destination.contains("NotificationCenterView("), "la destination construit le centre")
        XCTAssertTrue(destination.contains("onOpenHelp: { navigate(.notificationsHelp) }"), "le refus mène à l'explication calme")
    }

    func testTheHeaderDrawsTheBellWiredToTheCallback() throws {
        let screen = try source("Views/Feed/FeedScreen.swift")
        let header = try source("Views/Feed/FeedHeaderView.swift")

        XCTAssertTrue(screen.contains("onOpenNotifications: actions.onOpenNotifications"), "l'écran donne la cloche à l'en-tête")
        XCTAssertTrue(screen.contains("unreadNotifications: notificationCenter.unreadCount"))
        XCTAssertTrue(header.contains("NotificationBell(unreadCount: unreadNotifications, action: onOpenNotifications)"))
    }

    func testTheCentreAsksTheNativeWindowOrExplainsAndNeverShowsRealLinesWhileLocked() throws {
        let view = try source("Views/Notification/NotificationCenterView.swift")
        let locked = try source("Views/Notification/NotificationCenterLockedView.swift")
        let model = try source("ViewModels/NotificationCenterViewModel.swift")

        XCTAssertTrue(view.contains("NotificationCenterLockedView(lock: lock)"), "verrouillé : le centre flou")
        XCTAssertTrue(view.contains("model.enable(openHelp: actions.onOpenHelp)"), "le bouton est branché")
        XCTAssertTrue(locked.contains(".blur("))
        XCTAssertTrue(locked.contains(".accessibilityHidden(true)"), "les fausses lignes sont cachées à VoiceOver")
        XCTAssertTrue(locked.contains("notif_center_enable_action"))
        XCTAssertTrue(model.contains("await permissions.requestNotifications()"), "la fenêtre native d'iOS")
        // Les vraies lignes ne sont dessinées que sur l'état « autorisées ».
        XCTAssertTrue(view.contains("} else if model.showsContent {"))
        XCTAssertFalse(locked.contains("service.history"), "le flou ne lit jamais les vraies lignes")
    }
}

// swiftlint:enable line_length
