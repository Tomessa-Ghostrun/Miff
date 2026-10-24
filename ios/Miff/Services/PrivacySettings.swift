import Combine
import Foundation

/// Petit magasin de booléens (UserDefaults dans l'app, mémoire dans les tests). Données non sensibles.
protocol BooleanStore {
    func value(forKey key: String, default defaultValue: Bool) -> Bool
    func set(_ value: Bool, forKey key: String)
}

/// Interrupteur de collecte d'un SDK (Analytics, Crashlytics).
protocol CollectionSwitch {
    func setEnabled(_ enabled: Bool)
}

struct UserDefaultsBooleanStore: BooleanStore {
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func value(forKey key: String, default defaultValue: Bool) -> Bool {
        defaults.object(forKey: key) as? Bool ?? defaultValue
    }

    func set(_ value: Bool, forKey key: String) {
        defaults.set(value, forKey: key)
    }
}

/// Choix de l'utilisateur pour Analytics et les rapports de plantage. Les deux sont ÉTEINTS par défaut, et
/// l'`Info.plist` les désactive aussi au démarrage : rien n'est collecté avant un accord explicite
/// (le vrai écran de consentement viendra avec l'onboarding ; pour l'instant, seul le diagnostic interne peut
/// les allumer).
///
/// Le choix est conservé sur l'appareil et réappliqué à chaque lancement, car l'`Info.plist` démarre toujours
/// « désactivé ».
final class PrivacySettings: ObservableObject {
    static let analyticsKey = "analytics_enabled"
    static let crashReportsKey = "crash_reports_enabled"

    @Published private(set) var analyticsEnabled: Bool
    @Published private(set) var crashReportsEnabled: Bool

    private let store: BooleanStore
    private let analytics: CollectionSwitch
    private let crashReports: CollectionSwitch

    init(store: BooleanStore, analytics: CollectionSwitch, crashReports: CollectionSwitch) {
        self.store = store
        self.analytics = analytics
        self.crashReports = crashReports
        analyticsEnabled = store.value(forKey: Self.analyticsKey, default: false)
        crashReportsEnabled = store.value(forKey: Self.crashReportsKey, default: false)
    }

    /// Appelé une fois au lancement : transmet les choix enregistrés aux SDK.
    func applyStoredChoices() {
        analytics.setEnabled(analyticsEnabled)
        crashReports.setEnabled(crashReportsEnabled)
    }

    func setAnalyticsEnabled(_ enabled: Bool) {
        store.set(enabled, forKey: Self.analyticsKey)
        analyticsEnabled = enabled
        analytics.setEnabled(enabled)
    }

    func setCrashReportsEnabled(_ enabled: Bool) {
        store.set(enabled, forKey: Self.crashReportsKey)
        crashReportsEnabled = enabled
        crashReports.setEnabled(enabled)
    }
}
