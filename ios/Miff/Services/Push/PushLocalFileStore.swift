import Foundation

/// Ce que ce téléphone a retenu de son enregistrement auprès du registre des appareils, dans UN fichier du stockage privé de l'app
/// (Application Support), EXCLU des sauvegardes (iCloud et ordinateur) : l'identifiant du compte, celui de la session, l'EMPREINTE du jeton
/// (jamais le jeton), le fuseau, l'interrupteur et la date de la dernière écriture. Il part à la sortie (déconnexion, suppression du compte).
///
/// Les écritures sont synchrones. La protection du fichier est « jusqu'à la première authentification » : lisible dès que l'appareil a été
/// déverrouillé une fois après son démarrage. Un fichier illisible se lit comme « rien retenu » : au pire une écriture de plus.
final class PushLocalFileStore: PushLocalState {
    private struct Snapshot: Codable {
        var record: RegistrationRecord?
        var serviceOn = false
        var othersClearedFor: String?
    }

    private let directory: URL
    private let lock = NSLock()

    private var fileURL: URL {
        directory.appendingPathComponent("push-registration.json")
    }

    init(directory: URL? = nil) {
        if let directory {
            self.directory = directory
        } else {
            let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
                ?? FileManager.default.temporaryDirectory
            self.directory = base.appendingPathComponent("PushRegistration", isDirectory: true)
        }
    }

    func record() -> RegistrationRecord? {
        read().record
    }

    func saveRecord(_ record: RegistrationRecord) {
        update { $0.record = record }
    }

    func serviceOn() -> Bool {
        read().serviceOn
    }

    func setServiceOn(_ value: Bool) {
        update { $0.serviceOn = value }
    }

    func othersClearedFor() -> String? {
        read().othersClearedFor
    }

    func setOthersClearedFor(_ deviceId: String?) {
        update { $0.othersClearedFor = deviceId }
    }

    func clear() {
        lock.lock()
        defer { lock.unlock() }
        try? FileManager.default.removeItem(at: fileURL)
    }

    // MARK: - Intérieur

    private func read() -> Snapshot {
        lock.lock()
        defer { lock.unlock() }
        return load()
    }

    private func update(_ change: (inout Snapshot) -> Void) {
        lock.lock()
        defer { lock.unlock() }
        var snapshot = load()
        change(&snapshot)
        write(snapshot)
    }

    private func load() -> Snapshot {
        guard let data = try? Data(contentsOf: fileURL),
              let snapshot = try? JSONDecoder().decode(Snapshot.self, from: data) else {
            return Snapshot()
        }
        return snapshot
    }

    private func write(_ snapshot: Snapshot) {
        guard let data = try? JSONEncoder().encode(snapshot) else {
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
            // Le fichier n'est qu'une mémoire : sans lui, la prochaine ouverture réécrit le document (une écriture de plus, jamais d'erreur).
        }
    }
}
