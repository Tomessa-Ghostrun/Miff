import FirebaseFirestore
import Foundation

// CNC-I (ticket docs/tickets/centre-notifications-complet.md §8.5) : UNE lecture ponctuelle de `miffs/{miffId}/members/{uid}`, la fiche d'UN membre, pour
// dessiner sa photo (ou ses initiales) et écrire son prénom dans une ligne du centre quand le Feed ne l'a pas déjà lue. Jamais un écouteur. `CenterFaces`
// décide QUAND la faire (une par membre et par session, une douzaine au plus à chaque ouverture du centre, jamais en boucle après un échec).
//
// SÉCURITÉ et CONFIDENTIALITÉ : ce que l'on lit vient d'un autre téléphone : le prénom est nettoyé, une photo de plus de 20 000 octets est ignorée (même
// lecture que l'écran de la Miff, `FirestoreMiffObserver.members(of:)`). Seuls le prénom, les initiales et la photo sont gardés : ni numéro, ni nom entier.
// Rien n'est journalisé, aucune erreur n'est montrée.

final class FirestoreMemberFaceReader: MemberFaceReading {
    /// Attente maximale d'une lecture : au-delà, le visage reste ce qu'il était (le logo ou les initiales), sans message d'erreur.
    static let timeoutNanos: UInt64 = 15_000_000_000

    func read(miffId: String, uid: String) async -> FaceRead {
        guard FirebaseBootstrap.isConfigured, MiffId.isValid(miffId), NotificationJournal.isIdentifier(uid) else {
            return .failed
        }
        return await TimedAnswer.of(nanoseconds: Self.timeoutNanos, fallback: FaceRead.failed) {
            await Self.fetch(miffId: miffId, uid: uid)
        }
    }

    private static func fetch(miffId: String, uid: String) async -> FaceRead {
        do {
            let reference = Firestore.firestore().collection("miffs").document(miffId)
                .collection("members").document(uid)
            let snapshot = try await reference.getDocument()
            guard snapshot.exists else {
                // « Pas dans le cache » n'est pas « parti » : seul le SERVEUR peut dire qu'une fiche n'existe pas (le mode avion n'est pas un départ).
                return snapshot.metadata.isFromCache ? .failed : .gone
            }
            guard let face = face(of: snapshot) else {
                return .failed
            }
            return .found(face)
        } catch {
            let nsError = error as NSError
            // Un refus des règles : la Miff n'est plus celle de la personne (ou le membre en est parti) : le logo et « Un proche ».
            if nsError.domain == FirestoreErrorNumber.domain && nsError.code == FirestoreErrorNumber.permissionDenied {
                return .gone
            }
            return .failed
        }
    }

    private static func face(of snapshot: DocumentSnapshot) -> ActorFace? {
        guard let data = snapshot.data(), let first = data["firstName"] as? String,
              let last = data["lastName"] as? String else {
            return nil
        }
        var photo = data["avatar"] as? Data
        if let bytes = photo, bytes.isEmpty || bytes.count > ProfileDocument.maxAvatarBytes {
            photo = nil
        }
        let cleanFirst = MiffName.clean(first)
        let cleanLast = MiffName.clean(last)
        let firstTrimmed = cleanFirst.trimmingCharacters(in: .whitespacesAndNewlines)
        let lastTrimmed = cleanLast.trimmingCharacters(in: .whitespacesAndNewlines)
        return ActorFace(
            firstName: firstTrimmed.isEmpty ? lastTrimmed : firstTrimmed,
            initials: Initials.of(first: cleanFirst, last: cleanLast),
            photo: photo
        )
    }
}
