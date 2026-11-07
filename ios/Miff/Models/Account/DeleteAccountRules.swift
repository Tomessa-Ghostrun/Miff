import Foundation

/// Les règles d'affichage de la suppression du compte, en Swift pur (testées sans interface).
enum DeleteAccountRules {
    /// L'écran reste allumé SEULEMENT pendant que la procédure tourne : pas pendant une pause (pas de réseau), un arrêt, une
    /// ré-authentification, une demande de reconnexion ni la fin (critère du ticket, §3.2 D, et SI5 : l'iPhone se suspend dès que l'écran
    /// se verrouille, la procédure reprendrait alors à la réouverture).
    static func keepsScreenOn(_ state: AccountDeletionState) -> Bool {
        switch state {
        case .idle, .running:
            return true
        case .waitingForNetwork, .needsRecentLogin, .stuck, .needsSignIn, .done:
            return false
        }
    }

    /// L'UNE ligne d'étape (maquettes D1 et D2) : « Miff 1 sur 2 » tant que les Miffs partent (les rejointes d'abord, puis les créées),
    /// « On supprime ton profil… » une fois qu'elles sont faites, rien pendant la lecture du compte. Jamais un pourcentage, jamais le
    /// détail des lots.
    static func stepLine(_ step: DeletionStep) -> String? {
        switch step {
        case .checking:
            return nil
        case .profile:
            return L10n.string("settings_delete_progress_profile")
        case .miff(let index, let total, _):
            return L10n.format("settings_delete_progress_miff", index, total)
        }
    }

    /// Plus de trois personnes à retirer : la phrase d'attente plus longue (maquette D3).
    static func isLongWait(_ step: DeletionStep) -> Bool {
        if case .miff(_, _, let longWait) = step {
            return longWait
        }
        return false
    }

    /// « « {nom} » : N autres proches », ou le nom seul quand il n'y a personne d'autre (jamais « 0 proche »).
    static func ownedRowText(_ miff: OwnedMiff, fallbackName: String) -> String {
        let name = miff.name.isEmpty ? fallbackName : miff.name
        if miff.otherMembers == 0 {
            return L10n.format("settings_delete_owned_row_alone", name)
        }
        return String.localizedStringWithFormat(L10n.string("settings_delete_owned_row"), name, miff.otherMembers)
    }

    /// La phrase sous la liste des Miffs créées (maquettes A7 et A8) : ce qui arrive aux autres selon le nombre de Miffs et de proches.
    static func ownedExplainKey(owned: [OwnedMiff]) -> String {
        let anyOthers = owned.contains { $0.otherMembers > 0 }
        switch (anyOthers, owned.count == 1) {
        case (true, true):
            return "settings_delete_owned_explain_one"
        case (true, false):
            return "settings_delete_owned_explain"
        case (false, true):
            return "settings_delete_owned_explain_alone"
        case (false, false):
            return "settings_delete_owned_explain_alone_many"
        }
    }
}
