import Foundation

/// Les écrans du flux Miff dans une navigation. Seuls des identifiants voyagent dans une destination (la Miff, le lieu, la demande) :
/// jamais le code, jamais un nom, une adresse ni une coordonnée. L'offre Miff+ n'est PAS une destination : elle s'ouvre en plein écran
/// (`PaywallPresentation`), sans geste retour pendant les 3 secondes de sa croix.
enum MiffRoute: Hashable {
    case create
    case join
    case miff(String)
    /// Le formulaire d'un lieu : nouveau (`placeId` nil), ou le lieu à modifier, avec la Miff d'où il a été ouvert.
    case placeForm(placeId: String?, miffId: String?)
    case placeDetail(miffId: String, placeId: String)
    /// Le formulaire d'une DEMANDE d'ajout d'un lieu (LIEU-REQ-B) : un membre qui n'est pas le créateur propose un lieu à cette Miff.
    case placeRequestForm(miffId: String)
    /// Le détail d'une demande en attente (la sienne, ou celle qu'un membre a faite au créateur).
    case placeRequestDetail(miffId: String, requestId: String)
    /// Un écran des Réglages (ONB-7) : poussé dans la MÊME pile que les écrans de la Miff, jamais dans une pile imbriquée.
    case settings(SettingsRoute)

    /// Le formulaire d'un lieu, ou celui d'une demande (même écran, même sortie).
    var isPlaceFormScreen: Bool {
        switch self {
        case .placeForm, .placeRequestForm:
            return true
        default:
            return false
        }
    }

    /// Un écran des demandes d'ajout d'un lieu : le formulaire d'une demande, ou le détail d'une demande en attente.
    var isRequestScreen: Bool {
        switch self {
        case .placeRequestForm, .placeRequestDetail:
            return true
        default:
            return false
        }
    }

    var isSettingsScreen: Bool {
        if case .settings = self {
            return true
        }
        return false
    }

    /// « Mes informations » : le seul écran des Réglages qu'un compte sans Miff n'a pas.
    var isMyInfoScreen: Bool {
        self == .settings(.myInfo)
    }

    /// Réglages > Langue : un compte sans Miff n'a pas cette ligne (décision du PM du 2026-11-07), donc pas cet écran non plus.
    var isLanguageScreen: Bool {
        self == .settings(.language)
    }

    /// Les écrans des notifications : un compte sans Miff n'en a pas (rien à notifier).
    var isNotificationScreen: Bool {
        if case .settings(let screen) = self {
            return screen.isNotificationScreen
        }
        return false
    }

    /// Cet écran dépend de cette Miff : il part avec elle quand son accès est perdu.
    func belongs(to miffId: String) -> Bool {
        switch self {
        case .miff(let id):
            return id == miffId
        case .placeDetail(let id, _):
            return id == miffId
        case .placeForm(_, let id):
            return id == miffId
        case .placeRequestForm(let id):
            return id == miffId
        case .placeRequestDetail(let id, _):
            return id == miffId
        case .create, .join, .settings:
            return false
        }
    }
}
