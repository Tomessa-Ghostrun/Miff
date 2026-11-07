import XCTest
@testable import Miff

/// Règles locales des formulaires d'inscription et de connexion (ticket §7.1 et §7.2) : mêmes cas qu'Android.
final class AuthFormTests: XCTestCase {
    func testEmailFormatAcceptsOrdinaryAddressesAndIgnoresSpacesAroundThem() {
        XCTAssertTrue(AuthForm.isEmailFormatValid("alex@exemple.fr"))
        XCTAssertTrue(AuthForm.isEmailFormatValid("  Alex.Martin+miff@Exemple.COM \n"))
        XCTAssertEqual(AuthForm.normalizeEmail("  alex@exemple.fr "), "alex@exemple.fr")
    }

    func testEmailFormatRejectsMalformedAddresses() {
        for bad in ["", " ", "alex", "alex@", "@exemple.fr", "alex@exemple", "al ex@exemple.fr", "a@@b.fr"] {
            XCTAssertFalse(AuthForm.isEmailFormatValid(bad), "« \(bad) » ne devrait pas passer")
        }
    }

    func testEmailLongerThanTheLimitIsRejected() {
        let local = String(repeating: "a", count: AuthForm.maxEmailLength)
        XCTAssertFalse(AuthForm.isEmailFormatValid("\(local)@exemple.fr"))
    }

    func testPasswordNeedsEightCharactersAndNothingElse() {
        XCTAssertFalse(AuthForm.isPasswordLongEnough("1234567"))
        XCTAssertTrue(AuthForm.isPasswordLongEnough("12345678"))
        // Aucune règle de complexité : huit lettres minuscules suffisent.
        XCTAssertTrue(AuthForm.isPasswordLongEnough("abcdefgh"))
    }

    func testPasswordLengthCountsCharactersTheWayAPersonDoes() {
        // Huit symboles Unicode, dont des accents et un émoji : l'émoji ne compte pas pour deux.
        XCTAssertTrue(AuthForm.isPasswordLongEnough("é\u{1F600}abcdef"))
        XCTAssertFalse(AuthForm.isPasswordLongEnough("é\u{1F600}abcde"))
    }

    func testButtonsAreEnabledAsSoonAsBothFieldsAreFilled() {
        XCTAssertFalse(AuthForm.hasBothFields(email: "", password: "x"))
        XCTAssertFalse(AuthForm.hasBothFields(email: "   ", password: "x"))
        XCTAssertFalse(AuthForm.hasBothFields(email: "a@b.fr", password: ""))
        XCTAssertTrue(AuthForm.hasBothFields(email: "a", password: "x"))
    }

    func testInputIsCut() {
        XCTAssertEqual(AuthForm.limitEmailInput(String(repeating: "a", count: 400)).count, AuthForm.maxEmailLength)
        let longPassword = String(repeating: "a", count: 5000)
        XCTAssertEqual(AuthForm.limitPasswordInput(longPassword).count, AuthForm.maxPasswordLength)
    }
}

/// Limite de confort : 5 échecs de suite, puis une pause de 30 s ; les vieux échecs expirent.
final class LoginThrottleTests: XCTestCase {
    func testFiveWrongAnswersInARowStartAPause() {
        var now = 0
        let throttle = LoginThrottle(nowMillis: { now })

        for _ in 0..<4 {
            throttle.recordWrongCredentials()
            XCTAssertEqual(throttle.remainingPauseMillis(), 0)
        }
        throttle.recordWrongCredentials()

        XCTAssertEqual(throttle.remainingPauseMillis(), LoginThrottle.defaultPauseMillis)
        now += 10_000
        XCTAssertEqual(throttle.remainingPauseMillis(), LoginThrottle.defaultPauseMillis - 10_000)
        now += 20_000
        XCTAssertEqual(throttle.remainingPauseMillis(), 0)
    }

    func testASuccessResetsTheCount() {
        let throttle = LoginThrottle(nowMillis: { 0 })
        for _ in 0..<4 {
            throttle.recordWrongCredentials()
        }
        throttle.recordSuccess()
        throttle.recordWrongCredentials()

        XCTAssertEqual(throttle.remainingPauseMillis(), 0)
    }

    func testOldFailuresExpire() {
        var now = 0
        let throttle = LoginThrottle(nowMillis: { now })
        for _ in 0..<4 {
            throttle.recordWrongCredentials()
        }
        // Une heure plus tard : les quatre premiers échecs ne comptent plus.
        now += 60 * 60_000
        throttle.recordWrongCredentials()

        XCTAssertEqual(throttle.remainingPauseMillis(), 0)
    }
}
