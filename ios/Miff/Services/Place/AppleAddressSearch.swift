import Foundation
import MapKit

// SÉCURITÉ, pour ce fichier : le texte tapé est une adresse (souvent un domicile). Il part au service de suggestions d'Apple
// (`MKLocalSearchCompleter`) et nulle part ailleurs : jamais dans un journal, un événement Analytics ou un message d'erreur. AUCUNE
// permission de localisation n'est demandée ni nécessaire : ni `MKLocalSearchCompleter` ni `MKLocalSearch` ne lisent la position de
// l'appareil (la région de recherche reste celle par défaut). Aucune clé, aucun compte : MapKit est livré avec le système.

/// Les suggestions d'adresse et les coordonnées de l'adresse choisie, par MapKit (iOS : à la place de Photon sur Android, qui n'a
/// pas d'équivalent dans le système). Deux temps, pour ne pas payer une requête par ligne montrée : `search` donne les lignes
/// (titre et sous-titre), `resolve` demande les coordonnées de LA suggestion choisie seulement.
///
/// MapKit n'a pas de quota publié ; Apple peut refuser (« throttled ») un usage trop fréquent : c'est une réponse `unavailable`,
/// avec « Réessayer ». Les garde-fous du ticket réduisent l'usage : 3 caractères au moins, 300 ms après la dernière frappe, requête
/// précédente annulée, cache de session (`CachedAddressSearch`).
@MainActor
final class AppleAddressSearch: AddressSearch {
    private let core = AppleSearchCore()

    func search(query: String) async -> AddressSearchResult {
        await core.search(query: query)
    }

    func resolve(_ suggestion: AddressSuggestion) async -> ResolvedAddress? {
        await core.resolve(suggestion)
    }

    func reset() {
        core.reset()
    }
}

/// Le moteur, sur le fil principal (MapKit l'exige pour le complétion). Une seule recherche en cours : une nouvelle fait finir
/// l'ancienne en `unavailable` (l'appelant l'ignore, son texte a changé).
@MainActor
private final class AppleSearchCore: NSObject, MKLocalSearchCompleterDelegate {
    private static let memoryLimit = 400

    private let completer = MKLocalSearchCompleter()
    private var pending: CheckedContinuation<AddressSearchResult, Never>?
    private var currentQuery = ""
    private var completions: [String: MKLocalSearchCompletion] = [:]
    private var counter = 0

    override init() {
        super.init()
        completer.delegate = self
        // Des adresses et des lieux nommés (un stade, une école) ; pas de « requêtes » du genre « restaurants à proximité ».
        completer.resultTypes = [.address, .pointOfInterest]
    }

    func search(query: String) async -> AddressSearchResult {
        finishPending(with: .unavailable)
        completer.cancel()
        return await withCheckedContinuation { continuation in
            pending = continuation
            currentQuery = query
            completer.queryFragment = query
        }
    }

    func resolve(_ suggestion: AddressSuggestion) async -> ResolvedAddress? {
        guard let completion = completions[suggestion.id] else {
            return nil
        }
        do {
            let response = try await MKLocalSearch(request: MKLocalSearch.Request(completion: completion)).start()
            guard let item = response.mapItems.first else {
                return nil
            }
            let coordinate = item.placemark.coordinate
            return AddressCleaning.resolved(suggestion: suggestion, lat: coordinate.latitude, lng: coordinate.longitude)
        } catch {
            return nil
        }
    }

    /// Fin de session : ce qui a été tapé ou reçu n'est pas gardé.
    func reset() {
        finishPending(with: .unavailable)
        completer.cancel()
        completer.queryFragment = ""
        completions = [:]
        currentQuery = ""
    }

    private func finishPending(with result: AddressSearchResult) {
        guard let continuation = pending else {
            return
        }
        pending = nil
        continuation.resume(returning: result)
    }

    // MARK: - Réponses de MapKit (rappelées sur le fil principal)

    nonisolated func completerDidUpdateResults(_ completer: MKLocalSearchCompleter) {
        let fragment = completer.queryFragment
        let results = completer.results
        Task { @MainActor in
            self.deliver(results, for: fragment)
        }
    }

    nonisolated func completer(_ completer: MKLocalSearchCompleter, didFailWithError error: Error) {
        let fragment = completer.queryFragment
        Task { @MainActor in
            // Refus d'Apple (« throttled »), pas de réseau : la même phrase calme, jamais le détail.
            if fragment == self.currentQuery {
                self.finishPending(with: .unavailable)
            }
        }
    }

    private func deliver(_ results: [MKLocalSearchCompletion], for fragment: String) {
        // Une réponse tardive à une frappe précédente n'est pas celle de ce qui est tapé maintenant.
        guard pending != nil, fragment == currentQuery else {
            return
        }
        if completions.count > Self.memoryLimit {
            completions = [:]
        }
        var found: [AddressSuggestion] = []
        for completion in results {
            counter += 1
            let identifier = "s\(counter)"
            if let suggestion = AddressCleaning.suggestion(
                id: identifier,
                title: completion.title,
                subtitle: completion.subtitle
            ) {
                completions[identifier] = completion
                found.append(suggestion)
            }
        }
        let suggestions = AddressCleaning.list(found)
        finishPending(with: suggestions.isEmpty ? .noResults : .found(suggestions))
    }
}
