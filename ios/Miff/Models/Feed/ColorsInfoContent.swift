import Foundation

// Le contenu de l'écran « Les couleurs de ta Miff » (ticket COULEURS-INFO) : une liste ordonnée de lignes, construite à partir des clés
// `feed_colors_*` et des durées lues dans `StatusThresholds`. Aucune donnée, aucun réseau : un écran statique. Séparé de la vue pour que l'ordre
// des blocs, les textes et les durées soient testables sans écran. Miroir d'Android (`ColorsInfoScreen.kt`).

/// Une ligne de l'écran, dans l'ordre où elle est dessinée.
enum ColorsInfoItem: Equatable {
    /// Un titre de section (lu comme un en-tête).
    case heading(String)
    /// La phrase qui précise une section (couleur de description).
    case note(String)
    /// Une couleur de PERSONNE : pastille ronde (l'avatar de statut), le nom de la couleur en gras, puis sa phrase.
    case person(level: StatusLevel, title: String, body: String)
    /// Une couleur de la MIFF : mini-tache à la forme de la bulle, le nom de la couleur, puis sa phrase.
    case miff(level: StatusLevel, title: String, body: String)
    /// Un nom en gras et sa phrase, sans illustration (la zone de danger).
    case block(title: String, body: String)
    /// Une phrase seule.
    case line(String)
}

struct ColorsInfoContent: Equatable {
    let title: String
    let intro: String
    let items: [ColorsInfoItem]

    /// Le contenu, dans la langue du bundle et avec le formateur de la langue donnée.
    static func make(
        durations: ColorsInfoDurations = .fromThresholds,
        locale: Locale = .current,
        bundle: Bundle = .main
    ) -> ColorsInfoContent {
        let builder = Builder(durations: durations, locale: locale, bundle: bundle)
        return ColorsInfoContent(
            title: builder.text("feed_colors_title"),
            intro: builder.text("feed_colors_intro"),
            items: builder.personItems() + builder.changeItems() + builder.miffItems() + builder.notItems()
        )
    }

    private struct Builder {
        let durations: ColorsInfoDurations
        let locale: Locale
        let bundle: Bundle

        private var orange: String {
            WholeDuration.of(millis: durations.orangeAfterMillis).text(locale: locale)
        }

        private var red: String {
            WholeDuration.of(millis: durations.redAfterMillis).text(locale: locale)
        }

        private var danger: String {
            WholeDuration.of(millis: durations.dangerGreenMillis).text(locale: locale)
        }

        /// Le texte d'une clé ; `%1$@`, `%2$@` reçoivent les durées déjà écrites.
        func text(_ key: String, _ arguments: String...) -> String {
            let format = L10n.string(key, bundle: bundle)
            return arguments.isEmpty ? format : String(format: format, locale: locale, arguments: arguments)
        }

        func personItems() -> [ColorsInfoItem] {
            [
                .heading(text("feed_colors_person_title")),
                .note(text("feed_colors_person_intro")),
                .person(
                    level: .green, title: text("feed_colors_person_green_title"),
                    body: text("feed_colors_person_green_body", orange)
                ),
                .person(
                    level: .orange, title: text("feed_colors_person_orange_title"),
                    body: text("feed_colors_person_orange_body", orange, red)
                ),
                .person(
                    level: .red, title: text("feed_colors_person_red_title"),
                    body: text("feed_colors_person_red_body", red)
                ),
                .person(
                    level: .neutral, title: text("feed_colors_person_gray_title"),
                    body: text("feed_colors_person_gray_body")
                ),
                .person(
                    level: .neutral, title: text("feed_colors_person_travel_title"),
                    body: text("feed_colors_person_travel_body")
                ),
                // La zone de danger : un court paragraphe à part, elle ne concerne que les Miffs qui en ont défini une.
                .block(title: text("feed_colors_danger_title"), body: text("feed_colors_danger_body", danger)),
            ]
        }

        func changeItems() -> [ColorsInfoItem] {
            [
                .heading(text("feed_colors_changes_title")),
                .line(text("feed_colors_changes_arrive")),
                .line(text("feed_colors_changes_leave")),
                .line(text("feed_colors_changes_news")),
                .line(text("feed_colors_changes_time", orange, red)),
                .line(text("feed_colors_changes_pause")),
            ]
        }

        func miffItems() -> [ColorsInfoItem] {
            [
                .heading(text("feed_colors_miff_title")),
                .note(text("feed_colors_miff_intro")),
                .miff(
                    level: .green, title: text("feed_colors_miff_green_title"),
                    body: text("feed_colors_miff_green_body")
                ),
                .miff(
                    level: .orange, title: text("feed_colors_miff_orange_title"),
                    body: text("feed_colors_miff_orange_body")
                ),
                .miff(
                    level: .red, title: text("feed_colors_miff_red_title"),
                    body: text("feed_colors_miff_red_body")
                ),
                .miff(
                    level: .neutral, title: text("feed_colors_miff_gray_title"),
                    body: text("feed_colors_miff_gray_body")
                ),
                .line(text("feed_colors_miff_ignored")),
            ]
        }

        func notItems() -> [ColorsInfoItem] {
            [
                .heading(text("feed_colors_not_title")),
                .line(text("feed_colors_not_alert")),
                .line(text("feed_colors_not_location")),
                .line(text("feed_colors_not_verdict")),
                .line(text("feed_colors_not_delay")),
            ]
        }
    }
}
