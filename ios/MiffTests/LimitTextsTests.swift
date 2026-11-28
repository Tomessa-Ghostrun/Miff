import XCTest
@testable import Miff

/// Les limites écrites dans les textes (lieux gratuits, alertes par jour, demandes en attente, Miffs par lieu) sont des VALEURS INSÉRÉES
/// venues des constantes de l'app, et les textes qui en parlent s'accordent (« 1 lieu », « 3 lieux »). Ce test lit le catalogue COMPILÉ de
/// chaque langue : il dit aussi si un pluriel à deux valeurs (« %1$lld lieu sur %2$lld ») est bien résolu par le système. Aucun plafond n'est
/// écrit en dur ici : si une limite change, les textes et ce test la suivent.
final class LimitTextsTests: XCTestCase {
    private func bundle(_ language: String) throws -> Bundle {
        let path = try XCTUnwrap(Bundle.main.path(forResource: language, ofType: "lproj"), language)
        return try XCTUnwrap(Bundle(path: path), language)
    }

    func testThePlaceCounterTakesTheFreeLimitAsASecondValueAndAgreesInNumber() throws {
        let french = try bundle("fr")
        let english = try bundle("en")
        let counter = "place_list_counter_free"
        let free = PlaceLimits.freePlaces

        XCTAssertEqual(
            L10n.plural(counter, count: 1, bundle: french, free), "1 lieu sur \(free) avec la formule gratuite"
        )
        XCTAssertEqual(
            L10n.plural(counter, count: free, bundle: french, free),
            "\(free) lieux sur \(free) avec la formule gratuite"
        )
        XCTAssertEqual(
            L10n.plural(counter, count: 2, bundle: english, free), "2 of \(free) places on the free plan"
        )
    }

    func testTheDailyAlertLimitIsTheOneOfTheRules() throws {
        let french = try bundle("fr")
        let english = try bundle("en")
        let limit = DangerAlertRules.maxStartsPerDay

        for key in ["alert_quota_title", "alert_banner_quota"] {
            XCTAssertTrue(L10n.plural(key, count: limit, bundle: french).contains("\(limit) alertes par jour"), key)
            XCTAssertTrue(L10n.plural(key, count: limit, bundle: english).contains("\(limit) alerts a day"), key)
            XCTAssertTrue(L10n.plural(key, count: 1, bundle: french).contains("1 alerte par jour"), key)
            XCTAssertTrue(L10n.plural(key, count: 1, bundle: english).contains("1 alert a day"), key)
        }
    }

    func testThePendingRequestLimitAgreesInNumber() throws {
        let french = try bundle("fr")
        let english = try bundle("en")
        let limit = PlaceRequestLimits.perMember

        let many = L10n.plural("place_request_limit_member", count: limit, bundle: french)
        XCTAssertTrue(many.contains("\(limit) demandes en attente"), many)
        XCTAssertTrue(many.contains("quand elles auront été traitées"), many)
        let one = L10n.plural("place_request_limit_member", count: 1, bundle: french)
        XCTAssertTrue(one.contains("1 demande en attente") && one.contains("quand elle aura été traitée"), one)
        let oneEnglish = L10n.plural("place_request_limit_member", count: 1, bundle: english)
        XCTAssertTrue(oneEnglish.contains("1 pending request"), oneEnglish)
        XCTAssertTrue(oneEnglish.contains("once it's been handled"), oneEnglish)
    }

    func testTheMaximumOfMiffsPerPlaceIsTheOneOfTheRules() throws {
        for language in ["fr", "en"] {
            let format = L10n.string("place_miffs_max_reached", bundle: try bundle(language))
            let text = String(format: format, PlaceLimits.maxMiffs)

            XCTAssertTrue(text.contains("\(PlaceLimits.maxMiffs) Miffs"), "\(language) : \(text)")
        }
    }

    func testTheNotificationHelpHasItsOwnIphoneKeyForTheSecondStep() throws {
        XCTAssertEqual(L10n.string("ios_notif_help_step_2", bundle: try bundle("fr")), "Choisis « Notifications »")
        XCTAssertEqual(L10n.string("ios_notif_help_step_2", bundle: try bundle("en")), "Choose “Notifications”")
    }
}
