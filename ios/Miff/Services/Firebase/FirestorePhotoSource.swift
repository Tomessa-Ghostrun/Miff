import FirebaseFirestore
import Foundation

// COÛT, CONFIDENTIALITÉ et SÉCURITÉ, pour ce fichier (FEED-8) : le Feed LIT et n'écrit ni ne supprime jamais une photo.
// - UN `get` d'UN document `miffs/{id}/photos/{uid}` (jamais une liste, jamais une écoute), à l'apparition de la carte, seulement si le statut
//   dit `hasPhoto` et que la dernière update a moins de 23 h (l'appelant décide). Coût : 3 lectures de règles + 1 lecture du document = 4
//   lectures facturées par photo d'un AUTRE membre (2 + 1 pour la sienne), voir docs/dev/surveillance-quotas.md.
// - La lecture se fait auprès du SERVEUR (`source: .server`), comme « Localiser » et la lecture de ma propre photo (décision commune de l'Update :
//   jamais une vieille copie du cache qui passerait pour la photo d'aujourd'hui ; hors ligne : « injoignable », la carte réessaie au retour du
//   réseau). LIMITE CONNUE, déjà écrite côté backend (backend/data-model.md, point 27) : le SDK Firestore peut garder dans son cache local
//   persistant le document qu'il vient de lire, octets de la photo compris, jusqu'à la purge du cache (déconnexion, perte d'accès à une Miff).
//   CE code, lui, ne l'écrit nulle part : la copie qu'il garde est en mémoire (`PhotoCache`), jamais dans un fichier ni dans la photothèque.
// - Un refus des règles est AMBIGU (réciprocité, partage arrêté, expirée) : il est rendu tel quel (`refused`), jamais pris pour « expirée ».
// - Ce qui est lu est nettoyé avant d'être utilisé (`SharedPhoto.fromServer`) : quelqu'un d'autre l'a écrit. Aucun chemin n'est construit avec
//   un identifiant dont la forme n'a pas été vérifiée. Rien n'est journalisé.

final class FirestorePhotoSource: PhotoSource {
    /// Hors ligne, une lecture auprès du serveur se termine d'elle-même par « indisponible » ; ceci n'est que le plafond.
    private let timeoutSeconds: TimeInterval

    init(timeoutSeconds: TimeInterval = 15) {
        self.timeoutSeconds = timeoutSeconds
    }

    func fetch(miffId: String, uid: String, nowMillis: Int) async -> PhotoFetch {
        guard FirebaseBootstrap.isConfigured, MiffId.isValid(miffId), FeedFirestoreMapping.isUid(uid) else {
            return .absent
        }
        do {
            let snapshot = try await timedOperation(seconds: timeoutSeconds) {
                try await Firestore.firestore().collection("miffs").document(miffId)
                    .collection("photos").document(uid).getDocument(source: .server)
            }
            guard snapshot.exists, let fields = snapshot.data() else {
                return .absent
            }
            return Self.photo(fields: fields, nowMillis: nowMillis)
        } catch is OperationTimedOut {
            return .unreachable
        } catch {
            return Self.fetch(forError: error)
        }
    }

    /// Les champs d'un document de photo, nettoyés : ce qui n'est pas une photo de cette app est « absent » (rien n'est dessiné).
    static func photo(fields: [String: Any], nowMillis: Int) -> PhotoFetch {
        let raw = RawPhoto(
            jpeg: fields["jpeg"] as? Data,
            createdAtMillis: millis(fields["createdAt"]),
            expiresAtMillis: millis(fields["expiresAt"]),
            lat: (fields["lat"] as? NSNumber)?.doubleValue,
            lng: (fields["lng"] as? NSNumber)?.doubleValue,
            measuredAtMillis: millis(fields["measuredAt"])
        )
        guard let photo = SharedPhoto.fromServer(raw, nowMillis: nowMillis) else {
            return .absent
        }
        return .photo(photo)
    }

    /// Ce qu'une erreur de Firestore veut dire pour la carte : un refus est rendu tel quel, un serveur injoignable aussi, le reste n'est
    /// « rien à montrer ». Jamais un message de l'erreur (elle peut citer un chemin).
    static func fetch(forError error: Error) -> PhotoFetch {
        let nsError = error as NSError
        guard nsError.domain == FirestoreErrorNumber.domain else {
            return .absent
        }
        switch nsError.code {
        case FirestoreErrorNumber.permissionDenied:
            return .refused
        case FirestoreErrorNumber.unavailable, FirestoreErrorNumber.deadlineExceeded:
            return .unreachable
        default:
            return .absent
        }
    }

    private static func millis(_ value: Any?) -> Int? {
        (value as? Timestamp).map { Int($0.dateValue().timeIntervalSince1970 * 1000) }
    }
}
