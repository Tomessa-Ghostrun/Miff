// Scénarios de test longs par nature (un cas par méthode) : les limites de longueur ne s'appliquent pas ici.
// swiftlint:disable file_length type_body_length
import XCTest
@testable import Miff

// LOC-4a : les écrans de consentement avant le Feed (miroir de `PermissionGateViewModelTest` d'Android). Tout est en mémoire : le
// système, le serveur et la mémoire des écrans déjà montrés sont des doubles. Rien ici ne lit une position.

@MainActor
private final class FakePermissions: DevicePermissions {
    var current: PermissionSnapshot
    var onLocationChange: (() -> Void)?
    /// Ce que le système dit des notifications APRÈS que la personne a répondu à sa fenêtre.
    var notificationsAfterWindow = NotificationAuthorization.authorized
    private(set) var locationRequests = 0
    private(set) var notificationRequests = 0
    private(set) var settingsOpened = 0

    init(_ snapshot: PermissionSnapshot) {
        current = snapshot
    }

    func snapshot() async -> PermissionSnapshot {
        current
    }

    func requestLocationAuthorization() {
        locationRequests += 1
    }

    func requestNotifications() async {
        notificationRequests += 1
        current = PermissionSnapshot(
            location: current.location,
            servicesEnabled: current.servicesEnabled,
            preciseLocation: current.preciseLocation,
            notifications: notificationsAfterWindow
        )
    }

    func openSettings() {
        settingsOpened += 1
    }

    func set(_ location: LocationAuthorization, precise: Bool = true, services: Bool = true) {
        current = PermissionSnapshot(
            location: location,
            servicesEnabled: services,
            preciseLocation: precise,
            notifications: current.notifications
        )
    }
}

private final class FakeAsks: NotificationAskStore {
    var shown = Set<String>()

    func wasShown(uid: String) -> Bool {
        shown.contains(uid)
    }

    func markShown(uid: String) {
        shown.insert(uid)
    }
}

private final class FakeRenewals: ConsentRenewals {
    var checkResult: ConsentRenewalCheck = .nothingToRenew
    /// Vrai : la vérification ne se termine jamais (pas de réseau, serveur muet) : seul le délai de l'écran y met fin.
    var hangs = false
    var renewResult = MiffWriteResult.done
    private(set) var checks = 0
    private(set) var renewCalls: [[String]] = []

    func check(uid: String) async -> ConsentRenewalCheck {
        checks += 1
        if hangs {
            try? await Task.sleep(nanoseconds: 60_000_000_000)
        }
        return checkResult
    }

    func renew(uid: String, miffIds: [String]) async -> MiffWriteResult {
        renewCalls.append(miffIds)
        return renewResult
    }
}

private final class LeaveRecorder {
    var result = MiffWriteResult.done
    private(set) var leaves: [String] = []
    private(set) var left: [String] = []

    func leave(uid: String, miffId: String) async -> MiffWriteResult {
        leaves.append("\(uid)/\(miffId)")
        return result
    }

    func afterLeft(miffId: String) async {
        left.append(miffId)
    }
}

@MainActor
final class PermissionGateViewModelTests: XCTestCase {
    private let uid = "uid-1"
    private let miffId = testMiffId

    private func snap(
        _ location: LocationAuthorization,
        notifications: NotificationAuthorization = .authorized
    ) -> PermissionSnapshot {
        PermissionSnapshot(location: location, notifications: notifications)
    }

    private struct Rig {
        let permissions: FakePermissions
        let asks: FakeAsks
        let renewals: FakeRenewals
        let leaves: LeaveRecorder
        let model: PermissionGateViewModel
    }

    /// Les réglages d'un essai (tout est au défaut du build : un compte connecté, le blocage, l'accord d'abord).
    private struct RigOptions {
        var uid: String? = "uid-1"
        var blocking = true
        var consentFirst = true
        var timeout: TimeInterval = 5
    }

    private func makeRig(
        _ snapshot: PermissionSnapshot,
        _ options: RigOptions = RigOptions(),
        asks: FakeAsks = FakeAsks(),
        renewals: FakeRenewals = FakeRenewals()
    ) -> Rig {
        let permissions = FakePermissions(snapshot)
        let leaves = LeaveRecorder()
        let uid = options.uid
        let model = PermissionGateViewModel(
            dependencies: PermissionGateDependencies(
                permissions: permissions,
                asked: asks,
                renewals: renewals,
                currentUid: { uid },
                leaver: { uid, miffId in await leaves.leave(uid: uid, miffId: miffId) },
                onLeft: { miffId in await leaves.afterLeft(miffId: miffId) }
            ),
            settings: PermissionGateSettings(
                locationBlocking: options.blocking,
                consentBeforeLocation: options.consentFirst,
                checkTimeoutSeconds: options.timeout
            )
        )
        return Rig(permissions: permissions, asks: asks, renewals: renewals, leaves: leaves, model: model)
    }

