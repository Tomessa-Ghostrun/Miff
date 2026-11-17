// Les assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import FirebaseFirestore
import XCTest
@testable import Miff

/// La description d'une photo, côté LECTURE et plein écran du Feed (ticket description-update, critères F1 à F6, F8, C2, C3, C5, A3, A4) : ce qui vient
/// du réseau est nettoyé avant d'être montré (quelqu'un d'autre l'a écrit), une ancienne photo (sans clé) reste telle qu'avant, la description est
/// AU-DESSUS de « Photo de … », le pied reste borné à 45 % de la hauteur et défile avec la plus grande police. Miroir de `PhotoDescriptionTest.kt` et
/// de `PhotoFullScreenDescriptionTest.kt` d'Android.
final class PhotoDescriptionFeedTests: XCTestCase {
    private func raw(description: Any?) -> RawPhoto {
        RawPhoto(
            jpeg: Data(repeating: 7, count: 1_000),
            createdAtMillis: feedNow - feedMinutes(20),
            expiresAtMillis: feedNow + feedHours(22),
            lat: 48.85661,
            lng: 2.35222,
            measuredAtMillis: feedNow - feedMinutes(22),
            descriptionValue: description
        )
    }

    private func read(_ description: Any?) throws -> SharedPhoto {
        try XCTUnwrap(SharedPhoto.fromServer(raw(description: description), nowMillis: feedNow))
    }

    // MARK: - Lecture

    func testAPhotoFromTheServerCarriesTheCleanedDescriptionOrNone() throws {
        XCTAssertEqual(try read("Bien arrivé").descriptionText, "Bien arrivé")
        XCTAssertEqual(try read("a\n\u{202E}b").descriptionText, "a b")
        XCTAssertNil(try read(nil).descriptionText)
        XCTAssertNil(try read(42).descriptionText)
        XCTAssertNil(try read("").descriptionText)
        XCTAssertNil(try read(NSNull()).descriptionText)
    }

    func testAnInvalidDescriptionNeverHidesThePhotoItself() throws {
        let photo = try read(String(repeating: "x", count: 500))

        XCTAssertNil(photo.descriptionText, "un texte que les règles n'auraient jamais laissé passer est ignoré")
        XCTAssertEqual(photo.jpeg.count, 1_000)
        XCTAssertNotNil(photo.place)
    }

    func testAPhotoOfTheOldKindHasNoDescriptionAndShowsNothingOfItself() {
        let old = SharedPhoto(jpeg: Data([1, 2, 3]), createdAtMillis: 1, expiresAtMillis: 2, place: nil)
        let withText = SharedPhoto(jpeg: Data([1, 2, 3]), createdAtMillis: 1, expiresAtMillis: 2, place: nil, descriptionText: "secret words")

        XCTAssertNil(old.descriptionText)
        XCTAssertEqual("\(withText)", "SharedPhoto(redacted)")
        XCTAssertFalse("\(withText)".contains("secret"))
        XCTAssertNotEqual(old, withText, "deux photos qui ne diffèrent que par la description ne sont pas égales")
    }

    func testTheFirestoreDocumentIsReadFieldByFieldAndADescriptionOfAnotherTypeIsIgnored() throws {
        let now = Date(timeIntervalSince1970: Double(feedNow) / 1000)
        func fields(_ description: Any?) -> [String: Any] {
            var document: [String: Any] = [
                "jpeg": Data(repeating: 7, count: 1_000),
                "createdAt": Timestamp(date: now.addingTimeInterval(-1_200)),
                "expiresAt": Timestamp(date: now.addingTimeInterval(79_200)),
                "lat": 48.85661, "lng": 2.35222,
                "measuredAt": Timestamp(date: now.addingTimeInterval(-1_320))
            ]
            if let description {
                document["description"] = description
            }
            return document
        }

        for (value, expected) in [("Je suis là", "Je suis là"), ("  Coucou \n ", "Coucou")] {
            guard case .photo(let photo) = FirestorePhotoSource.photo(fields: fields(value), nowMillis: feedNow) else {
                return XCTFail("une photo était attendue")
            }
            XCTAssertEqual(photo.descriptionText, expected)
        }
        for value in [nil, 12, true, ["a"]] as [Any?] {
            guard case .photo(let photo) = FirestorePhotoSource.photo(fields: fields(value), nowMillis: feedNow) else {
                return XCTFail("la photo reste montrée")
            }
            XCTAssertNil(photo.descriptionText)
        }
    }

