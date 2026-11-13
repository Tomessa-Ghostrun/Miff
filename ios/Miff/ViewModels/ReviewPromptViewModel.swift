#if REVIEW_PROMPT
import Foundation
#if canImport(UIKit)
import UIKit
#endif

// CONFIDENTIALITÉ, pour ce fichier : aucun événement Analytics, aucun journal, rien de ce que la personne répond ne quitte l'appareil. La
// seule trace est l'état local de `ReviewStateStore` (trois petites valeurs).

/// Ce que montre la fenêtre.
enum ReviewStage: Equatable {
    case hidden
    /// « Que penses-tu de Miff ? » et deux boutons : « J'adore ! » et « Peux faire mieux... » (pas d'étoiles : décision du PM, 2026-11-08).
    case question
    /// « Merci ! » : un court instant, rien à toucher ; la demande du store suit.
    case thanksForLove
    /// « Merci de nous le dire ! » : « Nous écrire » et « Plus tard ». Seulement tant qu'aucun formulaire externe n'est valide.
    case thanksForFeedback
}

/// Ce que l'écran doit faire avec l'appareil (c'est lui qui tient la fenêtre du système ; ce modèle n'en tient aucune).
enum ReviewEffect: Equatable {
    /// Demander un avis au store : la fenêtre NATIVE d'Apple, qui décide seule si elle s'affiche (son propre quota).
    case requestStoreReview
    /// Ouvrir l'application de courrier sur l'adresse d'aide, sans rien d'autre dans le message.
    case openMail
    /// Ouvrir le formulaire externe dans le navigateur : `address` est en HTTPS et s'ouvre TELLE QUELLE, rien de la personne n'y est ajouté.
    case openFeedbackForm(address: String)
}

/// Les faits que le Feed (et la coquille) donnent à la fenêtre au moment de la décision. Lus quand l'heure vient, pas quand le retour a eu
/// lieu : le Feed s'est posé, la personne a pu changer d'onglet.
struct ReviewFeedSnapshot: Equatable {
    var friendStatusSeen = false
    var myLabel: StateLabel?
    var locateSheetOpen = false
    var connectionPopInOpen = false
}

/// Les points où l'app dit à la fenêtre ce qu'elle seule sait (l'onglet Update ouvert, le Feed, la pop-in du Profil). Une classe simple, SANS
/// observation : la fenêtre lit ces valeurs au moment de décider, rien ne se redessine pour elles.
@MainActor
final class ReviewSignals {
    var updateScreenOpen = false
    var feed: @MainActor () -> ReviewFeedSnapshot = { ReviewFeedSnapshot() }
    var profileConnectionPopInOpen: @MainActor () -> Bool = { false }
}

/// La fenêtre d'avis de l'app (décision du PM du 2026-11-07, voir `docs/tickets/pop-ins-avis.md`). Elle décide QUAND la fenêtre apparaît
/// (règles de `ReviewPromptRules`) et la fait passer d'un écran au suivant ; l'écran ne fait que dessiner. Miroir de
/// `ReviewPromptViewModel.kt` d'Android.
///
/// Créée UNE fois pour l'app (`AppEnvironment`) : les hôtes de l'écran sont recréés à chaque retour de la porte des autorisations, pas elle.
///
/// `debugForceAllowed` : seuls les builds Debug et Internal honorent « montre la fenêtre au prochain retour » (la carte du diagnostic, pour
/// la voir sans attendre 7 ou 90 jours). Rien d'autre dans les règles n'est contourné : la fenêtre ne recouvre toujours ni l'Update, ni le
/// clavier, ni un message de l'app.
@MainActor
final class ReviewPromptCoordinator: ObservableObject {
    @Published private(set) var stage: ReviewStage = .hidden
    /// Ce que l'écran doit faire, une fois, quand l'app est devant (voir `takeEffect`).
    @Published private(set) var pendingEffect: ReviewEffect?
    /// Le nombre de retours de l'app au premier plan (l'ouverture à froid en est un). L'écran regarde les règles une fois par retour.
    @Published private(set) var foregroundTick = 1

    let signals = ReviewSignals()
    /// Le « Merci ! » en cours (visible des tests, qui l'attendent).
    private(set) var thanksTask: Task<Void, Never>?

    private let store: ReviewStateStore
    private let nowMillis: () -> Int
    private let debugForceAllowed: Bool
    private let feedbackAddress: String?
    private let thanksMillis: Int
    private let pause: (Int) async -> Void
    /// Le dernier retour au premier plan regardé : une rotation ou un passage par les Réglages ne regarde jamais deux fois.
    private var handledTick = 0
    /// Gardé (jamais retiré : la fenêtre vit autant que l'app) pour que l'observateur ne soit pas un résultat jeté.
    private var foregroundObserver: NSObjectProtocol?

