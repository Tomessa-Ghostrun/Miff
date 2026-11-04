import Foundation

// SÉCURITÉ, pour ce fichier : ce que la personne tape est une adresse (souvent un domicile), et ce qui revient est écrit par un
// service que nous ne contrôlons pas. Le texte tapé n'est JAMAIS journalisé, jamais dans un événement Analytics, jamais dans un
// message d'erreur. Tout ce qui revient est nettoyé comme tout texte lu sur le réseau (caractères de contrôle et invisibles
// retirés, longueurs bornées, coordonnées vérifiées) AVANT d'être montré ou écrit quelque part.

/// Une suggestion d'adresse telle qu'elle est montrée, sur deux lignes. Elle n'a PAS de coordonnées : le service d'Apple ne les
/// donne qu'à la demande, pour la suggestion choisie (`AddressSearch.resolve`), ce qui évite une requête par ligne montrée.
struct AddressSuggestion: Equatable, CustomStringConvertible {
    /// Désigne cette suggestion auprès du service qui l'a donnée ; sans sens ailleurs.
    let id: String
    /// Première ligne : la rue et son numéro (ou le nom du lieu, ou la ville).
    let title: String
    /// Seconde ligne : code postal, ville, pays. Peut être vide.
    let subtitle: String

    var description: String {
        "AddressSuggestion(redacted)"
    }
}

/// L'adresse choisie, avec l'endroit où elle est : ce qui est écrit dans le lieu.
struct ResolvedAddress: Equatable, CustomStringConvertible {
    /// L'adresse entière telle qu'elle est enregistrée (200 caractères au plus).
    let label: String
    let lat: Double
    let lng: Double

    var description: String {
        "ResolvedAddress(redacted)"
    }
}

/// Ce que dit une recherche de suggestions.
enum AddressSearchResult: Equatable {
    case found([AddressSuggestion])
    /// Le service a répondu et ne connaît pas cette adresse : « On ne trouve pas cette adresse. Essaie avec moins de détails. »
    case noResults
    /// Pas de réseau, trop lent, refusé, réponse sans sens : « La recherche d'adresse n'est pas disponible… ». Jamais gardé en
    /// cache.
    case unavailable
}

/// La recherche elle-même, derrière un protocole : le service d'Apple sur l'iPhone, un double dans les tests. Sur le fil principal
/// (MapKit l'exige pour la complétion ; le formulaire qui l'appelle y est aussi).
@MainActor
protocol AddressSearch {
    /// Les suggestions pour ce que la personne a tapé. Une recherche qui en remplace une autre fait finir la première
    /// (`unavailable`, que l'appelant ignore parce que son texte a changé).
    func search(query: String) async -> AddressSearchResult

    /// Les coordonnées de la suggestion choisie, ou nil quand le service ne sait pas les donner.
    func resolve(_ suggestion: AddressSuggestion) async -> ResolvedAddress?

    /// Oublie tout ce qui a été tapé ou reçu (fin de session).
    func reset()
}

/// Les textes du service, rendus sûrs : l'unique chemin par lequel une suggestion ou une adresse choisie entre dans l'app.
enum AddressCleaning {
    /// Un morceau de texte du réseau : nettoyé, sans les espaces des bords, borné ; nil quand il n'en reste rien.
    static func part(_ raw: String) -> String? {
        let text = MiffName.limit(raw, maximum: 100).text.trimmingCharacters(in: .whitespacesAndNewlines)
        return text.isEmpty ? nil : text
    }

    /// Une suggestion prête à être montrée ; nil quand elle n'a rien de visible.
    static func suggestion(id: String, title: String, subtitle: String) -> AddressSuggestion? {
        guard let cleanTitle = part(title) else {
            return nil
        }
        return AddressSuggestion(id: id, title: cleanTitle, subtitle: part(subtitle) ?? "")
    }

    /// L'adresse entière d'une suggestion : « rue, code postal ville, pays », bornée comme les règles.
    static func label(of suggestion: AddressSuggestion) -> String {
        let joined = [suggestion.title, suggestion.subtitle].filter { !$0.isEmpty }.joined(separator: ", ")
        return PlaceAddressText.stored(joined)
    }

    /// L'adresse choisie avec ses coordonnées ; nil quand elles sont hors de la planète ou que l'adresse est vide.
    static func resolved(suggestion: AddressSuggestion, lat: Double, lng: Double) -> ResolvedAddress? {
        let text = label(of: suggestion)
        guard !text.isEmpty, PlaceCoordinates.isValid(lat: lat, lng: lng) else {
            return nil
        }
        return ResolvedAddress(label: text, lat: PlaceCoordinates.round5(lat), lng: PlaceCoordinates.round5(lng))
    }

    /// Les suggestions d'une réponse : nettoyées, sans doublon (même adresse entière), au plus `suggestionCount`.
    static func list(_ suggestions: [AddressSuggestion]) -> [AddressSuggestion] {
        var seen = Set<String>()
        var kept: [AddressSuggestion] = []
        for suggestion in suggestions {
            let key = label(of: suggestion)
            if seen.insert(key).inserted {
                kept.append(suggestion)
            }
            if kept.count == PlaceLimits.suggestionCount {
                break
            }
        }
        return kept
    }
}

/// Garde en mémoire, pour la session seulement, les réponses déjà reçues : le même texte tapé deux fois n'ajoute aucune requête
/// (critère ADR1). Les réponses « indisponible » ne sont jamais gardées. Le cache est vidé avec la session (`reset`) : ce que la
/// personne a tapé ne survit pas à sa déconnexion.
@MainActor
final class CachedAddressSearch: AddressSearch {
    private let inner: AddressSearch
    private let capacity: Int
    private var answers: [String: AddressSearchResult] = [:]
    private var order: [String] = []

    init(_ inner: AddressSearch, capacity: Int = 40) {
        self.inner = inner
        self.capacity = capacity
    }

    private func key(_ query: String) -> String {
        query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }

    func search(query: String) async -> AddressSearchResult {
        let cacheKey = key(query)
        if let known = answers[cacheKey] {
            return known
        }
        let answer = await inner.search(query: query)
        if answer != .unavailable {
            remember(answer, for: cacheKey)
        }
        return answer
    }

    private func remember(_ answer: AddressSearchResult, for cacheKey: String) {
        if answers[cacheKey] == nil {
            order.append(cacheKey)
        }
        answers[cacheKey] = answer
        while order.count > capacity {
            answers[order.removeFirst()] = nil
        }
    }

    func resolve(_ suggestion: AddressSuggestion) async -> ResolvedAddress? {
        let resolved = await inner.resolve(suggestion)
        if resolved == nil {
            // Le service a oublié cette suggestion (sa mémoire est bornée) : toute réponse gardée qui la contient est périmée, sinon
            // « Réessayer » reprendrait la même réponse périmée sans fin.
            forget(containing: suggestion.id)
        }
        return resolved
    }

    private func forget(containing identifier: String) {
        let stale = answers.filter { _, answer in
            if case .found(let suggestions) = answer {
                return suggestions.contains { $0.id == identifier }
            }
            return false
        }.map { $0.key }
        for key in stale {
            answers[key] = nil
            order.removeAll { $0 == key }
        }
    }

    func reset() {
        answers = [:]
        order = []
        inner.reset()
    }
}
