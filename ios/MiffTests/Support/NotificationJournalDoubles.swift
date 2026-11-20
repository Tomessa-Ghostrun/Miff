import Foundation
@testable import Miff

// CNC-I : doubles pour les tests du journal des notifications. Aucun ne touche au réseau, au système ni au disque.

/// L'heure et le compte d'un test du journal, que le test fait avancer ou changer à la main.
final class JournalTestClock {
    var now = 1_800_000_000_000
    var uid: String? = "uidA"
    var sessionValid = true
}

/// Le journal du serveur en mémoire : rend la réponse qu'on lui donne, et se souvient de ce qu'on lui a demandé.
final class FakeJournalGateway: NotificationJournalGateway {
    var answer: JournalRead = .loaded([])
    /// Appelé pendant une lecture (la personne se déconnecte pendant que le serveur répond).
    var whileReading: (() -> Void)?
    private(set) var readCalls: [Int?] = []
    private(set) var deletedBatches: [[String]] = []
    private(set) var deleteAllCalls = 0

    func read(uid: String, sinceMillis: Int?) async -> JournalRead {
        readCalls.append(sinceMillis)
        whileReading?()
        return answer
    }

    func delete(uid: String, entryIds: [String]) {
        deletedBatches.append(entryIds)
    }

    func deleteAll(uid: String) async {
        deleteAllCalls += 1
    }
}

/// Une lecture de fiche qui ne trouve personne (les tests de l'écran n'ont aucun serveur).
final class SilentFaceReader: MemberFaceReading {
    func read(miffId: String, uid: String) async -> FaceRead {
        .failed
    }
}
