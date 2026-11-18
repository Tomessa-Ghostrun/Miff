import XCTest
@testable import Miff

/// La carte « Répartition de ton temps » : ses états (ticket §7), ses textes en français et en anglais (critère TS-15, TS-22), l'anneau, la
/// palette (critère TS-17, contraste 3:1) et la phrase de synthèse (D13, critère TS-17).
final class TimeShareCardTests: XCTestCase {
    private typealias Scn = TimeShareFixtures

    private func readyResult() throws -> TimeShareResult {
        let facts = Scn.timeline([(.trusted, "p1", 30), (.out, nil, 10)])
        return try XCTUnwrap(Scn.ready(Scn.compute(facts, places: [Scn.home])))
    }

    // MARK: - Les états et leur priorité

    func testNoPlaceOrNoPlacesYetMeansHidden() {
        let outcome = TimeShareOutcome.notEnough(daysLeft: 7)

        XCTAssertEqual(
            TimeShareCardState.make(
                places: nil, readFailed: false, outcome: outcome, conditions: TimeShareConditions()
            ),
            .hidden
        )
        XCTAssertEqual(
            TimeShareCardState.make(places: [], readFailed: true, outcome: nil, conditions: TimeShareConditions()),
            .hidden,
            "masquée prime sur l'erreur"
        )
    }

    func testAnErrorComesBeforeEverythingButHidden() {
        let state = TimeShareCardState.make(
            places: [Scn.home], readFailed: true, outcome: nil, conditions: TimeShareConditions(travelActive: true)
        )

        XCTAssertEqual(state, .unavailable)
    }

    func testTheStoppedTrackingReasonsComeBeforeNotEnoughDataInThisOrder() {
        let outcome = TimeShareOutcome.notEnough(daysLeft: 7)
        func state(_ conditions: TimeShareConditions) -> TimeShareCardState {
            TimeShareCardState.make(places: [Scn.home], readFailed: false, outcome: outcome, conditions: conditions)
        }

        XCTAssertEqual(state(TimeShareConditions()), .notEnough(daysLeft: 7))
        XCTAssertEqual(state(TimeShareConditions(locationInsufficient: true)), .locationInsufficient)
        XCTAssertEqual(state(TimeShareConditions(sharingStopped: true, locationInsufficient: true)), .sharingStopped)
        XCTAssertEqual(
            state(TimeShareConditions(travelActive: true, sharingStopped: true, locationInsufficient: true)),
            .travel
        )
    }

    func testTheOverTwentyZonesNoteDoesNotChangeTheState() {
        let outcome = TimeShareOutcome.notEnough(daysLeft: 7)

        let state = TimeShareCardState.make(
            places: [Scn.home], readFailed: false, outcome: outcome,
            conditions: TimeShareConditions(overWatchedLimit: true)
        )

        XCTAssertEqual(state, .notEnough(daysLeft: 7))
    }

    func testEnoughDataIsNominalAndAStoppedTrackingOnlyAddsAPauseLine() throws {
        let result = try readyResult()
        func state(_ conditions: TimeShareConditions) -> TimeShareCardState {
            TimeShareCardState.make(
                places: [Scn.home], readFailed: false, outcome: .ready(result), conditions: conditions
            )
        }

        XCTAssertEqual(state(TimeShareConditions()), .nominal(result, pause: false))
        XCTAssertEqual(state(TimeShareConditions(travelActive: true)), .nominal(result, pause: true))
        XCTAssertEqual(state(TimeShareConditions(sharingStopped: true)), .nominal(result, pause: true))
        XCTAssertEqual(state(TimeShareConditions(locationInsufficient: true)), .nominal(result, pause: true))
    }

    // MARK: - Les textes, en français et en anglais

    private func bundle(_ language: String) throws -> Bundle {
        let path = try XCTUnwrap(Bundle.main.path(forResource: language, ofType: "lproj"), language)
        return try XCTUnwrap(Bundle(path: path))
    }

