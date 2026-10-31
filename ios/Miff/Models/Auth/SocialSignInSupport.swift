import Foundation

/// Interprétation des erreurs des fenêtres système « Continuer avec Google » et « Continuer avec Apple ». Fermer la
/// fenêtre ne produit AUCUN message (critère S7) ; tout le reste est neutre, sans code technique.
enum SocialSignInErrors {
    /// Domaine et code d'une fenêtre Google fermée par la personne (`kGIDSignInErrorDomain`, `kGIDSignInErrorCodeCanceled`).
    static let googleDomain = "com.google.GIDSignIn"
    static let googleCanceledCode = -5
    /// Domaine et code d'une fenêtre Apple fermée par la personne (`ASAuthorizationError.canceled`).
    static let appleDomain = "com.apple.AuthenticationServices.AuthorizationError"
    static let appleCanceledCode = 1001

    static func googleResult(for error: Error) -> SocialTokenResult {
        let nsError = error as NSError
        if nsError.domain == googleDomain && nsError.code == googleCanceledCode {
            return .cancelled
        }
        return nsError.domain == NSURLErrorDomain ? .failed(.network) : .failed(.unknown)
    }

    static func appleResult(for error: Error) -> SocialTokenResult {
        let nsError = error as NSError
        if nsError.domain == appleDomain && nsError.code == appleCanceledCode {
            return .cancelled
        }
        return nsError.domain == NSURLErrorDomain ? .failed(.network) : .failed(.unknown)
    }
}

/// Le nom donné par Apple UNE SEULE FOIS (à la première connexion). Une connexion suivante n'en porte plus : le nom déjà
/// reçu est conservé (en mémoire seulement) jusqu'à la fin de la session, et jamais écrasé par une absence de nom.
struct AppleNameMemory {
    private(set) var name: PersonNameParts?

    mutating func record(_ newName: PersonNameParts?) {
        name = newName ?? name
    }

    mutating func forget() {
        name = nil
    }
}

/// Jeton à usage unique de « Continuer avec Apple » (protection contre le rejeu) : une chaîne aléatoire de 32 caractères
/// dont seule l'empreinte SHA-256 est donnée à Apple, la chaîne elle-même allant à Firebase. 64 caractères possibles
/// exactement : un octet modulo 64 ne favorise aucun caractère.
enum NonceGenerator {
    static let charset = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_")

    /// `randomBytes(n)` rend `n` octets aléatoires sûrs pour la cryptographie (le système en fournit dans l'app).
    static func make(length: Int = 32, randomBytes: (Int) -> [UInt8]) -> String {
        let bytes = randomBytes(length)
        var result = ""
        for byte in bytes.prefix(length) {
            result.append(charset[Int(byte) % charset.count])
        }
        return result
    }
}
