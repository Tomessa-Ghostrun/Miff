import XCTest
@testable import Miff

/// Consentement : tout est éteint par défaut ; au démarrage la collecte est mise EN PAUSE (rien n'est effacé) et le choix
/// gardé sur l'appareil n'est jamais réappliqué tel quel ; le choix du compte s'applique aux deux collectes d'un coup, et
/// son retrait efface ce qui n'est pas encore parti.
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
        private(set) var log: [String] = []

        func setEnabled(_ enabled: Bool) {
            log.append(enabled ? "on" : "off")
        }

        func pause() {
            log.append("pause")
        }
    }

    func testEverythingIsOffByDefault() {
        let settings = PrivacySettings(
            store: MemoryStore(), analytics: RecordingSwitch(), crashReports: RecordingSwitch())

        XCTAssertFalse(settings.analyticsEnabled)
        XCTAssertFalse(settings.crashReportsEnabled)
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
        XCTAssertEqual(analytics.log, ["on"])
        XCTAssertEqual(crash.log, ["on"])
        XCTAssertEqual(store.value(forKey: PrivacySettings.analyticsKey, default: false), true)
    }

    func testSwitchingOffIsAppliedAndPersisted() {
        let store = MemoryStore()
        let analytics = RecordingSwitch()
        let settings = PrivacySettings(store: store, analytics: analytics, crashReports: RecordingSwitch())

        settings.setAnalyticsEnabled(true)
        settings.setAnalyticsEnabled(false)

        XCTAssertFalse(settings.analyticsEnabled)
        XCTAssertEqual(analytics.log, ["on", "off"])
        XCTAssertEqual(store.value(forKey: PrivacySettings.analyticsKey, default: true), false)
    }

    func testAnalyticsAndCrashReportsAreIndependentForTheDiagnostic() {
        let settings = PrivacySettings(
            store: MemoryStore(), analytics: RecordingSwitch(), crashReports: RecordingSwitch())

        settings.setCrashReportsEnabled(true)

        XCTAssertFalse(settings.analyticsEnabled)
        XCTAssertTrue(settings.crashReportsEnabled)
    }

    func testPauseTurnsCollectionOffWithoutDeletingAnything() {
        let analytics = RecordingSwitch()
        let crash = RecordingSwitch()
        let settings = PrivacySettings(store: MemoryStore(), analytics: analytics, crashReports: crash)
        settings.setAnalyticsEnabled(true)
        settings.setCrashReportsEnabled(true)

        settings.pauseCollection()

        XCTAssertFalse(settings.analyticsEnabled)
        XCTAssertFalse(settings.crashReportsEnabled)
        // « pause » et non « off » : le rapport du plantage précédent reste sur le disque jusqu'à la réponse du compte.
        XCTAssertEqual(analytics.log, ["on", "pause"])
        XCTAssertEqual(crash.log, ["on", "pause"])
    }

    func testTheStoredChoiceIsNotReappliedAtAColdStart() {
        let store = MemoryStore()
        PrivacySettings(store: store, analytics: RecordingSwitch(), crashReports: RecordingSwitch())
            .setAnalyticsEnabled(true)

        // « Redémarrage » : une nouvelle instance relit le magasin, puis le démarrage met la collecte en pause.
        let analytics = RecordingSwitch()
        let restarted = PrivacySettings(store: store, analytics: analytics, crashReports: RecordingSwitch())
        restarted.pauseCollection()

        XCTAssertFalse(restarted.analyticsEnabled)
        XCTAssertEqual(analytics.log, ["pause"])
        XCTAssertEqual(store.value(forKey: PrivacySettings.analyticsKey, default: true), false)
    }

    func testTheAccountConsentIsOneSingleChoiceForBothCollections() {
        let analytics = RecordingSwitch()
        let crash = RecordingSwitch()
        let settings = PrivacySettings(store: MemoryStore(), analytics: analytics, crashReports: crash)

        settings.applyAccountConsent(true)
        XCTAssertTrue(settings.analyticsEnabled)
        XCTAssertTrue(settings.crashReportsEnabled)

        // Le retrait coupe les deux ET efface ce qui n'est pas encore parti (« off »).
        settings.applyAccountConsent(false)
        XCTAssertFalse(settings.analyticsEnabled)
        XCTAssertFalse(settings.crashReportsEnabled)
        XCTAssertEqual(analytics.log, ["on", "off"])
        XCTAssertEqual(crash.log, ["on", "off"])
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
