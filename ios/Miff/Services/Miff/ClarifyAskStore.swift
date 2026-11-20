import Foundation

// CONFIDENTIALITÉ, pour ce fichier : un COMPTEUR par compte (combien de fois la carte calme « Une précision sur ce que tu partages » a été
// montrée sur ce téléphone). Ni position, ni nom de Miff, ni texte ; il ne quitte jamais le téléphone (aucun réseau, aucun événement,
// aucun journal), il est effacé avec le compte (`AccountLocalData`) et le fichier est EXCLU des sauvegardes (iCloud et ordinateur).

/// CONS-4-I, option D du PM : la carte des comptes en version 3 revient AU PLUS `maxShows` fois, puis seul « Revoir et accepter » de
/// Réglages > Confidentialité reste. Un lancement ne la montre qu'une fois. Aucune pression : pas de compte à rebours, rien n'est en
/// pause, rien n'est écrit sur le serveur (miroir de `ClarifyPolicy` d'Android).
enum ClarifyPolicy {
    static let maxShows = 3

    /// La carte est-elle encore proposée d'elle-même, vu le nombre de fois où elle a déjà été montrée à ce compte ?
    static func mayShowByItself(shownCount: Int) -> Bool {
        shownCount < maxShows
    }
}

/// Combien de fois la carte calme a été montrée à un compte sur ce téléphone (un double dans les tests).
protocol ClarifyAskStore {
    func shownCount(uid: String) -> Int
    /// La carte a été montrée (une fois par lancement au plus) : une de plus. Ne redescend jamais.
    func markShown(uid: String)
    /// Le compte a été supprimé : rien n'est gardé pour lui.
    func forget(uid: String)
}

/// Le compteur dans UN fichier du stockage privé de l'app (Application Support), EXCLU des sauvegardes : il ne contient que des
/// identifiants de compte opaques et des entiers. L'écriture est SYNCHRONE : le compte est sur le disque AVANT que la carte soit à
/// l'écran, pour qu'une app tuée ne donne jamais une quatrième apparition (miroir du `commit()` d'Android).
final class ClarifyAskFileStore: ClarifyAskStore {
    private let directory: URL
    private let lock = NSLock()

    private var fileURL: URL {
        directory.appendingPathComponent("clarify-asks.json")
    }

    init(directory: URL? = nil) {
        if let directory {
            self.directory = directory
        } else {
            let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
                ?? FileManager.default.temporaryDirectory
            self.directory = base.appendingPathComponent("ConsentClarify", isDirectory: true)
        }
    }

    func shownCount(uid: String) -> Int {
        lock.lock()
        defer { lock.unlock() }
        return readAll()[uid] ?? 0
    }

    func markShown(uid: String) {
        lock.lock()
        defer { lock.unlock() }
        var counts = readAll()
        counts[uid] = min((counts[uid] ?? 0) + 1, ClarifyPolicy.maxShows)
        write(counts)
    }

    func forget(uid: String) {
        lock.lock()
        defer { lock.unlock() }
        var counts = readAll()
        counts[uid] = nil
        write(counts)
    }

    private func readAll() -> [String: Int] {
        guard let data = try? Data(contentsOf: fileURL),
              let counts = try? JSONDecoder().decode([String: Int].self, from: data) else {
            return [:]
        }
        return counts
    }

    private func write(_ counts: [String: Int]) {
        if counts.isEmpty {
            try? FileManager.default.removeItem(at: fileURL)
            return
        }
        guard let data = try? JSONEncoder().encode(counts) else {
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
            // Le compteur est un confort : s'il ne peut pas être écrit, la carte pourra revenir une fois de plus, jamais plus
            // d'une fois par lancement. Rien n'est affiché ni journalisé.
        }
    }
}

/// Le compteur gardé en mémoire seulement : pour les tests, et le défaut de la porte avant que l'environnement lui donne le vrai.
final class InMemoryClarifyAskStore: ClarifyAskStore {
    private var counts: [String: Int] = [:]

    func shownCount(uid: String) -> Int {
        counts[uid] ?? 0
    }

    func markShown(uid: String) {
        counts[uid] = min(shownCount(uid: uid) + 1, ClarifyPolicy.maxShows)
    }

    func forget(uid: String) {
        counts[uid] = nil
    }
}
