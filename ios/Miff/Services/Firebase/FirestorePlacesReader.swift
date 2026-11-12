import FirebaseFirestore
import Foundation

// COÛT FIRESTORE et SÉCURITÉ, pour ce fichier : UN `getDocuments` de la collection `miffs/{id}/places`, jamais un écouteur. Les règles
// l'autorisent à TOUT membre d'une Miff qui existe encore (`allow get, list: if isMember(miffId) && miffAlive(miffId)`, backend/
// firestore.rules, `match /places/{placeId}`) : une Miff que la personne a quittée, ou qui a été supprimée, est simplement refusée. La
// lecture coûte une lecture Firestore par lieu (3 pour un créateur gratuit, davantage en Miff+) plus les deux lectures des règles.
// `ProfilePlacesViewModel` (avec `MiffPlacesBook`) décide QUAND la faire : au plus une fois par Miff et par 5 minutes, jamais pour la Miff
// que le Feed a déjà lue. Les lieux reviennent comme le Feed et l'écran de la Miff les ont (même nettoyage, même ordre). Ce que l'on lit
// vient d'un autre téléphone : un document qui ne peut pas être un lieu de cette app n'est pas montré. Rien n'est journalisé, et aucun
// message d'erreur ne sort d'ici (il peut citer un chemin).

final class FirestorePlacesReader: PlacesReader {
    /// Attente maximale d'une lecture, en secondes : hors ligne le SDK répond de lui-même, ceci n'est qu'un plafond.
    static let timeoutSeconds: TimeInterval = 15

    func read(miffId: String) async -> PlacesRead {
        // L'identifiant est vérifié avant de devenir une partie d'un chemin (même forme que les règles).
        guard FirebaseBootstrap.isConfigured, MiffId.isValid(miffId) else {
            return .failed
        }
        do {
            let snapshot = try await timedOperation(seconds: Self.timeoutSeconds) {
                try await Firestore.firestore().collection("miffs").document(miffId)
                    .collection("places").getDocuments()
            }
            // Une copie du téléphone qui est VIDE n'est pas une réponse (rien n'était encore gardé) : jamais dessinée comme « aucun
            // lieu ». Une copie qui a des lieux est montrée telle quelle (elle sera relue plus tard).
            if snapshot.metadata.isFromCache && snapshot.documents.isEmpty {
                return .failed
            }
            let places = snapshot.documents.compactMap { document in
                Place.fromServer(id: document.documentID, miffId: miffId, fields: document.data())
            }
            return .loaded(Place.sortedForList(places))
        } catch {
            return .failed
        }
    }
}
