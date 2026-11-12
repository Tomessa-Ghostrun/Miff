import Combine
import Foundation

// CONFIDENTIALITÉ, pour ce fichier : rien ici ne lit une position (AUT7) ni n'écrit autre chose que le renouvellement du
// consentement, et seulement après le toucher de la personne sur « J'accepte » (et, pour « Quitter la Miff », après sa
// confirmation). Aucun événement Analytics, aucun journal (décision (g) du PM). Les noms des Miffs ne vivent en mémoire que dans
// cet état. Miroir de `PermissionGateViewModel` d'Android (LOC-2a).

/// L'écran de renouvellement (maquettes 21 et 22).
struct RenewalUiState: Equatable, CustomStringConvertible {
    let miffs: [RenewalMiff]
    /// Écriture en cours : le bouton le dit et rien d'autre ne peut être touché (un appui = une opération).
    var busy = false
    var message: ActionMessage?
    /// La question « Quitter la Miff « X » ? » est ouverte (membre d'une seule Miff : le créateur ne peut pas quitter, il supprime).
    var leaveAsked = false

    /// La sortie de la maquette 21B : un membre dont la seule Miff attend l'accord peut la quitter.
    var leavable: RenewalMiff? {
        guard miffs.count == 1, let only = miffs.first, !only.isOwner, !only.name.isEmpty else {
            return nil
        }
        return only
    }

    var description: String {
        "RenewalUiState(redacted)"
    }
}

struct GateUiState: Equatable {
    let screen: GateScreen
    let location: LocationScreenModel?
    let renewal: RenewalUiState?
    /// L'écran « Toujours » est relâché par l'interrupteur de compilation : un lien « Plus tard » est montré.
    let canPutOffLocation: Bool
}

/// Ce dont la porte a besoin du monde extérieur (des doubles dans les tests).
struct PermissionGateDependencies {
    let permissions: DevicePermissions
    let asked: NotificationAskStore
    let renewals: ConsentRenewals
    let currentUid: () -> String?
    /// « Quitter la Miff » du flux existant (uid, identifiant de la Miff) : le même lot que le bouton de l'écran de la Miff.
    let leaver: (String, String) async -> MiffWriteResult
    /// Ce que l'app fait une fois une Miff quittée (purge du cache, liste des Miffs relue).
    let onLeft: (String) async -> Void
    /// Les Miffs qui attendent l'accord (FEED-5 : le Feed le dit pendant que « Pas maintenant » est choisi) ; nil dans les tests qui n'en ont pas besoin.
    var awaiting: AwaitingAgreement?
}

/// Les réglages de la porte : l'interrupteur de COMPILATION, l'ordre « accord d'abord », la durée maximale de l'attente.
struct PermissionGateSettings {
    /// Vrai (défaut du build) : l'écran de la localisation ne se quitte pas sans « Toujours ».
    let locationBlocking: Bool
    var consentBeforeLocation = GatePolicy.consentBeforeLocation
    /// Le plus longtemps que l'écran « Toujours » attend la lecture des consentements (2 lectures du serveur au plus pour un
    /// compte gratuit).
    var checkTimeoutSeconds: TimeInterval = 8
}

/// Les écrans de consentement entre la personne et le Feed (LOC-4a). Il relit l'état des autorisations chaque fois que l'app
/// revient au premier plan (AUT5 : une autorisation retirée fait revenir l'écran bloquant), et regarde UNE FOIS par lancement
/// si le consentement doit être renouvelé (R-LIEU-3).
@MainActor
final class PermissionGateViewModel: ObservableObject {
    @Published private(set) var state: GateUiState

    private let permissions: DevicePermissions
    private let asked: NotificationAskStore
    private let renewals: ConsentRenewals
    private let currentUid: () -> String?
    private let locationBlocking: Bool
    /// « Quitter la Miff » du flux existant (uid, identifiant de la Miff) : le même lot que le bouton de l'écran de la Miff.
    private let leaver: (String, String) async -> MiffWriteResult
    /// Ce que l'app fait une fois une Miff quittée (purge du cache, liste des Miffs relue).
    private let onLeft: (String) async -> Void
    private let awaiting: AwaitingAgreement?
    private var reviewSubscription: AnyCancellable?
    private let consentBeforeLocation: Bool
    private let checkTimeoutSeconds: TimeInterval

