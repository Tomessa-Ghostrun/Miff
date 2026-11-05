import CoreLocation
import UserNotifications
import XCTest
@testable import Miff

/// LOC-4a : la logique PURE des autorisations (miroir de `PermissionDomainTest` d'Android) : ce qui est « complet », l'ordre des
/// écrans de la porte, la machine de l'écran de la localisation, l'interrupteur de compilation dans ses deux positions. Rien ici
/// ne demande quoi que ce soit au système et rien ne lit une position.
final class PermissionDomainTests: XCTestCase {
    private func snap(
        _ location: LocationAuthorization,
        services: Bool = true,
        precise: Bool = true,
        notifications: NotificationAuthorization = .authorized
    ) -> PermissionSnapshot {
        PermissionSnapshot(
            location: location,
            servicesEnabled: services,
            preciseLocation: precise,
            notifications: notifications
        )
    }

    /// Les réglages d'un essai de la porte (tout est au défaut du build : bloquant, l'accord d'abord).
    private struct Flags {
        var handled = false
        var blocking = true
        var deferred = false
        var renewal = RenewalState.nothingDue
        var renewalDeferred = false
        var consentFirst = true
    }

    private func gate(_ snapshot: PermissionSnapshot, _ flags: Flags = Flags()) -> GateScreen {
        resolveGate(
            GateInput(
                snapshot: snapshot,
                notificationsHandled: flags.handled,
                locationBlocking: flags.blocking,
                locationDeferred: flags.deferred,
                renewal: flags.renewal,
                renewalDeferred: flags.renewalDeferred,
                consentBeforeLocation: flags.consentFirst
            )
        )
    }

    // MARK: - Ce qui est « complet »

    func testLocationIsCompleteOnlyWithAlwaysAndThePreciseLocationAndTheServiceOn() {
        XCTAssertTrue(snap(.always).locationComplete)
        // La position approximative seule est refusée (décision du PM) : la surveillance des zones a besoin de l'exacte.
        XCTAssertFalse(snap(.always, precise: false).locationComplete)
        XCTAssertFalse(snap(.always, services: false).locationComplete)
        for other in [LocationAuthorization.whenInUse, .denied, .restricted, .notDetermined] {
            XCTAssertFalse(snap(other).locationComplete, "\(other)")
        }
    }

    func testNotificationsAreAskedOnlyWhenTheSystemWindowCanStillShow() {
        XCTAssertTrue(snap(.always, notifications: .notDetermined).notificationsAskable)
        // Refusées : le système ne montrerait plus sa fenêtre, un écran qui la promet n'aurait aucun sens.
        XCTAssertFalse(snap(.always, notifications: .denied).notificationsAskable)
        XCTAssertFalse(snap(.always, notifications: .authorized).notificationsAskable)
    }

    // MARK: - La porte

    func testNothingIsAskedWhenEverythingIsGranted() {
        XCTAssertEqual(gate(snap(.always)), .none)
    }

    func testNotificationsComeFirstAndAreSkippedWhenGrantedRefusedOrAlreadyShown() {
        let everything = snap(.notDetermined, notifications: .notDetermined)
        XCTAssertEqual(gate(everything), .notifications)
        XCTAssertEqual(gate(everything, Flags(handled: true)), .location)
        XCTAssertEqual(gate(snap(.notDetermined, notifications: .authorized)), .location)
        XCTAssertEqual(gate(snap(.notDetermined, notifications: .denied)), .location)
        // Jamais bloquantes : toutes les réponses laissent passer quand la localisation est complète.
        XCTAssertEqual(gate(snap(.always, notifications: .denied)), .none)
    }

    func testTheLocationScreenBlocksUntilAlwaysAndComesBackWhenWithdrawn() {
        XCTAssertEqual(gate(snap(.notDetermined)), .location)
        XCTAssertEqual(gate(snap(.whenInUse)), .location)
        XCTAssertEqual(gate(snap(.denied)), .location)
        XCTAssertEqual(gate(snap(.restricted)), .location)
        XCTAssertEqual(gate(snap(.always, precise: false)), .location)
        XCTAssertEqual(gate(snap(.always)), .none)
        // Retrait constaté au retour au premier plan : l'écran bloquant réapparaît (critère AUT5).
        XCTAssertEqual(gate(snap(.denied)), .location)
    }

    func testTheBuildSwitchInBothPositions() {
        let missing = snap(.whenInUse)
        // Par défaut (bloquant) : « Plus tard » ne laisse jamais passer, même s'il a été demandé.
        XCTAssertEqual(gate(missing, Flags(blocking: true, deferred: false)), .location)
        XCTAssertEqual(gate(missing, Flags(blocking: true, deferred: true)), .location)
        // Relâché : l'écran est montré, et « Plus tard » laisse passer.
        XCTAssertEqual(gate(missing, Flags(blocking: false, deferred: false)), .location)
        XCTAssertEqual(gate(missing, Flags(blocking: false, deferred: true)), .none)
        // Relâché ou non, une localisation complète ne montre rien.
        XCTAssertEqual(gate(snap(.always), Flags(blocking: false)), .none)
        XCTAssertEqual(gate(snap(.always), Flags(blocking: true)), .none)
    }

