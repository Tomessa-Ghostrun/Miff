#if REVIEW_PROMPT
import Foundation

// La fenêtre d'avis « Que penses-tu de Miff ? » (ticket docs/tickets/pop-ins-avis.md ; décision du PM du 2026-11-07 : construite TELLE
// QUELLE, risque assumé : Apple 5.6.1 / 5.6.3 et Google déconseillent tout filtrage des avis ; décision du 2026-11-13 : iOS reprend la
// fenêtre d'Android). Ce fichier est la LOGIQUE PURE : quand la fenêtre peut apparaître, et ce que l'appareil en retient. Aucune classe
// d'UIKit ni de SwiftUI ici : toutes les règles se testent sans écran. Miroir de `ReviewPrompt.kt` d'Android.
//
// CONFIDENTIALITÉ : rien de ce fichier n'est une donnée personnelle. Les seules choses gardées sur l'appareil sont trois petites valeurs
// (voir `ReviewState`) ; ce que la personne répond ne quitte l'appareil nulle part, et aucun événement Analytics ne parle de la fenêtre
// (critère AV4 du ticket).
//
// DRAPEAU DE BUILD : tout ce qui touche à cette fenêtre est compilé SEULEMENT quand la condition de compilation `REVIEW_PROMPT` existe
// (project.yml : Debug et Internal oui, Release non). En publication, le code n'existe pas (critère AV2).

/// Les seuils de la fenêtre. 7 jours et 90 jours sont des choix par défaut du développeur (« à confirmer », ticket §8) ; les 30 jours
/// après une fermeture et les 90 jours après « Peux faire mieux... » sont confirmés par le PM (2026-11-08). Un seul endroit pour les changer.
enum ReviewPromptDefaults {
    private static let dayMillis = 86_400_000

    /// La personne a rejoint ou créé une Miff depuis au moins cette durée (mesurée depuis la première fois que CET appareil a montré le
    /// Feed avec une Miff).
    static let minMiffAgeMillis = 7 * dayMillis

    /// Au plus une apparition par cette durée après « Peux faire mieux... » (après « J'adore ! », la fenêtre ne revient jamais).
    static let minIntervalAfterAnswerMillis = 90 * dayMillis

    /// La fenêtre revient après cette durée quand elle a été fermée SANS réponse (croix ou geste « échappe »), sans limite de fois
    /// (décision du PM, 2026-11-08).
    static let minIntervalAfterCloseMillis = 30 * dayMillis

    /// Le temps laissé au Feed pour se poser après le retour de l'app au premier plan, avant que la fenêtre puisse apparaître.
    static let settleMillis = 1_500

    /// La durée du petit « Merci ! » avant la demande du store (la personne peut le lire, rien à toucher).
    static let thanksMillis = 1_600
}

/// Comment la dernière apparition s'est terminée. `shown` reste quand l'app a été arrêtée pendant que la fenêtre était à l'écran. Les
/// valeurs écrites sont celles d'Android (`SHOWN`, `CLOSED`, `LOVED`, `CAN_DO_BETTER`).
enum ReviewOutcome: String, Equatable {
    case shown = "SHOWN"
    case closed = "CLOSED"
    case loved = "LOVED"
    case canDoBetter = "CAN_DO_BETTER"
}

/// Une vraie réponse à la question (par opposition à une fermeture). Un type à part : « répondre » par `closed` est impossible à écrire.
enum ReviewAnswer: Equatable {
    case loved
    case canDoBetter

    var outcome: ReviewOutcome {
        switch self {
        case .loved:
            return .loved
        case .canDoBetter:
            return .canDoBetter
        }
    }
}

/// Ce que l'appareil retient, en trois petites valeurs :
/// - `firstMiffSeenAtMillis` : quand CET appareil a montré le Feed avec une Miff pour la première fois (vaut « a rejoint ou créé une
///   Miff » : aucune lecture de plus du serveur) ;
/// - `lastShownAtMillis` et `lastOutcome` : la dernière apparition et sa fin.
/// Rien ne compte les fermetures : il n'y a pas de limite.
struct ReviewState: Equatable {
    var firstMiffSeenAtMillis: Int?
    var lastShownAtMillis: Int?
    var lastOutcome: ReviewOutcome?

    /// Le Feed a été montré avec une Miff : la date n'est gardée que la première fois.
    func withMiffSeen(nowMillis: Int) -> ReviewState {
        if firstMiffSeenAtMillis != nil {
            return self
        }
        var copy = self
        copy.firstMiffSeenAtMillis = nowMillis
        return copy
    }

    func shown(nowMillis: Int) -> ReviewState {
        var copy = self
        copy.lastShownAtMillis = nowMillis
        copy.lastOutcome = .shown
        return copy
    }

    /// Fermée sans réponse : la fenêtre revient après 30 jours, autant de fois qu'elle a été fermée.
    func closed() -> ReviewState {
        var copy = self
        copy.lastOutcome = .closed
        return copy
    }

    func answered(_ answer: ReviewAnswer) -> ReviewState {
        var copy = self
        copy.lastOutcome = answer.outcome
        return copy
    }
}

