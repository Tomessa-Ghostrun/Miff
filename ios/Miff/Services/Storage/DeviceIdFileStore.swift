import Foundation

/// L'identifiant de session de cet appareil (SESS-3), dans UN fichier du stockage privé de l'app (Application Support).
///
/// Pourquoi un fichier et pas le Trousseau ni les réglages (`UserDefaults`) :
/// - le Trousseau iOS SURVIT à la désinstallation : l'identifiant y survivrait à une réinstallation et l'app croirait être
///   encore l'appareil actif (contrat `backend/data-model.md`, « Un compte, un seul appareil »). Application Support est effacé
///   avec l'app : après une réinstallation il n'y a plus d'identifiant local, et la session est vérifiée au lancement ;
/// - les réglages sont inclus dans les sauvegardes : restaurer une sauvegarde sur un NOUVEAU téléphone y poserait l'identifiant
///   de l'ancien, et les deux se croiraient actifs. Le dossier est donc EXCLU des sauvegardes (iCloud et ordinateur).
///
/// Protection du fichier : `completeUntilFirstUserAuthentication` (lisible dès le premier déverrouillage après le démarrage), pas
/// `complete` : une écriture de statut déclenchée en arrière-plan alors que le téléphone est verrouillé (LOC-4b) doit pouvoir lire
/// l'identifiant. L'identifiant est aléatoire et sans lien avec l'appareil ; il n'ouvre aucun accès à lui seul.
///
/// Jamais journalisé, jamais envoyé à Analytics ni à Crashlytics.
final class DeviceIdFileStore: DeviceIdStore {
    private let directory: URL
    private let queue = DispatchQueue(label: "com.tomessa.miff.device-id")

    private var fileURL: URL {
        directory.appendingPathComponent("device-id.txt")
    }

    init(directory: URL? = nil) {
        if let directory {
            self.directory = directory
        } else {
            let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
                ?? FileManager.default.temporaryDirectory
            self.directory = base.appendingPathComponent("DeviceSession", isDirectory: true)
        }
    }

    func read() -> String? {
        queue.sync {
            guard let data = try? Data(contentsOf: fileURL),
                  let text = String(data: data, encoding: .utf8) else {
                return nil
            }
            // Un fichier abîmé ou modifié n'est pas un identifiant : effacé, comme s'il n'y en avait pas.
            guard DeviceId.isValid(text) else {
                removeFile()
                return nil
            }
            return text
        }
    }

    func save(_ deviceId: String) -> Bool {
        guard DeviceId.isValid(deviceId), let data = deviceId.data(using: .utf8) else {
            return false
        }
        return queue.sync {
            do {
                try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
                var folder = directory
                var values = URLResourceValues()
                values.isExcludedFromBackup = true
                try folder.setResourceValues(values)
                try data.write(to: fileURL, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
                return true
            } catch {
                // Pas d'identifiant gardé = pas de session ouverte : la connexion échoue proprement et peut être retentée.
                removeFile()
                return false
            }
        }
    }

    func clear() {
        queue.sync {
            removeFile()
        }
    }

    private func removeFile() {
        try? FileManager.default.removeItem(at: fileURL)
    }
}
