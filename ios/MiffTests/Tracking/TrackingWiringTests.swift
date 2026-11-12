import XCTest
@testable import Miff

/// Ce qui relie le suivi au reste de l'app (LOC-4b) : le partage qui change prévient le suivi, un lieu écrit le prévient, la session
/// lui donne sa génération, l'exécution du système (premier plan, zones, temps d'arrière-plan) arrive au moteur.
@MainActor
final class SharingChangeHookTests: XCTestCase {
    private let uid = "uid-1"
    private let store = FakeMiffStore()
    private let location = FakeLocationProbe()
    private let timeline = Timeline()
    private lazy var cache = FakeCachePurger(timeline: timeline)
    private let changes = TestBox(0)

    private func repo(onResumed: ((String) -> Void)? = nil) -> FirestoreMiffRepository {
        let counter = changes
        var rules = MiffRepositoryRules(
            newMiffId: { testMiffId },
            newCode: { testCode },
            timeoutSeconds: 1,
            deviceId: { testDeviceId }
        )
        rules.onSharingChanged = { counter.value += 1 }
        rules.onSharingResumed = onResumed
        return FirestoreMiffRepository(
            store: store,
            observer: FakeMiffObserver(),
            location: location,
            cache: cache,
            rules: rules
        )
    }

    private func joinable() {
        store.codes[testCode] = ["miffId": testMiffId, "miffName": "Chez nous"]
        store.miffs[testMiffId] = testMiffDetails()
    }

    func testCreatingAMiffTellsTheTrackingOnlyOnceTheServerSaidYes() async {
        let prepared = await repo().prepareCreate(uid: uid)
        guard case .ready(let ready) = prepared else {
            return XCTFail("la préparation devrait être prête")
        }
        store.commitFailures = [firestoreError(FirestoreErrorNumber.permissionDenied)]
        _ = await repo().create(uid: uid, name: "Chez nous", preparation: ready)
        XCTAssertEqual(changes.value, 0, "un lot refusé ne change rien")

        store.commitFailures = []
        let result = await repo().create(uid: uid, name: "Chez nous", preparation: ready)

        guard case .created = result else {
            return XCTFail("la Miff devrait être créée")
        }
        XCTAssertEqual(changes.value, 1)
    }

    func testJoiningAMiffTellsTheTracking() async {
        joinable()
        let prepared = await repo().prepareJoin(uid: uid, typedCode: testCode)
        guard case .ready(let ready) = prepared else {
            return XCTFail("le code devrait être reconnu")
        }

        let result = await repo().join(uid: uid, preparation: ready)

        XCTAssertEqual(result, .joined(miffId: testMiffId, firstMiff: true))
        XCTAssertEqual(changes.value, 1)
    }

    func testStoppingAndResumingTheSharingTellTheTrackingButAFailureDoesNot() async {
        let stop = await repo().setSharing(miffId: testMiffId, uid: uid, enabled: false)
        let resume = await repo().setSharing(miffId: testMiffId, uid: uid, enabled: true)
        XCTAssertEqual([stop, resume], [.done, .done])
        XCTAssertEqual(changes.value, 2)

        store.commitFailures = [firestoreError(FirestoreErrorNumber.unavailable)]
        let offline = await repo().setSharing(miffId: testMiffId, uid: uid, enabled: false)

        XCTAssertEqual(offline, .failed(.offline))
        XCTAssertEqual(changes.value, 2)
    }

    func testResumingTheSharingTellsTheTrackingWhichMiffInsteadOfAPlainChangeAndStoppingDoesNot() async {
        let resumedIds = TestBox([String]())
        let onResumed: (String) -> Void = { resumedIds.value.append($0) }

        let stop = await repo(onResumed: onResumed).setSharing(miffId: testMiffId, uid: uid, enabled: false)
        XCTAssertEqual(stop, .done)
        XCTAssertEqual(changes.value, 1)
        XCTAssertTrue(resumedIds.value.isEmpty)

        let resume = await repo(onResumed: onResumed).setSharing(miffId: testMiffId, uid: uid, enabled: true)

        XCTAssertEqual(resume, .done)
        XCTAssertEqual(resumedIds.value, [testMiffId])
        // La reprise remplace le simple changement : le suivi relit lui-même, dans l'ordre.
        XCTAssertEqual(changes.value, 1)
    }

