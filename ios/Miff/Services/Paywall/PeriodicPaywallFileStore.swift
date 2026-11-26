import Foundation

// CONFIDENTIALITÉ, pour ce fichier (ticket docs/tickets/paywall-periodique.md §4.3, critère PP4) : UN seul entier, les ouvertures comptées depuis la dernière apparition de l'offre.
// Aucune date, aucun identifiant, aucun nom de Miff, aucune position. Jamais envoyé, jamais journalisé, jamais dans un événement Analytics. Fichier privé de l'app, EXCLU des
// sauvegardes (iCloud et ordinateur : restaurer une sauvegarde sur un autre téléphone ne lui apporte pas ce nombre). Effacé à la déconnexion et à la suppression du compte
// (`PeriodicPaywallServices.teardowns`). Miroir de `SharedPreferencesPeriodicPaywallStore` d'Android.

/// Le compteur de l'offre périodique, dans un petit fichier JSON privé (Application Support). Pas un secret : la protection des fichiers est celle du premier déverrouillage.
/// Un fichier illisible, d'un format inconnu ou à une valeur absurde se lit comme zéro : l'offre attend alors, elle ne vient jamais d'une supposition.
final class PeriodicPaywallFileStore: PeriodicPaywallCounterStore, @unchecked Sendable {
    /// Ce qui est écrit : UN entier.
    private struct Stored: Codable {
        var openings: Int
    }

    /// Une valeur plus grande n'est pas un compteur de cette règle (le seuil vaut 10) : elle est ramenée là.
    static let maxStored = 1_000

    private let directory: URL
    private let lock = NSLock()
    /// La dernière valeur lue ou écrite : le fichier n'est lu qu'une fois par processus.
    private var cached: Int?

    private var fileURL: URL {
        directory.appendingPathComponent("periodic-paywall.json")
    }

    init(directory: URL? = nil) {
        if let directory {
            self.directory = directory
        } else {
            let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
                ?? FileManager.default.temporaryDirectory
            self.directory = base.appendingPathComponent("PeriodicPaywall", isDirectory: true)
        }
    }

    func read() -> Int {
        lock.lock()
        defer { lock.unlock() }
        if let cached {
            return cached
        }
        let value = load()
        cached = value
        return value
    }

    func write(_ count: Int) {
        lock.lock()
        defer { lock.unlock() }
        let value = Self.clamp(count)
        cached = value
        save(value)
    }

    func clear() {
        lock.lock()
        defer { lock.unlock() }
        cached = 0
        try? FileManager.default.removeItem(at: fileURL)
    }

    // MARK: - Intérieur

    private static func clamp(_ count: Int) -> Int {
        min(max(count, 0), maxStored)
    }

    private func load() -> Int {
        guard let data = try? Data(contentsOf: fileURL),
              let stored = try? JSONDecoder().decode(Stored.self, from: data) else {
            return 0
        }
        return Self.clamp(stored.openings)
    }

    private func save(_ value: Int) {
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            var folder = directory
            var values = URLResourceValues()
            values.isExcludedFromBackup = true
            try folder.setResourceValues(values)
            let data = try JSONEncoder().encode(Stored(openings: value))
            try data.write(to: fileURL, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        } catch {
            // Rien n'est retenu : au pire l'offre attend (ou revient) un peu plus tôt. Aucune trace, aucun message.
        }
    }
}
