import XCTest
@testable import Miff

/// N6 côté réconciliateur : la notification locale IMMÉDIATE, avant toute écriture sur le serveur, pour toutes mes Miffs, qui n'est jamais
/// retirée par une réconciliation, ne laisse aucune trace de lieu, et qui annule les rappels N1 / N2 (Q27).
@MainActor
final class NotificationDangerZoneReconcilerTests: ReconcilerTestCase {
    private func n6Identifier(_ miff: String = "miff-1") -> String {
        NotificationIdentifier.make(type: .n6, miffId: miff)
    }

    // MARK: - V1 : immédiate, texte fixe, annule N1 et N2

    func testV1EnteringADangerZoneShowsN6AtOnceAndCancelsTheOwnReminders() async {
        let rig = makeRig(now: at(14, 0))
        seed(rig, [out("me", ref: at(13, 40)), green("x")])
        await rig.reconciler.reconcile()
        XCTAssertEqual(rig.scheduler.pending.count, 2, "N1 et N2 sont programmées")

        let fact = StatusFact(presence: .inRisk, placeId: "p-risk", presenceAtMillis: at(14, 0), setLastUpdate: false)
        await rig.reconciler.myPresenceChanged(miffId: miffId, fact: fact)
        await rig.reconciler.dangerZoneEntered(miffId: miffId, placeId: "p-risk", enteredAtMillis: at(14, 0))

        let pendingIds = Set(rig.scheduler.pending.keys)
        XCTAssertEqual(pendingIds, [n6Identifier()], "plus de rappel « à toi » en zone de danger (Q27)")
        let request = rig.scheduler.pending[n6Identifier()]
        XCTAssertEqual(request?.title, "notif_n6a_title")
        XCTAssertEqual(request?.body, "notif_n6a_body")
        XCTAssertEqual(request?.delaySeconds, 1)
        XCTAssertEqual(request?.threadIdentifier, miffId)
        XCTAssertEqual(request?.categoryIdentifier, "miff.notif.category.n6")
        XCTAssertEqual(Set(request?.userInfo.keys.map { $0 } ?? []), ["type", "miffId"], "aucun nom de lieu")
        XCTAssertEqual(rig.center.history.items.map { $0.type }, [.n6])
        XCTAssertEqual(rig.center.history.items.first?.state, .shown)
    }

    func testDuringMyTripNoN6IsShownNorRecorded() async {
        let rig = makeRig(now: at(14, 0))
        seed(rig, [green("me"), green("x")])
        await rig.reconciler.myTravelChanged(uid: "me", untilMillis: at(24, 0), previousUntilMillis: nil)

        await rig.reconciler.dangerZoneEntered(miffId: miffId, placeId: "p-risk", enteredAtMillis: at(14, 0))

        XCTAssertNil(rig.scheduler.pending[n6Identifier()])
        XCTAssertTrue(rig.center.history.items.isEmpty, "ni notification, ni ligne du centre : N7 seule est attendue")
        XCTAssertEqual(Set(rig.scheduler.pending.keys), [NotificationIdentifier.make(type: .n7, miffId: "")])
    }

    func testN6IsNeverRemovedByAReconciliation() async {
        let rig = makeRig(now: at(14, 0))
        seed(rig, [green("me"), green("x")])
        await rig.reconciler.dangerZoneEntered(miffId: miffId, placeId: "p-risk", enteredAtMillis: at(14, 0))

        await rig.reconciler.reconcile()
        XCTAssertNotNil(rig.scheduler.pending[n6Identifier()], "la demande d'une seconde survit à une réconciliation")

        rig.scheduler.fire(n6Identifier())
        await rig.reconciler.reconcile()
        XCTAssertTrue(rig.scheduler.removedDelivered.isEmpty, "une N6 affichée n'est pas retirée à la reprise")
    }

    func testTheOneMinuteReentryRuleSurvivesAReconciliation() async {
        let rig = makeRig(now: at(14, 0))
        await rig.reconciler.dangerZoneEntered(miffId: miffId, placeId: "p-risk", enteredAtMillis: at(14, 0))
        await rig.reconciler.reconcile()

        rig.state.now = at(14, 30)
        await rig.reconciler.dangerZoneEntered(miffId: miffId, placeId: "p-risk", enteredAtMillis: at(14, 29))

        XCTAssertEqual(rig.scheduler.added.filter { $0.identifier == n6Identifier() }.count, 1, "V5 : une seule N6")
        XCTAssertEqual(rig.center.history.items.count, 1)
    }