/// Ce que l'écran peut dire au moment de la décision (tout est un état, rien n'est personnel).
struct ReviewSituation: Equatable {
    /// La personne a une Miff (le Feed a été chargé pour elle).
    var hasMiff: Bool
    /// Au moins un statut d'un proche est sur le Feed (pas « Pas encore de nouvelles »).
    var friendStatusSeen: Bool
    /// Le viseur de l'Update est l'écran en usage : la fenêtre ne le recouvre jamais.
    var updateScreenOpen: Bool
    var keyboardOpen: Bool
    /// Un message de l'app est à l'écran ou attend la personne (accès perdu, « En attente de ton accord », feuille « Localiser »,
    /// « Tu as arrêté de partager », pop-in « Pas de connexion ») : la fenêtre ne le recouvre jamais et ne le cache jamais.
    var blockingMessageOpen = false

    /// Rien ne l'empêche : ni l'Update, ni le clavier, ni un message de l'app (une fenêtre forcée en test attend aussi cela).
    var isFree: Bool {
        !updateScreenOpen && !keyboardOpen && !blockingMessageOpen
    }

    /// Vrai quand l'un des messages qu'on ne doit pas recouvrir est là : voir `blockingMessageOpen`.
    static func messageOpen(
        lostAccessNotice: Bool,
        awaitingAgreement: Bool,
        locateSheetOpen: Bool,
        myLabel: StateLabel?,
        connectionPopInOpen: Bool = false
    ) -> Bool {
        if lostAccessNotice || awaitingAgreement || locateSheetOpen || connectionPopInOpen {
            return true
        }
        return myLabel == .sharingStopped || myLabel == .waitingAgreement
    }

    /// « A vu le statut d'un proche » : la carte de quelqu'un d'autre dit où il est, ou qu'il est parti, ou dans une zone de danger.
    static func isFriendStatusSeen(in content: FeedContent?) -> Bool {
        guard let content else {
            return false
        }
        return content.cards.contains { card in
            if card.isMe {
                return false
            }
            switch card.label {
            case .at, .left, .danger:
                return true
            default:
                return false
            }
        }
    }
}

enum ReviewPromptRules {
    /// Vrai quand la fenêtre peut apparaître MAINTENANT, au retour de l'app au premier plan. Chaque condition est un « non » si elle échoue :
    /// 1. une Miff depuis au moins 7 jours, et le statut d'un proche déjà vu ;
    /// 2. ni sur l'Update, ni avec le clavier, ni par-dessus (ou en cachant) un message de l'app ;
    /// 3. plus rien une fois que la personne a dit « J'adore » (la demande du store a été faite) ;
    /// 4. une pause depuis la dernière apparition : 90 jours après une réponse « Peux faire mieux... », 30 jours après une fermeture (ou une
    ///    fenêtre sur laquelle l'app a été arrêtée).
    /// Une horloge reculée (dernière apparition dans le futur) compte comme « trop tôt » : la fenêtre attend plutôt que de revenir deux fois.
    static func mayAppear(state: ReviewState, situation: ReviewSituation, nowMillis: Int) -> Bool {
        if !situation.hasMiff || !situation.friendStatusSeen {
            return false
        }
        if !situation.isFree {
            return false
        }
        guard let firstSeen = state.firstMiffSeenAtMillis else {
            return false
        }
        if nowMillis - firstSeen < ReviewPromptDefaults.minMiffAgeMillis {
            return false
        }
        if state.lastOutcome == .loved {
            return false
        }
        guard let last = state.lastShownAtMillis else {
            return true
        }
        let pause = state.lastOutcome == .canDoBetter
            ? ReviewPromptDefaults.minIntervalAfterAnswerMillis
            : ReviewPromptDefaults.minIntervalAfterCloseMillis
        return nowMillis - last >= pause
    }
}

/// « Peux faire mieux... » mène à un FORMULAIRE EXTERNE (décision du PM, 2026-11-08 ; adresse donnée le 2026-11-13, Google Forms). L'adresse est
/// écrite à UN seul endroit : `ReviewFeedbackForm.configuredAddress`. Vide, ou pas une adresse HTTPS simple : la fenêtre montre l'écran
/// calme « Merci de nous le dire ! » avec « Nous écrire ». L'adresse est ouverte TELLE QUELLE : rien de la personne (nom, e-mail, Miff,
/// position, identifiant, jeton) n'y est jamais ajouté ; ne pas en mettre non plus dans l'adresse elle-même.
///
/// Adresse PUBLIQUE, pas un secret. Pour l'essayer vide : la vider ici (ou la remplacer par une autre adresse https).
enum ReviewFeedbackForm {
    static let configuredAddress = "https://forms.gle/5EEH4sEfsnCCLzEx7"

    /// L'adresse à ouvrir dans le navigateur, ou nil quand il n'y en a pas (ni HTTPS : jamais d'adresse en clair, jamais un autre schéma).
    static func openableAddress(_ configured: String?) -> String? {
        guard let trimmed = configured?.trimmingCharacters(in: .whitespacesAndNewlines) else {
            return nil
        }
        // Même règle qu'Android : `https://`, un nom d'hôte, aucun espace.
        guard trimmed.range(of: #"^https://[^\s/?#]+[^\s]*$"#, options: .regularExpression) != nil,
              let url = URL(string: trimmed), url.scheme == "https", let host = url.host, !host.isEmpty else {
            return nil
        }
        return trimmed
    }

    /// Le même contrôle, en `URL` (pour l'ouvrir).
    static func openableURL(_ configured: String?) -> URL? {
        openableAddress(configured).flatMap { URL(string: $0) }
    }
}
#endif
