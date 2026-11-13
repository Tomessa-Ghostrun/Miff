import CryptoKit
import Foundation

// SRV-2I : le registre des appareils (ticket `docs/tickets/notification-update-membres.md`, §8 et §9 ; contrat du backend :
// `docs/dev/jetons-appareil-clients.md`). QUAND ce téléphone écrit (ou supprime) son document `users/{uid}/devices/{deviceId}`. Des règles
// PURES, testées sans réseau ni Firebase ; l'accès au système et à Firestore est dans `Services/Push` et `Services/Firebase`. Miroir de
// `domain/push/PushRegistration.kt` d'Android.
//
// COÛT : le document est petit et rarement écrit. Rien n'est écrit sans compte connecté, sans session valide et sans l'accord de la personne ;
// il n'est réécrit que quand quelque chose a CHANGÉ (le jeton, le fuseau, l'interrupteur) ou 30 jours après la dernière écriture.
//
// SÉCURITÉ : ni le jeton ni l'identifiant de session n'apparaissent dans une description texte (journal, rapport de plantage) : tous les types
// ci-dessous cachent leur contenu. Le jeton lui-même n'est JAMAIS gardé sur le téléphone : seulement son empreinte (SHA-256), qui suffit à voir
// qu'il a changé.

/// Ce que ce téléphone a écrit la dernière fois (gardé dans un fichier privé, hors sauvegarde).
struct RegistrationRecord: Equatable, Codable, CustomStringConvertible {
    let uid: String
    let deviceId: String
    let tokenFingerprint: String
    let timeZoneId: String
    let notifyUpdates: Bool
    let writtenAtMillis: Int

    var description: String {
        "RegistrationRecord(redacted)"
    }
}

/// Ce que ce téléphone voudrait que le registre dise maintenant.
struct RegistrationWish: Equatable, CustomStringConvertible {
    let uid: String
    let deviceId: String
    let tokenFingerprint: String
    let timeZoneId: String
    let notifyUpdates: Bool

    var description: String {
        "RegistrationWish(redacted)"
    }
}

enum RegistrationAction: Equatable {
    /// Le registre dit déjà ce qu'il doit dire.
    case none
    /// Écrire le document (le créer, ou le mettre à jour : la couche des données lit d'abord et choisit, contrat §4.1).
    case write(RegistrationReason)

    enum RegistrationReason: Equatable {
        case first
        case newSession
        case tokenChanged
        case settingsChanged
        case refresh
    }
}

enum RegistrationRules {
    /// Un document inchangé est réécrit après 30 jours, à l'ouverture de l'app (ticket §9, contrat §4.3) : il reste vivant pour la purge des inactifs.
    static let refreshAfterMillis = 30 * 24 * 60 * 60 * 1000
    /// Un changement de fuseau s'écrit une fois par jour au plus (contrat §4.3). Un nouveau jeton et un nouvel interrupteur sont immédiats.
    static let minSpacingMillis = 24 * 60 * 60 * 1000
    /// Après une écriture ratée, la suivante attend 10 minutes (le retour au premier plan ne martèle pas un serveur injoignable).
    static let retryAfterFailureMillis = 10 * 60 * 1000

    /// Ce qu'il faut écrire pour que le registre dise `wish`, sachant ce que ce téléphone a écrit (`record`). La version de l'app seule ne
    /// déclenche jamais d'écriture (elle part avec la suivante).
    static func decide(wish: RegistrationWish, record: RegistrationRecord?, nowMillis: Int) -> RegistrationAction {
        guard let record else {
            return .write(.first)
        }
        if record.uid != wish.uid || record.deviceId != wish.deviceId {
            return .write(.newSession)
        }
        if record.tokenFingerprint != wish.tokenFingerprint {
            return .write(.tokenChanged)
        }
        if record.notifyUpdates != wish.notifyUpdates {
            return .write(.settingsChanged)
        }
        if record.timeZoneId != wish.timeZoneId && nowMillis - record.writtenAtMillis >= minSpacingMillis {
            return .write(.settingsChanged)
        }
        if nowMillis - record.writtenAtMillis >= refreshAfterMillis {
            return .write(.refresh)
        }
        return .none
    }

    /// Un nouvel essai est-il permis après un échec à `failedAtMillis` ? (`nil` : aucun échec à attendre.) Une horloge qui recule autorise l'essai.
    static func mayRetry(failedAtMillis: Int?, nowMillis: Int) -> Bool {
        guard let failedAtMillis else {
            return true
        }
        return nowMillis - failedAtMillis >= retryAfterFailureMillis || nowMillis < failedAtMillis
    }
}

/// L'empreinte du jeton : assez pour voir qu'il a changé, sans garder le jeton.
enum TokenFingerprint {
    static func of(_ token: String) -> String {
        SHA256.hash(data: Data(token.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}

/// Les limites des règles Firestore (contrat §2).
enum DeviceEntryLimits {
    static let maxTokenChars = 4096
    static let maxTimeZoneChars = 64
    static let maxAppVersionChars = 32
}

/// La version écrite dans le document : le nom et le numéro de build, coupée aux 32 caractères que la règle accepte (jamais vide).
enum DeviceAppVersion {
    static func of(name: String?, build: String?) -> String {
        let shownName = (name?.isEmpty == false ? name : nil) ?? "0"
        let shownBuild = (build?.isEmpty == false ? build : nil) ?? "0"
        return String("\(shownName) (\(shownBuild))".prefix(DeviceEntryLimits.maxAppVersionChars))
    }
}

/// Un fuseau acceptable pour la règle : une lettre puis `A-Za-z0-9_+:/-`, 64 caractères au plus (contrat §2).
enum TimeZoneShape {
    static func isValid(_ value: String) -> Bool {
        guard (1...DeviceEntryLimits.maxTimeZoneChars).contains(value.unicodeScalars.count),
              let first = value.unicodeScalars.first,
              isLetter(first) else {
            return false
        }
        return value.unicodeScalars.allSatisfy { scalar in
            switch scalar {
            case "A"..."Z", "a"..."z", "0"..."9", "_", "+", ":", "/", "-":
                return true
            default:
                return false
            }
        }
    }

    private static func isLetter(_ scalar: Unicode.Scalar) -> Bool {
        switch scalar {
        case "A"..."Z", "a"..."z":
            return true
        default:
            return false
        }
    }
}