    func testEveryTextOfTheCardExistsInBothLanguages() throws {
        var keys = [
            "profile_timeshare_title", "profile_timeshare_window_a11y", "profile_timeshare_window_selected_a11y",
            "profile_timeshare_center_caption", "profile_timeshare_a11y_card", "profile_timeshare_a11y_row",
            "profile_timeshare_a11y_danger_row", "profile_timeshare_elsewhere",
            "profile_timeshare_percent_tiny", "profile_timeshare_detail_close",
            "profile_timeshare_not_enough", "profile_timeshare_summary", "profile_timeshare_paused",
            "profile_timeshare_travel",
            "profile_timeshare_sharing_stopped", "profile_timeshare_sharing_stopped_action",
            "profile_timeshare_location_needed", "profile_timeshare_over_limit", "profile_timeshare_error",
            "profile_timeshare_other_places", "profile_timeshare_danger_zones",
            "profile_timeshare_danger_zones_several", "settings_privacy_time_title",
            "settings_privacy_time_body", "settings_privacy_time_erase", "settings_privacy_time_confirm_title",
            "settings_privacy_time_confirm_body", "settings_privacy_time_confirm_action", "settings_privacy_time_done"
        ]
        for window in TimeShareLimits.windowsDays {
            for base in [
                "profile_timeshare_window"
            ] {
                keys.append("\(base)_\(window)")
            }
        }
        for language in ["fr", "en"] {
            let bundle = try bundle(language)
            for key in keys {
                XCTAssertNotEqual(L10n.string(key, bundle: bundle), key, "\(key) non traduit en \(language)")
            }
        }
    }

    func testTheFrozenWordingOfTheNotEnoughDataStateIsExactAndPluralisedInBothLanguages() throws {
        let french = try bundle("fr")
        let english = try bundle("en")
        func text(_ bundle: Bundle, _ days: Int) -> String {
            String.localizedStringWithFormat(L10n.string("profile_timeshare_not_enough", bundle: bundle), days)
        }

        XCTAssertEqual(text(french, 7), "Ton graphique arrive dans 7 jours, nous analysons tes habitudes")
        XCTAssertEqual(text(french, 3), "Ton graphique arrive dans 3 jours, nous analysons tes habitudes")
        XCTAssertEqual(text(french, 1), "Ton graphique arrive dans 1 jour, nous analysons tes habitudes")
        XCTAssertEqual(text(english, 7), "Your chart arrives in 7 days, we're analysing your habits")
        XCTAssertEqual(text(english, 1), "Your chart arrives in 1 day, we're analysing your habits")
        XCTAssertEqual(TimeShareTexts.notEnough(daysLeft: 2).contains("2"), true)
    }

    func testTheErasingConfirmationIsExactInFrench() throws {
        let french = try bundle("fr")

        XCTAssertEqual(L10n.string("settings_privacy_time_erase", bundle: french), "Effacer mon historique de temps")
        XCTAssertEqual(L10n.string("settings_privacy_time_confirm_title", bundle: french), "Effacer l'historique ?")
        XCTAssertEqual(
            L10n.string("settings_privacy_time_confirm_body", bundle: french),
            "Ta répartition repartira de zéro. Cela ne change rien pour tes proches."
        )
    }

    func testTheDangerLabelIsTheOneOfTheAppAndPluralised() throws {
        let french = try bundle("fr")
        let english = try bundle("en")
        let danger = { (bundle: Bundle, count: Int) in
            let key = count > 1 ? "profile_timeshare_danger_zones_several" : "profile_timeshare_danger_zones"
            return L10n.string(key, bundle: bundle)
        }

        XCTAssertEqual(danger(french, 1), L10n.string("place_type_danger", bundle: french), "le libellé de l'app")
        XCTAssertEqual(danger(french, 1), "Zone de danger")
        XCTAssertEqual(danger(french, 2), "Zones de danger")
        XCTAssertEqual(danger(english, 1), "Danger zone")
        XCTAssertEqual(danger(english, 2), "Danger zones")
    }

