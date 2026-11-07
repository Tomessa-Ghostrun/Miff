import Foundation
#if canImport(Combine)
import Combine
#endif

// SÉCURITÉ, pour ce fichier : ni mot de passe, ni jeton, ni code d'autorisation Apple, ni e-mail, ni identifiant, ni nom de Miff n'est
// journalisé, mis dans un événement ou gardé dans un état (critères SC13 et SC47). Le mot de passe est un PARAMÈTRE de `submitPassword`,
// jamais un champ de l'état. RIEN ici ne change quoi que ce soit sur le serveur : la suppression elle-même ne commence, dans le
// contrôleur, qu'après une ré-authentification réussie.

/// Ce que le serveur a dit des Miffs du compte (l'écran d'information les lit auprès du SERVEUR, jamais du cache : SC2).
enum DeleteInventory: Equatable {
    case loading
    case loaded
    case offline
    case readFailed
}

struct DeleteInfoUiState: Equatable {
    var inventory: DeleteInventory = .loading
    /// Les Miffs rejointes et créées, telles que le SERVEUR les a dites (complète : sans sens tant que `inventory` n'est pas `loaded`).
    var miffs = AccountMiffsSummary.none
    /// La fenêtre de confirmation est ouverte.
    var confirming = false
    /// Le réseau est parti entre l'écran d'information et la fenêtre : une note calme, le bouton reste actif.
    var confirmOffline = false

    /// Seulement sur une liste complète (SC2) : pendant la lecture, hors connexion et après une lecture ratée « Continuer » reste grisé.
    var canContinue: Bool {
        inventory == .loaded
    }

    /// Le compte a créé au moins une Miff : elles sont supprimées avec lui (l'écran et la fenêtre le disent).
    var ownsMiffs: Bool {
        !miffs.owned.isEmpty
    }
}

/// Écran A puis fenêtre B (ticket AUTH-5, §3). Lit les Miffs du compte auprès du serveur ; n'écrit rien. « Annuler », la flèche de
/// retour et le geste de retour quittent tous sans rien avoir changé (SC4) : ce view model n'a rien à défaire.
@MainActor
final class DeleteAccountInfoViewModel: ObservableObject {
    @Published private(set) var state = DeleteInfoUiState()

    private let procedure: AccountDeletionProcedure
    private let currentUid: () -> String?
    private let network: NetworkMonitor
    private var loading = false
    private var networkWatch: Task<Void, Never>?

    init(procedure: AccountDeletionProcedure, currentUid: @escaping () -> String?, network: NetworkMonitor) {
        self.procedure = procedure
        self.currentUid = currentUid
        self.network = network
        load()
        // Hors connexion à l'ouverture : la liste s'affiche toute seule dès que le réseau revient.
        let changes = network.onlineChanges()
        networkWatch = Task { [weak self] in
            for await online in changes where online {
                self?.reloadIfOffline()
            }
        }
    }

    deinit {
        networkWatch?.cancel()
    }

    private func reloadIfOffline() {
        if state.inventory == .offline {
            load()
        }
    }

    /// Première lecture, et « Réessayer ».
    func load() {
        guard !loading, let uid = currentUid() else {
            return
        }
        loading = true
        state.inventory = .loading
        Task {
            await self.read(uid: uid)
        }
    }

    private func read(uid: String) async {
        var result = DeleteInventory.offline
        var summary = AccountMiffsSummary.none
        if network.isOnline() {
            switch await procedure.inventory(uid: uid) {
            case .loaded(let found):
                summary = found
                result = .loaded
            case .offline:
                result = .offline
            case .failed:
                result = .readFailed
            }
        }
        loading = false
        state.inventory = result
        state.miffs = summary
    }

    /// « Continuer » : ouvre la fenêtre, seulement sur une liste complète.
    func onContinue() {
        if state.canContinue {
            state.confirming = true
            state.confirmOffline = false
        }
    }

    /// « Garder mon compte » : on revient à l'écran, rien n'a été touché.
    func onKeep() {
        state.confirming = false
        state.confirmOffline = false
    }

    /// La fenêtre a été fermée par le système (ses boutons ferment toujours la fenêtre) : seul son état d'ouverture change, la note
    /// « pas de connexion » éventuelle reste.
    func dismissConfirmation() {
        state.confirming = false
    }

    /// « Supprimer mon compte » dans la fenêtre. Vrai = on passe à la ré-authentification (rien n'est écrit avant qu'elle réussisse).
    /// Sans réseau, la fenêtre se ferme (une alerte du système ne peut pas rester ouverte) et une note calme le dit sur l'écran ;
    /// « Continuer » reste actif.
    func onConfirm() -> Bool {
        guard state.canContinue else {
            return false
        }
        state.confirming = false
        guard network.isOnline() else {
            state.confirmOffline = true
            return false
        }
        state.confirmOffline = false
        return true
    }
}

struct DeleteReauthUiState: Equatable {
    /// Montrée pour dire de quel compte il s'agit ; jamais gardée ailleurs.
    let email: String?
    /// Les méthodes réellement liées au compte (mot de passe, Google, Apple).
    let providers: Set<SignInProvider>
    /// « Dernière vérification avant de terminer » (la seconde demande, faite par Firebase tout à la fin).
    let last: Bool
    /// Quelque chose est en cours de vérification : un geste = une opération.
    var busy = false
    var message: AuthMessage?

    private var linkedToApple: Bool {
        providers.contains(.apple)
    }

    /// Un compte lié à Apple se ré-authentifie PAR la feuille Apple (et seulement elle) : le code d'autorisation qu'elle donne sert aussi à
    /// RETIRER l'autorisation « Se connecter avec Apple » (règle 5.1.1(v) de l'App Store). Passer par le mot de passe obligerait à
    /// repasser par la feuille Apple de toute façon : un seul geste vaut mieux que deux.
    var offersApple: Bool {
        linkedToApple
    }

