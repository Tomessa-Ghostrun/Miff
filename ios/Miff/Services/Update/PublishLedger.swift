import Foundation

// CONFIDENTIALITÉ, pour ce fichier : ce que le téléphone retient de l'Update, et rien d'autre : des nombres et des moments (jamais une
// photo, un lieu, un nom ou une Miff), pour le garde-fou des publications (20 par jour, 30 s entre deux), l'astuce du double toucher
// et le moment de la dernière photo envoyée (pour la retirer de la base quand elle expire). Un fichier privé de l'app, EXCLU des
// sauvegardes (iCloud et ordinateur). Miroir de `PublishLedger.kt` d'Android.
//
// Le moment de la photo est lié au COMPTE (son `uid`) et n'est PAS effacé à la déconnexion : la photo reste en base après une déconnexion
// (seuls l'arrêt du partage, le départ ou la suppression du compte la retirent), donc le même compte qui se reconnecte doit encore pouvoir
// la nettoyer (revue C1). Les comptes et le reste sont effacés à la déconnexion ; la note de la photo et l'astuce (qui concerne le
// téléphone, pas le compte) y survivent. À la SUPPRESSION du compte, la note de CE compte est effacée (`eraseAccountData`).

protocol PublishLedger: AnyObject {
    /// Le jour (clé du jour du téléphone) et le nombre de publications comptées pour lui.
    func countedDay() -> String?
    func countToday() -> Int
    func lastPublishMillis() -> Int?

    /// Compte une publication faite à `nowMillis` le jour `dayKey` (`count` après elle).
    func recordPublication(nowMillis: Int, dayKey: String, count: Int)

    /// Le moment où la photo de `uid` maintenant en base a été ENVOYÉE (la note d'un autre compte est ignorée), ou nil. Elle est écrite AVANT
    /// le début de l'envoi : un envoi qui part après que l'écran a renoncé (un délai) est quand même nettoyé 24 h plus tard (revue C2).
    func photoSentMillis(uid: String) -> Int?
    func notePhotoSent(uid: String, millis: Int)

    /// Oublie la note (la photo a été retirée, ou l'envoi est connu pour ne pas être parti).
    func clearPhotoSent()

    /// La personne a changé de caméra par un double toucher au moins une fois : l'astuce « Touche deux fois l'écran… » a disparu pour de
    /// bon (PM, 2026-11-06, D38). Gardé pour l'installation, PAS effacé à la déconnexion.
    func doubleTapUsed() -> Bool
    func markDoubleTapUsed()

    /// Déconnexion : tout sauf la note de la photo et ce qui concerne l'installation (le double toucher).
    func clear()

    /// Suppression du compte `uid` : la note de CE compte seulement (l'unique note du téléphone peut être celle d'un autre compte).
    func eraseAccountData(uid: String)
}

/// Ce que le fichier du carnet contient.
struct LedgerState: Codable, Equatable {
    var day: String?
    var count = 0
    var lastPublish: Int?
    var photoSentAt: Int?
    var photoSentUid: String?
    var doubleTapUsed = false
}

/// Le carnet dans un fichier du stockage privé de l'app (Application Support), exclu des sauvegardes. Les écritures sont synchrones et
/// protégées par un verrou : le fichier est minuscule.
final class FilePublishLedger: PublishLedger {
    private let directory: URL
    private let lock = NSLock()
    private var cached: LedgerState?

    private var fileURL: URL {
        directory.appendingPathComponent("update-ledger.json")
    }

    init(directory: URL? = nil) {
        if let directory {
            self.directory = directory
        } else {
            let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
                ?? FileManager.default.temporaryDirectory
            self.directory = base.appendingPathComponent("Update", isDirectory: true)
        }
    }

    func countedDay() -> String? {
        read { $0.day }
    }

    func countToday() -> Int {
        read { $0.count }
    }

    func lastPublishMillis() -> Int? {
        read { $0.lastPublish }
    }

    func recordPublication(nowMillis: Int, dayKey: String, count: Int) {
        change {
            $0.day = dayKey
            $0.count = count
            $0.lastPublish = nowMillis
        }
    }

    func photoSentMillis(uid: String) -> Int? {
        read { $0.photoSentUid == uid ? $0.photoSentAt : nil }
    }

    func notePhotoSent(uid: String, millis: Int) {
        change {
            $0.photoSentUid = uid
            $0.photoSentAt = millis
        }
    }

    func clearPhotoSent() {
        change {
            $0.photoSentUid = nil
            $0.photoSentAt = nil
        }
    }

    func doubleTapUsed() -> Bool {
        read { $0.doubleTapUsed }
    }

    func markDoubleTapUsed() {
        change { $0.doubleTapUsed = true }
    }

    func clear() {
        change {
            $0.day = nil
            $0.count = 0
            $0.lastPublish = nil
        }
    }

    func eraseAccountData(uid: String) {
        change {
            if $0.photoSentUid == uid {
                $0.photoSentUid = nil
                $0.photoSentAt = nil
            }
        }
    }

    // MARK: - Fichier

    private func read<T>(_ value: (LedgerState) -> T) -> T {
        lock.lock()
        defer { lock.unlock() }
        return value(load())
    }

    private func change(_ mutation: (inout LedgerState) -> Void) {
        lock.lock()
        defer { lock.unlock() }
        var state = load()
        mutation(&state)
        cached = state
        save(state)
    }

    private func load() -> LedgerState {
        if let cached {
            return cached
        }
        let state = (try? Data(contentsOf: fileURL))
            .flatMap { try? JSONDecoder().decode(LedgerState.self, from: $0) } ?? LedgerState()
        cached = state
        return state
    }

    private func save(_ state: LedgerState) {
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            var folder = directory
            var values = URLResourceValues()
            values.isExcludedFromBackup = true
            try folder.setResourceValues(values)
            let data = try JSONEncoder().encode(state)
            try data.write(to: fileURL, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
        } catch {
            // Un carnet qui ne s'écrit pas n'empêche rien : le garde-fou et le nettoyage retombent sur l'état en mémoire.
        }
    }
}
