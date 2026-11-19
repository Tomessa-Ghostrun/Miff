import Foundation

// Les phrases du Feed : ce que `FeedBuilder` a décidé (des faits), écrit dans la langue du téléphone avec les clés `feed_*` du
// catalogue (français et anglais, mêmes clés qu'Android). Les heures suivent la langue ET le choix 12 h / 24 h du téléphone
// (« 16 h 02 » en français ; « 4:02 PM » ou « 16:02 » en anglais). Une phrase est une clé entière : rien n'est assemblé à partir de
// morceaux, sauf une heure, un nombre, un prénom ou un complément de lieu COMPLET (préposition et article compris, une clé par catégorie et par sens). Rien n'est journalisé ; les prénoms reçus sont montrés, jamais gardés.
// La recherche du texte, la langue, le fuseau et le format 12 h / 24 h sont injectables : les tests lisent le VRAI catalogue.

struct FeedTexts {
    private let lookup: (String) -> String
    let locale: Locale
    private let zone: TimeZone
    private let is24Hour: Bool

    init(
        lookup: @escaping (String) -> String = { L10n.string($0) },
        locale: Locale = AppLanguage.locale,
        zone: TimeZone = .current,
        is24Hour: Bool = FeedTexts.phoneUses24Hour()
    ) {
        self.lookup = lookup
        self.locale = locale
        self.zone = zone
        self.is24Hour = is24Hour
    }

    /// Le téléphone affiche-t-il l'heure sur 24 h ? Le motif « j » du système se résout en « h a » (12 h) ou « HH » (24 h).
    static func phoneUses24Hour(locale: Locale = .current) -> Bool {
        let pattern = DateFormatter.dateFormat(fromTemplate: "j", options: 0, locale: locale) ?? ""
        return !pattern.contains("a")
    }

    func text(_ key: String, _ arguments: CVarArg...) -> String {
        String(format: lookup(key), locale: locale, arguments: arguments)
    }

    private func formatted(_ millis: Int, pattern: String) -> String {
        let formatter = DateFormatter()
        formatter.locale = locale
        formatter.timeZone = zone
        formatter.dateFormat = pattern
        return formatter.string(from: Date(timeIntervalSince1970: Double(millis) / 1000))
    }

    /// Espaces insécables : « 16 h 02 » et « 4:40 PM » ne se coupent jamais sur deux lignes d'un titre.
    func clock(_ millis: Int) -> String {
        let pattern = lookup(is24Hour ? "feed_time_pattern_24" : "feed_time_pattern_12")
        return formatted(millis, pattern: pattern).replacingOccurrences(of: " ", with: "\u{00A0}")
    }

    func date(_ millis: Int) -> String {
        formatted(millis, pattern: lookup("feed_date_pattern"))
    }

    /// « à 16 h 02 », « hier à 16 h 02 », « le 3 nov. à 16 h 02 ».
    func at(_ moment: Moment) -> String {
        switch moment.day {
        case .today:
            return text("feed_moment_at_today", clock(moment.atMillis))
        case .yesterday:
            return text("feed_moment_at_yesterday", clock(moment.atMillis))
        case .other:
            return text("feed_moment_at_date", date(moment.atMillis), clock(moment.atMillis))
        }
    }

    /// « 16 h 02 » (après « depuis »), « hier à 16 h 02 », « le 3 nov. à 16 h 02 ».
    func since(_ moment: Moment) -> String {
        switch moment.day {
        case .today:
            return text("feed_moment_since_today", clock(moment.atMillis))
        case .yesterday:
            return text("feed_moment_since_yesterday", clock(moment.atMillis))
        case .other:
            return text("feed_moment_since_date", date(moment.atMillis), clock(moment.atMillis))
        }
    }

    /// « de 16 h 02 », « d'hier à 16 h 02 », « du 3 nov. à 16 h 02 ».
    func from(_ moment: Moment) -> String {
        switch moment.day {
        case .today:
            return text("feed_moment_from_today", clock(moment.atMillis))
        case .yesterday:
            return text("feed_moment_from_yesterday", clock(moment.atMillis))
        case .other:
            return text("feed_moment_from_date", date(moment.atMillis), clock(moment.atMillis))
        }
    }

    private func moment(of age: Age) -> Moment? {
        switch age {
        case .yesterday(let atMillis):
            return Moment(atMillis: atMillis, day: .yesterday)
        case .date(let atMillis):
            return Moment(atMillis: atMillis, day: .other)
        case .minutes, .hours:
            return nil
        }
    }

    /// « il y a 20 min » / « il y a 3 h » : la feuille « Localiser » seulement (une position de moins d'un jour).
    func ago(_ age: Age) -> String {
        switch age {
        case .minutes(let minutes):
            return text("feed_ago_minutes", minutes)
        case .hours(let hours):
            return text("feed_ago_hours", hours)
        default:
            return moment(of: age).map { at($0) } ?? ""
        }
    }