    private func due(owner: Bool = false, name: String = "Chez nous", id: String? = nil) -> ConsentRenewalCheck {
        .due([RenewalMiff(miffId: id ?? miffId, name: name, isOwner: owner)])
    }

    // MARK: - Rien à demander

    func testEverythingGrantedTheFeedIsShownAndNothingIsAskedOfTheSystem() async {
        let rig = makeRig(snap(.always))

        // Avant la lecture : attente neutre, jamais le Feed ni « Toujours ».
        XCTAssertEqual(rig.model.state.screen, .checking)
        await rig.model.start()

        XCTAssertEqual(rig.model.state.screen, .none)
        XCTAssertEqual(rig.permissions.locationRequests, 0)
        XCTAssertEqual(rig.permissions.notificationRequests, 0)
        XCTAssertEqual(rig.permissions.settingsOpened, 0)
        XCTAssertEqual(rig.renewals.checks, 1)
    }

    // MARK: - Notifications

    func testNotificationsThenLocationAndTheNotificationScreenIsShownOnce() async {
        let asks = FakeAsks()
        let first = makeRig(snap(.notDetermined, notifications: .notDetermined), asks: asks)
        await first.model.start()
        XCTAssertEqual(first.model.state.screen, .notifications)

        await first.model.onNotificationsAllowTapped()

        XCTAssertEqual(first.permissions.notificationRequests, 1)
        XCTAssertTrue(asks.shown.contains(uid))
        XCTAssertEqual(first.model.state.screen, .location, "puis la localisation")
        // Un deuxième toucher n'ouvre pas deux fois la fenêtre du système.
        await first.model.onNotificationsAllowTapped()
        XCTAssertEqual(first.permissions.notificationRequests, 1)

        // Au lancement suivant, même refusée : l'écran ne revient pas (il ne bloque jamais).
        let again = makeRig(snap(.notDetermined, notifications: .notDetermined), asks: asks)
        await again.model.start()
        XCTAssertEqual(again.model.state.screen, .location)
        XCTAssertEqual(again.permissions.notificationRequests, 0)
    }

    func testRefusingTheNotificationsStillLetsThePersonGoOnAndNeverBlocks() async {
        let rig = makeRig(snap(.always, notifications: .notDetermined))
        rig.permissions.notificationsAfterWindow = .denied
        await rig.model.start()
        XCTAssertEqual(rig.model.state.screen, .notifications)

        await rig.model.onNotificationsAllowTapped()

        XCTAssertEqual(rig.model.state.screen, .none)
    }

    // MARK: - La localisation, de la fenêtre aux Réglages

    func testLocationBlocksTheWindowThenTheSettingsAndClosesOnlyWhenAlwaysIsGiven() async {
        let rig = makeRig(snap(.notDetermined))
        await rig.model.start()
        XCTAssertEqual(rig.model.state.screen, .location)
        XCTAssertEqual(rig.model.state.location, LocationScreenModel(variant: .first, action: .systemWindow))

        rig.model.onLocationActionTapped()
        XCTAssertEqual(rig.permissions.locationRequests, 1)
        XCTAssertEqual(rig.permissions.settingsOpened, 0)

        // « Pendant l'utilisation » : « plus qu'une étape », les Réglages (parcours en deux temps).
        rig.permissions.set(.whenInUse)
        await rig.model.onResumed()
        XCTAssertEqual(rig.model.state.screen, .location)
        XCTAssertEqual(rig.model.state.location, LocationScreenModel(variant: .upgrade, action: .openSettings))
        rig.model.onLocationActionTapped()
        XCTAssertEqual(rig.permissions.settingsOpened, 1)
        XCTAssertEqual(rig.permissions.locationRequests, 1, "la fenêtre du système n'est pas redemandée")

        // De retour des Réglages avec un mauvais choix : l'écran est toujours là.
        await rig.model.onResumed()
        XCTAssertEqual(rig.model.state.screen, .location)

        rig.permissions.set(.always)
        await rig.model.onResumed()
        XCTAssertEqual(rig.model.state.screen, .none)
    }

