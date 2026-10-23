import XCTest
@testable import Miff

/// Mêmes cas que les tests Android (AnalyticsContractTest), issus de docs/contracts/analytics-events.md (version 1).
final class AnalyticsContractTests: XCTestCase {
    private func reason(_ event: String, _ params: [String: Any] = [:]) -> RefusalReason? {
        if case .refused(let reason, _) = AnalyticsContract.validate(event, params: params) {
            return reason
        }
        return nil
    }

    // 1. Un événement de la liste avec des paramètres corrects est accepté.
    func testAcceptsEveryListedEventWithCorrectParameters() {
        let accepted: [(String, [String: Any])] = [
            ("onboarding_cta_tapped", ["slide_index": 0]),
            ("onboarding_cta_tapped", ["slide_index": 2]),
            ("sign_up_completed", ["method": "apple"]),
            ("sign_in_completed", ["method": "google"]),
            ("miff_created", ["is_first_miff": true]),
            ("miff_joined", ["is_first_miff": false]),
            ("onboarding_completed", ["via": "joined"]),
            ("paywall_shown", ["trigger": "extra_miff"]),
            ("paywall_dismissed", ["trigger": "extra_trusted_place"]),
            ("analytics_diagnostic_test", [:])
        ]
        for (name, params) in accepted {
            XCTAssertEqual(AnalyticsContract.validate(name, params: params), .allowed, "\(name) \(params)")
        }
    }

    func testAllowListHasExactlyTheContractEvents() {
        XCTAssertEqual(AnalyticsContract.contractVersion, 1)
        let productEvents: Set<String> = [
            "onboarding_cta_tapped", "sign_up_completed", "sign_in_completed", "miff_created", "miff_joined",
            "onboarding_completed", "paywall_shown", "paywall_dismissed"
        ]
        // Les tests tournent en configuration Debug : l'événement du diagnostic fait partie de la liste.
        // (En Release, il en est absent : voir AnalyticsContract.allowedEvents.)
        XCTAssertEqual(Set(AnalyticsContract.allowedEvents.keys), productEvents.union(["analytics_diagnostic_test"]))
    }

    // 2. Un événement inconnu est refusé.
    func testRefusesUnknownEvents() {
        XCTAssertEqual(reason("screen_view_secret"), .unknownEvent)
        XCTAssertEqual(reason("diagnostic_test"), .unknownEvent)
    }

    // 3. Un paramètre inconnu est refusé.
    func testRefusesUnknownParameters() {
        XCTAssertEqual(reason("miff_created", ["user_name": "Alice"]), .unknownParam)
        XCTAssertEqual(reason("miff_joined", ["source": "x"]), .unknownParam)
    }

    // 4. Les noms qui évoquent une position sont refusés, même sur un événement connu.
    func testRefusesPositionLikeParameterNamesOnAKnownEvent() {
        let names = [
            "lat", "lng", "latitude", "longitude", "address", "place", "location", "userLatitude", "place_name",
            "user_location", "home_lng", "GPS", "nearby_distance", "ville"
        ]
        for name in names {
            XCTAssertEqual(reason("miff_created", [name: true]), .locationLikeName, name)
        }
    }

    func testPositionLikeNameIsRefusedEvenOnAnUnknownEvent() {
        XCTAssertEqual(reason("some_unknown_event", ["lat": 48]), .locationLikeName)
    }

    func testDoesNotMistakeHarmlessNamesForPositions() {
        let harmless = ["slide_index", "method", "is_first_miff", "via", "trigger", "template", "platform", "slate"]
        for name in harmless {
            XCTAssertFalse(AnalyticsContract.isForbiddenName(name), "'\(name)' ne doit pas être interdit")
        }
    }

    func testNoEventNorParameterOfTheAllowListHasAForbiddenName() {
        for spec in AnalyticsContract.allowedEvents.values {
            XCTAssertFalse(AnalyticsContract.isForbiddenName(spec.name), spec.name)
            for param in spec.params.keys {
                XCTAssertFalse(AnalyticsContract.isForbiddenName(param), "\(spec.name).\(param)")
            }
        }
    }

    // 5. Une valeur hors de la liste fermée est refusée.
    func testRefusesValuesOutsideTheClosedLists() {
        XCTAssertEqual(reason("sign_up_completed", ["method": "sms"]), .valueNotAllowed)
        XCTAssertEqual(reason("paywall_shown", ["trigger": "Grand Place"]), .valueNotAllowed)
        XCTAssertEqual(reason("onboarding_completed", ["via": "48.85,2.35"]), .valueNotAllowed)
        XCTAssertEqual(reason("onboarding_cta_tapped", ["slide_index": 3]), .valueNotAllowed)
        XCTAssertEqual(reason("sign_in_completed", ["method": String(repeating: "x", count: 101)]), .valueNotAllowed)
    }

    // 6. Un type incorrect est refusé.
    func testRefusesWrongTypes() {
        XCTAssertEqual(reason("onboarding_cta_tapped", ["slide_index": "1"]), .wrongType)
        XCTAssertEqual(reason("onboarding_cta_tapped", ["slide_index": 1.0]), .wrongType)
        XCTAssertEqual(reason("onboarding_cta_tapped", ["slide_index": true]), .wrongType)
        XCTAssertEqual(reason("miff_created", ["is_first_miff": "true"]), .wrongType)
        XCTAssertEqual(reason("sign_up_completed", ["method": 1]), .wrongType)
    }

    // MARK: - Événements typés

    func testTypedEventsAreAllAcceptedByTheAllowList() {
        let events: [AnalyticsEvent] = [
            .onboardingCtaTapped(slideIndex: 1),
            .signUpCompleted(method: .email),
            .signInCompleted(method: .apple),
            .miffCreated(isFirstMiff: true),
            .miffJoined(isFirstMiff: false),
            .onboardingCompleted(via: .created),
            .paywallShown(trigger: .extraMiff),
            .paywallDismissed(trigger: .extraTrustedPlace),
            .diagnosticTest
        ]
        for event in events {
            XCTAssertEqual(AnalyticsContract.validate(event.name, params: event.params), .allowed, event.name)
        }
    }

    func testAnOutOfRangeSlideIndexIsRefusedAtTheTypedEntryPoint() {
        let tracker = AnalyticsTracker(sink: RecordingSink(), isEnabled: { true })

        XCTAssertEqual(
            tracker.track(.onboardingCtaTapped(slideIndex: 7)),
            .refused(reason: .valueNotAllowed, detail: "slide_index")
        )
    }
}

/// Faux destination : mémorise les noms des événements remis au SDK.
final class RecordingSink: AnalyticsSink {
    private(set) var logged: [String] = []

    func log(eventName: String, params: [String: Any]) {
        logged.append(eventName)
    }
}
