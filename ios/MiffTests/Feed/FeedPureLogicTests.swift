// Quelques lignes citent des cas longs (numéros invalides, noms d'arguments) : leur longueur dépasse la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

// Les heures, « Localiser », les liens confiés au système, l'en-tête qui se masque et le mouvement de la bulle : de la logique pure
// (ticket FEED : D1, D40, D41, critères L1 à L4, M1, H3, B7).
final class FeedPureLogicTests: XCTestCase {
    // MARK: - Les heures

    func testAgeIsMinutesThenHoursThenYesterdayThenDate() {
        func age(_ ago: Int) -> Age {
            Age.of(thenMillis: feedNow - ago, nowMillis: feedNow, zone: feedZone)
        }

        XCTAssertEqual(age(0), .minutes(1), "jamais « 0 min »")
        XCTAssertEqual(age(feedMinutes(12)), .minutes(12))
        XCTAssertEqual(age(feedMinutes(59)), .minutes(59))
        XCTAssertEqual(age(feedHours(1)), .hours(1))
        XCTAssertEqual(age(feedHours(23)), .hours(23))
        XCTAssertEqual(age(-feedMinutes(5)), .minutes(1), "une heure dans le futur compte comme maintenant")
        XCTAssertEqual(age(feedHours(24)), .yesterday(atMillis: feedNow - feedHours(24)))
        XCTAssertEqual(age(feedHours(60)), .date(atMillis: feedNow - feedHours(60)))
    }

    func testTheDayIsTheLocalDayNotTheNumberOfHours() {
        // 00 h 30 le 7 novembre à Paris : 23 h 30 la veille est « hier » même à une heure de là.
        let justAfterMidnight = feedNow - feedMinutes(15 * 60 + 30)
        let beforeMidnight = justAfterMidnight - feedMinutes(60)

        XCTAssertEqual(Moment.dayOf(atMillis: justAfterMidnight, nowMillis: justAfterMidnight, zone: feedZone), .today)
        XCTAssertEqual(Moment.dayOf(atMillis: beforeMidnight, nowMillis: justAfterMidnight, zone: feedZone), .yesterday)
        XCTAssertEqual(Moment.dayOf(atMillis: feedNow - 3 * FeedMillis.day, nowMillis: feedNow, zone: feedZone), .other)
    }

    // MARK: - Localiser

    func testARecentPositionKeepsItsCoordinatesAndSaysHowOldItIs() {
        let lookup = PositionLookup.found(lat: 48.8566, lng: 2.3522, measuredAtMillis: feedNow - feedMinutes(20))

        let result = Locate.interpret(lookup, nowMillis: feedNow, zone: feedZone)

        guard case .recent(let fix, let measuredAt, let age) = result else {
            return XCTFail("une position de 20 min est récente")
        }
        XCTAssertEqual(fix, PositionFix(lat: 48.8566, lng: 2.3522))
        XCTAssertEqual(measuredAt.day, .today)
        XCTAssertEqual(age, .minutes(20))
    }

    func testAPositionOlderThanADayLosesItsCoordinatesHere() {
        let day = FeedMillis.day
        let atTheLimit = PositionLookup.found(lat: 1, lng: 2, measuredAtMillis: feedNow - day)
        let past = PositionLookup.found(lat: 1, lng: 2, measuredAtMillis: feedNow - day - 1)

        guard case .recent = Locate.interpret(atTheLimit, nowMillis: feedNow, zone: feedZone) else {
            return XCTFail("exactement 24 h reste affichée (strictement plus : masquée)")
        }
        guard case .old(let moment) = Locate.interpret(past, nowMillis: feedNow, zone: feedZone) else {
            return XCTFail("plus de 24 h : la date seulement")
        }
        XCTAssertEqual(moment.day, .yesterday)
    }

    func testTheCalmAnswers() {
        XCTAssertEqual(Locate.interpret(.notFound, nowMillis: feedNow, zone: feedZone), .notFound)
        XCTAssertEqual(Locate.interpret(.denied, nowMillis: feedNow, zone: feedZone), .denied)
        XCTAssertEqual(Locate.interpret(.offline, nowMillis: feedNow, zone: feedZone), .offline)
        XCTAssertEqual(Locate.interpret(.failed, nowMillis: feedNow, zone: feedZone), .unavailable)
    }

