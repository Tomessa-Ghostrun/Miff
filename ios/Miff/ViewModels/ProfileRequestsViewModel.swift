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
    /// La demande en cours de reprise, d'acceptation ou de refus (ses boutons attendent, un second toucher ne fait rien).
    var busyRequestId: String?
    /// Qui a demandé, par Miff et par personne (voir `RequesterKey`). Une personne absente n'a pas encore été lue.
    var requesters: [String: RequesterState] = [:]
    /// Le point de l'onglet Profil : une demande attend que le créateur n'a pas encore vue dans sa liste (ticket LIEU-REQ, question Q-R1).
    var profileDot = false

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

    func requester(of request: PlaceRequest) -> RequesterState? {
        requesters[RequesterKey.key(miffId: request.miffId, uid: request.requestedBy)]
    }
}

/// Ce que les écrans disent une fois, calmement, après un geste sur une demande.
enum RequestNoticeKind: Equatable, CustomStringConvertible {
    /// « Demande retirée. » (par son auteur).
    case withdrawn
    /// « Cette demande n'est plus en attente. »
    case gone
    /// « « X » a été ajouté à la Miff. » (le créateur a accepté : les lieux de cette Miff sont relus).
    case accepted(placeName: String)
    /// « Demande retirée de la liste. » (le créateur a refusé : rien n'est dit au demandeur).
    case declined
    /// « Impossible de vérifier ton abonnement pour l'instant. » : aucune offre n'est ouverte sur un doute.
    case subscriptionUnknown
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
        case .accepted:
            return "place_request_accepted_toast"
        case .declined:
            return "place_request_declined_toast"
        case .subscriptionUnknown:
            return "place_request_subscription_unknown"
        case .failed(let offline):
            return offline ? "miff_message_offline" : "miff_message_generic"
        }
    }

    /// Le geste n'a pas abouti (le message reste sur l'écran où la personne est) ; les autres le terminent (l'écran se referme).
    var isProblem: Bool {
        switch self {
        case .failed, .subscriptionUnknown:
            return true
        case .withdrawn, .gone, .accepted, .declined:
            return false
        }
    }

    /// Le texte montré (le nom du lieu accepté est celui de la demande, en mémoire seulement).
    var text: String {
        if case .accepted(let name) = self {
            return L10n.format(textKey, name)
        }
        return L10n.string(textKey)
    }
}

/// Les demandes d'ajout d'un lieu, sous la liste des lieux de l'onglet Profil (ticket LIEU-REQ, décision du PM du 2026-11-11).
///
/// MEMBRE (B) : les demandes en attente de la personne sont lues quand la carte d'une Miff apparaît (`onCardShown`), en tête de sa liste ;
/// une demande est reprise avec `onWithdraw`.
/// CRÉATEUR (C) : `watch` écoute les demandes d'UNE Miff (celle de la carte regardée, sinon la Miff active : `tracked`) pendant que l'app est
/// devant ; chaque demande allume le point de l'onglet Profil jusqu'à ce que la liste soit vue, et se montre dans la liste avec qui l'a faite ;
/// `onAccept` et `onDecline` y répondent. Les demandes de plus de 30 jours, ou faites par quelqu'un qui a quitté la Miff, sont masquées et
/// supprimées par le créateur (`ProfileRequestsViewModel+Creator.swift`).
@MainActor
final class ProfileRequestsViewModel: ObservableObject {
    @Published var state = ProfileRequestsState()
    /// Le message calme du dernier geste, montré sur la liste jusqu'à ce que la personne la quitte ou fasse un autre geste.
    @Published var notice: RequestNoticeKind?
    /// La Miff dont le créateur est prévenu en direct : celle de la carte montrée si la personne l'a créée, sinon la Miff active du Feed si
    /// elle l'a créée, sinon aucune (un membre n'est prévenu de rien : il ne lit que ses propres demandes). UN écouteur, jamais un par Miff.
    @Published var tracked: String?
    /// L'offre doit s'ouvrir (la formule gratuite a ses 3 lieux, ou la demande est une zone de danger) : l'hôte la consomme. La demande reste
    /// en attente.
    @Published var paywall: PaywallReason?
    /// Monte à chaque « Réessayer » d'un créateur : l'hôte relance l'écouteur (un créateur ne lit pas, il écoute).
    @Published var watchEpoch = 0

    let repository: PlaceRequestRepository
    let network: NetworkMonitor
    let myUid: () -> String?
    let book: PlaceRequestBook
    /// Les Miffs que la personne a créées parmi celles dont une carte a été montrée.
    var creatorMiffs = Set<String>()
    /// Les Miffs dont la liste est dessinée : redessinées à chaque changement.
    var drawn = Set<String>()
    /// La Miff active du Feed si la personne l'a créée (décide `tracked` tant qu'aucune carte n'a été montrée).
    var activeCreatorMiff: String?
    /// Les demandes dont la liste a été vue (par Miff et par demande) : le point de l'onglet Profil s'éteint.
    var seen = Set<String>()
    var resolving = Set<String>()
    var unknownSince: [String: Int] = [:]
    var purging = Set<String>()
    let nowMillis: () -> Int
    /// Une demande vient d'être acceptée : les lieux de cette Miff sont relus (l'hôte branche la liste des lieux du Profil).
    var onAccepted: (String) -> Void = { _ in }
    /// Les Miffs que la personne a maintenant, une fois connues : une lecture qui finit après le départ d'une Miff est jetée.
    var knownMiffIds: Set<String>?
    /// Le centre de notifications (local) et les prénoms derrière ses lignes : une ligne par demande reçue (nil dans les tests sans centre).
    var center: NotificationCenterService?
    var directory: RequesterDirectory?

