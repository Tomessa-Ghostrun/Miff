import XCTest
@testable import Miff

/// Routage au lancement (ticket Authentification §5, Onboarding §6.2) : la destination se déduit de l'ÉTAT DU COMPTE.
final class AppRoutingTests: XCTestCase {
    private func route(
        signedIn: Bool = true,
        account: AccountSnapshot? = nil,
        pendingTerms: Bool = false,
        returning: Bool = true
    ) -> AppDestination {
        resolveDestination(signedIn: signedIn, account: account, hasPendingTerms: pendingTerms, returningUser: returning)
    }

    private func account(
        profile: Bool = true,
        membership: Bool = false,
        phone: Bool = true,
        terms: Bool = true
    ) -> AccountSnapshot {
        AccountSnapshot(
            profileExists: profile,
            hasMembership: membership,
            analyticsConsentEnabled: false,
            hasPhone: phone,
            hasTermsVersion: terms
        )
    }

    func testNoSessionOpensSignUpTheFirstTimeAndSignInAfterwards() {
        XCTAssertEqual(route(signedIn: false, returning: false), .signUp)
        XCTAssertEqual(route(signedIn: false, returning: true), .signIn)
    }

    func testASessionWithoutProfileAndWithoutRecordedTermsOpensTheConsentPanel() {
        XCTAssertEqual(route(account: nil), .consentPanel)
        XCTAssertEqual(route(account: account(profile: false)), .consentPanel)
    }

    func testASessionWithoutProfileButWithRecordedTermsOpensTheProfileStep() {
        XCTAssertEqual(route(account: account(profile: false), pendingTerms: true), .profile)
    }

    func testAProfileWithoutPhoneOpensThePhoneStepAlone() {
        XCTAssertEqual(route(account: account(phone: false)), .phoneOnly)
        // Même avec une Miff : le numéro est obligatoire.
        XCTAssertEqual(route(account: account(membership: true, phone: false)), .phoneOnly)
    }

    func testAProfileAndPhoneWithoutMiffOpensTheChoiceAndNeverTheProfileAgain() {
        XCTAssertEqual(route(account: account()), .miffChoice)
    }

    func testAProfileAndAMiffOpenTheFeed() {
        XCTAssertEqual(route(account: account(membership: true)), .feed)
    }

    func testTheCarouselOnlyShowsOnTheVeryFirstOpening() {
        XCTAssertTrue(shouldShowWelcomeCarousel(start: .signUp, carouselSeen: false))
        XCTAssertFalse(shouldShowWelcomeCarousel(start: .signUp, carouselSeen: true))
        // Jamais après une déconnexion, jamais avec une session.
        XCTAssertFalse(shouldShowWelcomeCarousel(start: .signIn, carouselSeen: false))
        XCTAssertFalse(shouldShowWelcomeCarousel(start: .feed, carouselSeen: false))
        XCTAssertFalse(shouldShowWelcomeCarousel(start: .consentPanel, carouselSeen: false))
    }

    func testOnlySignedOutDestinationsDoNotRequireASession() {
        XCTAssertFalse(AppDestination.signUp.requiresSession)
        XCTAssertFalse(AppDestination.signIn.requiresSession)
        for destination in [AppDestination.consentPanel, .profile, .phoneOnly, .miffChoice, .feed] {
            XCTAssertTrue(destination.requiresSession, "\(destination)")
        }
    }

    // MARK: - Lecture du profil

    func testSnapshotReadsTheRawProfileFields() {
        let full = AccountSnapshot.fromProfileFields(
            profileExists: true,
            hasMembership: true,
            analyticsConsentEnabled: true,
            phone: "+33612345678",
            termsVersion: "0.1"
        )
        XCTAssertTrue(full.hasPhone)
        XCTAssertTrue(full.hasTermsVersion)
        XCTAssertTrue(full.hasMembership)
        XCTAssertTrue(full.analyticsConsentEnabled)
    }

    func testAPhoneCountsOnlyIfItHasTheShapeOfTheRules() {
        func hasPhone(_ phone: Any?) -> Bool {
            AccountSnapshot.fromProfileFields(
                profileExists: true,
                hasMembership: false,
                analyticsConsentEnabled: false,
                phone: phone,
                termsVersion: "0.1"
            ).hasPhone
        }
        XCTAssertFalse(hasPhone(nil))
        XCTAssertFalse(hasPhone(""))
        XCTAssertFalse(hasPhone("0612345678"))
        XCTAssertFalse(hasPhone("+0612345678"))
        XCTAssertFalse(hasPhone("+33 6 12 34 56 78"))
        XCTAssertFalse(hasPhone(33_612_345_678))
        XCTAssertFalse(hasPhone("+33612345678\n"))
        XCTAssertTrue(hasPhone("+33612345678"))
    }

    func testTermsVersionCountsOnlyIfItIsANonEmptyString() {
        func hasTerms(_ value: Any?) -> Bool {
            AccountSnapshot.fromProfileFields(
                profileExists: true,
                hasMembership: false,
                analyticsConsentEnabled: false,
                phone: "+33612345678",
                termsVersion: value
            ).hasTermsVersion
        }
        XCTAssertFalse(hasTerms(nil))
        XCTAssertFalse(hasTerms(""))
        XCTAssertFalse(hasTerms(1))
        XCTAssertTrue(hasTerms("0.1"))
    }

    func testWithoutAProfileNothingElseCounts() {
        let none = AccountSnapshot.fromProfileFields(
            profileExists: false,
            hasMembership: true,
            analyticsConsentEnabled: true,
            phone: "+33612345678",
            termsVersion: "0.1"
        )
        XCTAssertFalse(none.hasMembership)
        XCTAssertFalse(none.analyticsConsentEnabled)
        XCTAssertFalse(none.hasPhone)
        XCTAssertFalse(none.hasTermsVersion)
    }

    // MARK: - Liens légaux

    func testLegalUrlNeverHasADoubleOrMissingSlash() {
        XCTAssertEqual(legalPageUrl(baseUrl: "https://a.fr", path: "/fr/cgu"), "https://a.fr/fr/cgu")
        XCTAssertEqual(legalPageUrl(baseUrl: "https://a.fr/", path: "/fr/cgu"), "https://a.fr/fr/cgu")
        XCTAssertEqual(legalPageUrl(baseUrl: "https://a.fr", path: "fr/cgu"), "https://a.fr/fr/cgu")
        XCTAssertEqual(legalPageUrl(baseUrl: "https://a.fr//", path: "//fr/cgu"), "https://a.fr/fr/cgu")
    }
}