    func testNoConnectionIsOnlyForARealAbsenceOfNetwork() {
        XCTAssertEqual(Locate.reconcile(.offline, networkOnline: true), .failed)
        XCTAssertEqual(Locate.reconcile(.failed, networkOnline: false), .offline)
        XCTAssertEqual(Locate.reconcile(.offline, networkOnline: false), .offline)
        XCTAssertEqual(Locate.reconcile(.denied, networkOnline: false), .denied)
        XCTAssertEqual(Locate.reconcile(.notFound, networkOnline: true), .notFound)
    }

    func testTheMapAddressHoldsTheCoordinatesAloneWithADotWhateverTheLanguage() {
        let url = Locate.mapsUrl(PositionFix(lat: 48.856614, lng: 2.352222))

        XCTAssertEqual(url, "https://maps.apple.com/?ll=48.85661,2.35222&q=48.85661,2.35222")
        XCTAssertFalse(url.contains("name"), "aucun nom ni étiquette")
    }

    func testAPositionNeverShowsInADescription() {
        let fix = PositionFix(lat: 48.8566, lng: 2.3522)
        let lookup = PositionLookup.found(lat: 48.8566, lng: 2.3522, measuredAtMillis: feedNow)
        let result = LocateResult.recent(fix: fix, measuredAt: Moment(atMillis: feedNow, day: .today), age: .minutes(1))
        let shown = LocateUiState.shown(uid: "uid-a", firstName: "Thomas", result: result)

        for text in ["\(fix)", "\(lookup)", "\(result)", "\(shown)", String(describing: shown)] {
            XCTAssertFalse(text.contains("48.85"), text)
            XCTAssertFalse(text.contains("Thomas"), text)
        }
    }

    // MARK: - Les liens confiés au système

    func testOnlyAnE164NumberReachesALink() {
        XCTAssertEqual(FeedLinks.message(phone: "+33612345678")?.absoluteString, "sms:+33612345678")
        XCTAssertEqual(FeedLinks.call(phone: "+33612345678")?.absoluteString, "tel:+33612345678")
        for bad in ["0612345678", "+33 6 12 34 56 78", "+33612345678;rm", "tel:+33612345678", "", "+0123456789", "+33612345678\n"] {
            XCTAssertNil(FeedLinks.message(phone: bad), bad)
            XCTAssertNil(FeedLinks.call(phone: bad), bad)
        }
    }

    // MARK: - L'en-tête qui se masque (D41)

    func testTheHeaderFollowsTheFingerInBothDirectionsAndComesBackAtOnce() {
        var header = CollapsingHeaderState()
        header.measured(100)

        header.follow(delta: -30)
        XCTAssertEqual(header.offset, -30)
        header.follow(delta: -200)
        XCTAssertEqual(header.offset, -100, "jamais plus caché que sa hauteur")
        header.follow(delta: 25)
        XCTAssertEqual(header.offset, -75, "il revient dès qu'on remonte, sans attendre le haut de la liste")
        header.follow(delta: 500)
        XCTAssertEqual(header.offset, 0)
    }

    func testNothingHidesBeforeTheHeaderIsMeasured() {
        var header = CollapsingHeaderState()

        header.follow(delta: -50)

        XCTAssertEqual(header.offset, 0)
    }

    func testNearTheTopTheHeaderIsNeverMoreHiddenThanTheListScrolled() {
        var header = CollapsingHeaderState()
        header.measured(100)
        header.follow(delta: -60)

        header.limit(scrolledFromTop: 20)

        XCTAssertEqual(header.offset, -20, "sinon une bande vide resterait au-dessus de la première carte")
        header.limit(scrolledFromTop: nil)
        XCTAssertEqual(header.offset, -20)
        header.limit(scrolledFromTop: -40)
        XCTAssertEqual(header.offset, 0, "tirer vers le bas en haut de liste : l'en-tête est là")
    }

    func testALockedHeaderStaysShownAndComesBackWhenItGetsLocked() {
        var header = CollapsingHeaderState()
        header.measured(100)
        header.follow(delta: -80)

        header.lock(true)
        header.follow(delta: -80)

        XCTAssertEqual(header.offset, 0)
        header.lock(false)
        header.follow(delta: -40)
        XCTAssertEqual(header.offset, -40)
    }

