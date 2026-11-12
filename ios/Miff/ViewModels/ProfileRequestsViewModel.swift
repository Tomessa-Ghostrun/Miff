import Foundation
#if canImport(Combine)
import Combine
#endif

// CONFIDENTIALITÉ et COÛT, pour ce fichier : les demandes (noms, adresses) restent en mémoire, pour ce view model seulement (un par compte et
// par session : la personne suivante ne les rencontre jamais) ; rien n'est journalisé ni envoyé comme événement. Les règles de lecture sont
// celles de `PlaceRequestBook` : un MEMBRE ne lit QUE ses propres demandes (une requête de 3 documents au plus), au plus une fois par Miff
// et par 5 minutes.

/// La Miff sur laquelle le carrousel est arrêté, et ce qu'on sait des demandes de chaque Miff montrée jusqu'ici.
struct ProfileRequestsState: Equatable, CustomStringConvertible {
    var selectedMiffId: String?
    /// Par identifiant de Miff. Une Miff absente n'a encore rien.
    var views: [String: RequestsView] = [:]
    /// La demande en cours de reprise (ses boutons attendent, un second toucher ne fait rien).
    var busyRequestId: String?

    var description: String {
        "ProfileRequestsState(redacted)"
    }

    /// Ce que montre la liste de la Miff choisie.
    var selected: RequestsView {
        selectedMiffId.flatMap { views[$0] } ?? .loading
    }

    func request(miffId: String, requestId: String) -> PlaceRequest? {
        views[miffId]?.requests.first { $0.id == requestId }
    }
}

/// Ce que les écrans disent une fois, calmement, après un geste sur une demande.
enum RequestNoticeKind: Equatable, CustomStringConvertible {
    /// « Demande retirée. » (par son auteur).
    case withdrawn
    /// « Cette demande n'est plus en attente. »
    case gone
    /// Le geste n'a pas marché : la phrase calme et générique, ou celle de « pas de connexion ».
    case failed(offline: Bool)

    var description: String {
        "RequestNoticeKind"
    }

    /// La clé du texte (catalogue partagé avec Android).
    var textKey: String {
        switch self {
        case .withdrawn:
            return "place_request_withdrawn"
        case .gone:
            return "place_request_gone"
        case .failed(let offline):
            return offline ? "miff_message_offline" : "miff_message_generic"
        }
    }
}

/// Les demandes d'ajout d'un lieu, sous la liste des lieux de l'onglet Profil (ticket LIEU-REQ, décision du PM du 2026-11-11).
///
/// MEMBRE (B) : les demandes en attente de la personne sont lues quand la carte d'une Miff apparaît (`onCardShown`), en tête de sa liste ;
/// une demande est reprise avec `onWithdraw`.
@MainActor
final class ProfileRequestsViewModel: ObservableObject {
    @Published private(set) var state = ProfileRequestsState()
    /// Le message calme du dernier geste, montré sur la liste jusqu'à ce que la personne la quitte ou fasse un autre geste.
    @Published private(set) var notice: RequestNoticeKind?

    let repository: PlaceRequestRepository
    let network: NetworkMonitor
    let myUid: () -> String?
    let book: PlaceRequestBook
    /// Les Miffs que la personne a créées parmi celles dont une carte a été montrée.
    var creatorMiffs = Set<String>()
    /// Les Miffs dont la liste est dessinée : redessinées à chaque changement.
    var drawn = Set<String>()

    init(
        repository: PlaceRequestRepository,
        network: NetworkMonitor,
        myUid: @escaping () -> String?,
        nowMillis: @escaping () -> Int = { Int(Date().timeIntervalSince1970 * 1000) }
    ) {
        self.repository = repository
        self.network = network
        self.myUid = myUid
        book = PlaceRequestBook(nowMillis: nowMillis)
    }

    // MARK: - Lire

    /// La carte de `miffId` vient d'apparaître (c'est la page que la personne regarde). `creator` : la personne a créé cette Miff. Un
    /// membre lit ses demandes si, et seulement si, le registre le dit.
    func onCardShown(miffId: String, creator: Bool) {
        if creator {
            creatorMiffs.insert(miffId)
        } else {
            creatorMiffs.remove(miffId)
        }
        drawn.insert(miffId)
        if state.selectedMiffId != miffId {
            state.selectedMiffId = miffId
        }
        publish()
        guard !creator, book.needsReading(miffId: miffId), let uid = myUid() else {
            return
        }
        book.readingStarted(miffId: miffId)
        publish()
        let repository = self.repository
        Task { [weak self] in
            let answer = await repository.readMine(uid: uid, miffId: miffId)
            self?.readingEnded(miffId: miffId, answer: answer)
        }
    }

