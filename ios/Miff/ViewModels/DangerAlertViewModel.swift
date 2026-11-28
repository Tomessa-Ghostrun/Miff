#if ALERT_DANGER
import Combine
import Foundation

// CONFIDENTIALITÉ, pour ce fichier : les numéros des membres viennent des fiches que le Feed lit déjà (`members/{uid}.phone`, visibles de tous les membres
// de la Miff) : UNE lecture ponctuelle des membres de la Miff d'origine à l'ouverture de l'écran « avertis », rien d'autre. Aucune permission « Contacts »,
// aucun carnet d'adresses. Rien n'est journalisé ni envoyé comme événement. La région de la LOCALE du téléphone (jamais la position) choisit les numéros
// d'urgence. Miroir d'`AlertViewModel.kt` d'Android.

/// Ce que montre la liste de l'écran « avertis ». Les numéros d'urgence sont TOUJOURS là, dès le premier instant (revue indépendante de l'Android :
/// jamais une liste vide pendant que les proches chargent).
struct DangerCallState: Equatable, CustomStringConvertible {
    var list: AlertCallList
    var selected: AlertDialTarget
    /// Les membres n'ont pas pu être lus (hors ligne sans copie) : seuls les services d'urgence sont proposés, avec une phrase calme.
    var contactsUnavailable: Bool
    /// Les proches sont en cours de lecture : les numéros d'urgence sont déjà montrés et appelables.
    var loading: Bool

    var description: String {
        "DangerCallState(redacted)"
    }
}

/// L'écran « avertis » ouvert : pour quelle Miff, et dans quelle variante calme.
struct DangerSentRoute: Equatable {
    /// La Miff d'origine ; nil quand aucune Miff n'est connue (seuls les numéros d'urgence sont montrés).
    var miffId: String?
    /// Je ne partage pas (ou plus) avec cette Miff : l'alerte n'est PAS partie.
    var stopped = false
    /// Aucune alerte ne peut partir maintenant (les Miffs ne sont pas connues, la caméra n'est pas disponible) : une phrase calme et les numéros d'urgence, rien d'autre.
    var unavailable = false
}

@MainActor
final class DangerAlertViewModel: ObservableObject {
    let controller: DangerAlertController
    private let local: DangerAlertLocalStore
    private let members: DangerAlertMembersReader
    private let myUid: () -> String?
    /// La région de la locale du TÉLÉPHONE (ISO 3166), jamais la position.
    private let region: () -> String?
    private let signal: DangerAlertSignal

    /// La bulle bleue de l'onboarding se montre tant que ceci est faux (ticket 7.1, AD7). Local au téléphone.
    @Published private(set) var hintDone: Bool
    @Published private(set) var call: DangerCallState
    /// L'écran « avertis » ouvert, ou nil.
    @Published var sentRoute: DangerSentRoute? {
        didSet { signal.sentScreenOpen = sentRoute != nil }
    }

    /// Une ligne calme dite une fois (fin de l'alerte, fin refusée, abandon non confirmé), retirée d'elle-même par l'hôte.
    @Published var notice: String? {
        didSet { signal.noticeShown = notice != nil }
    }
    /// Les noms des Miffs de la personne (par identifiant), tenus par l'hôte : jamais gardés ailleurs qu'en mémoire.
    var miffNames: [String: String] = [:]
    private var subscription: AnyCancellable?
    private var callMiffId: String?
    private var callTask: Task<Void, Never>?
    /// La personne a choisi une ligne elle-même : ce qui arrive ensuite ne remplace jamais son choix.
    private var chosen = false

    init(
        controller: DangerAlertController,
        local: DangerAlertLocalStore,
        members: DangerAlertMembersReader,
        myUid: @escaping () -> String?,
        region: @escaping () -> String?,
        signal: DangerAlertSignal
    ) {
        self.signal = signal
        self.controller = controller
        self.local = local
        self.members = members
        self.myUid = myUid
        self.region = region
        hintDone = local.hintDone()
        call = Self.emergencyOnly(region: region(), loading: true, unavailable: false)
        // La coquille lit l'état de l'alerte à travers ce modèle : chaque changement du contrôleur se répercute.
        subscription = controller.$state.sink { [weak self] state in
            self?.objectWillChange.send()
            self?.signal.inProgress = state != .idle
            self?.signal.miffId = state.miffId
        }
        controller.onEvent = { [weak self] event in
            self?.handle(event)
        }
        signal.inProgress = controller.state != .idle
        signal.miffId = controller.state.miffId
    }

    private func handle(_ event: DangerAlertEvent) {
        switch event {
        case .ended(let miffId):
            // Un nom de Miff pas encore connu (après un relancement) : « tes proches », jamais un blanc.
            if let name = dangerNamed(miffNames[miffId]) {
                notice = L10n.format("alert_ended_notice", name)
            } else {
                notice = L10n.string("alert_ended_notice_no_name")
            }
        case .endNotSent:
            notice = L10n.string("alert_end_not_sent")
        case .notSent(let miffId, _):
            sentRoute = DangerSentRoute(miffId: miffId, stopped: true)
        }
    }

