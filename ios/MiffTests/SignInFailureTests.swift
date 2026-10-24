import XCTest
@testable import Miff

/// Les erreurs de connexion ne révèlent jamais si un e-mail correspond à un compte, et le ton reste neutre.
final class SignInFailureTests: XCTestCase {
    private func authError(_ code: Int) -> NSError {
        NSError(domain: "FIRAuthErrorDomain", code: code)
    }

    func testNetworkProblemsAreReportedAsNetwork() {
        XCTAssertEqual(interpretSignInError(authError(17020)), .network)
        let offline = NSError(domain: NSURLErrorDomain, code: NSURLErrorNotConnectedToInternet)
        let timedOut = NSError(domain: NSURLErrorDomain, code: NSURLErrorTimedOut)
        XCTAssertEqual(interpretSignInError(offline), .network)
        XCTAssertEqual(interpretSignInError(timedOut), .network)
    }

    func testWrongCredentialsAndUnknownAccountsLookTheSame() {
        // 17004 invalidCredential, 17009 wrongPassword, 17011 userNotFound, 17008 invalidEmail.
        let outcomes = [17004, 17009, 17011, 17008].map { interpretSignInError(authError($0)) }

        XCTAssertEqual(outcomes, Array(repeating: SignInFailure.invalidCredentials, count: 4))
    }

    func testDisabledAccountIsDistinguished() {
        XCTAssertEqual(interpretSignInError(authError(17005)), .accountDisabled)
    }

    func testTooManyRequests() {
        XCTAssertEqual(interpretSignInError(authError(17010)), .tooManyAttempts)
    }

    func testAnythingElseIsUnknown() {
        XCTAssertEqual(interpretSignInError(authError(17999)), .unknown)
        XCTAssertEqual(interpretSignInError(NSError(domain: "AutreDomaine", code: 7)), .unknown)
        XCTAssertEqual(interpretSignInError(OperationTimeoutError()), .unknown)
    }

    // MARK: - Contrôle local de la saisie

    func testAcceptsPlausibleInput() {
        XCTAssertNil(CredentialsInput.check(email: "test@example.com", password: "a-strong-password"))
        XCTAssertNil(CredentialsInput.check(email: "  test@example.com  ", password: "x"))
    }

    func testRejectsEmptyOrMalformedInput() {
        XCTAssertEqual(CredentialsInput.check(email: "  ", password: "pw"), .emailEmpty)
        XCTAssertEqual(CredentialsInput.check(email: "a@b.co", password: ""), .passwordEmpty)
        XCTAssertEqual(CredentialsInput.check(email: "not-an-email", password: "pw"), .emailInvalid)
        XCTAssertEqual(CredentialsInput.check(email: "a b@c.de", password: "pw"), .emailInvalid)
    }

    func testRejectsAbsurdlyLongInput() {
        let longEmail = String(repeating: "a", count: 250) + "@b.co"
        let longPassword = String(repeating: "p", count: 2000)
        XCTAssertEqual(CredentialsInput.check(email: longEmail, password: "pw"), .tooLong)
        XCTAssertEqual(CredentialsInput.check(email: "a@b.co", password: longPassword), .tooLong)
    }

    func testEveryFailureHasAKindMessageInBothLanguages() throws {
        // Les messages d'erreur du diagnostic sont rassurants : aucun ne contient de mot alarmiste.
        let keys = [
            "diag_signin_invalid", "diag_signin_network", "diag_signin_too_many",
            "diag_signin_disabled", "diag_signin_unknown"
        ]
        let alarming = ["danger", "alerte", "erreur fatale", "fatal", "critique", "urgent", "alert", "critical"]
        for language in ["fr", "en"] {
            let path = try XCTUnwrap(Bundle.main.path(forResource: language, ofType: "lproj"), language)
            let bundle = try XCTUnwrap(Bundle(path: path))
            for key in keys {
                let text = L10n.string(key, bundle: bundle).lowercased()
                XCTAssertNotEqual(text, key, "\(key) manquant en \(language)")
                for word in alarming {
                    XCTAssertFalse(text.contains(word), "\(key) (\(language)) contient « \(word) »")
                }
            }
        }
    }
}