    private func readingEnded(miffId: String, answer: MineRead) {
        switch answer {
        case .loaded(let found):
            book.readingSucceeded(miffId: miffId, requests: found)
        case .failed:
            book.readingFailed(miffId: miffId)
        }
        publish()
    }

    /// Le détail d'une demande est ouvert alors que rien n'est connu (l'écran a été recréé) : les demandes de cette Miff sont lues une fois.
    func onRequestOpened(miffId: String, creator: Bool) {
        guard state.views[miffId] == nil else {
            return
        }
        onCardShown(miffId: miffId, creator: creator)
    }

    /// « Réessayer » : relit à la prochaine apparition (un rafraîchissement discret, rien n'est dit en cas d'échec).
    func onRetry() {
        guard let miffId = state.selectedMiffId else {
            return
        }
        book.invalidate(miffId: miffId)
        onCardShown(miffId: miffId, creator: creatorMiffs.contains(miffId))
    }

    /// La personne ouvre le formulaire ou une demande : quoi qu'il s'y passe, les listes sont relues à leur retour.
    func onRequestsMayChange() {
        book.invalidateAll()
    }

    /// Les Miffs de la personne maintenant : les demandes de toute autre sont oubliées.
    func retainOnly(miffIds: Set<String>) {
        book.retainOnly(miffIds: miffIds)
        creatorMiffs = creatorMiffs.intersection(miffIds)
        drawn = drawn.intersection(miffIds)
        // Rien d'une Miff que la personne a quittée ne reste : ses demandes.
        let kept = state.views.filter { miffIds.contains($0.key) }
        if kept != state.views {
            state.views = kept
        }
        if let selected = state.selectedMiffId, !miffIds.contains(selected) {
            state.selectedMiffId = nil
        }
        publish()
    }

    /// Une demande ouverte n'est plus là (reprise, expirée, traitée ailleurs) : la phrase neutre, sur la liste où l'on revient.
    func showGone() {
        notice = .gone
    }

    /// Le message calme est lu : la personne quitte la liste.
    func consumeNotice() {
        if notice != nil {
            notice = nil
        }
    }

    // MARK: - Reprendre sa demande (membre)

    /// « Retirer ma demande » (critère M6) : supprime la demande, qui quitte alors la liste tout de suite.
    func onWithdraw(_ request: PlaceRequest) {
        guard startAnswer(request) else {
            return
        }
        let repository = self.repository
        Task { [weak self] in
            let result = await repository.withdraw(request)
            self?.withdrawEnded(request, result: result)
        }
    }

    private func withdrawEnded(_ request: PlaceRequest, result: RequestWithdrawResult) {
        switch result {
        case .done:
            book.remove(miffId: request.miffId, requestId: request.id)
            notice = .withdrawn
        case .failed(let failure):
            notice = .failed(offline: failure == .offline)
        }
        endAnswer()
    }

    // MARK: - Publier

    /// Un geste sur une demande commence : un seul à la fois, et jamais sans réseau (la phrase calme le dit).
    func startAnswer(_ request: PlaceRequest) -> Bool {
        guard state.busyRequestId == nil else {
            return false
        }
        guard network.isOnline() else {
            notice = .failed(offline: true)
            return false
        }
        notice = nil
        state.busyRequestId = request.id
        return true
    }

    func endAnswer() {
        state.busyRequestId = nil
        publish()
    }

    /// Chaque Miff dessinée est redessinée (l'horloge a avancé : une demande a pu expirer).
    func publish() {
        var views = state.views
        var ids = Set(views.keys).union(drawn)
        if let selected = state.selectedMiffId {
            ids.insert(selected)
        }
        for id in ids {
            views[id] = visibleView(id)
        }
        if views != state.views {
            state.views = views
        }
    }

    /// Les demandes d'une Miff telles que la liste les dessine.
    func visibleView(_ miffId: String) -> RequestsView {
        book.view(miffId: miffId, creator: creatorMiffs.contains(miffId))
    }
}
