import XCTest
@testable import Miff

/// Le réconciliateur : le plan du planificateur devient des demandes du système, sans doublon, annulé dès que ce n'est plus vrai
/// (publication, retour dans un lieu, autre Miff, interrupteur coupé, plus de session), avec retrait à la reprise.
@MainActor
final class NotificationReconcilerTests: ReconcilerTestCase {
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
        XCTAssertEqual(userInfoKeys, ["type", "miffId"], "ni nom, ni lieu, ni position, ni identifiant de compte")
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

    func testTheMiffFollowUpIsProgrammedForTheObserverAndTheSecondOneOnceTheDataIsFreshEnough() async {
        let rig = makeRig(now: at(14, 0))
        seed(rig, [green("me"), out("x", ref: at(13, 40)), green("y")])

        await rig.reconciler.reconcile()
        XCTAssertEqual(Set(rig.scheduler.pending.keys), [identifier(.n3)], "N4 est à plus de 3 h de la donnée")

        rig.state.now = at(16, 0)
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

    func testLeavingTheLastMiffCancelsEverythingEvenWithAnOldSnapshot() async {
        let rig = makeRig(now: at(14, 0))
        seed(rig, [out("me", ref: at(13, 40)), green("x")])
        await rig.reconciler.reconcile()

        // L'annuaire est lu et ne contient plus aucune Miff : la valeur ne correspond à aucune Miff de l'instantané.
        rig.state.active = ""
        await rig.reconciler.reconcile()

        XCTAssertTrue(rig.scheduler.pending.isEmpty)
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
}
