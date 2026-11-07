import FirebaseAnalytics
import FirebaseCrashlytics
import Foundation

// Branchement de la logique de l'app sur les SDK Firebase. Tout appel est protégé par `FirebaseBootstrap.isConfigured` :
// sans configuration Firebase (poste sans GoogleService-Info.plist, tests unitaires), on ne touche pas aux SDK
// (ils lèveraient une exception).

/// Collecte Analytics. SDK utilisé : `FirebaseAnalyticsCore` (aucun identifiant publicitaire).
struct FirebaseAnalyticsSwitch: CollectionSwitch {
    func setEnabled(_ enabled: Bool) {
        guard FirebaseBootstrap.isConfigured else {
            return
        }
        Analytics.setAnalyticsCollectionEnabled(enabled)
    }

    func pause() {
        setEnabled(false)
    }
}

struct FirebaseCrashlyticsSwitch: CollectionSwitch {
    func setEnabled(_ enabled: Bool) {
        guard FirebaseBootstrap.isConfigured else {
            return
        }
        let crashlytics = Crashlytics.crashlytics()
        crashlytics.setCrashlyticsCollectionEnabled(enabled)
        // Sans accord, un rapport resté sur l'appareil ne doit jamais partir plus tard par surprise.
        if !enabled {
            crashlytics.deleteUnsentReports()
        }
    }

    /// Collecte coupée, mais le rapport du plantage précédent reste sur le disque jusqu'à la réponse du compte.
    func pause() {
        guard FirebaseBootstrap.isConfigured else {
            return
        }
        Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(false)
    }
}

/// Remet à Firebase Analytics les événements déjà validés. Uniquement utilisé via `AnalyticsTracker`.
struct FirebaseAnalyticsSink: AnalyticsSink {
    func log(eventName: String, params: [String: Any]) {
        guard FirebaseBootstrap.isConfigured else {
            return
        }
        var parameters: [String: Any] = [:]
        for (key, value) in params {
            switch value {
            case let text as String:
                parameters[key] = text
            case let flag as Bool:
                // Le contrat n'a que des chaînes, entiers et booléens ; Analytics n'a pas de booléen : 1 ou 0.
                parameters[key] = flag ? 1 : 0
            case let number as Int:
                parameters[key] = number
            default:
                break
            }
        }
        Analytics.logEvent(eventName, parameters: parameters)
    }
}