    func testTheReasonsToStayVisible() {
        func stay(_ list: Bool = true, _ offline: Bool = false, _ assistive: Bool = false, _ open: Bool = false) -> Bool {
            CollapsingHeaderState.mustStayVisible(isList: list, offline: offline, assistiveTechnology: assistive, selectorOpen: open)
        }

        XCTAssertFalse(stay())
        XCTAssertTrue(stay(false), "chargement, erreur, état vide")
        XCTAssertTrue(stay(true, true), "hors connexion")
        XCTAssertTrue(stay(true, false, true), "VoiceOver ou Contrôle de sélection")
        XCTAssertTrue(stay(true, false, false, true), "sélecteur ouvert")
    }

    @MainActor
    func testTheHeaderModelTurnsTheMovementOfTheContentIntoAMovementOfTheHeader() {
        let model = FeedHeaderModel()
        model.measured(100)

        model.contentMoved(to: 150)
        XCTAssertEqual(model.state.offset, 0, "la première position est le repos")
        model.contentMoved(to: 120)
        XCTAssertEqual(model.state.offset, -30)
        model.contentMoved(to: 10)
        XCTAssertEqual(model.state.offset, -100)
        model.contentMoved(to: 40)
        XCTAssertEqual(model.state.offset, -70, "remonter de 30 points fait redescendre l'en-tête de 30")
        model.contentMoved(to: 190)
        XCTAssertEqual(model.state.offset, 0, "tirer en haut de liste : l'en-tête est entièrement là")
    }

    // MARK: - Le mouvement de la bulle (D40 et réglages du 2026-11-07)

    func testTheMotionIsStillWhenAnimationsAreReducedOrTheScreenIsNotOnView() {
        XCTAssertTrue(BubbleMotion.enabled(reduceMotion: false, active: true))
        XCTAssertFalse(BubbleMotion.enabled(reduceMotion: true, active: true))
        XCTAssertFalse(BubbleMotion.enabled(reduceMotion: false, active: false))
    }

    func testTheMotionStaysWithinItsNamedAmplitudesAndIsSlow() {
        var largestStretch = 0.0
        var largestSquash = 1.0
        for step in 0..<200 {
            let phase = Double(step) / 200
            largestStretch = max(largestStretch, BubbleMotion.scaleX(phase), BubbleMotion.scaleY(phase))
            largestSquash = min(largestSquash, BubbleMotion.scaleX(phase), BubbleMotion.scaleY(phase))
            XCTAssertLessThanOrEqual(abs(BubbleMotion.drift(phase)), BubbleMotion.driftPoints + 1e-9)
        }

        XCTAssertEqual(largestStretch, 1 + BubbleMotion.scaleAmplitude, accuracy: 0.001)
        XCTAssertEqual(largestSquash, 1 - BubbleMotion.scaleAmplitude, accuracy: 0.001)
        XCTAssertEqual(BubbleMotion.scaleAmplitude, 0.05, "amplitude initiale demandée par le PM")
        XCTAssertTrue((5.0...8.0).contains(BubbleMotion.blobPeriodSeconds), "un cycle de 5 à 8 s")
        XCTAssertTrue((5.0...8.0).contains(BubbleMotion.starPeriodSeconds))
    }

    func testTheTwoAxesAreOutOfPhase() {
        // Quand la tache s'allonge en largeur, elle s'écrase en hauteur.
        XCTAssertGreaterThan(BubbleMotion.scaleX(0.25), 1)
        XCTAssertLessThan(BubbleMotion.scaleY(0.25), 1)
        XCTAssertEqual(BubbleMotion.scaleX(0), 1, accuracy: 1e-9)
    }

    func testThePhaseIsACycleFractionAndTheMotionIsTheSameInEveryColour() {
        XCTAssertEqual(BubbleMotion.phase(at: 6.5, period: 6.5), 0, accuracy: 1e-9)
        XCTAssertEqual(BubbleMotion.phase(at: 9.75, period: 6.5), 0.5, accuracy: 1e-9)
        XCTAssertEqual(BubbleMotion.phase(at: 1, period: 0), 0)
    }
}
