import Foundation

/// Les régions que l'app vient de créer, par clé de zone, gardées en mémoire un court moment.
///
/// Pourquoi : juste après `startMonitoring`, la liste `monitoredRegions` du système peut ne pas contenir encore la région. Retrouver la
/// région dans cette liste pour lui demander son état (`requestState`) ferait alors une demande ignorée en silence, et le cas « déjà
/// dedans » à l'armement serait perdu. On garde donc l'objet qu'on vient de créer, on s'en sert pour la demande, et on le lâche dès que
/// le système le montre dans sa liste (ou après un délai, pour ne jamais prétendre surveiller une région que le système a abandonnée).
/// Générique et sans CoreLocation, pour être testé.
struct RegionRegistry<Region> {
    private struct Entry {
        let region: Region
        let createdAt: Date
    }

    private var entries: [String: Entry] = [:]

    /// Les clés des régions créées et pas encore rendues par le système.
    var keys: Set<String> {
        Set(entries.keys)
    }

    mutating func remember(_ region: Region, key: String, at date: Date) {
        entries[key] = Entry(region: region, createdAt: date)
    }

    mutating func forget(key: String) {
        entries[key] = nil
    }

    func region(for key: String) -> Region? {
        entries[key]?.region
    }

    /// Lâche ce que le système montre maintenant (il fait foi) et ce qui date de plus de `maxAge` secondes.
    mutating func prune(visible: Set<String>, now: Date, maxAge: TimeInterval) {
        for (key, entry) in entries where visible.contains(key) || now.timeIntervalSince(entry.createdAt) > maxAge {
            entries[key] = nil
        }
    }
}
