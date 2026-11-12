// Les assertions citent des cas entiers : certaines lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// Démarrage à froid (réveil par géofence, app tuée) : AUCUN écran n'a appelé `reload()` ni `center.load()`. Les choix de la personne et les
/// entrées du jour sont lus depuis l'appareil avant toute décision ; les valeurs par défaut (tout actif) ne passent jamais avant un
/// interrupteur coupé (défaut de consentement). Plus : la session qui finit pendant une réconciliation, et la permission retirée entre-temps.
@MainActor
final class NotificationColdStartTests: ReconcilerTestCase {
    private var leave: StatusFact {
        StatusFact(presence: .out, placeId: "p1", presenceAtMillis: at(13, 40), setLastUpdate: false)
    }

    private func coldRig(_ preferences: NotificationPreferences) -> Rig {
        let rig = makeRig(now: at(14, 0))
        // Le choix est dans le magasin de l'appareil, comme après un lancement précédent ; aucun écran ne l'a relu.
        rig.memory.preferences.save(preferences, uid: "me")
        return rig
    }

    // MARK: - Les choix coupés tiennent à froid

    func testColdStartAnOffSwitchHoldsWhenALeaveWakesTheApp() async {
        let rig = coldRig(NotificationPreferences().setting(.n1, to: false))

        await rig.reconciler.myPresenceChanged(miffId: miffId, fact: leave)

        XCTAssertEqual(Set(rig.scheduler.pending.keys), [identifier(.n2)], "N1 coupée dans le magasin : jamais programmée")
        XCTAssertTrue(rig.preferences.preferences.typeValue(.n2), "les autres choix sont intacts")
        XCTAssertFalse(rig.preferences.preferences.typeValue(.n1))
    }

    func testColdStartTheGeneralSwitchOffProgramsNothingAtAll() async {
        let rig = coldRig(NotificationPreferences().settingGeneral(false))

        await rig.reconciler.myPresenceChanged(miffId: miffId, fact: leave)

        XCTAssertTrue(rig.scheduler.pending.isEmpty)
        XCTAssertTrue(rig.scheduler.added.isEmpty)
    }

    func testColdStartBothReminderSwitchesOffProgramNothing() async {
        let rig = coldRig(NotificationPreferences().setting(.n1, to: false).setting(.n2, to: false))

        await rig.reconciler.myPresenceChanged(miffId: miffId, fact: leave)

        XCTAssertTrue(rig.scheduler.pending.isEmpty)
    }

    func testColdStartTheN7SwitchOffHoldsToo() async {
        let rig = coldRig(NotificationPreferences().setting(.n7, to: false))
        rig.memory.plan.saveTravelEnd(at(24, 0), uid: "me")

        await rig.reconciler.reconcile()

        XCTAssertTrue(rig.scheduler.pending.isEmpty, "N7 est coupée dans le magasin")
    }

    func testColdStartTheGeneralSwitchOffHoldsForAnAnnouncedTrip() async {
        let rig = coldRig(NotificationPreferences().settingGeneral(false))

        await rig.reconciler.myTravelChanged(uid: "me", untilMillis: at(24, 0), previousUntilMillis: nil)

        XCTAssertTrue(rig.scheduler.pending.isEmpty)
    }

    func testColdStartWithTheDefaultsEverythingIsStillProgrammed() async {
        let rig = coldRig(NotificationPreferences())

        await rig.reconciler.myPresenceChanged(miffId: miffId, fact: leave)

        XCTAssertEqual(Set(rig.scheduler.pending.keys), [identifier(.n1), identifier(.n2)])
    }

    // MARK: - Les plafonds du jour tiennent à froid

    func testColdStartTheDailyCapsAreRespectedFromTheCentreFile() async {
        let rig = coldRig(NotificationPreferences())
        // Six notifications déjà affichées aujourd'hui, inscrites dans le fichier du centre (jamais relu par un écran).
        let shown = [NotificationType.n1, .n2, .n3].flatMap { type in
            (0..<2).map { NotificationFixtures.item("\(type.rawValue)|old\($0)", type: type, at: at(10, $0)) }
        }
        rig.memory.center.seed(shown, uid: "me")

        await rig.reconciler.myPresenceChanged(miffId: miffId, fact: leave)

        XCTAssertTrue(rig.scheduler.pending.isEmpty, "les six du jour sont atteintes : ni N1 ni N2")
    }

    // MARK: - Le chargement paresseux des choix

    func testThePreferencesServiceLoadsLazilyOncePerAccountAndFollowsAChangeOfAccount() {
        let memory = NotificationMemory()
        var uid: String? = "me"
        let service = NotificationPreferencesService(store: memory.preferences, currentUid: { uid })
        memory.preferences.save(NotificationPreferences().setting(.n3, to: false), uid: "me")
        memory.preferences.save(NotificationPreferences().setting(.n4, to: false), uid: "other")

        XCTAssertTrue(service.preferences.typeValue(.n3), "avant tout chargement : les valeurs par défaut")
        service.ensureLoaded()
        XCTAssertFalse(service.preferences.typeValue(.n3))
        uid = "other"
        service.ensureLoaded()
        XCTAssertFalse(service.preferences.typeValue(.n4))
        XCTAssertTrue(service.preferences.typeValue(.n3))
        service.forget()
        uid = "me"
        service.ensureLoaded()
        XCTAssertTrue(service.preferences.typeValue(.n3), "oubliés à la fin de session : le magasin est vidé")
    }

    // MARK: - La session finit pendant la réconciliation (I1)

    func testTheSessionEndingDuringAReconciliationProgramsNothingMoreAndWritesNoLedger() async {
        let rig = makeRig(now: at(14, 0))
        seed(rig, [out("me", ref: at(13, 40)), green("x")])
        let state = rig.state
        rig.scheduler.onAdd = { state.uid = nil }

        await rig.reconciler.reconcile()

        XCTAssertEqual(rig.scheduler.added.count, 1, "la demande suivante ne part pas : le compte est parti")
        XCTAssertTrue(rig.memory.plan.loadLedger(uid: "me").scheduled.isEmpty, "aucun registre écrit pour un compte parti")
    }

    // MARK: - La permission retirée entre-temps (M4)

    func testARequestMissingFromPendingIsNotRecordedAsShownWhenThePermissionWasRemoved() async {
        let rig = makeRig(now: at(14, 0))
        seed(rig, [out("me", ref: at(13, 40)), green("x")])
        await rig.reconciler.reconcile()
        await rig.scheduler.removePending([identifier(.n1)])

        rig.state.now = at(16, 0)
        rig.authorization.value = .denied
        await rig.reconciler.reconcile()

        XCTAssertTrue(rig.center.history.items.isEmpty, "on ne sait pas si elle a sonné : jamais « affichée »")
    }

    func testARequestMissingFromPendingIsRecordedAsShownWhenThePermissionHolds() async {
        let rig = makeRig(now: at(14, 0))
        seed(rig, [out("me", ref: at(13, 40)), green("x")])
        await rig.reconciler.reconcile()
        rig.scheduler.fire(identifier(.n1))

        rig.state.now = at(16, 0)
        await rig.reconciler.reconcile()

        XCTAssertEqual(rig.center.history.items.filter { $0.type == .n1 }.count, 1)
    }
}
// swiftlint:enable line_length
