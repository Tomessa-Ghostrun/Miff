import Foundation

/// Les retraits de consentement en attente (`PendingWithdrawals`), dans un petit fichier du stockage privé de l'app (Application
/// Support), une ligne `clé:heure` par compte. Ni nom, ni e-mail, ni position, ni identifiant de compte : la clé est une empreinte à
/// sens unique (voir `PendingWithdrawals`).
///
/// Pourquoi un fichier et pas les réglages (`UserDefaults`) : les réglages sont inclus dans les sauvegardes (iCloud et ordinateur), et
/// restaurer une sauvegarde sur un autre téléphone y poserait le retrait d'un compte qui ne l'a peut-être plus demandé. Le dossier est
/// EXCLU des sauvegardes et effacé avec l'app, comme sur Android (`backup_rules.xml`).
///
/// Protection : `completeUntilFirstUserAuthentication` (lisible dès le premier déverrouillage après le démarrage : l'app peut être
/// relancée en arrière-plan par une zone). Écriture atomique : un fichier à moitié écrit n'existe jamais.
///
/// Un fichier illisible est lu comme vide : la marque est alors perdue, et le choix du compte redevient celui qui commande au
/// démarrage (limite connue, rare : le fichier n'est lisible que par l'app).
final class WithdrawalFileStorage: WithdrawalStorage {
    private let directory: URL
    private let queue = DispatchQueue(label: "com.tomessa.miff.consent-withdrawals")

    private var fileURL: URL {
        directory.appendingPathComponent("pending-withdrawals.txt")
    }

    init(directory: URL? = nil) {
        if let directory {
            self.directory = directory
        } else {
            let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
                ?? FileManager.default.temporaryDirectory
            self.directory = base.appendingPathComponent("PrivacyChoices", isDirectory: true)
        }
    }

    func read() -> [String: Int] {
        queue.sync {
            guard let data = try? Data(contentsOf: fileURL), let text = String(data: data, encoding: .utf8) else {
                return [:]
            }
            var entries: [String: Int] = [:]
            for line in text.split(separator: "\n") {
                let parts = line.split(separator: ":", omittingEmptySubsequences: false)
                if parts.count == 2, !parts[0].isEmpty, let time = Int(parts[1]) {
                    entries[String(parts[0])] = time
                }
            }
            return entries
        }
    }

    func write(_ entries: [String: Int]) -> Bool {
        queue.sync {
            do {
                try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
                var folder = directory
                var values = URLResourceValues()
                values.isExcludedFromBackup = true
                try folder.setResourceValues(values)
                let text = entries.sorted { $0.key < $1.key }.map { "\($0.key):\($0.value)" }.joined(separator: "\n")
                let options: Data.WritingOptions = [.atomic, .completeFileProtectionUntilFirstUserAuthentication]
                try Data(text.utf8).write(to: fileURL, options: options)
                return true
            } catch {
                return false
            }
        }
    }
}
