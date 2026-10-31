import XCTest
@testable import Miff

/// Interprétation des erreurs d'authentification : jamais de code brut affiché, un seul message pour « adresse inconnue »
/// et « mauvais mot de passe », et « identifiants invalides » ne vaut « mauvais mot de passe » que pour une connexion par
/// e-mail (mêmes cas qu'Android).
final class AuthFailureTests: XCTestCase {
    private func authError(_ code: Int) -> NSError {
        NSError(domain: AuthErrorNumber.domain, code: code)
    }

    func testWrongPasswordAndUnknownAccountGiveTheSameAnswerForAnEmailSignIn() {
        let codes = [
            AuthErrorNumber.invalidCredential,
            AuthErrorNumber.wrongPassword,
            AuthErrorNumber.userNotFound,
            AuthErrorNumber.invalidEmail
        ]
        for code in codes {
            XCTAssertEqual(interpretAuthError(authError(code), attempt: .signInEmail), .invalidCredentials, "\(code)")
        }
    }

    func testInvalidCredentialIsNotAWrongPasswordForAGoogleOrAppleToken() {
        XCTAssertEqual(interpretAuthError(authError(AuthErrorNumber.invalidCredential), attempt: .social), .unknown)
        let invalid = authError(AuthErrorNumber.invalidCredential)
        XCTAssertEqual(interpretAuthError(invalid, attempt: .signUpEmail), .unknown)
        XCTAssertEqual(interpretAuthError(authError(AuthErrorNumber.wrongPassword), attempt: .deleteAccount), .unknown)
    }

    func testAnAddressAlreadyInUseIsOnlyShownAtSignUp() {
        let inUse = authError(AuthErrorNumber.emailAlreadyInUse)

        XCTAssertEqual(interpretAuthError(inUse, attempt: .signUpEmail), .emailAlreadyInUse)
        XCTAssertEqual(interpretAuthError(inUse, attempt: .signInEmail), .linkedToOtherMethod)
        XCTAssertEqual(interpretAuthError(inUse, attempt: .social), .linkedToOtherMethod)
    }

    func testAnAccountLinkedToAnotherMethodGivesAGuidingMessage() {
        let different = authError(AuthErrorNumber.accountExistsWithDifferentCredential)
        let alreadyUsed = authError(AuthErrorNumber.credentialAlreadyInUse)

        XCTAssertEqual(interpretAuthError(different, attempt: .social), .linkedToOtherMethod)
        XCTAssertEqual(interpretAuthError(alreadyUsed, attempt: .social), .linkedToOtherMethod)
    }

    func testNetworkTooManyAttemptsDisabledAndRecentLoginAreSorted() {
        XCTAssertEqual(interpretAuthError(authError(AuthErrorNumber.networkError), attempt: .signInEmail), .network)
        XCTAssertEqual(interpretAuthError(NSError(domain: NSURLErrorDomain, code: -1009), attempt: .social), .network)
        XCTAssertEqual(
            interpretAuthError(authError(AuthErrorNumber.tooManyRequests), attempt: .signInEmail), .tooManyAttempts)
        XCTAssertEqual(
            interpretAuthError(authError(AuthErrorNumber.userDisabled), attempt: .signInEmail), .accountDisabled)
        XCTAssertEqual(
            interpretAuthError(authError(AuthErrorNumber.requiresRecentLogin), attempt: .deleteAccount),
            .recentLoginRequired)
    }

    func testAnythingElseIsUnknownAndLeaksNothing() {
        XCTAssertEqual(interpretAuthError(authError(99_999), attempt: .signInEmail), .unknown)
        let foreign = NSError(domain: "AutreDomaine", code: 17_004)
        XCTAssertEqual(interpretAuthError(foreign, attempt: .signInEmail), .unknown)
    }
}

final class AuthMessageTests: XCTestCase {
    func testEveryFailureHasAKindMessage() {
        XCTAssertEqual(AuthFailure.invalidCredentials.toMessage(), .invalidCredentials)
        XCTAssertEqual(AuthFailure.network.toMessage(), .network)
        XCTAssertEqual(AuthFailure.tooManyAttempts.toMessage(), .tooManyAttempts)
        XCTAssertEqual(AuthFailure.accountDisabled.toMessage(), .accountUnavailable)
        XCTAssertEqual(AuthFailure.emailAlreadyInUse.toMessage(), .emailInUse)
        XCTAssertEqual(AuthFailure.linkedToOtherMethod.toMessage(), .otherMethod)
        XCTAssertEqual(AuthFailure.recentLoginRequired.toMessage(), .unknown)
        XCTAssertEqual(AuthFailure.unknown.toMessage(), .unknown)
    }

