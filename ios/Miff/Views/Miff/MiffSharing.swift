import SwiftUI
import UIKit
import UniformTypeIdentifiers

// SÉCURITÉ, pour ce fichier : le code est un secret que la personne choisit de transmettre. Il va seulement dans la feuille de
// partage du système (la personne choisit qui le reçoit) et dans le presse-papiers (marqué « local seulement » et qui
// expire). Il n'est jamais journalisé. L'app ne lit JAMAIS le presse-papiers elle-même : seul le bouton système « Coller » (qui
// ne déclenche aucune alerte de collage) donne un texte à l'écran.
enum MiffSharing {
    /// Combien de temps le code copié reste dans le presse-papiers : assez pour aller le coller dans une messagerie, pas
    /// plus (un secret ne traîne pas dans le presse-papiers).
    static let clipboardLifetime: TimeInterval = 120

    /// Où les personnes arrivent pour récupérer l'app (décision QM14) : le site public, tant qu'une page dédiée n'existe pas.
    static func downloadUrl() -> String {
        legalPageUrl(baseUrl: AppLinks.legalBaseURL, path: "")
    }

    /// Le message de la feuille de partage, dans la langue de l'expéditeur (ticket §10, cas 24) : nom, code, lien. Rien d'autre.
    static func message(miffName: String, code: String) -> String {
        L10n.format("miff_share_message", MiffName.clean(miffName), JoinCode.grouped(code), downloadUrl())
    }

    /// Copie le code seul. Le presse-papiers est marqué « local seulement » (le code ne passe pas aux autres appareils de la
    /// personne par le presse-papiers universel) et expire. Aucun libellé : certains claviers le montrent.
    @MainActor
    static func copy(code: String) {
        let item: [String: Any] = [UTType.utf8PlainText.identifier: code]
        let options: [UIPasteboard.OptionsKey: Any] = [
            .localOnly: true,
            .expirationDate: Date().addingTimeInterval(clipboardLifetime)
        ]
        UIPasteboard.general.setItems([item], options: options)
    }
}