    func testThePluralOfTheOtherPlacesFollowsTheLanguage() throws {
        let french = try bundle("fr")
        let english = try bundle("en")
        let format = { (bundle: Bundle, count: Int) in
            String.localizedStringWithFormat(L10n.string("profile_timeshare_other_places", bundle: bundle), count)
        }

        XCTAssertEqual(format(french, 1), "1 autre lieu")
        XCTAssertEqual(format(french, 2), "2 autres lieux")
        XCTAssertEqual(format(english, 1), "1 other place")
        XCTAssertEqual(format(english, 3), "3 other places")
    }

    func testTheSummaryIsTheOneOfTheMockUpAndNeverNamesTheDangerZone() throws {
        let french = try bundle("fr")
        let english = try bundle("en")
        func text(_ bundle: Bundle) -> String {
            String(format: L10n.string("profile_timeshare_summary", bundle: bundle), "72 %")
        }

        XCTAssertEqual(text(french), "Tu as passé 72 % de ton temps dans tes zones de confiance")
        XCTAssertEqual(text(english), "You spent 72 % of your time in your trusted zones")
        for bundle in [french, english] {
            XCTAssertFalse(text(bundle).localizedCaseInsensitiveContains("danger"))
        }
        XCTAssertTrue(TimeShareTexts.summary(trustedPercent: 72, locale: Locale(identifier: "en_US")).contains("72%"))
    }

    func testTheHeaderTextsAreTheOnesOfTheMockUp() throws {
        let french = try bundle("fr")
        let english = try bundle("en")

        XCTAssertEqual(L10n.string("profile_timeshare_elsewhere", bundle: french), "Hors-zone")
        XCTAssertEqual(L10n.string("profile_timeshare_elsewhere", bundle: english), "Outside zones")
        XCTAssertEqual(L10n.string("profile_timeshare_a11y_card", bundle: french), "Répartition du temps : %1$@.")

        XCTAssertEqual(L10n.string("profile_timeshare_title", bundle: french), "Répartition du temps")
        XCTAssertEqual(L10n.string("profile_timeshare_center_caption", bundle: french), "en zone de confiance")
        for window in TimeShareLimits.windowsDays {
            XCTAssertTrue(TimeShareTexts.windowLabel(window).contains("\(window)"))
        }
    }

    // MARK: - La légende : 4 lignes au plus, la zone de danger toujours

    /// Une ligne : `percent` est la part arrondie (0 : « < 1 % » ou rien).
    private func row(
        _ kind: TimeShareRowKind,
        _ hours: Double,
        percent: Int = 10,
        name: String? = nil
    ) -> TimeShareRow {
        TimeShareRow(
            kind: kind, name: name, category: nil, millis: Int(hours * Double(TimeShareLimits.hourMillis)),
            percent: percent, isUnderOnePercent: percent == 0 && hours > 0
        )
    }

    private func result(_ rows: [TimeShareRow]) -> TimeShareResult {
        TimeShareResult(
            windowDays: 7, measuredMillis: rows.reduce(0) { $0 + $1.millis }, rows: rows, dangerDetails: [],
            trustedPercent: 0
        )
    }

    private func place(_ id: String, _ hours: Double) -> TimeShareRow {
        row(.place(id), hours, name: id)
    }

    func testALegendOfFourPartsOrLessShowsEverything() {
        XCTAssertEqual(result([place("a", 10)]).legendIndices, [0], "un seul lieu")
        XCTAssertEqual(result([place("a", 10), row(.elsewhere, 5)]).legendIndices, [0, 1])
        let four = [place("a", 40), place("b", 30), row(.danger(1), 20), row(.elsewhere, 10)]
        XCTAssertEqual(result(four).legendIndices, [0, 1, 2, 3])
    }

