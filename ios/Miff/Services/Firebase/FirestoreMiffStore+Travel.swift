import FirebaseFirestore
import Foundation

// SÉCURITÉ, pour ce fichier : une lecture, de MON propre statut, auprès du serveur. Ce qui est lu a été écrit par ce compte (ou par un
// client modifié) : la fin du voyage est un instant, le jour doit être une date `AAAA-MM-JJ` qui existe ; sinon ce n'est pas un voyage que
// l'on garde. Aucun chemin n'est construit avec une valeur dont la forme n'a pas été vérifiée. Rien n'est journalisé.

extension FirestoreMiffStore {
    func readOwnTravel(miffId: String, uid: String) async throws -> TravelRead? {
        guard MiffId.isValid(miffId), !uid.isEmpty, !uid.contains("/") else {
            throw InvalidWriteError()
        }
        let snapshot = try await firestore()
            .collection("miffs").document(miffId).collection("status").document(uid)
            .getDocument(source: .server)
        guard snapshot.exists, let data = snapshot.data() else {
            return nil
        }
        return Self.travel(fromStatus: data)
    }

    /// Le voyage d'un document de statut, ou nil quand il n'en dit pas un que l'on puisse garder.
    static func travel(fromStatus data: [String: Any]) -> TravelRead? {
        guard (data["presence"] as? String) == PresenceValue.travel.rawValue,
              let until = data["travelUntil"] as? Timestamp,
              let day = data["travelUntilDay"] as? String, TravelDate.parse(day) != nil else {
            return nil
        }
        let since = (data["presenceAt"] as? Timestamp).map { Int($0.dateValue().timeIntervalSince1970 * 1000) }
        let ends = Int(until.dateValue().timeIntervalSince1970 * 1000)
        return TravelRead(period: TravelPeriod(untilMillis: ends, untilDay: day), sinceMillis: since)
    }
}
