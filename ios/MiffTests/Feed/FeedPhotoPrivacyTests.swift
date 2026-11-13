// Les assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// Ce que la photo du Feed ne doit JAMAIS faire, vérifié sur les SOURCES pour qu'un changement ultérieur ne le casse pas sans que personne le voie
/// (FEED-8, critères V1, Q1, Q2) : la photo jamais sur le disque ni dans la photothèque, aucun journal ni événement, aucune écriture ni suppression
/// de photo depuis le Feed, aucune coordonnée dans un texte parlé ou une `description`, la lecture auprès du serveur d'UN document, l'écran
/// masqué dans le sélecteur d'applications. Miroir de `FeedPrivacyRuleTest.kt` d'Android.
final class FeedPhotoPrivacyTests: XCTestCase {
    private var iosRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Feed
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
    }

    /// Tous les fichiers de la photo du Feed, avec leur chemin relatif à `Miff/`.
    private let photoFiles = [
        "Models/Feed/SharedPhoto.swift", "Models/Feed/PhotoCache.swift", "Models/Feed/PhotoPan.swift",
        "Services/Contracts/FeedPhotoContracts.swift", "Services/Feed/PhotoLoader.swift", "Services/Feed/PhotoDecoder.swift",
        "Services/Firebase/FirestorePhotoSource.swift", "ViewModels/PhotoSlotViewModel.swift",
        "Views/Feed/FeedPhotoViews.swift", "Views/Feed/FeedPhotoFullScreen.swift", "App/AppEnvironment+FeedPhotos.swift",
        "Views/Feed/PhotoSaveButton.swift", "Services/Feed/OwnPositionResolver.swift", "Services/Firebase/FirestoreOwnPositionSource.swift"
    ]

    private func code(_ relative: String) throws -> [String] {
        let url = iosRoot.appendingPathComponent("Miff").appendingPathComponent(relative)
        guard FileManager.default.fileExists(atPath: url.path) else {
            XCTFail("Fichier introuvable : \(relative)")
            return []
        }
        return UpdatePrivacyTests.codeLines(try String(contentsOf: url, encoding: .utf8))
    }

    private func offenders(in files: [String], matching fragments: [String]) throws -> [String] {
        var found: [String] = []
        for file in files {
            for line in try code(file) where fragments.contains(where: { line.contains($0) }) {
                found.append("\(file) : \(line.trimmingCharacters(in: .whitespaces))")
            }
        }
        return found
    }

    func testTheSourcesAreFound() throws {
        for file in photoFiles {
            XCTAssertFalse(try code(file).isEmpty, file)
        }
    }

    func testThePhotoNeverGoesToTheDiskNorToThePhotoLibraryNorToAPasteboard() throws {
        let forbidden = [
            "import Photos", "PHPhotoLibrary", "PHAsset", "PHPicker", "UIImageWriteToSavedPhotosAlbum", "UIImagePickerController",
            "NSTemporaryDirectory", "cachesDirectory", "documentDirectory", "applicationSupportDirectory", "FileManager",
            ".write(", "FileHandle", "OutputStream", "writeToFile", "createFile", "UIPasteboard",
            "ShareLink", "URLCache", "NSCache", "UserDefaults", "SceneStorage", "AppStorage", "NSUbiquitous", "Keychain"
        ]

        XCTAssertEqual(try offenders(in: photoFiles, matching: forbidden), [])
    }

    func testNothingOfAPhotoIsLoggedReportedOrSentAsAnEvent() throws {
        let forbidden = [
            "print(", "NSLog", "os_log", "Logger(", "debugPrint", "dump(", "Crashlytics", "recordError", "FirebaseAnalytics",
            "logEvent", "AnalyticsEvent", "AnalyticsTracker", "tracker.", "Analytics.log"
        ]

        XCTAssertEqual(try offenders(in: photoFiles, matching: forbidden), [])
    }

    func testTheFeedNeverWritesNorDeletesAPhoto() throws {
        let forbidden = [
            ".setData", ".updateData", ".delete()", ".addDocument", "runTransaction", "batch()", "writeBatch", "DocWrite", "FieldValue",
            "UpdateWrites", "PhotoStore", "PhotoCleaner"
        ]

        XCTAssertEqual(try offenders(in: photoFiles, matching: forbidden), [])
    }

    func testOnlyAServerReadOfOneDocumentIsDoneNeverAListNorAListener() throws {
        let source = try code("Services/Firebase/FirestorePhotoSource.swift").joined(separator: "\n")

        XCTAssertTrue(source.contains("getDocument(source: .server)"))
        XCTAssertEqual(source.components(separatedBy: "getDocument(").count - 1, 1, "UN seul get")
        for forbidden in ["getDocuments", "addSnapshotListener", ".whereField", ".limit("] {
            XCTAssertFalse(source.contains(forbidden), forbidden)
        }
        XCTAssertTrue(source.contains("MiffId.isValid(miffId)"))
        XCTAssertTrue(source.contains("FeedFirestoreMapping.isUid(uid)"))
    }

    func testMyOwnPositionIsReadOnceFromTheServerOnlyItsExistenceAndNeverAField() throws {
        let source = try code("Services/Firebase/FirestoreOwnPositionSource.swift").joined(separator: "\n")

        XCTAssertTrue(source.contains("getDocument(source: .server)"))
        XCTAssertEqual(source.components(separatedBy: "getDocument(").count - 1, 1, "UN seul get")
        XCTAssertTrue(source.contains("snapshot.exists"))
        for forbidden in ["getDocuments", "addSnapshotListener", ".whereField", ".limit(", ".data()"] {
            XCTAssertFalse(source.contains(forbidden), forbidden)
        }
        XCTAssertTrue(source.contains("MiffId.isValid(miffId)"))
        XCTAssertTrue(source.contains("FeedFirestoreMapping.isUid(uid)"))
    }

    func testTheSentenceIsOnlyEverTheAnswerOfANotPublishedPositionConfirmedByTheServer() throws {
        let loader = try code("Services/Feed/PhotoLoader.swift").joined(separator: "\n")

        // Trois endroits, tous sous « non publiée » : la mémoire (réponse du serveur de moins de 30 s), le refus déjà connu, le refus du moment.
        XCTAssertEqual(loader.components(separatedBy: "return .needsOwnPosition").count - 1, 3)
        XCTAssertEqual(loader.components(separatedBy: "== .notPublished").count - 1, 1)
        XCTAssertEqual(loader.components(separatedBy: "case .notPublished:").count - 1, 2)
    }

    func testARefusalIsNeverTakenForAnExpiredPhotoNothingIsDeletedOnIt() throws {
        let loader = try code("Services/Feed/PhotoLoader.swift").joined(separator: "\n")

        XCTAssertTrue(loader.contains("case .refused:"))
        XCTAssertTrue(loader.contains("return await refusal(for: request, startedIn: startedIn)"))
        XCTAssertFalse(loader.contains("delete"))
    }

    func testNoCoordinateIsPutInATextADescriptionOrASpokenName() throws {
        // Les lignes de code qui touchent au lieu de la photo : il n'est donné qu'à `PositionFix` (Plans, coordonnées seules).
        let views = try ["Views/Feed/FeedPhotoViews.swift", "Views/Feed/FeedPhotoFullScreen.swift"].flatMap { try code($0) }
        let usesOfCoordinates = views.filter { $0.contains(".lat") || $0.contains(".lng") }

        XCTAssertEqual(usesOfCoordinates.count, 1, "\(usesOfCoordinates.count)")
        XCTAssertTrue(usesOfCoordinates[0].contains("PositionFix(lat: place.lat, lng: place.lng)"))
        for file in photoFiles {
            let lines = try code(file)
            for (index, line) in lines.enumerated() where line.contains("var description") {
                let body = lines[index..<min(index + 3, lines.count)].joined(separator: " ")
                XCTAssertFalse(body.contains("lat") || body.contains("lng") || body.contains("jpeg"), "\(file) : \(body)")
                XCTAssertTrue(body.contains("redacted"), "\(file) : \(body)")
            }
        }
    }

    func testTheFullScreenAndTheTabHideInTheAppSwitcherAndTheFullScreenClosesWhenTheFeedLeavesTheScreen() throws {
        let fullScreen = try code("Views/Feed/FeedPhotoFullScreen.swift").joined(separator: "\n")
        XCTAssertTrue(fullScreen.contains(".hidesInAppSwitcher()"))
        let screen = try code("Views/Feed/FeedScreen.swift").joined(separator: "\n")
        XCTAssertTrue(screen.contains(".fullScreenCover(item: $openedPhoto)"))
        XCTAssertTrue(screen.contains("openedPhoto = nil"))
        let tab = try code("Views/Feed/FeedTab.swift").joined(separator: "\n")
        XCTAssertTrue(tab.contains(".hidesInAppSwitcher()"))
    }

    func testThePhotoIsReadOnlyWhenTheCardAppearsAndWhileTheTabIsInUse() throws {
        let views = try code("Views/Feed/FeedPhotoViews.swift").joined(separator: "\n")

        XCTAssertTrue(views.contains(".task(id: PhotoTaskKey("))
        XCTAssertTrue(views.contains("guard context.active, !context.offline"), "rien hors du Feed en usage ni hors connexion")
        XCTAssertTrue(views.contains("reader.forget(miffId: context.miffId, uid: card.member.uid)"))
        XCTAssertEqual(views.components(separatedBy: ".load(").count - 1, 1, "une seule porte vers la lecture")
        let card = try code("Views/Feed/FeedCardView.swift").joined(separator: "\n")
        XCTAssertTrue(card.contains(".loadsPhoto(of: card, in: photoContext)"))
    }

    func testTheMemoryOfThePhotosIsEmptiedAtTheEndOfTheSession() throws {
        let services = try code("App/UpdateServices.swift").joined(separator: "\n")

        XCTAssertTrue(services.contains("photoCache.clear()"))
    }
}
// swiftlint:enable line_length
