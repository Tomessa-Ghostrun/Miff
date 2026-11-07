import Foundation

// SÉCURITÉ, pour ce fichier : prénom, nom, numéro de téléphone et photo sont des données personnelles. Ils vivent en mémoire, dans
// l'état de l'écran du Profil, pour la durée de la session seulement : jamais sur le disque, jamais journalisés, jamais dans un
// événement Analytics. `description` ne montre rien.

/// Le profil de la personne connectée, tel que `users/{uid}` le détient (ticket Profil / Réglages, onglet Profil et « Mes informations »).
struct ProfileSummary: Equatable, CustomStringConvertible {
    let firstName: String
    let lastName: String
    /// E.164 (« +33612345678 ») ; nil pour un compte créé avant que le numéro devienne obligatoire.
    let phone: String?
    /// JPEG carré de 20 000 octets au plus.
    let avatarJpeg: Data?

    var displayName: String {
        "\(firstName) \(lastName)".trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var initials: String {
        Initials.of(first: firstName, last: lastName)
    }

    var description: String {
        "ProfileSummary(redacted)"
    }

    /// Lit les champs d'un document `users/{uid}`. Une donnée venue du réseau est vérifiée avant d'être montrée : noms de 1 à 50
    /// unités, numéro de la forme E.164, photo de 20 000 octets au plus. Sans prénom ni nom valides, le profil est inutilisable (nil).
    static func from(fields: [String: Any]) -> ProfileSummary? {
        guard let first = fields["firstName"] as? String, let last = fields["lastName"] as? String,
              ProfileDocument.isNameValid(first), ProfileDocument.isNameValid(last) else {
            return nil
        }
        let phone = (fields["phone"] as? String).flatMap { ProfileDocument.isE164($0) ? $0 : nil }
        let avatar = (fields["avatar"] as? Data).flatMap { bytes -> Data? in
            bytes.isEmpty || bytes.count > ProfileDocument.maxAvatarBytes ? nil : bytes
        }
        return ProfileSummary(
            firstName: first.trimmingCharacters(in: .whitespacesAndNewlines),
            lastName: last.trimmingCharacters(in: .whitespacesAndNewlines),
            phone: phone,
            avatarJpeg: avatar
        )
    }
}

/// Ce que montre l'en-tête du Profil.
enum ProfileTabState: Equatable {
    case loading
    case ready(ProfileSummary)
    /// Le profil n'a pas pu être lu (hors ligne sans cache, refusé, trop lent) : une phrase calme et « Réessayer ».
    case unavailable

    var isReady: Bool {
        if case .ready = self {
            return true
        }
        return false
    }
}

/// Le numéro et le pays tels que « Mes informations » les montre : le pays se déduit de l'indicatif (le pays n'est pas stocké,
/// `backend/data-model.md` §2.1).
struct PhoneDisplay: Equatable {
    let country: PhoneCountry
    let national: String

    static func of(phone: String?, tools: PhoneNumberTools, locale: Locale) -> PhoneDisplay? {
        guard let phone, let detected = tools.detectInternational(phone) else {
            return nil
        }
        let country = tools.country(region: detected.region, locale: locale)
        return PhoneDisplay(country: country, national: detected.national)
    }
}
