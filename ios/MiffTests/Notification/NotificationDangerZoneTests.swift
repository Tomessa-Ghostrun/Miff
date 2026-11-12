import XCTest
@testable import Miff

/// N6 « Tu es dans une zone de danger de ta Miff » (ticket §20.7, vecteurs V1 à V10) : la décision pure, la détection par le moteur de
/// présence (une seule N6 par entrée EFFECTIVE) et la notification locale immédiate.
final class NotificationDangerZoneDecisionTests: XCTestCase {
    private let midnight = 1_794_096_000_000
    private let hour = 3_600_000

    private var utc: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC") ?? .current
        return calendar
    }

    private func input(
        atHour hour: Int = 14,
        configure: (inout DangerZoneInput) -> Void = { _ in }
    ) -> DangerZoneInput {
        var value = DangerZoneInput(
            nowMillis: midnight + hour * 3_600_000,
            sessionUid: "me",
            miffId: "miff-1",
            placeId: "p-risk",
            enteredAtMillis: midnight + hour * 3_600_000 - 5_000
        )
        value.calendar = utc
        configure(&value)
        return value
    }

    // MARK: - V1, V9

    func testV1EnteringADangerZoneShowsOneNotificationAtOnce() {
        XCTAssertEqual(NotificationDangerZone.decide(input()), .show(quiet: false))
    }

    func testV9TheSwitchTheGeneralSwitchTheAuthorizationAndTheSessionAreRespected() {
        let n6Off = NotificationPreferences().setting(.n6, to: false)
        let generalOff = NotificationPreferences().settingGeneral(false)

        XCTAssertEqual(NotificationDangerZone.decide(input { $0.preferences = n6Off }), .skip)
        XCTAssertEqual(NotificationDangerZone.decide(input { $0.preferences = generalOff }), .skip)
        for authorization in [NotificationAuthorization.denied, .notDetermined] {
            XCTAssertEqual(NotificationDangerZone.decide(input { $0.authorization = authorization }), .skip)
        }
        XCTAssertEqual(NotificationDangerZone.decide(input { $0.sessionUid = nil }), .skip)
    }

    func testDuringMyTripNoN6EvenIfAPositionInADangerZoneIsMeasured() {
        let tripEnd = midnight + 24 * hour
        let travelling = input { $0.myTravelUntilMillis = tripEnd }
        let ended = input(atHour: 14) { $0.myTravelUntilMillis = midnight + 14 * hour - 1 }

        XCTAssertEqual(NotificationDangerZone.decide(travelling), .skip, "Q7 : en voyage, aucune N6")
        XCTAssertEqual(NotificationDangerZone.decide(ended), .show(quiet: false), "le voyage est fini : N6 reprend")
        XCTAssertEqual(NotificationDangerZone.decide(input()), .show(quiet: false))
    }

    func testTurningN1OffDoesNotTurnN6Off() {
        let value = input { $0.preferences = NotificationPreferences().setting(.n1, to: false) }

        XCTAssertEqual(NotificationDangerZone.decide(value), .show(quiet: false))
    }

    // MARK: - V5, V3 : jamais deux fois

    func testV5AnExitAndARapidReentryGiveOnlyOneNotification() {
        let delay = NotificationDangerZone.rearmDelayMillis
        let almostAnHour = input { $0.lastForThisPlaceMillis = $0.nowMillis - delay + 1 }
        let exactlyAnHour = input { $0.lastForThisPlaceMillis = $0.nowMillis - delay }

        XCTAssertEqual(NotificationDangerZone.decide(almostAnHour), .skip)
        XCTAssertEqual(NotificationDangerZone.decide(exactlyAnHour), .show(quiet: false), "à 60 min pile : réarmée")
        XCTAssertEqual(NotificationDangerZone.rearmDelayMillis, 60 * 60 * 1000)
    }

    func testV3TheSameEntryIsNeverNotifiedTwice() {
        let key = NotificationDangerZone.key(
            miffId: "miff-1", placeId: "p-risk", enteredAtMillis: midnight + 14 * hour - 5_000
        )

        XCTAssertEqual(NotificationDangerZone.decide(input { $0.notifiedKeys = [key] }), .skip)
        XCTAssertEqual(key, "n6|miff-1|p-risk|\(midnight + 14 * hour - 5_000)")
    }

    func testAnotherPlaceOfTheSameMiffHasItsOwnRearmDelay() {
        XCTAssertNotEqual(
            NotificationDangerZone.placeKey(miffId: "miff-1", placeId: "a"),
            NotificationDangerZone.placeKey(miffId: "miff-1", placeId: "b")
        )
        XCTAssertNotEqual(
            NotificationDangerZone.placeKey(miffId: "miff-1", placeId: "a"),
            NotificationDangerZone.placeKey(miffId: "miff-2", placeId: "a")
        )
    }

    // MARK: - V6, V7, V8

    func testV6AtNightTheNotificationIsQuietAndTheDayIsNot() {
        XCTAssertEqual(NotificationDangerZone.decide(input(atHour: 2)), .show(quiet: true))
        XCTAssertEqual(NotificationDangerZone.decide(input(atHour: 22)), .show(quiet: true))
        XCTAssertEqual(NotificationDangerZone.decide(input(atHour: 7)), .show(quiet: false))
        let nightCalmOff = input(atHour: 2) { $0.preferences = $0.preferences.settingNightCalm(false) }
        XCTAssertEqual(NotificationDangerZone.decide(nightCalmOff), .show(quiet: false))
    }

    func testV7TheFourthEntryOfTheDayIsASilentCenterEntry() {
        XCTAssertEqual(NotificationDangerZone.decide(input { $0.shownToday = 2 }), .show(quiet: false))
        XCTAssertEqual(NotificationDangerZone.decide(input { $0.shownToday = 3 }), .silent)
        XCTAssertEqual(NotificationDangerZone.maxPerDay, 3)
    }

    func testV8InTheForegroundNoSystemNotificationJustASilentEntry() {
        XCTAssertEqual(NotificationDangerZone.decide(input { $0.isForeground = true }), .silent)
    }

    // MARK: - V2, V3, V4 : seul le moteur de présence décide d'une entrée EFFECTIVE

    private let trusted = ZoneRef(key: "zone-t", placeId: "p-trusted", type: .trusted)
    private let risk = ZoneRef(key: "zone-r", placeId: "p-risk", type: .risk)

    func testV1FromOutsideEnteringTheDangerZoneGivesAnInRiskStatus() {
        let change = PresenceMachine.onTransition(
            ZoneTransition(kind: .enter, zoneKey: risk.key, atMillis: 200),
            zones: [trusted, risk],
            insideAfter: [risk.key: 200],
            last: DerivedPresence(presence: .out, placeId: "p-trusted")
        )

        XCTAssertEqual(change?.fact.presence, .inRisk)
        XCTAssertEqual(change?.fact.placeId, "p-risk")
    }

    func testV2TheTrustedZoneWinsAndOnlyLeavingItWhileStayingInTheDangerZoneGivesN6() {
        let entering = PresenceMachine.onTransition(
            ZoneTransition(kind: .enter, zoneKey: risk.key, atMillis: 200),
            zones: [trusted, risk],
            insideAfter: [trusted.key: 100, risk.key: 200],
            last: DerivedPresence(presence: .inTrusted, placeId: "p-trusted")
        )
        XCTAssertNil(entering, "dans une zone de confiance : rien ne change, donc pas de N6")

        let leavingTrusted = PresenceMachine.onTransition(
            ZoneTransition(kind: .exit, zoneKey: trusted.key, atMillis: 300),
            zones: [trusted, risk],
            insideAfter: [risk.key: 200],
            last: DerivedPresence(presence: .inTrusted, placeId: "p-trusted")
        )
        XCTAssertEqual(leavingTrusted?.fact.presence, .inRisk, "sortir de la confiance en restant en danger : N6")
    }

    func testV3ARedetectionOfAnAlreadyInRiskStateGivesNothing() {
        let again = PresenceMachine.onTransition(
            ZoneTransition(kind: .enter, zoneKey: risk.key, atMillis: 400),
            zones: [trusted, risk],
            insideAfter: [risk.key: 400],
            last: DerivedPresence(presence: .inRisk, placeId: "p-risk")
        )

        XCTAssertNil(again)
    }

    func testV4TheMissedExitPathWritesInRiskOnlyWhenTheLastStatusWasNotInRisk() {
        let armed = PresenceMachine.onMissedExit(
            zoneKey: trusted.key,
            zones: [trusted, risk],
            insideAfter: [risk.key: 200],
            last: DerivedPresence(presence: .inTrusted, placeId: "p-trusted"),
            atMillis: 500
        )
        let alreadyInRisk = PresenceMachine.onMissedExit(
            zoneKey: trusted.key,
            zones: [trusted, risk],
            insideAfter: [risk.key: 200],
            last: DerivedPresence(presence: .inRisk, placeId: "p-risk"),
            atMillis: 500
        )

        XCTAssertEqual(armed?.fact.presence, .inRisk)
        XCTAssertNil(alreadyInRisk)
    }
}
