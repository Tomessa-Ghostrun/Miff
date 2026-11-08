import Foundation

// SÉCURITÉ, pour ce fichier : l'adresse de la photo Google et la photo elle-même sont des données personnelles. Elles ne sont
// jamais journalisées, jamais dans un événement Analytics, jamais dans un message d'erreur ; la photo ne vit qu'en mémoire jusqu'à
// ce qu'elle soit écrite avec le profil comme n'importe quelle photo de profil (ou jetée).

/// Quelle adresse de photo Google on accepte de télécharger, et à quelle taille (la photo Google devient la photo de profil de
/// la personne qui s'inscrit avec Google, décision du PM du 2026-11-08). Mêmes règles qu'Android (`GooglePhotoUrl.kt`).
///
/// Seules les adresses `https` de `googleusercontent.com` (et ses sous-domaines, par exemple `lh3.googleusercontent.com`) sont
/// acceptées : l'adresse vient du fournisseur de connexion, mais c'est quand même une donnée venue du réseau, donc rien d'autre
/// n'est jamais contacté (pas d'autre domaine, pas d'identifiant dans l'adresse, pas d'autre port). L'image que Google sert à
/// un compte SANS photo (une silhouette générique) est reconnue et refusée : ce serait une photo de profil pire que pas de photo.
enum GooglePhotoURL {
    /// Côté demandé à Google, en pixels : un peu plus que les 160 pixels de l'avatar enregistré, pour que la réduction reste nette.
    static let requestSide = 256

    private static let host = "googleusercontent.com"
    private static let maxLength = 2_048

    /// `...=s96-c`, `...=s96`, `...=w96-h96-c` : la taille que Google lit à la fin de l'adresse.
    private static let sizeSuffix = "=[swh][0-9]+(-[A-Za-z0-9]+)*$"
    /// Ancien format : `.../s96-c/photo.jpg`.
    private static let sizeSegment = "/s[0-9]+(-c)?/[^/]+$"
    /// Ancien format de la silhouette : le dossier fait uniquement de « A » que Google utilisait pour un compte sans photo.
    private static let legacyPlaceholder = "/A{11}/"

    /// L'adresse à télécharger pour la photo donnée par le fournisseur, ou nil s'il n'y a rien d'acceptable (pas d'adresse, pas
    /// Google, pas https, image générique). La taille demandée est `requestSide`.
    static func select(_ provided: URL?) -> URL? {
        guard let provided, isAllowed(provided), !isGenericPicture(provided) else {
            return nil
        }
        let sized = withRequestedSize(provided)
        return isAllowed(sized) ? sized : nil
    }

    /// Vrai pour une adresse qu'on peut contacter : https, un domaine d'images Google, pas d'identifiant, le port par défaut.
    static func isAllowed(_ url: URL) -> Bool {
        guard url.absoluteString.count <= maxLength,
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
              components.scheme?.lowercased() == "https",
              let candidate = components.host?.lowercased(),
              url.host?.lowercased() == candidate,
              candidate == host || candidate.hasSuffix("." + host),
              components.user == nil,
              components.password == nil,
              components.port == nil || components.port == 443 else {
            return false
        }
        return true
    }

    /// La silhouette générique que Google sert à un compte qui n'a pas de photo à lui.
    static func isGenericPicture(_ url: URL) -> Bool {
        let path = url.path
        return path.contains("/default-user") || path.range(of: legacyPlaceholder, options: .regularExpression) != nil
    }

    private static func withRequestedSize(_ url: URL) -> URL {
        // Sans le fragment (jamais utile). Une adresse qui porte déjà une requête est laissée telle quelle : sa taille, s'il y en
        // a une, n'est pas à nous de la deviner.
        var text = url.absoluteString
        if let hash = text.firstIndex(of: "#") {
            text = String(text[..<hash])
        }
        guard !text.contains("?") else {
            return URL(string: text) ?? url
        }
        let size = "s\(requestSide)-c"
        let lastSegment = text.split(separator: "/", omittingEmptySubsequences: false).last.map(String.init) ?? ""
        let sized: String
        if let range = text.range(of: sizeSuffix, options: .regularExpression) {
            sized = text.replacingCharacters(in: range, with: "=\(size)")
        } else if let range = text.range(of: sizeSegment, options: .regularExpression) {
            sized = text.replacingCharacters(in: range, with: "/\(size)/\(lastSegment)")
        } else if !lastSegment.contains("=") {
            sized = "\(text)=\(size)"
        } else {
            sized = text
        }
        return URL(string: sized) ?? url
    }
}
