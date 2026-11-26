import XCTest
@testable import Miff

/// Le diagnostic interne n'existe que dans les configurations Debug et Internal (critère I13).
/// La preuve pour Release est faite par la CI sur le binaire Release lui-même (ios/ci/check-binaries.sh) ; ce test
/// vérifie que la compilation conditionnelle suit bien le nom de la configuration Xcode.
final class BuildFlavorTests: XCTestCase {
    func testDiagnosticIsPresentExactlyInDebugAndInternalBuilds() {
        let configuration = BuildInfo.current.configuration
        let expected = configuration == "Debug" || configuration == "Internal"

        XCTAssertEqual(BuildFlavor.includesDiagnostic, expected, "Configuration : \(configuration)")
    }

    /// La fenêtre d'avis (`docs/tickets/pop-ins-avis.md`, critère AV2) : même drapeau qu'Android (`REVIEW_PROMPT`), allumé en Debug et Internal,
    /// ÉTEINT en Release. La preuve sur le binaire Release est faite par la CI (`check-binaries.sh release`).
    func testTheReviewPromptIsPresentExactlyInDebugAndInternalBuilds() {
        let configuration = BuildInfo.current.configuration
        let expected = configuration == "Debug" || configuration == "Internal"

        XCTAssertEqual(BuildFlavor.includesReviewPrompt, expected, "Configuration : \(configuration)")
    }

    /// L'alerte « Je suis en danger » (`docs/tickets/alerte-danger.md`) : même drapeau qu'Android (`ALERT_DANGER`), allumé en Debug et Internal, ÉTEINT en Release. La preuve
    /// sur le binaire Release est faite par la CI (`check-binaries.sh release`).
    func testTheAlertIsPresentExactlyInDebugAndInternalBuilds() {
        let configuration = BuildInfo.current.configuration
        let expected = configuration == "Debug" || configuration == "Internal"

        XCTAssertEqual(BuildFlavor.includesAlertDanger, expected, "Configuration : \(configuration)")
    }

    /// L'ACHAT de Miff+ (SRV-8I) : même drapeau qu'Android (`PREMIUM_PURCHASE`), allumé en Debug, ÉTEINT en Release ; en Internal (le build TestFlight), éteint par défaut et allumé
    /// seulement par l'entrée `premium_purchase` du workflow. Le FAUX magasin implique l'achat allumé et n'existe jamais en Release. La preuve sur le binaire Release est faite
    /// par la CI (`check-binaries.sh release`).
    func testThePurchaseIsOnInDebugOffInReleaseAndTheFakeStoreNeverLivesInRelease() {
        let configuration = BuildInfo.current.configuration

        if configuration == "Debug" {
            XCTAssertTrue(BuildFlavor.includesPremiumPurchase)
            XCTAssertTrue(BuildFlavor.includesBillingFake)
        }
        if configuration == "Release" {
            XCTAssertFalse(BuildFlavor.includesPremiumPurchase, "faux en Release par défaut")
            XCTAssertFalse(BuildFlavor.includesBillingFake, "jamais de faux magasin dans un build de publication")
        }
        if BuildFlavor.includesBillingFake {
            XCTAssertTrue(BuildFlavor.includesPremiumPurchase, "le faux magasin n'a de sens que si l'achat est allumé")
            XCTAssertTrue(configuration == "Debug" || configuration == "Internal", "Configuration : \(configuration)")
        }
    }

    func testDiagnosticEventIsAllowedOnlyWhenTheDiagnosticIsPresent() {
        XCTAssertEqual(
            AnalyticsContract.allowedEvents["analytics_diagnostic_test"] != nil,
            BuildFlavor.includesDiagnostic
        )
    }
}
