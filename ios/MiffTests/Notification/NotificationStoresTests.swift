import XCTest
@testable import Miff

/// Les choix, le centre et la carte « deuxième chance » : ce qui est gardé sur l'appareil, pour qui, et ce qui l'efface.
@MainActor
final class NotificationStoresTests: XCTestCase {
    private let now = 1_800_000_000_000
    private var directory = URL(fileURLWithPath: NSTemporaryDirectory())

    override func setUp() {
        super.setUp()
        directory = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("notif-\(UUID().uuidString)", isDirectory: true)
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: directory)
        super.tearDown()
    }

    private func suite() -> UserDefaults {
        let name = "notif-prefs-\(UUID().uuidString)"
        guard let defaults = UserDefaults(suiteName: name) else {
            XCTFail("suite introuvable")
            return .standard
        }
        defaults.removePersistentDomain(forName: name)
        return defaults
    }

    // MARK: - Les choix

    func testEverythingIsOnByDefault() {
        let preferences = NotificationPreferences()

        XCTAssertTrue(preferences.general)
        XCTAssertTrue(preferences.nightCalm)
        for type in NotificationType.allCases {
            XCTAssertTrue(preferences.typeValue(type), "\(type)")
            XCTAssertTrue(preferences.isEnabled(type), "\(type)")
        }
    }

    func testTurningTheGeneralSwitchOffKeepsTheValuesOfTheTypes() {
        let preferences = NotificationPreferences().setting(.n2, to: false).settingGeneral(false)

        XCTAssertFalse(preferences.isEnabled(.n1), "l'interrupteur général coupe tout")
        XCTAssertTrue(preferences.typeValue(.n1), "mais la valeur du type est gardée (grisée)")
        XCTAssertFalse(preferences.typeValue(.n2))
        let back = preferences.settingGeneral(true)
        XCTAssertTrue(back.isEnabled(.n1))
        XCTAssertFalse(back.isEnabled(.n2), "la réactivation redonne les choix d'avant")
    }

    func testEachTypeIsIndependent() {
        for type in NotificationType.allCases {
            let preferences = NotificationPreferences().setting(type, to: false)

            XCTAssertFalse(preferences.isEnabled(type))
            for other in NotificationType.allCases where other != type {
                XCTAssertTrue(preferences.isEnabled(other), "\(other) ne doit pas suivre \(type)")
            }
            XCTAssertEqual(preferences.setting(type, to: true), NotificationPreferences())
        }
    }

    func testThereIsNoSwitchForTheAbandonedNotificationFive() {
        XCTAssertEqual(NotificationType.allCases.map { $0.rawValue }, ["n1", "n2", "n3", "n4", "n6", "n7"])
    }

    func testTheChoicesAreKeptPerAccountAndForgottenForAll() {
        let defaults = suite()
        let store = UserDefaultsNotificationPreferencesStore(defaults: defaults)
        store.save(NotificationPreferences().setting(.n3, to: false), uid: "uid-1")
        store.save(NotificationPreferences().settingNightCalm(false), uid: "uid-2")

        XCTAssertFalse(store.load(uid: "uid-1").typeValue(.n3))
        XCTAssertTrue(store.load(uid: "uid-1").nightCalm)
        XCTAssertFalse(store.load(uid: "uid-2").nightCalm)
        XCTAssertEqual(store.load(uid: "uid-3"), NotificationPreferences(), "un compte sans choix : tout actif")

        store.forgetAll()

        XCTAssertEqual(store.load(uid: "uid-1"), NotificationPreferences())
        XCTAssertEqual(store.load(uid: "uid-2"), NotificationPreferences())
    }

    func testForgettingTheChoicesLeavesTheOtherSettingsOfTheAppAlone() {
        let defaults = suite()
        defaults.set(true, forKey: "notification_screen_shown.uid-1")
        let store = UserDefaultsNotificationPreferencesStore(defaults: defaults)
        store.save(NotificationPreferences(), uid: "uid-1")

        store.forgetAll()

        XCTAssertTrue(defaults.bool(forKey: "notification_screen_shown.uid-1"))
    }

    func testAnUnreadableValueGivesTheDefaultsNeverAnError() {
        let defaults = suite()
        defaults.set(Data("pas du json".utf8), forKey: "notification_preferences.uid-1")

        let store = UserDefaultsNotificationPreferencesStore(defaults: defaults)
        XCTAssertEqual(store.load(uid: "uid-1"), NotificationPreferences())
    }

    func testTheServiceKeepsEachChangeAtOnceAndTellsTheListener() {
        let store = MemoryNotificationPreferencesStore()
        var uid: String? = "uid-1"
        let service = NotificationPreferencesService(store: store, currentUid: { uid })
        var heard: [NotificationPreferences] = []
        service.onChange = { heard.append($0) }

        service.update { $0.setting(.n4, to: false) }
        service.update { $0.setting(.n4, to: false) }
        service.update { $0.settingGeneral(false) }

        XCTAssertEqual(heard.count, 2, "un changement sans effet n'est pas annoncé")
        XCTAssertEqual(store.load(uid: "uid-1"), service.preferences)
        XCTAssertFalse(service.preferences.general)

        uid = nil
        service.update { $0.settingGeneral(true) }
        XCTAssertEqual(heard.count, 2, "sans session, rien n'est gardé")
        service.forget()
        XCTAssertEqual(service.preferences, NotificationPreferences())
        XCTAssertEqual(store.forgetCalls, 1)
    }

    // MARK: - La carte « deuxième chance »

    func testTheSecondChanceStateIsKeptPerAccountAndForgotten() {
        let store = UserDefaultsNotificationSecondChanceStore(defaults: suite())
        store.save(SecondChanceState(firstUpdateSeen: true, answered: false), uid: "uid-1")
        store.save(SecondChanceState(firstUpdateSeen: true, answered: true), uid: "uid-2")

        XCTAssertEqual(store.state(uid: "uid-1"), SecondChanceState(firstUpdateSeen: true, answered: false))
        XCTAssertEqual(store.state(uid: "uid-3"), SecondChanceState())

        store.forget(uid: "uid-1")

        XCTAssertEqual(store.state(uid: "uid-1"), SecondChanceState())
        XCTAssertTrue(store.state(uid: "uid-2").answered)
    }

    // MARK: - Le fichier du centre

    func testTheCenterFileRoundTripsAndBelongsToOneAccount() {
        let store = NotificationCenterFileStore(directory: directory)
        let items = [
            NotificationFixtures.item("n1|a", at: now),
            NotificationFixtures.item("n3|b", type: .n3, at: now - 1)
        ]

        store.save(items, uid: "uid-1")

        XCTAssertEqual(store.load(uid: "uid-1"), items)
        let relaunched = NotificationCenterFileStore(directory: directory)
        XCTAssertEqual(relaunched.load(uid: "uid-1"), items, "survit à un processus tué")
        XCTAssertEqual(store.load(uid: "uid-2"), [], "le fichier d'un autre compte n'est jamais lu")
    }

    func testTheCenterFileIsExcludedFromBackupsAndClearedOnDemand() throws {
        let store = NotificationCenterFileStore(directory: directory)
        store.save([NotificationFixtures.item("n1|a", at: now)], uid: "uid-1")

        let values = try directory.resourceValues(forKeys: [.isExcludedFromBackupKey])
        XCTAssertEqual(values.isExcludedFromBackup, true)

        store.clear()

        XCTAssertFalse(FileManager.default.fileExists(atPath: directory.path))
        XCTAssertEqual(store.load(uid: "uid-1"), [])
    }

    func testAnUnreadableCenterFileIsNotBelievedAndGivesAnEmptyList() throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try Data("{ pas du json".utf8).write(to: directory.appendingPathComponent("notification-center.json"))

        XCTAssertEqual(NotificationCenterFileStore(directory: directory).load(uid: "uid-1"), [])
    }

    func testTheCenterFileHoldsNoPositionNoAddressNoPlaceNameNoPhoto() throws {
        let store = NotificationCenterFileStore(directory: directory)
        store.save([NotificationFixtures.item("n6|miff-1|place-1|1", type: .n6, at: now)], uid: "uid-1")

        let text = try String(contentsOf: directory.appendingPathComponent("notification-center.json"), encoding: .utf8)
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(text.utf8)) as? [String: Any])
        let items = try XCTUnwrap(root["items"] as? [[String: Any]])

        XCTAssertEqual(Set(items[0].keys), ["id", "type", "miffId", "createdAtMillis", "state"])
        for forbidden in ["lat", "lng", "address", "adresse", "placeName", "photo", "jpeg", "phone"] {
            XCTAssertFalse(text.lowercased().contains(forbidden.lowercased()), forbidden)
        }
    }
}
