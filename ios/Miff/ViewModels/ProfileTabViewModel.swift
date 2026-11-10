import Foundation
#if canImport(Combine)
import Combine
#endif

// SÉCURITÉ, pour ce fichier : le nom et la photo sont des données personnelles. Ils restent dans l'état de l'écran, pour la durée de
// la session, et ne sont ni journalisés ni envoyés dans un événement Analytics (l'onglet Profil n'en envoie aucun).

/// L'en-tête de l'onglet Profil (ticket Onboarding §6.8) : la photo (ou les initiales), « Prénom Nom », « C'est toi », l'engrenage.
/// Un view model par compte et par session (l'hôte de la session est recréé à chaque connexion) : la personne suivante ne retrouve
/// jamais le profil de la précédente.
@MainActor
final class ProfileTabViewModel: ObservableObject {
    @Published private(set) var state: ProfileTabState = .loading {
        didSet { updateOfflineNeed() }
    }

    /// La pop-in « Pas de connexion » de l'onglet Profil tant que rien n'est lu (même composant que le Feed).
    let offlineRetry: OfflineRetryModel
    private let reader: ProfileReading
    private let currentUid: () -> String?
    private var isLoading = false

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
        Task { [weak self] in
            guard let self else {
                return
            }
            let summary = await reader.read(uid: uid)
            isLoading = false
            // La session a pu se terminer, ou changer de compte, pendant l'attente : cette réponse serait celle de la personne d'avant.
            guard currentUid() == uid else {
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
