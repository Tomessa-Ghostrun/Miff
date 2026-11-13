import Foundation
#if canImport(Combine)
import Combine
#endif

// SÉCURITÉ, pour ce fichier : le lieu (nom, adresse, coordonnées) et les noms des membres vivent en mémoire dans cet état seulement :
// jamais dans un journal, un événement Analytics (aucun n'est envoyé depuis cet écran) ni un rapport de plantage.

enum PlaceDetailStatus: Equatable {
    case loading
    case ready
    /// Le lieu a été supprimé (depuis un autre téléphone) pendant qu'il était ouvert : « Ce lieu n'existe plus. » et retour.
    case gone
    /// La Miff n'a pas pu être lue et rien n'est encore connu.
    case unavailable
    /// La Miff n'est plus à la personne : le message unique d'une perte d'accès est dessiné par-dessus tout par l'hôte.
    case accessLost
}

struct PlaceDetailState: CustomStringConvertible {
    var status = PlaceDetailStatus.loading
    var place: Place?
    var miffName = ""
    /// Prénom et nom du créateur du lieu, comme la fiche de la Miff les donne ; vide quand ils sont inconnus.
    var creatorName = ""
    var creatorIsMe = false
    /// Seul le créateur de la Miff peut changer le lieu (critère PL1).
    var canEdit = false
    var fromCache = false
    var offline = false

    var description: String {
        "PlaceDetailState(redacted)"
    }
}

/// Ce dont l'écran du détail d'un lieu a besoin.
struct PlaceDetailDependencies {
    let repository: MiffRepository
    let network: NetworkMonitor
    let currentUid: () -> String?
    /// L'annuaire interroge le serveur et, SEULEMENT si la perte est confirmée, nettoie et met le message unique en file (même
    /// contrat que l'écran de la Miff). Faux : le serveur ne l'a pas dit, ce n'est PAS un retrait.
    let onAccessLost: (String, String?) async -> Bool
}

/// Le détail d'un lieu (maquette 16) : lu en direct dans la Miff d'où il a été ouvert, pour qu'un changement fait sur un autre
/// téléphone se voie et qu'une suppression soit remarquée. Rien n'est écrit ici ; « Modifier » ouvre le formulaire.
@MainActor
final class PlaceDetailViewModel: ObservableObject {
    @Published private(set) var state = PlaceDetailState()

    private let miffId: String
    private let placeId: String
    private let dependencies: PlaceDetailDependencies
    private var lostReported = false

    init(miffId: String, placeId: String, dependencies: PlaceDetailDependencies) {
        self.miffId = miffId
        self.placeId = placeId
        self.dependencies = dependencies
    }

    func update(_ change: (inout PlaceDetailState) -> Void) {
        var next = state
        change(&next)
        state = next
    }

    /// Lit la Miff jusqu'à ce que l'appelant arrête (l'écran l'appelle dans une tâche que le système annule quand l'écran disparaît
    /// ou que l'app passe en arrière-plan : rien n'écoute une Miff que personne ne regarde).
    func observe() async {
        let network = dependencies.network
        let watcher = Task { [weak self] in
            for await online in network.onlineChanges() {
                self?.update { $0.offline = !online }
            }
        }
        defer {
            watcher.cancel()
        }
        for await observation in dependencies.repository.observe(miffId: miffId) {
            await handle(observation)
        }
    }

    private func handle(_ observation: MiffObservation) async {
        switch observation {
        case .loading:
            break
        case .accessLost:
            await handleLoss()
        case .failed:
            if state.status != .ready {
                update { $0.status = .unavailable }
            }
        case .data(let snapshot):
            lostReported = false
            apply(snapshot)
        }
    }

    private func handleLoss() async {
        if lostReported {
            return
        }
        lostReported = true
        let known = state.miffName.isEmpty ? nil : state.miffName
        // Rien du lieu ne reste à l'écran pendant que le serveur est interrogé.
        update {
            $0.status = .accessLost
            $0.place = nil
            $0.creatorName = ""
        }
        if await dependencies.onAccessLost(miffId, known) {
            return
        }
        lostReported = false
        // Le serveur ne l'a pas dit : un doute n'est pas un retrait. « Réessayer », aucun message ; l'écran a pu quitter le premier
        // plan pendant ce temps : alors rien n'est décidé et les écoutes le redisent au retour.
        update { $0.status = Task.isCancelled ? .loading : .unavailable }
    }

    /// « Réessayer » : l'écran relance l'écoute.
    func retry() {
        guard state.status == .unavailable else {
            return
        }
        lostReported = false
        update { $0.status = .loading }
    }

    private func apply(_ snapshot: MiffSnapshot) {
        guard let place = snapshot.places.first(where: { $0.id == placeId }) else {
            // « Pas encore dans le cache » n'est pas « supprimé » : seule une réponse du serveur peut dire que le lieu a disparu.
            update { $0.status = snapshot.fromCache ? .loading : .gone }
            return
        }
        let uid = dependencies.currentUid() ?? ""
        let creator = snapshot.members.first { $0.uid == place.createdBy }
        update {
            $0.status = .ready
            $0.place = place
            $0.miffName = snapshot.miff.name
            $0.creatorName = creator?.displayName ?? ""
            $0.creatorIsMe = place.createdBy == uid
            $0.canEdit = PlaceViewerRole.isCreator(ownerUid: snapshot.miff.ownerUid, myUid: dependencies.currentUid())
            $0.fromCache = snapshot.fromCache
        }
    }
}
