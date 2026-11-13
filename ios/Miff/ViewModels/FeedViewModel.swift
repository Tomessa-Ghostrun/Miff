import Combine
import Foundation

// SÉCURITÉ, CONFIDENTIALITÉ et COÛT, pour ce fichier :
// - les noms, les lieux, les statuts et surtout les POSITIONS ne vivent en mémoire que le temps dont l'écran a besoin (la feuille
//   « Localiser » est vidée dès que le Feed quitte l'écran), jamais dans un journal, un événement Analytics (le Feed n'en envoie
//   aucun) ni un rapport de plantage ; chaque type qui en porte un cache son contenu dans `description` ;
// - le Feed n'écrit JAMAIS (0 écriture, 0 suppression) : il écoute la Miff (membres, lieux, comme l'écran de la Miff) et ses statuts,
//   et lit UNE position par toucher sur « Localiser » ;
// - les écoutes n'existent QUE pendant que le Feed est à l'écran : l'écran appelle `observe(miffId:)` dans une tâche qui s'arrête
//   quand l'app passe en arrière-plan ou que l'onglet change ; rien n'écoute une Miff que personne ne regarde, et une seule Miff (la
//   Miff active) est écoutée ;
// - la couleur est recalculée à la lecture, puis chaque minute, avec l'horloge : aucune lecture du serveur pour cela.
// Miroir de `FeedViewModel.kt` d'Android (FEED-1 à FEED-3).

/// Ce que montre le Feed pour la Miff active.
enum FeedUiState: Equatable {
    /// Pas encore connu : le squelette (aucune couleur, aucune phrase de sécurité).
    case loading
    /// La Miff n'a pas pu être lue et rien n'est connu : une phrase calme et « Réessayer ».
    case unavailable
    case ready(FeedContent)
}

/// La feuille « Dernière position connue de {Prénom} » (critères L1 à L4). Rien n'en est gardé : elle se ferme quand le Feed quitte l'écran.
enum LocateUiState: Equatable, CustomStringConvertible {
    case idle
    /// Une lecture est en cours pour ce membre : le bouton montre un indicateur.
    case loading(uid: String)
    case shown(uid: String, firstName: String, result: LocateResult)

    var description: String {
        "LocateUiState(redacted)"
    }
}

/// Moi, pour l'en-tête : les initiales et la photo de mon profil (celles de ma fiche de membre).
struct FeedMe: Equatable, CustomStringConvertible {
    var initials = "?"
    var avatarJpeg: Data?

    var description: String {
        "FeedMe(redacted)"
    }
}

/// Ce que l'écoute de la Miff a dit en dernier, pour la Miff écoutée.
enum FeedMiffPiece: Equatable, CustomStringConvertible {
    case loading(String)
    case data(String, MiffSnapshot)
    case failed(String)
    case lost(String)

    var miffId: String {
        switch self {
        case .loading(let id), .data(let id, _), .failed(let id), .lost(let id):
            return id
        }
    }

    var description: String {
        "FeedMiffPiece(redacted)"
    }
}

/// Les délais du Feed, en un seul endroit (des constantes nommées, réglables ; les tests en passent de courts).
struct FeedTimings {
    /// Pauses avant un nouvel essai quand les règles refusent la liste des statuts (le mien n'est pas encore publié) : peu, espacées, puis on attend le retour à l'écran.
    var statusRetryDelaysMillis = [10_000, 30_000, 90_000, 300_000]
    /// Passé ce délai sans aucune réponse des statuts, les cartes cessent d'attendre (neutres, jamais une couleur que personne ne connaît).
    var firstStatusAnswerMillis = 15_000
    /// En ligne, ce que dit le cache n'est pas montré comme le présent : le Feed attend ce délai que le serveur le confirme (une copie
    /// d'hier peut dire « orange » pour quelqu'un qui vient de rentrer : une fausse alerte). Après, la copie est montrée AVEC sa date.
    var cacheGraceMillis = 5_000
    /// « Actualiser » ne lit le serveur qu'une fois par ce délai (15 s) : un geste plus rapproché se termine aussitôt, sans lecture.
    var minRefreshIntervalMillis = 15_000
    /// L'indicateur d'« Actualiser » se termine au plus tard après ce délai (8 s), avec la mention honnête « Dernières informations de… ».
    var refreshTimeoutSeconds: TimeInterval = 8
    /// La pop-in « Pas de connexion » : un essai automatique toutes les 10 s, et le délai sans aucune donnée avant qu'elle vienne
    /// quand le système dit pourtant « en ligne » (8 s).
    var offlinePopin = OfflinePopinTimings()
}