    var offersPassword: Bool {
        !linkedToApple && providers.contains(.password)
    }

    var offersGoogle: Bool {
        !linkedToApple && providers.contains(.google)
    }

    /// Aucune méthode que ce téléphone sache utiliser.
    var noMethod: Bool {
        !offersApple && !offersPassword && !offersGoogle
    }
}

/// Ce dont la ré-authentification a besoin. `email` est lu à chaque ouverture de l'écran (le compte connecté peut changer entre-temps),
/// jamais gardé.
struct DeleteReauthDependencies {
    let gateway: AuthGateway
    let email: () -> String?
    let network: NetworkMonitor
    let social: SocialCredentialSource
    let revoker: AppleTokenRevoker
}

/// Écran C, et le même écran pour « Dernière vérification » : la personne prouve à nouveau qui elle est avec une méthode liée à son
/// compte. `onVerified` est appelé quand Firebase a accepté (avec ce qui est arrivé à l'autorisation Apple) ; plusieurs essais sont
/// permis, un à la fois.
@MainActor
final class DeleteReauthViewModel: ObservableObject {
    @Published private(set) var state: DeleteReauthUiState

    private let gateway: AuthGateway
    private let network: NetworkMonitor
    private let social: SocialCredentialSource
    private let revoker: AppleTokenRevoker
    private let onVerified: (AppleRevocation) -> Void
    private let email: () -> String?

    init(
        dependencies: DeleteReauthDependencies,
        last: Bool,
        onVerified: @escaping (AppleRevocation) -> Void
    ) {
        gateway = dependencies.gateway
        email = dependencies.email
        network = dependencies.network
        social = dependencies.social
        revoker = dependencies.revoker
        self.onVerified = onVerified
        state = DeleteReauthUiState(
            email: dependencies.email(), providers: dependencies.gateway.linkedProviders(), last: last
        )
    }

    /// L'écran s'ouvre : les méthodes liées au compte connecté MAINTENANT sont relues (le modèle peut avoir été créé avant la connexion), et
    /// rien d'occupé ni d'ancien message ne reste d'une visite précédente.
    func refresh() {
        state = DeleteReauthUiState(email: email(), providers: gateway.linkedProviders(), last: state.last)
    }

    /// L'écran a été quitté (la suppression a continué ou s'est arrêtée) : une visite suivante repart sans rien d'occupé ni d'ancien
    /// message.
    func resetBusy() {
        state.busy = false
        state.message = nil
    }

    /// « Confirmer » avec un mot de passe. Il sert une fois et n'est pas gardé.
    func submitPassword(_ password: String) {
        guard !state.busy, !password.isEmpty, state.offersPassword else {
            return
        }
        guard network.isOnline() else {
            state.message = .network
            return
        }
        state.busy = true
        state.message = nil
        Task {
            let failure = await self.gateway.reauthenticateWithPassword(password)
            self.finish(failure, isSocial: false, apple: .notNeeded)
        }
    }

    /// « Continuer avec Google » : la fenêtre du système, puis Firebase. La fermer ne dit rien (SC12).
    func submitGoogle() {
        guard !state.busy, state.offersGoogle else {
            return
        }
        state.busy = true
        state.message = nil
        Task {
            let result = await self.social.requestGoogle()
            guard case .credential(let credential) = result else {
                self.state.busy = false
                self.state.message = self.messageForDialog(result.message)
                return
            }
            let failure = await self.gateway.reauthenticateForDeletion(with: credential)
            self.finish(failure, isSocial: true, apple: .notNeeded)
        }
    }

    /// « Continuer avec Apple » : la feuille officielle d'Apple. Le code d'autorisation qu'elle donne sert à RETIRER l'autorisation
    /// « Se connecter avec Apple » juste après la ré-authentification (il est de courte durée et à usage unique, la procédure peut
    /// être longue). Un échec de la révocation n'arrête pas la suppression (décision Q8a) : il est retenu pour l'écran final.
    func submitApple() {
        guard !state.busy, state.offersApple else {
            return
        }
        state.busy = true
        state.message = nil
        Task {
            let result = await self.social.requestApple()
            guard case .credential(let credential) = result else {
                self.state.busy = false
                self.state.message = self.messageForDialog(result.message)
                return
            }
            let failure = await self.gateway.reauthenticateForDeletion(with: credential)
            guard failure == nil else {
                self.finish(failure, isSocial: true, apple: .notNeeded)
                return
            }
            let revocation = await self.revoke(credential)
            self.finish(nil, isSocial: true, apple: revocation)
        }
    }

    private func revoke(_ credential: SocialCredential) async -> AppleRevocation {
        guard case .apple(_, _, _, let authorization) = credential, let code = authorization else {
            return .failed
        }
        let revoked = await revoker.revoke(authorizationCode: code)
        return revoked ? .revoked : .failed
    }

    /// Le message d'une fenêtre système qui n'a pas donné de jeton : rien si elle a été fermée, « pas de connexion » si le téléphone est
    /// hors ligne, sinon la phrase calme.
    private func messageForDialog(_ message: AuthMessage?) -> AuthMessage? {
        guard let message else {
            return nil
        }
        return network.isOnline() ? message : .network
    }

    private func finish(_ failure: AuthFailure?, isSocial: Bool, apple: AppleRevocation) {
        guard let failure else {
            // Reste « occupé » : la suppression commence et cet écran est remplacé ; un second toucher ne doit pas lancer une autre opération.
            onVerified(apple)
            return
        }
        state.busy = false
        state.message = failure.reauthMessage(isSocial: isSocial)
    }
}
