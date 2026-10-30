import XCTest
@testable import Miff

/// Prénom et nom : 1 à 50 caractères, aucun alphabet interdit, caractères d'usurpation retirés (mêmes cas qu'Android).
final class ProfileFormTests: XCTestCase {
    func testNamesNeedBetweenOneAndFiftyCharactersOnceTheSpacesAreRemoved() {
        XCTAssertFalse(ProfileForm.isNameComplete(""))
        XCTAssertFalse(ProfileForm.isNameComplete("   "))
        XCTAssertTrue(ProfileForm.isNameComplete("A"))
        XCTAssertTrue(ProfileForm.isNameComplete(String(repeating: "a", count: 50)))
        XCTAssertFalse(ProfileForm.isNameComplete(String(repeating: "a", count: 51)))
        XCTAssertTrue(ProfileForm.isNameComplete("  Léa  "))
    }

    func testAnyAlphabetIsAccepted() {
        for name in ["Zoë", "O'Brien", "Anne-Marie", "山田", "Ελένη", "Алёна", "علي", "José Luis"] {
            XCTAssertTrue(ProfileForm.isNameComplete(name), name)
            XCTAssertEqual(ProfileForm.normalizeName(name), name)
        }
    }

    func testTypingIsCutAtFiftyAndSaysSo() {
        let limited = ProfileForm.limitInput(String(repeating: "a", count: 60))

        XCTAssertEqual(limited.text.utf16.count, 50)
        XCTAssertTrue(limited.wasCut)
        XCTAssertFalse(ProfileForm.limitInput("Camille").wasCut)
    }

    func testTheCutNeverSplitsAnEmoji() {
        // 49 lettres puis un émoji sur deux unités UTF-16 : la coupe à 50 ne doit pas en garder la moitié.
        let text = String(repeating: "a", count: 49) + "\u{1F600}"
        let limited = ProfileForm.limitInput(text)

        XCTAssertEqual(limited.text, String(repeating: "a", count: 49))
        XCTAssertTrue(limited.wasCut)
    }

    func testControlAndSpoofingCharactersAreRemoved() {
        // Marque de droite à gauche, espace de largeur nulle, forçage de direction : retirés.
        XCTAssertEqual(ProfileForm.normalizeName("Al\u{202E}ex\u{200B}"), "Alex")
        XCTAssertEqual(ProfileForm.normalizeName("\u{200F}Léa\u{FEFF}"), "Léa")
        // Un saut de ligne devient une espace (il casserait une carte).
        XCTAssertEqual(ProfileForm.normalizeName("Jean\nPaul"), "Jean Paul")
        XCTAssertEqual(ProfileForm.normalizeName("Jean\u{7}"), "Jean")
    }

    func testRepeatedSpacesAreReducedToOne() {
        XCTAssertEqual(ProfileForm.normalizeName("  Jean   Paul  "), "Jean Paul")
        XCTAssertEqual(ProfileForm.normalizeName("Jean\u{00A0}\u{00A0}Paul"), "Jean Paul")
    }

    func testTheCounterOnlyShowsNearTheLimit() {
        XCTAssertFalse(ProfileForm.showCounter(length: 44))
        XCTAssertTrue(ProfileForm.showCounter(length: 45))
        XCTAssertTrue(ProfileForm.showCounter(length: 50))
    }

    func testTheProviderNameIsOnlyASuggestionSplitAtTheFirstSpace() {
        XCTAssertTrue(ProfileForm.splitDisplayName(nil) == ("", ""))
        XCTAssertTrue(ProfileForm.splitDisplayName("   ") == ("", ""))
        XCTAssertTrue(ProfileForm.splitDisplayName("Camille") == ("Camille", ""))
        XCTAssertTrue(ProfileForm.splitDisplayName("Camille Dupont-Martin") == ("Camille", "Dupont-Martin"))
        XCTAssertTrue(ProfileForm.splitDisplayName("Jean Pierre de la Fontaine") == ("Jean", "Pierre de la Fontaine"))
    }

    func testEachPartOfASuggestedNameIsCutToFifty() {
        let suggested = ProfileForm.splitDisplayName(String(repeating: "a", count: 80) + " " + String(repeating: "b", count: 80))

        XCTAssertEqual(suggested.first.utf16.count, 50)
        XCTAssertEqual(suggested.last.utf16.count, 50)
    }
}

