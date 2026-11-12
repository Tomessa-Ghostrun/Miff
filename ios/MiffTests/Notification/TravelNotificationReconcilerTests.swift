// Les assertions citent des cas entiers : certaines lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// MODE VOYAGE, N7 dans le réconciliateur (VOY-4I ; ticket mode-voyage §9.1) : programmée sans aucune Miff, déplacée avec la date, annulée par
/// une fin anticipée (pas par une fin naturelle), coupée par son interrupteur, oubliée au bout de 24 h ; mon voyage retire aussitôt N1 à N4.
/// Le banc d'essai est en UTC : la fin des exemples est minuit (début du 9), N7 part à 8 h ce jour-là.
@MainActor
final class TravelNotificationReconcilerTests: ReconcilerTestCase {
    /// 00:00 du lendemain du dernier jour (le 8) : la fin « technique » du voyage.
    private var tripEnd: Int {
        at(24, 0)
    }

    /// 08:00 le lendemain du dernier jour.
    private var due: Int {
        at(32, 0)
    }

    private var n7Identifier: String {
        NotificationIdentifier.make(type: .n7, miffId: "")
    }

    private func startTrip(_ rig: Rig) async {
        await rig.reconciler.myTravelChanged(uid: "me", untilMillis: tripEnd, previousUntilMillis: nil)
    }

    // MARK: - Programmer

    func testN7IsProgrammedWithoutAnyMiffAsAFixedTextLocalNotification() async {
        let rig = makeRig(now: at(16, 0))
        rig.state.active = nil

        await startTrip(rig)

        let request = rig.scheduler.pending[n7Identifier]
        XCTAssertEqual(Set(rig.scheduler.pending.keys), [n7Identifier])
        XCTAssertEqual(request?.title, "notif_n7_title")
        XCTAssertEqual(request?.body, "notif_n7_body")
        XCTAssertEqual(request?.delaySeconds, Double(due - at(16, 0)) / 1000)
        XCTAssertEqual(request?.quiet, false, "8 h : jamais la nuit")
        XCTAssertEqual(request?.categoryIdentifier, "miff.notif.category.n7")
        XCTAssertEqual(Set(request?.userInfo.keys.map { $0 } ?? []), ["type", "miffId", "key"], "ni nom, ni lieu, ni position")
        XCTAssertEqual(request?.userInfo["key"], "n7|me|\(tripEnd)")
        XCTAssertEqual(rig.memory.plan.loadTravelEnd(uid: "me"), tripEnd)
    }

    func testAReconciliationThatChangesNothingProgramsNothingAgain() async {
        let rig = makeRig(now: at(16, 0))
        await startTrip(rig)
        let count = rig.scheduler.added.count

        rig.state.now = at(17, 0)
        await rig.reconciler.reconcile()

        XCTAssertEqual(rig.scheduler.added.count, count)
    }

    func testAMovedDateReplacesTheWaitingRequestWithTheNewHour() async {
        let rig = makeRig(now: at(16, 0))
        await startTrip(rig)

        await rig.reconciler.myTravelChanged(uid: "me", untilMillis: tripEnd + 24 * 3_600_000, previousUntilMillis: tripEnd)

        XCTAssertEqual(Set(rig.scheduler.pending.keys), [n7Identifier], "une seule N7, jamais deux")
        XCTAssertEqual(rig.scheduler.pending[n7Identifier]?.delaySeconds, Double(due + 24 * 3_600_000 - at(16, 0)) / 1000)
        XCTAssertEqual(rig.scheduler.pending[n7Identifier]?.userInfo["key"], "n7|me|\(tripEnd + 24 * 3_600_000)")
    }

    // MARK: - Mon voyage coupe N1 à N4

    func testTheStartOfMyTripRemovesTheWaitingRemindersAtOnceAndKeepsN7Alone() async {
        let rig = makeRig(now: at(14, 0))
        seed(rig, [out("me", ref: at(13, 40)), green("x")])
        await rig.reconciler.reconcile()
        XCTAssertEqual(Set(rig.scheduler.pending.keys), [identifier(.n1), identifier(.n2)])

        await startTrip(rig)

        XCTAssertEqual(Set(rig.scheduler.pending.keys), [n7Identifier])
    }

    func testWhenTheTripEndsNaturallyTheNormalPlanIsBackAndN7StillWaitsForEight() async {
        let rig = makeRig(now: at(14, 0))
        seed(rig, [out("me", ref: at(13, 40)), green("x")])
        await startTrip(rig)
        XCTAssertEqual(Set(rig.scheduler.pending.keys), [n7Identifier])

        // 00:30 le 9 : le voyage est fini (la fin est passée) ; j'ai quitté un lieu à 00:05.
        rig.state.now = at(24, 30)
        seed(rig, [out("me", ref: at(24, 5)), green("x")])
        await rig.reconciler.myTravelChanged(uid: "me", untilMillis: nil, previousUntilMillis: tripEnd)

        XCTAssertEqual(Set(rig.scheduler.pending.keys), [identifier(.n1), identifier(.n2), n7Identifier])
        XCTAssertEqual(rig.memory.plan.loadTravelEnd(uid: "me"), tripEnd, "fin naturelle : N7 attend ses 8 h")
    }

