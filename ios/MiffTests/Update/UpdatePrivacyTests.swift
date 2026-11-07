import XCTest
@testable import Miff

/// Ce que l'Update ne doit JAMAIS faire, vérifié sur les SOURCES pour qu'un changement ultérieur ne le casse pas sans que personne le voie
/// (critères P1, V4, U6, U8, L1, C1 à C4, PR3) : la photo jamais sur le disque ni dans la photothèque, aucun journal ni événement ni rapport de
/// plantage, aucune lecture de position hors du pont du suivi, aucune écriture de `positions/{moi}`, la caméra ouverte seulement par le
/// viseur, aucune alternative « appareil photo du système » (D31), aucun mot « modéré » (D30). Miroir de `UpdatePrivacyTest.kt` d'Android.
final class UpdatePrivacyTests: XCTestCase {
    private var iosRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Update
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
    }

    /// Les fichiers de l'Update (jamais ceux des tests), avec leur chemin relatif à `Miff/`.
    private let updateFiles = [
        "Models/Update/CameraAccess.swift", "Models/Update/JpegMetadata.swift", "Models/Update/PhotoPlace.swift",
        "Models/Update/PublishGuard.swift", "Models/Update/UpdateModels.swift", "Models/Update/UpdatePhotoFormat.swift",
        "Models/Update/UpdatePrompt.swift", "Models/Update/UpdateWrites.swift",
        "Services/Update/CameraController.swift", "Services/Update/CameraPermission.swift",
        "Services/Update/PhotoCleaner.swift", "Services/Update/PhotoEncoder.swift",
        "Services/Update/PhotoPositionSource.swift", "Services/Update/PhotoStore.swift",
        "Services/Update/PublishLedger.swift", "Services/Update/UpdatePublisher.swift",
        "Services/Firebase/FirestorePhotoStore.swift", "Services/Firebase/FirestoreMyStatusObserver.swift",
        "ViewModels/UpdateViewModel.swift", "ViewModels/MyStatusStore.swift", "App/UpdateServices.swift",
        "Views/Update/UpdateComponents.swift", "Views/Update/UpdateBubble.swift",
        "Views/Update/UpdatePreviewView.swift",
        "Views/Update/UpdateTabView.swift", "Views/Update/ViewfinderView.swift"
    ]

    private func code(_ relative: String) throws -> [String] {
        let url = iosRoot.appendingPathComponent("Miff").appendingPathComponent(relative)
        guard FileManager.default.fileExists(atPath: url.path) else {
            XCTFail("Fichier introuvable : \(relative)")
            return []
        }
        return try String(contentsOf: url, encoding: .utf8).components(separatedBy: "\n").filter {
            !$0.trimmingCharacters(in: .whitespaces).hasPrefix("//")
        }
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
        for file in updateFiles {
            XCTAssertFalse(try code(file).isEmpty, file)
        }
    }

    func testNothingIsLoggedReportedSentAsAnEventOrCopied() throws {
        let forbidden = [
            "print(", "NSLog", "os_log", "Logger(", "debugPrint", "dump(", "Crashlytics", "recordError",
            "FirebaseAnalytics",
            "logEvent", "AnalyticsEvent", "AnalyticsTracker", "tracker.", "UIPasteboard", "Analytics.log"
        ]

        XCTAssertEqual(try offenders(in: updateFiles, matching: forbidden), [])
    }

    func testThePhotoNeverReachesTheLibraryNorAFileAndNoStoragePermissionIsUsed() throws {
        let forbidden = [
            "import Photos", "PHPhotoLibrary", "PHAsset", "PHPicker", "UIImageWriteToSavedPhotosAlbum",
            "UIImagePickerController", "NSTemporaryDirectory", "cachesDirectory", "documentDirectory",
            "FileManager.default.createFile", "AVCaptureMovieFileOutput", "AVAudio", "AVCaptureAudio"
        ]

        XCTAssertEqual(try offenders(in: updateFiles, matching: forbidden), [])
    }

    func testOnlyTheLedgerWritesAFileAndItHoldsNoPhoto() throws {
        let fragments = [".write(to", "FileHandle", "OutputStream", "writeToFile"]
        let writers = try offenders(in: updateFiles, matching: fragments)

        XCTAssertTrue(writers.allSatisfy { $0.hasPrefix("Services/Update/PublishLedger.swift") }, "\(writers)")
        let ledger = try code("Services/Update/PublishLedger.swift").joined(separator: "\n")
        XCTAssertFalse(ledger.contains("jpeg"))
    }

    func testThereIsNoAlternativeToTheCameraOfMiffNoCameraApplicationNoLibrary() throws {
        // D31 : un refus de la caméra veut dire « pas de publication » avec les Réglages ; aucune alternative n'est proposée (elles exigent
        // toutes la même autorisation, ou la photothèque, que D16 exclut).
        let screens = [
        "Views/Update/UpdateTabView.swift", "Views/Update/UpdateComponents.swift",
        "Views/Update/UpdatePreviewView.swift"
        ]
        let text = try screens.flatMap { try code($0) }.joined(separator: "\n")

        XCTAssertFalse(text.contains("update_action_phone_camera"))
        XCTAssertFalse(text.contains("UIImagePickerController"))
        XCTAssertFalse(text.contains("PHPicker"))
    }

    func testThePositionIsNotReadHereAndNoPositionDocumentIsWritten() throws {
        let noRead = ["import CoreLocation", "import MapKit", "CLLocation", "requestLocation(", "startUpdatingLocation"]

        XCTAssertEqual(try offenders(in: updateFiles, matching: noRead), [])
        // Le document de la position (`positions/{moi}`) n'est JAMAIS écrit par l'Update (critère U8).
        let positionPaths = try offenders(in: updateFiles, matching: ["/positions/", "\"positions\""])
        XCTAssertEqual(positionPaths, [])
    }

    func testTheCameraIsStartedOnlyByTheViewfinderAndReleasedWhenItGoesOrTheAppLeavesTheForeground() throws {
        let starters = try offenders(in: updateFiles, matching: [".start()"])
        XCTAssertTrue(starters.allSatisfy { $0.hasPrefix("Views/Update/ViewfinderView.swift") }, "\(starters)")
        let viewfinder = try code("Views/Update/ViewfinderView.swift").joined(separator: "\n")
        XCTAssertTrue(viewfinder.contains("camera.stop()"))
        XCTAssertTrue(viewfinder.contains("scenePhase"))
        XCTAssertTrue(viewfinder.contains(".onDisappear"))
        // La capture se fait en mémoire : aucun fichier.
        let controller = try code("Services/Update/CameraController.swift").joined(separator: "\n")
        XCTAssertTrue(controller.contains("fileDataRepresentation()"))
        XCTAssertFalse(controller.contains("AVCaptureFileOutput"))
    }

    func testThePublicationOnlyHappensFromThePreviewAndTheModelNeverPublishesWithoutAPhoto() throws {
        let model = try code("ViewModels/UpdateViewModel.swift").joined(separator: "\n")

        // « Publier » ne s'exécute que sur l'aperçu d'une photo (D16, critère U9).
        let gate = "guard case .preview(let current) = stage, !current.publishing, publishTask == nil"
        XCTAssertTrue(model.contains(gate))
        // Une seule porte vers l'écriture : le publieur, appelé avec la photo de l'aperçu.
        XCTAssertEqual(model.components(separatedBy: "publisher.publish(").count - 1, 1)
        XCTAssertTrue(model.contains("publisher.publish(current.photo"))
    }

    func testNoTextClaimsTheContentIsModeratedOrCheckedAutomatically() throws {
        // D30 : ne jamais écrire « modéré » (ni dans l'app, ni dans les fiches des stores, ni dans les textes).
        let url = iosRoot.appendingPathComponent("Miff/Resources/Localizable.xcstrings")
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        let strings = try XCTUnwrap(root["strings"] as? [String: [String: Any]])
        for (key, entry) in strings where key.hasPrefix("update_") || key.hasPrefix("tabbar_") {
            let localizations = try XCTUnwrap(entry["localizations"] as? [String: [String: Any]])
            for (_, value) in localizations {
                let unit = try XCTUnwrap(value["stringUnit"] as? [String: Any])
                let text = ((unit["value"] as? String) ?? "").lowercased()
                for word in ["modér", "moderat", "vérifié automatiquement", "automatically checked"] {
                    XCTAssertFalse(text.contains(word), "\(key) : \(word)")
                }
            }
        }
    }
}