    func testTheFourLongestPartsAreShownInTheOrderOfTheRing() {
        let rows = [
            place("a", 50), place("b", 40), place("c", 5), place("d", 30), place("e", 20), row(.elsewhere, 10)
        ]

        XCTAssertEqual(result(rows).legendIndices, [0, 1, 3, 4], "c et Hors-zone sont dans l'anneau seulement")
    }

    func testASmallDangerZoneOfAtLeastOnePercentTakesTheFourthPlaceOfTheBiggerPlaces() {
        // 4 plus gros, puis une zone de danger à 1 % : elle prend la place du 4e plus gros.
        let rows = [
            place("a", 50), place("b", 40), place("c", 30), place("d", 20), row(.danger(1), 2, percent: 1),
            row(.elsewhere, 25)
        ]

        XCTAssertEqual(result(rows).legendIndices, [0, 1, 2, 4], "le 4e (Hors-zone, 25 h) cède sa place")
    }

    func testADangerZoneJustUnderOnePercentHasNoLineAndItsPlaceGoesToTheNextPart() {
        let rows = [
            place("a", 50), place("b", 40), place("c", 30), row(.danger(1), 1, percent: 0), place("d", 20),
            place("e", 10)
        ]

        XCTAssertEqual(result(rows).legendIndices, [0, 1, 2, 4], "pas de ligne « < 1 % » : d (20 h) garde sa place")
    }

    func testADangerZoneAlreadyInTheTopFourChangesNothing() {
        let rows = [place("a", 50), row(.danger(2), 40, percent: 30), place("b", 30), place("c", 20), place("d", 5)]

        XCTAssertEqual(result(rows).legendIndices, [0, 1, 2, 3])
    }

    func testSingleNullAndTinyParts() {
        XCTAssertEqual(result([place("a", 10)]).legendIndices, [0], "un seul lieu")
        XCTAssertEqual(result([]).legendIndices, [])
        // Part nulle ou « < 1 % » alors qu'il y a peu de parts : jamais de ligne de zone de danger.
        let few = [place("a", 50), row(.danger(1), 1, percent: 0), row(.elsewhere, 5)]
        XCTAssertEqual(result(few).legendIndices, [0, 2])
        let zero = [place("a", 50), place("b", 40), place("c", 30), place("d", 20), row(.danger(1), 0, percent: 0)]
        XCTAssertEqual(result(zero).legendIndices, [0, 1, 2, 3])
    }

    func testTiesKeepTheOrderOfTheRingAndTheDangerZoneStillWins() {
        let rows = [
            place("a", 10), place("b", 10), place("c", 10), place("d", 10), place("e", 10),
            row(.danger(1), 10, percent: 15)
        ]

        // Égalité parfaite : les 4 premiers de l'anneau ; la zone de danger (dernière) prend la place du 4e.
        XCTAssertEqual(result(rows).legendIndices, [0, 1, 2, 5])
    }

    func testTheDangerLineOfARealResultIsInTheLegendFromOnePercentAndCitedOnceByVoiceOver() throws {
        // 4 lieux et Hors-zone plus longs ; une zone de danger de 4 h (1,7 %) : elle est dans la légende, pas dans la description.
        let segments: [TimeShareFixtures.Segment] = [
            (.trusted, "p1", 40), (.out, nil, 30), (.trusted, "p2", 30), (.out, nil, 30), (.trusted, "p3", 25),
            (.out, nil, 20), (.trusted, "p4", 20), (.out, nil, 20), (.danger, "r1", 4), (.out, nil, 20)
            (.trusted, "p4", 20), (.out, nil, 20), (.danger, "r1", 4), (.out, nil, 20)
        ]
        let gym = TimeSharePlace(id: "p4", name: "Gym", type: .trusted, category: nil)
        let places = [Scn.home, Scn.sport, Scn.work, gym, Scn.risk1]
        let real = try XCTUnwrap(Scn.ready(Scn.compute(Scn.timeline(segments), places: places, days: 30)))

        let kinds = real.legendIndices.map { real.rows[$0].kind }
        let spoken = TimeShareTexts.spokenSummary(real, locale: Locale(identifier: "en_US"))

        XCTAssertEqual(kinds.count, 4)
        XCTAssertTrue(kinds.contains(.danger(1)))
        XCTAssertFalse(spoken.contains(TimeShareTexts.dangerZones(1)), spoken)
    }

