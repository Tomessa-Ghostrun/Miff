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
            writtenDescription: description
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

    func testNoDescriptionAddsNoHeightSoTheFooterIsTheOneOfBefore() {
        XCTAssertEqual(PhotoFullScreenLayout.descriptionHeight(unitCount: 0, fontSize: 16, lineHeight: 22, width: 343), 0)
        XCTAssertEqual(PhotoFullScreenLayout.descriptionHeight(unitCount: 50, fontSize: 16, lineHeight: 22, width: 0), 0)
    }

    // La description est au style du grand titre (gras 20, interligne 26), « Photo de … » au corps 16 (retour du PM sur la recette Android).
    func testTheDescriptionHeightGrowsWithTheLengthAndWithTheFontSize() {
        let short = PhotoFullScreenLayout.descriptionHeight(unitCount: 20, fontSize: 20, lineHeight: 26, width: 343)
        let full = PhotoFullScreenLayout.descriptionHeight(unitCount: 140, fontSize: 20, lineHeight: 26, width: 343)
        let large = PhotoFullScreenLayout.descriptionHeight(unitCount: 140, fontSize: 55, lineHeight: 70, width: 343)

        XCTAssertGreaterThan(full, short)
        XCTAssertGreaterThan(large, full)
        // 140 caractères en gras 20 tiennent sur environ 5 lignes à 343 pt de large (30 caractères par ligne).
        XCTAssertEqual(full, 5 * 26 + PhotoFullScreenLayout.captionSpacing, accuracy: 0.001)
    }

    func testALongDescriptionFitsAtNormalSizeOnTallPhonesAndScrollsOnSmallOnesAndWithTheLargestFont() {
        let fade = PhotoFullScreenLayout.fadeHeight
        // La marge basse réellement utilisée en portrait : la barre d'accueil (34 pt) + 16 pt.
        let bottom = PhotoFullScreenLayout.bottomMargin(safeInset: 34, isPortrait: true)
        let normal = PhotoFullScreenLayout.footerEstimateWithDescription + bottom
            + PhotoFullScreenLayout.descriptionHeight(unitCount: 140, fontSize: 20, lineHeight: 26, width: 343) + fade
        XCTAssertFalse(PhotoFullScreenLayout.footerScrolls(estimate: normal, screenHeight: 812), "11 Pro : tient dans 45 %")
        XCTAssertTrue(PhotoFullScreenLayout.footerScrolls(estimate: normal, screenHeight: 647), "iPhone SE : défile")

        // La plus grande taille de texte : le pied (x 2,7), le corps (55) et l'interligne (70) grandissent ; il défile même sur un grand écran.
        let largest = PhotoFullScreenLayout.footerEstimateWithDescription * 2.7 + bottom
            + PhotoFullScreenLayout.descriptionHeight(unitCount: 140, fontSize: 55, lineHeight: 70, width: 343) + fade
        XCTAssertTrue(PhotoFullScreenLayout.footerScrolls(estimate: largest, screenHeight: 812))
        // La zone reste bornée à 45 % : la photo n'est jamais masquée en entier.
        XCTAssertLessThan(PhotoFullScreenLayout.footerMaxHeight(screenHeight: 647), 647 / 2)
    }

    func testTheEstimateWithoutADescriptionIsStillTheOneOfBefore() {
        XCTAssertEqual(PhotoFullScreenLayout.footerEstimate, 170)
        XCTAssertLessThan(PhotoFullScreenLayout.footerEstimateWithDescription, PhotoFullScreenLayout.footerEstimate)
    }

    // MARK: - Typographie du pied : la description prend le titre, « Photo de … » le texte courant

    func testWhenThereIsADescriptionItTakesTheHeadingAndThePhotoLineTheBodyWithoutOneNothingChanges() {
        XCTAssertEqual(PhotoFullScreenLayout.descriptionLevel, .heading)
        XCTAssertEqual(PhotoFullScreenLayout.titleLevel(hasDescription: true), .body)
        XCTAssertEqual(PhotoFullScreenLayout.titleLevel(hasDescription: false), .heading, "sans description, le pied est celui d'avant")
    }

    /// Le plein écran, lu dans ses SOURCES (sans les commentaires).
    private func fullScreenSource() throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Feed
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff/Views/Feed/FeedPhotoFullScreen.swift")
        return UpdatePrivacyTests.codeLines(try String(contentsOf: url, encoding: .utf8)).joined(separator: "\n")
    }

    /// Le texte entre deux repères du source.
    private func slice(_ source: String, from start: String, to end: String) throws -> String {
        let begin = try XCTUnwrap(source.range(of: start), "repère introuvable : \(start)")
        let finish = try XCTUnwrap(source.range(of: end, range: begin.upperBound..<source.endIndex), "repère introuvable : \(end)")
        return String(source[begin.upperBound..<finish.lowerBound])
    }

    func testTheViewGivesTheHeadingStyleToTheDescriptionAndKeepsThePhotoLineAsTheHeaderForVoiceOver() throws {
        let source = try fullScreenSource()

        XCTAssertTrue(source.contains("level == .heading ? textTitleBold20 : textBodyRegular"))
        XCTAssertTrue(source.contains(".miffTextStyle(style(for: PhotoFullScreenLayout.descriptionLevel))"))
        XCTAssertTrue(source.contains(".miffTextStyle(style(for: PhotoFullScreenLayout.titleLevel(hasDescription: hasDescription)))"))
        // « Photo de … » reste l'en-tête VoiceOver ; la description garde « Description : » et reste AVANT le titre (`footerRows`).
        let title = try slice(source, from: "case .title:", to: "case .place:")
        XCTAssertTrue(title.contains(".accessibilityAddTraits(.isHeader)"))
        XCTAssertTrue(source.contains("update_description_shown_a11y"))
    }

    // MARK: - Le cadrage de la photo ne dépend jamais de la description (retour du PM : bandes noires vues sur Android)

    func testThePhotoFramingDoesNotDependOnTheDescription() throws {
        let source = try fullScreenSource()

        // La mise en page de l'écran (`body`) : la taille de l'écran vient de la géométrie seule ; la photo et le fond noir remplissent l'écran
        // entier ; le pied n'est qu'une SURIMPRESSION (il ne retire rien à la place de la photo). Aucune mention de la description.
        let body = try slice(source, from: "var body: some View {", to: "private func topBar")
        XCTAssertFalse(body.lowercased().contains("description"), "le cadre de l'écran ne lit pas la description")
        XCTAssertFalse(body.contains("opened.photo"))
        XCTAssertTrue(body.contains("photo(size: size)"))
        XCTAssertTrue(body.contains(".frame(width: size.width, height: size.height)"))
        XCTAssertTrue(body.contains(".overlay(alignment: .bottom)"))
        XCTAssertTrue(body.contains("footerOverlay(size: size, insets: insets)"))
        // Le modificateur `.safeAreaInset(edge:` (qui réserverait de la place) est absent ; la LECTURE `proxy.safeAreaInsets` reste permise.
        XCTAssertFalse(body.contains(".safeAreaInset(edge"), "un pied inséré dans la mise en page réduirait la photo")
        XCTAssertFalse(body.contains("VStack"), "un pied empilé sous la photo réduirait la photo")

        // La photo : taille ENTIÈRE de l'écran, centrée, sans marge ni lien avec le pied ni la description.
        let photo = try slice(source, from: "private func photo(size: CGSize)", to: "private func clamped")
        XCTAssertFalse(photo.lowercased().contains("description"))
        XCTAssertFalse(photo.contains("footer"))
        XCTAssertFalse(photo.contains(".padding"))
        XCTAssertTrue(photo.contains(".scaledToFill()"), "la photo remplit l'écran, avec ou sans description")
        XCTAssertTrue(photo.contains(".clipped()"), "ce qui dépasse est coupé, jamais décalé")
        XCTAssertTrue(photo.contains(".frame(width: size.width, height: size.height)"))

        // Le pied : SEUL il lit la description, et il est une surimpression (l'estimation de hauteur ne touche pas la photo).
        let footerStart = try XCTUnwrap(source.range(of: "private func footerOverlay"))
        XCTAssertTrue(source[footerStart.lowerBound...].contains("descriptionText"))
    }

    func testThePhotoSizeComesOnlyFromTheImageAndTheScreen() {
        // 11 Pro : zone visible 375 x 734 + zones sûres 44 en haut et 34 en bas = écran de 375 x 812 ; une photo 3:4 la remplit en hauteur (coupée sur les côtés).
        let screen = PhotoFullScreenLayout.screenSize(visible: CGSize(width: 375, height: 734), top: 44, bottom: 34, leading: 0, trailing: 0)
        XCTAssertEqual(screen, CGSize(width: 375, height: 812))
        let filled = PhotoFullScreenLayout.filledSize(imageSize: CGSize(width: 3_000, height: 4_000), in: screen)
        XCTAssertEqual(filled.width, 609, accuracy: 0.5)
        XCTAssertEqual(filled.height, 812, accuracy: 0.001)
        // Ni `screenSize` ni `filledSize` ne reçoivent le pied ni la description : leur résultat ne peut pas en dépendre.
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
// swiftlint:enable line_length
