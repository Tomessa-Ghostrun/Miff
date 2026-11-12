import UserNotifications
import XCTest
@testable import Miff

/// SRV-2I / SRV-4I : les petits réglages (accord, interrupteur), la mémoire de l'enregistrement, la demande d'ouverture et le délégué de
/// réception. Tout est local : un dossier temporaire, des réglages à part, aucun système.
@MainActor
final class PushStoresAndDelegateTests: XCTestCase {
    /// Des réglages à part pour chaque essai, effacés ensuite.
    private func makeDefaults() -> UserDefaults {
        let name = "push-tests-(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name) ?? .standard
        addTeardownBlock {
            defaults.removePersistentDomain(forName: name)
        }
        return defaults
    }

    /// Un dossier temporaire neuf pour chaque essai, effacé ensuite.
    private func makeDirectory() -> URL {
        let directory = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("push-(UUID().uuidString)", isDirectory: true)
        addTeardownBlock {
            try? FileManager.default.removeItem(at: directory)
        }
        return directory
    }

    // MARK: - L'accord et l'interrupteur

    func testTheConsentIsPerAccountAndGoneWhenForgotten() {
        let store = UserDefaultsPushConsentStore(defaults: makeDefaults())
        XCTAssertFalse(store.isGiven(uid: "a"))

        store.give(uid: "a")

        XCTAssertTrue(store.isGiven(uid: "a"))
        XCTAssertFalse(store.isGiven(uid: "b"))
        store.forget(uid: "a")
        XCTAssertFalse(store.isGiven(uid: "a"))
    }

    func testTheSwitchIsOnByDefaultPerAccountAndRemembered() {
        let store = UserDefaultsCircleNewsSwitchStore(defaults: makeDefaults())
        XCTAssertTrue(store.isOn(uid: "a"), "actif par défaut")

        store.set(false, uid: "a")

        XCTAssertFalse(store.isOn(uid: "a"))
        XCTAssertTrue(store.isOn(uid: "b"))
        store.forget(uid: "a")
        XCTAssertTrue(store.isOn(uid: "a"), "oublié : retour à actif")
    }

    func testChangingTheSwitchIsKeptAtOnceAndAnnouncedOnlyWhenTheValueChanges() {
        let store = UserDefaultsCircleNewsSwitchStore(defaults: makeDefaults())
        var uid: String? = "a"
        let model = CircleNewsSwitch(store: store, currentUid: { uid })
        var announced = 0
        model.onChange = { announced += 1 }
        XCTAssertTrue(model.isOn)

        model.set(true)
        XCTAssertEqual(announced, 0, "pas de changement, pas d'écriture")
        model.set(false)
        XCTAssertFalse(model.isOn)
        XCTAssertFalse(store.isOn(uid: "a"))
        XCTAssertEqual(announced, 1)

        uid = nil
        model.set(true)
        XCTAssertEqual(announced, 1, "personne de connecté : rien")
        model.reload()
        XCTAssertTrue(model.isOn)
    }

    // MARK: - La mémoire de l'enregistrement

    func testTheRegistrationMemoryKeepsTheFingerprintNeverTheToken() throws {
        let directory = makeDirectory()
        let store = PushLocalFileStore(directory: directory)
        let record = RegistrationRecord(
            uid: "uid-1", deviceId: "device-AAAAAAAAAAAAAAAA",
            tokenFingerprint: TokenFingerprint.of("the-secret-token"),
            timeZoneId: "Europe/Paris", notifyUpdates: true, writtenAtMillis: 42
        )

        store.saveRecord(record)
        store.setServiceOn(true)
        store.setOthersClearedFor("device-AAAAAAAAAAAAAAAA")

        let relaunched = PushLocalFileStore(directory: directory)
        XCTAssertEqual(relaunched.record(), record)
        XCTAssertTrue(relaunched.serviceOn())
        XCTAssertEqual(relaunched.othersClearedFor(), "device-AAAAAAAAAAAAAAAA")
        let text = try String(contentsOf: directory.appendingPathComponent("push-registration.json"), encoding: .utf8)
        XCTAssertFalse(text.contains("the-secret-token"))

        relaunched.clear()
        XCTAssertNil(PushLocalFileStore(directory: directory).record())
        XCTAssertFalse(PushLocalFileStore(directory: directory).serviceOn())
    }

    func testAnUnreadableMemoryReadsAsNothingRemembered() throws {
        let directory = makeDirectory()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try Data("pas du json".utf8).write(to: directory.appendingPathComponent("push-registration.json"))

        let store = PushLocalFileStore(directory: directory)

        XCTAssertNil(store.record())
        XCTAssertFalse(store.serviceOn())
        XCTAssertNil(store.othersClearedFor())
    }

    // MARK: - La demande d'ouverture

    func testOneRequestAtATimeAndItCanBeCleared() {
        let requests = PushOpenRequests()
        XCTAssertNil(requests.pending)
        let first = CircleNewsTarget(miffId: "m1", authorUid: "u1")
        let second = CircleNewsTarget(miffId: "m2", authorUid: "u2")

        requests.request(first)
        requests.request(second)

        XCTAssertEqual(requests.pending, second)
        requests.clear()
        XCTAssertNil(requests.pending)
    }

    // MARK: - Le délégué de réception

    private final class Forwarded: NSObject, UNUserNotificationCenterDelegate {
        private(set) var willPresentCalls = 0

        func userNotificationCenter(
            _ center: UNUserNotificationCenter,
            willPresent notification: UNNotification,
            withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
        ) {
            willPresentCalls += 1
            completionHandler([.banner])
        }
    }

    func testTheDelegateSortsMessagesOnTheSameRuleAsTheParser() {
        let good: [AnyHashable: Any] = ["type": "circle_update", "miffId": "m1", "authorUid": "u1"]
        let local: [AnyHashable: Any] = ["type": "n1", "key": "k", "miffId": "m1"]
        let forged: [AnyHashable: Any] = ["type": "circle_update", "miffId": "https://example.com", "authorUid": "u1"]

        XCTAssertNotNil(CircleNewsParser.parse(good))
        XCTAssertNil(CircleNewsParser.parse(local), "un rappel local passe à l'autre délégué")
        XCTAssertNil(CircleNewsParser.parse(forged), "une valeur forgée n'ouvre rien")
    }

    func testTheDelegateKeepsAWeakLinkToTheNextDelegate() {
        let delegate = PushNotificationDelegate()
        var other: Forwarded? = Forwarded()
        delegate.next = other

        XCTAssertNotNil(delegate.next)
        other = nil
        XCTAssertNil(delegate.next, "le délégué suivant appartient à l'app, pas à celui-ci")
    }
}
