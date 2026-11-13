#if REVIEW_PROMPT
import XCTest
@testable import Miff

/// Le formulaire externe de « Peux faire mieux... » (décision du PM du 2026-11-08, adresse donnée le 2026-11-13) : une adresse HTTPS telle
/// quelle, ou rien. Miroir de `FeedbackFormTest` d'Android.
final class ReviewFeedbackFormTests: XCTestCase {
    func testEmptyOrMissingMeansNoForm() {
        XCTAssertNil(ReviewFeedbackForm.openableAddress(""))
        XCTAssertNil(ReviewFeedbackForm.openableAddress("   "))
        XCTAssertNil(ReviewFeedbackForm.openableAddress(nil))
        XCTAssertNil(ReviewFeedbackForm.openableURL(nil))
    }

    func testAnHttpsAddressIsOpenedAsItIs() {
        XCTAssertEqual(
            ReviewFeedbackForm.openableAddress("https://forms.example.org/miff"), "https://forms.example.org/miff"
        )
        XCTAssertEqual(
            ReviewFeedbackForm.openableAddress("  https://forms.example.org/miff?lang=fr#top \n"),
            "https://forms.example.org/miff?lang=fr#top"
        )
    }

    func testAnythingElseIsRefusedNeverClearTextNeverAnotherScheme() {
        let refused = [
            "http://forms.example.org/miff",
            "HTTP://forms.example.org",
            "javascript:alert(1)",
            "miff://forms.example.org",
            "itms-apps://apps.apple.com/app/id1",
            "mailto:a@b.c",
            "forms.example.org/miff",
            "https://",
            "https:///path",
            "https://forms.example.org/a b",
            "file:///var/mobile/x"
        ]
        for bad in refused {
            XCTAssertNil(ReviewFeedbackForm.openableAddress(bad), bad)
            XCTAssertNil(ReviewFeedbackForm.openableURL(bad), bad)
        }
    }

    // MARK: - L'adresse donnée par le PM

    func testTheAddressGivenByThePmIsOpenedAsItIsWithNothingAppended() throws {
        let address = "https://forms.gle/5EEH4sEfsnCCLzEx7"

        XCTAssertEqual(ReviewFeedbackForm.configuredAddress, address)
        XCTAssertEqual(ReviewFeedbackForm.openableAddress(address), address)
        XCTAssertEqual(ReviewFeedbackForm.openableAddress("  \(address) \n"), address)
        let url = try XCTUnwrap(ReviewFeedbackForm.openableURL(ReviewFeedbackForm.configuredAddress))
        XCTAssertEqual(url.absoluteString, address)
        XCTAssertEqual(url.scheme, "https")
        XCTAssertEqual(url.host, "forms.gle")
        // Ni paramètre, ni fragment : rien de la personne (nom, e-mail, Miff, position, identifiant, jeton) ne peut y être.
        let components = try XCTUnwrap(URLComponents(url: url, resolvingAgainstBaseURL: false))
        XCTAssertNil(components.query)
        XCTAssertNil(components.fragment)
        XCTAssertNil(components.user)
    }

    func testTheAddressIsWrittenInOnePlaceOnly() throws {
        let sources = try ReviewSourceFiles.swiftFiles(under: "Miff")
        let writing = sources.filter { $0.text.contains("forms.gle") }.map(\.path)

        XCTAssertEqual(writing, ["Models/Review/ReviewPromptRules.swift"])
    }
}
#endif