    func testResetAdviceOnlyStaysOnTheEmailSignIn() {
        XCTAssertEqual(AuthMessage.tooManyAttempts.withoutResetAdvice(), .tooManyRequests)
        XCTAssertEqual(AuthMessage.network.withoutResetAdvice(), .network)
        XCTAssertEqual(AuthMessage.invalidCredentials.withoutResetAdvice(), .invalidCredentials)
    }

    func testClosingTheSocialDialogSaysNothing() {
        XCTAssertNil(SocialTokenResult.cancelled.message)
        XCTAssertNil(SocialTokenResult.credential(.google(idToken: "a", accessToken: "b")).message)
        XCTAssertEqual(SocialTokenResult.unavailable.message, .unknown)
        XCTAssertEqual(SocialTokenResult.failed(.network).message, .network)
    }

    func testEmailFieldErrorOnlyAppearsOnceSomethingIsTyped() {
        XCTAssertFalse(emailFieldHasError(""))
        XCTAssertFalse(emailFieldHasError("   "))
        XCTAssertTrue(emailFieldHasError("alex@"))
        XCTAssertFalse(emailFieldHasError("alex@exemple.fr"))
    }
}

final class SocialSignInSupportTests: XCTestCase {
    func testClosingTheGoogleSheetIsACancellation() {
        let closed = NSError(domain: SocialSignInErrors.googleDomain, code: SocialSignInErrors.googleCanceledCode)
        let other = NSError(domain: SocialSignInErrors.googleDomain, code: -1)

        XCTAssertEqual(SocialSignInErrors.googleResult(for: closed), .cancelled)
        XCTAssertEqual(SocialSignInErrors.googleResult(for: other), .failed(.unknown))
        let offline = NSError(domain: NSURLErrorDomain, code: -1009)
        XCTAssertEqual(SocialSignInErrors.googleResult(for: offline), .failed(.network))
    }

    func testClosingTheAppleSheetIsACancellation() {
        let closed = NSError(domain: SocialSignInErrors.appleDomain, code: SocialSignInErrors.appleCanceledCode)
        let failed = NSError(domain: SocialSignInErrors.appleDomain, code: 1_004)

        XCTAssertEqual(SocialSignInErrors.appleResult(for: closed), .cancelled)
        XCTAssertEqual(SocialSignInErrors.appleResult(for: failed), .failed(.unknown))
    }

    func testTheNonceUsesOnlyTheExpectedCharactersAndTheRequestedLength() {
        let bytes: [UInt8] = (0..<32).map { UInt8($0 * 8 % 256) }
        let nonce = NonceGenerator.make(length: 32) { _ in bytes }

        XCTAssertEqual(nonce.count, 32)
        let allowed = Set(NonceGenerator.charset)
        XCTAssertTrue(nonce.allSatisfy { allowed.contains($0) })
        XCTAssertEqual(NonceGenerator.charset.count, 64)
    }

    func testEveryByteValueMapsToACharacterWithoutBias() {
        // 256 octets possibles pour 64 caractères : chaque caractère revient exactement quatre fois.
        let all = (0..<256).map { UInt8($0) }
        let text = NonceGenerator.make(length: 256) { _ in all }

        let counts = Dictionary(grouping: text, by: { $0 }).mapValues { $0.count }
        XCTAssertEqual(counts.count, 64)
        XCTAssertTrue(counts.values.allSatisfy { $0 == 4 })
    }

    func testASocialCredentialNeverPrintsItsTokens() {
        let credential = SocialCredential.google(idToken: "SECRET-ID", accessToken: "SECRET-ACCESS")

        XCTAssertFalse("\(credential)".contains("SECRET"))
        let apple = SocialCredential.apple(idToken: "SECRET", rawNonce: "N", name: nil)
        XCTAssertFalse(String(describing: apple).contains("SECRET"))
    }
}
