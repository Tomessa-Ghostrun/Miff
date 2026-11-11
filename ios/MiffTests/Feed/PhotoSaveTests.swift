// Les assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import ImageIO
import Photos
import XCTest
@testable import Miff

/// Un faux ajout à la galerie : il compte ses demandes et ses écritures, et ne touche à rien.
@MainActor
final class FakePhotoSaver: PhotoSaving {
    var current = PhotoSaveAccess.notDetermined
    var afterRequest = PhotoSaveAccess.allowed
    var saves = true
    private(set) var requests = 0
    private(set) var written: [Data] = []

    func access() -> PhotoSaveAccess {
        current
    }

    func requestAccess() async -> PhotoSaveAccess {
        requests += 1
        current = afterRequest
        return afterRequest
    }

    func save(jpeg: Data) async -> Bool {
        written.append(jpeg)
        return saves
    }
}

/// « Enregistrer cette photo » dans le plein écran de la photo d'un proche (décision du PM du 2026-11-11 : DIRECTEMENT dans la galerie, au toucher,
/// autorisation d'AJOUT seul) : jamais d'écriture sans toucher, un refus se dit calmement sans rien écrire, l'image enregistrée n'a aucune métadonnée,
/// la galerie n'est jamais lue, une seule clé de photothèque (l'ajout), et, par lecture des sources, ce bouton n'existe qu'à cet endroit.
@MainActor
final class PhotoSaveTests: XCTestCase {
    private let saver = FakePhotoSaver()
    private let image = UIImage(data: PhotoFixtures.jpeg()) ?? UIImage()

