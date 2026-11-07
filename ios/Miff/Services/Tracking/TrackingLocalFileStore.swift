import Foundation

/// La copie locale de la détection (voir `TrackingLocalState.swift`), dans UN fichier du stockage privé de l'app (Application Support),
/// EXCLU des sauvegardes (iCloud et ordinateur) : un téléphone restauré depuis une sauvegarde ne retrouve ni les lieux d'une autre
/// personne, ni sa position. Effacée à la déconnexion, à la suppression du compte et quand l'accès ou le partage est perdu (critères
/// POS5, VIE3).
///
/// Protection du fichier : « jusqu'à la première authentification » (lisible dès que l'appareil a été déverrouillé une fois après son
/// démarrage) : une relance en arrière-plan par une zone, téléphone verrouillé, doit pouvoir la lire. Les écritures sont SYNCHRONES :
/// l'état est sur le disque avant l'envoi au serveur (une app tuée aussitôt reprend quand même). Une copie ILLISIBLE n'est pas une
/// copie : elle est reconstruite depuis le serveur, jamais crue.
///
/// Jamais journalisée, jamais envoyée à Analytics ni à Crashlytics.
final class TrackingLocalFileStore: TrackingLocalStore {
    private let directory: URL
    private let queue = DispatchQueue(label: "com.tomessa.miff.tracking-local")

    private var fileURL: URL {
        directory.appendingPathComponent("tracking-state.json")
    }

    init(directory: URL? = nil) {
        if let directory {
            self.directory = directory
        } else {
            let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
                ?? FileManager.default.temporaryDirectory
            self.directory = base.appendingPathComponent("Tracking", isDirectory: true)
        }
    }

    func load() -> LocalState? {
        queue.sync {
            guard let data = try? Data(contentsOf: fileURL) else {
                return nil
            }
            return try? JSONDecoder().decode(LocalState.self, from: data)
        }
    }

    func save(_ state: LocalState) {
        queue.sync {
            guard prepareDirectory(), let data = try? JSONEncoder().encode(state) else {
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