    func testTheRenewalComesBeforeTheLocationWhenTheAgreementIsFirst() {
        let missing = snap(.notDetermined)
        XCTAssertEqual(gate(missing, Flags(renewal: .due)), .renewal)
        // « Pas maintenant » : rien n'est écrit, et l'écran « Toujours » n'est pas montré avant l'accord (décision (a) du PM).
        XCTAssertEqual(gate(missing, Flags(renewal: .due, renewalDeferred: true)), .none)
        // Les notifications passent avant tout.
        XCTAssertEqual(gate(snap(.notDetermined, notifications: .notDetermined), Flags(renewal: .due)), .notifications)
        // Une localisation complète : le renouvellement dû est montré quand même.
        XCTAssertEqual(gate(snap(.always), Flags(renewal: .due)), .renewal)
        XCTAssertEqual(gate(snap(.always), Flags(renewal: .due, renewalDeferred: true)), .none)
    }

    func testWhileTheCheckRunsNeitherTheLocationScreenNorTheFeedWhenTheLocationIsMissing() {
        // Un membre qui partage sous un ancien texte ne doit pas pouvoir donner « Toujours » AVANT l'accord.
        XCTAssertEqual(gate(snap(.notDetermined), Flags(renewal: .checking)), .checking)
        XCTAssertEqual(gate(snap(.whenInUse), Flags(renewal: .checking)), .checking)
        // Localisation complète : rien à attendre, le Feed passe.
        XCTAssertEqual(gate(snap(.always), Flags(renewal: .checking)), .none)
        // La vérification se termine sans réponse : plus rien ne bloque, l'écran « Toujours » est montré.
        XCTAssertEqual(gate(snap(.notDetermined), Flags(renewal: .unknown)), .location)
        XCTAssertEqual(gate(snap(.notDetermined), Flags(renewal: .nothingDue)), .location)
    }

    func testTheOtherOrderTheLocationStaysBlockingWhateverTheConsent() {
        let missing = snap(.notDetermined)
        XCTAssertEqual(gate(missing, Flags(renewal: .due, consentFirst: false)), .location)
        XCTAssertEqual(gate(missing, Flags(renewal: .due, renewalDeferred: true, consentFirst: false)), .location)
        XCTAssertEqual(gate(missing, Flags(renewal: .checking, consentFirst: false)), .location)
        // Une fois la localisation donnée, le renouvellement dû est montré.
        XCTAssertEqual(gate(snap(.always), Flags(renewal: .due, consentFirst: false)), .renewal)
    }

    func testThePolicyConstantsAreTheOnesTheCoordinatorAskedFor() {
        // Les deux constantes ENCORE en attente de décision du PM (points ouverts de la PR) : une ligne à changer, un test.
        XCTAssertTrue(GatePolicy.consentBeforeLocation)
        XCTAssertTrue(GatePolicy.exitLinksOnBlockingScreens)
    }

    // MARK: - L'écran de la localisation (maquettes 08, 09, 10)

    func testEachSystemStateHasItsOwnScreen() {
        XCTAssertNil(LocationFlow.model(for: snap(.always)))
        XCTAssertEqual(
            LocationFlow.model(for: snap(.notDetermined)),
            LocationScreenModel(variant: .first, action: .systemWindow)
        )
        XCTAssertEqual(
            LocationFlow.model(for: snap(.whenInUse)),
            LocationScreenModel(variant: .upgrade, action: .openSettings)
        )
        XCTAssertEqual(
            LocationFlow.model(for: snap(.denied)),
            LocationScreenModel(variant: .denied, action: .openSettings)
        )
        XCTAssertEqual(
            LocationFlow.model(for: snap(.restricted)),
            LocationScreenModel(variant: .restricted, action: LocationAction.none)
        )
        XCTAssertEqual(
            LocationFlow.model(for: snap(.always, precise: false)),
            LocationScreenModel(variant: .approximate, action: .openSettings)
        )
    }

    func testTheSystemWindowIsOnlyEverOfferedWhenTheSystemCanStillShowIt() {
        // Jamais demandée : la fenêtre. Dans tous les autres cas (refusée, « pendant l'utilisation », restreinte, service coupé),
        // le bouton ne promet pas une fenêtre que le système ne montrerait plus.
        for location in [LocationAuthorization.whenInUse, .denied, .restricted, .always] {
            let model = LocationFlow.model(for: snap(location, precise: false))
            XCTAssertNotEqual(model?.action, .systemWindow, "\(location)")
        }
        XCTAssertNotEqual(LocationFlow.model(for: snap(.notDetermined, services: false))?.action, .systemWindow)
    }

