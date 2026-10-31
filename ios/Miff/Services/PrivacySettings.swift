import Combine
import Foundation

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

/// Choix de confidentialité pour Analytics et les rapports de plantage. Les deux sont ÉTEINTS par défaut, et
/// l'`Info.plist` les désactive aussi au démarrage : rien n'est collecté avant un accord.
///
/// Le choix qui compte est celui du COMPTE (`users/{uid}.analyticsConsent`), appliqué par `SessionManager` une fois lu.
/// La valeur gardée sur l'appareil n'est volontairement PAS réappliquée au démarrage à froid : au lancement la collecte
/// est seulement mise EN PAUSE (`pauseCollection`), et rien ne se rallume avant la réponse du compte. L'écran de
/// diagnostic (Debug et Internal) reste un second moyen d'allumer ces collectes pendant une session.
final class PrivacySettings: ObservableObject, CollectionConsentApplying {
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

    /// Démarrage à froid, et chaque fois que le compte est (re)lu : ne rien collecter, mais garder sur le disque le
    /// rapport du plantage précédent (voir `CollectionSwitch.pause`) ; la réponse du compte l'enverra ou l'effacera.
    func pauseCollection() {
        store.set(false, forKey: Self.analyticsKey)
        store.set(false, forKey: Self.crashReportsKey)
        analyticsEnabled = false
        crashReportsEnabled = false
        analytics.pause()
        crashReports.pause()
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

    /// L'interrupteur unique du consentement « statistiques et rapports de plantage » (un seul choix, deux SDK) :
    /// applique le choix du COMPTE (lu dans `users/{uid}.analyticsConsent`, ou donné par le geste à l'inscription) aux
    /// deux collectes à la fois. `false` coupe les deux ET efface les rapports pas encore envoyés : c'est le retrait du
    /// consentement, la déconnexion et le refus. Pour « pas encore connu », utiliser `pauseCollection`, qui n'efface
    /// rien.
    func applyAccountConsent(_ enabled: Bool) {
        setAnalyticsEnabled(enabled)
        setCrashReportsEnabled(enabled)
    }
}