    // MARK: - Le pied du plein écran

    func testTheDescriptionIsAboveThePhotoTitleThenThePlace() {
        XCTAssertEqual(PhotoFullScreenLayout.footerRows(hasDescription: true, hasPlace: true), [.description, .title, .place])
        XCTAssertEqual(PhotoFullScreenLayout.footerRows(hasDescription: true, hasPlace: false), [.description, .title])
    }

    func testWithoutADescriptionTheFooterIsExactlyWhatItAlwaysWasNoEmptyLineNoLabel() {
        XCTAssertEqual(PhotoFullScreenLayout.footerRows(hasDescription: false, hasPlace: true), [.title, .place])
        XCTAssertEqual(PhotoFullScreenLayout.footerRows(hasDescription: false, hasPlace: false), [.title])
    }

    func testAFooterWithALongDescriptionScrollsInsideTheCapOf45PercentEvenOnASmallPhoneAndWithTheLargestFont() {
        // Un petit téléphone (SE : 647 pt sous la zone sûre) : le plafond est de 45 % de la hauteur.
        let height: CGFloat = 647
        let cap = PhotoFullScreenLayout.footerMaxHeight(screenHeight: height)
        XCTAssertEqual(cap, height * 0.45, accuracy: 0.001)

        // Pied naturel : titre, position, bouton (~170) + zone sûre + 140 caractères sur 4 lignes (~4 x 22) : tient à taille normale...
        let normal = PhotoFullScreenLayout.footerEstimate + 34 + 4 * 22
        XCTAssertFalse(PhotoFullScreenLayout.footerScrolls(estimate: normal + PhotoFullScreenLayout.fadeHeight, screenHeight: 812))
        // ... et défile avec la plus grande police (le texte passe à ~9 lignes, tout le pied grandit de moitié et plus) sur un petit téléphone.
        let largest = (PhotoFullScreenLayout.footerEstimate + 34) * 2.2 + 9 * 40
        XCTAssertTrue(PhotoFullScreenLayout.footerScrolls(estimate: largest + PhotoFullScreenLayout.fadeHeight, screenHeight: height))
        // La photo n'est jamais masquée en entier : au moins la moitié reste visible.
        XCTAssertLessThan(cap, height / 2)
    }

    func testTheDescriptionIsWhiteOnTheSameDarkVeilSoItKeepsAtLeast45To1EvenOverAWhitePhoto() {
        let contrast = PhotoFullScreenLayout.whiteTextContrast(overScrimAlpha: colorPhotoCaptionScrim.alpha)

        XCTAssertGreaterThanOrEqual(contrast, PhotoFullScreenLayout.minimumContrast, "contraste \(contrast)")
    }

    func testTheDescriptionStringsOfTheCatalogAreTheOnesOfTheTicket() throws {
        let expected: [String: (fr: String, en: String)] = [
            "update_description_placeholder": ("Renseigne une description", "Add a description"),
            "update_description_a11y_label": ("Description de ta photo (facultatif)", "Photo description (optional)"),
            "update_description_a11y_hint": ("Tes proches la verront avec ta photo pendant 24 h.", "Your loved ones will see it with your photo for 24 hours."),
            "update_description_counter": ("%1$lld/%2$lld", "%1$lld/%2$lld"),
            "update_description_counter_a11y": ("%1$lld caractères sur %2$lld", "%1$lld of %2$lld characters"),
            "update_description_limit": ("Limite de %1$lld caractères atteinte.", "Limit of %1$lld characters reached."),
            "update_description_shown_a11y": ("Description : %1$@", "Description: %1$@")
        ]
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("Miff/Resources/Localizable.xcstrings")
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        let strings = try XCTUnwrap(root["strings"] as? [String: [String: Any]])

        for (key, texts) in expected {
            let localizations = try XCTUnwrap(strings[key]?["localizations"] as? [String: [String: Any]], key)
            for (language, text) in [("fr", texts.fr), ("en", texts.en)] {
                let unit = try XCTUnwrap(localizations[language]?["stringUnit"] as? [String: Any], "\(key) \(language)")
                XCTAssertEqual(unit["value"] as? String, text, "\(key) \(language)")
            }
        }
    }
}
