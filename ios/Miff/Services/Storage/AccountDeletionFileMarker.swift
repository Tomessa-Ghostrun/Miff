import Foundation

/// Le marqueur local « la suppression du compte est en cours », dans UN fichier du stockage privé de l'app (Application Support), EXCLU
/// des sauvegardes (iCloud et ordinateur). Il ne contient que l'identifiant opaque du compte qui a lancé la suppression et un drapeau
/// « Firebase a confirmé la suppression du compte » : ni nom, ni e-mail, ni code (ticket AUTH-5, §5.6). Inutile sans le compte.
///
/// Il est écrit AVANT la première modification (critère SC15) et n'est retiré qu'une fois tout, données locales comprises, nettoyé. Il a
/// PRIORITÉ sur le routage du lancement : sans lui, un profil déjà supprimé enverrait la personne vers « crée ton profil ».
///
/// L'écriture est SYNCHRONE et ATOMIQUE : le marqueur est sur le disque avant de rendre la main (une app tuée aussitôt reprend quand
/// même). Protection « jusqu'à la première authentification » : lisible dès que l'appareil a été déverrouillé une fois après son
/// démarrage, donc aussi quand l'app est relancée écran verrouillé par un événement de zone.
final class AccountDeletionFileMarker: AccountDeletionMarker {
    private struct Stored: Codable {
        let uid: String
        let accountDeleted: Bool
        /// Absent des marqueurs écrits avant ce champ : alors « pas d'Apple ».
        let appleRevocation: String?
    }

    private let directory: URL
    private let lock = NSLock()

    private var fileURL: URL {
        directory.appendingPathComponent("account-deletion.json")
    }

    init(directory: URL? = nil) {
        if let directory {
            self.directory = directory
        } else {
            let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
                ?? FileManager.default.temporaryDirectory
            self.directory = base.appendingPathComponent("AccountDeletion", isDirectory: true)
        }
    }

    func read() -> AccountDeletionMark? {
        lock.lock()
        defer { lock.unlock() }
        return load()
    }

    private func load() -> AccountDeletionMark? {
        guard let data = try? Data(contentsOf: fileURL),
              let stored = try? JSONDecoder().decode(Stored.self, from: data),
              !stored.uid.isEmpty else {
            return nil
        }
        let apple = stored.appleRevocation.flatMap { AppleRevocation(rawValue: $0) } ?? .notNeeded
        return AccountDeletionMark(uid: stored.uid, accountDeleted: stored.accountDeleted, appleRevocation: apple)
    }

    /// Vrai SEULEMENT si le fichier, relu, est bien ce qu'on a voulu écrire. Un marqueur impossible à écrire (disque plein, stockage
    /// refusé) fait REFUSER le démarrage de la suppression : sans lui, une app tuée en route ne pourrait pas la reprendre, et un profil déjà
    /// supprimé enverrait la personne vers « crée ton profil ».
    @discardableResult
    func write(_ mark: AccountDeletionMark) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        let apple = mark.appleRevocation.rawValue
        let stored = Stored(uid: mark.uid, accountDeleted: mark.accountDeleted, appleRevocation: apple)
        guard let data = try? JSONEncoder().encode(stored) else {
            return false
        }
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            var folder = directory
            var values = URLResourceValues()
            values.isExcludedFromBackup = true
            try? folder.setResourceValues(values)
            try data.write(to: fileURL, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        } catch {
            return false
        }
        return load() == mark
    }

    func clear() {
        lock.lock()
        defer { lock.unlock() }
        try? FileManager.default.removeItem(at: fileURL)
    }
}
