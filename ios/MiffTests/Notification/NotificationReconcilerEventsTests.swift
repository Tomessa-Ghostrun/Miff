import XCTest
@testable import Miff

/// Le réconciliateur, côté événements : ce qui a déjà sonné, le rattrapage (§20.8), le premier plan, le toucher et les statuts du
/// Feed.
@MainActor
final class NotificationReconcilerEventsTests: ReconcilerTestCase {
    // MARK: - Ce qui a déjà sonné

    func testAProgrammedRequestThatFiredIsRecordedAsShownOnceAndNeverNotifiedAgain() async {
        let rig = makeRig(now: at(15, 0))
        seed(rig, [out("me", ref: at(13, 40)), green("x")])
        await rig.reconciler.reconcile()

        rig.scheduler.fire(identifier(.n1))
        rig.state.now = at(15, 45)
        await rig.reconciler.reconcile()
        await rig.reconciler.reconcile()

        let items = rig.center.history.items.filter { $0.type == .n1 }
        XCTAssertEqual(items.count, 1)
        XCTAssertEqual(items.first?.state, .shown)
        XCTAssertEqual(items.first?.createdAtMillis, after(15, 40))
        XCTAssertNil(rig.scheduler.pending[identifier(.n1)], "jamais renotifiée")
        XCTAssertTrue(rig.center.hasUnread)
    }

    func testAFollowUpShownAfterTheColorWentBackIsCountedAsAFalseAlert() async {
        let rig = makeRig(now: at(15, 0))
        seed(rig, [green("me"), out("x", ref: at(13, 40)), green("y")])
        await rig.reconciler.reconcile()
        rig.scheduler.fire(identifier(.n3))

        // Le Feed apprend ensuite que X a publié à 16 h 05 (avant 16 h 10) : la couleur était déjà revenue.
        rig.state.now = at(16, 30)
        seed(rig, [green("me"), out("x", ref: at(13, 40), update: at(16, 5)), green("y")], savedAt: at(16, 30))
        await rig.reconciler.reconcile()

        XCTAssertEqual(rig.reconciler.followUpCounters()?.judged, 1)
        XCTAssertEqual(rig.reconciler.followUpCounters()?.falseAlerts, 1)
    }

    func testAFollowUpThatWasTrueIsJudgedButNotCountedAsFalse() async {
        let rig = makeRig(now: at(15, 0))
        seed(rig, [green("me"), out("x", ref: at(13, 40)), green("y")])
        await rig.reconciler.reconcile()
        rig.scheduler.fire(identifier(.n3))

        rig.state.now = at(16, 30)
        seed(rig, [green("me"), out("x", ref: at(13, 40)), green("y")], savedAt: at(16, 30))
        await rig.reconciler.reconcile()

        XCTAssertEqual(rig.reconciler.followUpCounters()?.judged, 1)
        XCTAssertEqual(rig.reconciler.followUpCounters()?.falseAlerts, 0)
    }

    func testARequestThatDisappearedBeforeItsTimeIsForgottenAndProgrammedAgain() async {
        let rig = makeRig(now: at(14, 0))
        seed(rig, [out("me", ref: at(13, 40)), green("x")])
        await rig.reconciler.reconcile()

        await rig.scheduler.removePending([identifier(.n1)])
        await rig.reconciler.reconcile()

        XCTAssertNotNil(rig.scheduler.pending[identifier(.n1)])
        XCTAssertTrue(rig.center.history.isEmpty, "elle n'a pas sonné : rien dans le centre")
    }

    // MARK: - Rattrapage (§20.8)

    func testALateReminderInTheBackgroundWithinFifteenMinutesIsShownAtOnce() async {
        let rig = makeRig(now: at(15, 50))
        seed(rig, [out("me", ref: at(13, 40)), green("x")])

        await rig.reconciler.reconcile()

        let immediate = rig.scheduler.pending[identifier(.n1)]
        XCTAssertEqual(immediate?.delaySeconds, 1)
        XCTAssertEqual(rig.center.history.items.first?.state, .shown)
    }

    func testALateReminderInTheForegroundIsASilentEntryWithoutSystemNotification() async {
        let rig = makeRig(now: at(15, 50))
        rig.state.foreground = true
        seed(rig, [out("me", ref: at(13, 40)), green("x")])

        await rig.reconciler.reconcile()

        XCTAssertNil(rig.scheduler.pending[identifier(.n1)])
        XCTAssertEqual(rig.center.history.items.first?.state, .silent)
    }

    func testAReminderMoreThanFifteenMinutesLateIsASilentEntry() async {
        let rig = makeRig(now: at(15, 56))
        seed(rig, [out("me", ref: at(13, 40)), green("x")])

        await rig.reconciler.reconcile()

        XCTAssertNil(rig.scheduler.pending[identifier(.n1)])
        XCTAssertEqual(rig.center.history.items.first?.state, .silent)
    }

