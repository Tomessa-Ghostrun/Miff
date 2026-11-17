import Foundation

/// Les images de « Depuis ta dernière visite » (NOTIF-P1I) dans UN fichier du stockage privé de l'app (Application Support), EXCLU des
/// sauvegardes, propre à un compte, effacé à la déconnexion, à la suppression du compte et à la perte de session. Il ne contient ni position,
/// ni nom de lieu, ni nom de personne, ni photo : des identifiants, le mot d'une présence, une catégorie et des heures. Une copie illisible
/// (abîmée, d'une version plus récente) n'est pas crue : elle redonne une mémoire vide, jamais un plantage. Écrit de façon SYNCHRONE : une app
/// fermée de force juste après l'affichage du Feed retrouve, à l'ouverture suivante, ce qui était SUR L'ÉCRAN (critère C1-T20).
/// Jamais journalisé, jamais envoyé à Analytics ni à Crashlytics.
final class LastVisitFileStore: LastVisitStoring {
    private struct StoredMember: Codable {
        let uid: String
        let muted: Bool
        let presence: String?
        let place: String?
        let presenceAt: Int?
        let lastUpdateAt: Int?
        let silent: Bool
    }

    private struct StoredState: Codable {
        let miffId: String
        let members: [StoredMember]
    }

    private struct StoredMemory: Codable {
        let uid: String
        let states: [StoredState]
        let lastMiffId: String?
    }

    private let directory: URL
    private let queue = DispatchQueue(label: "com.tomessa.miff.notification-last-visit")

    private var fileURL: URL {
        directory.appendingPathComponent("last-visit.json")
    }

    init(directory: URL? = nil) {
        if let directory {
            self.directory = directory
        } else {
            let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
                ?? FileManager.default.temporaryDirectory
            self.directory = base.appendingPathComponent("NotificationLastVisit", isDirectory: true)
        }
    }

    func load(uid: String) -> LastVisitMemory {
        queue.sync {
            guard let data = try? Data(contentsOf: fileURL),
                  let stored = try? JSONDecoder().decode(StoredMemory.self, from: data),
                  stored.uid == uid else {
                return LastVisitMemory()
            }
            var states: [String: VisitState] = [:]
            for state in stored.states {
                states[state.miffId] = VisitState(miffId: state.miffId, members: state.members.map(Self.member))
            }
            return LastVisitMemory(states: states, lastMiffId: stored.lastMiffId)
        }
    }

    func save(_ memory: LastVisitMemory, uid: String) {
        queue.sync {
            let states = memory.states.values.map { state in
                StoredState(miffId: state.miffId, members: state.members.map(Self.stored))
            }
            let stored = StoredMemory(uid: uid, states: states, lastMiffId: memory.lastMiffId)
            guard prepareDirectory(), let data = try? JSONEncoder().encode(stored) else {
                return
            }
            try? data.write(to: fileURL, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        }
    }

    func clear() {
        queue.sync {
            try? FileManager.default.removeItem(at: directory)
        }
    }

    private static func stored(_ member: VisitMember) -> StoredMember {
        StoredMember(
            uid: member.uid,
            muted: member.muted,
            presence: member.presence?.rawValue,
            place: member.place?.visitCode,
            presenceAt: member.presenceAtMillis,
            lastUpdateAt: member.lastUpdateAtMillis,
            silent: member.silent
        )
    }

    private static func member(_ stored: StoredMember) -> VisitMember {
        VisitMember(
            uid: stored.uid,
            muted: stored.muted,
            presence: PresenceValue.fromWire(stored.presence),
            place: stored.place.flatMap { PlaceKind(visitCode: $0) },
            presenceAtMillis: stored.presenceAt,
            lastUpdateAtMillis: stored.lastUpdateAt,
            silent: stored.silent
        )
    }

    private func prepareDirectory() -> Bool {
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            var folder = directory
            var values = URLResourceValues()
            values.isExcludedFromBackup = true
            try folder.setResourceValues(values)
            return true
        } catch {
            return false
        }
    }
}
