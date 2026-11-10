import FirebaseFirestore
import Foundation

// COÛT FIRESTORE et SÉCURITÉ, pour ce fichier : UNE lecture ponctuelle (`getDocuments`) de `miffs/{id}/members`, jamais un écouteur. Elle
// coûte autant de lectures que la Miff a de membres (15 au plus, plafond doux) ; `MiffMemberPreviews` décide QUAND la faire (voir son
// en-tête). Ce que l'on lit vient d'autres téléphones : les noms sont nettoyés, une photo de plus de 20 000 octets est ignorée (même
// lecture que l'écran de la Miff, `FirestoreMiffObserver.members(of:)`). Seuls les initiales et la photo sont gardées : ni numéro, ni
// nom entier. Rien n'est journalisé.

final class FirestoreMemberPreviewReader: MemberPreviewReader {
    func read(miffId: String) async -> MemberPreview? {
        guard FirebaseBootstrap.isConfigured, MiffId.isValid(miffId) else {
            return nil
        }
        do {
            let snapshot = try await Firestore.firestore().collection("miffs").document(miffId)
                .collection("members").getDocuments()
            return MemberPreview(members: FirestoreMiffObserver.members(of: snapshot))
        } catch {
            return nil
        }
    }
}