    /// Le sens dans lequel un lieu est cité : ce qui suit « a quitté » (`leave`) ou « a rejoint » (`arrive`).
    enum PlaceMovement {
        case leave
        case arrive
    }

    /// Le COMPLÉMENT DE LIEU d'une phrase, préposition et article compris, écrit en entier dans la langue (« la maison » après « A quitté » ; en
    /// espagnol « de casa » après « salió » et « a casa » après « llegó »). Une clé par catégorie ET par sens : aucun article ni aucune
    /// préposition n'est jamais collé à un nom de lieu par le code (accords et contractions « del », « al », « do », « na » impossibles à
    /// deviner). Un lieu nommé par la personne passe dans `feed_place_<sens>_named` (le nom, tel quel : jamais traduit ni décliné).
    func placeComplement(_ place: PlaceRef, movement: PlaceMovement) -> String {
        switch place {
        case .category(let kind):
            return text(categoryKey(kind, movement))
        case .named(let name):
            return text(movement == .leave ? "feed_place_leave_named" : "feed_place_arrive_named", name)
        case .unknown:
            return text(movement == .leave ? "feed_place_leave_generic" : "feed_place_arrive_generic")
        }
    }

    /// Les clés sont écrites en entier (et non bâties par morceaux) : on les retrouve en cherchant le texte, et un test vérifie qu'elles existent.
    private func categoryKey(_ kind: PlaceKind, _ movement: PlaceMovement) -> String {
        switch (movement, kind) {
        case (.leave, .home):
            return "feed_place_leave_home"
        case (.leave, .work):
            return "feed_place_leave_work"
        case (.leave, .school):
            return "feed_place_leave_school"
        case (.leave, .sport):
            return "feed_place_leave_sport"
        case (.leave, .relative):
            return "feed_place_leave_relative"
        case (.arrive, .home):
            return "feed_place_arrive_home"
        case (.arrive, .work):
            return "feed_place_arrive_work"
        case (.arrive, .school):
            return "feed_place_arrive_school"
        case (.arrive, .sport):
            return "feed_place_arrive_sport"
        case (.arrive, .relative):
            return "feed_place_arrive_relative"
        }
    }

    func label(_ label: StateLabel) -> String {
        switch label {
        case .at(let place):
            return atLabel(place)
        case .left(let place):
            return text("feed_label_left", placeComplement(place, movement: .leave))
        case .danger:
            return text("feed_label_danger")
        case .noNews:
            return text("feed_label_no_news")
        case .sharingStopped:
            return text("feed_label_sharing_stopped")
        case .newsHidden:
            return text("feed_label_news_hidden")
        case .waitingAgreement:
            return text("miff_waiting_title")
        case .traveling(let day, let mine):
            return travelLabel(day, mine: mine)
        case .travelEnded:
            return text("voyage_feed_label_ended")
        case .alertActive:
            // ALERTE : factuel et calme, jamais « en sécurité », jamais un point d'exclamation.
            return text("feed_label_alert")
        }
    }

    private func atLabel(_ place: PlaceRef) -> String {
        switch place {
        case .category(let kind):
            switch kind {
            case .home:
                return text("feed_label_at_home")
            case .work:
                return text("feed_label_at_work")
            case .school:
                return text("feed_label_at_school")
            case .sport:
                return text("feed_label_at_sport")
            case .relative:
                return text("feed_label_at_relative")
            }
        case .named(let name):
            // Loisir, Autre : le NOM du lieu, tel quel (décision du PM) ; la valeur du catalogue est « %@ » seul.
            return text("feed_label_at_place", name)
        case .unknown:
            return text("feed_label_trusted_generic")
        }
    }

    /// La ligne sous l'étiquette d'une carte ; nil quand la carte n'en a pas.
    func timeLine(_ time: TimeLine) -> String? {
        switch time {
        case .nothing:
            return nil
        case .since(let moment):
            return text("feed_time_since", since(moment))
        case .latestNews(let moment):
            return text("feed_time_latest_news", at(moment))
        case .noNewsSince(let moment):
            return text("feed_time_no_news_since", since(moment))
        case .inDangerSince(let moment):
            return text("feed_time_since", since(moment))
        case .travelSince(let millis):
            return travelSince(millis)
        }
    }

    func battery(_ battery: Battery) -> String {
        text("feed_battery", battery.pct)
    }

    func batteryA11y(_ battery: Battery) -> String {
        text("feed_battery_a11y", battery.pct)
    }

    // MARK: - La bulle

    func latestNews(_ age: Age) -> String {
        switch age {
        case .minutes(let minutes):
            return text("feed_bubble_latest_minutes", minutes)
        case .hours(let hours):
            return text("feed_bubble_latest_hours", hours)
        default:
            return moment(of: age).map { text("feed_time_latest_news", at($0)) } ?? ""
        }
    }

