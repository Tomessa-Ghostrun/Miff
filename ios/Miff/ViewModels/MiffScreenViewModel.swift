import Foundation
#if canImport(Combine)
import Combine
#endif

// SÉCURITÉ, pour ce fichier : le nom, le code, les noms et les photos des membres sont des données personnelles / un secret.
// Ils vivent en mémoire dans cet état seulement : jamais dans un journal, un événement Analytics (aucun n'est envoyé depuis
// cet écran) ni un rapport de plantage.

enum MiffScreenStatus: Equatable {
    case loading
    case ready
    /// Le serveur a dit « non » ou « cette Miff n'existe pas » : le message unique de la décision QM8 prend le relais (voir
    /// `MiffDirectory`).
    case accessLost
    /// Autre chose a échoué à la lecture et il n'y a rien à montrer : « Réessayer ».
    case unavailable
}

/// Quelle question pose la fenêtre de « Partager mon statut ».
enum SharingDialog: String, Identifiable {
    case stop
    case resume

    var id: String {
        rawValue
    }
}

struct MiffScreenState: CustomStringConvertible {
    var status: MiffScreenStatus = .loading
    var miffId = ""
    /// Le nom tel que le serveur l'a (le titre de l'écran).
    var name = ""
    var joinCode = ""
    var isOwner = false
    var members: [MiffMember] = []
    var places: [MiffPlace] = []
    var myUid = ""
    /// Est-ce que je partage mon statut avec cette Miff (ma propre fiche) ? nil tant que ma fiche n'est pas lue.
    var sharing: Bool?
    /// Ce qui est tapé dans le champ du nom (le créateur seulement).
    var nameInput = ""
    var nameLimitReached = false
    var saving = false
    var sharingDialog: SharingDialog?
    var sharingBusy = false
    var message: ActionMessage?
    /// Le téléphone n'a pas de réseau : la lecture continue depuis le cache, ce qui écrit est grisé (critères G8, H5).
    var offline = false
    /// Ce qui est montré vient du cache du téléphone et le serveur n'a pas encore répondu.
    var fromCache = false
    var nameSaved = false
    /// Monte à « Réessayer » : l'écran relance l'écoute.
    var restart = 0

    /// « Enregistrer » n'est actif que si le nom a changé ET est valide (critère G3).
    var canSaveName: Bool {
        isOwner && !saving && !offline && MiffName.isValid(nameInput) && MiffName.stored(nameInput) != name
    }

    /// À 15 membres ou plus, « Partager le code » est remplacé par une phrase calme (décision QM5, un plafond doux).
    var isFull: Bool {
        members.count >= MiffSharingConsent.softMemberLimit
    }

    var canWrite: Bool {
        !offline && !saving && !sharingBusy
    }

    var description: String {
        "MiffScreenState(redacted)"
    }
}

/// Ce dont l'écran d'une Miff a besoin.
struct MiffScreenDependencies {
    let repository: MiffRepository
    let network: NetworkMonitor
    let currentUid: () -> String?
    /// L'annuaire interroge le serveur et, SEULEMENT si la perte est confirmée, nettoie et met le message unique en file.
    /// Vrai = confirmée. Faux = le serveur ne l'a pas dit (ou n'a pas pu être joint) : ce n'est PAS un retrait, l'écran propose
    /// « Réessayer » et personne n'est informé de rien.
    let onAccessLost: (String, String?) async -> Bool
    let onNameChanged: () async -> Void
}

/// L'écran d'une Miff, pour son créateur (F) et pour un membre (G) : le nom (le créateur peut le changer), le code à
/// partager, les proches, « Partager mon statut » (arrêter / reprendre à tout moment, critères K5 et K6), les lieux. Aucun
/// statut, aucune batterie, aucune position, aucune photo d'update n'apparaît ici (critère G7). Retirer, quitter, supprimer
/// et renouveler appartiennent à MIFF-4.
///
/// La lecture est en direct (trois écoutes, voir `FirestoreMiffObserver`). Écrire exige le serveur : sans réseau, les boutons
/// sont gris et rien n'est mis en attente (critère H5). Une perte d'accès est confiée à `onAccessLost` ; une coupure réseau
/// n'en est pas une (critère D3).
@MainActor
final class MiffScreenViewModel: ObservableObject {
    @Published private(set) var state: MiffScreenState

    private let miffId: String
    private let dependencies: MiffScreenDependencies
    private var lostReported = false

    init(miffId: String, dependencies: MiffScreenDependencies) {
        self.miffId = miffId
        self.dependencies = dependencies
        var first = MiffScreenState()
        first.miffId = miffId
        first.myUid = dependencies.currentUid() ?? ""
        state = first
    }