/// Le document de profil : la SEULE écriture qui crée `users/{uid}`, avec toutes les clés des règles.
final class ProfileDocumentTests: XCTestCase {
    private let consent = PendingConsent(uid: "u1", termsVersion: "0.1", analyticsAccepted: true)

    private func profile(
        first: String = "Camille",
        last: String = "Dupont",
        avatar: Data? = nil,
        phone: String = "+33612345678"
    ) -> NewProfile {
        NewProfile(firstName: first, lastName: last, avatarJpeg: avatar, phone: phone)
    }

    func testTheDocumentCarriesEveryKeyOfTheRules() throws {
        let fields = try ProfileDocument.forCreation(profile(), consent: consent)

        XCTAssertEqual(
            Set(fields.keys),
            ["firstName", "lastName", "phone", "createdAt", "termsAcceptedAt", "termsVersion", "analyticsConsent"])
        XCTAssertEqual(fields["firstName"], .string("Camille"))
        XCTAssertEqual(fields["phone"], .string("+33612345678"))
        XCTAssertEqual(fields["termsVersion"], .string("0.1"))
        // L'heure vient du serveur, jamais du téléphone.
        XCTAssertEqual(fields["createdAt"], .serverTimestamp)
        XCTAssertEqual(fields["termsAcceptedAt"], .serverTimestamp)
    }

    func testNoEmailAgeOrCountryIsEverWritten() throws {
        let fields = try ProfileDocument.forCreation(profile(), consent: consent)

        for forbidden in ["email", "age", "birthDate", "country", "uid"] {
            XCTAssertNil(fields[forbidden], forbidden)
        }
    }

    func testTheStatisticsConsentIsRecordedExplicitlyWhenAccepted() throws {
        let fields = try ProfileDocument.forCreation(profile(), consent: consent)

        XCTAssertEqual(
            fields["analyticsConsent"],
            .map([
                "enabled": .bool(true),
                "consentedAt": .serverTimestamp,
                "consentVersion": .string(ConsentVersions.analytics),
                "revokedAt": .null
            ]))
    }

    func testTheRefusedFormStaysWritableAndExplicit() throws {
        let refused = PendingConsent(uid: "u1", termsVersion: "0.1", analyticsAccepted: false)
        let fields = try ProfileDocument.forCreation(profile(), consent: refused)

        XCTAssertEqual(
            fields["analyticsConsent"],
            .map([
                "enabled": .bool(false),
                "consentedAt": .null,
                "consentVersion": .null,
                "revokedAt": .null
            ]))
    }

    func testThePhotoIsOnlyThereWhenThereIsOne() throws {
        XCTAssertNil(try ProfileDocument.forCreation(profile(), consent: consent)["avatar"])

        let bytes = Data(repeating: 1, count: 100)
        let withPhoto = try ProfileDocument.forCreation(profile(avatar: bytes), consent: consent)
        XCTAssertEqual(withPhoto["avatar"], .bytes(bytes))
    }

    func testNamesAreTrimmedBeforeBeingWritten() throws {
        let fields = try ProfileDocument.forCreation(profile(first: "  Léa ", last: " Martin"), consent: consent)

        XCTAssertEqual(fields["firstName"], .string("Léa"))
        XCTAssertEqual(fields["lastName"], .string("Martin"))
    }

    func testInvalidInputIsRefusedWithoutEchoingIt() {
        XCTAssertThrowsError(try ProfileDocument.forCreation(profile(first: " "), consent: consent)) { error in
            XCTAssertEqual(error as? ProfileDocumentError, .invalidName)
        }
        XCTAssertThrowsError(try ProfileDocument.forCreation(profile(last: String(repeating: "a", count: 51)), consent: consent))
        XCTAssertThrowsError(
            try ProfileDocument.forCreation(profile(avatar: Data(count: 20_001)), consent: consent)
        ) { error in
            XCTAssertEqual(error as? ProfileDocumentError, .avatarTooLarge)
        }
        XCTAssertThrowsError(try ProfileDocument.forCreation(profile(phone: "0612345678"), consent: consent)) { error in
            XCTAssertEqual(error as? ProfileDocumentError, .invalidPhone)
            // Un numéro de téléphone ne va jamais dans une erreur.
            XCTAssertFalse("\(error)".contains("0612345678"))
        }
    }

