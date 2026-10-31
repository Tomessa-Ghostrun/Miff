import XCTest
@testable import Miff

/// Parties pures de la saisie du numéro (sans bibliothèque) : nettoyage de ce qui est tapé ou collé, recherche du sélecteur
/// de pays, drapeaux, pays par défaut.
final class PhoneInputTests: XCTestCase {
    /// Une bibliothèque factice : seuls quelques pays « existent ».
    private struct StubTools: PhoneNumberTools {
        let supported: Set<String>

        func countries(locale: Locale) -> [PhoneCountry] { [] }
        func country(region: String, locale: Locale) -> PhoneCountry {
            PhoneCountry(region: region, callingCode: 0, name: region)
        }
        func isSupportedRegion(_ region: String?) -> Bool {
            region.map { supported.contains($0.uppercased()) } ?? false
        }
        func exampleHint(region: String) -> String? { nil }
        func normalize(region: String, input: String) -> PhoneResult { .invalid }
        func detectInternational(_ input: String) -> DetectedNumber? { nil }
    }

    func testSeparatorsAreToleratedAndOnlyDigitsAreKept() {
        XCTAssertEqual(PhoneInput.clean("06 12 34 56 78"), "0612345678")
        XCTAssertEqual(PhoneInput.clean("06-12-34-56-78"), "0612345678")
        XCTAssertEqual(PhoneInput.clean("06.12.34.56.78"), "0612345678")
        XCTAssertEqual(PhoneInput.clean("(06) 12/34 56\u{2011}78"), "0612345678")
        XCTAssertEqual(PhoneInput.clean("\u{200E}06 12 34 56 78\u{200F}"), "0612345678")
    }

    func testAPlusIsKeptOnlyAtTheStart() {
        XCTAssertEqual(PhoneInput.clean("+33 6 12 34 56 78"), "+33612345678")
        XCTAssertEqual(PhoneInput.clean("  +33612345678"), "+33612345678")
        XCTAssertNil(PhoneInput.clean("33+612345678"))
        XCTAssertNil(PhoneInput.clean("06+12"))
    }

    func testDigitsOfOtherScriptsAreTurnedIntoOrdinaryDigits() {
        // Chiffres arabes-indiens et devanagari.
        XCTAssertEqual(PhoneInput.clean("\u{0660}\u{0666}\u{0661}\u{0662}"), "0612")
        XCTAssertEqual(PhoneInput.clean("\u{0967}\u{0968}\u{0969}"), "123")
    }

    func testLettersAndOtherSymbolsMakeTheNumberInvalid() {
        XCTAssertNil(PhoneInput.clean("06 12 AB 56"))
        XCTAssertNil(PhoneInput.clean("1-800-FLOWERS"))
        XCTAssertNil(PhoneInput.clean("06 12 34 56 78 #"))
        XCTAssertNil(PhoneInput.clean("06,12"))
    }

    func testAnEmptyOrDigitlessTextOrAHugePasteIsRefused() {
        XCTAssertNil(PhoneInput.clean(""))
        XCTAssertNil(PhoneInput.clean("   "))
        XCTAssertNil(PhoneInput.clean("+"))
        XCTAssertNil(PhoneInput.clean("---"))
        XCTAssertNil(PhoneInput.clean(String(repeating: "1", count: PhoneInput.maxInputLength + 1)))
    }

    func testTheArrowIsActiveAsSoonAsOneDigitIsTyped() {
        XCTAssertFalse(PhoneInput.hasDigit(""))
        XCTAssertFalse(PhoneInput.hasDigit(" +-() "))
        XCTAssertTrue(PhoneInput.hasDigit("6"))
        XCTAssertTrue(PhoneInput.hasDigit("abc 1"))
    }

    func testTheDefaultCountryIsTheDeviceOneElseFrance() {
        let tools = StubTools(supported: ["FR", "BE", "CH"])

        XCTAssertEqual(PhoneInput.defaultRegion(deviceRegion: "be", tools: tools), "BE")
        XCTAssertEqual(PhoneInput.defaultRegion(deviceRegion: "CH", tools: tools), "CH")
        XCTAssertEqual(PhoneInput.defaultRegion(deviceRegion: "ZZ", tools: tools), "FR")
        XCTAssertEqual(PhoneInput.defaultRegion(deviceRegion: nil, tools: tools), "FR")
    }

    func testTheFlagIsBuiltFromTheTwoLettersOfTheRegion() {
        XCTAssertEqual(PhoneCountry(region: "FR", callingCode: 33, name: "France").flag, "\u{1F1EB}\u{1F1F7}")
        XCTAssertEqual(PhoneCountry(region: "fr", callingCode: 33, name: "France").flag, "\u{1F1EB}\u{1F1F7}")
        XCTAssertEqual(PhoneCountry(region: "FR", callingCode: 33, name: "France").dialCode, "+33")
    }

    func testTheCountrySearchIgnoresAccentsAndCase() {
        let canada = PhoneCountry(region: "CA", callingCode: 1, name: "Canada")
        let cote = PhoneCountry(region: "CI", callingCode: 225, name: "Côte d'Ivoire")

        XCTAssertTrue(PhoneInput.matchesSearch(country: cote, query: "cote"))
        XCTAssertTrue(PhoneInput.matchesSearch(country: cote, query: "CÔTE"))
        XCTAssertTrue(PhoneInput.matchesSearch(country: canada, query: "can"))
        XCTAssertFalse(PhoneInput.matchesSearch(country: canada, query: "mexique"))
        XCTAssertTrue(PhoneInput.matchesSearch(country: canada, query: ""))
        XCTAssertTrue(PhoneInput.matchesSearch(country: canada, query: "   "))
    }

    func testTheCountrySearchAlsoFindsByRegionCodeAndCallingCode() {
        let belgium = PhoneCountry(region: "BE", callingCode: 32, name: "Belgique")

        XCTAssertTrue(PhoneInput.matchesSearch(country: belgium, query: "be"))
        XCTAssertTrue(PhoneInput.matchesSearch(country: belgium, query: "+32"))
        XCTAssertTrue(PhoneInput.matchesSearch(country: belgium, query: "32"))
        XCTAssertTrue(PhoneInput.matchesSearch(country: belgium, query: "3"))
        XCTAssertFalse(PhoneInput.matchesSearch(country: belgium, query: "33"))
    }
}
