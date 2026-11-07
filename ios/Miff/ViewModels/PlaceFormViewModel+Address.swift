import Foundation

// SÉCURITÉ, pour ce fichier : ce que la personne tape ici est une adresse, souvent un domicile. Le texte part au service de
// suggestions d'Apple et nulle part ailleurs : jamais dans un journal, un événement Analytics ou un message d'erreur.

/// L'adresse du formulaire : suggestions dès 3 caractères, 300 ms après la dernière frappe, la requête précédente annulée quand une
/// nouvelle lettre arrive (critère ADR1) ; une adresse CHOISIE donne la latitude et la longitude du lieu.
extension PlaceFormViewModel {
    /// Taper change le texte ; taper après avoir choisi une adresse efface le choix (le bouton et la carte attendent de nouveau).
    func onAddressChanged(_ typed: String) {
        let current = state
        guard current.canEditAddress else {
            return
        }
        let text = MiffName.limit(typed, maximum: PlaceLimits.addressMax).text
        let keepChoice = current.chosen != nil && text == current.chosen?.label
        if !keepChoice {
            // Une réponse du service qui arrive après la frappe n'est plus celle de ce qui est tapé.
            resolveTask?.cancel()
        }
        update {
            $0.addressText = text
            if keepChoice {
                $0.suggestions = .idle
            } else {
                $0.chosen = nil
                $0.resolving = false
            }
            $0.message = nil
        }
        if !keepChoice {
            startSearch(text)
        }
    }

    /// La personne touche une suggestion : ses coordonnées sont demandées au service (une seule requête, pour ce choix).
    func onSuggestionChosen(_ suggestion: AddressSuggestion) {
        guard state.canEditAddress else {
            return
        }
        searchTask?.cancel()
        resolveTask?.cancel()
        let label = AddressCleaning.label(of: suggestion)
        update {
            $0.addressText = label
            $0.suggestions = .idle
            $0.chosen = nil
            $0.resolving = true
            $0.message = nil
        }
        resolveTask = Task { [weak self] in
            await self?.resolve(suggestion, label: label)
        }
    }

    private func resolve(_ suggestion: AddressSuggestion, label: String) async {
        let search = dependencies.search
        let resolved: ResolvedAddress?
        do {
            resolved = try await timedOperation(seconds: dependencies.searchTimeoutSeconds) {
                await search.resolve(suggestion)
            }
        } catch {
            resolved = nil
        }
        if Task.isCancelled {
            return
        }
        update {
            // La personne a pu retaper pendant ce temps : alors cette réponse n'est plus la sienne.
            guard $0.resolving, $0.addressText == label else {
                return
            }
            $0.resolving = false
            if let resolved {
                $0.chosen = resolved
            } else {
                $0.suggestions = .unavailable
            }
        }
    }

    /// La croix du champ : recommencer.
    func onAddressCleared() {
        guard state.canEditAddress else {
            return
        }
        searchTask?.cancel()
        resolveTask?.cancel()
        update {
            $0.addressText = ""
            $0.chosen = nil
            $0.resolving = false
            $0.suggestions = .idle
        }
    }

    /// « Réessayer » sous « La recherche d'adresse n'est pas disponible ».
    func onRetrySearch() {
        let current = state
        if current.chosen == nil && !current.resolving {
            startSearch(current.addressText)
        }
    }

    /// À partir de 3 caractères, 300 ms après la dernière frappe ; le même texte retapé est servi par le cache de la session (voir
    /// `CachedAddressSearch`). Le texte ne va nulle part ailleurs qu'au service.
    func startSearch(_ text: String) {
        searchTask?.cancel()
        let query = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard query.utf16.count >= PlaceLimits.suggestionMinChars else {
            update { $0.suggestions = .idle }
            return
        }
        update { $0.suggestions = .loading }
        searchTask = Task { [weak self] in
            await self?.runSearch(query)
        }
    }

    private func runSearch(_ query: String) async {
        try? await Task.sleep(nanoseconds: UInt64(dependencies.debounceMillis) * 1_000_000)
        if Task.isCancelled {
            return
        }
        let answer = await answer(for: query)
        if Task.isCancelled {
            return
        }
        let next: SuggestionsState
        switch answer {
        case .found(let suggestions):
            next = .found(suggestions)
        case .noResults:
            next = .noResults
        case .unavailable:
            next = .unavailable
        }
        update {
            // Seulement si la personne n'a ni choisi ni retapé entre-temps.
            if $0.chosen == nil && !$0.resolving
                && $0.addressText.trimmingCharacters(in: .whitespacesAndNewlines) == query {
                $0.suggestions = next
            }
        }
    }

    private func answer(for query: String) async -> AddressSearchResult {
        guard dependencies.network.isOnline() else {
            return .unavailable
        }
        let search = dependencies.search
        do {
            return try await timedOperation(seconds: dependencies.searchTimeoutSeconds) {
                await search.search(query: query)
            }
        } catch {
            return .unavailable
        }
    }
}
