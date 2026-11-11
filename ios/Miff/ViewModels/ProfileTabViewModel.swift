import Foundation
#if canImport(Combine)
import Combine
#endif

// SÉCURITÉ, pour ce fichier : le nom et la photo sont des données personnelles. Ils restent dans l'état de l'écran, pour la durée de
// la session, et ne sont ni journalisés ni envoyés dans un événement Analytics (l'onglet Profil n'en envoie aucun).

/// Là où le profil de la personne connectée est gardé en mémoire pour la session : « Mes informations » y remet la nouvelle version dès
/// que le serveur a confirmé l'enregistrement, pour que l'en-tête du Profil et mon visage dans le carrousel changent tout de suite, sans
/// seconde lecture (comme `CurrentProfile` sur Android). La carte du Feed, elle, suit l'écoute des fiches des membres : elle change dès que
/// la fiche écrite lui revient.
@MainActor
protocol CurrentProfileHolding: AnyObject {
    /// Un enregistrement de `uid` vient d'être confirmé : voici son profil. Ignoré si cette personne n'est plus connectée.
    func onSaved(uid: String, profile: ProfileSummary)
}

/// L'en-tête de l'onglet Profil (ticket Onboarding §6.8) : la photo (ou les initiales), « Prénom Nom », « C'est toi », l'engrenage.
/// Un view model par compte et par session (l'hôte de la session est recréé à chaque connexion) : la personne suivante ne retrouve
/// jamais le profil de la précédente.
@MainActor
<<<<<<< HEAD
final class ProfileTabViewModel: ObservableObject, CurrentProfileHolding {
    @Published private(set) var state: ProfileTabState = .loading
=======
final class ProfileTabViewModel: ObservableObject {
    @Published private(set) var state: ProfileTabState = .loading {
        didSet { updateOfflineNeed() }
    }
>>>>>>> origin/ios/feed-hors-connexion

    /// La pop-in « Pas de connexion » de l'onglet Profil tant que rien n'est lu (même composant que le Feed).
    let offlineRetry: OfflineRetryModel
    private let reader: ProfileReading
    private let currentUid: () -> String?
    private var isLoading = false
    /// Monte à chaque enregistrement confirmé : une lecture partie AVANT lui rendrait l'ancienne version et la remettrait par-dessus.
    private var saves = 0

    init(
        reader: ProfileReading,
        currentUid: @escaping () -> String?,
        network: NetworkMonitor = AlwaysOnlineMonitor(),
        sleep: @escaping (Int) async -> Void = AppClock.live.pause,
        popinTimings: OfflinePopinTimings = OfflinePopinTimings()
    ) {
        self.reader = reader
        self.currentUid = currentUid
        offlineRetry = OfflineRetryModel(network: network, sleep: sleep, timings: popinTimings)
        offlineRetry.onRetry = { [weak self] _ in
            // Une seule lecture du profil à la fois, et seulement quand le réseau est là : rien de plus léger à proposer.
            self?.retry()
        }
        load()
        updateOfflineNeed()
    }

    /// « Réessayer » : sans effet pendant une lecture en cours (deux touchers ne lancent pas deux lectures).
    func retry() {
        guard !isLoading else {
            return
        }
        load()
    }

<<<<<<< HEAD
    func onSaved(uid: String, profile: ProfileSummary) {
        guard currentUid() == uid else {
            return
        }
        saves += 1
        state = .ready(profile)
=======
    /// Suit le réseau pour la pop-in tant que l'onglet est à l'écran (annulé en le quittant ou en quittant l'app).
    func watchConnectivity() async {
        await offlineRetry.watch()
    }

    private func updateOfflineNeed() {
        switch state {
        case .loading:
            offlineRetry.setNeed(.loading)
        case .unavailable:
            offlineRetry.setNeed(.failed)
        case .ready:
            offlineRetry.setNeed(.none)
        }
>>>>>>> origin/ios/feed-hors-connexion
    }

    private func load() {
        guard let uid = currentUid() else {
            state = .unavailable
            return
        }
        isLoading = true
        // Une version déjà lue reste affichée pendant la relecture.
        if !state.isReady {
            state = .loading
        }
        let savesAtStart = saves
        Task { [weak self] in
            guard let self else {
                return
            }
            let summary = await reader.read(uid: uid)
            isLoading = false
            // La session a pu se terminer, ou changer de compte, pendant l'attente : cette réponse serait celle de la personne d'avant.
            // Un enregistrement confirmé pendant l'attente rend aussi cette réponse périmée.
            guard currentUid() == uid, saves == savesAtStart else {
                return
            }
            if let summary {
                state = .ready(summary)
            } else if !state.isReady {
                // Une relecture ratée ne cache pas ce qu'on sait déjà.
                state = .unavailable
            }
        }
    }
}
