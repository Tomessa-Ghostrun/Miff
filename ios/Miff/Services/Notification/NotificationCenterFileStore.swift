import Foundation

/// Où le centre de notifications garde ses entrées (un double en mémoire dans les tests). Un seul compte à la fois sur le téléphone.
protocol NotificationCenterStoring: AnyObject {
    /// Les entrées de ce compte ; vide si le fichier est absent, illisible ou celui d'un autre compte.
    func load(uid: String) -> [NotificationItem]
    func save(_ items: [NotificationItem], uid: String)
    func clear()
}

/// Le centre de notifications LOCAL, dans UN fichier du stockage privé de l'app (Application Support), EXCLU des sauvegardes (iCloud et
/// ordinateur) : un téléphone restauré depuis une sauvegarde ne retrouve pas l'historique d'une autre personne. Effacé à la déconnexion et
/// à la suppression du compte. Il ne contient ni position, ni adresse, ni nom de lieu, ni photo (voir `NotificationItem`).
///
/// Protection du fichier : « jusqu'à la première authentification » (le système peut relancer l'app en arrière-plan, téléphone
/// verrouillé, pour une zone ou une tâche de fond). Une copie illisible n'est pas crue : elle redonne une liste vide.
///
/// Jamais journalisé, jamais envoyé à Analytics ni à Crashlytics.
final class NotificationCenterFileStore: NotificationCenterStoring {
    private struct Stored: Codable {
        let uid: String
        let items: [NotificationItem]
    }

    private let directory: URL
    private let queue = DispatchQueue(label: "com.tomessa.miff.notification-center")

    private var fileURL: URL {
        directory.appendingPathComponent("notification-center.json")
    }

    init(directory: URL? = nil) {
        if let directory {
            self.directory = directory
        } else {
            let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
                ?? FileManager.default.temporaryDirectory
            self.directory = base.appendingPathComponent("Notifications", isDirectory: true)
        }
    }

    func load(uid: String) -> [NotificationItem] {
        queue.sync {
            guard let data = try? Data(contentsOf: fileURL),
                  let stored = try? JSONDecoder().decode(Stored.self, from: data),
                  stored.uid == uid else {
                return []
            }
            return stored.items
        }
    }

    func save(_ items: [NotificationItem], uid: String) {
        queue.sync {
            guard prepareDirectory(), let data = try? JSONEncoder().encode(Stored(uid: uid, items: items)) else {
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
