import Foundation

// Réglages > « À propos et aide » (ticket `docs/tickets/reglages-a-propos.md`, ABOUT-1I). Ici, la logique sans écran : les lignes de
// chaque bloc, leur présentation, l'adresse du formulaire d'avis, ce que copie la ligne de version. Rien ne lit ni n'écrit de donnée :
// aucune lecture Firestore, aucun événement Analytics, aucun appel réseau à l'ouverture de l'écran (critère AP18).

/// Les lignes de l'écran, dans l'ordre où elles s'affichent. Retour du PM du 2026-11-13 : « Nous contacter » et « Un retour sur l'app ? »
/// sont EN HAUT et MIS EN AVANT ; tout le reste est plus bas, sous « Informations », en lignes de liste discrètes.
enum AboutRow: Equatable, Hashable {
    /// « Nous contacter » : l'application e-mail, sans texte pré-rempli. Déplacé ici depuis Confidentialité (décision Q2 du PM).
    case contact
    /// « Un retour sur l'app ? » : le formulaire externe, seulement si son adresse est valide.
    case feedback
    /// La version, qu'un toucher copie.
    case version
    /// L'aide propre à l'iPhone, déjà existante (le lien de Confidentialité reste).
    case detectionHelp
    /// L'aide « notifications coupées », déjà existante (absente des Réglages restreints, comme tout l'écran des notifications).
    case notificationsHelp
    case terms
    case privacy
    case licenses
}

/// Comment une ligne se présente : une grande carte avec icône (en haut), ou une ligne de liste standard (en bas).
enum AboutPresentation: Equatable {
    case featured
    case standard
}

enum AboutContent {
    /// Les lignes mises en avant, en haut : le contact, puis le formulaire d'avis s'il a une adresse valide.
    static func featuredRows(feedbackURL: URL?) -> [AboutRow] {
        feedbackURL == nil ? [.contact] : [.contact, .feedback]
    }

    /// Les lignes du bloc « Informations », plus bas. `restricted` : un compte sans Miff (Réglages restreints) n'a pas l'aide des
    /// notifications.
    static func informationRows(restricted: Bool) -> [AboutRow] {
        var rows: [AboutRow] = [.version, .detectionHelp]
        if !restricted {
            rows.append(.notificationsHelp)
        }
        rows += [.terms, .privacy, .licenses]
        return rows
    }

    static func presentation(of row: AboutRow) -> AboutPresentation {
        switch row {
        case .contact, .feedback:
            return .featured
        case .version, .detectionHelp, .notificationsHelp, .terms, .privacy, .licenses:
            return .standard
        }
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