/// Ce dont le Feed a besoin du monde extérieur (des doubles dans les tests).
struct FeedDependencies {
    let directory: MiffDirectory
    let repository: MiffRepository
    let services: FeedServices
    let network: NetworkMonitor
    let awaiting: AwaitingAgreement
    let currentUid: () -> String?
    var clock: () -> Int = { Int(Date().timeIntervalSince1970 * 1000) }
    var zone: () -> TimeZone = { TimeZone.current }
    var sleep: (Int) async -> Void = AppClock.live.pause
    var timings = FeedTimings()
    /// NOTIF-P0 : appelé à chaque réponse DU SERVEUR sur les statuts de la Miff affichée (la carte « deuxième chance » des notifications
    /// en vit ; le planificateur de rappels s'y branchera).
    var onStatusesLoaded: @MainActor (FeedStatusesEvent) -> Void = { _ in }
    /// MON statut et « mon partage est arrêté » à chaque calcul : l'Update en tire la couleur du bouton central et la bulle (D19).
    var onMyStatus: (MyStatusFacts?, Bool) -> Void = { _, _ in }
    /// Les membres de la Miff affichée, DÉJÀ lus par l'écoute : le Profil les réutilise pour les photos de sa carte (zéro lecture de plus).
    var onMembers: (String, [MiffMember]) -> Void = { _, _ in }
    /// Les lieux de la Miff affichée, DÉJÀ lus par l'écoute : la liste des lieux du Profil les réutilise (zéro lecture de plus). Seulement
    /// quand le serveur les a confirmés : une copie du téléphone encore vide n'est jamais donnée (le Profil lit alors la Miff une fois).
    var onPlaces: (String, [Place]) -> Void = { _, _ in }
}

@MainActor
// swiftlint:disable:next type_body_length
final class FeedViewModel: ObservableObject {
    @Published private(set) var uiState: FeedUiState = .loading
    @Published private(set) var directoryState: DirectoryState = .loading {
        didSet { recompute() }
    }
    @Published var locate: LocateUiState = .idle
    /// Vrai pendant une relecture forcée d'« Actualiser » : l'indicateur.
    @Published var refreshing = false
    @Published private(set) var me = FeedMe()
    /// Monte à « Réessayer » : l'écran relance son observation.
    @Published private(set) var restarts = 0

    let dependencies: FeedDependencies
    /// La pop-in « Pas de connexion » du Feed quand rien n'est à montrer (ni contenu, ni copie du cache).
    let offlineRetry: OfflineRetryModel
    var directory: MiffDirectory { dependencies.directory }

    var piece: FeedMiffPiece? { didSet { recompute() } }
    var statusView: StatusesView = .waiting { didSet { recompute() } }
    var offline = false { didSet { recompute() } }
    var nowMillis = 0 { didSet { recompute() } }
    var waiting: Set<String> = [] { didSet { recompute() } }
    var cacheGraceOver = false { didSet { recompute() } }
    var staleAfterRefresh = false { didSet { recompute() } }

    /// La Miff que l'observation en cours écoute, et son numéro (une réponse attendue à travers un changement n'est jamais appliquée).
    var observedMiffId: String?
    var generation = 0
    var lostReported = false
    var statusRestarts = 0
    var statusTask: Task<Void, Never>?
    var lastStatusDecision: StatusDecision?
    var lastRefreshAtMillis: Int?
    var locateTask: Task<Void, Never>?
    private var subscriptions: [AnyCancellable] = []
    private var directoryRefreshing = false
    private var directoryReadStartedOffline = false
    private var directoryReplayPending = false

    struct StatusDecision: Equatable {
        let shouldRead: Bool
        let restart: Int
    }

    init(dependencies: FeedDependencies) {
        self.dependencies = dependencies
        offlineRetry = OfflineRetryModel(
            network: dependencies.network,
            sleep: dependencies.sleep,
            timings: dependencies.timings.offlinePopin
        )
        offlineRetry.onRetry = { [weak self] kind in
            self?.offlineRetryRequested(kind)
        }
        nowMillis = dependencies.clock()
        waiting = dependencies.awaiting.waiting
        directoryState = dependencies.directory.state
        subscriptions.append(
            dependencies.directory.$state.sink { [weak self] state in
                self?.directoryState = state
            }
        )
        subscriptions.append(
            dependencies.awaiting.$waiting.sink { [weak self] set in
                self?.waiting = set
                self?.reconsiderStatuses()
            }
        )
        recompute()
    }

    /// « Réessayer » sur le Feed lui-même : les Miffs sont relues et les écoutes rattachées de nouveau.
    func retry() {
        lostReported = false
        restarts += 1
        refreshDirectory()
    }