    func answered(_ answered: Int, of total: Int) -> String {
        text("feed_bubble_answered", answered, total)
    }

    /// Le titre d'une page d'alertes. Factuel et calme : des heures absolues, jamais « alerte », jamais de point d'exclamation.
    func alertTitle(_ page: AlertPage) -> String {
        let name = page.member.callName
        switch page.kind {
        case .left(let place, let moment):
            if page.isMe {
                return text("feed_bubble_me_orange", placeComplement(place, movement: .leave), at(moment))
            }
            return text("feed_bubble_orange", name, placeComplement(place, movement: .leave), at(moment))
        case .noNewsSince(let moment):
            if page.isMe {
                return text("feed_bubble_me_red_stale", since(moment))
            }
            return text("feed_bubble_red_stale", name, since(moment))
        case .declared(let moment):
            // ALERTE : attribuée à la personne (« a déclaré une alerte à 15 h 02 ») ; jamais « en sécurité », jamais un point d'exclamation.
            if page.isMe {
                return text("feed_bubble_me_alert", at(moment))
            }
            return text("feed_bubble_alert", name, at(moment))
        case .danger(let place, _):
            if page.isMe {
                return text("feed_bubble_me_danger")
            }
            // Le mot du PM pour les proches : factuel et calme, « {Prénom} est à {lieu} ».
            if case .named(let placeName) = place {
                return text("feed_bubble_danger_named", name, placeName)
            }
            return text("feed_bubble_danger", name)
        }
    }

    /// La seconde ligne d'une page d'alertes (seule une zone de danger en a une : depuis quand).
    func alertDetail(_ page: AlertPage) -> String? {
        if case .danger(_, let moment) = page.kind {
            return text("feed_time_since", since(moment))
        }
        return nil
    }

    func alertNote(_ page: AlertPage) -> String? {
        guard case .danger = page.kind else {
            return nil
        }
        return text(page.isMe ? "feed_bubble_me_danger_note" : "feed_bubble_danger_note")
    }

    /// Le nom parlé du bouton d'une bulle d'alerte : « Contacter Thomas » (même clé qu'Android, `feed_bubble_contact_a11y`).
    func contactA11y(_ name: String) -> String {
        text("feed_bubble_contact_a11y", name)
    }

    /// Le nom parlé du bouton « Appeler » d'une bulle d'alerte déclarée : « Appeler Thomas » (même clé que le bouton de la carte).
    func callA11y(_ name: String) -> String {
        text("feed_card_call_a11y", name)
    }

    func offlineAlert(_ page: AlertPage) -> String? {
        page.offlineSince.map { text("feed_bubble_offline_alert", from($0)) }
    }

    /// Ce que le lecteur d'écran annonce quand la bulle change (jamais à chaque minute) : le titre et son heure.
    func announcement(_ bubble: Bubble) -> String {
        switch bubble {
        case .allGood:
            return text("feed_bubble_all_good")
        case .noNews, .awaitingAgreement:
            return text("feed_bubble_no_news_title")
        case .alone:
            return text("feed_bubble_alone")
        case .sharingStopped:
            return text("feed_bubble_sharing_stopped_title")
        case .travelers(let note):
            return travelersHeadline(note)
        case .alerts(let pages, _):
            return pages.map { page in
                [alertTitle(page), alertDetail(page)].compactMap { $0 }.joined(separator: ", ")
            }.joined(separator: ". ")
        }
    }

    func offlineBanner(_ latest: Moment?) -> String {
        guard let latest else {
            return text("feed_offline_note")
        }
        return text("feed_banner_offline", from(latest))
    }

    // MARK: - Ce que dit une carte pour le lecteur d'écran, et la feuille « Localiser »

    /// « Thomas, à la maison, depuis 12 min, batterie 85 % » : la carte se lit comme un tout (critère T2). Ma carte ajoute sa ligne
    /// « Ce que tes proches voient de toi » (décision D42 : rien n'est affiché à côté du prénom, ma carte se reconnaît à cette ligne).
    func cardSpoken(_ card: FeedMemberCard) -> String {
        var parts = [card.member.callName, label(card.label)]
        if let line = timeLine(card.time) {
            parts.append(line)
        }
        if card.limitedLocation {
            parts.append(text("feed_label_limited_location"))
        }
        if let battery = card.battery {
            parts.append(batteryA11y(battery))
        }
        if card.isMe {
            parts.append(text("feed_card_you_sees"))
        }
        return parts.joined(separator: ", ")
    }

    func locateTitle(_ firstName: String) -> String {
        text("feed_locate_title", firstName)
    }

    func locateRecent(measuredAt: Moment, age: Age) -> String {
        text("feed_locate_recent", at(measuredAt), ago(age))
    }

    func locateOld(_ measuredAt: Moment) -> String {
        text("feed_locate_old", from(measuredAt))
    }
}
