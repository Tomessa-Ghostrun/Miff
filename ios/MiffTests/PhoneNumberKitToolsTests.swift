import XCTest
@testable import Miff

/// Les règles de numéro de l'Onboarding (ticket §6.4) sur la vraie bibliothèque (PhoneNumberKit, mêmes données que le
/// libphonenumber d'Android) : mêmes cas qu'Android.
final class PhoneNumberKitToolsTests: XCTestCase {
    private let tools = PhoneNumberKitTools()

    private func e164(_ region: String, _ input: String) -> String? {
        if case .valid(let number, _) = tools.normalize(region: region, input: input) {
            return number
        }
        return nil
    }

    func testAFrenchMobileIsNormalisedToE164WhateverTheFormatting() {
        let inputs = [
            "06 12 34 56 78", "0612345678", "6 12 34 56 78", "06-12-34-56-78", "06.12.34.56.78", "(06) 12 34 56 78"
        ]
        for input in inputs {
            XCTAssertEqual(e164("FR", input), "+33612345678", input)
        }
    }

    func testTheExampleOfTheMockupIsAValidMobile() {
        // Le numéro d'exemple de la maquette -6 (07 83 30 00 44) est en fait un mobile français valide.
        XCTAssertEqual(e164("FR", "07 83 30 00 44"), "+33783300044")
    }

    func testAnInternationalNumberChoosesItsOwnCountry() {
        guard case .valid(let number, let region) = tools.normalize(region: "FR", input: "+32 470 12 34 56") else {
            return XCTFail("un mobile belge devrait être valide")
        }

        XCTAssertEqual(number, "+32470123456")
        XCTAssertEqual(region, "BE")
        XCTAssertEqual(e164("US", "+33 6 12 34 56 78"), "+33612345678")
    }

    func testLandlinesAreAcceptedToo() {
        XCTAssertEqual(e164("FR", "01 42 68 53 00"), "+33142685300")
    }

    func testInvalidInputIsRefused() {
        for input in ["", "123", "06 12 34", "06 12 34 56 78 90", "abc", "06 12 34 56 7a", "+", "0000000000"] {
            XCTAssertEqual(tools.normalize(region: "FR", input: input), .invalid, input)
        }
    }

    func testPremiumRateAndTollFreeNumbersAreRefusedWithAPreciseReason() {
        // 0899 (surtaxé) et 0800 (gratuit) : valides mais pas de « vrais » numéros de personne.
        XCTAssertEqual(tools.normalize(region: "FR", input: "08 99 12 34 56"), .unsupportedType)
        XCTAssertEqual(tools.normalize(region: "FR", input: "0 800 12 34 56"), .unsupportedType)
    }

    func testTheCountryListIsCompleteSortedAndInTheLanguageOfThePhone() {
        let french = tools.countries(locale: Locale(identifier: "fr_FR"))
        let english = tools.countries(locale: Locale(identifier: "en_US"))

        XCTAssertGreaterThan(french.count, 200)
        XCTAssertEqual(french.count, english.count)
        XCTAssertEqual(french.first { $0.region == "DE" }?.name, "Allemagne")
        XCTAssertEqual(english.first { $0.region == "DE" }?.name, "Germany")
        XCTAssertEqual(french.first { $0.region == "FR" }?.callingCode, 33)
        XCTAssertTrue(french.allSatisfy { $0.region.count == 2 })
        let names = french.map { $0.name }
        XCTAssertEqual(names, names.sorted { $0.compare($1, locale: Locale(identifier: "fr_FR")) == .orderedAscending })
    }

    func testSupportedRegionsAndTheClosedSelector() {
        XCTAssertTrue(tools.isSupportedRegion("FR"))
        XCTAssertTrue(tools.isSupportedRegion("fr"))
        XCTAssertFalse(tools.isSupportedRegion("ZZ"))
        XCTAssertFalse(tools.isSupportedRegion("001"))
        XCTAssertFalse(tools.isSupportedRegion(nil))
        let france = tools.country(region: "FR", locale: Locale(identifier: "fr_FR"))
        XCTAssertEqual(france.dialCode, "+33")
        XCTAssertEqual(france.name, "France")
    }

    func testTheExampleHintHasNeitherTheCallingCodeNorTheFirstZero() {
        XCTAssertEqual(tools.exampleHint(region: "FR")?.first, "6")
        XCTAssertFalse(tools.exampleHint(region: "FR")?.hasPrefix("+") ?? true)
    }

    func testPastingAPlusNumberSelectsItsCountryAndKeepsTheNationalPart() {
        let detected = tools.detectInternational("+33 6 12 34 56 78")

        XCTAssertEqual(detected, DetectedNumber(region: "FR", national: "612345678"))
        XCTAssertNil(tools.detectInternational("06 12 34 56 78"))
        XCTAssertNil(tools.detectInternational("+"))
        XCTAssertNil(tools.detectInternational("+33"))
        XCTAssertNil(tools.detectInternational("+abc"))
    }

    func testTheDefaultCountryComesFromTheDeviceElseFrance() {
        XCTAssertEqual(PhoneInput.defaultRegion(deviceRegion: "BE", tools: tools), "BE")
        XCTAssertEqual(PhoneInput.defaultRegion(deviceRegion: "ZZ", tools: tools), "FR")
        XCTAssertEqual(PhoneInput.defaultRegion(deviceRegion: nil, tools: tools), "FR")
    }
}
