#if ALERT_DANGER
import Foundation

// CONFIDENTIALITÉ, pour ce fichier : ce que le téléphone retient de MON alerte, et rien d'autre. Un pointeur (l'identifiant de la Miff d'origine, la phase,
// trois instants) : aucun nom, aucune position, aucun texte. Il est EXCLU des sauvegardes (iCloud et ordinateur) : restaurer une sauvegarde sur un autre
// téléphone n'y ferait pas revenir une alerte qui n'est pas la sienne (ticket 6 ; la vérité est le document du serveur, relu par le nouveau téléphone).
// Le dossier est dans Application Support (effacé avec l'app : la bulle bleue de l'onboarding, qui concerne l'INSTALLATION, revient après une
// réinstallation, ticket 7.1). Protection des fichiers `completeUntilFirstUserAuthentication` : l'app relancée en arrière-plan doit pouvoir le lire.
// Jamais journalisé, jamais envoyé à Analytics ni à Crashlytics. Miroir d'`AlertLocalStore.kt` d'Android.

final class DangerAlertFileStore: DangerAlertLocalStore {
    private struct Stored: Codable {
        let miffId: String
        let phase: String
        let armedAtMillis: Int
        let startedAtMillis: Int?
        let expiresAtMillis: Int?
        let staleConfirmed: Bool
    }

    private let directory: URL
    private let queue = DispatchQueue(label: "com.tomessa.miff.danger-alert")

    private var pointerURL: URL {
        directory.appendingPathComponent("pointer.json")
    }

    private var hintURL: URL {
        directory.appendingPathComponent("hint-done")
    }

    private var adoptedURL: URL {
        directory.appendingPathComponent("adopted-for.txt")
    }

    init(directory: URL? = nil) {
        if let directory {
            self.directory = directory
        } else {
            let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
                ?? FileManager.default.temporaryDirectory
            self.directory = base.appendingPathComponent("DangerAlert", isDirectory: true)
        }
    }

    func pointer() -> DangerAlertPointer? {
        queue.sync {
            guard let data = try? Data(contentsOf: pointerURL),
                  let stored = try? JSONDecoder().decode(Stored.self, from: data),
                  MiffId.isValid(stored.miffId),
                  let phase = DangerPointerPhase(rawValue: stored.phase) else {
                return nil
            }
            return DangerAlertPointer(
                miffId: stored.miffId,
                phase: phase,
                armedAtMillis: stored.armedAtMillis,
                startedAtMillis: stored.startedAtMillis,
                expiresAtMillis: stored.expiresAtMillis,
                staleConfirmed: stored.staleConfirmed
            )
        }
    }

    func save(_ pointer: DangerAlertPointer) {
        let stored = Stored(
            miffId: pointer.miffId,
            phase: pointer.phase.rawValue,
            armedAtMillis: pointer.armedAtMillis,
            startedAtMillis: pointer.startedAtMillis,
            expiresAtMillis: pointer.expiresAtMillis,
            staleConfirmed: pointer.staleConfirmed
        )
        guard let data = try? JSONEncoder().encode(stored) else {
            return
        }
        queue.sync {
            if prepareDirectory() {
                _ = write(data, to: pointerURL)
            }
        }
    }

    func clearPointer() {
        queue.sync {
            try? FileManager.default.removeItem(at: pointerURL)
        }
    }

    func hintDone() -> Bool {
        queue.sync {
            FileManager.default.fileExists(atPath: hintURL.path)
        }
    }

    func markHintDone() {
        queue.sync {
            if prepareDirectory() {
                _ = write(Data([1]), to: hintURL)
            }
        }
    }

    func adoptedFor() -> String? {
        queue.sync {
            guard let data = try? Data(contentsOf: adoptedURL) else {
                return nil
            }
            return String(data: data, encoding: .utf8)
        }
    }

    func markAdoptedFor(_ uid: String) {
        guard let data = uid.data(using: .utf8) else {
            return
        }
        queue.sync {
            if prepareDirectory() {
                _ = write(data, to: adoptedURL)
            }
        }
    }

    func clearAccount() {
        queue.sync {
            try? FileManager.default.removeItem(at: pointerURL)
            try? FileManager.default.removeItem(at: adoptedURL)
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
}
#endif