    func testAUnderOnePercentDangerOfARealResultHasNoLineAndIsSpokenOnce() throws {
        // La même journée avec une zone de danger de 1 h (0,4 %) : pas de ligne, mais la description la cite une fois (« < 1 % »).
        let segments: [TimeShareFixtures.Segment] = [
            (.trusted, "p1", 40), (.out, nil, 30), (.trusted, "p2", 30), (.out, nil, 30), (.trusted, "p3", 25),
            (.out, nil, 20), (.trusted, "p4", 20), (.out, nil, 20), (.danger, "r1", 1), (.out, nil, 20)
            (.trusted, "p4", 20), (.out, nil, 20), (.danger, "r1", 1), (.out, nil, 20)
        ]
        let gym = TimeSharePlace(id: "p4", name: "Gym", type: .trusted, category: nil)
        let places = [Scn.home, Scn.sport, Scn.work, gym, Scn.risk1]
        let real = try XCTUnwrap(Scn.ready(Scn.compute(Scn.timeline(segments), places: places, days: 30)))

        let kinds = real.legendIndices.map { real.rows[$0].kind }
        let spoken = TimeShareTexts.spokenSummary(real, locale: Locale(identifier: "en_US"))

        XCTAssertFalse(kinds.contains(.danger(1)))
        XCTAssertEqual(spoken.components(separatedBy: TimeShareTexts.dangerZones(1)).count - 1, 1, spoken)
    }

    // MARK: - Pour le lecteur d'écran

    func testTheSpokenSummaryNamesTheCategoriesAndNeverTheEmoji() throws {
        let segments: [TimeShareFixtures.Segment] = [
            (.trusted, "p1", 30), (.out, nil, 10), (.danger, "r1", 10), (.out, nil, 10)
        ]
        let facts = Scn.timeline(segments)
        let result = try XCTUnwrap(Scn.ready(Scn.compute(facts, places: [Scn.home, Scn.risk1])))

        let spoken = TimeShareTexts.spokenSummary(result, locale: Locale(identifier: "en_US"))

        XCTAssertTrue(spoken.contains("Maison 50%"), spoken)
        // La zone de danger n'est citée qu'une fois : par sa propre ligne (un bouton), pas par cette description.
        XCTAssertFalse(spoken.contains(TimeShareTexts.name(of: result.rows[1])), spoken)
        XCTAssertFalse(spoken.contains("17%"), spoken)
        XCTAssertTrue(spoken.contains("33%"), spoken)
        for emoji in PlaceCategory.allCases.map({ $0.emoji }) {
            XCTAssertFalse(spoken.contains(emoji), "l'emoji est décoratif : le nom de la catégorie est lu")
        }
    }

    func testTheSpokenDangerRowSaysItCanBeOpened() throws {
        let facts = Scn.timeline([(.trusted, "p1", 30), (.danger, "r1", 10), (.out, nil, 10)])
        let result = try XCTUnwrap(Scn.ready(Scn.compute(facts, places: [Scn.home, Scn.risk1])))
        let first = result.rows.first { row in
            if case .danger = row.kind {
                return true
            }
            return false
        }
        let danger = try XCTUnwrap(first)

        let spoken = TimeShareTexts.spokenDangerRow(danger)

        XCTAssertTrue(spoken.localizedCaseInsensitiveContains("danger"))
    }