    private var snapshot: PermissionSnapshot?
    private var locationDeferred = false
    private var renewalDeferred = false
    // Avec une session, la vérification est « en cours » dès le départ : l'écran « Toujours » attend sa fin (bornée).
    private var renewalState: RenewalState
    private var due: [RenewalMiff] = []
    private var renewalBusy = false
    private var renewalMessage: ActionMessage?
    private var leaveAsked = false
    private var notificationsHandled: Bool
    private var notificationsBusy = false
    private var started = false

    init(dependencies: PermissionGateDependencies, settings: PermissionGateSettings) {
        permissions = dependencies.permissions
        asked = dependencies.asked
        renewals = dependencies.renewals
        currentUid = dependencies.currentUid
        leaver = dependencies.leaver
        onLeft = dependencies.onLeft
        awaiting = dependencies.awaiting
        locationBlocking = settings.locationBlocking
        consentBeforeLocation = settings.consentBeforeLocation
        checkTimeoutSeconds = settings.checkTimeoutSeconds
        let uid = dependencies.currentUid()
        renewalState = uid == nil ? .unknown : .checking
        notificationsHandled = uid.map { dependencies.asked.wasShown(uid: $0) } ?? true
        state = GateUiState(
            screen: .checking,
            location: nil,
            renewal: nil,
            canPutOffLocation: !settings.locationBlocking
        )
    }

    /// Démarre la lecture des autorisations et la vérification du consentement (une seule fois).
    func start() async {
        guard !started else {
            return
        }
        started = true
        // « Revoir et accepter » depuis le Feed : l'écran de renouvellement revient.
        reviewSubscription = awaiting?.$reviewRequests.dropFirst().sink { [weak self] _ in
            self?.onReviewRequested()
        }
        permissions.onLocationChange = { [weak self] in
            Task { @MainActor in
                await self?.refresh()
            }
        }
        await refresh()
        await checkRenewal()
    }

    /// L'app est au premier plan (de nouveau) : les autorisations sont relues (une autorisation retirée dans les Réglages fait
    /// revenir l'écran bloquant).
    func onResumed() async {
        await refresh()
    }

    private func refresh() async {
        snapshot = await permissions.snapshot()
        publish()
    }

    private func checkRenewal() async {
        guard let uid = currentUid() else {
            return
        }
        let renewals = self.renewals
        let check: ConsentRenewalCheck
        do {
            // Une vérification trop lente se termine par « inconnu » : l'écran n'attend jamais indéfiniment (pas de réseau,
            // serveur lent).
            check = try await timedOperation(seconds: checkTimeoutSeconds) {
                await renewals.check(uid: uid)
            }
        } catch {
            check = .unknown
        }
        // La session a pu changer pendant l'attente : la réponse d'un autre compte n'est jamais appliquée.
        guard currentUid() == uid else {
            return
        }
        switch check {
        case .due(let miffs):
            due = miffs
            renewalState = .due
        case .nothingToRenew:
            renewalState = .nothingDue
        case .unknown:
            // Pas connu : rien n'est affirmé et plus rien ne bloque ; le prochain lancement demandera de nouveau.
            renewalState = .unknown
        }
        publish()
    }

    // MARK: - Notifications

    /// « Autoriser » : la fenêtre du système. Quelle que soit la réponse, la personne continue (jamais bloquant) et l'écran ne
    /// revient plus pour ce compte.
    func onNotificationsAllowTapped() async {
        guard state.screen == .notifications, !notificationsBusy else {
            return
        }
        notificationsBusy = true
        await permissions.requestNotifications()
        if let uid = currentUid() {
            asked.markShown(uid: uid)
        }
        notificationsHandled = true
        notificationsBusy = false
        await refresh()
    }

    /// « Plus tard » : la fenêtre du système n'est PAS consommée (elle pourra encore s'afficher, depuis Réglages > Notifications ou la carte du
    /// Feed). L'amorce ne revient plus pour ce compte, et la personne continue (jamais bloquant).
    func onNotificationsLaterTapped() async {
        guard state.screen == .notifications, !notificationsBusy else {
            return
        }
        if let uid = currentUid() {
            asked.markShown(uid: uid)
        }
        notificationsHandled = true
        await refresh()
    }