    /// Ouvre l'écran « avertis » (rouvert par la bannière, ou à l'instant des 5 secondes).
    func openSent(miffId: String?, stopped: Bool = false, unavailable: Bool = false) {
        sentRoute = DangerSentRoute(miffId: miffId, stopped: stopped, unavailable: unavailable)
    }

    /// Les numéros d'urgence seuls, présélection 112 (ou son équivalent régional) : l'état de départ, et celui d'une Miff sans proche joignable.
    private static func emergencyOnly(region: String?, loading: Bool, unavailable: Bool) -> DangerCallState {
        let list = AlertCallList.build(members: [], myUid: "", region: region)
        return DangerCallState(
            list: list, selected: list.preselected, contactsUnavailable: unavailable, loading: loading
        )
    }

    /// La personne a commencé à maintenir le bouton (le geste est découvert), a publié une update après la livraison ou a déclaré une alerte : la
    /// bulle a fini pour de bon.
    func onHintDone() {
        guard !hintDone else {
            return
        }
        local.markHintDone()
        hintDone = true
    }

    /// Les 5 secondes ont été tenues sur l'Update, dans la Miff `miffId` (celle que le Feed montre).
    func onTriggered(miffId: String) -> DangerTriggerResult {
        onHintDone()
        return controller.trigger(miffId: miffId)
    }

    /// L'app s'ouvre : une alerte en route continue, une alerte active revient (le pointeur du téléphone).
    func restore() {
        controller.restore()
    }

    /// Les Miffs de la personne sont connues : un NOUVEAU téléphone lit mon alerte de chaque Miff UNE fois (1 lecture par Miff) et adopte une alerte en
    /// cours. Une liste ne termine JAMAIS une alerte : une Miff dont la lecture échoue manque dans la liste sans être perdue.
    func onMiffsKnown(_ miffIds: [String]) async {
        await controller.adoptFromServer(miffIds: miffIds)
    }

    /// Chaque minute et au retour au premier plan : une alerte arrivée à sa date disparaît ; une alerte active est vérifiée UNE fois auprès du serveur.
    func onForeground() async {
        controller.tick()
        await controller.verifyActive()
    }

    // MARK: - L'écran « avertis »

    /// L'écran s'ouvre pour `miffId` : les numéros d'urgence sont déjà là ; les proches de CETTE Miff sont lus une fois (et relus si la Miff d'origine
    /// est une autre).
    func loadCallList(miffId: String?) {
        guard let miffId else {
            // Aucune Miff connue : seuls les numéros d'urgence, tout de suite et appelables.
            callTask?.cancel()
            callMiffId = nil
            chosen = false
            call = Self.emergencyOnly(region: region(), loading: false, unavailable: false)
            return
        }
        // Une lecture des proches qui a échoué est refaite à la réouverture de l'écran pour la même Miff.
        if callMiffId == miffId, !call.loading, !call.contactsUnavailable {
            return
        }
        callMiffId = miffId
        callTask?.cancel()
        chosen = false
        call = Self.emergencyOnly(region: region(), loading: true, unavailable: false)
        callTask = Task { [weak self] in
            await self?.readMembers(miffId: miffId)
        }
    }

    private func readMembers(miffId: String) async {
        let read = await members.members(miffId: miffId)
        guard !Task.isCancelled, callMiffId == miffId else {
            return
        }
        let list = AlertCallList.build(members: read ?? [], myUid: myUid() ?? "", region: region())
        // Un choix déjà fait par la personne n'est jamais remplacé par ce qui arrive ensuite.
        let kept = list.targets.first { chosen && $0.id == call.selected.id }
        let next = DangerCallState(
            list: list, selected: kept ?? list.preselected, contactsUnavailable: read == nil, loading: false
        )
        // L'arrivée des proches remplace 112 (présélectionné faute de mieux) par le créateur : l'écran l'annonce poliment (VoiceOver), jamais en silence.
        call = next
    }

    /// Une ligne de la liste a été choisie (gardée tant que la même alerte continue).
    func select(_ target: AlertDialTarget) {
        guard call.list.targets.contains(where: { $0.id == target.id }) else {
            return
        }
        chosen = true
        // La même ligne ne change RIEN et n'ÉMET rien : un nouvel état pour le même choix relançait l'effet de l'écran de la limite sans fin.
        if call.selected.id == target.id {
            return
        }
        call.selected = target
    }

    /// « Appeler les secours » : le numéro d'urgence (112 là où il existe) est choisi.
    func selectEmergency() {
        // Déjà sur un service d'urgence (112 dès le départ, ou celui que la personne a choisi) : rien à émettre, et ce choix n'est plus jamais remplacé.
        if case .emergency = call.selected {
            chosen = true
            return
        }
        let entry = call.list.emergency.first { $0.number == "112" } ?? call.list.emergency[0]
        select(.emergency(entry))
    }

    /// L'adresse du composeur du système (`tel:`), jamais un appel passé par Miff ; nil quand le numéro ne peut pas l'atteindre.
    func dialURL(for target: AlertDialTarget) -> URL? {
        switch target {
        case .person(let contact):
            return FeedLinks.call(phone: contact.phone)
        case .emergency(let entry):
            return EmergencyCatalog.dialURL(entry.number)
        }
    }
}
#endif
