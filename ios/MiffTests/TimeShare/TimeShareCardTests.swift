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
        let outcome = TimeShareOutcome.notEnough(measuredMillis: 0)

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
        let outcome = TimeShareOutcome.notEnough(measuredMillis: 0)
        func state(_ conditions: TimeShareConditions) -> TimeShareCardState {
            TimeShareCardState.make(places: [Scn.home], readFailed: false, outcome: outcome, conditions: conditions)
        }

        XCTAssertEqual(state(TimeShareConditions()), .notEnough)
        XCTAssertEqual(state(TimeShareConditions(locationInsufficient: true)), .locationInsufficient)
        XCTAssertEqual(state(TimeShareConditions(sharingStopped: true, locationInsufficient: true)), .sharingStopped)
        XCTAssertEqual(
            state(TimeShareConditions(travelActive: true, sharingStopped: true, locationInsufficient: true)),
            .travel
        )
    }

    func testTheOverTwentyZonesNoteDoesNotChangeTheState() {
        let outcome = TimeShareOutcome.notEnough(measuredMillis: 0)

        let state = TimeShareCardState.make(
            places: [Scn.home], readFailed: false, outcome: outcome,
            conditions: TimeShareConditions(overWatchedLimit: true)
        )

        XCTAssertEqual(state, .notEnough)
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
            "profile_timeshare_a11y_danger_row", "profile_timeshare_a11y_list", "profile_timeshare_elsewhere",
            "profile_timeshare_percent_tiny", "profile_timeshare_detail_close", "profile_timeshare_privacy",
            "profile_timeshare_not_enough", "profile_timeshare_paused", "profile_timeshare_travel",
            "profile_timeshare_sharing_stopped", "profile_timeshare_sharing_stopped_action",
            "profile_timeshare_location_needed", "profile_timeshare_over_limit", "profile_timeshare_error",
            "profile_timeshare_other_places", "profile_timeshare_danger_zones", "settings_privacy_time_title",
            "settings_privacy_time_body", "settings_privacy_time_erase", "settings_privacy_time_confirm_title",
            "settings_privacy_time_confirm_body", "settings_privacy_time_confirm_action", "settings_privacy_time_done"
        ]
        for window in TimeShareLimits.windowsDays {
            for base in [
                "profile_timeshare_subtitle", "profile_timeshare_window", "profile_timeshare_summary_place",
                "profile_timeshare_summary_elsewhere", "profile_timeshare_summary_generic"
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

    func testTheFrozenWordingOfTheNotEnoughDataStateIsExactInBothLanguages() throws {
        XCTAssertEqual(
            L10n.string("profile_timeshare_not_enough", bundle: try bundle("fr")),
            "Ton graphique arrive dans quelques jours, nous analysons ta situation"
        )
        XCTAssertEqual(
            L10n.string("profile_timeshare_not_enough", bundle: try bundle("en")),
            "Your chart will be ready in a few days, we are looking into your situation"
        )
    }

    func testThePrivacyLineAndTheErasingConfirmationAreExactInFrench() throws {
        let french = try bundle("fr")

        XCTAssertEqual(
            L10n.string("profile_timeshare_privacy", bundle: french),
            "Ces données restent sur ton téléphone. Personne d'autre ne peut les voir."
        )
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
            let key = count > 1 ? "ios_timeshare_danger_zones_many" : "profile_timeshare_danger_zones"
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

    func testTheSentencesAreCalmAndNeverNameTheDangerZone() throws {
        for language in ["fr", "en"] {
            let bundle = try bundle(language)
            for window in TimeShareLimits.windowsDays {
                for kind in ["place", "elsewhere", "generic"] {
                    let key = "profile_timeshare_summary_\(kind)_\(window)"
                    let text = L10n.string(key, bundle: bundle)
                    XCTAssertFalse(text.localizedCaseInsensitiveContains("danger"), text)
                    XCTAssertFalse(text.contains("!"), "aucun point d'exclamation : \(text)")
                    XCTAssertFalse(text.contains("🎉"), text)
                    XCTAssertFalse(text.contains("✓"), text)
                }
            }
        }
    }

    func testTheSentenceHasOneTextPerWindowWithItsNumber() {
        for window in TimeShareLimits.windowsDays {
            XCTAssertTrue(TimeShareTexts.subtitle(window: window).contains("\(window)"))
            XCTAssertTrue(TimeShareTexts.windowLabel(window).contains("\(window)"))
            XCTAssertTrue(TimeShareTexts.synthesis(.generic, window: window).contains("\(window)"))
        }
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
        XCTAssertTrue(spoken.contains("\(TimeShareTexts.name(of: result.rows[1])) 17%"), spoken)
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
        XCTAssertNotEqual(colorChartDanger, colorStatusDanger, "légèrement assombrie pour tenir 3:1")
    }

    func testThePlaceColoursAreDistinctAndTheListKeepsOneSpareColour() {
        XCTAssertEqual(Set(ChartTokens.places.map { $0.hexString }).count, ChartTokens.places.count)
        XCTAssertFalse(ChartTokens.places.contains(colorChartReserve), "la couleur de réserve reste libre")
        XCTAssertEqual(ChartTokens.places.count, TimeShareLimits.maxNamedPlaces)
    }
}
