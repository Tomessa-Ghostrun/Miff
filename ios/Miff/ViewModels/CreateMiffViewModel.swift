import Foundation
#if canImport(Combine)
import Combine
#endif

// SÉCURITÉ, pour ce fichier : le nom de la Miff et son code sont des données personnelles / un secret. Ils vivent en mémoire
// dans cet état seulement, jamais dans un journal, un événement Analytics (les événements ne portent que `is_first_miff`) ni
// un rapport de plantage.

/// Tout ce dont les écrans « Créer » et « Rejoindre » ont besoin.
struct MiffFlowContext {
    let repository: MiffRepository
    let network: NetworkMonitor
    let currentUid: () -> String?
    let tracker: AnalyticsTracker
    /// La session apprend que le compte a maintenant une Miff (fin de l'onboarding comptée, aucun changement d'écran).
    let onEntered: (AnalyticsEvent.OnboardingVia) -> Void
    /// L'accueil et la fiche de la Miff apprennent la nouvelle Miff.
    let onDirectoryChanged: () async -> Void
    /// Le profil manque : le routage est redemandé et renvoie la personne à l'étape profil.
    let onProfileMissing: () -> Void
}

enum CreateStep: Equatable {
    case name
    case ready
}

struct CreateUiState: CustomStringConvertible {
    var step: CreateStep = .name
    var name = ""
    var limitReached = false
    var saving = false
    var message: ActionMessage?
    var created: MiffCreation?

    /// « Créer et partager » reste gris tant que le nom, espaces des deux bouts retirés, a moins de 2 caractères (critère C2).
    var canSubmit: Bool {
        !saving && MiffName.isValid(name)
    }

    var description: String {
        "CreateUiState(redacted)"
    }
}

/// « Nouvelle Miff » puis « Ta Miff est prête » (ticket Miff, écrans B et C, décision QM15 : deux temps). Le code est tiré à
/// l'enregistrement, jamais avant ; le lot est UNE transaction confirmée ; rien n'est montré comme un succès avant que le
/// serveur ait dit oui, et rien ne reste en attente quand il ne l'a pas dit (critère C5). Un appui = une opération (H2).
@MainActor
final class CreateMiffViewModel: ObservableObject {
    @Published private(set) var state = CreateUiState()

    private let context: MiffFlowContext

    init(context: MiffFlowContext) {
        self.context = context
    }

    func onNameChanged(_ typed: String) {
        guard !state.saving, state.step == .name else {
            return
        }
        let limited = MiffName.limit(typed)
        var next = state
        next.name = limited.text
        next.limitReached = limited.limitReached
        next.message = nil
        state = next
    }

    func onSubmit() {
        guard state.canSubmit, state.step == .name else {
            return
        }
        // Rien à essayer sans réseau : la réponse est immédiate et rien ne reste en attente (ticket §5.11).
        guard context.network.isOnline() else {
            state.message = .offline
            return
        }
        guard let uid = context.currentUid() else {
            state.message = .generic
            return
        }
        state.saving = true
        state.message = nil
        let name = state.name
        Task {
            await self.create(uid: uid, name: name)
        }
    }

    private func create(uid: String, name: String) async {
        let repository = context.repository
        let result: CreateResult
        switch await repository.prepareCreate(uid: uid) {
        case .ready(let ready):
            result = await repository.create(uid: uid, name: name, preparation: ready)
        case .needsPremium:
            result = .needsPremium
        case .failed(let failure):
            result = .failed(failure)
        }
        switch result {
        case .created(let creation):
            context.tracker.track(.miffCreated(isFirstMiff: creation.firstMiff))
            context.onEntered(.created)
            // Le lot est accepté : la personne voit son code TOUT DE SUITE. L'annuaire se rafraîchit ensuite en tâche de fond
            // (il garde ses propres garde-fous : verrou, fin de session) ; sur un réseau instable il pouvait faire attendre
            // longtemps sur un écran verrouillé, au risque qu'on quitte l'app sans avoir vu le code.
            var next = state
            next.step = .ready
            next.saving = false
            next.created = creation
            state = next
            let refresh = context.onDirectoryChanged
            Task {
                await refresh()
            }
        case .needsPremium:
            // Rien n'est dit de l'offre Premium ici : c'est au ticket Premium de le décider.
            state.saving = false
            state.message = .oneMiff
        case .failed(let failure):
            state.saving = false
            state.message = ActionMessage(failure: failure)
            if failure == .noProfile {
                context.onProfileMissing()
            }
        }
    }
}
