import Combine
import Foundation
import SwiftUI

// ALERTE « Je suis en danger » : ce que la coquille de l'app reçoit de l'alerte. Ce fichier est compilé dans TOUS les builds et ne contient AUCUN code
// de la fonction : dans un build sans la condition de compilation `ALERT_DANGER` (Release), le signal ne change jamais et la structure ne porte rien
// d'autre. Même principe que `ReviewPromptServices` (project.yml : Debug et Internal ont le drapeau, Release non ; contrôle : ci/check-binaries.sh).

/// « Une alerte est en cours » (de moi, dans une Miff quelconque) : la coquille le lit pour taire la bulle d'incitation orange/rouge et le compte à rebours
/// (jamais deux messages qui se contredisent, ticket C9), et l'écran d'une Miff pour prévenir avant d'arrêter le partage ou de quitter la Miff d'origine.
/// Toujours vide sans la fonction.
@MainActor
final class DangerAlertSignal: ObservableObject {
    @Published var inProgress = false
    /// La Miff d'origine de l'alerte en cours (un identifiant seulement), ou nil.
    @Published var miffId: String?
}

private struct DangerAlertSignalKey: EnvironmentKey {
    static let defaultValue: DangerAlertSignal? = nil
}

extension EnvironmentValues {
    /// nil (aperçus, builds sans la fonction) : aucune alerte à signaler.
    var dangerAlertSignal: DangerAlertSignal? {
        get { self[DangerAlertSignalKey.self] }
        set { self[DangerAlertSignalKey.self] = newValue }
    }
}

struct DangerAlertShell {
    let signal: DangerAlertSignal
    #if ALERT_DANGER
    let model: DangerAlertViewModel
    /// « Voir la Miff {nom} » : la Miff d'origine devient celle que le Feed affiche.
    let onSelectMiff: (String) -> Void
    #endif
}

/// La phrase d'un arrêt de partage ou d'un départ, avec (si une alerte de moi est en cours dans CETTE Miff) la ligne « Tu as une alerte en cours dans cette Miff : elle
/// sera retirée. » : le lot qui arrête le partage, quitte ou retire supprime l'alerte, sans notification (les règles l'exigent), et la personne le sait AVANT de confirmer.
@MainActor
func dangerAlertWarning(_ message: String, miffId: String, signal: DangerAlertSignal?) -> String {
    guard let signal, signal.inProgress, signal.miffId == miffId else {
        return message
    }
    return message + "\n\n" + L10n.string("miff_alert_removed_warning")
}
