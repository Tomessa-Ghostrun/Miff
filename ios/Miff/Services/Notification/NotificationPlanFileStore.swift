import Foundation

/// Où l'instantané et le registre des rappels sont gardés (un double en mémoire dans les tests). Un seul compte à la fois sur le téléphone.
protocol NotificationPlanStoring: AnyObject {
    private var travelEndURL: URL {
        directory.appendingPathComponent("travel-end.json")
    }

    func loadSnapshot(uid: String) -> NotificationPlanSnapshot?
    func saveSnapshot(_ snapshot: NotificationPlanSnapshot, uid: String)
    func loadLedger(uid: String) -> NotificationLedger
    func saveLedger(_ ledger: NotificationLedger, uid: String)
    /// MODE VOYAGE : la fin (instant) de MON voyage, ou nil. Un compte et un instant, rien d'autre : N7 n'a besoin d'aucune Miff.
    func loadTravelEnd(uid: String) -> Int?
    func saveTravelEnd(_ untilMillis: Int?, uid: String)
    func clear()
}

/// L'instantané (`NotifPlanSnapshot`) et le registre des rappels, dans deux fichiers du stockage privé de l'app (Application Support),
/// EXCLUS des sauvegardes, propres à un compte, effacés à la déconnexion, à la suppression du compte et à la perte de session. Ils ne
/// contiennent ni position, ni nom de lieu, ni photo : des identifiants, des heures, des couleurs. Une copie illisible n'est pas crue.
/// Jamais journalisés, jamais envoyés à Analytics ni à Crashlytics.
final class NotificationPlanFileStore: NotificationPlanStoring {
    private struct StoredSnapshot: Codable {
        let uid: String
        let snapshot: NotificationPlanSnapshot
    }

    private struct StoredLedger: Codable {
        let uid: String
        let ledger: NotificationLedger
    }

    private struct StoredTravelEnd: Codable {
        let uid: String
        let untilMillis: Int
    }

    private let directory: URL
    private let queue = DispatchQueue(label: "com.tomessa.miff.notification-plan")

    init(directory: URL? = nil) {
        if let directory {
            self.directory = directory
        } else {
            let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
                ?? FileManager.default.temporaryDirectory
            self.directory = base.appendingPathComponent("NotificationPlan", isDirectory: true)
        }
    }

    private var snapshotURL: URL {
        directory.appendingPathComponent("snapshot.json")
    }

    private var ledgerURL: URL {
        directory.appendingPathComponent("ledger.json")
    }

    func loadSnapshot(uid: String) -> NotificationPlanSnapshot? {
        queue.sync {
            guard let data = try? Data(contentsOf: snapshotURL),
                  let stored = try? JSONDecoder().decode(StoredSnapshot.self, from: data),
                  stored.uid == uid else {
                return nil
            }
            return stored.snapshot
        }
    }

    func saveSnapshot(_ snapshot: NotificationPlanSnapshot, uid: String) {
        write(StoredSnapshot(uid: uid, snapshot: snapshot), to: snapshotURL)
    }

    func loadLedger(uid: String) -> NotificationLedger {
        queue.sync {
            guard let data = try? Data(contentsOf: ledgerURL),
                  let stored = try? JSONDecoder().decode(StoredLedger.self, from: data),
                  stored.uid == uid else {
                return NotificationLedger()
            }
            return stored.ledger
        }
    }

    func saveLedger(_ ledger: NotificationLedger, uid: String) {
        write(StoredLedger(uid: uid, ledger: ledger), to: ledgerURL)
    }

    func loadTravelEnd(uid: String) -> Int? {
        queue.sync {
            guard let data = try? Data(contentsOf: travelEndURL),
                  let stored = try? JSONDecoder().decode(StoredTravelEnd.self, from: data),
                  stored.uid == uid else {
                return nil
            }
            return stored.untilMillis
        }
    }

    func saveTravelEnd(_ untilMillis: Int?, uid: String) {
        guard let untilMillis else {
            queue.sync {
                try? FileManager.default.removeItem(at: travelEndURL)
            }
            return
        }
        write(StoredTravelEnd(uid: uid, untilMillis: untilMillis), to: travelEndURL)
    }

    func clear() {
        queue.sync {
            try? FileManager.default.removeItem(at: directory)
        }
    }

    private func write<Value: Encodable>(_ value: Value, to url: URL) {
        queue.sync {
            guard prepareDirectory(), let data = try? JSONEncoder().encode(value) else {
                return
            }
            try? data.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
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
