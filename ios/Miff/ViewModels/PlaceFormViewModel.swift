import Foundation
#if canImport(Combine)
import Combine
#endif

// SÉCURITÉ, pour ce fichier : le nom, l'adresse (tapée et choisie), les coordonnées et les Miffs d'un lieu vivent en mémoire
// dans `PlaceFormState` seulement : jamais dans un journal, un événement Analytics, un état de restauration ou un rapport de
// plantage. Le texte tapé dans le champ d'adresse va au service de suggestions d'Apple et nulle part ailleurs.

/// Ce dont le formulaire d'un lieu a besoin.
struct PlaceFormDependencies {
    let repository: PlaceRepository
    let search: AddressSearch
    let network: NetworkMonitor
    let currentUid: () -> String?
    let notices: PlaceNotices
    /// La pause avant une recherche, après la dernière frappe (ticket ADR1) ; courte dans les tests.
    var debounceMillis = PlaceLimits.suggestionDebounceMillis
    /// Une recherche ne fait jamais attendre un écran pour toujours.
    var searchTimeoutSeconds: TimeInterval = 8
}

/// Le formulaire « Nouveau lieu » / « Modifier le lieu » (ticket LOC-3, miroir de LOC-1, §6.1 à §6.4 ; fiches `nouveau-lieu.md` et
/// `modifier-lieu.md`).
///
/// Rien n'est écrit avant l'appui sur « Enregistrer », rien n'est montré comme enregistré avant que le serveur ait confirmé, rien
/// n'est mis en attente hors ligne (critères PL9, HOR2). Les limites de la formule gratuite sont appliquées AVANT d'écrire ; les
/// règles ne sont que le filet. Aucune permission de localisation n'est demandée : la recherche d'adresse et la carte n'en ont
/// pas besoin (ticket LOC-4 pour la détection).
@MainActor
final class PlaceFormViewModel: ObservableObject {
    @Published private(set) var state: PlaceFormState

    /// nil : un nouveau lieu. Sinon l'identifiant du lieu à modifier.
    let placeId: String?
    /// La Miff d'où le lieu a été ouvert (deux lieux différents peuvent avoir le même identifiant dans deux Miffs).
    let miffId: String?
    let dependencies: PlaceFormDependencies
    var context: PlaceContext?
    var original: PlaceGroup?
    var searchTask: Task<Void, Never>?
    var resolveTask: Task<Void, Never>?
    private var loadTask: Task<Void, Never>?

    init(placeId: String?, miffId: String?, dependencies: PlaceFormDependencies) {
        self.placeId = placeId
        self.miffId = miffId
        self.dependencies = dependencies
        var first = PlaceFormState()
        first.mode = placeId == nil ? .create : .edit
        state = first
        load()
    }

    func update(_ change: (inout PlaceFormState) -> Void) {
        var next = state
        change(&next)
        state = next
    }

    // MARK: - Lire

    /// « Réessayer » après une lecture qui a échoué.
    func retry() {
        guard state.loading || state.loadFailed != nil else {
            return
        }
        update {
            $0.loading = true
            $0.loadFailed = nil
        }
        load()
    }

    private func load() {
        loadTask?.cancel()
        loadTask = Task { [weak self] in
            await self?.runLoad()
        }
    }

    private func runLoad() async {
        guard let uid = dependencies.currentUid() else {
            update {
                $0.loading = false
                $0.loadFailed = .generic
            }
            return
        }
        guard dependencies.network.isOnline() else {
            update {
                $0.loading = false
                $0.loadFailed = .offline
            }
            return
        }
        switch await dependencies.repository.prepare(uid: uid) {
        case .failed(let failure):
            update {
                $0.loading = false
                $0.loadFailed = PlaceMessage(failure: failure)
            }
        case .loaded(let loaded):
            context = loaded
            open(loaded)
        }
    }

    private func open(_ loaded: PlaceContext) {
        guard !loaded.ownedMiffs.isEmpty else {
            // Seul le créateur d'une Miff arrive ici : rien de sensé à montrer sinon.
            update {
                $0.loading = false
                $0.loadFailed = .generic
            }
            return
        }
        if let placeId {
            openForEdit(placeId: placeId, loaded: loaded)
        } else {
            openForCreate(loaded)
        }
    }

