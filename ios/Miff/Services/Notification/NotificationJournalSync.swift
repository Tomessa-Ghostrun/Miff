import Foundation

// CNC-I (ticket docs/tickets/centre-notifications-complet.md §8.4) : la LECTURE du journal du serveur (`users/{uid}/notifications/{entryId}`) qui
// remplit le centre avec ce que le téléphone n'a pas reçu (un message expiré après 2 heures, l'app arrêtée de force, l'autorisation retirée, l'app
// fermée : sur iPhone, AUCUN message n'entrait jamais dans le centre). Miroir de `NotificationJournalSync.kt` d'Android.
//
// SÉCURITÉ et CONFIDENTIALITÉ : seul le propriétaire lit ou supprime ses entrées (règles du serveur) ; une entrée ne porte que des identifiants et des
// dates, jamais un prénom, une photo, un lieu ni une position ; chaque champ venu du réseau est validé par `NotificationJournal.parse` avant d'être
// utilisé ; rien ici n'est journalisé, envoyé comme événement, ni gardé dans le message d'une erreur.
// COÛT : AUCUN écouteur permanent, jamais. UNE requête depuis un curseur (2 minutes avant l'entrée la plus récente déjà vue), 100 entrées au plus par
// page et 2 pages au plus, au premier plan, à l'ouverture du centre et à la réception d'un message ; au plus une par minute hors message (10
// secondes après un message) ; une réponse vide coûte UNE lecture. Une requête que les règles refusent (le serveur n'est pas encore déployé) n'est
// pas renouvelée avant 30 minutes.
// CALME : tout échec (hors ligne, refus, trop lent, entrée qui ne ressemble à rien) ne montre RIEN ; le centre continue avec les lignes qu'il a, et
// l'occasion suivante réessaie. Sans passerelle (build sans le drapeau `NOTIF_JOURNAL`), rien n'est lu ni supprimé : la classe n'a aucun effet.

/// Ce qu'une requête du journal a donné.
enum JournalRead: Equatable {
    case loaded([JournalEntry])
    /// Hors ligne, trop lent, ou toute autre panne qui n'est pas un refus : rien n'est connu, rien n'est perdu, le curseur ne bouge pas.
    case unavailable
    /// Les règles refusent la requête (le serveur n'est pas encore déployé, ou App Check dit non) : pas de nouvelle tentative avant un moment.
    case refused
}

/// Les trois opérations sur le journal du serveur (un double dans les tests).
protocol NotificationJournalGateway: AnyObject {
    /// Les entrées plus récentes que `sinceMillis` (toutes, sur 30 jours, quand nil), 2 pages au plus. Ne lève jamais.
    func read(uid: String, sinceMillis: Int?) async -> JournalRead

    /// Supprime les entrées `entryIds` de `uid` sur le serveur : demandé puis oublié (hors ligne, le téléphone garde l'ordre et l'envoie plus tard).
    func delete(uid: String, entryIds: [String])

    /// « Tout effacer » et la suppression du compte : supprime TOUTES les entrées de `uid` (une liste de 100, un lot, encore, jusqu'à ce qu'elle soit
    /// vide). Au mieux, bornée, ne lève jamais.
    func deleteAll(uid: String) async
}

/// Où le curseur du journal est gardé : la date la plus récente vue, pour UN compte (des identifiants et une date, jamais un nom). Effacé avec la session.
protocol NotificationJournalCursorStoring: AnyObject {
    func cursor(uid: String) -> Int?
    func setCursor(_ millis: Int, uid: String)
    func clear()
}

/// Pourquoi une synchronisation est demandée : les occasions du ticket (§8.4).
enum JournalSyncReason {
    case foreground
    case centerOpened
    case pushReceived
}

/// Amène les lignes du journal dans le centre. Une requête à la fois (une seconde demandée entre-temps est laissée : celle qui tourne lit tout) ;
/// chaque occasion est espacée de la précédente.
@MainActor
final class NotificationJournalSync {
    /// « Une par minute au plus » hors message (ticket §8.4).
    static let minGapMillis = 60_000
    /// Un message peut amener une requête plus tôt, mais jamais une rafale.
    static let pushGapMillis = 10_000
    /// Après un échec pour joindre le serveur : une nouvelle tentative est permise après ce délai.
    static let offlineGapMillis = 10_000
    /// Après un refus des règles : le serveur n'est probablement pas déployé, on n'insiste pas (une requête refusée se paie comme une lecture).
    static let refusedBackoffMillis = 30 * 60 * 1000