    // MARK: - Annuler

    func testAnEarlyEndOfTheTripCancelsN7() async {
        let rig = makeRig(now: at(16, 0))
        await startTrip(rig)

        await rig.reconciler.myTravelChanged(uid: "me", untilMillis: nil, previousUntilMillis: tripEnd)

        XCTAssertTrue(rig.scheduler.pending.isEmpty, "rien à annoncer : le voyage a été arrêté avant la date")
        XCTAssertNil(rig.memory.plan.loadTravelEnd(uid: "me"))
    }

    func testAChangeOfAccountOrNoTripAtAllChangesNothing() async {
        let rig = makeRig(now: at(16, 0))
        await startTrip(rig)

        await rig.reconciler.myTravelChanged(uid: "other", untilMillis: nil, previousUntilMillis: tripEnd)
        await rig.reconciler.myTravelChanged(uid: "me", untilMillis: nil, previousUntilMillis: nil)

        XCTAssertEqual(Set(rig.scheduler.pending.keys), [n7Identifier])
        XCTAssertEqual(rig.memory.plan.loadTravelEnd(uid: "me"), tripEnd)
    }

    func testTurningTheSwitchOffCancelsN7AndTurningItBackOnBringsItBack() async {
        let rig = makeRig(now: at(16, 0))
        await startTrip(rig)

        rig.preferences.update { $0.setting(.n7, to: false) }
        await rig.reconciler.reconcile()
        XCTAssertTrue(rig.scheduler.pending.isEmpty)

        rig.preferences.update { $0.setting(.n7, to: true) }
        await rig.reconciler.reconcile()
        XCTAssertEqual(Set(rig.scheduler.pending.keys), [n7Identifier])
    }

    func testTheGeneralSwitchOrThePermissionRemovedCancelsN7() async {
        let rig = makeRig(now: at(16, 0))
        await startTrip(rig)

        rig.authorization.value = .denied
        await rig.reconciler.reconcile()

        XCTAssertTrue(rig.scheduler.pending.isEmpty)
    }

    func testNoSessionLeavesNothingWaiting() async {
        let rig = makeRig(now: at(16, 0))
        await startTrip(rig)

        rig.state.uid = nil
        await rig.reconciler.reconcile()

        XCTAssertTrue(rig.scheduler.pending.isEmpty)
    }

    // MARK: - Quand elle sonne

    func testN7ThatFiredIsRecordedOnceInTheCentreWithoutAMiffAndNeverNotifiedAgain() async {
        let rig = makeRig(now: at(16, 0))
        await startTrip(rig)

        rig.scheduler.fire(n7Identifier)
        rig.state.now = due + 60_000
        await rig.reconciler.reconcile()
        await rig.reconciler.reconcile()

        let items = rig.center.history.items.filter { $0.type == .n7 }
        XCTAssertEqual(items.count, 1)
        XCTAssertEqual(items.first?.state, .shown)
        XCTAssertEqual(items.first?.miffId, "", "aucun nom de Miff dans la ligne")
        XCTAssertEqual(items.first?.id, "n7|me|\(tripEnd)")
        XCTAssertNil(rig.scheduler.pending[n7Identifier], "jamais renotifiée")
    }

    func testN7InTheForegroundAtEightLeavesASilentLineOfTheCentre() async {
        let rig = makeRig(now: at(16, 0))
        await startTrip(rig)

        rig.state.foreground = true
        rig.state.now = due + 60_000
        rig.scheduler.fire(n7Identifier)
        rig.reconciler.foregroundDelivery(identifier: n7Identifier)

        XCTAssertEqual(rig.center.history.items.map { $0.type }, [.n7])
        XCTAssertEqual(rig.center.history.items.first?.state, .silent)
    }

    func testAPhoneOffAtEightGivesASilentLineWhenItComesBackThenN7IsForgottenAfterADay() async {
        let rig = makeRig(now: at(16, 0))
        await startTrip(rig)

        rig.scheduler.fire(n7Identifier)
        rig.state.now = due + 5 * 3_600_000
        rig.state.foreground = true
        await rig.reconciler.reconcile()
        XCTAssertEqual(rig.center.history.items.first?.type, .n7)

        rig.state.now = due + 25 * 3_600_000
        await rig.reconciler.reconcile()
        XCTAssertNil(rig.memory.plan.loadTravelEnd(uid: "me"), "plus de 24 h après : le voyage est oublié")
    }

    // MARK: - Retrait à la reprise

    func testADeliveredN7StaysOnScreenWithNoMiffButGoesWhenItsSwitchIsCut() async {
        let rig = makeRig(now: due + 60_000)
        rig.state.active = nil
        rig.memory.plan.saveTravelEnd(tripEnd, uid: "me")
        rig.scheduler.delivered = [n7Identifier]

        await rig.reconciler.reconcile()
        XCTAssertEqual(rig.scheduler.delivered, [n7Identifier])

        rig.preferences.update { $0.setting(.n7, to: false) }
        await rig.reconciler.reconcile()
        XCTAssertTrue(rig.scheduler.delivered.isEmpty)
    }
}
// swiftlint:enable line_length