    private var iosRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Feed
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
    }

    private func text(_ relative: String) throws -> String {
        try String(contentsOf: iosRoot.appendingPathComponent(relative), encoding: .utf8)
    }

    private func code(_ relative: String) throws -> String {
        UpdatePrivacyTests.codeLines(try text(relative)).joined(separator: "\n")
    }

    private func model() -> PhotoSaveViewModel {
        PhotoSaveViewModel(saver: saver, image: image)
    }

    // MARK: - Jamais sans toucher

    func testNothingIsAskedNorWrittenUntilTheIconIsTouched() {
        let slot = model()

        XCTAssertEqual(slot.state, .idle)
        XCTAssertEqual(saver.requests, 0)
        XCTAssertTrue(saver.written.isEmpty)
    }

    // MARK: - Le premier toucher demande l'autorisation d'ajout, puis enregistre

    func testTheFirstTouchAsksForTheAddOnlyPermissionThenSavesAndConfirms() async {
        let slot = model()

        await slot.save()

        XCTAssertEqual(saver.requests, 1)
        XCTAssertEqual(saver.written.count, 1)
        XCTAssertEqual(slot.state, .saved)
    }

    func testOnceAllowedTheNextTouchSavesWithoutAskingAgain() async {
        saver.current = .allowed
        let slot = model()

        await slot.save()
        await slot.save()

        XCTAssertEqual(saver.requests, 0)
        XCTAssertEqual(saver.written.count, 2, "un toucher = une photo enregistrée")
    }

    // MARK: - Refus, restriction, échec : calme, rien d'écrit

    func testARefusalWhenAskedWritesNothingAndOffersTheSettings() async throws {
        saver.afterRequest = .denied
        let slot = model()

        await slot.save()

        XCTAssertEqual(slot.state, .denied)
        XCTAssertTrue(saver.written.isEmpty)
        let message = try XCTUnwrap(PhotoSaveMessage(state: .denied))
        XCTAssertTrue(message.offersSettings, "« Ouvrir les réglages »")
    }

    func testAnAlreadyDeniedPermissionDoesNotAskAgainAndWritesNothing() async {
        saver.current = .denied
        let slot = model()

        await slot.save()

        XCTAssertEqual(slot.state, .denied)
        XCTAssertEqual(saver.requests, 0, "iOS ne repose pas la question : les Réglages seuls y changent quelque chose")
        XCTAssertTrue(saver.written.isEmpty)
    }

    func testARestrictedDeviceSaysSoWithoutASettingsButton() async throws {
        saver.current = .restricted
        let slot = model()

        await slot.save()

        XCTAssertEqual(slot.state, .restricted)
        XCTAssertTrue(saver.written.isEmpty)
        XCTAssertFalse(try XCTUnwrap(PhotoSaveMessage(state: .restricted)).offersSettings)
    }

    func testAnUnwritablePhotoOrAnImageThatCannotBePreparedIsACalmFailureNotAnError() async {
        saver.current = .allowed
        saver.saves = false
        let first = model()
        await first.save()
        let second = PhotoSaveViewModel(saver: saver, image: image, encode: { _ in nil })
        await second.save()

        XCTAssertEqual(first.state, .failed)
        XCTAssertEqual(second.state, .failed)
        XCTAssertEqual(saver.written.count, 1, "rien n'est écrit quand l'image n'a pas pu être préparée")
    }

    func testTheMessagesAreCalmAndOnlySavedFailedRestrictedAndDeniedHaveOne() throws {
        XCTAssertNil(PhotoSaveMessage(state: .idle))
        XCTAssertNil(PhotoSaveMessage(state: .saving))
        for state in [PhotoSaveState.saved, .failed, .restricted, .denied] {
            let message = try XCTUnwrap(PhotoSaveMessage(state: state))
            XCTAssertFalse(message.text.isEmpty)
            for word in ["erreur", "error", "échec", "refus", "interdit"] {
                XCTAssertFalse(message.text.lowercased().contains(word), "\(state) : ton calme, jamais « \(word) »")
            }
        }
        XCTAssertEqual(PhotoSaveMessage(state: .saved)?.seconds, 3)
    }

    func testTheMessageGoesAwayAndNeverInTheMiddleOfASave() async {
        saver.current = .allowed
        let slot = model()
        await slot.save()

        slot.dismissMessage()

        XCTAssertEqual(slot.state, .idle)
    }

    func testTheLibraryStatusesAreMappedSafely() {
        XCTAssertEqual(PhotosLibrarySaver.access(for: .authorized), .allowed)
        XCTAssertEqual(PhotosLibrarySaver.access(for: .limited), .allowed)
        XCTAssertEqual(PhotosLibrarySaver.access(for: .notDetermined), .notDetermined)
        XCTAssertEqual(PhotosLibrarySaver.access(for: .denied), .denied)
        XCTAssertEqual(PhotosLibrarySaver.access(for: .restricted), .restricted)
    }

    // MARK: - Aucune métadonnée, aucun lieu dans l'image enregistrée

    private func properties(of data: Data) -> [CFString: Any] {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let found = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any] else {
            return [:]
        }
        return found
    }

    private func jpegWithPlaceAndCamera() -> Data {
        guard let cgImage = image.cgImage else {
            return Data()
        }
        let output = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(output, "public.jpeg" as CFString, 1, nil) else {
            return Data()
        }
        let metadata: [CFString: Any] = [
            kCGImagePropertyGPSDictionary: [
                kCGImagePropertyGPSLatitude: 48.85661, kCGImagePropertyGPSLatitudeRef: "N",
                kCGImagePropertyGPSLongitude: 2.35222, kCGImagePropertyGPSLongitudeRef: "E"
            ],
            kCGImagePropertyTIFFDictionary: [kCGImagePropertyTIFFMake: "AppareilDeTest", kCGImagePropertyTIFFModel: "Modele"]
        ]
        CGImageDestinationAddImage(destination, cgImage, metadata as CFDictionary)
        CGImageDestinationFinalize(destination)
        return output as Data
    }

    func testTheSavedJpegCarriesNoGpsNoCameraNoMetadataEvenIfTheSourceDid() throws {
        let source = jpegWithPlaceAndCamera()
        XCTAssertNotNil(properties(of: source)[kCGImagePropertyGPSDictionary], "le test part d'une source qui PORTE un lieu")
        let received = try XCTUnwrap(UIImage(data: source))

        let saved = try XCTUnwrap(PhotoExport.cleanJpeg(from: received))
        let props = properties(of: saved)

        XCTAssertNil(props[kCGImagePropertyGPSDictionary], "aucun lieu")
        XCTAssertNil((props[kCGImagePropertyTIFFDictionary] as? [CFString: Any])?[kCGImagePropertyTIFFMake], "aucun appareil")
        XCTAssertNil((props[kCGImagePropertyTIFFDictionary] as? [CFString: Any])?[kCGImagePropertyTIFFModel])
        XCTAssertNil((props[kCGImagePropertyExifDictionary] as? [CFString: Any])?[kCGImagePropertyExifDateTimeOriginal], "aucune date de prise de vue")
        XCTAssertEqual(props[kCGImagePropertyPixelWidth] as? Int, Int(received.size.width * received.scale))
        XCTAssertTrue(ImagePhotoDecoder.hasJpegSignature(saved))
    }

    func testAnEmptyImageIsNotExported() {
        XCTAssertNil(PhotoExport.cleanJpeg(from: UIImage()))
    }

    // MARK: - Textes

    func testTheTextsExistInFrenchAndEnglishAndMatchAndroid() throws {
        let data = try Data(contentsOf: iosRoot.appendingPathComponent("Miff/Resources/Localizable.xcstrings"))
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        let strings = try XCTUnwrap(root["strings"] as? [String: [String: Any]])
        let android = iosRoot.deletingLastPathComponent().appendingPathComponent("android/app/src/main/res")
        let french = try String(contentsOf: android.appendingPathComponent("values-fr/strings.xml"), encoding: .utf8)
        let english = try String(contentsOf: android.appendingPathComponent("values/strings.xml"), encoding: .utf8)
        // Les clés `feed_photo_download*` sont les MÊMES qu'Android, avec les mêmes textes ; les deux clés propres à iOS (permission) sont `ios_`.
        let shared: [String: [String: String]] = [
            "feed_photo_download": ["fr": "Enregistrer la photo", "en": "Save the photo"],
            "feed_photo_download_a11y": ["fr": "Enregistrer cette photo sur ton téléphone", "en": "Save this photo on your phone"],
            "feed_photo_download_done": ["fr": "Photo enregistrée.", "en": "Photo saved."]
        ]

        for (key, byLanguage) in shared {
            let localizations = try XCTUnwrap(strings[key]?["localizations"] as? [String: [String: Any]], key)
            for (language, value) in byLanguage {
                let unit = try XCTUnwrap(localizations[language]?["stringUnit"] as? [String: Any], key)
                XCTAssertEqual(unit["value"] as? String, value, "\(key) \(language)")
            }
            XCTAssertTrue(french.contains("name=\"\(key)\">\(byLanguage["fr"] ?? "")</string>"), "Android fr \(key)")
            XCTAssertTrue(english.contains("name=\"\(key)\">\(byLanguage["en"] ?? "")</string>"), "Android en \(key)")
        }
        for key in ["feed_photo_download_failed", "ios_feed_photo_download_denied", "ios_feed_photo_download_restricted", "update_action_open_settings"] {
            let localizations = try XCTUnwrap(strings[key]?["localizations"] as? [String: [String: Any]], key)
            XCTAssertNotNil(localizations["fr"], key)
            XCTAssertNotNil(localizations["en"], key)
        }
    }

    // MARK: - Une seule clé de photothèque : l'ajout

    func testOnlyTheAddOnlyKeyIsDeclaredAndItsTextIsHonestInBothLanguages() throws {
        let settings = try text("project.yml").components(separatedBy: "\n").filter { !$0.trimmingCharacters(in: .whitespaces).hasPrefix("#") }
        XCTAssertTrue(settings.contains { $0.contains("NSPhotoLibraryAddUsageDescription") })
        XCTAssertFalse(settings.contains { $0.contains("NSPhotoLibraryUsageDescription") }, "jamais la clé de LECTURE")

        let data = try Data(contentsOf: iosRoot.appendingPathComponent("Miff/Resources/InfoPlist.xcstrings"))
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        let strings = try XCTUnwrap(root["strings"] as? [String: [String: Any]])
        XCTAssertNil(strings["NSPhotoLibraryUsageDescription"])
        let localizations = try XCTUnwrap(strings["NSPhotoLibraryAddUsageDescription"]?["localizations"] as? [String: [String: Any]])
        let french = try XCTUnwrap((localizations["fr"]?["stringUnit"] as? [String: Any])?["value"] as? String)
        let english = try XCTUnwrap((localizations["en"]?["stringUnit"] as? [String: Any])?["value"] as? String)
        XCTAssertTrue(french.contains("uniquement quand tu touches") && french.contains("ne lit jamais"), french)
        XCTAssertTrue(english.contains("only when you tap") && english.contains("never reads"), english)
    }

    // MARK: - Par lecture des sources : ajout seul, au toucher, à cet endroit seulement

    private func swiftFiles() -> [URL] {
        let miff = iosRoot.appendingPathComponent("Miff")
        var found: [URL] = []
        for case let file as URL in FileManager.default.enumerator(at: miff, includingPropertiesForKeys: nil) ?? NSEnumerator()
        where file.pathExtension == "swift" {
            found.append(file)
        }
        return found
    }

    private func filesContaining(_ fragment: String) throws -> [String] {
        try swiftFiles().filter {
            UpdatePrivacyTests.codeLines(try String(contentsOf: $0, encoding: .utf8)).joined(separator: "\n").contains(fragment)
        }.map { $0.lastPathComponent }.sorted()
    }

    func testOnlyOneFileOfTheAppImportsPhotosAndItOnlyAddsNeverReads() throws {
        XCTAssertEqual(try filesContaining("import Photos\n"), ["PhotoLibrarySaver.swift"], "PhotosUI (le sélecteur du profil) est un autre module")
        let saver = try code("Miff/Services/Feed/PhotoLibrarySaver.swift")

        XCTAssertEqual(saver.components(separatedBy: "for: .addOnly").count - 1, 2, "statut et demande : en ajout seul")
        for forbidden in [".readWrite", "PHAsset.fetch", "fetchAssets", "PHFetch", "PHPickerViewController", ".location", "UIImageWriteToSavedPhotosAlbum", "FileManager", ".write(", "print("] {
            XCTAssertFalse(saver.contains(forbidden), forbidden)
        }
        XCTAssertEqual(saver.components(separatedBy: "PHAssetCreationRequest.forAsset()").count - 1, 1)
    }

    func testNothingElseInTheAppReadsOrWritesTheLibraryOrOpensAShareSheet() throws {
        for fragment in ["PHPhotoLibrary", "PHAsset", "UIImageWriteToSavedPhotosAlbum", "UIActivityViewController", "PHPickerViewController"] {
            let files = try filesContaining(fragment)
            XCTAssertTrue(files.allSatisfy { $0 == "PhotoLibrarySaver.swift" }, "\(fragment) : \(files)")
        }
        XCTAssertTrue(try filesContaining("PhotoShareSheet").isEmpty, "la feuille de partage est supprimée")
    }

    func testTheWriteStartsOnlyFromTheTouchOfTheIconAndOnlyInTheFullScreenOfAFriendsPhoto() throws {
        XCTAssertEqual(try filesContaining("saver.save(jpeg:"), ["PhotoSaveViewModel.swift"])
        XCTAssertEqual(try filesContaining("model.save()"), ["PhotoSaveButton.swift"], "le seul appel de save() est le toucher de l'icône")
        XCTAssertEqual(try filesContaining("PhotoSaveButton(image:"), ["FeedPhotoFullScreen.swift"], "ni la carte, ni l'Update")
        let button = try code("Miff/Views/Feed/PhotoSaveButton.swift")
        XCTAssertTrue(button.contains("Task { await model.save() }"))
        XCTAssertFalse(button.contains(".onAppear"), "aucune écriture automatique")
    }

    func testTheIconIsInTheTopBarOppositeTheCloseButtonAndNotInTheFooter() throws {
        let screen = try code("Miff/Views/Feed/FeedPhotoFullScreen.swift")
        let closeAt = try XCTUnwrap(screen.range(of: "labelKey: \"update_photo_close_a11y\"")?.lowerBound)
        let saveAt = try XCTUnwrap(screen.range(of: "PhotoSaveButton(image: opened.image)")?.lowerBound)
        let footerAt = try XCTUnwrap(screen.range(of: "private var footer: some View")?.lowerBound)

        XCTAssertLessThan(closeAt, saveAt, "après la croix, dans la même barre du haut")
        XCTAssertLessThan(saveAt, footerAt, "plus dans le pied")
        XCTAssertEqual(screen.components(separatedBy: "PhotoSaveButton(image:").count - 1, 1)
    }

    func testTheButtonIsAnIconNamedForVoiceOverWithAComfortableTargetAndOneAnnouncement() throws {
        let button = try code("Miff/Views/Feed/PhotoSaveButton.swift")

        XCTAssertTrue(button.contains("RoundOverlayButton(systemName: \"square.and.arrow.down\", labelKey: \"feed_photo_download_a11y\")"))
        XCTAssertGreaterThanOrEqual(UpdateLayout.roundButtonSize, 44, "zone de toucher d'au moins 44 pt, comme la croix")
        XCTAssertFalse(button.contains("accessibilityValue"), "une seule annonce VoiceOver, pas de valeur en plus")
        XCTAssertEqual(button.components(separatedBy: "UIAccessibility.post").count - 1, 1)
        XCTAssertTrue(button.contains("isAccessibilitySize"), "en grand texte le message passe sur plusieurs lignes")
        XCTAssertTrue(button.contains("update_action_open_settings"), "« Ouvrir les réglages » en cas de refus")
        let screen = try code("Miff/Views/Feed/FeedPhotoFullScreen.swift")
        XCTAssertTrue(screen.contains(".hidesInAppSwitcher()"), "le voile des applications récentes couvre aussi la boîte du système")
    }
}
// swiftlint:enable line_length