    private let gateway: NotificationJournalGateway?
    private let cursors: NotificationJournalCursorStoring
    private let center: NotificationCenterService
    private let currentUid: () -> String?
    private let nowMillis: () -> Int
    /// Dit si cette session peut encore servir à lire (faux pendant la fin de session). Branché par `AppEnvironment` une fois le gestionnaire de session créé.
    var sessionValid: (String) -> Bool = { _ in true }
    private var running = false
    private var nextAllowedAtMillis = 0
    /// Les clés des lignes supprimées dans cette session : une entrée que le serveur n'a pas encore supprimée (hors ligne) ne doit pas ramener sa ligne
    /// dans le recouvrement de la requête suivante. En mémoire seulement.
    private var deleted: Set<String> = []

    init(
        gateway: NotificationJournalGateway?,
        cursors: NotificationJournalCursorStoring,
        center: NotificationCenterService,
        currentUid: @escaping () -> String?,
        nowMillis: @escaping () -> Int = NotificationClock.wallMillis
    ) {
        self.gateway = gateway
        self.cursors = cursors
        self.center = center
        self.currentUid = currentUid
        self.nowMillis = nowMillis
    }

    /// La lecture du journal est-elle allumée dans ce build ?
    var isEnabled: Bool {
        gateway != nil
    }

    /// Une synchronisation. Vrai quand le journal a été lu et que ses lignes sont dans le centre (même s'il n'y en avait aucune). Faux quand rien n'a été
    /// fait : éteint, personne de connecté, trop tôt, hors ligne, refusé. Ne lève jamais, ne montre jamais rien.
    @discardableResult
    func sync(_ reason: JournalSyncReason) async -> Bool {
        guard let gateway, let uid = currentUid(), sessionValid(uid), !running else {
            return false
        }
        running = true
        defer { running = false }
        let startedAt = nowMillis()
        // Une horloge remise en arrière ne bloque pas pour toujours : au-delà du plus long délai, l'attente est oubliée.
        if startedAt < nextAllowedAtMillis, nextAllowedAtMillis - startedAt <= Self.refusedBackoffMillis {
            return false
        }
        let cursor = cursors.cursor(uid: uid)
        let read = await gateway.read(uid: uid, sinceMillis: NotificationJournal.since(cursorMillis: cursor))
        switch read {
        case .refused:
            nextAllowedAtMillis = startedAt + Self.refusedBackoffMillis
            return false
        case .unavailable:
            // Hors ligne : réessayer bientôt ne coûte rien (prochain premier plan ou ouverture), mais pas en boucle.
            nextAllowedAtMillis = startedAt + Self.offlineGapMillis
            return false
        case .loaded(let entries):
            nextAllowedAtMillis = startedAt + (reason == .pushReceived ? Self.pushGapMillis : Self.minGapMillis)
            // La personne a pu se déconnecter, ou une autre se connecter, pendant la lecture : rien n'est écrit pour elle.
            guard currentUid() == uid, sessionValid(uid) else {
                return false
            }
            let now = nowMillis()
            let items = entries
                .compactMap { NotificationJournal.item(for: $0, myUid: uid, nowMillis: now, firstSync: cursor == nil) }
                .filter { !deleted.contains($0.id) }
            center.record(contentsOf: items)
            if let next = NotificationJournal.nextCursor(previous: cursor, entries: entries) {
                cursors.setCursor(next, uid: uid)
            }
            return true
        }
    }

    /// La personne a supprimé des lignes (une, ou « Tout effacer ») : les entrées d'où elles viennent sont supprimées sur le serveur aussi, pour
    /// qu'elles ne reviennent pas après une réinstallation (CNC-15). Les lignes qui n'ont jamais eu d'entrée (un rappel, une demande, « Depuis ta
    /// dernière visite », une ligne écrite par un serveur sans journal) n'ont rien à supprimer.
    func deleteOnServer(centerIds: [String]) {
        guard let gateway else {
            return
        }
        deleted.formUnion(centerIds)
        guard let uid = currentUid() else {
            return
        }
        let entryIds = Set(centerIds.compactMap { NotificationJournal.entryId(ofCenterId: $0) }).sorted()
        if !entryIds.isEmpty {
            gateway.delete(uid: uid, entryIds: entryIds)
        }
    }

    /// « Tout effacer » : TOUTES les entrées du compte sont supprimées sur le serveur, y compris celles que ce téléphone n'a jamais vues. Au mieux
    /// (hors ligne : rien ; celles que ce téléphone connaissait ont déjà été demandées une à une, et le téléphone garde ces ordres). Ne lève jamais.
    func deleteAllOnServer() async {
        guard let gateway, let uid = currentUid() else {
            return
        }
        await gateway.deleteAll(uid: uid)
    }

    /// La déconnexion et la suppression du compte : le curseur s'en va (le compte suivant repart de rien).
    func purge() {
        cursors.clear()
        nextAllowedAtMillis = 0
        deleted = []
    }
}