    // MARK: - L'anneau et la palette

    func testTheRingStartsAtNoonAndItsSlicesFollowTheLegendOrderWithoutGapOrOverlap() throws {
        let segments: [TimeShareFixtures.Segment] = [
            (.trusted, "p1", 25), (.out, nil, 19), (.trusted, "p1", 25), (.out, nil, 19), (.danger, "r1", 10),
            (.out, nil, 19), (.trusted, "p1", 25), (.out, nil, 19), (.trusted, "p2", 14), (.trusted, "p1", 25)
        ]
        let places = [Scn.home, Scn.sport, Scn.risk1]
        let result = try XCTUnwrap(Scn.ready(Scn.compute(Scn.timeline(segments), places: places, days: 14)))

        let slices = TimeShareDonutLayout.slices(for: result)

        XCTAssertEqual(slices.count, result.rows.count)
        XCTAssertEqual(slices.first?.start, 0)
        XCTAssertEqual(slices.last?.end, 1)
        for pair in zip(slices, slices.dropFirst()) {
            XCTAssertEqual(pair.0.end, pair.1.start, accuracy: 1e-12)
        }
        XCTAssertEqual(slices.map { $0.style }, TimeShareDonutLayout.styles(for: result.rows))
        XCTAssertEqual(slices.first?.style, .place(rank: 0))
        XCTAssertEqual(slices.last?.style, .elsewhere)
    }

    func testOnlyTheDangerSliceIsHatched() {
        XCTAssertTrue(TimeShareSliceStyle.danger.isHatched)
        let others: [TimeShareSliceStyle] = [.place(rank: 0), .otherPlaces, .elsewhere]
        for style in others {
            XCTAssertFalse(style.isHatched)
        }
    }

    func testEveryChartColourHoldsThreeToOneOnTheWhiteCard() {
        for token in ChartTokens.all {
            XCTAssertGreaterThanOrEqual(token.value.contrastRatio(with: colorBgSurface), 3, token.name)
        }
    }

    func testOnlyTheDangerZoneUsesAnAlertColour() {
        for token in ChartTokens.all where token.name != "colorChartDanger" {
            let value = token.value
            let reddish = value.red > 150 && value.green < 130
            let statusYellowGreen = value == colorStatusSafe
            XCTAssertFalse(reddish, "\(token.name) : aucune teinte rouge ou orange hors de la zone de danger")
            XCTAssertFalse(statusYellowGreen, token.name)
        }
        XCTAssertEqual(colorChartDanger, colorStatusDanger, "exactement le rouge de la bulle de statut danger")
    }

    func testTheTrustedPlacesAreShadesOfMiffGreenAndBlueAndElsewhereIsTheGreyOfTheMockUp() {
        // Vert, bleu, vert, bleu, vert : la teinte dominante alterne.
        for (index, token) in ChartTokens.places.enumerated() {
            let green = token.green > token.blue && token.red > 0x40
            let blue = token.blue > token.red && token.blue > token.green
            XCTAssertTrue(index.isMultiple(of: 2) ? green : blue, token.hexString)
        }
        XCTAssertEqual(colorChart2, colorActionPrimary, "le bleu de Miff")
        XCTAssertEqual(colorChartElsewhere.hexString, "#D4D7DF", "le gris relevé sur la maquette du PM")
        XCTAssertEqual(colorChartDanger.hexString, colorStatusDanger.hexString)
    }

    func testThePlaceColoursAreDistinctAndTheListKeepsOneSpareColour() {
        XCTAssertEqual(Set(ChartTokens.places.map { $0.hexString }).count, ChartTokens.places.count)
        XCTAssertFalse(ChartTokens.places.contains(colorChartReserve), "la couleur de réserve reste libre")
        XCTAssertEqual(ChartTokens.places.count, TimeShareLimits.maxNamedPlaces)
    }
}
