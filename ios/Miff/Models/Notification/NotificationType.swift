import Foundation

// NOTIF-P0 : les notifications du périmètre sans serveur (ticket Notifications §20). Du Swift PUR : rien ici ne demande quoi que ce
// soit au système, ne lit une position ni n'écrit un nom de lieu. Les textes sont ceux du PM, mot pour mot (clés `notif_*` partagées
// avec Android).

/// Où mène le toucher sur une notification (ou sur sa ligne du centre). L'écran Update est le bouton central de la barre ; le Feed est
/// l'onglet des proches.
enum NotificationDestination: Equatable {
    case update
    case feed
    /// L'onglet Profil (une ligne N8 dont la Miff n'est pas connue).
    case profile
    /// Une ligne N8 : le Profil, sur la Miff concernée (la liste des lieux y montre les demandes à valider).
    case placeRequests(miffId: String)
}

/// Les notifications de la phase 0 (N1 à N4, N6, et N7 du mode voyage). N5 (prévenir les autres membres d'une entrée en zone de danger) est ABANDONNÉE : elle exige
/// un serveur, elle n'existe ni ici, ni dans les réglages, ni dans les textes.
enum NotificationType: String, CaseIterable, Codable, Equatable {
    /// Je passe orange (hors zone de danger).
    case n1
    /// Je passe rouge (hors zone de danger).
    case n2
    /// Ma Miff est passée en orange (30 minutes après, si elle l'est toujours).
    case n3
    /// Ma Miff est passée en rouge (idem).
    case n4
    /// J'entre dans une zone de danger de ma Miff.
    case n6
    /// MODE VOYAGE : « Ton mode voyage est terminé » (locale, 08:00 le lendemain du dernier jour ; ticket mode-voyage §9.1).
    case n7
    /// DEMANDE D'AJOUT D'UN LIEU (LIEU-REQ §7.1) : une ligne du centre pour le créateur, jamais une notification du système, jamais un
    /// réglage (HORS PÉRIMÈTRE, voir `allCases`). Ses mots se font à l'affichage (`PlaceRequestLines`).
    case n8

    /// Le périmètre des notifications du système (réglages, programmation) : N8 n'en fait pas partie, c'est une ligne du centre seulement.
    static var allCases: [NotificationType] {
        [.n1, .n2, .n3, .n4, .n6, .n7]
    }

    var titleKey: String {
        switch self {
        case .n1:
            return "notif_n1_title"
        case .n2:
            return "notif_n2_title"
        case .n3:
            return "notif_n3_title"
        case .n4:
            return "notif_n4_title"
        case .n6:
            // Variante A du PM (Q34) : « Tu es dans une zone de danger de ta Miff ».
            return "notif_n6a_title"
        case .n7:
            return "notif_n7_title"
        case .n8:
            // Mots de remplacement, quand le prénom ou la Miff ne sont pas connus à l'affichage.
            return "notif_n8_title"
        }
    }

    var bodyKey: String {
        switch self {
        case .n1:
            return "notif_n1_body"
        case .n2:
            return "notif_n2_body"
        case .n3:
            return "notif_n3_body"
        case .n4:
            return "notif_n4_body"
        case .n6:
            return "notif_n6a_body"
        case .n7:
            return "notif_n7_body"
        case .n8:
            return "notif_n8_body"
        }
    }

    /// Le texte de l'interrupteur de cette notification dans Réglages > Notifications.
    var settingTitleKey: String {
        switch self {
        case .n1:
            return "notif_settings_n1"
        case .n2:
            return "notif_settings_n2"
        case .n3:
            return "notif_settings_n3"
        case .n4:
            return "notif_settings_n4"
        case .n6:
            return "notif_settings_n6"
        case .n7:
            return "notif_settings_n7"
        case .n8:
            // Aucun interrupteur : N8 est hors périmètre (jamais affichée dans Réglages).
            return "notif_n8_title"
        }
    }

    /// N1 et N2 invitent à publier (l'écran Update) ; N3, N4 et N6 renvoient au Feed (en zone de danger publier ne ramène pas au vert :
    /// décision Q34 du PM).
    var destination: NotificationDestination {
        switch self {
        case .n1, .n2:
            return .update
        case .n3, .n4, .n6, .n7:
            return .feed
        case .n8:
            return .profile
        }
    }

    /// Nuit calme (22 h à 7 h) : N1, N3 et N6 arrivent sans son ni vibration ; N2 et N4 gardent leur niveau normal (décision Q30).
    var silentAtNight: Bool {
        switch self {
        case .n1, .n3, .n6:
            return true
        case .n2, .n4, .n7, .n8:
            // N8 n'est jamais une notification du système. N7 part à 8 h : jamais la nuit, donc rien à adoucir.
            return false
        }
    }
}

/// L'heure du mur (millisecondes depuis 1970) : l'âge d'une entrée du centre se compte en temps réel, jamais avec l'horloge monotone de
/// `AppClock`, qui repart de zéro au redémarrage du téléphone.
enum NotificationClock {
    static let wallMillis: () -> Int = { Int(Date().timeIntervalSince1970 * 1000) }
}