    func testE164HasExactlyTheShapeOfTheRules() {
        XCTAssertTrue(ProfileDocument.isE164("+33612345678"))
        XCTAssertTrue(ProfileDocument.isE164("+1234567"))
        XCTAssertTrue(ProfileDocument.isE164("+123456789012345"))
        XCTAssertFalse(ProfileDocument.isE164("+123456"))
        XCTAssertFalse(ProfileDocument.isE164("+1234567890123456"))
        XCTAssertFalse(ProfileDocument.isE164("+0123456789"))
        XCTAssertFalse(ProfileDocument.isE164("33612345678"))
        XCTAssertFalse(ProfileDocument.isE164("+33 6 12 34 56 78"))
        XCTAssertFalse(ProfileDocument.isE164("+33612345678\n"))
    }
}

/// Recadrage carré : le carré ne quitte jamais la photo, quels que soient le zoom et le centre.
final class CropMathTests: XCTestCase {
    func testZoomOneIsTheLargestSquareThatFits() {
        XCTAssertEqual(CropMath.sideAtZoom(width: 1_200, height: 800, zoom: 1), 800)
        XCTAssertEqual(CropMath.sideAtZoom(width: 1_200, height: 800, zoom: 4), 200)
    }

    func testZoomIsKeptBetweenOneAndFour() {
        XCTAssertEqual(CropMath.clampZoom(0.2), 1)
        XCTAssertEqual(CropMath.clampZoom(9), 4)
        XCTAssertEqual(CropMath.clampZoom(.nan), 1)
        XCTAssertEqual(CropMath.clampZoom(2.5), 2.5)
    }

    func testTheCentreIsKeptInsideThePhoto() {
        let near = CropMath.clampCenter(width: 1_000, height: 1_000, zoom: 2, center: (0, 0))
        XCTAssertEqual(near.x, 250)
        XCTAssertEqual(near.y, 250)

        let far = CropMath.clampCenter(width: 1_000, height: 1_000, zoom: 2, center: (5_000, 5_000))
        XCTAssertEqual(far.x, 750)
        XCTAssertEqual(far.y, 750)

        let nan = CropMath.clampCenter(width: 1_000, height: 600, zoom: 1, center: (Double.nan, Double.nan))
        XCTAssertEqual(nan.x, 500)
        XCTAssertEqual(nan.y, 300)
    }

    func testTheCropIsAlwaysAnInsideSquare() {
        for zoom in [0.5, 1, 1.7, 4, 10] {
            for center in [(-100.0, -100.0), (0, 0), (500, 400), (1_500, 900), (Double.nan, 10)] {
                let crop = CropMath.toCrop(width: 1_200, height: 800, zoom: zoom, center: center)

                XCTAssertGreaterThanOrEqual(crop.size, 1)
                XCTAssertGreaterThanOrEqual(crop.left, 0)
                XCTAssertGreaterThanOrEqual(crop.top, 0)
                XCTAssertLessThanOrEqual(crop.left + crop.size, 1_200)
                XCTAssertLessThanOrEqual(crop.top + crop.size, 800)
            }
        }
    }

    func testAnEmptyPhotoGivesAOnePixelCropInsteadOfACrash() {
        XCTAssertEqual(CropMath.toCrop(width: 0, height: 0, zoom: 1, center: (0, 0)), SquareCrop(left: 0, top: 0, size: 1))
    }
}

/// L'avatar tient en 20 000 octets : la qualité baisse, puis l'image rétrécit.
final class AvatarCompressionTests: XCTestCase {
    func testTheFirstResultThatFitsWins() {
        var asked: [[Int]] = []
        let result = AvatarCompression.fit { side, quality in
            asked.append([side, quality])
            return Data(count: quality > 60 ? 30_000 : 10_000)
        }

        XCTAssertEqual(result?.count, 10_000)
        // 160 pixels d'abord, qualité 80 puis 70 puis 60.
        XCTAssertEqual(asked, [[160, 80], [160, 70], [160, 60]])
    }

    func testASmallerPictureIsTriedWhenNoQualityFits() {
        let result = AvatarCompression.fit { side, _ in
            Data(count: side == 96 ? 5_000 : 25_000)
        }

        XCTAssertEqual(result?.count, 5_000)
    }

    func testNothingIsReturnedWhenNothingFitsOrTheEncoderFails() {
        XCTAssertNil(AvatarCompression.fit { _, _ in Data(count: 99_999) })
        XCTAssertNil(AvatarCompression.fit { _, _ in nil })
    }
}
