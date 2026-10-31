import XCTest
@testable import Miff

/// Session après une réinstallation, déconnexion qui échoue, nom donné une seule fois par Apple.
@MainActor
final class SessionSafetyTests: XCTestCase {
    // MARK: - Réinstallation (le Trousseau garde la session, pas les réglages de l'app)

    func testASessionFoundAtTheFirstLaunchOfAnInstallationIsClosedBeforeAnyRouting() async {
        let harness = SessionHarness(freshInstall: true)
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.gateway.signOutCalls, 1)
        XCTAssertNil(harness.gateway.currentUid)
        XCTAssertEqual(harness.manager.state, .ready(.signIn))
        XCTAssertTrue(harness.flags.value(forKey: SessionManager.keyInstallMarker, default: false))
        // Le compte n'a même pas été lu : rien n'a été collecté ni envoyé.
        XCTAssertEqual(harness.accounts.loadCalls, 0)
        XCTAssertTrue(harness.sink.names.isEmpty)
    }

    func testTheSessionIsKeptOnLaterLaunchesOfTheSameInstallation() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.gateway.signOutCalls, 0)
        XCTAssertEqual(harness.manager.state, .ready(.feed))
    }

    func testTheFirstLaunchWithoutASessionOnlyLeavesTheMarker() async {
        let harness = SessionHarness(freshInstall: true)

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.gateway.signOutCalls, 0)
        XCTAssertEqual(harness.manager.state, .ready(.signUp))
        XCTAssertTrue(harness.flags.value(forKey: SessionManager.keyInstallMarker, default: false))
    }

    // MARK: - Déconnexion qui échoue

    func testASignOutThatFailsOnceIsRetried() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.gateway.signOutFailures = 1
        await harness.manager.startAndResolve()

        await harness.manager.signOut()

        XCTAssertNil(harness.gateway.currentUid)
        XCTAssertEqual(harness.gateway.signOutCalls, 2)
    }

    func testNoSignInStartsWhileAnOldSessionCannotBeClosed() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-old")
        harness.gateway.signOutFailures = 5

        let email = await harness.manager.signInWithEmail(email: "a@b.fr", password: "motdepasse")
        let created = await harness.manager.createAccountWithEmail(
            email: "a@b.fr", password: "motdepasse", consent: .implicit)
        let social = await harness.manager.signIn(
            with: .google(idToken: "id", accessToken: "acc"), signUpConsent: nil)

        XCTAssertEqual(email, .unknown)
        XCTAssertEqual(created, .unknown)
        XCTAssertEqual(social, .unknown)
        XCTAssertEqual(harness.gateway.signInCalls, 0)
        XCTAssertEqual(harness.gateway.createCalls, 0)
        XCTAssertEqual(harness.gateway.socialCalls, 0)
    }

    func testASignInStartsOnceTheOldSessionIsFinallyClosed() async {
        let harness = SessionHarness()
        harness.gateway.setSignedIn("uid-old")
        harness.gateway.signOutFailures = 1
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)

        let failure = await harness.manager.signInWithEmail(email: "a@b.fr", password: "motdepasse")

        XCTAssertNil(failure)
        XCTAssertEqual(harness.gateway.signInCalls, 1)
    }

    // MARK: - Nom donné une seule fois par Apple

    func testTheNameGivenByAppleIsKeptWhenALaterSignInCarriesNone() {
        var memory = AppleNameMemory()
        let first = PersonNameParts(givenName: "Camille", familyName: "Martin")

        memory.record(first)
        memory.record(nil)

        XCTAssertEqual(memory.name, first)
    }

    func testANewNameReplacesTheOldOneAndForgettingClearsIt() {
        var memory = AppleNameMemory()
        memory.record(PersonNameParts(givenName: "A", familyName: "B"))
        let second = PersonNameParts(givenName: "C", familyName: "D")

        memory.record(second)
        XCTAssertEqual(memory.name, second)

        memory.forget()
        XCTAssertNil(memory.name)
    }

    func testTheAuthorizationCodeNeverAppearsInTheDescription() {
        let credential = SocialCredential.apple(
            idToken: "TOKEN", rawNonce: "NONCE", name: nil, authorizationCode: "CODE-SECRET")

        XCTAssertFalse(String(describing: credential).contains("CODE-SECRET"))
        XCTAssertFalse("\(credential)".contains("TOKEN"))
    }
}
