// Les assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// La DESCRIPTION d'une update (ticket description-update, critères V1 à V5, F3, F10), gardée sur les SOURCES pour qu'un changement ultérieur ne la
/// casse pas sans que personne le voie. Une description est un texte libre qui peut contenir des données personnelles : elle vit en mémoire, va
/// dans Firestore (le document de la photo) et nulle part ailleurs, et se dessine en texte brut. Miroir de `DescriptionPrivacyRuleTest.kt` d'Android.
final class DescriptionPrivacyTests: XCTestCase {
    private var app: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Update
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff")
    }

    /// Les fichiers qui portent la description, avec leur chemin relatif à `Miff/`.
    private let carriers = [
        "Models/Update/PhotoDescription.swift", "Views/Update/DescriptionField.swift", "Views/Update/UpdatePreviewView.swift",
        "ViewModels/UpdateViewModel.swift", "Models/Update/UpdateWrites.swift", "Models/Feed/SharedPhoto.swift",
        "Services/Update/UpdatePublisher.swift", "Views/Feed/FeedPhotoFullScreen.swift",
        "Services/Firebase/FirestorePhotoSource.swift"
    ]

    private func code(_ relative: String) throws -> String {
        let url = app.appendingPathComponent(relative)
        guard FileManager.default.fileExists(atPath: url.path) else {
            XCTFail("Fichier introuvable : \(relative)")
            return ""
        }
        return UpdatePrivacyTests.codeLines(try String(contentsOf: url, encoding: .utf8)).joined(separator: "\n")
    }

    private func offenders(in files: [String], matching fragments: [String]) throws -> [String] {
        try files.filter { file in
            let text = try code(file)
            return fragments.contains { text.contains($0) }
        }
    }

    func testTheCarriersAreFound() throws {
        for file in carriers {
            XCTAssertFalse(try code(file).isEmpty, file)
        }
    }

    func testTheDescriptionIsNeverLoggedSentAsAnEventReportedInACrashOrCopied() throws {
        let forbidden = [
            "print(", "NSLog", "os_log", "Logger(", "debugPrint", "dump(", "Crashlytics", "recordError", "FirebaseAnalytics",
            "logEvent", "AnalyticsEvent", "AnalyticsTracker", "tracker.", "UIPasteboard", "Analytics.log"
        ]

        XCTAssertEqual(try offenders(in: carriers, matching: forbidden), [])
    }

    func testTheDraftIsNeverSavedByTheSystemNorOnTheDisk() throws {
        let forbidden = [
            "SceneStorage", "AppStorage", "UserDefaults", "NSUbiquitous", "FileManager", ".write(", "NSCache", "URLCache"
        ]

        XCTAssertEqual(try offenders(in: ["Views/Update/DescriptionField.swift", "ViewModels/UpdateViewModel.swift"], matching: forbidden), [])
    }

    func testNoNotificationCodeEverReadsTheDescription() throws {
        let folders = ["Models/Notification", "Services/Notification"]
        var files = ["App/NotificationServices.swift", "App/AppEnvironment+Notifications.swift", "ViewModels/NotificationCenterViewModel.swift"]
        for folder in folders {
            let names = (try? FileManager.default.contentsOfDirectory(atPath: app.appendingPathComponent(folder).path)) ?? []
            files += names.filter { $0.hasSuffix(".swift") }.map { "\(folder)/\($0)" }
        }

        XCTAssertGreaterThan(files.count, 10)
        XCTAssertEqual(try offenders(in: files, matching: ["PhotoDescription", "descriptionText", "photoDescription"]), [])
    }

    func testTheFullScreenDrawsPlainTextNoLinkNoSelectionNoMarkdown() throws {
        let text = try code("Views/Feed/FeedPhotoFullScreen.swift")
        let forbidden = ["Link(", "AttributedString", "LocalizedStringKey", ".textSelection", "dataDetector", "UITextView", "openURL"]

        XCTAssertTrue(text.contains("Text(verbatim: description)"), "le texte est dessiné en « verbatim » : jamais interprété")
        for fragment in forbidden {
            XCTAssertFalse(text.contains(fragment), fragment)
        }
    }

    func testSavingThePhotoNeverSavesTheDescription() throws {
        let saving = ["Services/Feed/PhotoLibrarySaver.swift", "Views/Feed/PhotoSaveButton.swift"]

        XCTAssertEqual(try offenders(in: saving, matching: ["PhotoDescription", "descriptionText"]), [])
    }

    func testOnlyTheDocumentOfThePhotoCarriesTheDescriptionTheStatusBuilderNeverDoes() throws {
        let writes = try code("Models/Update/UpdateWrites.swift")
        let start = try XCTUnwrap(writes.range(of: "static func photoStatus("))
        let end = try XCTUnwrap(writes.range(of: "private static func battery("))
        let status = String(writes[start.lowerBound..<end.lowerBound])

        XCTAssertFalse(status.contains("description"))
        XCTAssertFalse(status.contains("PhotoDescription"))
    }

    func testTheKeyboardActionIsDoneNeverASendAndTheFieldIsNotAnAlternativeToThePublishButton() throws {
        let field = try code("Views/Update/DescriptionField.swift")

        XCTAssertTrue(field.contains(".submitLabel(.done)"))
        XCTAssertFalse(field.contains(".submitLabel(.send)") || field.contains(".submitLabel(.go)"))
        // Le champ ne publie rien : aucune action de publication ne lui est donnée.
        XCTAssertFalse(field.contains("onPublish"))
    }

    func testTheModelPublishesTheDescriptionOfThePreviewThroughTheOnlyDoorToTheWrite() throws {
        let model = try code("ViewModels/UpdateViewModel.swift")

        XCTAssertTrue(model.contains("let text = current.descriptionText"))
        XCTAssertTrue(model.contains("publisher.publish(current.photo, only: only, description: text)"))
        XCTAssertEqual(model.components(separatedBy: "publisher.publish(").count - 1, 1)
    }
}
// swiftlint:enable line_length
