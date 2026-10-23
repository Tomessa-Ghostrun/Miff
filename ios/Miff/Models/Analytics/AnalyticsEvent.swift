import Foundation

/// Point d'entrée typé pour le code applicatif (contrat : docs/contracts/analytics-events.md). Les écrans et
/// les view models envoient ces événements via `AnalyticsTracker.track` ; ils n'appellent jamais le SDK
/// Analytics directement. Les valeurs viennent de listes fermées : une valeur erronée ne peut même pas s'écrire.
enum AnalyticsEvent: Equatable {
    enum Method: String {
        case email
        case google
        case apple
    }

    enum OnboardingVia: String {
        case created
        case joined
    }

    enum PaywallTrigger: String {
        case extraMiff = "extra_miff"
        case extraTrustedPlace = "extra_trusted_place"
    }

    /// `slideIndex` doit valoir 0, 1 ou 2 (toute autre valeur est refusée par la liste blanche).
    case onboardingCtaTapped(slideIndex: Int)
    case signUpCompleted(method: Method)
    case signInCompleted(method: Method)
    case miffCreated(isFirstMiff: Bool)
    case miffJoined(isFirstMiff: Bool)
    case onboardingCompleted(via: OnboardingVia)
    case paywallShown(trigger: PaywallTrigger)
    case paywallDismissed(trigger: PaywallTrigger)
    #if DEBUG || INTERNAL
    /// Bouton de test de l'écran de diagnostic.
    case diagnosticTest
    #endif

    var name: String {
        switch self {
        case .onboardingCtaTapped: return "onboarding_cta_tapped"
        case .signUpCompleted: return "sign_up_completed"
        case .signInCompleted: return "sign_in_completed"
        case .miffCreated: return "miff_created"
        case .miffJoined: return "miff_joined"
        case .onboardingCompleted: return "onboarding_completed"
        case .paywallShown: return "paywall_shown"
        case .paywallDismissed: return "paywall_dismissed"
        #if DEBUG || INTERNAL
        case .diagnosticTest: return AnalyticsContract.eventDiagnosticTest
        #endif
        }
    }

    var params: [String: Any] {
        switch self {
        case .onboardingCtaTapped(let slideIndex): return ["slide_index": slideIndex]
        case .signUpCompleted(let method): return ["method": method.rawValue]
        case .signInCompleted(let method): return ["method": method.rawValue]
        case .miffCreated(let isFirstMiff): return ["is_first_miff": isFirstMiff]
        case .miffJoined(let isFirstMiff): return ["is_first_miff": isFirstMiff]
        case .onboardingCompleted(let via): return ["via": via.rawValue]
        case .paywallShown(let trigger): return ["trigger": trigger.rawValue]
        case .paywallDismissed(let trigger): return ["trigger": trigger.rawValue]
        #if DEBUG || INTERNAL
        case .diagnosticTest: return [:]
        #endif
        }
    }
}