    func testARefusalThenTheSettingsAndTheApproximatePositionAloneIsRefused() async {
        let rig = makeRig(snap(.notDetermined))
        await rig.model.start()

        rig.permissions.set(.denied)
        await rig.model.onResumed()
        XCTAssertEqual(rig.model.state.location?.variant, .denied)

        // « Toujours » mais la position exacte éteinte : refusé, l'écran reste.
        rig.permissions.set(.always, precise: false)
        await rig.model.onResumed()
        XCTAssertEqual(rig.model.state.screen, .location)
        XCTAssertEqual(rig.model.state.location?.variant, .approximate)

        rig.permissions.set(.always, precise: true)
        await rig.model.onResumed()
        XCTAssertEqual(rig.model.state.screen, .none)
    }

    func testTheServiceTurnedOffOnTheWholePhoneAndTheRestrictedCase() async {
        let rig = makeRig(snap(.always))
        await rig.model.start()
        XCTAssertEqual(rig.model.state.screen, .none)

        rig.permissions.set(.always, services: false)
        await rig.model.onResumed()
        XCTAssertEqual(rig.model.state.location?.variant, .servicesOff)

        rig.permissions.set(.restricted)
        await rig.model.onResumed()
        XCTAssertEqual(rig.model.state.location?.variant, .restricted)
        // Rien à faire depuis l'app : le bouton ne fait rien (et l'écran n'en montre pas).
        rig.model.onLocationActionTapped()
        XCTAssertEqual(rig.permissions.settingsOpened, 0)
        XCTAssertEqual(rig.permissions.locationRequests, 0)
    }

    func testAWithdrawalSeenWhenTheAppComesBackToTheFrontBringsTheBlockingScreenBack() async {
        let rig = makeRig(snap(.always))
        await rig.model.start()
        XCTAssertEqual(rig.model.state.screen, .none)

        rig.permissions.set(.denied)
        await rig.model.onResumed()

        XCTAssertEqual(rig.model.state.screen, .location)
        XCTAssertEqual(rig.model.state.location?.variant, .denied)

        rig.permissions.set(.always)
        await rig.model.onResumed()
        XCTAssertEqual(rig.model.state.screen, .none)
    }

    func testTheSystemTellingUsTheAuthorizationChangedRefreshesTheScreen() async {
        let rig = makeRig(snap(.notDetermined))
        await rig.model.start()
        XCTAssertEqual(rig.model.state.location?.variant, .first)

        rig.permissions.set(.whenInUse)
        rig.permissions.onLocationChange?()
        await waitUntil { rig.model.state.location?.variant == .upgrade }

        XCTAssertEqual(rig.model.state.location?.variant, .upgrade)
    }

    // MARK: - L'interrupteur de compilation, dans ses deux positions

    func testTheBuildSwitchBlockingCannotBePutOffRelaxedCan() async {
        let blocking = makeRig(snap(.whenInUse), RigOptions(blocking: true))
        await blocking.model.start()
        XCTAssertFalse(blocking.model.state.canPutOffLocation, "aucun « Plus tard » quand le build bloque")
        blocking.model.onLocationPutOff()
        XCTAssertEqual(blocking.model.state.screen, .location, "même appelé, « Plus tard » ne laisse pas passer")

        let relaxed = makeRig(snap(.whenInUse), RigOptions(blocking: false))
        await relaxed.model.start()
        XCTAssertTrue(relaxed.model.state.canPutOffLocation)
        XCTAssertEqual(relaxed.model.state.screen, .location)
        relaxed.model.onLocationPutOff()
        XCTAssertEqual(relaxed.model.state.screen, .none)
        // « Plus tard » n'est gardé qu'en mémoire : au lancement suivant l'écran revient.
        let next = makeRig(snap(.whenInUse), RigOptions(blocking: false))
        await next.model.start()
        XCTAssertEqual(next.model.state.screen, .location)
    }

    // MARK: - Le renouvellement du consentement

    func testAnOldConsentShowsTheRenewalScreenAndNotNowWritesNothing() async {
        let renewals = FakeRenewals()
        renewals.checkResult = due()
        let rig = makeRig(snap(.always), renewals: renewals)

        await rig.model.start()
        XCTAssertEqual(rig.model.state.screen, .renewal)
        XCTAssertEqual(rig.model.state.renewal?.miffs.map { $0.miffId }, [miffId])

        rig.model.onRenewalPutOff()

        XCTAssertEqual(rig.model.state.screen, .none)
        XCTAssertTrue(renewals.renewCalls.isEmpty, "« Pas maintenant » n'écrit rien")
    }

