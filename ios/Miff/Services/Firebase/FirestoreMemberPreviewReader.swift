import FirebaseFirestore
import Foundation

// COÛT FIRESTORE et SÉCURITÉ, pour ce fichier : UNE lecture ponctuelle (`getDocuments`) de `miffs/{id}/members`, jamais un écouteur. Elle
// coûte autant de lectures que la Miff a de membres (15 au plus, plafond doux) ; `MiffMemberPreviews` décide QUAND la faire (voir son
// en-tête). Ce que l'on lit vient d'autres téléphones : les noms sont nettoyés, une photo de plus de 20 000 octets est ignorée (même
// lecture que l'écran de la Miff, `FirestoreMiffObserver.members(of:)`). Seuls les initiales et la photo sont gardées : ni numéro, ni
// nom entier. Rien n'est journalisé.

/// Rend une réponse UNE seule fois, la première arrivée (la lecture ou le délai) : la suite est ignorée.
private final class FirstAnswer {
    private let lock = NSLock()
    private var continuation: CheckedContinuation<MemberPreview?, Never>?

    init(_ continuation: CheckedContinuation<MemberPreview?, Never>) {
        self.continuation = continuation
    }

    func give(_ answer: MemberPreview?) {
        lock.lock()
        let waiting = continuation
        continuation = nil
        lock.unlock()
        waiting?.resume(returning: answer)
    }
}

final class FirestoreMemberPreviewReader: MemberPreviewReader {
    /// Attente maximale d'une lecture, en nanosecondes : au-delà, la carte garde sa phrase d'aide (sans message d'erreur) et la réponse
    /// tardive est ignorée. Le SDK peut attendre longtemps le réseau avant de répondre : la carte ne doit pas rester en attente.
    static let timeoutNanos: UInt64 = 15_000_000_000

    func read(miffId: String) async -> MemberPreview? {
        guard FirebaseBootstrap.isConfigured, MiffId.isValid(miffId) else {
            return nil
        }
        return await withCheckedContinuation { continuation in
            let first = FirstAnswer(continuation)
            Task {
                first.give(await Self.fetch(miffId: miffId))
            }
            Task {
                try? await Task.sleep(nanoseconds: Self.timeoutNanos)
                first.give(nil)
            }
        }
    }

    private static func fetch(miffId: String) async -> MemberPreview? {
        do {
            let snapshot = try await Firestore.firestore().collection("miffs").document(miffId)
                .collection("members").getDocuments()
            let members = FirestoreMiffObserver.members(of: snapshot)
            // Une Miff a toujours au moins son créateur : une liste vide n'est pas une réponse (cache incomplet hors ligne, fiches pas
            // encore arrivées) mais un échec : la carte garde sa phrase d'aide au lieu d'afficher « 0 membre ».
            guard !members.isEmpty else {
                return nil
            }
            return MemberPreview(members: members)
        } catch {
            return nil
        }
    }
}