    func testLeavingAMiffAndCleaningUpALostOneTellTheTracking() async {
        let left = await repo().leave(uid: uid, miffId: testMiffId)
        XCTAssertEqual(left, .done)
        XCTAssertEqual(changes.value, 1)

        let lost = LostMiff(ref: MembershipRef(key: "free", miffId: testMiffId), name: nil)
        let cleaned = await repo().cleanUpLost(uid: uid, lost: lost)

        XCTAssertTrue(cleaned)
        XCTAssertEqual(changes.value, 2)
    }

    func testRenewingTheConsentTellsTheTrackingSoTheNewTextStartsTheDetection() async {
        let counter = changes
        store.consents[testMiffId] = SharingConsentRead(enabled: true, version: "2026-10-v2")
        let renewals = FirestoreConsentRenewals(store: store, timeoutSeconds: 1, onRenewed: { counter.value += 1 })

        let result = await renewals.renew(uid: uid, miffIds: [testMiffId])

        XCTAssertEqual(result, .done)
        XCTAssertEqual(changes.value, 1)
    }

    // MARK: - Les lieux

    private func draft() -> PlaceDraft {
        PlaceDraft(
            name: "Chez nous",
            miffIds: [testMiffId],
            address: "17 Avenue d'Italie, 75013 Paris",
            lat: 48.8,
            lng: 2.3,
            radiusM: 100,
            type: .trusted,
            category: .home
        )
    }

    func testAPlaceThatReallyChangedTellsTheTrackingAndARefusalDoesNot() async {
        let base = FakePlaceRepository()
        let counter = changes
        let repository = ChangeNotifyingPlaceRepository(base, onChanged: { counter.value += 1 })
        let context = PlaceContext(premium: false, ownedMiffs: [], lastJoinedMiffId: nil, places: [])
        let group = PlaceGroup(id: "p1", copies: [testPlace(id: "p1", name: "Chez nous")])

        _ = await repository.create(uid: uid, draft: draft(), context: context)
        _ = await repository.update(uid: uid, original: group, draft: draft(), context: context)
        _ = await repository.delete(placeId: "p1", miffIds: [testMiffId])
        XCTAssertEqual(changes.value, 3)

        base.createResult = .needsPremium(.limit)
        base.updateResult = .failed(.offline)
        base.deleteResult = .placeGone
        _ = await repository.create(uid: uid, draft: draft(), context: context)
        _ = await repository.update(uid: uid, original: group, draft: draft(), context: context)
        _ = await repository.delete(placeId: "p1", miffIds: [testMiffId])

        XCTAssertEqual(changes.value, 3, "rien n'a changé : le suivi n'est pas dérangé")
    }

    // MARK: - La session

    func testTheGenerationCounterFollowsTheSessionAndIsStillTheOldOneWhenTheTrackingStops() async {
        let counter = GenerationCounter()
        var seenAtStop = -1
        let harness = SessionHarness(teardown: [{ seenAtStop = counter.current }], generationCounter: counter)
        harness.gateway.setSignedIn("uid-1")
        harness.accounts.setLoaded(profileExists: true, hasMembership: true, analyticsConsent: true)
        await harness.manager.startAndResolve()

        await harness.manager.signOut()

        XCTAssertEqual(seenAtStop, 0, "à l'arrêt du suivi, la session n'a pas encore changé")
        XCTAssertGreaterThan(counter.current, 0)
        XCTAssertEqual(counter.current, harness.manager.generation)
    }
}

/// Du temps d'arrière-plan sans le système : ce qui a été demandé et rendu.
final class FakeBackgroundExecution: BackgroundExecution {
    private(set) var begun = 0
    private(set) var ended: [Int] = []

    func begin() -> Int {
        begun += 1
        return begun
    }

    func end(_ token: Int) {
        ended.append(token)
    }
}

/// Le pont entre le système et le moteur : chaque travail demande du temps d'arrière-plan et le rend, les événements arrivent au moteur.
@MainActor
final class TrackingRuntimeTests: XCTestCase {
    private let rig = TrackingRig()
    private let background = FakeBackgroundExecution()

    private func runtime() -> TrackingRuntime {
        TrackingRuntime(engine: rig.engine, background: background, journal: rig.journal, beforeWork: {})
    }