    /// Lit la Miff en direct jusqu'à ce que l'appelant arrête (l'écran l'appelle dans une tâche annulée quand l'app passe en
    /// arrière-plan ou que l'écran disparaît : les trois écoutes Firestore et la veille du réseau s'arrêtent, rien n'écoute
    /// une Miff que personne ne regarde).
    func observe() async {
        let network = dependencies.network
        let watcher = Task { [weak self] in
            for await online in network.onlineChanges() {
                self?.state.offline = !online
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
        case .data(let snapshot):
            lostReported = false
            apply(snapshot)
        case .accessLost:
            await handleLoss()
        case .failed:
            if state.status != .ready {
                state.status = .unavailable
            }
        }
    }

    private func handleLoss() async {
        if lostReported {
            return
        }
        lostReported = true
        let known = state.name.isEmpty ? nil : state.name
        // Rien de la Miff ne reste à l'écran pendant que le serveur est interrogé (critère D7).
        state = cleared(.accessLost)
        let confirmed = await dependencies.onAccessLost(miffId, known)
        if confirmed {
            return
        }
        lostReported = false
        if Task.isCancelled {
            // L'écran a quitté le premier plan (ou pivoté) pendant que le serveur était interrogé : rien n'a été décidé. Sans
            // ceci, la perte resterait « signalée » sur une page blanche et la prochaine serait ignorée ; les écoutes sont
            // rattachées au retour de l'écran et le redisent.
            state = cleared(.loading)
        } else {
            // Le serveur ne l'a pas dit : un doute n'est pas un retrait. « Réessayer », aucun message (ticket §5.7, D3).
            state = cleared(.unavailable)
        }
    }

    private func cleared(_ status: MiffScreenStatus) -> MiffScreenState {
        var fresh = MiffScreenState()
        fresh.status = status
        fresh.miffId = miffId
        fresh.offline = state.offline
        fresh.restart = state.restart
        return fresh
    }

    /// « Réessayer » après une lecture qui a échoué.
    func retry() {
        guard state.status == .unavailable else {
            return
        }
        lostReported = false
        state.status = .loading
        state.restart += 1
    }

    private func apply(_ snapshot: MiffSnapshot) {
        let uid = dependencies.currentUid() ?? ""
        var next = state
        // Ce que la personne est en train de taper n'est jamais écrasé par un changement qui arrive pendant qu'elle tape.
        let untouched = next.status != .ready || next.nameInput == next.name
        let saved = snapshot.miff.name
        next.status = .ready
        next.name = saved
        next.joinCode = snapshot.miff.joinCode
        next.isOwner = snapshot.miff.ownerUid == uid
        next.members = snapshot.members
        next.places = snapshot.places
        next.myUid = uid
        next.sharing = snapshot.members.first { $0.uid == uid }?.sharingEnabled
        if untouched {
            next.nameInput = saved
        }
        next.fromCache = snapshot.fromCache
        state = next
    }

    // MARK: - Nom

    func onNameInputChanged(_ typed: String) {
        guard state.isOwner, !state.saving else {
            return
        }
        let limited = MiffName.limit(typed)
        var next = state
        next.nameInput = limited.text
        next.nameLimitReached = limited.limitReached
        next.nameSaved = false
        next.message = nil
        state = next
    }

    func onSaveName() {
        guard state.canSaveName else {
            return
        }
        guard dependencies.network.isOnline() else {
            state.message = .offline
            return
        }
        state.saving = true
        state.message = nil
        state.nameSaved = false
        let code = state.joinCode
        let typed = state.nameInput
        Task {
            await self.rename(code: code, name: typed)
        }
    }

    private func rename(code: String, name: String) async {
        let result = await dependencies.repository.rename(miffId: miffId, code: code, name: name)
        switch result {
        case .done:
            state.saving = false
            state.nameSaved = true
            await dependencies.onNameChanged()
        case .failed(let failure):
            state.saving = false
            state.message = ActionMessage(failure: failure)
        }
    }

    // MARK: - Partager mon statut

    /// La ligne « Partager mon statut » a été touchée : la question vient d'abord (critères K5 et K6).
    func onSharingRequested() {
        guard let sharing = state.sharing, state.canWrite else {
            return
        }
        state.sharingDialog = sharing ? .stop : .resume
        state.message = nil
    }

    func onSharingDialogDismissed() {
        guard !state.sharingBusy else {
            return
        }
        state.sharingDialog = nil
        state.message = nil
    }

    func onSharingConfirmed() {
        guard let dialog = state.sharingDialog, !state.sharingBusy else {
            return
        }
        guard dependencies.network.isOnline() else {
            state.message = .offline
            return
        }
        guard let uid = dependencies.currentUid() else {
            return
        }
        state.sharingBusy = true
        state.message = nil
        Task {
            await self.applySharing(uid: uid, enabled: dialog == .resume)
        }
    }

    private func applySharing(uid: String, enabled: Bool) async {
        let result = await dependencies.repository.setSharing(miffId: miffId, uid: uid, enabled: enabled)
        switch result {
        case .done:
            // L'interrupteur ne bouge qu'avec le serveur : la fiche en direct apporte le nouvel état (critère H1).
            state.sharingBusy = false
            state.sharingDialog = nil
        case .failed(let failure):
            state.sharingBusy = false
            state.message = ActionMessage(failure: failure)
        }
    }
}
