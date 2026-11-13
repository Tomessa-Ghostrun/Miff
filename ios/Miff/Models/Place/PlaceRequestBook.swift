import Foundation

// CONFIDENTIALITÉ et COÛT, pour ce fichier (demandes d'ajout d'un lieu, ticket LIEU-REQ ; miroir d'Android `PlaceRequestBook.kt`) : les
// noms et adresses restent en mémoire, le temps de la session du view model, jamais journalisés, jamais dans un événement ; chaque type
// qui les porte cache son contenu dans `description`.
// COÛT : `PlaceRequestBook` décide quand une lecture du serveur est nécessaire. Un membre ne lit QUE ses propres demandes (3 documents au
// plus), au plus une fois par Miff et par 5 minutes, jamais en boucle après un échec ; le créateur est prévenu en direct par UN écouteur
// pendant que l'app est au premier plan (le registre ne lit alors jamais).

/// Ce que la liste des demandes d'une Miff sait.
enum RequestsView: Equatable, CustomStringConvertible {
    /// Rien n'est connu encore : une lecture est en cours ou va partir. Rien n'est dessiné (pas d'absence inventée).
    case loading
    /// Les demandes n'ont pas pu être lues (hors ligne sans copie, refus, lenteur) : la liste des lieux se dessine sans elles, sans rien
    /// dire d'inquiétant.
    case unavailable
    /// Les demandes que la personne peut voir (30 jours ou moins), dans l'ordre de la liste. Peut être vide : c'est un vrai « aucune ».
    case ready([PlaceRequest])

    var description: String {
        "RequestsView(redacted)"
    }

    var requests: [PlaceRequest] {
        if case .ready(let found) = self {
            return found
        }
        return []
    }
}

/// Qui sait quoi des demandes de chaque Miff, et quand une lecture est nécessaire. Du Swift pur : pas d'écran, pas d'horloge autre que
/// celle qu'on lui donne ; les règles du coût et de l'expiration vivent ici et sont testées ici. Utilisé depuis un seul fil (le principal).
final class PlaceRequestBook {
    /// Ce que la lecture d'une Miff vaut : réutilisée 5 minutes (ouvrir le Profil de nouveau, ou revenir sur une carte, ne lit rien).
    static let ttlMillis = 5 * 60 * 1000

    /// Après un échec, le prochain essai attend au moins ce temps (jamais de boucle ; la liste ne dit rien d'inquiétant entre-temps).
    static let retryMillis = 30 * 1000

    private enum Source {
        case read
        case live
        case failed
    }

    private struct Entry {
        var requests: [PlaceRequest]?
        var source: Source
        var atMillis: Int
        var stale = false
        var reading = false
    }

    private let nowMillis: () -> Int
    private var entries: [String: Entry] = [:]

    init(nowMillis: @escaping () -> Int) {
        self.nowMillis = nowMillis
    }

    /// Ce que la liste de `miffId` montre maintenant. Une demande de plus de 30 jours n'y est jamais (critère C10) : masquée ici,
    /// supprimée par celui qui la rencontre.
    func view(miffId: String, creator: Bool = false) -> RequestsView {
        guard let entry = entries[miffId] else {
            return .loading
        }
        guard let requests = entry.requests else {
            return entry.source == .failed ? .unavailable : .loading
        }
        let now = nowMillis()
        let alive = requests.filter { !$0.isExpired(nowMillis: now) }
        return .ready(creator ? PlaceRequestOrder.forCreator(alive) : PlaceRequestOrder.forMember(alive))
    }

    /// Les demandes connues de `miffId`, expirées comprises (pour les supprimer), ou nil quand rien n'est connu.
    func known(miffId: String) -> [PlaceRequest]? {
        entries[miffId]?.requests
    }

