#if REVIEW_PROMPT
import Foundation

// CONFIDENTIALITÉ, pour ce fichier : ce que la fenêtre d'avis retient sur l'appareil, trois petites valeurs (voir `ReviewState`) et un
// drapeau d'essai. Aucun nom, aucun identifiant, aucun compte. Jamais envoyé nulle part, jamais journalisé.

/// Ce que la fenêtre d'avis retient sur l'appareil. Miroir de `ReviewStateStore.kt` d'Android.
protocol ReviewStateStore {
    func read() -> ReviewState

    func write(_ state: ReviewState)

    /// Oublie tout (déconnexion, suppression du compte : la personne suivante repart de zéro). Sans effet s'il n'y a rien.
    func clear()

    /// BUILDS DEBUG ET INTERNAL SEULEMENT (voir `ReviewPromptCoordinator`) : « montre la fenêtre au prochain retour dans l'app », quoi que
    /// disent les règles.
    func forceNext() -> Bool

    func setForceNext(_ value: Bool)
}

/// Un petit fichier JSON privé de l'app (Application Support), EXCLU des sauvegardes (iCloud et ordinateur) : restaurer une sauvegarde sur
/// un autre téléphone ne lui apporte pas les dates de celui-ci. Pas un secret : la protection des fichiers est celle du premier
/// déverrouillage. Un fichier illisible ou d'un format inconnu se lit comme « rien retenu » (jamais un plantage).
final class ReviewStateFileStore: ReviewStateStore {
    /// Ce qui est écrit. La fin d'apparition est le nom Android (`CAN_DO_BETTER`) ; une valeur inconnue (version future) se lit comme « pas
    /// de réponse » : les règles attendent alors comme après une fermeture.
    private struct Stored: Codable {
        var firstMiffSeenAtMillis: Int?
        var lastShownAtMillis: Int?
        var lastOutcome: String?
        var forceNext: Bool?
    }

    private let directory: URL
    private let lock = NSLock()

    private var fileURL: URL {
        directory.appendingPathComponent("review-prompt.json")
    }

    init(directory: URL? = nil) {
        if let directory {
            self.directory = directory
        } else {
            let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
                ?? FileManager.default.temporaryDirectory
            self.directory = base.appendingPathComponent("ReviewPrompt", isDirectory: true)
        }
    }

    func read() -> ReviewState {
        lock.lock()
        defer { lock.unlock() }
        let stored = load()
        return ReviewState(
            firstMiffSeenAtMillis: stored.firstMiffSeenAtMillis,
            lastShownAtMillis: stored.lastShownAtMillis,
            lastOutcome: stored.lastOutcome.flatMap { ReviewOutcome(rawValue: $0) }
        )
    }

    func write(_ state: ReviewState) {
        lock.lock()
        defer { lock.unlock() }
        var stored = load()
        stored.firstMiffSeenAtMillis = state.firstMiffSeenAtMillis
        stored.lastShownAtMillis = state.lastShownAtMillis
        stored.lastOutcome = state.lastOutcome?.rawValue
        save(stored)
    }

    func clear() {
        lock.lock()
        defer { lock.unlock() }
        try? FileManager.default.removeItem(at: fileURL)
    }

    func forceNext() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return load().forceNext ?? false
    }

    func setForceNext(_ value: Bool) {
        lock.lock()
        defer { lock.unlock() }
        var stored = load()
        stored.forceNext = value ? true : nil
        save(stored)
    }

    // MARK: - Intérieur

    private func load() -> Stored {
        guard let data = try? Data(contentsOf: fileURL),
              let stored = try? JSONDecoder().decode(Stored.self, from: data) else {
            return Stored()
        }
        return stored
    }

    private func save(_ stored: Stored) {
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            var folder = directory
            var values = URLResourceValues()
            values.isExcludedFromBackup = true
            try folder.setResourceValues(values)
            let data = try JSONEncoder().encode(stored)
            try data.write(to: fileURL, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        } catch {
            // Rien n'est retenu : au pire la fenêtre se représente plus tôt. Aucune trace, aucun message.
        }
    }
}
#endif