    // MARK: - Localisation

    /// Le bouton de l'écran de la localisation : la fenêtre du système, ou les Réglages de l'app.
    func onLocationActionTapped() {
        guard let model = state.location else {
            return
        }
        switch model.action {
        case .systemWindow:
            permissions.requestLocationAuthorization()
        case .openSettings:
            permissions.openSettings()
        case .none:
            break
        }
    }

    /// « Plus tard » : seulement quand le build ne bloque pas (l'écran ne montre le lien que dans ce cas).
    func onLocationPutOff() {
        guard !locationBlocking else {
            return
        }
        locationDeferred = true
        publish()
    }

    // MARK: - Renouvellement du consentement

    /// « Pas maintenant » : rien n'est écrit ; l'écran revient au prochain lancement de l'app.
    func onRenewalPutOff() {
        guard !renewalBusy else {
            return
        }
        renewalDeferred = true
        renewalMessage = nil
        // Le Feed dit la vérité tant que l'accord n'est pas donné : rien n'est partagé avec ces Miffs.
        awaiting?.replace(Set(due.map { $0.miffId }))
        publish()
    }

    private func onReviewRequested() {
        guard renewalState == .due, !renewalBusy else {
            return
        }
        renewalDeferred = false
        publish()
    }

    func onRenewalAccepted() async {
        guard let uid = currentUid(), !renewalBusy, renewalState == .due else {
            return
        }
        renewalBusy = true
        renewalMessage = nil
        publish()
        let result = await renewals.renew(uid: uid, miffIds: due.map { $0.miffId })
        switch result {
        case .done:
            due = []
            renewalState = .nothingDue
            awaiting?.replace([])
        case .failed(let failure):
            renewalMessage = ActionMessage(failure: failure)
        }
        renewalBusy = false
        publish()
    }

    func onLeaveRequested() {
        guard !renewalBusy, renewalState == .due, RenewalUiState(miffs: due).leavable != nil else {
            return
        }
        leaveAsked = true
        renewalMessage = nil
        publish()
    }

    func onLeaveCancelled() {
        guard !renewalBusy else {
            return
        }
        leaveAsked = false
        publish()
    }

    /// « Quitter la Miff » confirmé : le lot existant (rien n'est écrit avant ce toucher) ; une fois la Miff quittée, il ne
    /// reste rien à accepter.
    func onLeaveConfirmed() async {
        guard let uid = currentUid(), let miff = RenewalUiState(miffs: due).leavable, !renewalBusy, leaveAsked else {
            return
        }
        renewalBusy = true
        renewalMessage = nil
        publish()
        switch await leaver(uid, miff.miffId) {
        case .done:
            await onLeft(miff.miffId)
            awaiting?.remove(miff.miffId)
            due = []
            renewalState = .nothingDue
            leaveAsked = false
        case .failed(let failure):
            renewalMessage = ActionMessage(failure: failure)
        }
        renewalBusy = false
        publish()
    }

    // MARK: - État affiché

    private func publish() {
        state = build()
    }

    private func build() -> GateUiState {
        guard let snapshot else {
            // Les autorisations ne sont pas encore lues : attente neutre, jamais l'écran « Toujours » avant la fin des contrôles.
            return GateUiState(screen: .checking, location: nil, renewal: nil, canPutOffLocation: !locationBlocking)
        }
        let screen = resolveGate(
            GateInput(
                snapshot: snapshot,
                notificationsHandled: notificationsHandled,
                locationBlocking: locationBlocking,
                locationDeferred: locationDeferred,
                renewal: renewalState,
                renewalDeferred: renewalDeferred,
                consentBeforeLocation: consentBeforeLocation
            )
        )
        let renewal: RenewalUiState?
        if renewalState == .due {
            renewal = RenewalUiState(
                miffs: due,
                busy: renewalBusy,
                message: renewalMessage,
                leaveAsked: leaveAsked
            )
        } else {
            renewal = nil
        }
        return GateUiState(
            screen: screen,
            location: LocationFlow.model(for: snapshot),
            renewal: renewal,
            canPutOffLocation: !locationBlocking
        )
    }
}