    init(
        repository: PlaceRequestRepository,
        network: NetworkMonitor,
        myUid: @escaping () -> String?,
        nowMillis: @escaping () -> Int = { Int(Date().timeIntervalSince1970 * 1000) }
    ) {
        self.repository = repository
        self.network = network
        self.myUid = myUid
        self.nowMillis = nowMillis
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
        updateTracked()
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
        if let known = knownMiffIds, !known.contains(miffId) {
            return
        }
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
        guard creator else {
            onCardShown(miffId: miffId, creator: false)
            return
        }
        // Le créateur : toutes les demandes de la Miff, une fois (l'écouteur n'est pas branché).
        creatorMiffs.insert(miffId)
        drawn.insert(miffId)
        guard book.needsReading(miffId: miffId) else {
            return
        }
        book.readingStarted(miffId: miffId)
        let repository = self.repository
        Task { [weak self] in
            let answer = await repository.readAll(miffId: miffId)
            self?.creatorReadingEnded(miffId: miffId, answer: answer)
        }
    }

    /// « Réessayer » : relit à la prochaine apparition (un rafraîchissement discret, rien n'est dit en cas d'échec).
    func onRetry() {
        guard let miffId = state.selectedMiffId else {
            return
        }
        book.invalidate(miffId: miffId)
        if creatorMiffs.contains(miffId) {
            watchEpoch += 1
        }
        onCardShown(miffId: miffId, creator: creatorMiffs.contains(miffId))
    }

    /// La personne ouvre le formulaire ou une demande : quoi qu'il s'y passe, les listes sont relues à leur retour.
    func onRequestsMayChange() {
        book.invalidateAll()
    }

    /// Le Profil revient à l'écran (au retour d'un formulaire ou d'un détail) : la liste regardée est relue si elle est périmée.
    func onProfileAppeared() {
        if let selected = state.selectedMiffId {
            onCardShown(miffId: selected, creator: creatorMiffs.contains(selected))
        }
    }

    /// Les Miffs de la personne maintenant : les demandes de toute autre sont oubliées.
    func retainOnly(miffIds: Set<String>) {
        knownMiffIds = miffIds
        book.retainOnly(miffIds: miffIds)
        creatorMiffs = creatorMiffs.intersection(miffIds)
        drawn = drawn.intersection(miffIds)
        // Rien d'une Miff que la personne a quittée ne reste : ses demandes.
        let kept = state.views.filter { miffIds.contains($0.key) }
        if kept != state.views {
            state.views = kept
        }
        // Rien d'une Miff quittée ne reste : ni ses demandes, ni qui les a faites.
        let people = state.requesters.filter { entry in miffIds.contains { entry.key.hasPrefix("\($0)|") } }
        if people != state.requesters {
            state.requesters = people
        }
        if let active = activeCreatorMiff, !miffIds.contains(active) {
            activeCreatorMiff = nil
        }
        if let selected = state.selectedMiffId, !miffIds.contains(selected) {
            state.selectedMiffId = nil
        }
        updateTracked()
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

    /// Chaque Miff dessinée est redessinée (l'horloge a avancé : une demande a pu expirer), et le point de l'onglet Profil suit.
    func publish() {
        var views = state.views
        var ids = Set(views.keys).union(drawn)
        if let selected = state.selectedMiffId {
            ids.insert(selected)
        }
        if let tracked {
            ids.insert(tracked)
        }
        for id in ids {
            views[id] = visibleView(id)
        }
        if views != state.views {
            state.views = views
        }
        let dot = hasUnseen(in: tracked.flatMap { views[$0] })
        if dot != state.profileDot {
            state.profileDot = dot
        }
    }

    /// Une demande de la Miff suivie dont la liste n'a pas encore été vue : le point s'allume.
    private func hasUnseen(in view: RequestsView?) -> Bool {
        guard let tracked, let view else {
            return false
        }
        return view.requests.contains { !seen.contains(seenKey(tracked, $0)) }
    }

    func seenKey(_ miffId: String, _ request: PlaceRequest) -> String {
        "\(miffId)|\(request.id)"
    }

    /// Les demandes d'une Miff telles que la liste les dessine : pour un créateur, seulement celles de personnes qui sont encore dans la Miff
    /// (les autres sont en cours de suppression).
    func visibleView(_ miffId: String) -> RequestsView {
        let creator = creatorMiffs.contains(miffId)
        let view = book.view(miffId: miffId, creator: creator)
        guard creator, case .ready(let requests) = view else {
            return view
        }
        return .ready(requests.filter { state.requester(of: $0) != .notMember })
    }

    func updateTracked() {
        let selected = state.selectedMiffId.flatMap { creatorMiffs.contains($0) ? $0 : nil }
        let next = selected ?? activeCreatorMiff
        if next != tracked {
            tracked = next
        }
    }

    private func creatorReadingEnded(miffId: String, answer: MineRead) {
        if let known = knownMiffIds, !known.contains(miffId) {
            return
        }
        switch answer {
        case .loaded(let found):
            onKnown(miffId: miffId, requests: found, live: false)
        case .failed:
            book.readingFailed(miffId: miffId)
        }
        publish()
    }
}