    /// La liste de `miffId`, qui vient d'apparaître, a-t-elle besoin d'une lecture du serveur ? Jamais pendant que l'écouteur la nourrit
    /// ni pendant qu'une lecture est en cours.
    func needsReading(miffId: String) -> Bool {
        guard let entry = entries[miffId] else {
            return true
        }
        if entry.reading || entry.source == .live {
            return false
        }
        let age = nowMillis() - entry.atMillis
        if entry.stale {
            return true
        }
        return entry.source == .failed ? age >= Self.retryMillis : age >= Self.ttlMillis
    }

    /// Une lecture part : la liste garde ce qu'elle montrait (ou rien), et aucune seconde lecture ne part pour elle.
    func readingStarted(miffId: String) {
        if var entry = entries[miffId] {
            entry.reading = true
            entries[miffId] = entry
        } else {
            entries[miffId] = Entry(requests: nil, source: .read, atMillis: nowMillis(), reading: true)
        }
    }

    func readingSucceeded(miffId: String, requests: [PlaceRequest]) {
        entries[miffId] = Entry(requests: requests, source: .read, atMillis: nowMillis())
    }

    /// Ce qui était dessiné reste dessiné (une liste qui y était ne disparaît pas parce que le réseau a cligné) ; seule une liste vide
    /// devient « indisponible ».
    func readingFailed(miffId: String) {
        entries[miffId] = Entry(requests: entries[miffId]?.requests, source: .failed, atMillis: nowMillis())
    }

    /// L'écouteur du créateur a donné les demandes de `miffId` : elles remplacent ce qui était connu, et rien n'est à lire tant qu'il est
    /// branché.
    func live(miffId: String, requests: [PlaceRequest]) {
        entries[miffId] = Entry(requests: requests, source: .live, atMillis: nowMillis())
    }

    /// L'écouteur n'est plus branché (l'app a quitté le premier plan) : ce qu'il a donné reste mais vieillit comme une lecture.
    func liveStopped(miffId: String) {
        guard var entry = entries[miffId], entry.source == .live else {
            return
        }
        entry.source = .read
        entry.stale = true
        entries[miffId] = entry
    }

    /// Une demande de la personne a été écrite ou retirée : à la prochaine apparition, chaque liste relit (le téléphone ne sait rien de ce
    /// qui a changé ailleurs).
    func invalidateAll() {
        for (miffId, entry) in entries where entry.source != .live {
            var stale = entry
            stale.stale = true
            entries[miffId] = stale
        }
    }

    /// « Réessayer » : la prochaine apparition relit tout de suite (sans attendre les 30 secondes).
    func invalidate(miffId: String) {
        guard var entry = entries[miffId], entry.source != .live else {
            return
        }
        entry.stale = true
        entries[miffId] = entry
    }

    /// Une demande qui a reçu sa réponse, ou qui a été retirée, quitte la liste tout de suite, sans attendre une lecture.
    func remove(miffId: String, requestId: String) {
        guard var entry = entries[miffId], let requests = entry.requests else {
            return
        }
        entry.requests = requests.filter { $0.id != requestId }
        entries[miffId] = entry
    }

    /// Une demande que le serveur vient d'accepter apparaît tout de suite dans la liste de son auteur.
    func add(miffId: String, request: PlaceRequest) {
        let entry = entries[miffId]
        let requests = (entry?.requests ?? []).filter { $0.id != request.id } + [request]
        entries[miffId] = Entry(
            requests: requests,
            source: entry?.source ?? .read,
            atMillis: entry?.atMillis ?? nowMillis(),
            stale: entry?.stale ?? false,
            reading: entry?.reading ?? false
        )
    }

    /// L'accès à une Miff est perdu : rien de ses demandes ne reste.
    func forget(miffId: String) {
        entries[miffId] = nil
    }

    /// Une Miff qui n'est plus dans la liste (quittée, supprimée, accès perdu) emporte ses demandes.
    func retainOnly(miffIds: Set<String>) {
        entries = entries.filter { miffIds.contains($0.key) }
    }
}