    /// Relit les Miffs, une lecture à la fois : un geste ou un essai automatique pendant qu'une lecture est en cours n'en empile pas une autre.
    /// Une seule exception : la lecture en cours a COMMENCÉ hors connexion (elle ne répondra que par la copie du téléphone) et le réseau est
    /// revenu depuis. Elle est alors rejouée UNE fois à sa fin (drapeau « rejouer »), sans quoi l'écran resterait sur la copie.
    private func refreshDirectory() {
        guard !directoryRefreshing else {
            if directoryReadStartedOffline, dependencies.network.isOnline() {
                directoryReplayPending = true
            }
            return
        }
        directoryRefreshing = true
        let directory = self.directory
        Task {
            var again = true
            while again {
                self.directoryReadStartedOffline = !self.dependencies.network.isOnline()
                self.directoryReplayPending = false
                await directory.refresh()
                again = self.directoryReplayPending
            }
            self.directoryRefreshing = false
        }
    }

    /// La pop-in « Pas de connexion » demande une relecture (le réseau est là, c'est garanti par la pop-in). Un geste ou un retour du
    /// réseau relancent tout ; l'essai automatique reste léger : les écoutes en cours se rattachent seules, seules les Miffs jamais
    /// lues ou une écoute en échec sont relancées.
    private func offlineRetryRequested(_ kind: OfflineRetryKind) {
        switch kind {
        case .manual, .networkBack:
            retry()
        case .automatic:
            guard case .ready = directoryState else {
                refreshDirectory()
                return
            }
            if case .failed? = piece {
                retry()
            }
        }
    }

    /// Suit le réseau pour la pop-in tant que le Feed est à l'écran (l'écran lance cette tâche, annulée en quittant l'onglet ou l'app).
    func watchConnectivity() async {
        await offlineRetry.watch()
    }

    /// Premium : une autre Miff a été choisie dans le sélecteur.
    func onSelect(_ miffId: String) {
        directory.select(miffId: miffId)
    }

    // MARK: - Le calcul de ce que montre le Feed

    private func recompute() {
        let next = compute()
        if next != uiState {
            uiState = next
        }
        offlineRetry.setNeed(offlineNeed)
        if case .ready(let content) = next {
            dependencies.onMyStatus(content.myFacts, content.myLabel == .sharingStopped)
        }
    }

    private func compute() -> FeedUiState {
        guard case .ready(let ready) = directoryState, let activeId = ready.activeId else {
            return .loading
        }
        guard let piece, piece.miffId == activeId else {
            return .loading
        }
        switch piece {
        case .data(_, let snapshot):
            // Le squelette reste tant que les statuts n'ont pas répondu : une couleur que personne ne connaît n'est jamais dessinée.
            if statusView == .waiting {
                return .loading
            }
            // En ligne, une copie que le serveur n'a pas encore confirmée n'est pas le présent : aucune alerte (ni aucun vert) n'en est
            // tirée pendant un instant.
            let unconfirmed = isFromCache(statusView)
            if unconfirmed && !(offline || staleAfterRefresh) && !cacheGraceOver {
                return .loading
            }
            return .ready(content(for: snapshot, activeId: activeId))
        case .failed:
            return .unavailable
        case .loading, .lost:
            return .loading
        }
    }

    /// Le Feed n'a RIEN à montrer (ni contenu, ni copie du cache) : c'est le seul cas où la pop-in « Pas de connexion » a un sens. Avec
    /// la Miff connue (même en cache) le Feed garde son comportement : « Dernières informations de… » et la mention hors connexion.
    private var offlineNeed: OfflineNeed {
        switch directoryState {
        case .loading:
            return .loading
        case .unavailable:
            return .failed
        case .ready(let ready):
            guard let activeId = ready.activeId else {
                return .none
            }
            guard let piece, piece.miffId == activeId else {
                return .loading
            }
            switch piece {
            case .data, .lost:
                return .none
            case .loading:
                return .loading
            case .failed:
                return .failed
            }
        }
    }

    private func isFromCache(_ view: StatusesView) -> Bool {
        if case .loaded(_, let fromCache) = view {
            return fromCache
        }
        return false
    }

    private func content(for snapshot: MiffSnapshot, activeId: String) -> FeedContent {
        FeedBuilder.build(
            input: FeedInput(
                miffId: snapshot.miff.id,
                miffName: snapshot.miff.name,
                joinCode: snapshot.miff.joinCode,
                members: snapshot.members,
                places: snapshot.places,
                myUid: dependencies.currentUid() ?? "",
                statuses: statusView,
                waitingAgreement: waiting.contains(activeId),
                // Pas de réseau, OU un « Actualiser » que le serveur n'a pas répondu : ce qui est montré est daté, jamais présenté comme le présent.
                offline: offline || staleAfterRefresh
            ),
            nowMillis: nowMillis,
            zone: dependencies.zone()
        )
    }

    // MARK: - L'observation (tant que le Feed est à l'écran)

