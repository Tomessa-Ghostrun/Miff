import Foundation

/// L'état local de la session de cet appareil (SESS-3), dans un petit dossier du stockage privé de l'app (Application Support) :
/// - `device-id.txt` : l'identifiant ;
/// - `device-id.pending` : présent tant que le serveur n'a pas confirmé l'identifiant (il est alors un CANDIDAT) ;
/// - `device-revoked` : le marqueur « révoqué » (remplacé ou déconnecté, pas encore reconnecté).
///
/// Pourquoi des fichiers et pas le Trousseau ni les réglages (`UserDefaults`) :
/// - le Trousseau iOS SURVIT à la désinstallation : l'identifiant y survivrait à une réinstallation et l'app croirait être
///   encore l'appareil actif (contrat `backend/data-model.md`, « Un compte, un seul appareil »). Application Support est effacé
///   avec l'app : après une réinstallation il n'y a plus d'identifiant local, et la session est vérifiée au lancement ;
/// - les réglages sont inclus dans les sauvegardes : restaurer une sauvegarde sur un NOUVEAU téléphone y poserait l'identifiant
///   de l'ancien, et les deux se croiraient actifs. Le dossier est donc EXCLU des sauvegardes (iCloud et ordinateur).
///
/// Protection des fichiers : `completeUntilFirstUserAuthentication` (lisibles dès le premier déverrouillage après le démarrage),
/// pas `complete` : une écriture de statut déclenchée en arrière-plan alors que le téléphone est verrouillé (LOC-4b) doit pouvoir
/// lire l'identifiant. L'identifiant est aléatoire et sans lien avec l'appareil ; il n'ouvre aucun accès à lui seul.
///
/// Une erreur de LECTURE (autre chose que « fichier absent ») rend `.unreadable` : état indéterminé, jamais « aucun identifiant ».
///
/// Jamais journalisé, jamais envoyé à Analytics ni à Crashlytics.
final class DeviceIdFileStore: DeviceIdStore {
    private let directory: URL
    private let queue = DispatchQueue(label: "com.tomessa.miff.device-id")

    private var idURL: URL {
        directory.appendingPathComponent("device-id.txt")
    }

    private var pendingURL: URL {
        directory.appendingPathComponent("device-id.pending")
    }

    private var revokedURL: URL {
        directory.appendingPathComponent("device-revoked")
    }

    private var goneURL: URL {
        directory.appendingPathComponent("device-gone")
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

    func read() -> StoredDevice {
        queue.sync {
            let data: Data
            do {
                data = try Data(contentsOf: idURL)
            } catch let error as NSError {
                let missing = error.domain == NSCocoaErrorDomain
                    && (error.code == NSFileReadNoSuchFileError || error.code == NSFileNoSuchFileError)
                if missing {
                    // Pas d'identifiant : un marqueur « en attente » orphelin n'a plus de sens.
                    removeFile(pendingURL)
                    return .absent
                }
                return .unreadable
            }
            // Un fichier dont le contenu n'a pas la forme d'un identifiant n'en est pas un : effacé, comme s'il n'y en avait pas.
            guard let text = String(data: data, encoding: .utf8), DeviceId.isValid(text) else {
                removeFile(idURL)
                removeFile(pendingURL)
                return .absent
            }
            return FileManager.default.fileExists(atPath: pendingURL.path) ? .candidate(text) : .confirmed(text)
        }
    }

    func saveCandidate(_ deviceId: String) -> Bool {
        guard DeviceId.isValid(deviceId), let data = deviceId.data(using: .utf8) else {
            return false
        }
        return queue.sync {
            // Le marqueur « en attente » d'abord : un identifiant sans marqueur serait lu comme confirmé.
            guard prepareDirectory(), write(Data([1]), to: pendingURL), write(data, to: idURL) else {
                removeFile(idURL)
                removeFile(pendingURL)
                return false
            }
            return true
        }
    }

    func confirm() -> Bool {
        queue.sync {
            guard FileManager.default.fileExists(atPath: idURL.path) else {
                return false
            }
            do {
                try FileManager.default.removeItem(at: pendingURL)
            } catch let error as NSError {
                let missing = error.domain == NSCocoaErrorDomain
                    && (error.code == NSFileNoSuchFileError || error.code == NSFileReadNoSuchFileError)
                return missing
            }
            return true
        }
    }

    func clear() {
        queue.sync {
            removeFile(idURL)
            removeFile(pendingURL)
        }
    }

    func isRevoked() -> Bool {
        queue.sync {
            FileManager.default.fileExists(atPath: revokedURL.path)
        }
    }

    func setRevoked(_ value: Bool) -> Bool {
        queue.sync {
            if value {
                return prepareDirectory() && write(Data([1]), to: revokedURL)
            }
            // La raison (« session disparue ») n'a de sens qu'avec le marqueur : elle part avec lui.
            removeFile(goneURL)
            return removeMarker(revokedURL)
        }
    }

    func isGone() -> Bool {
        queue.sync {
            FileManager.default.fileExists(atPath: goneURL.path)
        }
    }

    func setGone(_ value: Bool) -> Bool {
        queue.sync {
            if value {
                return prepareDirectory() && write(Data([1]), to: goneURL)
            }
            return removeMarker(goneURL)
        }
    }

    // MARK: - Intérieur

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

    private func write(_ data: Data, to url: URL) -> Bool {
        do {
            try data.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
            return true
        } catch {
            return false
        }
    }

    private func removeFile(_ url: URL) {
        try? FileManager.default.removeItem(at: url)
    }

    /// Retire un marqueur : vrai s'il n'y est plus (déjà absent compris), faux si le disque refuse.
    private func removeMarker(_ url: URL) -> Bool {
        do {
            try FileManager.default.removeItem(at: url)
        } catch let error as NSError {
            let missing = error.domain == NSCocoaErrorDomain
                && (error.code == NSFileNoSuchFileError || error.code == NSFileReadNoSuchFileError)
            return missing
        }
        return true
    }
}
