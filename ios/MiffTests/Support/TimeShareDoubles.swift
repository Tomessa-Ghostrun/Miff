import Foundation
@testable import Miff

// Doubles et fabriques de la répartition du temps (TIME-3, TIME-4). Du Swift pur : aucun fichier, aucun réseau.

/// Le journal de présence en mémoire : ce que le moteur y a écrit, dans l'ordre, et ce qu'on lui a demandé d'effacer.
final class MemoryPresenceHistory: PresenceHistory {
    private(set) var facts: [PresenceFact] = []
    private(set) var window = TimeShareLimits.defaultWindowDays
    private(set) var clearAllCalls = 0
    private(set) var eraseFactsCalls = 0
    private(set) var purged: [String] = []
    /// Vrai : le stockage est illisible (« Réessayer »).
    var unreadable = false

    func record(_ fact: PresenceFact) {
        facts.append(fact)
    }

    func read(miffId: String) -> PresenceHistoryRead {
        unreadable ? .unreadable : .facts(facts.filter { $0.miffId == miffId })
    }

    func windowDays() -> Int {
        window
    }

    func setWindowDays(_ days: Int) {
        window = TimeShareLimits.validWindow(days)
    }

    func forgetPlaces(miffId: String, keeping placeIds: Set<String>) {
        facts = facts.map { fact in
            let named = fact.state == .trusted || fact.state == .danger
            guard fact.miffId == miffId, named, !(fact.placeId.map { placeIds.contains($0) } ?? false) else {
                return fact
            }
            return PresenceFact(miffId: miffId, state: .unknown, placeId: nil, atMillis: fact.atMillis)
        }
    }

    func purge(miffId: String) {
        purged.append(miffId)
        facts.removeAll { $0.miffId == miffId }
    }

    func retainOnly(miffIds: Set<String>) {
        facts.removeAll { !miffIds.contains($0.miffId) }
    }

    func eraseFacts() {
        eraseFactsCalls += 1
        facts = []
    }

    func clearAll() {
        clearAllCalls += 1
        facts = []
        window = TimeShareLimits.defaultWindowDays
    }

    /// Les faits d'une Miff sous forme de triplets lisibles : (état, lieu, heure).
    func triples(_ miffId: String) -> [String] {
        facts.filter { $0.miffId == miffId }.map { "\($0.state.rawValue)|\($0.placeId ?? "-")|\($0.atMillis)" }
    }
}

/// Fabrique de scénarios de calcul : l'heure est en HEURES avant « maintenant » (168 h = 7 jours).
enum TimeShareFixtures {
    /// Un segment d'une suite d'états : (état, lieu, durée en heures).
    typealias Segment = (PresenceState, String?, Double) // swiftlint:disable:this large_tuple

    static let hour = TimeShareLimits.hourMillis
    static let now = 1_800_000_000_000

    static let home = TimeSharePlace(id: "p1", name: "Maison", type: .trusted, category: .home)
    static let sport = TimeSharePlace(id: "p2", name: "Salle", type: .trusted, category: .sport)
    static let work = TimeSharePlace(id: "p3", name: "Bureau", type: .trusted, category: .work)
    static let risk1 = TimeSharePlace(id: "r1", name: "Chantier", type: .risk, category: .other)
    static let risk2 = TimeSharePlace(id: "r2", name: "Entrepôt", type: .risk, category: nil)

    /// Un fait « il y a `hoursAgo` heures ».
    static func fact(
        _ state: PresenceState,
        _ placeId: String? = nil,
        hoursAgo: Double,
        miff: String = "m1"
    ) -> PresenceFact {
        PresenceFact(miffId: miff, state: state, placeId: placeId, atMillis: now - Int(hoursAgo * Double(hour)))
    }

    /// Une suite d'états qui finit à « maintenant » : chaque segment (état, lieu, durée en heures) commence quand le précédent finit.
    /// Les segments se SUIVENT : deux segments identiques de suite n'en feraient qu'un (T5), donc on les alterne.
    static func timeline(_ segments: [TimeShareFixtures.Segment], miff: String = "m1") -> [PresenceFact] {
        let total = segments.reduce(0.0) { $0 + $1.2 }
        var cursor = now - Int(total * Double(hour))
        var facts: [PresenceFact] = []
        for (state, placeId, hours) in segments {
            facts.append(PresenceFact(miffId: miff, state: state, placeId: placeId, atMillis: cursor))
            cursor += Int(hours * Double(hour))
        }
        return facts
    }

    static func compute(
        _ facts: [PresenceFact],
        places: [TimeSharePlace],
        days: Int = 7
    ) -> TimeShareOutcome {
        TimeShareCalculator.compute(facts: facts, places: places, windowDays: days, nowMillis: now)
    }

    /// Le résultat quand il y a assez de données ; le test échoue sinon.
    static func ready(_ outcome: TimeShareOutcome) -> TimeShareResult? {
        if case .ready(let result) = outcome {
            return result
        }
        return nil
    }
}
