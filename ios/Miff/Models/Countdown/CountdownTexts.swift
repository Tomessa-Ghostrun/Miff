import Foundation

// Les textes du compte à rebours (clés `countdown_*`, ticket §2.7). Les durées sont des textes du catalogue avec des nombres en paramètres,
// JAMAIS le formateur de durée du système : ses abréviations changent d'une plateforme à l'autre, or Android et iOS doivent afficher
// exactement la même chose (« 1 h 12 min »). Un espace insécable relie le nombre à son unité.

struct CountdownTexts {
    let bundle: Bundle
    let locale: Locale

    init(bundle: Bundle = .main, locale: Locale = .current) {
        self.bundle = bundle
        self.locale = locale
    }

    private func text(_ key: String) -> String {
        L10n.string(key, bundle: bundle)
    }

    private func format(_ key: String, _ arguments: CVarArg...) -> String {
        String(format: text(key), locale: locale, arguments: arguments)
    }

    private func plural(_ key: String, count: Int) -> String {
        String.localizedStringWithFormat(text(key), count)
    }

    var title: String {
        text("countdown_title")
    }

    /// « 1 h 12 min », « 1 h », « 47 min », « moins d'1 min ».
    func duration(_ value: CountdownDuration) -> String {
        switch value {
        case .hoursMinutes(let hours, let minutes):
            return format("countdown_duration_hours_minutes", hours, minutes)
        case .hours(let hours):
            return format("countdown_duration_hours", hours)
        case .minutes(let minutes):
            return format("countdown_duration_minutes", minutes)
        case .lessThanMinute:
            return text("countdown_duration_less_than_minute")
        }
    }

    /// « Tu passeras orange dans 1 h 12 min ».
    func body(_ value: CountdownDuration) -> String {
        format("countdown_body", duration(value))
    }

    /// La durée DITE par le lecteur d'écran : « 1 heure et 12 minutes », « 2 heures », « 47 minutes », « moins d'une minute ».
    func spoken(_ value: CountdownDuration) -> String {
        switch value {
        case .hoursMinutes(let hours, let minutes):
            return format(
                "countdown_a11y_hours_and_minutes",
                plural("countdown_a11y_hours", count: hours),
                plural("countdown_a11y_minutes", count: minutes)
            )
        case .hours(let hours):
            return plural("countdown_a11y_hours", count: hours)
        case .minutes(let minutes):
            return plural("countdown_a11y_minutes", count: minutes)
        case .lessThanMinute:
            return text("countdown_a11y_less_than_minute")
        }
    }

    /// Le nom parlé de l'encart : les deux phrases, avec la durée dite.
    func accessibilityLabel(_ value: CountdownDuration) -> String {
        format("countdown_a11y_label", spoken(value))
    }

    /// Ce que dit le lecteur d'écran qu'un double toucher fait (l'équivalent iOS de l'étiquette d'action d'Android).
    var accessibilityAction: String {
        text("countdown_a11y_action")
    }
}