    // MARK: - V3, V5

    func testV3TheSameEntryAnnouncedTwiceGivesOneNotification() async {
        let rig = makeRig(now: at(14, 0))

        await rig.reconciler.dangerZoneEntered(miffId: miffId, placeId: "p-risk", enteredAtMillis: at(13, 59))
        await rig.reconciler.dangerZoneEntered(miffId: miffId, placeId: "p-risk", enteredAtMillis: at(13, 59))

        XCTAssertEqual(rig.scheduler.added.count, 1)
        XCTAssertEqual(rig.center.history.items.count, 1)
    }

    func testV5AfterAnHourAndOneMinuteTheSamePlaceNotifiesAgain() async {
        let rig = makeRig(now: at(14, 0))
        await rig.reconciler.dangerZoneEntered(miffId: miffId, placeId: "p-risk", enteredAtMillis: at(14, 0))

        rig.state.now = at(15, 1)
        await rig.reconciler.dangerZoneEntered(miffId: miffId, placeId: "p-risk", enteredAtMillis: at(15, 0))

        XCTAssertEqual(rig.center.history.items.count, 2)
    }

    // MARK: - V6, V7, V8, V9

    func testV6AtNightN6IsQuiet() async {
        let rig = makeRig(now: at(2, 0))

        await rig.reconciler.dangerZoneEntered(miffId: miffId, placeId: "p-risk", enteredAtMillis: at(2, 0))

        XCTAssertEqual(rig.scheduler.pending[n6Identifier()]?.quiet, true)
    }

    func testV7TheFourthEntryOfTheDayIsAnEntryOfTheCenterOnly() async {
        let rig = makeRig(now: at(10, 0))
        for index in 0..<3 {
            rig.state.now = at(10 + 2 * index, 0)
            let entered = at(10 + 2 * index, 0)
            await rig.reconciler.dangerZoneEntered(miffId: miffId, placeId: "p-\(index)", enteredAtMillis: entered)
        }
        XCTAssertEqual(rig.scheduler.added.count, 3)

        rig.state.now = at(18, 0)
        await rig.reconciler.dangerZoneEntered(miffId: miffId, placeId: "p-3", enteredAtMillis: at(18, 0))

        XCTAssertEqual(rig.scheduler.added.count, 3, "pas de quatrième notification")
        XCTAssertEqual(rig.center.history.items.filter { $0.state == .silent }.count, 1)
    }

    func testV8InTheForegroundNothingIsShownButTheCenterKeepsAnEntry() async {
        let rig = makeRig(now: at(14, 0))
        rig.state.foreground = true

        await rig.reconciler.dangerZoneEntered(miffId: miffId, placeId: "p-risk", enteredAtMillis: at(14, 0))

        XCTAssertTrue(rig.scheduler.added.isEmpty)
        XCTAssertEqual(rig.center.history.items.first?.state, .silent)
    }

    func testV9WhenTheSwitchIsOffOrNotAuthorizedNothingHappens() async {
        let rig = makeRig(now: at(14, 0))
        rig.preferences.update { $0.setting(.n6, to: false) }
        await rig.reconciler.dangerZoneEntered(miffId: miffId, placeId: "p-risk", enteredAtMillis: at(14, 0))
        rig.preferences.update { $0.setting(.n6, to: true) }
        rig.authorization.value = .denied
        await rig.reconciler.dangerZoneEntered(miffId: miffId, placeId: "p-risk", enteredAtMillis: at(14, 1))
        rig.state.uid = nil
        await rig.reconciler.dangerZoneEntered(miffId: miffId, placeId: "p-risk", enteredAtMillis: at(14, 2))

        XCTAssertTrue(rig.scheduler.added.isEmpty)
        XCTAssertTrue(rig.center.history.isEmpty)
    }

    // MARK: - Démarrage à froid : réveil par géofence, aucun écran n'a rien relu (revue indépendante, B1)

