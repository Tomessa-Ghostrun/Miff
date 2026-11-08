import XCTest
@testable import Miff

/// Le réconciliateur : le plan du planificateur devient des demandes du système, sans doublon, annulé dès que ce n'est plus vrai
/// (publication, retour dans un lieu, autre Miff, interrupteur coupé, plus de session), avec rattrapage et retrait à la reprise.
@MainActor
final class NotificationReconcilerTests: XCTestCase {
    private let midnight = 1_794_096_000_000
    private let miffId = "miff-1"

    private func at(_ hour: Int, _ minute: Int, ms: Int = 0) -> Int {
        midnight + hour * 3_600_000 + minute * 60_000 + ms
    }

    private func after(_ hour: Int, _ minute: Int) -> Int {
        at(hour, minute, ms: 1)
    }

    private var utc: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC") ?? .current
        return calendar
    }

    // MARK: - Le banc d'essai

    private final class State {
        var now = 0
        var uid: String? = "me"
        var active: String? = "miff-1"
        var foreground = false
        var language = "fr"
        var waiting: Set<String> = []
    }

    private final class FakeScheduler: LocalNotificationScheduling {
        private(set) var pending: [String: LocalNotificationRequest] = [:]
        var delivered: Set<String> = []
        private(set) var added: [LocalNotificationRequest] = []
        private(set) var removedDelivered: [String] = []

        func pendingIdentifiers() async -> Set<String> {
            Set(pending.keys)
        }

        func deliveredIdentifiers() async -> Set<String> {
            delivered
        }

        func add(_ request: LocalNotificationRequest) async {
            pending[request.identifier] = request
            added.append(request)
        }

        func removePending(_ identifiers: [String]) async {
            for identifier in identifiers {
                pending[identifier] = nil
            }
        }

        func removeDelivered(_ identifiers: [String]) async {
            removedDelivered += identifiers
            delivered.subtract(identifiers)
        }

        /// Le système a livré cette demande : elle n'est plus en attente.
        func fire(_ identifier: String) {
            pending[identifier] = nil
            delivered.insert(identifier)
        }
    }

    private final class FakeAuthorization: NotificationAuthorizationReading {
        var value = NotificationAuthorization.authorized

        func authorization() async -> NotificationAuthorization {
            value
        }
    }

    private struct Rig {
        let state: State
        let scheduler: FakeScheduler
        let authorization: FakeAuthorization
        let memory: NotificationMemory
        let preferences: NotificationPreferencesService
        let center: NotificationCenterService
        let reconciler: NotificationReconciler
    }

    private func makeRig(now: Int) -> Rig {
        let state = State()
        state.now = now
        let scheduler = FakeScheduler()
        let authorization = FakeAuthorization()
        let memory = NotificationMemory()
        let preferences = NotificationPreferencesService(store: memory.preferences, currentUid: { state.uid })
        let center = NotificationCenterService(
            store: memory.center, currentUid: { state.uid }, nowMillis: { state.now }
        )
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC") ?? .current
        var deps = NotificationReconcilerDependencies(
            scheduler: scheduler,
            authorization: authorization,
            preferences: preferences,
            plans: memory.plan,
            center: center,
            currentUid: { state.uid }
        )
        deps.activeMiffId = { state.active }
        deps.agreementWaiting = { state.waiting.contains($0) }
        deps.nowMillis = { state.now }
        deps.calendar = { calendar }
        deps.isForeground = { state.foreground }
        deps.language = { state.language }
        deps.text = { $0 }
        return Rig(
            state: state, scheduler: scheduler, authorization: authorization, memory: memory,
            preferences: preferences, center: center, reconciler: NotificationReconciler(deps)
        )
    }

    private func green(_ uid: String) -> PlanMember {
        PlanMember(
            uid: uid, sharing: true, presence: "in_trusted", presenceAtMillis: at(8, 0), lastUpdateAtMillis: at(8, 0)
        )
    }

    private func out(_ uid: String, ref: Int, update: Int? = nil) -> PlanMember {
        PlanMember(uid: uid, sharing: true, presence: "out", presenceAtMillis: ref, lastUpdateAtMillis: update)
    }

    private func seed(_ rig: Rig, _ members: [PlanMember], savedAt: Int? = nil) {
        let snapshot = NotificationPlanSnapshot(
            miffId: miffId, myUid: "me", members: members, savedAtMillis: savedAt ?? rig.state.now
        )
        rig.memory.plan.saveSnapshot(snapshot, uid: "me")
    }

    private func identifier(_ type: NotificationType) -> String {
        NotificationIdentifier.make(type: type, miffId: miffId)
    }

    // MARK: - Programmer

    func testTheOwnRemindersAreProgrammedAsFixedTextLocalNotifications() async {
        let rig = makeRig(now: at(14, 0))
        seed(rig, [out("me", ref: at(13, 40)), green("x")])

        await rig.reconciler.reconcile()

        XCTAssertEqual(Set(rig.scheduler.pending.keys), [identifier(.n1), identifier(.n2)])
        let first = rig.scheduler.pending[identifier(.n1)]
        XCTAssertEqual(first?.title, "notif_n1_title")
        XCTAssertEqual(first?.body, "notif_n1_body")
        XCTAssertEqual(first?.threadIdentifier, miffId, "regroupées par Miff")
        XCTAssertEqual(first?.categoryIdentifier, "miff.notif.category.n1")
        XCTAssertEqual(first?.delaySeconds, Double(after(15, 40) - at(14, 0)) / 1000)
        XCTAssertEqual(first?.quiet, false)
        let userInfoKeys = Set(first?.userInfo.keys.map { $0 } ?? [])
        XCTAssertEqual(userInfoKeys, ["type", "miffId", "key"], "ni nom, ni lieu, ni position")
        XCTAssertLessThanOrEqual(rig.scheduler.pending.count, 4, "au plus 4 demandes en attente")
    }

    func testAReconciliationThatChangesNothingProgramsNothingAgain() async {
        let rig = makeRig(now: at(14, 0))
        seed(rig, [out("me", ref: at(13, 40)), green("x")])
        await rig.reconciler.reconcile()
        let count = rig.scheduler.added.count

        rig.state.now = at(14, 5)
        await rig.reconciler.reconcile()

        XCTAssertEqual(rig.scheduler.added.count, count, "idempotent : même plan, mêmes demandes")
    }

    func testTheMiffFollowUpsAreProgrammedForTheObserver() async {
        let rig = makeRig(now: at(14, 0))
        seed(rig, [green("me"), out("x", ref: at(13, 40)), green("y")])

        await rig.reconciler.reconcile()

        XCTAssertEqual(Set(rig.scheduler.pending.keys), [identifier(.n3), identifier(.n4)])
    }

    func testAChangeOfLanguageReprogramsTheFixedText() async {
        let rig = makeRig(now: at(14, 0))
        seed(rig, [out("me", ref: at(13, 40)), green("x")])
        await rig.reconciler.reconcile()
        let count = rig.scheduler.added.count

        rig.state.language = "en"
        await rig.reconciler.reconcile()

        XCTAssertEqual(rig.scheduler.added.count, count + 2, "le texte est figé à la programmation")
    }

    // MARK: - Annuler

    func testPublishingAnUpdateCancelsAndReprogramsWithTheNewReference() async {
        let rig = makeRig(now: at(15, 0))
        seed(rig, [out("me", ref: at(13, 40)), green("x")])
        await rig.reconciler.reconcile()
        let before = rig.scheduler.pending[identifier(.n1)]?.delaySeconds

        rig.state.now = at(15, 30)
        await rig.reconciler.myUpdatePublished(atMillis: at(15, 30))

        let after = rig.scheduler.pending[identifier(.n1)]?.delaySeconds
        XCTAssertNotNil(before)
        XCTAssertEqual(after, Double(self.after(17, 30) - at(15, 30)) / 1000, "N1 repart de la nouvelle update")
    }

    func testReturningToATrustedPlaceCancelsTheOwnRemindersAtOnce() async {
        let rig = makeRig(now: at(14, 0))
        seed(rig, [out("me", ref: at(13, 40)), green("x")])
        await rig.reconciler.reconcile()
        XCTAssertEqual(rig.scheduler.pending.count, 2)

        let fact = StatusFact(presence: .inTrusted, placeId: "p1", presenceAtMillis: at(14, 10), setLastUpdate: true)
        rig.state.now = at(14, 10)
        await rig.reconciler.myPresenceChanged(miffId: miffId, fact: fact)

        XCTAssertTrue(rig.scheduler.pending.isEmpty)
    }

    func testLeavingAPlaceProgramsN1AndN2FromTheDetectedDeparture() async {
        let rig = makeRig(now: at(9, 0))
        seed(rig, [green("me"), green("x")])

        let fact = StatusFact(presence: .out, placeId: "p1", presenceAtMillis: at(9, 0), setLastUpdate: false)
        await rig.reconciler.myPresenceChanged(miffId: miffId, fact: fact)

        XCTAssertEqual(Set(rig.scheduler.pending.keys), [identifier(.n1), identifier(.n2)])
        XCTAssertEqual(rig.memory.plan.snapshots["me"]?.members.count, 2, "seule ma ligne a changé")
    }

    func testADepartureWithoutAnyFeedDataStillProgramsTheOwnReminders() async {
        let rig = makeRig(now: at(9, 0))
        rig.state.active = nil

        let fact = StatusFact(presence: .out, placeId: nil, presenceAtMillis: at(9, 0), setLastUpdate: false)
        await rig.reconciler.myPresenceChanged(miffId: miffId, fact: fact)

        XCTAssertEqual(Set(rig.scheduler.pending.keys), [identifier(.n1), identifier(.n2)])
    }

    func testADepartureFromAnotherMiffThanTheActiveOneProgramsNothing() async {
        let rig = makeRig(now: at(9, 0))
        rig.state.active = "another-miff"

        let fact = StatusFact(presence: .out, placeId: nil, presenceAtMillis: at(9, 0), setLastUpdate: false)
        await rig.reconciler.myPresenceChanged(miffId: miffId, fact: fact)

        XCTAssertTrue(rig.scheduler.pending.isEmpty)
    }

    func testChangingTheActiveMiffCancelsEverythingOfTheOldOne() async {
        let rig = makeRig(now: at(14, 0))
        seed(rig, [out("me", ref: at(13, 40)), green("x")])
        await rig.reconciler.reconcile()

        rig.state.active = "another-miff"
        await rig.reconciler.reconcile()

        XCTAssertTrue(rig.scheduler.pending.isEmpty, "rien de l'ancienne Miff n'est affiché")
    }

    func testTurningATypeOrEverythingOffCancelsAndRemovesWhatIsOnScreen() async {
        let rig = makeRig(now: at(14, 0))
        seed(rig, [out("me", ref: at(13, 40)), green("x")])
        await rig.reconciler.reconcile()

        rig.preferences.update { $0.setting(.n1, to: false) }
        await rig.reconciler.reconcile()
        XCTAssertEqual(Set(rig.scheduler.pending.keys), [identifier(.n2)])

        rig.scheduler.delivered = [identifier(.n2)]
        rig.preferences.update { $0.settingGeneral(false) }
        await rig.reconciler.reconcile()
        XCTAssertTrue(rig.scheduler.pending.isEmpty)
        XCTAssertEqual(rig.scheduler.removedDelivered, [identifier(.n2)], "la file de Miff est vidée")
    }

    func testNoAuthorizationMeansNothingIsProgrammed() async {
        let rig = makeRig(now: at(14, 0))
        seed(rig, [out("me", ref: at(13, 40)), green("x")])
        rig.authorization.value = .denied

        await rig.reconciler.reconcile()

        XCTAssertTrue(rig.scheduler.pending.isEmpty)
    }

    func testWithoutASessionNothingStaysPendingOrOnScreen() async {
        let rig = makeRig(now: at(14, 0))
        seed(rig, [out("me", ref: at(13, 40)), green("x")])
        await rig.reconciler.reconcile()
        rig.scheduler.delivered = [identifier(.n1)]

        rig.state.uid = nil
        await rig.reconciler.reconcile()

        XCTAssertTrue(rig.scheduler.pending.isEmpty)
        XCTAssertTrue(rig.scheduler.delivered.isEmpty)
    }

    func testAnAgreementThatIsAwaitedMeansNoReminder() async {
        let rig = makeRig(now: at(14, 0))
        seed(rig, [out("me", ref: at(13, 40)), green("x")])
        rig.state.waiting = [miffId]

        await rig.reconciler.reconcile()

        XCTAssertTrue(rig.scheduler.pending.isEmpty)
    }

    // MARK: - Retrait à la reprise

    func testADeliveredNotificationThatIsNoLongerTrueIsRemovedFromTheScreen() async {
        let rig = makeRig(now: at(16, 30))
        // La Miff n'est plus orange : X a publié.
        seed(rig, [green("me"), out("x", ref: at(13, 40), update: at(16, 20)), green("y")])
        rig.scheduler.delivered = [identifier(.n3)]

        await rig.reconciler.reconcile()

        XCTAssertEqual(rig.scheduler.removedDelivered, [identifier(.n3)])
    }

    func testADeliveredNotificationThatIsStillTrueStays() async {
        let rig = makeRig(now: at(16, 30))
        seed(rig, [green("me"), out("x", ref: at(13, 40)), green("y")])
        rig.scheduler.delivered = [identifier(.n3)]

        await rig.reconciler.reconcile()

        XCTAssertTrue(rig.scheduler.removedDelivered.isEmpty)
    }

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
        let key = rig.memory.plan.ledgers["me"]?.scheduled[identifier(.n2)]?.key

        let destination = rig.reconciler.notificationTapped(type: "n2", key: key, miffId: miffId)

        XCTAssertEqual(destination, .update)
        XCTAssertFalse(rig.center.hasUnread, "lue au toucher")
        XCTAssertEqual(rig.reconciler.notificationTapped(type: "n3", key: nil, miffId: nil), .feed)
        XCTAssertNil(rig.reconciler.notificationTapped(type: "n5", key: nil, miffId: nil), "N5 n'existe pas")
        XCTAssertNil(rig.reconciler.notificationTapped(type: nil, key: nil, miffId: nil))
    }

    func testTouchingANotificationWithoutASessionOpensNothing() {
        let rig = makeRig(now: at(15, 0))
        rig.state.uid = nil

        XCTAssertNil(rig.reconciler.notificationTapped(type: "n1", key: "k", miffId: miffId))
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
        XCTAssertEqual(Set(rig.scheduler.pending.keys), [identifier(.n3), identifier(.n4)])
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
