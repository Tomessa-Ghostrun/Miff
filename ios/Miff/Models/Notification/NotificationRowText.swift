import Foundation

// Les mots d'une ligne du centre, faits quand la ligne est DESSINÉE (rien de ce qui suit n'est jamais écrit dans le fichier du centre).

struct NotificationRowText: Equatable {
    let title: String
    let body: String?
    /// « Dans la Miff « … » » : seulement quand le compte a plusieurs Miffs ; jamais pour une ligne de demande (sa phrase nomme déjà la Miff).
    let miffName: String?
}

enum NotificationRowTexts {
    /// - Parameters:
    ///   - count: combien de demandes la ligne représente (1 pour toute autre ligne).
    ///   - miffName: le nom de la Miff, seulement quand le compte en a plusieurs.
    ///   - requestMiffName: le nom de la Miff, TOUJOURS connu quand l'annuaire est lu (la phrase d'une demande dit pour quelle Miff).
    ///   - firstName: le prénom de la personne qui a demandé, tel que l'app le sait MAINTENANT.
    static func text(
        for item: NotificationItem,
        count: Int,
        miffName: String?,
        requestMiffName: String?,
        firstName: String?
    ) -> NotificationRowText {
        guard item.type == .n8 else {
            return NotificationRowText(
                title: L10n.string(item.type.titleKey), body: L10n.string(item.type.bodyKey), miffName: miffName
            )
        }
        // Sans prénom ou sans nom de Miff (pas encore lus), la ligne garde ses mots génériques : jamais un blanc ni un prénom inventé.
        guard let firstName, !firstName.isEmpty, let requestMiffName, !requestMiffName.isEmpty else {
            return NotificationRowText(
                title: L10n.string(item.type.titleKey), body: L10n.string(item.type.bodyKey), miffName: nil
            )
        }
        let sentence = count > 1
            ? L10n.format("place_request_center_many", firstName, count, requestMiffName)
            : L10n.format("place_request_center_one", firstName, requestMiffName)
        return NotificationRowText(title: sentence, body: nil, miffName: nil)
    }
}