    func testAlreadyRedTheOrangeReminderIsNotShownAndOnlyN2IsCaughtUp() async {
        let rig = makeRig(now: at(18, 0))
        rig.state.foreground = true
        seed(rig, [out("me", ref: at(13, 40)), green("x")])

        await rig.reconciler.reconcile()

        XCTAssertEqual(rig.center.history.items.map { $0.type }, [.n2])
    }

    // MARK: - Premier plan et toucher

    func testANotificationArrivingInTheForegroundBecomesASilentEntry() async {
        let rig = makeRig(now: at(15, 0))
        seed(rig, [out("me", ref: at(13, 40)), green("x")])
        await rig.reconciler.reconcile()

        rig.reconciler.foregroundDelivery(identifier: identifier(.n1))

        XCTAssertEqual(rig.center.history.items.first?.state, .silent)
        XCTAssertEqual(rig.center.history.items.first?.id, rig.memory.plan.ledgers["me"]?.notified.keys.first)
        rig.reconciler.foregroundDelivery(identifier: identifier(.n1))
        XCTAssertEqual(rig.center.history.items.count, 1, "une fois seulement")
    }

    func testTouchingANotificationMarksItReadAndSaysWhereToGo() async {
        let rig = makeRig(now: at(15, 0))
        seed(rig, [out("me", ref: at(13, 40)), green("x")])
        await rig.reconciler.reconcile()
        let destination = rig.reconciler.notificationTapped(type: "n2", miffId: miffId)

        XCTAssertEqual(destination, .update)
        XCTAssertFalse(rig.center.hasUnread, "lue au toucher (la clé vient du registre, pas de la demande)")
        XCTAssertEqual(rig.reconciler.notificationTapped(type: "n3", miffId: nil), .feed)
        XCTAssertNil(rig.reconciler.notificationTapped(type: "n5", miffId: nil), "N5 n'existe pas")
        XCTAssertNil(rig.reconciler.notificationTapped(type: nil, miffId: nil))
    }

    func testTouchingANotificationAlreadyRecordedByAReconciliationMarksItsLineRead() async {
        let rig = makeRig(now: at(15, 0))
        seed(rig, [out("me", ref: at(13, 40)), green("x")])
        await rig.reconciler.reconcile()
        rig.scheduler.fire(identifier(.n2))
        rig.state.now = at(18, 0)
        await rig.reconciler.reconcile()
        XCTAssertTrue(rig.center.hasUnread, "la réconciliation l'a inscrite comme affichée")

        let destination = rig.reconciler.notificationTapped(type: "n2", miffId: miffId)

        XCTAssertEqual(destination, .update)
        XCTAssertTrue(rig.center.history.items.filter { $0.type == .n2 }.allSatisfy { !$0.isUnread })
    }

    func testTouchingANotificationWithoutASessionOpensNothing() {
        let rig = makeRig(now: at(15, 0))
        rig.state.uid = nil

        XCTAssertNil(rig.reconciler.notificationTapped(type: "n1", miffId: miffId))
    }

    // MARK: - Le Feed donne les statuts du serveur

    func testTheFeedStatusesRewriteTheSnapshotAndProgramTheFollowUps() async {
        let rig = makeRig(now: at(14, 0))
        let statuses: [String: MemberStatus] = [
            "me": MemberStatus(
                presence: .inTrusted, placeId: "p1", presenceAtMillis: at(8, 0), lastUpdateAtMillis: at(8, 0),
                hasPhoto: false, batteryPct: nil, locationAccess: nil, updatedAtMillis: nil
            ),
            "x": MemberStatus(
                presence: .out, placeId: "p2", presenceAtMillis: at(13, 40), lastUpdateAtMillis: nil,
                hasPhoto: false, batteryPct: nil, locationAccess: nil, updatedAtMillis: nil
            )
        ]
        let event = FeedStatusesEvent(
            miffId: miffId,
            statuses: statuses,
            myUid: "me",
            members: [
                FeedStatusesEvent.Member(uid: "me", sharing: true), FeedStatusesEvent.Member(uid: "x", sharing: true)
            ]
        )

        await rig.reconciler.feedStatusesLoaded(event)

        XCTAssertEqual(rig.memory.plan.snapshots["me"]?.members.map { $0.uid }, ["me", "x"])
        XCTAssertEqual(rig.memory.plan.snapshots["me"]?.savedAtMillis, at(14, 0))
        XCTAssertEqual(Set(rig.scheduler.pending.keys), [identifier(.n3)], "N4 attend une donnée plus récente")
    }

    func testAnEventWithoutMembersOrFromAnotherAccountChangesNothing() async {
        let rig = makeRig(now: at(14, 0))
        let empty = FeedStatusesEvent(miffId: miffId, statuses: [:], myUid: "me", members: [])
        let other = FeedStatusesEvent(
            miffId: miffId, statuses: [:], myUid: "someone-else",
            members: [FeedStatusesEvent.Member(uid: "someone-else", sharing: true)]
        )

        await rig.reconciler.feedStatusesLoaded(empty)
        await rig.reconciler.feedStatusesLoaded(other)

        XCTAssertNil(rig.memory.plan.snapshots["me"])
    }
}
