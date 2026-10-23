import XCTest
@testable import Miff

/// Consentement : tout est éteint par défaut, le choix est appliqué aux SDK et conservé après un redémarrage.
final class PrivacySettingsTests: XCTestCase {
    private final class MemoryStore: BooleanStore {
        var values: [String: Bool] = [:]

        func value(forKey key: String, default defaultValue: Bool) -> Bool {
            values[key] ?? defaultValue
        }

        func set(_ value: Bool, forKey key: String) {
            values[key] = value
        }
    }

    private final class RecordingSwitch: CollectionSwitch {
        private(set) var calls: [Bool] = []

        func setEnabled(_ enabled: Bool) {
            calls.append(enabled)
        }
    }

    func testEverythingIsOffByDefault() {
        let analytics = RecordingSwitch()
        let crash = RecordingSwitch()
        let settings = PrivacySettings(store: MemoryStore(), analytics: analytics, crashReports: crash)

        XCTAssertFalse(settings.analyticsEnabled)
        XCTAssertFalse(settings.crashReportsEnabled)

        settings.applyStoredChoices()
        XCTAssertEqual(analytics.calls, [false])
        XCTAssertEqual(crash.calls, [false])
    }

    func testChoicesAreAppliedToTheSDKsAndPersisted() {
        let store = MemoryStore()
        let analytics = RecordingSwitch()
        let crash = RecordingSwitch()
        let settings = PrivacySettings(store: store, analytics: analytics, crashReports: crash)

        settings.setAnalyticsEnabled(true)
        settings.setCrashReportsEnabled(true)
        XCTAssertTrue(settings.analyticsEnabled)
        XCTAssertTrue(settings.crashReportsEnabled)
        XCTAssertEqual(analytics.calls, [true])
        XCTAssertEqual(crash.calls, [true])

        // « Redémarrage » : une nouvelle instance relit les choix enregistrés et les transmet de nouveau aux SDK.
        let analyticsAfterRestart = RecordingSwitch()
        let crashAfterRestart = RecordingSwitch()
        let restarted = PrivacySettings(store: store, analytics: analyticsAfterRestart, crashReports: crashAfterRestart)
        XCTAssertTrue(restarted.analyticsEnabled)
        restarted.applyStoredChoices()
        XCTAssertEqual(analyticsAfterRestart.calls, [true])
        XCTAssertEqual(crashAfterRestart.calls, [true])
    }

    func testSwitchingOffIsAppliedAndPersisted() {
        let store = MemoryStore()
        let analytics = RecordingSwitch()
        let settings = PrivacySettings(store: store, analytics: analytics, crashReports: RecordingSwitch())

        settings.setAnalyticsEnabled(true)
        settings.setAnalyticsEnabled(false)

        XCTAssertFalse(settings.analyticsEnabled)
        XCTAssertEqual(analytics.calls, [true, false])
        let restarted = PrivacySettings(store: store, analytics: RecordingSwitch(), crashReports: RecordingSwitch())
        XCTAssertFalse(restarted.analyticsEnabled)
    }

    func testAnalyticsAndCrashReportsAreIndependent() {
        let settings = PrivacySettings(
            store: MemoryStore(), analytics: RecordingSwitch(), crashReports: RecordingSwitch())

        settings.setCrashReportsEnabled(true)

        XCTAssertFalse(settings.analyticsEnabled)
        XCTAssertTrue(settings.crashReportsEnabled)
    }

    func testUserDefaultsStoreDefaultsToOffAndPersists() throws {
        let suiteName = "miff.tests.\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suiteName))
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let store = UserDefaultsBooleanStore(defaults: defaults)

        XCTAssertFalse(store.value(forKey: PrivacySettings.analyticsKey, default: false))

        store.set(true, forKey: PrivacySettings.analyticsKey)
        XCTAssertTrue(store.value(forKey: PrivacySettings.analyticsKey, default: false))
    }
}