    func testNotNowComesBackAtTheNextLaunch() async {
        let renewals = FakeRenewals()
        renewals.checkResult = due()
        let first = makeRig(snap(.always), renewals: renewals)
        await first.model.start()
        first.model.onRenewalPutOff()
        XCTAssertEqual(first.model.state.screen, .none)

        let next = makeRig(snap(.always), renewals: renewals)
        await next.model.start()

        XCTAssertEqual(next.model.state.screen, .renewal)
    }

    func testAgreeingWritesOnceClosesTheScreenAndAnExtraTapIsIgnored() async {
        let renewals = FakeRenewals()
        renewals.checkResult = due()
        let rig = makeRig(snap(.always), renewals: renewals)
        await rig.model.start()

        await rig.model.onRenewalAccepted()
        await rig.model.onRenewalAccepted()

        XCTAssertEqual(renewals.renewCalls, [[miffId]])
        XCTAssertEqual(rig.model.state.screen, .none)
        XCTAssertNil(rig.model.state.renewal)
    }

    func testAFailedRenewalStaysOnTheScreenWithACalmMessageAndNothingIsRetriedByItself() async {
        let renewals = FakeRenewals()
        renewals.checkResult = due()
        renewals.renewResult = .failed(.offline)
        let rig = makeRig(snap(.notDetermined), renewals: renewals)
        await rig.model.start()

        await rig.model.onRenewalAccepted()

        XCTAssertEqual(rig.model.state.screen, .renewal)
        XCTAssertEqual(rig.model.state.renewal?.message, .offline)
        XCTAssertEqual(rig.model.state.renewal?.busy, false)
        XCTAssertEqual(renewals.renewCalls.count, 1)
        // Jamais l'écran « Toujours » avant l'accord, même après un échec.
        XCTAssertNotEqual(rig.model.state.screen, .location)

        // Réessayer est un geste de la personne ; le message s'efface à ce moment-là.
        renewals.renewResult = .done
        await rig.model.onRenewalAccepted()
        XCTAssertEqual(renewals.renewCalls.count, 2)
        XCTAssertEqual(rig.model.state.screen, .location, "accord donné : alors seulement « Toujours »")
    }

    func testWhenTheServerCannotBeAskedNothingIsClaimedAndNothingBlocks() async {
        let renewals = FakeRenewals()
        renewals.checkResult = .unknown
        let rig = makeRig(snap(.always), renewals: renewals)

        await rig.model.start()

        XCTAssertEqual(rig.model.state.screen, .none)
        XCTAssertNil(rig.model.state.renewal)
        XCTAssertTrue(renewals.renewCalls.isEmpty)
    }

    func testConsentFirstWhileItIsDueTheLocationScreenWaitsForTheAgreement() async {
        let renewals = FakeRenewals()
        renewals.checkResult = due()
        let rig = makeRig(snap(.notDetermined), renewals: renewals)
        await rig.model.start()
        XCTAssertEqual(rig.model.state.screen, .renewal)

        // « Pas maintenant » : toujours pas d'écran « Toujours » (aucune permission demandée avant l'accord).
        rig.model.onRenewalPutOff()
        XCTAssertNotEqual(rig.model.state.screen, .location)
        XCTAssertEqual(rig.permissions.locationRequests, 0)
    }

    func testTheOtherOrderTheLocationScreenStaysBlockingWhateverTheConsent() async {
        let renewals = FakeRenewals()
        renewals.checkResult = due()
        let rig = makeRig(snap(.notDetermined), RigOptions(consentFirst: false), renewals: renewals)

        await rig.model.start()

        XCTAssertEqual(rig.model.state.screen, .location)
        rig.model.onRenewalPutOff()
        XCTAssertEqual(rig.model.state.screen, .location)
    }

    func testTheLocationScreenWaitsForTheEndOfTheCheckAndTheWaitIsBounded() async throws {
        let renewals = FakeRenewals()
        renewals.hangs = true
        let rig = makeRig(snap(.notDetermined), RigOptions(timeout: 0.4), renewals: renewals)

        let start = Task { await rig.model.start() }
        try await Task.sleep(nanoseconds: 100_000_000)
        // La lecture des autorisations est faite, la vérification traîne : attente neutre, jamais « Toujours » avant la fin.
        XCTAssertEqual(rig.model.state.screen, .checking)

        await start.value

        // Le délai a mis fin à l'attente : « inconnu », rien n'est affirmé, l'écran « Toujours » est montré.
        XCTAssertEqual(rig.model.state.screen, .location)
        XCTAssertNil(rig.model.state.renewal)
    }

