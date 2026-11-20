import Foundation

/// CNC-I : le curseur de la lecture du journal du serveur (la date de l'entrée la plus récente déjà vue), dans UN petit fichier du stockage privé de
/// l'app (Application Support), EXCLU des sauvegardes comme le centre lui-même. Il ne contient qu'un identifiant de compte et une date : jamais un nom,
/// une photo, un lieu ni une position. Effacé à la déconnexion et à la suppression du compte (`NotificationLocalData.eraseSession`). Un fichier absent,
/// illisible ou celui d'un autre compte ne dit rien : la lecture repart de zéro (les 100 entrées les plus récentes).
///
/// Jamais journalisé, jamais envoyé à Analytics ni à Crashlytics.
final class NotificationJournalCursorFileStore: NotificationJournalCursorStoring {
    private struct Stored: Codable {
        let uid: String
        let cursorMillis: Int
    }

    private let directory: URL
    private let queue = DispatchQueue(label: "com.tomessa.miff.notification-journal-cursor")

    private var fileURL: URL {
        directory.appendingPathComponent("notification-journal-cursor.json")
    }

    init(directory: URL? = nil) {
        if let directory {
            self.directory = directory
        } else {
            let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
                ?? FileManager.default.temporaryDirectory
            self.directory = base.appendingPathComponent("NotificationJournal", isDirectory: true)
        }
    }

    func cursor(uid: String) -> Int? {
        queue.sync {
            guard let data = try? Data(contentsOf: fileURL),
                  let stored = try? JSONDecoder().decode(Stored.self, from: data),
                  stored.uid == uid, stored.cursorMillis > 0 else {
                return nil
            }
            return stored.cursorMillis
        }
    }

    func setCursor(_ millis: Int, uid: String) {
        queue.sync {
            let stored = Stored(uid: uid, cursorMillis: millis)
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
