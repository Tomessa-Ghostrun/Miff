import Foundation

/// Versions des textes que la personne a vus en touchant le bouton principal (il n'y a plus de case à cocher depuis
/// la décision du PM du 2026-10-28). Constantes partagées avec Android : à changer en même temps que le texte.
enum ConsentVersions {
    /// Conditions d'utilisation et politique de confidentialité : version publiée de docs/legal (1 à 20 caractères).
    static let terms = "0.1"
    /// Texte de la ligne sur les statistiques et rapports de plantage. Format AAAA-MM-vNN (les règles interdisent de
    /// revenir en arrière).
    static let analytics = "2026-10-v01"
}

/// Ce que la personne a accepté en touchant le bouton principal de l'inscription (« Rejoindre », « Continuer avec
/// Google » ou « Continuer avec Apple ») ou « Continuer » du panneau de consentement. Pas de case : le geste vaut
/// acceptation des conditions, de la politique ET du partage de statistiques anonymes (un seul consentement). Le
/// risque légal et celui des boutiques sont assumés par le PM.
struct ConsentChoice: Equatable {
    let termsAccepted: Bool

    static let implicit = ConsentChoice(termsAccepted: true)
}

/// Ce que l'appareil retient entre l'inscription (ou le panneau) et la création du profil : la version des conditions
/// affichée et le consentement aux statistiques qui va avec. Lié au compte pour ne jamais servir à une autre personne.
/// Ne contient ni mot de passe, ni jeton, ni adresse e-mail.
struct PendingConsent: Equatable {
    let uid: String
    let termsVersion: String
    let analyticsAccepted: Bool
}

/// Valeur d'un champ du document de profil, indépendante de Firestore (la conversion est faite par l'adaptateur).
enum FieldValueSpec: Equatable {
    case string(String)
    case bool(Bool)
    case bytes(Data)
    /// Heure du serveur (jamais celle du téléphone).
    case serverTimestamp
    case null
    case map([String: FieldValueSpec])
}

/// Ce que la personne a saisi dans les deux étapes du profil, prêt à être écrit.
struct NewProfile: Equatable {
    let firstName: String
    let lastName: String
    let avatarJpeg: Data?
    /// Au format E.164.
    let phone: String
}

enum ProfileDocumentError: Error, Equatable {
    case invalidName
    case avatarTooLarge
    case invalidPhone
}

/// L'UNE écriture qui crée `users/{uid}` (backend/data-model.md §8, règles AUTH-B et ONB-B) : toutes les clés d'un coup,
/// l'heure du serveur pour les dates, `analyticsConsent` toujours écrit pour que l'état soit explicite, et le numéro de
/// téléphone en E.164 (OBLIGATOIRE depuis ONB-B : sans lui le serveur refuse le profil). Rien d'autre n'est accepté par
/// les règles (ni e-mail, ni âge, ni date de naissance, ni pays : le pays se déduit du préfixe du numéro).
enum ProfileDocument {
    static let maxNameLength = 50
    static let maxAvatarBytes = 20_000

    static func isNameValid(_ name: String) -> Bool {
        // Unités UTF-16, comme Android : le décompte le plus strict.
        let length = name.trimmingCharacters(in: .whitespacesAndNewlines).utf16.count
        return length >= 1 && length <= maxNameLength
    }

    /// Même forme que les règles : « + » puis 7 à 15 chiffres, le premier non nul (8 à 16 caractères au total).
    /// `\z` (et non `$`) : une fin de ligne après le numéro ne doit pas passer.
    static func isE164(_ phone: String) -> Bool {
        phone.range(of: "^[+][1-9][0-9]{6,14}\\z", options: .regularExpression) != nil
    }

    /// Le numéro n'est jamais recopié dans une erreur : un numéro de téléphone ne va jamais dans un journal.
    static func forCreation(_ profile: NewProfile, consent: PendingConsent) throws -> [String: FieldValueSpec] {
        guard isNameValid(profile.firstName), isNameValid(profile.lastName) else {
            throw ProfileDocumentError.invalidName
        }
        if let avatarJpeg = profile.avatarJpeg, avatarJpeg.count > maxAvatarBytes {
            throw ProfileDocumentError.avatarTooLarge
        }
        guard isE164(profile.phone) else {
            throw ProfileDocumentError.invalidPhone
        }
        var fields: [String: FieldValueSpec] = [
            "firstName": .string(profile.firstName.trimmingCharacters(in: .whitespacesAndNewlines)),
            "lastName": .string(profile.lastName.trimmingCharacters(in: .whitespacesAndNewlines)),
            "phone": .string(profile.phone),
            "createdAt": .serverTimestamp,
            "termsAcceptedAt": .serverTimestamp,
            "termsVersion": .string(consent.termsVersion),
            "analyticsConsent": analyticsConsent(accepted: consent.analyticsAccepted)
        ]
        if let avatarJpeg = profile.avatarJpeg {
            fields["avatar"] = .bytes(avatarJpeg)
        }
        return fields
    }

    private static func analyticsConsent(accepted: Bool) -> FieldValueSpec {
        if accepted {
            return .map([
                "enabled": .bool(true),
                "consentedAt": .serverTimestamp,
                "consentVersion": .string(ConsentVersions.analytics),
                "revokedAt": .null
            ])
        }
        return .map([
            "enabled": .bool(false),
            "consentedAt": .null,
            "consentVersion": .null,
            "revokedAt": .null
        ])
    }
}