    func testTheServiceTurnedOffOnTheWholePhoneComesFirst() {
        for location in [LocationAuthorization.notDetermined, .whenInUse, .denied, .always] {
            XCTAssertEqual(
                LocationFlow.model(for: snap(location, services: false)),
                LocationScreenModel(variant: .servicesOff, action: .openSettings),
                "\(location)"
            )
        }
    }

    func testTheScreenIsDecidedByTheStateOfTheSystemAloneSoAWithdrawalWhileClosedGivesTheRightScreen() {
        // Pas d'historique à retrouver : « Toujours » retiré app fermée donne, à la réouverture, l'écran « refusée ».
        XCTAssertEqual(LocationFlow.model(for: snap(.denied))?.variant, .denied)
        // Retiré pour « pendant l'utilisation » : « plus qu'une étape ».
        XCTAssertEqual(LocationFlow.model(for: snap(.whenInUse))?.variant, .upgrade)
    }

    // MARK: - L'interrupteur de compilation

    func testTheBlockingSwitchInBothPositionsAndSafeByDefault() {
        let key = LocationBlockingSwitch.infoKey
        XCTAssertEqual(key, "MiffLocationBlocking")
        // Bloquant : la valeur de project.yml, ou rien du tout.
        XCTAssertTrue(LocationBlockingSwitch.isBlocking(info: [key: "YES"]))
        XCTAssertTrue(LocationBlockingSwitch.isBlocking(info: nil))
        XCTAssertTrue(LocationBlockingSwitch.isBlocking(info: [:]))
        // Relâché : seulement un « NO » explicite.
        XCTAssertFalse(LocationBlockingSwitch.isBlocking(info: [key: "NO"]))
        XCTAssertFalse(LocationBlockingSwitch.isBlocking(info: [key: "no"]))
        XCTAssertFalse(LocationBlockingSwitch.isBlocking(info: [key: " NO "]))
        XCTAssertFalse(LocationBlockingSwitch.isBlocking(info: [key: false]))
        // Toute valeur inconnue BLOQUE : un réglage non résolu ne relâche jamais rien.
        for unknown in ["$(MIFF_LOCATION_BLOCKING)", "", "maybe", "N", "yes please"] {
            XCTAssertTrue(LocationBlockingSwitch.isBlocking(info: [key: unknown]), unknown)
        }
        XCTAssertTrue(LocationBlockingSwitch.isBlocking(info: [key: 42]))
    }

    func testThisBuildIsBlockingByDefault() {
        // Les tests tournent sur un build Debug compilé avec la valeur par défaut de project.yml.
        XCTAssertTrue(LocationBlockingSwitch.current)
    }

    // MARK: - Du système à l'app

    func testTheSystemStatesAreTranslatedWithoutEverTakingAnUnknownOneForAlways() {
        XCTAssertEqual(SystemPermissionMapping.location(.notDetermined), .notDetermined)
        XCTAssertEqual(SystemPermissionMapping.location(.restricted), .restricted)
        XCTAssertEqual(SystemPermissionMapping.location(.denied), .denied)
        XCTAssertEqual(SystemPermissionMapping.location(.authorizedWhenInUse), .whenInUse)
        XCTAssertEqual(SystemPermissionMapping.location(.authorizedAlways), .always)
        XCTAssertEqual(SystemPermissionMapping.notifications(.notDetermined), .notDetermined)
        XCTAssertEqual(SystemPermissionMapping.notifications(.denied), .denied)
        XCTAssertEqual(SystemPermissionMapping.notifications(.authorized), .authorized)
        XCTAssertEqual(SystemPermissionMapping.notifications(.provisional), .authorized)
        XCTAssertEqual(SystemPermissionMapping.notifications(.ephemeral), .authorized)
    }

    func testTheFirstStatusWritesWhatThePermissionReallyIs() {
        XCTAssertEqual(SystemPermissionMapping.locationAccess(location: .notDetermined, precise: true), .unknown)
        XCTAssertEqual(SystemPermissionMapping.locationAccess(location: .denied, precise: true), .denied)
        XCTAssertEqual(SystemPermissionMapping.locationAccess(location: .restricted, precise: true), .denied)
        XCTAssertEqual(SystemPermissionMapping.locationAccess(location: .whenInUse, precise: true), .foreground)
        XCTAssertEqual(SystemPermissionMapping.locationAccess(location: .always, precise: true), .background)
        // « Toujours » avec la position approximative seule n'est pas « background » (valeur du ticket Lieux §6.7).
        XCTAssertEqual(SystemPermissionMapping.locationAccess(location: .always, precise: false), .foreground)
    }
}
