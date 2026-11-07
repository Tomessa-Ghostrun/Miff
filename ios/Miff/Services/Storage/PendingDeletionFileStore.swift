import Foundation

/// Le marqueur « une suppression est en cours », dans UN fichier du stockage privé de l'app (Application Support), EXCLU des
/// sauvegardes (iCloud et ordinateur) : il ne contient que des identifiants opaques (la Miff et le compte qui a lancé la
/// suppression), ni nom, ni code. Il survit à une déconnexion et à un redémarrage : la suppression reprend quand le même compte
/// revient ; un autre compte sur le même téléphone ne le voit pas.
///
/// Les écritures sont SYNCHRONES : le marqueur est sur le disque avant la première écriture vers le serveur (une app tuée
/// aussitôt reprend quand même). La protection du fichier est « jusqu'à la première authentification » : lisible dès que
/// l'appareil a été déverrouillé une fois après son démarrage, donc aussi quand la suppression se termine écran verrouillé.
final class PendingDeletionFileStore: PendingDeletionStore {
    private struct Entry: Codable, Hashable {
        let uid: String
        let miffId: String
    }

    private let directory: URL
    private let lock = NSLock()

    private var fileURL: URL {
        directory.appendingPathComponent("pending-deletions.json")
    }

    init(directory: URL? = nil) {
        if let directory {
            self.directory = directory
        } else {
            let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
                ?? FileManager.default.temporaryDirectory
            self.directory = base.appendingPathComponent("PendingDeletions", isDirectory: true)
        }
    }

    func ids(uid: String) -> Set<String> {
        lock.lock()
        defer { lock.unlock() }
        return Set(readAll().filter { $0.uid == uid }.map { $0.miffId })
    }

    func add(uid: String, miffId: String) {
        lock.lock()
        defer { lock.unlock() }
        var entries = readAll()
        entries.insert(Entry(uid: uid, miffId: miffId))
        write(entries)
    }

    func remove(uid: String, miffId: String) {
        lock.lock()
        defer { lock.unlock() }
        var entries = readAll()
        entries.remove(Entry(uid: uid, miffId: miffId))
        write(entries)
    }

    private func readAll() -> Set<Entry> {
        guard let data = try? Data(contentsOf: fileURL),
              let entries = try? JSONDecoder().decode([Entry].self, from: data) else {
            return []
        }
        return Set(entries)
    }

    private func write(_ entries: Set<Entry>) {
        if entries.isEmpty {
            try? FileManager.default.removeItem(at: fileURL)
            return
        }
        guard let data = try? JSONEncoder().encode(Array(entries)) else {
            return
        }
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            var folder = directory
            var values = URLResourceValues()
            values.isExcludedFromBackup = true
            try? folder.setResourceValues(values)
            try data.write(to: fileURL, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        } catch {
            // Le marqueur est un filet : s'il ne peut pas être écrit, la suppression tourne quand même, sans reprise
            // automatique au prochain lancement (le créateur peut la relancer depuis l'écran de la Miff).
        }
    }
}
