import XCTest
@testable import Miff

/// Un retrait du consentement que le compte ne connaît pas encore (la personne a coupé sans réseau) ne se perd jamais : au démarrage, à la
/// connexion et à la déconnexion, la collecte reste éteinte pour CE compte, quoi que dise encore le serveur (ONB-3b). Mêmes cas
/// qu'Android (`SessionManagerTest`).
@MainActor
final class SessionManagerWithdrawalTests: XCTestCase {
    private let pending = PendingWithdrawals(storage: MemoryWithdrawalStorage(), nowMillis: { 1 })

    private func makeHarness(resolved: TestBox<[String]> = TestBox([])) -> SessionHarness {
        SessionHarness(
            withdrawals: pending,
            onAccountResolved: { uid in resolved.value.append(uid) }
        )
    }

    func testAWaitingWithdrawalWinsOverTheAccountAtAColdStart() async {
        pending.mark(uid: "uid-1")
        let harness = makeHarness()
        harness.gateway.setSignedIn("uid-1")
        // Le serveur dit encore « oui » : il n'a pas encore reçu le retrait.
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.privacy.states, ["pause", "off"])
        XCTAssertFalse(harness.privacy.analyticsEnabled)
        XCTAssertEqual(harness.manager.state, .ready(.feed))
    }

    func testAWaitingWithdrawalWinsOverTheAccountAtSignInAndSendsNoEvent() async {
        pending.mark(uid: "uid-1")
        let harness = makeHarness()
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        await harness.manager.startAndResolve()

        let failure = await harness.manager.signInWithEmail(email: "alex@exemple.fr", password: "motdepasse")

        XCTAssertNil(failure)
        XCTAssertFalse(harness.privacy.analyticsEnabled)
        XCTAssertTrue(harness.sink.names.isEmpty, "aucun événement n'est parti : la personne a retiré son accord")
    }

    func testAWithdrawalWaitingForAnotherAccountDoesNotCutThisOne() async {
        pending.mark(uid: "uid-2")
        let harness = makeHarness()
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        await harness.manager.startAndResolve()

        _ = await harness.manager.signInWithEmail(email: "alex@exemple.fr", password: "motdepasse")

        XCTAssertTrue(harness.privacy.analyticsEnabled)
        XCTAssertEqual(harness.sink.names, ["sign_in_completed"])
        // La marque de l'autre compte est intacte : elle attend son compte.
        XCTAssertTrue(pending.isPending(uid: "uid-2"))
    }

    func testTheWithdrawalIsHandedToTheRecorderOnceTheAccountHasBeenRead() async {
        pending.mark(uid: "uid-1")
        let resolved = TestBox<[String]>([])
        let harness = makeHarness(resolved: resolved)
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)

        await harness.manager.startAndResolve()

        XCTAssertEqual(resolved.value, ["uid-1"])
    }

    func testNothingIsHandedToTheRecorderWhenTheAccountCannotBeRead() async {
        let resolved = TestBox<[String]>([])
        let harness = makeHarness(resolved: resolved)
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.result = .unavailable

        await harness.manager.startAndResolve()

        XCTAssertTrue(resolved.value.isEmpty)
        XCTAssertEqual(harness.privacy.states, ["pause"])
    }

    func testNothingIsHandedToTheRecorderBeforeTheProfileExists() async {
        let resolved = TestBox<[String]>([])
        let harness = makeHarness(resolved: resolved)
        harness.gateway.setSignedIn("uid-1")
        harness.pending.save(PendingConsent(uid: "uid-1", termsVersion: "1.0", analyticsAccepted: true))

        await harness.manager.startAndResolve()

        XCTAssertTrue(resolved.value.isEmpty)
    }

    func testSigningOutKeepsTheWaitingWithdrawalAndCollectsNothing() async {
        pending.mark(uid: "uid-1")
        let harness = makeHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        await harness.manager.startAndResolve()

        await harness.manager.signOut()

        XCTAssertTrue(pending.isPending(uid: "uid-1"), "la marque attend son compte")
        XCTAssertFalse(harness.privacy.analyticsEnabled)
    }

    func testDeletingTheAccountForgetsItsWithdrawalAndOnlyIts() async {
        pending.mark(uid: "uid-1")
        pending.mark(uid: "uid-2")
        let harness = makeHarness()
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        await harness.manager.startAndResolve()

        await harness.manager.eraseAfterAccountDeletion(uid: "uid-1")

        XCTAssertFalse(pending.isPending(uid: "uid-1"))
        XCTAssertTrue(pending.isPending(uid: "uid-2"))
        XCTAssertEqual(harness.privacy.eraseCalls, 1)
    }
}
