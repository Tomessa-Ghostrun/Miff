import XCTest
@testable import Miff

/// La suppression d'un compte vue de la session (AUTH-5c, miroir de `SessionManagerAccountDeletionTest` d'Android) : le marqueur prime
/// sur le routage (critères SC27 à SC29), l'appareil est fermé avant la première écriture, tout s'arrête, le nettoyage local est complet
/// et le marqueur part en dernier, une annulation remet l'app sur ses rails.
@MainActor
final class SessionManagerAccountDeletionTests: XCTestCase {
    private let timeline = Timeline()

    private func makeHarness(
        marker: MemoryDeletionMarker?,
        teardown: [SessionTeardown] = [],
        localData: [LocalDataEraser] = []
    ) -> SessionHarness {
        SessionHarness(teardown: teardown, deletionMarker: marker, localData: localData, timeline: timeline)
    }

    // MARK: - Le marqueur prime sur le routage

    func testAMarkerOfTheSameAccountOpensTheDeletionScreensAndNeverTheProfileStep() async {
        let marker = MemoryDeletionMarker(timeline: timeline, mark: AccountDeletionMark(uid: "uid-1"))
        let harness = makeHarness(marker: marker)
        harness.gateway.setSignedIn("uid-1")
        // Le profil est déjà supprimé : sans la priorité du marqueur, la personne irait vers « crée ton profil ».
        harness.accounts.setLoaded(profileExists: false)

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.manager.state, .ready(.accountDeletion))
        XCTAssertEqual(harness.accounts.loadCalls, 0, "le compte n'est même pas lu")
        XCTAssertTrue(harness.remote.writtenIds.isEmpty, "aucune session d'appareil pour un compte qui disparaît")
        XCTAssertEqual(harness.remote.readCalls, 0)
    }

    func testTheDeviceIsClosedWithItsMarkerOnTheDiskBeforeAnythingIsStopped() async {
        let marker = MemoryDeletionMarker(timeline: timeline, mark: AccountDeletionMark(uid: "uid-1"))
        let closedAtStop = TestBox(false)
        let store = TestBox<MemoryDeviceIdStore?>(nil)
        let stopping: SessionTeardown = { closedAtStop.value = store.value?.revoked == true }
        let harness = makeHarness(marker: marker, teardown: [stopping])
        store.value = harness.deviceStore
        harness.gateway.setSignedIn("uid-1")

        await harness.manager.startAndResolve()

        XCTAssertTrue(closedAtStop.value, "marqueur « fermé » sur le disque AVANT l'arrêt des écoutes et du suivi")
        XCTAssertTrue(harness.deviceStore.revoked)
        XCTAssertNil(harness.device.currentDeviceId)
    }

    func testEverythingStopsWhenTheDeletionBegins() async {
        let marker = MemoryDeletionMarker(timeline: timeline, mark: AccountDeletionMark(uid: "uid-1"))
        let stopped = TestBox(0)
        let harness = makeHarness(marker: marker, teardown: [{ stopped.value += 1 }])
        harness.gateway.setSignedIn("uid-1")

        await harness.manager.startAndResolve()
        await harness.manager.beginAccountDeletion()

        XCTAssertGreaterThanOrEqual(stopped.value, 2, "idempotent : aussi appelé à la reprise")
        XCTAssertEqual(harness.manager.state, .ready(.accountDeletion))
    }

    func testTheMarkerOfAnotherAccountIsIgnoredAndErased() async {
        let marker = MemoryDeletionMarker(timeline: timeline, mark: AccountDeletionMark(uid: "someone-else"))
        let harness = makeHarness(marker: marker)
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)

        await harness.manager.startAndResolve()

        XCTAssertNil(marker.mark)
        XCTAssertEqual(harness.manager.state, .ready(.feed))
    }

    func testNoSessionAndNoProofOfDeletionKeepsTheMarkerAndSaysNothingIsDeleted() async {
        let marker = MemoryDeletionMarker(timeline: timeline, mark: AccountDeletionMark(uid: "uid-1"))
        let harness = makeHarness(marker: marker)

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.manager.state, .ready(.signIn))
        XCTAssertEqual(harness.manager.consumeNotice(), .deletionPending)
        XCTAssertNotNil(marker.mark, "se reconnecter avec le même compte reprend la suppression")
        XCTAssertEqual(harness.privacy.eraseCalls, 0)
    }

    func testNoSessionButFirebaseConfirmedTheDeletionIsASuccessCleanedUpAndSaidOnce() async {
        let marker = MemoryDeletionMarker(
            timeline: timeline, mark: AccountDeletionMark(uid: "uid-1", accountDeleted: true)
        )
        let erased = TestBox<[String]>([])
        let eraser: LocalDataEraser = { uid in erased.value.append(uid) }
        let harness = makeHarness(marker: marker, localData: [eraser])

        await harness.manager.startAndResolve()

        XCTAssertEqual(harness.manager.state, .ready(.signUp))
        XCTAssertTrue(harness.manager.hasAccountDeletedNotice)
        XCTAssertTrue(harness.manager.consumeAccountDeletedNotice())
        XCTAssertFalse(harness.manager.consumeAccountDeletedNotice(), "une seule fois")
        // Le carrousel de bienvenue revient (décision du PM).
        XCTAssertTrue(harness.manager.consumeCarouselAfterDeletion())
        XCTAssertFalse(harness.manager.consumeCarouselAfterDeletion())
        XCTAssertNil(marker.mark)
        XCTAssertEqual(erased.value, ["uid-1"])
        XCTAssertEqual(harness.privacy.eraseCalls, 1)
    }

    // MARK: - Le nettoyage de la fin

    func testTheLocalCleaningIsCompleteAndTheMarkerGoesLast() async {
        let marker = MemoryDeletionMarker(timeline: timeline, mark: AccountDeletionMark(uid: "uid-1"))
        let timeline = self.timeline
        let eraser: LocalDataEraser = { _ in timeline.add("localData") }
        let harness = makeHarness(marker: marker, localData: [eraser])
        harness.gateway.setSignedIn("uid-1")
        await harness.manager.startAndResolve()
        harness.pending.save(PendingConsent(uid: "uid-1", termsVersion: "v", analyticsAccepted: true))
        let generation = harness.manager.generation
        timeline.add("---")

        await harness.manager.eraseAfterAccountDeletion(uid: "uid-1")

        let after = Array(timeline.entries.drop { $0 != "---" }.dropFirst())
        let markPending = after.firstIndex(of: "markPending") ?? Int.max
        let purge = after.firstIndex(of: "purge") ?? Int.min
        let localData = after.firstIndex(of: "localData") ?? Int.min
        let signOut = after.firstIndex(of: "signOut") ?? Int.min
        let markerClear = after.firstIndex(of: "markerClear") ?? Int.min
        XCTAssertLessThan(markPending, purge, "le drapeau de purge est posé AVANT la purge")
        XCTAssertLessThan(purge, localData)
        XCTAssertLessThan(localData, signOut)
        XCTAssertLessThan(signOut, markerClear, "le marqueur part EN DERNIER")
        XCTAssertEqual(after.last, "markerClear")
        XCTAssertEqual(harness.privacy.eraseCalls, 1)
        XCTAssertGreaterThan(harness.drafts.clearCalls, 0)
        XCTAssertNil(harness.pending.stored)
        XCTAssertGreaterThan(harness.manager.generation, generation)
        XCTAssertNil(harness.deviceStore.stored)
        XCTAssertTrue(harness.deviceStore.revoked)
        XCTAssertNil(harness.gateway.currentUid)
    }

    func testEveryLocalEraserIsTriedWhateverTheOthersDo() async {
        let marker = MemoryDeletionMarker(timeline: timeline, mark: AccountDeletionMark(uid: "uid-1"))
        let calls = TestBox(0)
        let erasers: [LocalDataEraser] = [{ _ in calls.value += 1 }, { _ in calls.value += 10 }]
        let harness = makeHarness(marker: marker, localData: erasers)
        harness.gateway.setSignedIn("uid-1")

        await harness.manager.eraseAfterAccountDeletion(uid: "uid-1")

        XCTAssertEqual(calls.value, 11)
    }

    func testTheEndOfTheDeletionLeadsToTheCarouselThenSignUp() async {
        let marker = MemoryDeletionMarker(timeline: timeline, mark: AccountDeletionMark(uid: "uid-1"))
        let harness = makeHarness(marker: marker)
        harness.flags.set(true, forKey: SessionManager.keyReturningUser)

        harness.manager.leaveAfterAccountDeletion()

        XCTAssertEqual(harness.manager.state, .ready(.signUp))
        XCTAssertTrue(harness.manager.consumeCarouselAfterDeletion())
        XCTAssertFalse(harness.manager.hasAccountDeletedNotice, "jamais la bannière après l'écran de fin")
    }

    // MARK: - Annulation, session fermée, reconnexion

    func testACancelledDeletionPutsTheAppBackOnItsRailsAndSaysItOnce() async {
        let marker = MemoryDeletionMarker(timeline: timeline, mark: AccountDeletionMark(uid: "uid-1"))
        let harness = makeHarness(marker: marker)
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        await harness.manager.startAndResolve()
        XCTAssertEqual(harness.manager.state, .ready(.accountDeletion))
        let generation = harness.manager.generation

        await harness.manager.cancelAccountDeletion()

        XCTAssertNil(marker.mark, "la session efface le marqueur, après avoir rouvert l'appareil")
        let written = timeline.entries.firstIndex(of: "sessionWrite") ?? Int.max
        let cleared = timeline.entries.firstIndex(of: "markerClear") ?? Int.min
        XCTAssertLessThan(written, cleared, "l'appareil est rouvert AVANT l'effacement du marqueur")
        XCTAssertEqual(harness.manager.state, .ready(.feed))
        XCTAssertTrue(harness.manager.deletionBlockedNotice)
        XCTAssertFalse(harness.device.isRevoked, "cet appareil redevient l'appareil actif")
        XCTAssertNotNil(harness.device.currentDeviceId)
        XCTAssertEqual(harness.remote.serverId, harness.device.currentDeviceId, "la session est réécrite")
        XCTAssertGreaterThan(harness.manager.generation, generation)
        harness.manager.dismissDeletionBlockedNotice()
        XCTAssertFalse(harness.manager.deletionBlockedNotice)
    }

    func testASessionClosedMidDeletionGoesBackToSignInWithTheMarkerKept() async {
        let marker = MemoryDeletionMarker(timeline: timeline, mark: AccountDeletionMark(uid: "uid-1"))
        let harness = makeHarness(marker: marker)
        harness.gateway.setSignedIn("uid-1")
        await harness.manager.startAndResolve()

        await harness.manager.endSessionKeepingDeletionMarker()

        XCTAssertEqual(harness.manager.state, .ready(.signIn))
        XCTAssertEqual(harness.manager.consumeNotice(), .deletionPending)
        XCTAssertNotNil(marker.mark)
        XCTAssertEqual(harness.cache.purgeCalls, 1)
        XCTAssertNil(harness.gateway.currentUid)
    }

    func testTheSameAccountSigningInAgainResumesTheDeletionWithoutWritingASession() async {
        let marker = MemoryDeletionMarker(timeline: timeline, mark: AccountDeletionMark(uid: "uid-1"))
        let harness = makeHarness(marker: marker)
        await harness.manager.startAndResolve()
        harness.accounts.setLoaded(profileExists: false)

        let failure = await harness.manager.signInWithEmail(email: "alex@exemple.fr", password: "motdepasse")

        XCTAssertNil(failure)
        XCTAssertEqual(harness.manager.state, .ready(.accountDeletion))
        // Aucune session d'appareil pour un compte en cours de suppression.
        XCTAssertTrue(harness.remote.writtenIds.isEmpty)
        XCTAssertEqual(harness.accounts.loadCalls, 0)
    }

    func testAnotherAccountSigningInIsRoutedAsUsual() async {
        let marker = MemoryDeletionMarker(timeline: timeline, mark: AccountDeletionMark(uid: "someone-else"))
        let harness = makeHarness(marker: marker)
        await harness.manager.startAndResolve()
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)

        _ = await harness.manager.signInWithEmail(email: "alex@exemple.fr", password: "motdepasse")

        XCTAssertEqual(harness.manager.state, .ready(.feed))
    }
}