    // MARK: - Quitter la Miff depuis l'écran de renouvellement

    func testAMemberWithOneMiffCanLeaveFromTheRenewalScreenAfterConfirming() async {
        let renewals = FakeRenewals()
        renewals.checkResult = due(owner: false)
        let rig = makeRig(snap(.always), renewals: renewals)
        await rig.model.start()
        XCTAssertNotNil(rig.model.state.renewal?.leavable)

        rig.model.onLeaveRequested()
        XCTAssertEqual(rig.model.state.renewal?.leaveAsked, true)
        XCTAssertTrue(rig.leaves.leaves.isEmpty, "rien n'est écrit avant la confirmation")

        // « Annuler » est le choix sûr : rien n'est écrit.
        rig.model.onLeaveCancelled()
        XCTAssertEqual(rig.model.state.renewal?.leaveAsked, false)
        XCTAssertTrue(rig.leaves.leaves.isEmpty)

        rig.model.onLeaveRequested()
        await rig.model.onLeaveConfirmed()

        XCTAssertEqual(rig.leaves.leaves, ["\(uid)/\(miffId)"])
        XCTAssertEqual(rig.leaves.left, [miffId])
        XCTAssertNil(rig.model.state.renewal, "une fois la Miff quittée, il ne reste rien à accepter")
        XCTAssertEqual(rig.model.state.screen, .none)
        XCTAssertTrue(renewals.renewCalls.isEmpty)
    }

    func testCancellingTheLeaveQuestionWritesNothingAndAFailedLeaveKeepsTheScreenWithACalmMessage() async {
        let renewals = FakeRenewals()
        renewals.checkResult = due()
        let rig = makeRig(snap(.always), renewals: renewals)
        await rig.model.start()
        rig.leaves.result = .failed(.offline)

        rig.model.onLeaveRequested()
        await rig.model.onLeaveConfirmed()

        XCTAssertEqual(rig.model.state.screen, .renewal)
        XCTAssertEqual(rig.model.state.renewal?.message, .offline)
        XCTAssertTrue(rig.leaves.left.isEmpty, "rien n'est purgé tant que le serveur n'a pas dit oui")
        // Une confirmation sans question ouverte ne fait rien.
        rig.model.onLeaveCancelled()
        await rig.model.onLeaveConfirmed()
        XCTAssertEqual(rig.leaves.leaves.count, 1)
    }

    func testTheCreatorAndSeveralMiffsHaveNoLeaveLink() async {
        let creator = FakeRenewals()
        creator.checkResult = due(owner: true)
        let first = makeRig(snap(.always), renewals: creator)
        await first.model.start()
        XCTAssertNil(first.model.state.renewal?.leavable)
        first.model.onLeaveRequested()
        XCTAssertEqual(first.model.state.renewal?.leaveAsked, false)
        await first.model.onLeaveConfirmed()
        XCTAssertTrue(first.leaves.leaves.isEmpty)

        let several = FakeRenewals()
        several.checkResult = .due([
            RenewalMiff(miffId: miffId, name: "Une"),
            RenewalMiff(miffId: testOtherMiffId, name: "Deux")
        ])
        let second = makeRig(snap(.always), renewals: several)
        await second.model.start()
        XCTAssertNil(second.model.state.renewal?.leavable)
        second.model.onLeaveRequested()
        XCTAssertEqual(second.model.state.renewal?.leaveAsked, false)
    }

    func testNoSessionNothingIsLookedAt() async {
        let renewals = FakeRenewals()
        renewals.checkResult = due()
        let rig = makeRig(snap(.always), RigOptions(uid: nil), renewals: renewals)

        await rig.model.start()

        XCTAssertEqual(renewals.checks, 0, "aucune lecture du serveur sans session")
        XCTAssertEqual(rig.model.state.screen, .none)
        await rig.model.onRenewalAccepted()
        XCTAssertTrue(renewals.renewCalls.isEmpty)
    }

    func testTheViewModelNeverDescribesANameInItsText() {
        let state = RenewalUiState(miffs: [RenewalMiff(miffId: miffId, name: "Chez nous")])

        XCTAssertFalse(String(describing: state).contains("Chez nous"))
        XCTAssertFalse(String(describing: RenewalMiff(miffId: miffId, name: "Chez nous")).contains("Chez nous"))
        let check = ConsentRenewalCheck.due([RenewalMiff(miffId: miffId, name: "Zorglub")])
        XCTAssertFalse(String(describing: check).contains("Zorglub"))
    }
}
// swiftlint:enable type_body_length
