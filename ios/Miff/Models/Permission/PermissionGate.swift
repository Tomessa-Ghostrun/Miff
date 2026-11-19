import Foundation

// Ce qui se tient entre la personne et le Feed (LOC-4a, miroir de `PermissionGate` d'Android) : une fonction PURE, testée sur
// chaque branche. Rien ici ne lit une position.

/// Les choix du PM qui ATTENDENT ENCORE une décision (message du coordinateur du 2026-11-04, déjà posés comme constantes sur
/// Android). Chacun est une constante : en changer une est une ligne (et un test). Ils sont listés comme points ouverts dans
/// la PR ; aucun n'est décidé ici.
enum GatePolicy {
    /// L'accord (renouvellement du consentement) AVANT l'écran « Toujours » (recommandation du design : aucune permission n'est
    /// demandée tant que rien ne s'en servirait). Tant que la personne n'a pas accepté (et dit « Pas maintenant »), l'écran
    /// « Toujours » n'est pas montré. Faux : l'écran « Toujours » passe en premier et reste bloquant quelle que soit la réponse
    /// au consentement.
    static let consentBeforeLocation = true

    /// Un lien « Se déconnecter » discret sur les écrans bloquants (recommandation : oui). « Supprimer mon compte » ne peut pas
    /// exister encore (AUTH-5).
    static let exitLinksOnBlockingScreens = true
}

/// À quoi ressemble le renouvellement du consentement pour la porte. « Pas maintenant » n'est gardé qu'en mémoire : un
/// renouvellement dû revient donc à chaque lancement de l'app (recommandation du design ; « une fois par jour » n'est pas
/// construit : il faudrait une date gardée).
enum RenewalState: Equatable {
    /// La vérification est en cours (elle démarre avec l'écran et son attente est bornée). L'écran « Toujours » attend sa fin :
    /// un membre qui partage sous un texte plus ancien ne doit pas pouvoir donner la localisation d'arrière-plan AVANT l'accord.
    case checking
    /// La vérification s'est terminée sans réponse (pas de réseau, trop lent, refus) : rien n'est affirmé, rien n'est attendu.
    case unknown
    case nothingDue
    /// La personne partage sous un texte plus ancien : l'écran de renouvellement est dû.
    case due
}

/// L'écran que la porte pose devant le Feed (`none` : le Feed lui-même).
enum GateScreen: Equatable {
    case none
    case notifications
    /// Attente neutre et bornée (lecture des autorisations, ou vérification du consentement).
    case checking
    case location
    case renewal
}

struct GateInput {
    let snapshot: PermissionSnapshot
    /// L'écran des notifications a déjà été montré à ce compte sur ce téléphone (il ne bloque jamais, il ne revient pas).
    let notificationsHandled: Bool
    /// Interrupteur de COMPILATION : vrai (défaut) = l'écran de la localisation ne se quitte pas sans « Toujours » (décision du PM).
    let locationBlocking: Bool
    /// « Plus tard » a été touché (possible seulement quand `locationBlocking` est faux).
    let locationDeferred: Bool
    let renewal: RenewalState
    /// « Pas maintenant » a été touché pendant ce lancement de l'app.
    let renewalDeferred: Bool
    var consentBeforeLocation = GatePolicy.consentBeforeLocation
    /// SRV-2I : les notifications sont autorisées dans le système, mais la personne n'a jamais répondu à l'écran qui dit que Miff enregistre un
    /// identifiant technique de son téléphone (elle avait autorisé AVANT cette version, avec l'ancien texte) : l'écran revient UNE fois. Sans
    /// cette réponse, aucun jeton n'est enregistré.
    var pushExplanationDue = false
}

extension GateInput {
    /// L'écran des notifications est dû : la fenêtre du système peut encore s'afficher et l'écran n'a pas déjà été montré.
    fileprivate var notificationsAsked: Bool {
        (snapshot.notificationsAskable && !notificationsHandled) || pushExplanationDue
    }

    /// Le renouvellement est dû et la personne ne l'a pas remis à plus tard pendant ce lancement.
    fileprivate var renewalAsked: Bool {
        renewal == .due && !renewalDeferred
    }

    /// La localisation manque, et rien ne permet de la laisser passer (le build bloque, ou « Plus tard » n'a pas été touché).
    fileprivate var locationAsked: Bool {
        !snapshot.locationComplete && (locationBlocking || !locationDeferred)
    }

    /// L'accord d'abord : l'écran « Toujours » attend la fin de la vérification du consentement.
    fileprivate var waitsForTheCheck: Bool {
        locationAsked && consentBeforeLocation && renewal == .checking
    }

    /// L'écran « Toujours » peut être montré : l'accord n'est pas dû avant lui (ou l'ordre inverse est choisi).
    fileprivate var locationMayShow: Bool {
        locationAsked && !(consentBeforeLocation && renewal == .due)
    }
}

/// Ce qui se tient entre la personne et le Feed, dans l'ordre (ticket Tracking, décisions du PM) : les notifications (jamais
/// bloquantes, sautées quand elles sont accordées, refusées, ou déjà montrées), le renouvellement du consentement, puis la
/// localisation (bloquante : AUT1, AUT4, AUT5 ; relâchée par l'interrupteur de compilation : AUT6).
///
/// L'accord d'abord : l'écran « Toujours » n'est pas montré pendant la vérification, ni pendant que l'accord est dû (remis à
/// plus tard ou non) : aucune permission n'est demandée tant que rien ne s'en servirait, et aucune avant l'accord.
func resolveGate(_ input: GateInput) -> GateScreen {
    if input.notificationsAsked {
        return .notifications
    }
    if input.renewalAsked && input.consentBeforeLocation {
        return .renewal
    }
    if input.waitsForTheCheck {
        return .checking
    }
    if input.locationMayShow {
        return .location
    }
    return input.renewalAsked ? .renewal : .none
}
