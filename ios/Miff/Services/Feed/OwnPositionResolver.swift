import Foundation

// COÛT, CONFIDENTIALITÉ et SÉCURITÉ, pour ce fichier (correctif « Partage ta position pour voir cette photo », bug remonté par le PM, 2026-11-12) :
// - La phrase « Partage ta position pour voir cette photo. » n'est dite que si l'on SAIT que MA position n'est pas publiée dans cette Miff. La vérité est le
//   document `positions/{moi}` du serveur (c'est lui que lisent les règles des photos) ; la note locale de la détection ne dit « publiée » que dans un sens
//   (elle est vide après une reprise du partage, une réinstallation, ou sans copie locale : elle ne prouve jamais « non publiée »).
// - Coût : la note locale d'abord (gratuite : un « oui » suffit), puis, seulement si elle ne dit rien, UNE lecture de MON propre document de position
//   (1 lecture facturée), mémorisée 30 s par Miff : une liste de cartes, ou une carte qui revient à l'écran, ne la relit pas. Deux cartes qui la demandent
//   en même temps partagent UNE lecture. Une réponse inconnue (hors connexion, refusée, déconnecté) n'est PAS mémorisée.
// - Rien n'est écrit, rien n'est supprimé, rien n'est journalisé ; la mémoire ne garde qu'un oui / non par compte et par Miff, jamais une position.
// Miroir de `OwnPositionResolver` d'Android.

/// Ai-je publié MA position dans cette Miff ?
enum OwnPosition: Equatable {
    case published
    case notPublished
    /// Pas su (hors connexion, lecture refusée, déconnecté, lecture en cours) : JAMAIS pris pour « non publiée ».
    case unknown
}

@MainActor
protocol OwnPositionResolving {
    /// Ce qui est su SANS rien lire (note locale « publiée », ou réponse du serveur de moins de 30 s) ; nil : il faudrait lire.
    func known(miffId: String) -> OwnPosition?
    func state(miffId: String) async -> OwnPosition
}

@MainActor
final class OwnPositionResolver: OwnPositionResolving {
    /// Une réponse connue du serveur reste valable 30 s (en millisecondes).
    static let freshMillis = 30_000

    private struct Answer {
        let published: Bool
        let atMillis: Int
    }

    private let myUid: () -> String?
    private let localPublished: (String) -> Bool
    private let server: OwnPositionServerReading
    private let nowMillis: () -> Int
    private var answers: [String: Answer] = [:]
    private var inFlight: [String: Task<Bool?, Never>] = [:]

    init(
        myUid: @escaping () -> String?,
        localPublished: @escaping (String) -> Bool,
        server: OwnPositionServerReading,
        nowMillis: @escaping () -> Int = { Int(Date().timeIntervalSince1970 * 1000) }
    ) {
        self.myUid = myUid
        self.localPublished = localPublished
        self.server = server
        self.nowMillis = nowMillis
    }

    func known(miffId: String) -> OwnPosition? {
        guard let uid = myUid() else {
            return nil
        }
        if localPublished(miffId) {
            return .published
        }
        return freshAnswer(Self.key(uid, miffId)).map { $0 ? OwnPosition.published : OwnPosition.notPublished }
    }

    func state(miffId: String) async -> OwnPosition {
        // Déconnecté : rien n'est su, donc jamais la phrase.
        guard let uid = myUid() else {
            return .unknown
        }
        if localPublished(miffId) {
            return .published
        }
        let key = Self.key(uid, miffId)
        if let published = freshAnswer(key) {
            return published ? .published : .notPublished
        }
        let reading: Task<Bool?, Never>
        if let running = inFlight[key] {
            reading = running
        } else {
            reading = Task { [server] () -> Bool? in
                let exists = await server.exists(miffId: miffId)
                if let exists {
                    self.answers[key] = Answer(published: exists, atMillis: self.nowMillis())
                }
                self.inFlight[key] = nil
                return exists
            }
            inFlight[key] = reading
        }
        let exists = await reading.value
        // Le compte a changé pendant la lecture : la réponse n'est pas pour la personne qui est là maintenant.
        guard myUid() == uid, let exists else {
            return .unknown
        }
        return exists ? .published : .notPublished
    }

    /// Fin de session : les réponses sont oubliées.
    func clear() {
        answers = [:]
    }

    private func freshAnswer(_ key: String) -> Bool? {
        guard let answer = answers[key], nowMillis() - answer.atMillis < Self.freshMillis else {
            return nil
        }
        return answer.published
    }

    private static func key(_ uid: String, _ miffId: String) -> String {
        "\(uid)/\(miffId)"
    }
}
