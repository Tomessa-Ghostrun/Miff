import XCTest
@testable import Miff

/// Réglages > Notifications (ticket §9 et §20.11) : l'état du téléphone, l'interrupteur général, les cinq interrupteurs de type, la nuit
/// calme. Un interrupteur de l'app ne contourne jamais un refus du téléphone.
@MainActor
final class NotificationSettingsViewModelTests: XCTestCase {
    private struct Rig {
        let model: NotificationSettingsViewModel
        let permissions: NotificationStubPermissions
        let service: NotificationPreferencesService
        let store: MemoryNotificationPreferencesStore
    }

    private func makeRig(_ authorization: NotificationAuthorization) -> Rig {
        let permissions = NotificationStubPermissions(authorization)
        let store = MemoryNotificationPreferencesStore()
        let service = NotificationPreferencesService(store: store, currentUid: { "uid-1" })
        let model = NotificationSettingsViewModel(permissions: permissions, service: service)
        return Rig(model: model, permissions: permissions, service: service, store: store)
    }

    // MARK: - Ce que montre l'interrupteur général

    func testBeforeTheStateIsReadNothingIsOnAndNothingCanBeTouched() {
        let rig = makeRig(.authorized)

        XCTAssertNil(rig.model.authorization)
        XCTAssertFalse(rig.model.generalIsOn)
        XCTAssertFalse(rig.model.generalIsEnabled)
        XCTAssertFalse(rig.model.typesAreEnabled)
    }

    func testAllowedByThePhoneTheGeneralSwitchIsOnAndTheTypesCanBeTouched() async {
        let rig = makeRig(.authorized)

        await rig.model.refresh()

        XCTAssertTrue(rig.model.generalIsOn)
        XCTAssertTrue(rig.model.generalIsEnabled)
        XCTAssertTrue(rig.model.typesAreEnabled)
    }

    func testTurnedOffInThePhoneTheSwitchIsGreyedAndNeverPretendsToBeOn() async {
        let rig = makeRig(.denied)
        await rig.model.refresh()

        XCTAssertFalse(rig.model.generalIsOn, "malgré le choix par défaut « tout actif »")
        XCTAssertFalse(rig.model.generalIsEnabled)
        XCTAssertFalse(rig.model.typesAreEnabled)
        rig.model.setGeneral(true)
        XCTAssertFalse(rig.model.primeVisible, "pas de fenêtre à montrer : seul iOS peut les rallumer")
        XCTAssertEqual(rig.permissions.notificationRequests, 0)
        XCTAssertFalse(rig.model.generalIsOn)
    }

    func testNeverAskedTheSwitchIsOffAndTurningItOnOpensThePrimeBeforeTheWindow() async {
        let rig = makeRig(.notDetermined)
        await rig.model.refresh()
        XCTAssertFalse(rig.model.generalIsOn)
        XCTAssertTrue(rig.model.generalIsEnabled)

        rig.model.setGeneral(true)

        XCTAssertTrue(rig.model.primeVisible)
        XCTAssertEqual(rig.permissions.notificationRequests, 0, "l'amorce explique AVANT la fenêtre du système")
        XCTAssertFalse(rig.model.generalIsOn, "toujours éteint tant que le téléphone n'autorise rien")
    }

    func testAllowingOnThePrimeShowsTheWindowAndTurnsEverythingOn() async {
        let rig = makeRig(.notDetermined)
        await rig.model.refresh()
        rig.model.setGeneral(true)

        await rig.model.primeAllowed()

        XCTAssertFalse(rig.model.primeVisible)
        XCTAssertEqual(rig.permissions.notificationRequests, 1)
        XCTAssertTrue(rig.model.generalIsOn)
    }

    func testRefusingInTheWindowLeavesTheSwitchOffAndSaysSo() async {
        let rig = makeRig(.notDetermined)
        rig.permissions.afterWindow = .denied
        await rig.model.refresh()
        rig.model.setGeneral(true)

        await rig.model.primeAllowed()

        XCTAssertFalse(rig.model.generalIsOn)
        XCTAssertEqual(rig.model.authorization, .denied)
    }

    func testLaterOnThePrimeKeepsTheWindowForAnotherTime() async {
        let rig = makeRig(.notDetermined)
        await rig.model.refresh()
        rig.model.setGeneral(true)

        rig.model.primeDeferred()

        XCTAssertFalse(rig.model.primeVisible)
        XCTAssertEqual(rig.permissions.notificationRequests, 0)
        XCTAssertEqual(rig.model.authorization, .notDetermined)
    }

    // MARK: - Les choix

    func testTurningTheGeneralSwitchOffIsImmediateKeptAndKeepsTheTypes() async {
        let rig = makeRig(.authorized)
        await rig.model.refresh()
        rig.model.setType(.n2, isOn: false)
        var heard: [NotificationPreferences] = []
        rig.service.onChange = { heard.append($0) }

        rig.model.setGeneral(false)

        XCTAssertFalse(rig.model.generalIsOn)
        XCTAssertFalse(rig.model.typesAreEnabled, "les interrupteurs de type sont grisés")
        XCTAssertEqual(heard.count, 1, "le planificateur est prévenu dans la seconde")
        XCTAssertFalse(heard[0].general)
        XCTAssertFalse(rig.model.preferences.typeValue(.n2), "la valeur du type est gardée")
        XCTAssertEqual(rig.store.load(uid: "uid-1").general, false)
    }

    func testEachTypeSwitchIsKeptAndTheOthersDoNotMove() async {
        let rig = makeRig(.authorized)
        await rig.model.refresh()

        for type in NotificationType.allCases {
            rig.model.setType(type, isOn: false)
            XCTAssertFalse(rig.model.preferences.typeValue(type))
            XCTAssertEqual(rig.model.preferences.disabledTypes, [type])
            rig.model.setType(type, isOn: true)
            XCTAssertTrue(rig.model.preferences.disabledTypes.isEmpty)
        }
    }

    func testTheNightCalmSwitchIsKept() async {
        let rig = makeRig(.authorized)
        await rig.model.refresh()

        rig.model.setNightCalm(false)

        XCTAssertFalse(rig.model.preferences.nightCalm)
        XCTAssertFalse(rig.store.load(uid: "uid-1").nightCalm)
    }

    func testReturningFromThePhoneSettingsReadsTheStateAgain() async {
        let rig = makeRig(.denied)
        await rig.model.refresh()
        XCTAssertFalse(rig.model.generalIsOn)

        rig.permissions.notifications = .authorized
        await rig.model.refresh()

        XCTAssertTrue(rig.model.generalIsOn)
    }

    func testOpeningThePhoneSettingsGoesThroughThePermissions() {
        let rig = makeRig(.denied)

        rig.model.openPhoneSettings()

        XCTAssertEqual(rig.permissions.settingsOpened, 1)
    }
}