    private func openForCreate(_ loaded: PlaceContext) {
        if PlaceQuota.isAtFreeLimit(premium: loaded.premium, distinctPlaces: loaded.distinctPlaces) {
            // Le serveur dit 2 lieux déjà (la liste était en retard) : l'offre, et rien n'est écrit.
            update {
                $0.loading = false
                $0.premium = false
                $0.paywall = .limit
            }
            return
        }
        let preselected = DefaultMiffChoice.of(owned: loaded.ownedMiffs, lastJoinedId: loaded.lastJoinedMiffId)
        let selected = preselected.map { [$0] } ?? []
        update {
            $0.loading = false
            $0.premium = loaded.premium
            $0.owned = loaded.ownedMiffs
            $0.lastJoinedId = loaded.lastJoinedMiffId
            $0.selectedMiffIds = selected
        }
    }

    private func openForEdit(placeId: String, loaded: PlaceContext) {
        let group = loaded.groups.first { candidate in
            candidate.id == placeId && (miffId == nil || candidate.miffIds.contains(miffId ?? ""))
        }
        guard let group else {
            update {
                $0.loading = false
                $0.end = .placeGone
            }
            return
        }
        original = group
        let place = group.main
        let address = place.address ?? ""
        // Un lieu écrit avant que les adresses soient obligatoires n'en a pas : rien n'est choisi, et le formulaire dit quoi faire.
        let chosen = address.isEmpty ? nil : ResolvedAddress(label: address, lat: place.lat, lng: place.lng)
        var filled = PlaceFormState()
        filled.mode = .edit
        filled.loading = false
        filled.premium = loaded.premium
        filled.name = place.name
        filled.owned = loaded.ownedMiffs
        filled.lastJoinedId = loaded.lastJoinedMiffId
        filled.selectedMiffIds = loaded.ownedMiffs.map { $0.id }.filter { group.miffIds.contains($0) }
        filled.addressText = address
        filled.chosen = chosen
        // Un rayon hérité (les règles acceptent 50 à 2 000 m, l'écran 50 à 800 par pas de 50) est ramené au pas permis le plus proche :
        // le curseur et la valeur écrite restent d'accord. Le bouton reste grisé tant que rien d'autre n'a changé.
        filled.radiusM = PlaceRadius.snap(Double(place.radiusM))
        filled.type = place.type
        // Un lieu écrit avant que les catégories existent n'en a pas : la personne doit en choisir une pour enregistrer.
        filled.category = place.category
        filled.initial = filled.snapshot
        state = filled
    }

    // MARK: - Nom, Miffs

    func onNameChanged(_ typed: String) {
        guard !state.busy else {
            return
        }
        let limited = PlaceName.limit(typed)
        update {
            $0.name = limited.text
            $0.nameLimitReached = limited.limitReached
            $0.nameTooShort = false
            $0.message = nil
        }
    }

    func onMiffSheetOpened() {
        if state.canEditAddress && !state.singleMiff {
            update { $0.miffSheetOpen = true }
        }
    }

    func onMiffSheetClosed() {
        update { $0.miffSheetOpen = false }
    }

    /// Coche ou décoche une Miff ; à 5 cochées, les autres ne peuvent plus l'être (les règles lisent 20 documents par lot au plus).
    func onMiffToggled(_ toggled: String) {
        let current = state
        guard !current.busy, current.owned.contains(where: { $0.id == toggled }) else {
            return
        }
        let now = current.selectedMiffIds
        let next: [String]
        if now.contains(toggled) {
            next = now.filter { $0 != toggled }
        } else if now.count >= PlaceLimits.maxMiffs {
            next = now
        } else {
            next = now + [toggled]
        }
        // Toujours dans l'ordre de la liste, pour que les pastilles ne sautent pas.
        let ordered = current.owned.map { $0.id }.filter { next.contains($0) }
        update {
            $0.selectedMiffIds = ordered
            $0.message = nil
        }
    }

    // MARK: - Rayon, type, catégorie

    func onRadiusChanged(_ raw: Double) {
        guard !state.busy, state.chosen != nil else {
            return
        }
        update { $0.radiusM = PlaceRadius.snap(raw) }
    }

    /// Un compte gratuit qui touche « Zone de danger » : l'offre s'ouvre et le choix reste « Zone de confiance » (critère LIM3).
    func onTypeSelected(_ type: PlaceType) {
        let current = state
        guard !current.busy, !current.loading, current.loadFailed == nil, type != current.type else {
            return
        }
        if let reason = PlaceQuota.forTypeChange(premium: current.premium, from: current.type, to: type) {
            update { $0.paywall = reason }
            return
        }
        update { $0.type = type }
    }

    func onCategorySelected(_ category: PlaceCategory) {
        guard !state.busy else {
            return
        }
        update { $0.category = category }
    }

    /// L'hôte a quitté l'écran ou ouvert l'offre : les drapeaux à usage unique redescendent.
    func onPaywallHandled() {
        update { $0.paywall = nil }
    }
}
