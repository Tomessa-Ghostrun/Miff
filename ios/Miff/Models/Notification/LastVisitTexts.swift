import Foundation

// Les phrases de « Depuis ta dernière visite » (NOTIF-P1I), faites quand une ligne est DESSINÉE, à partir de ce que la ligne porte (une
// sorte, une catégorie, une heure) et du prénom que l'app connaît MAINTENANT : le centre n'en garde aucun. Français et anglais, mêmes clés
// qu'Android (`notif_c1_*`). Les heures suivent la langue et le choix 12 h / 24 h du téléphone (comme le Feed). Aucune phrase ne porte de
// durée (« depuis 4 h »), de verdict ni de promesse : un fait et une heure (décision Q1 du PM, 2026-11-13).

struct LastVisitTexts {
    private let feed: FeedTexts
    private let zone: TimeZone

    /// Les tests passent un `FeedTexts` et un fuseau fixes ; l'app lit le téléphone À CHAQUE usage.
    init(feed: FeedTexts = FeedTexts(), zone: TimeZone = .current) {
        self.feed = feed
        self.zone = zone
    }

    private func weekday(_ millis: Int) -> String {
        let formatter = DateFormatter()
        formatter.locale = feed.locale
        formatter.timeZone = zone
        formatter.dateFormat = "EEEE"
        return formatter.string(from: Date(timeIntervalSince1970: Double(millis) / 1000))
    }

    /// « à 17 h 10 », « hier à 17 h 10 », « lundi à 9 h 30 », « le 3 nov. à 9 h 30 ».
    func at(_ millis: Int, nowMillis: Int) -> String {
        switch LastVisit.dayOf(atMillis: millis, nowMillis: nowMillis, zone: zone) {
        case .today:
            return feed.text("feed_moment_at_today", feed.clock(millis))
        case .yesterday:
            return feed.text("feed_moment_at_yesterday", feed.clock(millis))
        case .weekday:
            return feed.text("notif_c1_moment_at_weekday", weekday(millis), feed.clock(millis))
        case .date:
            return feed.text("feed_moment_at_date", feed.date(millis), feed.clock(millis))
        }
    }

    /// « 17 h 10 » (après les deux-points), « hier à 17 h 10 », « lundi à 9 h 30 », « le 3 nov. à 9 h 30 ».
    func since(_ millis: Int, nowMillis: Int) -> String {
        switch LastVisit.dayOf(atMillis: millis, nowMillis: nowMillis, zone: zone) {
        case .today:
            return feed.text("feed_moment_since_today", feed.clock(millis))
        case .yesterday:
            return feed.text("feed_moment_since_yesterday", feed.clock(millis))
        case .weekday:
            return feed.text("notif_c1_moment_since_weekday", weekday(millis), feed.clock(millis))
        case .date:
            return feed.text("feed_moment_since_date", feed.date(millis), feed.clock(millis))
        }
    }

    private static let vowels = "aeiouyàâäéèêëîïôöùûüœæh"

    /// La phrase d'une ligne. `firstName` est le prénom que l'app connaît maintenant, ou nil (démarrage à froid avant que le Feed ait relu les
    /// membres) : « Un proche » / « Someone » alors, jamais un blanc. Aucun accord de genre dans aucune des quatre phrases.
    func sentence(_ line: VisitParsed, firstName: String?, nowMillis: Int) -> String {
        let trimmed = firstName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let name = trimmed.isEmpty ? feed.text("notif_c1_someone") : trimmed
        let place = feed.placeNoun(line.place.map { PlaceRef.category($0) } ?? PlaceRef.unknown)
        let text: String
        switch line.kind {
        case .arrival:
            text = feed.text("notif_c1_arrival", name, place, at(line.shownAtMillis, nowMillis: nowMillis))
        case .departure:
            text = feed.text("notif_c1_departure", name, place, at(line.shownAtMillis, nowMillis: nowMillis))
        case .update:
            text = feed.text("notif_c1_update", name, at(line.shownAtMillis, nowMillis: nowMillis))
        case .silence:
            // « de Thomas », « d'Amandine » : l'élision se choisit à la première lettre (le texte anglais des deux clés est le même).
            let key = startsWithVowel(name) ? "notif_c1_silence_vowel" : "notif_c1_silence"
            text = feed.text(key, name, since(line.shownAtMillis, nowMillis: nowMillis))
        }
        // « un proche » ouvre une phrase (« Un proche a quitté l'école… ») : la première lettre est une majuscule, quelle que soit la langue.
        return String(text.prefix(1)).uppercased(with: feed.locale) + String(text.dropFirst())
    }

    private func startsWithVowel(_ name: String) -> Bool {
        guard let first = name.first else {
            return false
        }
        return Self.vowels.contains(first.lowercased())
    }
}
