import XCTest
@testable import Miff

// SRV-2I : l'accord à être notifiée n'est enregistré QUE depuis l'écran des notifications (qui porte la phrase sur l'identifiant du téléphone),
// et seulement si le système autorise. Les comptes qui avaient autorisé AVANT cette version revoient l'écran une fois. Tout est en mémoire.

@MainActor
private final class GatePermissions: DevicePermissions {
    var current: PermissionSnapshot
    var onLocationChange: (() -> Void)?
    var notificationsAfterWindow = NotificationAuthorization.authorized
    private(set) var notificationRequests = 0

    init(notifications: NotificationAuthorization) {
        current = PermissionSnapshot(location: .always, notifications: notifications)
    }

    func snapshot() async -> PermissionSnapshot {
        current
    }

    func requestLocationAuthorization() {
        // Jamais appelé ici.
    }

    func requestNotifications() async {
        notificationRequests += 1
        if current.notifications == .notDetermined {
            current = PermissionSnapshot(location: .always, notifications: notificationsAfterWindow)
        }
    }

    func openSettings() {
        // Jamais appelé ici.
    }
}

private final class GateAsks: NotificationAskStore {
    var shown = Set<String>()

    func wasShown(uid: String) -> Bool {
        shown.contains(uid)
    }

    func markShown(uid: String) {
        shown.insert(uid)
    }
}

private final class GateRenewals: ConsentRenewals {
    func check(uid: String) async -> ConsentRenewalCheck {
        .nothingToRenew
    }

    func renew(uid: String, miffIds: [String]) async -> MiffWriteResult {
        .done
    }
}

private final class GateConsent: PushConsentStore {
    var given = Set<String>()
    private(set) var writes: [String] = []

    func isGiven(uid: String) -> Bool {
        given.contains(uid)
    }

    func give(uid: String) {
        given.insert(uid)
        writes.append(uid)
    }

    func forget(uid: String) {
        given.remove(uid)
    }
}

@MainActor
final class PushConsentGateTests: XCTestCase {
    private struct Rig {
        let permissions: GatePermissions
        let asks: GateAsks
        let consent: GateConsent
        let model: PermissionGateViewModel
        let answered: Counter
    }

    private final class Counter {
        var value = 0
    }

    private func makeRig(
        notifications: NotificationAuthorization,
        shownBefore: Bool = false,
        consentBefore: Bool = false
    ) -> Rig {
        let permissions = GatePermissions(notifications: notifications)
        let asks = GateAsks()
        if shownBefore {
            asks.shown.insert("uid-1")
        }
        let consent = GateConsent()
        if consentBefore {
            consent.given.insert("uid-1")
        }
        let answered = Counter()
        let model = PermissionGateViewModel(
            dependencies: PermissionGateDependencies(
                permissions: permissions,
                asked: asks,
                renewals: GateRenewals(),
                currentUid: { "uid-1" },
                leaver: { _, _ in .done },
                onLeft: { _ in },
                pushConsent: consent,
                onNotificationsAnswered: { answered.value += 1 }
            ),
            settings: PermissionGateSettings(locationBlocking: true)
        )
        return Rig(permissions: permissions, asks: asks, consent: consent, model: model, answered: answered)
    }

    func testAllowingOnTheScreenThatTalksAboutTheIdentifierRecordsTheConsentAndSyncsAtOnce() async {
        let rig = makeRig(notifications: .notDetermined)
        await rig.model.start()
        XCTAssertEqual(rig.model.state.screen, .notifications)

        await rig.model.onNotificationsAllowTapped()

        XCTAssertTrue(rig.consent.given.contains("uid-1"))
        XCTAssertEqual(rig.answered.value, 1, "la synchronisation part tout de suite, pas au retour au premier plan")
        XCTAssertEqual(rig.model.state.screen, .none)
    }

    func testRefusingInTheSystemWindowRecordsNoConsent() async {
        let rig = makeRig(notifications: .notDetermined)
        rig.permissions.notificationsAfterWindow = .denied
        await rig.model.start()

        await rig.model.onNotificationsAllowTapped()

        XCTAssertTrue(rig.consent.given.isEmpty)
        XCTAssertEqual(rig.answered.value, 0)
    }

    func testAnAccountThatAllowedBeforeThisVersionSeesTheCorrectedScreenOnce() async {
        let rig = makeRig(notifications: .authorized, shownBefore: true)
        await rig.model.start()
        XCTAssertEqual(rig.model.state.screen, .notifications, "l'ancien texte ne parlait pas de l'identifiant")

        await rig.model.onNotificationsAllowTapped()

        XCTAssertTrue(rig.consent.given.contains("uid-1"))
        XCTAssertEqual(rig.model.state.screen, .none)
    }

    func testAnAccountThatAlreadyConsentedIsNotAskedAgain() async {
        let rig = makeRig(notifications: .authorized, shownBefore: true, consentBefore: true)
        await rig.model.start()

        XCTAssertEqual(rig.model.state.screen, .none)
        XCTAssertEqual(rig.permissions.notificationRequests, 0)
    }

    func testDeniedNotificationsNeverBringTheScreenBack() async {
        let rig = makeRig(notifications: .denied, shownBefore: true)
        await rig.model.start()

        XCTAssertEqual(rig.model.state.screen, .none)
        XCTAssertTrue(rig.consent.given.isEmpty)
    }

    func testTheAnswerToTheGateNeverRecordsTheConsentOfAnotherPlace() async {
        // Un seul endroit enregistre l'accord : la porte. Aucun autre appel à `give` dans cette configuration.
        let rig = makeRig(notifications: .notDetermined)
        await rig.model.start()
        XCTAssertTrue(rig.consent.writes.isEmpty)
    }
}
