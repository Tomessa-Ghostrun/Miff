// Les assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// « Enregistrer la photo » dans le plein écran de la photo d'un proche (décision du PM) : la photo est CONFIÉE à la feuille de partage du système, sans
/// permission ni clé de photothèque ; la confirmation n'est dite que si « Enregistrer l'image » a été choisi ; les textes existent en FR et EN ; et,
/// par lecture des sources, ce bouton n'existe qu'à cet endroit. Miroir côté Android à faire (mêmes clés `feed_photo_download*`).
final class PhotoSaveTests: XCTestCase {
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

    // MARK: - La confirmation

    func testOnlyAChosenSaveToThePhotoLibraryIsConfirmed() {
        XCTAssertTrue(PhotoShareSheet.isSaved(completed: true, activityType: .saveToCameraRoll))
        XCTAssertFalse(PhotoShareSheet.isSaved(completed: true, activityType: .copyToPasteboard), "copier n'est pas enregistrer")
        XCTAssertFalse(PhotoShareSheet.isSaved(completed: true, activityType: .airDrop))
        XCTAssertFalse(PhotoShareSheet.isSaved(completed: true, activityType: nil))
        XCTAssertFalse(PhotoShareSheet.isSaved(completed: false, activityType: .saveToCameraRoll), "annulé : rien n'est dit")
        XCTAssertFalse(PhotoShareSheet.isSaved(completed: false, activityType: nil))
    }

    // MARK: - Textes

    func testTheTextsExistInFrenchAndEnglish() throws {
        let data = try Data(contentsOf: iosRoot.appendingPathComponent("Miff/Resources/Localizable.xcstrings"))
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        let strings = try XCTUnwrap(root["strings"] as? [String: [String: Any]])
        let expected: [String: [String: String]] = [
            "feed_photo_download": ["fr": "Enregistrer la photo", "en": "Save the photo"],
            "feed_photo_download_done": ["fr": "Photo enregistrée.", "en": "Photo saved."]
        ]

        for (key, byLanguage) in expected {
            let localizations = try XCTUnwrap(strings[key]?["localizations"] as? [String: [String: Any]], key)
            for (language, value) in byLanguage {
                let unit = try XCTUnwrap(localizations[language]?["stringUnit"] as? [String: Any], key)
                XCTAssertEqual(unit["value"] as? String, value, "\(key) \(language)")
            }
        }
    }

    func testTheAndroidStringsHaveTheSameKeysAndTexts() throws {
        let android = iosRoot.deletingLastPathComponent().appendingPathComponent("android/app/src/main/res")
        let french = try String(contentsOf: android.appendingPathComponent("values-fr/strings.xml"), encoding: .utf8)
        let english = try String(contentsOf: android.appendingPathComponent("values/strings.xml"), encoding: .utf8)

        XCTAssertTrue(french.contains("<string name=\"feed_photo_download\">Enregistrer la photo</string>"))
        XCTAssertTrue(french.contains("<string name=\"feed_photo_download_done\">Photo enregistrée.</string>"))
        XCTAssertTrue(english.contains("<string name=\"feed_photo_download\">Save the photo</string>"))
        XCTAssertTrue(english.contains("<string name=\"feed_photo_download_done\">Photo saved.</string>"))
    }

    // MARK: - Aucune permission, aucune écriture de l'app

    func testNoPhotoLibraryKeyIsDeclaredAnywhere() throws {
        for file in ["project.yml", "Miff/Resources/InfoPlist.xcstrings"] {
            // Les commentaires du fichier de projet citent la clé pour dire qu'elle est interdite : seules les lignes de réglage comptent.
            let settings = try text(file).components(separatedBy: "\n").filter { !$0.trimmingCharacters(in: .whitespaces).hasPrefix("#") }
            XCTAssertFalse(settings.contains { $0.contains("NSPhotoLibrary") }, "\(file) : aucune clé de photothèque")
        }
    }

    func testTheShareSheetIsGivenOneImageAndNothingElseAndOnlyThisFileBuildsIt() throws {
        let sheet = try code("Miff/Views/Feed/PhotoSaveButton.swift")

        XCTAssertTrue(sheet.contains("UIActivityViewController(activityItems: [image], applicationActivities: nil)"))
        for forbidden in ["UIImageWriteToSavedPhotosAlbum", "import Photos", "PHPhotoLibrary", "FileManager", ".write(", "jpegData", "pngData"] {
            XCTAssertFalse(sheet.contains(forbidden), forbidden)
        }
        let miff = iosRoot.appendingPathComponent("Miff")
        var builders: [String] = []
        for case let file as URL in FileManager.default.enumerator(at: miff, includingPropertiesForKeys: nil) ?? NSEnumerator()
        where file.pathExtension == "swift" {
            let source = UpdatePrivacyTests.codeLines(try String(contentsOf: file, encoding: .utf8)).joined(separator: "\n")
            if source.contains("UIActivityViewController(") {
                builders.append(file.lastPathComponent)
            }
        }
        XCTAssertEqual(builders, ["PhotoSaveButton.swift"], "la feuille de partage n'est construite qu'ici")
    }

    func testTheSaveButtonExistsInTheFullScreenOfAFriendsPhotoOnly() throws {
        let miff = iosRoot.appendingPathComponent("Miff")
        var users: [String] = []
        for case let file as URL in FileManager.default.enumerator(at: miff, includingPropertiesForKeys: nil) ?? NSEnumerator()
        where file.pathExtension == "swift" {
            let source = UpdatePrivacyTests.codeLines(try String(contentsOf: file, encoding: .utf8)).joined(separator: "\n")
            if source.contains("PhotoSaveButton(image:") || source.contains("PhotoShareSheet(") {
                users.append(file.lastPathComponent)
            }
        }

        XCTAssertEqual(users.sorted(), ["FeedPhotoFullScreen.swift", "PhotoSaveButton.swift"], "ni la carte, ni l'Update")
    }

    func testTheButtonIsNamedForVoiceOverAndHasAComfortableTarget() throws {
        let button = try code("Miff/Views/Feed/PhotoSaveButton.swift")

        XCTAssertTrue(button.contains("Text(\"feed_photo_download\")"), "le nom parlé est le texte visible : « Enregistrer la photo »")
        XCTAssertTrue(button.contains("Spacing.minTouchTarget + Spacing.xs"), "au moins 44 pt")
        XCTAssertTrue(button.contains(".hidesInAppSwitcher()"), "la feuille est masquée dans le sélecteur d'applications")
        XCTAssertFalse(button.contains("print("))
    }
}
// swiftlint:enable line_length
