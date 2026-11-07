import Foundation

/// Liste blanche d'événements Analytics, partagée avec Android : implémente
/// `docs/contracts/analytics-events.md`, version 1 du contrat.
/// Ajouter ou modifier un événement est une décision produit (PR sur le contrat ET sur les deux plateformes,
/// validation du PM).
///
/// Logique pure, sans dépendance à Firebase : testée sans réseau (mêmes cas que sur Android).
enum ParamSpec: Equatable {
    /// Chaîne tirée d'une liste fermée (100 caractères au plus, garanti par la liste elle-même).
    case text(Set<String>)
    /// Entier tiré d'une liste fermée.
    case integer(Set<Int>)
    case flag
}

struct AnalyticsEventSpec: Equatable {
    let name: String
    let params: [String: ParamSpec]

    init(_ name: String, params: [String: ParamSpec] = [:]) {
        self.name = name
        self.params = params
    }
}

enum RefusalReason: String, Equatable {
    case unknownEvent
    case unknownParam
    case locationLikeName
    case wrongType
    case valueNotAllowed

    /// Identifiant lisible en snake_case (« unknown_event »), comme sur Android.
    var identifier: String {
        switch self {
        case .unknownEvent: return "unknown_event"
        case .unknownParam: return "unknown_param"
        case .locationLikeName: return "location_like_name"
        case .wrongType: return "wrong_type"
        case .valueNotAllowed: return "value_not_allowed"
        }
    }
}

enum AnalyticsValidation: Equatable {
    case allowed
    case refused(reason: RefusalReason, detail: String)
}

enum AnalyticsContract {
    static let contractVersion = 1
    static let maxStringValueLength = 100

    #if DEBUG || INTERNAL
    /// Événement interne du diagnostic (builds Debug et Internal seulement).
    static let eventDiagnosticTest = "analytics_diagnostic_test"
    #endif

    private static let signInMethods = ParamSpec.text(["email", "google", "apple"])
    private static let paywallTriggers = ParamSpec.text(["extra_miff", "extra_trusted_place"])

    /// Les huit événements produit de la version 1 du contrat.
    private static let productEvents: [AnalyticsEventSpec] = [
        AnalyticsEventSpec("onboarding_cta_tapped", params: ["slide_index": .integer([0, 1, 2])]),
        AnalyticsEventSpec("sign_up_completed", params: ["method": signInMethods]),
        AnalyticsEventSpec("sign_in_completed", params: ["method": signInMethods]),
        AnalyticsEventSpec("miff_created", params: ["is_first_miff": .flag]),
        AnalyticsEventSpec("miff_joined", params: ["is_first_miff": .flag]),
        AnalyticsEventSpec("onboarding_completed", params: ["via": .text(["created", "joined"])]),
        AnalyticsEventSpec("paywall_shown", params: ["trigger": paywallTriggers]),
        AnalyticsEventSpec("paywall_dismissed", params: ["trigger": paywallTriggers])
    ]

    /// La liste blanche. `analytics_diagnostic_test` n'en fait partie que dans les builds Debug et Internal :
    /// le build Release n'accepte que les huit événements produit.
    static let allowedEvents: [String: AnalyticsEventSpec] = {
        var specs = productEvents
        #if DEBUG || INTERNAL
        specs.append(AnalyticsEventSpec(eventDiagnosticTest))
        #endif
        return Dictionary(uniqueKeysWithValues: specs.map { ($0.name, $0) })
    }()

    /// Mots (nom découpé sur « _ », sans tenir compte de la casse) qui font refuser un nom.
    private static let forbiddenWords: Set<String> = [
        "lat", "lng", "lon", "long", "latitude", "longitude", "coord", "coords", "coordinate", "coordinates",
        "address", "adresse", "place", "places", "lieu", "location", "loc", "position", "geo", "geofence",
        "gps", "region", "zone", "city", "ville", "street", "rue", "zip", "postal", "distance", "nearby"
    ]

    /// Fragments refusés n'importe où dans un nom, même collés à un autre mot (ex. « userlatitude »).
    private static let forbiddenFragments = [
        "latitude", "longitude", "location", "address", "coordinate", "geoloc", "gps"
    ]

    static func isForbiddenName(_ name: String) -> Bool {
        let lower = name.lowercased()
        if forbiddenFragments.contains(where: { lower.contains($0) }) {
            return true
        }
        return lower.split(separator: "_").contains { forbiddenWords.contains(String($0)) }
    }

    static func validate(_ eventName: String, params: [String: Any] = [:]) -> AnalyticsValidation {
        // Un paramètre qui évoque une position est refusé en premier, quel que soit l'événement :
        // c'est la règle la plus importante du contrat.
        if let name = params.keys.sorted().first(where: { isForbiddenName($0) }) {
            return .refused(reason: .locationLikeName, detail: name)
        }
        guard let spec = allowedEvents[eventName] else {
            return .refused(reason: .unknownEvent, detail: eventName)
        }
        for key in params.keys.sorted() {
            guard let paramSpec = spec.params[key] else {
                return .refused(reason: .unknownParam, detail: key)
            }
            if let problem = checkValue(paramSpec, params[key]) {
                return .refused(reason: problem, detail: key)
            }
        }
        return .allowed
    }

    private static func checkValue(_ spec: ParamSpec, _ value: Any?) -> RefusalReason? {
        switch spec {
        case .text(let allowed):
            guard let text = value as? String else {
                return .wrongType
            }
            return text.count <= maxStringValueLength && allowed.contains(text) ? nil : .valueNotAllowed
        case .integer(let allowed):
            // Un booléen n'est pas un entier, même si Objective-C les confond.
            guard !(value is Bool), let number = value as? Int else {
                return .wrongType
            }
            return allowed.contains(number) ? nil : .valueNotAllowed
        case .flag:
            return value is Bool ? nil : .wrongType
        }
    }
}