    func testColdStartTheN6SwitchOffInTheStoreHoldsWhenAGeofenceWakesTheApp() async {
        let rig = makeRig(now: at(14, 0))
        rig.memory.preferences.save(NotificationPreferences().setting(.n6, to: false), uid: "me")

        await rig.reconciler.dangerZoneEntered(miffId: miffId, placeId: "p-risk", enteredAtMillis: at(14, 0))

        XCTAssertTrue(rig.scheduler.added.isEmpty, "N6 est coupée dans le magasin : jamais affichée")
        XCTAssertTrue(rig.center.history.isEmpty)
    }

    func testColdStartTheGeneralSwitchOffHoldsToo() async {
        let rig = makeRig(now: at(14, 0))
        rig.memory.preferences.save(NotificationPreferences().settingGeneral(false), uid: "me")

        await rig.reconciler.dangerZoneEntered(miffId: miffId, placeId: "p-risk", enteredAtMillis: at(14, 0))

        XCTAssertTrue(rig.scheduler.added.isEmpty)
    }

    func testColdStartTheThreePerDayCapIsReadFromTheCentreFile() async {
        let rig = makeRig(now: at(14, 0))
        let shown = (0..<3).map {
            NotificationFixtures.item("n6|old\($0)", type: .n6, at: at(9, $0))
        }
        rig.memory.center.seed(shown, uid: "me")

        await rig.reconciler.dangerZoneEntered(miffId: miffId, placeId: "p-risk", enteredAtMillis: at(14, 0))

        XCTAssertTrue(rig.scheduler.added.isEmpty, "trois N6 déjà affichées : une ligne du centre")
        XCTAssertEqual(rig.center.history.items.filter { $0.state == .silent }.count, 1)
    }

    func testTheSessionEndingWhileN6IsBeingProgrammedRecordsAndWritesNothing() async {
        let rig = makeRig(now: at(14, 0))
        let state = rig.state
        rig.scheduler.onAdd = { state.uid = nil }

        await rig.reconciler.dangerZoneEntered(miffId: miffId, placeId: "p-risk", enteredAtMillis: at(14, 0))

        XCTAssertTrue(rig.center.history.isEmpty, "aucune ligne du centre pour un compte parti")
        XCTAssertTrue(rig.memory.plan.loadLedger(uid: "me").notified.isEmpty)
    }

    func testTouchingN6MarksItsCentreLineReadAndOpensTheFeed() async {
        let rig = makeRig(now: at(14, 0))
        await rig.reconciler.dangerZoneEntered(miffId: miffId, placeId: "p-risk", enteredAtMillis: at(14, 0))
        XCTAssertTrue(rig.center.hasUnread)

        let destination = rig.reconciler.notificationTapped(type: "n6", miffId: miffId)

        XCTAssertEqual(destination, .feed)
        XCTAssertFalse(rig.center.hasUnread)
    }

    // MARK: - Toutes mes Miffs, plafond hors de celui de N1 à N4

    func testEveryMiffOfTheAccountIsConcernedNotOnlyTheActiveOne() async {
        let rig = makeRig(now: at(14, 0))
        rig.state.active = "miff-1"

        await rig.reconciler.dangerZoneEntered(miffId: "miff-2", placeId: "p-risk", enteredAtMillis: at(14, 0))

        XCTAssertNotNil(rig.scheduler.pending[n6Identifier("miff-2")])
        XCTAssertEqual(rig.center.history.items.first?.miffId, "miff-2")
    }

    func testN6DoesNotUseUpTheDailyCapOfN1ToN4() async {
        let rig = makeRig(now: at(14, 0))
        seed(rig, [out("me", ref: at(13, 40)), green("x")])
        for index in 0..<3 {
            await rig.reconciler.dangerZoneEntered(
                miffId: miffId, placeId: "p-\(index)", enteredAtMillis: at(13, 50 + index)
            )
        }
        await rig.reconciler.reconcile()

        XCTAssertNotNil(rig.scheduler.pending[NotificationIdentifier.make(type: .n1, miffId: miffId)])
        XCTAssertNotNil(rig.scheduler.pending[NotificationIdentifier.make(type: .n2, miffId: miffId)])
    }

    func testTouchingN6OpensTheFeedAndNeverTheUpdate() {
        let rig = makeRig(now: at(14, 0))

        XCTAssertEqual(rig.reconciler.notificationTapped(type: "n6", miffId: nil), .feed)
    }
}
