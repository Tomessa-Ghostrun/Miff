#if DEBUG || INTERNAL
import Foundation

// CONFIDENTIALITÉ, pour ce fichier (critère VIE2) : le journal de la détection n'existe que dans les builds Debug et Internal
// (TestFlight du PM). Il ne peut contenir que le TYPE d'événement (liste fermée `JournalKind`), l'identifiant d'un lieu (vérifié :
// lettres et chiffres, 20 au plus, donc jamais une clé de zone ni une coordonnée), l'heure et la batterie. Aucune coordonnée, aucune
// adresse, aucun nom, aucun texte libre. Il est dans un fichier privé, EXCLU des sauvegardes, et il est effacé à la déconnexion. Ce
// fichier n'est pas compilé dans le build de publication, qui reçoit `NoDetectionJournal` (la CI cherche le
// marqueur DETECTIONJOURNAL dans l'exécutable de publication).

/// Le journal sur le disque : 400 lignes au plus, la plus ancienne est oubliée la première.
final class FileDetectionJournal: DetectionJournal {
    /// Le marqueur que la CI cherche dans l'exécutable : présent en Debug et Internal, ABSENT en publication. Il sert de nom au fichier,
    /// donc il est utilisé à l'exécution et l'optimiseur ne peut pas le retirer du build Internal.
    static let marker = "DETECTIONJOURNAL-V1"
    static let maxLines = 400

    private let directory: URL
    private let clock: () -> Int
    private let queue = DispatchQueue(label: "com.tomessa.miff.detection-journal")

    private var fileURL: URL {
        directory.appendingPathComponent(Self.marker + ".txt")
    }

    init(directory: URL? = nil, clock: @escaping () -> Int = { Int(Date().timeIntervalSince1970 * 1000) }) {
        if let directory {
            self.directory = directory
        } else {
            let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
                ?? FileManager.default.temporaryDirectory
            self.directory = base.appendingPathComponent("DetectionJournal", isDirectory: true)
        }
        self.clock = clock
    }

    func record(kind: JournalKind, placeId: String?, batteryPct: Int?) {
        queue.sync {
            guard prepareDirectory() else {
                return
            }
            // Un identifiant qui n'a pas la forme d'un identifiant de lieu n'est pas écrit (jamais une clé de zone).
            var safeId = "-"
            if let placeId, Place.isPlaceId(placeId) {
                safeId = placeId
            }
            var battery = "-"
            if let batteryPct, (0...100).contains(batteryPct) {
                battery = String(batteryPct)
            }
            let line = "\(clock())|\(kind.rawValue)|\(safeId)|\(battery)"
            var kept = Array(readRaw().suffix(Self.maxLines - 1))
            kept.append(line)
            let text = kept.joined(separator: "\n") + "\n"
            try? Data(text.utf8).write(
                to: fileURL,
                options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication]
            )
        }
    }

    func lines() -> [JournalLine] {
        queue.sync {
            readRaw().compactMap { Self.parse($0) }
        }
    }

    func clear() {
        queue.sync {
            try? FileManager.default.removeItem(at: directory)
        }
    }

    // MARK: - Intérieur

    private func readRaw() -> [String] {
        guard let data = try? Data(contentsOf: fileURL), let text = String(data: data, encoding: .utf8) else {
            return []
        }
        return text.split(separator: "\n").map { String($0) }
    }

    /// Une ligne `heure|type|lieu|batterie`, ou nil si elle n'a pas cette forme (une ligne abîmée est ignorée).
    static func parse(_ line: String) -> JournalLine? {
        let parts = line.split(separator: "|", omittingEmptySubsequences: false).map { String($0) }
        guard parts.count == 4, let millis = Int(parts[0]), let kind = JournalKind(rawValue: parts[1]) else {
            return nil
        }
        return JournalLine(
            atMillis: millis,
            kind: kind,
            placeId: parts[2] == "-" ? nil : parts[2],
            batteryPct: Int(parts[3])
        )
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
#endif

/// Le journal du build : un fichier dans les builds de test, rien dans le build de publication.
enum DetectionJournals {
    static func make() -> DetectionJournal {
        #if DEBUG || INTERNAL
        return FileDetectionJournal()
        #else
        return NoDetectionJournal()
        #endif
    }
}