    init(
        store: ReviewStateStore,
        nowMillis: @escaping () -> Int = { Int(Date().timeIntervalSince1970 * 1000) },
        debugForceAllowed: Bool,
        feedbackFormAddress: String = ReviewFeedbackForm.configuredAddress,
        thanksMillis: Int = ReviewPromptDefaults.thanksMillis,
        pause: @escaping (Int) async -> Void = { millis in
            try? await Task.sleep(nanoseconds: UInt64(max(millis, 0)) * 1_000_000)
        },
        observesForeground: Bool = false
    ) {
        self.store = store
        self.nowMillis = nowMillis
        self.debugForceAllowed = debugForceAllowed
        feedbackAddress = ReviewFeedbackForm.openableAddress(feedbackFormAddress)
        self.thanksMillis = thanksMillis
        self.pause = pause
        #if canImport(UIKit)
        if observesForeground {
            // « Retour au premier plan » = l'app était en arrière-plan : ni une rotation, ni le centre de contrôle, ni une alerte du système
            // (celles-là ne sont que « inactive »).
            foregroundObserver = NotificationCenter.default.addObserver(
                forName: UIApplication.willEnterForegroundNotification,
                object: nil,
                queue: .main
            ) { [weak self] _ in
                Task { @MainActor in
                    self?.arrivedInForeground()
                }
            }
        }
        #endif
    }

    /// L'app revient au premier plan : un retour de plus à regarder.
    func arrivedInForeground() {
        foregroundTick += 1
    }

    func hasHandled(_ tick: Int) -> Bool {
        tick <= handledTick
    }

    /// Le Feed a été montré avec une Miff : la date n'est gardée que la première fois (« une Miff depuis au moins 7 jours »).
    func onMiffSeen() {
        let state = store.read()
        if state.firstMiffSeenAtMillis == nil {
            store.write(state.withMiffSeen(nowMillis: nowMillis()))
        }
    }

    /// L'app est revenue au premier plan (`tick` les compte) et le Feed a eu le temps de se poser : montre la fenêtre si les règles le disent.
    func onForeground(tick: Int, situation: ReviewSituation) {
        if hasHandled(tick) || stage != .hidden {
            return
        }
        let now = nowMillis()
        let forced = debugForceAllowed && store.forceNext()
        // Une fenêtre forcée qui ne peut pas apparaître maintenant (Update, clavier, message) attend le retour suivant : le retour n'est PAS consommé.
        if forced && !situation.isFree {
            return
        }
        handledTick = tick
        let state = store.read()
        if forced || ReviewPromptRules.mayAppear(state: state, situation: situation, nowMillis: now) {
            if forced {
                store.setForceNext(false)
            }
            // Écrit AVANT que la fenêtre soit dessinée : une app arrêtée devant elle compte comme une apparition (pas de boucle).
            store.write(state.shown(nowMillis: now))
            stage = .question
        }
    }

    func onLove() {
        if stage != .question {
            return
        }
        store.write(store.read().answered(.loved))
        stage = .thanksForLove
        let wait = thanksMillis
        let pause = self.pause
        thanksTask = Task { [weak self] in
            await pause(wait)
            if Task.isCancelled {
                return
            }
            self?.finishThanks()
        }
    }

    private func finishThanks() {
        if stage != .thanksForLove {
            return
        }
        stage = .hidden
        pendingEffect = .requestStoreReview
    }

    /// « Peux faire mieux... » : la fenêtre du store n'est PAS ouverte. Avec un formulaire valide, la fenêtre se ferme et le formulaire s'ouvre
    /// dans le navigateur ; sans formulaire, un remerciement calme avec un moyen de nous écrire.
    func onCanDoBetter() {
        if stage != .question {
            return
        }
        store.write(store.read().answered(.canDoBetter))
        if let address = feedbackAddress {
            stage = .hidden
            pendingEffect = .openFeedbackForm(address: address)
        } else {
            stage = .thanksForFeedback
        }
    }

    /// La croix ou le geste « échappe » de VoiceOver. Sans réponse, la fenêtre revient après 30 jours (sans limite) ; après « Peux faire
    /// mieux... » elle se ferme seulement.
    func onClose() {
        switch stage {
        case .question:
            store.write(store.read().closed())
            stage = .hidden
        case .thanksForFeedback:
            stage = .hidden
        case .thanksForLove, .hidden:
            // « Merci ! » dure un instant et continue seul ; rien à fermer quand rien n'est là.
            break
        }
    }

    func onWrite() {
        if stage != .thanksForFeedback {
            return
        }
        stage = .hidden
        pendingEffect = .openMail
    }

    func onLater() {
        onClose()
    }

    /// Rend ce que l'écran doit faire (une seule fois) ; nil s'il n'y a rien.
    func takeEffect() -> ReviewEffect? {
        let effect = pendingEffect
        pendingEffect = nil
        return effect
    }

    /// Fin de session (déconnexion, suppression du compte, session perdue) : tout est oublié, la personne suivante repart de zéro.
    func reset() {
        thanksTask?.cancel()
        thanksTask = nil
        stage = .hidden
        pendingEffect = nil
        signals.feed = { ReviewFeedSnapshot() }
        signals.profileConnectionPopInOpen = { false }
        signals.updateScreenOpen = false
        store.clear()
    }
}
#endif
