#if REVIEW_PROMPT
import Foundation
#if canImport(UIKit)
import UIKit
#endif

// CONFIDENTIALITÉ, pour ce fichier : il ouvre le formulaire externe ou le courrier d'aide, et demande l'avis au store. Rien de la personne
// (nom, e-mail, Miff, position, identifiant, jeton) n'est ajouté à une adresse ni à un message. Il n'envoie aucun événement et ne
// journalise rien. Miroir de `StoreReview.kt` d'Android (sans le repli sur la fiche du store : voir plus bas).

/// Ouvre une adresse par le système (le navigateur, l'application de courrier) et dit si quelque chose l'a prise.
protocol ReviewLinkOpener {
    @MainActor
    func open(_ url: URL) async -> Bool
}

#if canImport(UIKit)
struct SystemReviewLinkOpener: ReviewLinkOpener {
    @MainActor
    func open(_ url: URL) async -> Bool {
        await UIApplication.shared.open(url, options: [:])
    }
}
#endif

/// Fait ce que la fenêtre demande et dit si quelque chose s'est ouvert (faux : l'écran le dit calmement, jamais un geste muet).
///
/// - « J'adore ! » : la demande d'avis du store est la fenêtre NATIVE d'Apple (`requestStoreReview`, donné par l'écran). Apple limite son
///   affichage (au plus quelques fois par an, jamais dans un build TestFlight) et ne dit pas si elle s'est affichée : Miff n'en promet
///   jamais l'affichage, et n'écoute jamais ce qui y est donné. Pas de repli sur la fiche du store (Android en a un) : l'identifiant de
///   l'app dans l'App Store n'existe qu'après la première publication, et l'API ne signale aucun échec.
/// - « Peux faire mieux... » : le formulaire dans le navigateur (HTTPS seulement, adresse telle quelle) ; si rien ne peut l'ouvrir, le
///   courrier à l'aide ; si rien ne s'ouvre, faux.
@MainActor
struct ReviewEffectPerformer {
    let opener: ReviewLinkOpener
    /// L'adresse de courrier d'aide (`mailto:`), bâtie par composants d'URL ; nil si elle ne peut pas l'être.
    let supportMailURL: () -> URL?
    let requestStoreReview: () -> Void

    func perform(_ effect: ReviewEffect) async -> Bool {
        switch effect {
        case .requestStoreReview:
            requestStoreReview()
            return true
        case .openMail:
            return await openMail()
        case .openFeedbackForm(let address):
            // Contrôlé une seconde fois ici : jamais d'adresse en clair ni d'un autre schéma, quoi que dise la configuration.
            if let url = ReviewFeedbackForm.openableURL(address), await opener.open(url) {
                return true
            }
            return await openMail()
        }
    }

    private func openMail() async -> Bool {
        guard let url = supportMailURL() else {
            return false
        }
        return await opener.open(url)
    }
}
#endif