    func testOpeningTheAppReadsThePositionAndGivesTheTimeBack() async {
        let runtime = runtime()

        runtime.appBecameActive()
        await waitUntil { background.ended.count == 1 }

        XCTAssertEqual(background.begun, 1)
        XCTAssertEqual(background.ended, [1])
        XCTAssertEqual(rig.position.calls, 1)
        XCTAssertEqual(rig.store.positionWrites().count, 1)
    }

    func testGoingToTheBackgroundLooksAgainToo() async {
        let runtime = runtime()

        runtime.appWentToBackground()
        await waitUntil { background.ended.count == 1 }

        XCTAssertEqual(rig.position.calls, 1)
    }

    func testAZoneEventReachesTheEngineAndTheBackgroundTimeIsGivenBack() async throws {
        await rig.engine.refresh(.opened)
        let runtime = runtime()

        runtime.zoneTransition(.enter, zoneKey: rig.homeKey)
        await waitUntil { background.ended.count == 1 }

        let write = try XCTUnwrap(rig.store.statusWrites().first)
        XCTAssertEqual(write.data["presence"], .string("in_trusted"))
        XCTAssertEqual(background.begun, 1)
        XCTAssertEqual(background.ended, [1])
    }

    func testTheStateOfARegionAndASignificantMoveReachTheEngine() async {
        await rig.engine.refresh(.opened)
        let runtime = runtime()

        runtime.zoneStateKnown(zoneKey: rig.homeKey, isInside: true)
        await waitUntil { background.ended.count == 1 }
        XCTAssertEqual(rig.store.statusWrites().count, 1)

        let calls = rig.position.calls
        runtime.significantMove(TrackingFixtures.fixFar(at: rig.env.now))
        await waitUntil { background.ended.count == 2 }
        XCTAssertTrue(rig.journal.kinds.contains(.wakeSignificantMove))
        XCTAssertEqual(rig.position.calls, calls, "la position du système est utilisée, aucune autre n'est demandée")
    }

    func testARepeatedFailureOfTheMonitoringReregistersAtMostOnceEveryFiveMinutes() async {
        let runtime = runtime()

        runtime.monitoringFailed()
        runtime.monitoringFailed()
        runtime.monitoringFailed()
        await waitUntil { background.ended.count >= 1 }

        XCTAssertEqual(background.begun, 1)
        XCTAssertEqual(rig.journal.kinds.filter { $0 == .monitoringFailed }.count, 3)
        XCTAssertEqual(rig.journal.kinds.filter { $0 == .wakeRearm }.count, 1)
    }

    func testStoppingTheRuntimeStopsTheEngine() async {
        await rig.engine.refresh(.opened)
        let runtime = runtime()

        await runtime.stop()

        XCTAssertNil(rig.local.state)
    }

    func testEventsAreProcessedStrictlyInTheOrderTheyArrived() async {
        await rig.engine.refresh(.opened)
        let runtime = runtime()

        runtime.zoneTransition(.enter, zoneKey: rig.homeKey)
        runtime.zoneTransition(.exit, zoneKey: rig.homeKey)
        await waitUntil { background.ended.count == 2 }

        let values = rig.store.statusWrites().map { write -> String? in
            if case .string(let text)? = write.data["presence"] {
                return text
            }
            return nil
        }
        XCTAssertEqual(values, ["in_trusted", "out"])
        XCTAssertEqual(background.ended.count, 2, "chaque travail rend son temps")
    }

    func testResumingTheSharingThroughTheRuntimeRecomputesThePresenceAndWritesItAgain() async {
        await rig.engine.refresh(.opened)
        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now - 60 * 1000)
        let before = rig.store.statusWrites().count
        let runtime = runtime()

        runtime.sharingResumed(miffId: testMiffId)
        await waitUntil { background.ended.count == 1 }

        XCTAssertEqual(rig.store.statusWrites().count, before + 1)
        XCTAssertEqual(rig.store.statusWrites().last?.data["presence"], .string("in_trusted"))
    }

    func testTheTwoOpeningsOfAColdStartAreOne() async {
        let runtime = runtime()

        runtime.appBecameActive()
        runtime.appBecameActive()
        await waitUntil { background.ended.count == 1 }

        XCTAssertEqual(background.begun, 1)
        XCTAssertEqual(rig.position.calls, 1)
    }
}