    /// Lit ce que montre le Feed tant que l'appelant reste : la Miff affichée (membres, lieux), ses statuts, le réseau et la minute.
    /// Tout s'arrête quand l'app passe en arrière-plan, que l'onglet change ou que la Miff affichée change (l'écran relance alors
    /// l'observation) ; la feuille « Localiser » est alors fermée (l'écran ne garde aucune position).
    func observe(miffId: String?) async {
        generation += 1
        let mine = generation
        observedMiffId = miffId
        // Une lecture des statuts d'une observation précédente ne répond plus à personne.
        statusTask?.cancel()
        statusTask = nil
        lastStatusDecision = nil
        if let miffId {
            piece = .loading(miffId)
            // Ce qui a été dit d'une autre Miff n'est jamais dessiné sur celle-ci, ni l'attente de la confirmation du serveur, ni la mention « Dernières informations de… ».
            statusView = .waiting
            cacheGraceOver = false
            staleAfterRefresh = false
        } else {
            piece = nil
        }
        await withTaskGroup(of: Void.self) { group in
            group.addTask { await self.watchNetwork() }
            group.addTask { await self.tickEachMinute() }
            if let miffId {
                group.addTask { await self.watchMiff(miffId, generation: mine) }
            }
        }
        if generation == mine {
            statusTask?.cancel()
            statusTask = nil
            lastStatusDecision = nil
            observedMiffId = nil
        }
        locateTask?.cancel()
        locate = .idle
    }

    private func watchNetwork() async {
        for await online in dependencies.network.onlineChanges() {
            let wasOffline = offline
            offline = !online
            if online && wasOffline {
                rereadMiffsServedFromCache()
            }
        }
    }

    /// Le réseau revient alors que les Miffs affichées viennent du cache du téléphone (ou n'ont pas pu être lues) : elles sont relues UNE
    /// fois auprès du serveur, sans quoi l'écran resterait sur la copie jusqu'à la prochaine ouverture de l'app.
    private func rereadMiffsServedFromCache() {
        switch directoryState {
        case .ready(let ready) where ready.fromCache:
            refreshDirectory()
        case .unavailable, .loading:
            // « loading » : la lecture en cours a commencé hors connexion ; elle est rejouée à sa fin (drapeau « rejouer »).
            refreshDirectory()
        case .ready:
            break
        }
    }

    /// La couleur est recalculée avec l'horloge, une fois par minute, alignée sur la minute : aucune lecture du serveur.
    private func tickEachMinute() async {
        while !Task.isCancelled {
            let current = dependencies.clock()
            nowMillis = current
            await dependencies.sleep(FeedMillis.minute - current % FeedMillis.minute)
        }
    }

    private func watchMiff(_ miffId: String, generation mine: Int) async {
        for await observation in dependencies.repository.observe(miffId: miffId) {
            await handle(miffId, observation, generation: mine)
        }
    }

    private func handle(_ miffId: String, _ observation: MiffObservation, generation mine: Int) async {
        guard generation == mine else {
            return
        }
        switch observation {
        case .loading:
            break
        case .data(let snapshot):
            lostReported = false
            piece = .data(miffId, snapshot)
            dependencies.onMembers(miffId, snapshot.members)
            if !snapshot.fromCache {
                dependencies.onPlaces(miffId, snapshot.places)
            }
            updateMe(snapshot)
            reconsiderStatuses()
        case .accessLost:
            await handleAccessLost(miffId, generation: mine)
        case .failed:
            if case .data? = piece {
                return
            }
            piece = .failed(miffId)
        }
    }

    private func handleAccessLost(_ miffId: String, generation mine: Int) async {
        if lostReported {
            return
        }
        lostReported = true
        var name: String?
        if case .data(_, let snapshot)? = piece {
            name = snapshot.miff.name
        }
        piece = .lost(miffId)
        // Le message unique et le nettoyage sont ceux de l'annuaire (ticket Miff §5.7) ; seule la parole du serveur compte. L'annuaire
        // retire ensuite cette Miff de la liste, ce qui arrête CETTE observation : le nettoyage ne doit pas en être coupé (tâche à part).
        let directory = self.directory
        let confirmed = await Task { await directory.onAccessLost(miffId: miffId, name: name) }.value
        guard generation == mine else {
            return
        }
        if !confirmed {
            lostReported = false
            piece = .failed(miffId)
        }
    }

    private func updateMe(_ snapshot: MiffSnapshot) {
        guard let uid = dependencies.currentUid(), let mine = snapshot.members.first(where: { $0.uid == uid }) else {
            return
        }
        let next = FeedMe(
            initials: Initials.of(first: mine.firstName, last: mine.lastName),
            avatarJpeg: mine.avatarJpeg
        )
        if next != me {
            me = next
        }
    }
}
