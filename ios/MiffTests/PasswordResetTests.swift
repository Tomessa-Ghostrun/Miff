import XCTest
@testable import Miff

/// « Mot de passe oublié » : ce que l'app dit et fait ne dépend JAMAIS de l'existence d'un compte (critères M2 et C6), et la
/// pause de renvoi est par adresse (critère M5).
final class PasswordResetTests: XCTestCase {
    private func authError(_ code: Int) -> NSError {
        NSError(domain: AuthErrorNumber.domain, code: code)
    }

    func testUnknownAndDisabledAccountsLookExactlyLikeASuccess() {
        XCTAssertEqual(interpretPasswordResetError(authError(AuthErrorNumber.userNotFound)), .accepted)
        XCTAssertEqual(interpretPasswordResetError(authError(AuthErrorNumber.userDisabled)), .accepted)
    }

    func testAMalformedAddressSeenByTheServerGetsTheSameWordsAsTheLocalCheck() {
        XCTAssertEqual(interpretPasswordResetError(authError(AuthErrorNumber.invalidEmail)), .emailRejected)
    }

    func testNetworkAndQuotaProblemsAreNeutralFailures() {
        XCTAssertEqual(interpretPasswordResetError(authError(AuthErrorNumber.networkError)), .failed(.network))
        XCTAssertEqual(interpretPasswordResetError(NSError(domain: NSURLErrorDomain, code: -1009)), .failed(.network))
        XCTAssertEqual(
            interpretPasswordResetError(authError(AuthErrorNumber.tooManyRequests)), .failed(.tooManyAttempts))
    }

    func testAnythingElseIsAnUnknownFailureAndLeaksNothing() {
        XCTAssertEqual(interpretPasswordResetError(authError(99_999)), .failed(.unknown))
        XCTAssertEqual(interpretPasswordResetError(NSError(domain: "Autre", code: 17_011)), .failed(.unknown))
    }

    // MARK: - Pause de renvoi

    func testNothingIsPausedBeforeASend() {
        let cooldown = ResendCooldown(nowMillis: { 0 })

        XCTAssertEqual(cooldown.remainingMillis(for: "alex@exemple.fr"), 0)
    }

    func testTheSameAddressWaitsWhateverTheCaseAndTheSpaces() {
        var now = 1_000
        let cooldown = ResendCooldown(nowMillis: { now })
        cooldown.start(for: "Alex@Exemple.fr")

        XCTAssertEqual(cooldown.remainingMillis(for: "  alex@exemple.FR "), ResendCooldown.defaultDurationMillis)
        now += 25_000
        XCTAssertEqual(cooldown.remainingMillis(for: "alex@exemple.fr"), ResendCooldown.defaultDurationMillis - 25_000)
        now += 40_000
        XCTAssertEqual(cooldown.remainingMillis(for: "alex@exemple.fr"), 0)
    }

    func testAPersonWhoFixesATypoDoesNotWaitForTheWrongAddress() {
        let cooldown = ResendCooldown(nowMillis: { 0 })
        cooldown.start(for: "alex@exemple.fr")

        XCTAssertEqual(cooldown.remainingMillis(for: "alex@exemple.com"), 0)
        XCTAssertEqual(cooldown.remainingMillis(for: ""), 0)
    }
}
