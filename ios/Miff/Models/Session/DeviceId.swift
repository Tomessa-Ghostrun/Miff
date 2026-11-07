import Foundation

/// L'identifiant de session d'un appareil (SESS-3, ticket `docs/tickets/session-appareil-unique.md`, contrat de
/// `backend/data-model.md`, section « Un compte, un seul appareil »).
///
/// Règles, toutes vérifiées par des tests :
/// - ALÉATOIRE : 24 octets tirés par le générateur cryptographique du système, jamais dérivés de l'appareil (ni numéro de série,
///   ni identifiant de fournisseur, ni identifiant publicitaire, ni heure) ;
/// - généré à CHAQUE connexion, jamais à l'installation : une réinstallation ou une nouvelle connexion en produit un nouveau ;
/// - 16 à 64 caractères parmi `A-Z a-z 0-9 - _` (la forme exacte que les règles Firestore acceptent) ;
/// - jamais écrit dans un journal, un événement Analytics ou un rapport de plantage.
enum DeviceId {
    static let minLength = 16
    static let maxLength = 64
    /// 24 octets = 192 bits, soit 32 caractères une fois encodés.
    static let randomByteCount = 24

    static func isValid(_ value: String) -> Bool {
        guard (minLength...maxLength).contains(value.unicodeScalars.count) else {
            return false
        }
        return value.unicodeScalars.allSatisfy { isAllowed($0) }
    }

    private static func isAllowed(_ scalar: Unicode.Scalar) -> Bool {
        switch scalar {
        case "A"..."Z", "a"..."z", "0"..."9", "-", "_":
            return true
        default:
            return false
        }
    }

    /// Base64 « adapté aux adresses » (RFC 4648 §5), sans le remplissage « = ».
    static func encode(_ bytes: [UInt8]) -> String {
        Data(bytes).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    /// `randomBytes` rend exactement le nombre d'octets demandé, ou nil si le générateur du système a échoué (alors aucun
    /// identifiant n'est inventé : la connexion n'ouvre pas de session et la personne peut réessayer).
    static func generate(randomBytes: (Int) -> [UInt8]?) -> String? {
        guard let bytes = randomBytes(randomByteCount), bytes.count == randomByteCount else {
            return nil
        }
        let identifier = encode(bytes)
        return isValid(identifier) ? identifier : nil
    }
}
