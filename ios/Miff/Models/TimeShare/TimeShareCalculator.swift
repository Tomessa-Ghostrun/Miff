import Foundation

// CONFIDENTIALITÉ, pour ce fichier : du Swift pur. Rien n'est lu sur le téléphone, rien n'est écrit, rien n'est journalisé ; seuls des
// identifiants de lieux, des heures et des durées entrent et sortent. Les règles sont celles du contrat commun iOS / Android
// (`docs/tickets/profil-repartition-temps.md` §6.2 ; cas de test T1 à T23, `TimeShareCalculatorTests`).

/// Ce que dure chaque catégorie dans la fenêtre : le temps MESURÉ seulement.
struct TimeShareTotals: Equatable {
    var trusted: [String: Int] = [:]
    var danger: [String: Int] = [:]
    var elsewhere = 0

    var measured: Int {
        trusted.values.reduce(0, +) + danger.values.reduce(0, +) + elsewhere
    }
}

enum TimeShareCalculator {
    /// La répartition du temps d'UNE Miff sur les `windowDays` derniers jours. `facts` : les faits de cette Miff dans l'ordre où ils ont été
    /// enregistrés. `places` : la liste ACTUELLE des lieux de la Miff.
    static func compute(
        facts: [PresenceFact],
        places: [TimeSharePlace],
        windowDays: Int,
        nowMillis: Int
    ) -> TimeShareOutcome {
        let totals = measure(facts: facts, places: places, windowDays: windowDays, nowMillis: nowMillis)
        // Le journal de cette Miff doit avoir au moins 3 jours (son premier enregistrement) ET du temps mesuré dans la fenêtre choisie.
        let elapsed = facts.first.map { max(0, nowMillis - $0.atMillis) } ?? 0
        guard elapsed >= TimeShareLimits.minimumHistoryMillis, totals.measured > 0 else {
            return .notEnough(daysLeft: TimeShareLimits.daysLeft(elapsedMillis: elapsed))
        }
        return .ready(result(totals: totals, places: places, windowDays: windowDays))
    }

    // MARK: - Le temps mesuré

    /// Les faits rendus sûrs avant tout calcul (ticket §6.2 point 8, T5, T6, T16) : un fait dans le futur est ramené à « maintenant » ; un
    /// fait daté AVANT le précédent (horloge changée) est ramené à l'instant du précédent (durée 0, jamais négative) ; deux faits identiques
    /// de suite (même état, même lieu) n'en font qu'un, le premier.
    static func normalized(_ facts: [PresenceFact], nowMillis: Int) -> [PresenceFact] {
        var result: [PresenceFact] = []
        for fact in facts {
            var at = min(fact.atMillis, nowMillis)
            if let last = result.last {
                at = max(at, last.atMillis)
                if last.state == fact.state && last.placeId == fact.placeId {
                    continue
                }
            }
            result.append(PresenceFact(miffId: fact.miffId, state: fact.state, placeId: fact.placeId, atMillis: at))
        }
        return result
    }

    static func measure(
        facts: [PresenceFact],
        places: [TimeSharePlace],
        windowDays: Int,
        nowMillis: Int
    ) -> TimeShareTotals {
        let windowStart = nowMillis - windowDays * TimeShareLimits.dayMillis
        var typeById: [String: PlaceType] = [:]
        for place in places {
            typeById[place.id] = place.type
        }
        let steady = normalized(facts, nowMillis: nowMillis)
        var totals = TimeShareTotals()
        for (index, fact) in steady.enumerated() {
            // Chaque fait vaut jusqu'au suivant (le dernier jusqu'à maintenant), 48 h au plus, rogné à la fenêtre.
            let next = index + 1 < steady.count ? steady[index + 1].atMillis : nowMillis
            let end = min(next, fact.atMillis + TimeShareLimits.openStateCapMillis, nowMillis)
            let start = max(fact.atMillis, windowStart)
            guard end > start else {
                continue
            }
            let duration = end - start
            switch fact.state {
            case .unknown:
                // Non mesuré : inconnue, voyage, suivi arrêté.
                continue
            case .out:
                totals.elsewhere += duration
            case .trusted:
                // Un lieu supprimé, ou dont le type a changé depuis, est non mesuré (jamais une réattribution devinée).
                if let id = fact.placeId, typeById[id] == .trusted {
                    totals.trusted[id, default: 0] += duration
                }
            case .danger:
                if let id = fact.placeId, typeById[id] == .risk {
                    totals.danger[id, default: 0] += duration
                }
            }
        }
        return totals
    }

