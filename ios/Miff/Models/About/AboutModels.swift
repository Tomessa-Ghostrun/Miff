import Foundation

// Réglages > « À propos et aide » (ticket `docs/tickets/reglages-a-propos.md`, ABOUT-1I). Ici, la logique sans écran : les lignes du
// bloc d'aide, l'adresse du formulaire d'avis, ce que copie la ligne de version. Rien ne lit ni n'écrit de donnée : aucune lecture
// Firestore, aucun événement Analytics, aucun appel réseau à l'ouverture de l'écran (critère AP18).

/// Les lignes du bloc « Besoin d'un coup de main ? », dans l'ordre où elles s'affichent.
enum AboutHelpRow: Equatable, Hashable {
    /// L'aide propre à l'iPhone, déjà existante (le lien de Confidentialité reste).
    case detectionHelp
    /// L'aide « notifications coupées », déjà existante (absente des Réglages restreints, comme tout l'écran des notifications).
    case notificationsHelp
    /// « Nous contacter » : l'application e-mail, sans texte pré-rempli. Déplacé ici depuis Confidentialité (décision Q2 du PM).
    case contact
    /// « Un retour sur l'app ? » : le formulaire externe, seulement si son adresse est valide.
    case feedback
}

enum AboutContent {
    /// Les lignes d'aide montrées. `restricted` : un compte sans Miff (Réglages restreints) n'a pas l'aide des notifications ; il garde le
    /// contact, c'est le moment où l'on a le plus besoin d'aide.
    static func helpRows(restricted: Bool, feedbackURL: URL?) -> [AboutHelpRow] {
        var rows: [AboutHelpRow] = [.detectionHelp]
        if !restricted {
            rows.append(.notificationsHelp)
        }
        rows.append(.contact)
        if feedbackURL != nil {
            rows.append(.feedback)
        }
        return rows
    }
}

/// Le formulaire d'avis externe (décision du PM du 2026-11-13). L'adresse vit à UN SEUL endroit : ici. Elle est publique (un formulaire
/// qui reçoit des réponses ; rien de secret). Aucune donnée personnelle n'y est ajoutée : ni nom, ni e-mail, ni identifiant, ni position.
/// Le ticket parle d'un lien « Donner mon avis » ; le libellé retenu par le PM est « Un retour sur l'app ? » (`about_feedback_title`).
/// Ce lien n'ouvre JAMAIS une fiche de la boutique et ne parle ni de note ni d'étoiles.
enum FeedbackForm {
    static let address = "https://forms.gle/5EEH4sEfsnCCLzEx7"

    /// L'adresse du formulaire, ou nil si elle n'est pas valide : la ligne est alors masquée (jamais une ligne morte).
    static var url: URL? {
        validURL(from: address)
    }

    /// Une adresse n'est ouvrable que si elle est en `https://`, sans espace, guillemet, antislash ni `$` (même règle que
    /// `miff.feedbackFormUrl` sur Android, `pop-ins-avis.md` §8), avec un nom d'hôte. Jamais de `http://`, jamais de repli en clair.
    static func validURL(from text: String) -> URL? {
        let forbidden = CharacterSet.whitespacesAndNewlines.union(CharacterSet(charactersIn: "\"\\$"))
        guard text.hasPrefix("https://"), text.rangeOfCharacter(from: forbidden) == nil,
              let url = URL(string: text), url.scheme == "https",
              let host = url.host, !host.isEmpty else {
            return nil
        }
        return url
    }
}

extension BuildInfo {
    /// Ce que copie la ligne de version (décision Q7 du PM) : « Miff 0.1.0 (12) ». Rien d'autre : ni modèle de téléphone, ni identifiant,
    /// ni compte.
    var copyText: String {
        "Miff \(version) (\(build))"
    }
}

/// Là où le texte copié est posé. Un protocole, pour prouver par un test ce qui est copié sans toucher au presse-papiers du système.
protocol TextPasteboard {
    func writeText(_ text: String)
}

enum AboutVersion {
    /// Copie la version, et seulement elle.
    static func copy(_ build: BuildInfo, to pasteboard: TextPasteboard) {
        pasteboard.writeText(build.copyText)
    }
}
