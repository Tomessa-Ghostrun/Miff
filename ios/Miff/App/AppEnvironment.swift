import Foundation

/// Conteneur de dépendances écrit à la main (injection par initialiseur, pas de bibliothèque d'injection).
/// Créé une fois par `MiffApp`.
final class AppEnvironment {
    let privacySettings: PrivacySettings
    let analyticsTracker: AnalyticsTracker

    init(privacySettings: PrivacySettings, analyticsTracker: AnalyticsTracker) {
        self.privacySettings = privacySettings
        self.analyticsTracker = analyticsTracker
    }

    /// Environnement réel : choix de confidentialité stockés dans UserDefaults, SDK Firebase derrière.
    static func live() -> AppEnvironment {
        let privacy = PrivacySettings(
            store: UserDefaultsBooleanStore(),
            analytics: FirebaseAnalyticsSwitch(),
            crashReports: FirebaseCrashlyticsSwitch()
        )
        let tracker = AnalyticsTracker(sink: FirebaseAnalyticsSink(), isEnabled: { privacy.analyticsEnabled })
        return AppEnvironment(privacySettings: privacy, analyticsTracker: tracker)
    }
}
