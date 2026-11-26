import Combine
import Foundation

/// Une phrase calme sous l'état (jamais en rouge, jamais une alarme) sur ce que la dernière restauration a trouvé.
enum SubscriptionNotice: Equatable {
    /// Le store ne détient aucun abonnement Miff+ pour ce compte du store.
    case nothingFound
    /// L'abonnement trouvé est utilisé par un autre compte Miff.
    case otherAccount
    /// Un paiement attend la confirmation du store.
    case pending
    /// La restauration n'a pas marché cette fois (pas de connexion...).
    case refreshFailed

    /// Ce que dit une restauration terminée (nil : rien à dire).
    static func of(_ restore: RestoreOutcome) -> SubscriptionNotice? {
        switch restore {
        case .noneFound:
            return .nothingFound
        case .otherAccount:
            return .otherAccount
        case .pending:
            return .pending
        case .network, .failed:
            return .refreshFailed
        case .found, .onHold, .cancelled:
            return nil
        }
    }
}

/// Réglages > Abonnement (ticket `docs/tickets/premium-achat-reel.md` §8 et critères AN2, AN6 à AN10) : lit `entitlements/{uid}` (les droits, écrits par le SERVEUR seul) et
/// dit l'état. « Restaurer mes achats » demande au coordinateur de regarder le store et de faire vérifier l'achat par le serveur (seulement sur le geste de la personne : Apple
/// demande alors son identification).
///
/// Aucun prix dans les phrases (décision de l'étape iOS) : le prix du PROCHAIN renouvellement est celui que la personne a accepté chez Apple, pas forcément celui que le catalogue
/// annonce aujourd'hui ; une phrase avec un prix inexact serait pire qu'une phrase sans prix. La feuille de gestion d'Apple (« Gérer mon abonnement ») montre le prix exact.
@MainActor
final class SubscriptionViewModel: ObservableObject {
    @Published private(set) var loading = true
    /// Nil une fois le chargement fini = le serveur n'a pas pu être lu (hors ligne...) : un message calme et « Réessayer », jamais « tu n'as rien ».
    @Published private(set) var status: SubscriptionStatus?
    /// « Restaurer mes achats » est en cours.
    @Published private(set) var refreshing = false
    @Published private(set) var notice: SubscriptionNotice?

    private let subscriptions: SubscriptionRepository
    private let premium: PremiumCoordinator
    private var loadSerial = 0
    private var handledSerial = -1
    private var cancellables = Set<AnyCancellable>()

    init(subscriptions: SubscriptionRepository, premium: PremiumCoordinator) {
        self.subscriptions = subscriptions
        self.premium = premium
        // Un résultat fini AVANT l'ouverture de cet écran ne lui est pas raconté.
        if case .finished(let serial, _, _) = premium.session {
            handledSerial = serial
        }
        premium.$session
            .sink { [weak self] session in
                self?.onSession(session)
            }
            .store(in: &cancellables)
    }

    /// La première lecture, « Réessayer », et après une restauration.
    func load() {
        loadSerial += 1
        let serial = loadSerial
        loading = true
        Task {
            let read = await subscriptions.load()
            guard serial == loadSerial else {
                return
            }
            status = read
            loading = false
        }
    }

    /// L'écran revient au premier plan (après l'offre, ou la page du store) : ce que le serveur sait a peut-être changé. Pas à la première apparition (la première lecture court).
    func onReturned() {
        if !loading {
            load()
        }
    }

    /// « Restaurer mes achats ».
    func restore() {
        guard !refreshing else {
            return
        }
        notice = nil
        premium.startRestore()
    }

    /// « Gérer mon abonnement » : la gestion d'Apple pour un abonnement de l'App Store, la page de Google Play pour un abonnement souscrit sur Android. Rien ne se résilie dans l'app.
    func manage(_ source: SubscriptionSource) {
        if source == .appStore {
            Task {
                await premium.openManagement()
            }
        } else {
            ExternalOpener.open(MiffPlusProducts.manageURL(for: source))
        }
    }

    private func onSession(_ session: PurchaseSession) {
        switch session {
        case .idle:
            refreshing = false
        case .working:
            refreshing = true
            notice = nil
        case .finished(let serial, _, let restore):
            refreshing = false
            guard serial > handledSerial else {
                return
            }
            handledSerial = serial
            if let restore {
                notice = SubscriptionNotice.of(restore)
            }
            // Ce que le serveur sait a peut-être changé (un achat fait depuis l'offre, ou retrouvé par une restauration) : on le relit.
            load()
        }
    }
}