    // MARK: - La répartition

    private struct Entry {
        let kind: TimeShareRowKind
        let name: String?
        let category: PlaceCategory?
        let millis: Int
    }

    private struct Ranked {
        let place: TimeSharePlace
        let millis: Int
    }

    private static func result(totals: TimeShareTotals, places: [TimeSharePlace], windowDays: Int) -> TimeShareResult {
        let entries = legend(totals: totals, places: places)
        let shares = TimeSharePercentages.allocate(entries.map { $0.millis })
        let rows = zip(entries, shares).map { entry, share in
            TimeShareRow(
                kind: entry.kind,
                name: entry.name,
                category: entry.category,
                millis: entry.millis,
                percent: share.percent,
                isUnderOnePercent: share.isUnderOne
            )
        }
        let trusted = rows.reduce(0) { sum, row in
            switch row.kind {
            case .place, .otherPlaces:
                return sum + row.percent
            case .danger, .elsewhere:
                return sum
            }
        }
        return TimeShareResult(
            windowDays: windowDays,
            measuredMillis: totals.measured,
            rows: rows,
            dangerDetails: dangerDetails(totals: totals, places: places, rows: rows),
            trustedPercent: trusted
        )
    }

    /// Les lieux d'un type où du temps a été passé, du plus long au plus court ; à durée EXACTEMENT égale, par identifiant du lieu (le même
    /// ordre qu'Android, indépendant de l'ordre de la liste des lieux).
    private static func ranked(_ type: PlaceType, in times: [String: Int], places: [TimeSharePlace]) -> [Ranked] {
        places
            .compactMap { place -> Ranked? in
                guard place.type == type, let millis = times[place.id], millis > 0 else {
                    return nil
                }
                return Ranked(place: place, millis: millis)
            }
            .sorted { left, right in
                left.millis != right.millis ? left.millis > right.millis : left.place.id < right.place.id
            }
    }

    /// La légende, dans l'ordre de l'anneau : les lieux de confiance du plus long au plus court (5 nommés, puis « {n} autres lieux »), la
    /// zone de danger (si du temps y a été passé), « Ailleurs » en dernier. Une catégorie à 0 n'est pas listée.
    private static func legend(totals: TimeShareTotals, places: [TimeSharePlace]) -> [Entry] {
        let trusted = ranked(.trusted, in: totals.trusted, places: places)
        var entries = trusted.prefix(TimeShareLimits.maxNamedPlaces).map {
            Entry(kind: .place($0.place.id), name: $0.place.name, category: $0.place.category, millis: $0.millis)
        }
        let rest = trusted.dropFirst(TimeShareLimits.maxNamedPlaces)
        if !rest.isEmpty {
            let sum = rest.reduce(0) { $0 + $1.millis }
            entries.append(Entry(kind: .otherPlaces(rest.count), name: nil, category: nil, millis: sum))
        }
        let dangerous = ranked(.risk, in: totals.danger, places: places)
        if !dangerous.isEmpty {
            let sum = dangerous.reduce(0) { $0 + $1.millis }
            entries.append(Entry(kind: .danger(dangerous.count), name: nil, category: nil, millis: sum))
        }
        if totals.elsewhere > 0 {
            entries.append(Entry(kind: .elsewhere, name: nil, category: nil, millis: totals.elsewhere))
        }
        return entries
    }

    /// Le détail de la zone de danger : un lieu par ligne, du plus long au plus court, arrondi À L'INTÉRIEUR de la ligne agrégée.
    private static func dangerDetails(
        totals: TimeShareTotals,
        places: [TimeSharePlace],
        rows: [TimeShareRow]
    ) -> [TimeShareDangerDetail] {
        let line = rows.first { row in
            if case .danger = row.kind {
                return true
            }
            return false
        }
        guard let line else {
            return []
        }
        let items = ranked(.risk, in: totals.danger, places: places)
        let millis = items.map { $0.millis }
        let given = TimeSharePercentages.largestRemainder(millis, among: Array(millis.indices), target: line.percent)
        return items.enumerated().map { index, item in
            let percent = given[index] ?? 0
            return TimeShareDangerDetail(
                placeId: item.place.id,
                name: item.place.name,
                category: item.place.category,
                millis: item.millis,
                percent: percent,
                isUnderOnePercent: percent == 0
            )
        }
    }
}
