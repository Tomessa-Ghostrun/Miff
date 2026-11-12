// Les assertions citent des cas entiers : certaines lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// MODE VOYAGE, notifications (VOY-4I ; ticket mode-voyage §9, vecteurs T16 à T20 et N7-1 à N7-3, le contrat avec Android :
/// `TravelNotificationPlannerTest.kt`) : N7 « Ton mode voyage est terminé » à 08:00 le lendemain du dernier jour, et les gardes du
/// planificateur (mon voyage coupe N1 à N4 ; un voyageur de la Miff est neutre). Convention du ticket : membres moi, X, Y qui partagent tous,
/// mode LARGE, Miff active, autorisation accordée. Le voyage des exemples finit dans la nuit du 10 (dernier jour choisi : le 9), à 00:00 local.
final class TravelNotificationPlannerTests: XCTestCase {
    private let me = "me"
    private let miff = "miff-1"
    private let hour = 3_600_000

    private func calendar(_ zone: String = "Europe/Paris") -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: zone) ?? .current
        return calendar
    }

    private func millis(_ year: Int, _ month: Int, _ day: Int, _ hour: Int, _ minute: Int, zone: String = "Europe/Paris") -> Int {
        let parts = DateComponents(year: year, month: month, day: day, hour: hour, minute: minute)
        let date = calendar(zone).date(from: parts) ?? Date(timeIntervalSince1970: 0)
        return Int((date.timeIntervalSince1970 * 1000).rounded())
    }

    /// Novembre 2026, heure de Paris.
    private func at(_ day: Int, _ hour: Int, _ minute: Int, ms: Int = 0) -> Int {
        millis(2026, 11, day, hour, minute) + ms
    }

    /// La fin d'un voyage dont le dernier jour est le 9 : 00:00 local du 10.
    private var tripEnd: Int {
        at(10, 0, 0)
    }

    // MARK: - Les membres

    private func member(_ uid: String, _ presence: String, _ at: Int, update: Int? = nil) -> PlanMember {
        PlanMember(uid: uid, sharing: true, presence: presence, presenceAtMillis: at, lastUpdateAtMillis: update)
    }

    private func out(_ uid: String, ref: Int) -> PlanMember {
        member(uid, "out", ref)
    }

    private func trusted(_ uid: String) -> PlanMember {
        member(uid, "in_trusted", at(8, 9, 0), update: at(8, 9, 0))
    }

    private func travelling(_ uid: String) -> PlanMember {
        member(uid, "travel", at(8, 9, 0), update: at(8, 9, 0))
    }

    private func input(
        _ now: Int,
        _ members: [PlanMember],
        travelUntil: Int? = nil,
        preferences: NotificationPreferences = NotificationPreferences(),
        authorization: NotificationAuthorization = .authorized,
        session: String? = "me",
        withMiff: Bool = true
    ) -> NotificationPlanInput {
        var value = NotificationPlanInput(
            nowMillis: now,
            snapshot: withMiff
                ? NotificationPlanSnapshot(miffId: miff, myUid: me, members: members, savedAtMillis: now)
                : nil,
            activeMiffId: withMiff ? miff : nil,
            sessionUid: session
        )
        value.calendar = calendar()
        value.horizonMillis = Int.max
        value.preferences = preferences
        value.authorization = authorization
        value.myTravelUntilMillis = travelUntil
        return value
    }

    /// Ce que le plan veut, par type : l'instant (programmés, rattrapages et silencieux confondus).
    private func plan(_ value: NotificationPlanInput) -> [String: Int] {
        let plan = NotificationPlanner.plan(value)
        let all = plan.scheduled + plan.catchUp + plan.silent
        return Dictionary(uniqueKeysWithValues: all.map { ($0.type.rawValue, $0.fireAtMillis) })
    }

    // MARK: - N7 : l'heure et la clé

    func testN7IsEightHoursAfterTheEndTheMorningAfterTheLastDayNeverInTheNight() {
        XCTAssertEqual(TravelEndNotice.dueAt(untilMillis: tripEnd, calendar: calendar()), at(10, 8, 0))
        XCTAssertEqual(at(10, 8, 0), tripEnd + 8 * hour)
    }

    func testN7OnTheDayTheSummerTimeEndsIsStillEightLocalNotEightHoursLater() {
        // Le 25 octobre 2026 dure 25 h à Paris (retour à l'heure d'hiver à 3 h) : la fin (00:00) est encore à l'heure d'été, 08:00 est à l'heure d'hiver.
        let end = millis(2026, 10, 25, 0, 0)
        let expected = millis(2026, 10, 25, 8, 0)

        XCTAssertEqual(TravelEndNotice.dueAt(untilMillis: end, calendar: calendar()), expected)
        XCTAssertEqual(expected - end, 9 * hour, "le 25 octobre a 25 h : 9 h d'écart, pas 8")
    }

    func testN7AZoneChangedDuringTheTripNeverComesBeforeTheEndOfTheTrip() {
        // 00:00 à Paris le 10 = 18:00 le 9 à New York : 08:00 le 9 serait AVANT la fin ; on prend le 08:00 suivant.
        let due = TravelEndNotice.dueAt(untilMillis: tripEnd, calendar: calendar("America/New_York"))

        XCTAssertGreaterThan(due, tripEnd)
        XCTAssertEqual(due, millis(2026, 11, 10, 8, 0, zone: "America/New_York"))
    }

    func testN7WhenEightLocalIsTheEndItselfItLeavesAtOnceNotADayLate() {
        // 00:00 à Paris le 10 = 08:00 à Tokyo le 10.
        XCTAssertEqual(millis(2026, 11, 10, 8, 0, zone: "Asia/Tokyo"), tripEnd)
        XCTAssertEqual(TravelEndNotice.dueAt(untilMillis: tripEnd, calendar: calendar("Asia/Tokyo")), tripEnd)
    }

    func testN7TheKeyHoldsTheAccountAndTheEndAMovedEndIsAnotherKey() {
        XCTAssertEqual(TravelEndNotice.key(uid: me, untilMillis: tripEnd), "n7|me|\(tripEnd)")
        XCTAssertNotEqual(
            TravelEndNotice.key(uid: me, untilMillis: tripEnd),
            TravelEndNotice.key(uid: me, untilMillis: tripEnd + 24 * hour)
        )
    }

    func testIAmTravellingUntilTheEndItselfWhichIsAlreadyTheReturn() {
        XCTAssertTrue(TravelEndNotice.isTravelling(untilMillis: tripEnd, nowMillis: tripEnd - 1))
        XCTAssertFalse(TravelEndNotice.isTravelling(untilMillis: tripEnd, nowMillis: tripEnd))
        XCTAssertFalse(TravelEndNotice.isTravelling(untilMillis: nil, nowMillis: tripEnd - 1))
    }

    // MARK: - T16 à T20

    func testT16MyTripIsOnTheListIsN7AloneNoN1N2N3N4EvenIfIAmOutAndXIsOut() {
        let members = [out(me, ref: at(8, 13, 40)), out("x", ref: at(8, 13, 0)), trusted("y")]

        XCTAssertEqual(plan(input(at(8, 16, 30), members, travelUntil: tripEnd)), ["n7": at(10, 8, 0)])
    }

    func testT17XTravelsYIsGreenNoN3NoN4XIsIgnored() throws {
        let members = [trusted(me), travelling("x"), trusted("y")]
        let value = input(at(8, 14, 0), members)
        let snapshot = try XCTUnwrap(value.snapshot)

        XCTAssertTrue(plan(value).isEmpty)
        XCTAssertEqual(NotificationPlanner.levels(of: snapshot, atMillis: at(8, 14, 0)).miff, .green)
    }

    func testT17bXTravelsAloneTheMiffIsNeutralNoNotificationAboutHimEvenLongAfterTheEndOfHisTrip() {
        let members = [trusted(me), travelling("x")]
        let alone = NotificationPlanSnapshot(
            miffId: miff, myUid: "x", members: [travelling("x")], savedAtMillis: at(8, 14, 0)
        )

        XCTAssertTrue(plan(input(at(8, 14, 0), members)).isEmpty)
        XCTAssertTrue(plan(input(at(12, 14, 0), members)).isEmpty)
        XCTAssertEqual(NotificationPlanner.levels(of: alone, atMillis: at(8, 14, 0)).miff, .neutral)
        XCTAssertEqual(NotificationPlanner.levels(of: alone, atMillis: at(12, 14, 0)).miff, .neutral)
    }

    func testT18XWasOrangeThenXTravelsTheN3WaitingIsGone() {
        let before = [trusted(me), out("x", ref: at(8, 13, 40))]
        XCTAssertEqual(
            plan(input(at(8, 14, 0), before)),
            ["n3": at(8, 16, 10, ms: 1), "n4": at(8, 18, 10, ms: 1)]
        )

        let after = [trusted(me), travelling("x")]
        XCTAssertTrue(plan(input(at(8, 14, 5), after)).isEmpty)
    }

    func testT19TheTripEndsTheNormalPlanIsBackAndN7StillWaitsForEight() {
        let members = [out(me, ref: at(10, 0, 5)), trusted("x")]

        // 00:30 le 10 : le voyage est fini (fin = 00:00), j'ai quitté un lieu à 00:05.
        XCTAssertEqual(
            plan(input(at(10, 0, 30), members, travelUntil: tripEnd)),
            ["n1": at(10, 2, 5, ms: 1), "n2": at(10, 4, 5, ms: 1), "n7": at(10, 8, 0)]
        )
    }

    func testT20XTravelsAndTheEndOfHisTripIsPastStillNeutral() {
        let members = [trusted(me), travelling("x"), out("y", ref: at(8, 9, 0))]
        let x = members[1].facts

        XCTAssertEqual(StatusColor.level(x, nowMillis: at(8, 12, 0)), .neutral)
        XCTAssertEqual(StatusColor.changeInstants(x), [])
        XCTAssertEqual(
            plan(input(at(8, 9, 30), members)),
            ["n3": at(8, 11, 30, ms: 1), "n4": at(8, 13, 30, ms: 1)]
        )
    }

    // MARK: - N7 : les conditions

    func testN7NeedsNoMiffNoSnapshotATripAlone() throws {
        let planned = NotificationPlanner.plan(input(at(8, 16, 0), [], travelUntil: tripEnd, withMiff: false))

        XCTAssertEqual(planned.scheduled.count, 1)
        let only = try XCTUnwrap(planned.scheduled.first)
        XCTAssertEqual(only.type, .n7)
        XCTAssertEqual(only.miffId, "")
        XCTAssertFalse(only.quiet, "elle part à 8 h, jamais la nuit")
    }

    func testN7IsNotPlannedWithoutTheSwitchTheGeneralSwitchThePermissionTheSessionOrATrip() {
        let now = at(8, 16, 0)
        let switchOff = NotificationPreferences().setting(.n7, to: false)
        let generalOff = NotificationPreferences().settingGeneral(false)

        XCTAssertEqual(plan(input(now, [], travelUntil: tripEnd)).keys.sorted(), ["n7"])
        XCTAssertTrue(plan(input(now, [], travelUntil: tripEnd, preferences: switchOff)).isEmpty)
        XCTAssertTrue(plan(input(now, [], travelUntil: tripEnd, preferences: generalOff)).isEmpty)
        XCTAssertTrue(plan(input(now, [], travelUntil: tripEnd, authorization: .denied)).isEmpty)
        XCTAssertTrue(plan(input(now, [], travelUntil: tripEnd, authorization: .notDetermined)).isEmpty)
        XCTAssertTrue(plan(input(now, [], travelUntil: tripEnd, session: nil)).isEmpty)
        XCTAssertTrue(plan(input(now, [], travelUntil: nil)).isEmpty)
    }

    func testN7TheOtherSwitchesDoNotCutItAndItDoesNotCutTheirs() {
        let members = [out(me, ref: at(10, 0, 5)), trusted("x")]
        let others = NotificationPreferences()
            .setting(.n1, to: false).setting(.n2, to: false).setting(.n3, to: false)
            .setting(.n4, to: false).setting(.n6, to: false)
        let n7Off = NotificationPreferences().setting(.n7, to: false)

        XCTAssertEqual(
            plan(input(at(10, 0, 30), members, travelUntil: tripEnd, preferences: others)),
            ["n7": at(10, 8, 0)]
        )
        XCTAssertEqual(
            plan(input(at(10, 0, 30), members, travelUntil: tripEnd, preferences: n7Off)),
            ["n1": at(10, 2, 5, ms: 1), "n2": at(10, 4, 5, ms: 1)]
        )
    }

    func testN7AMissedEightIsCaughtUpForADayThenForgotten() {
        let due = at(10, 8, 0)

        // Moins de 15 minutes de retard : rattrapage affiché ; ensuite jusqu'à 24 h : entrée silencieuse du centre ; au-delà : rien.
        let soon = NotificationPlanner.plan(input(due + 5 * 60_000, [], travelUntil: tripEnd))
        XCTAssertEqual(soon.catchUp.map { $0.type }, [.n7])
        let later = NotificationPlanner.plan(input(due + 5 * hour, [], travelUntil: tripEnd))
        XCTAssertEqual(later.silent.map { $0.type }, [.n7])
        XCTAssertTrue(later.catchUp.isEmpty && later.scheduled.isEmpty)
        XCTAssertTrue(plan(input(due + 25 * hour, [], travelUntil: tripEnd)).isEmpty)
    }

    func testN7AMovedEndIsAnotherNotificationTheSameEndAgainIsTheSame() throws {
        func key(_ until: Int) throws -> String {
            try XCTUnwrap(NotificationPlanner.plan(input(at(8, 9, 0), [], travelUntil: until)).scheduled.first).key
        }

        XCTAssertNotEqual(try key(tripEnd), try key(tripEnd + 24 * hour))
        XCTAssertEqual(try key(tripEnd), try key(tripEnd))
        XCTAssertEqual(plan(input(at(8, 9, 0), [], travelUntil: tripEnd + 24 * hour))["n7"], at(11, 8, 0))
    }

    func testN7AKeyAlreadyNotifiedIsNeverNotifiedAgain() {
        var value = input(at(10, 9, 0), [], travelUntil: tripEnd)
        value.notifiedKeys = [TravelEndNotice.key(uid: me, untilMillis: tripEnd)]

        XCTAssertTrue(plan(value).isEmpty)
    }

    func testN7IsOutsideTheDailyCapsNeitherCappedNorCounted() {
        let members = [out(me, ref: at(10, 0, 5)), trusted("x")]
        var value = input(at(10, 0, 30), members, travelUntil: tripEnd)
        // Six rappels déjà affichés aujourd'hui : plus de N1 / N2, mais N7 part quand même.
        value.shown = [NotificationType.n1, .n2, .n3].flatMap { type in
            (0..<2).map { ShownNotification(type: type, atMillis: at(10, 0, $0)) }
        }

        XCTAssertEqual(plan(value), ["n7": at(10, 8, 0)])
        // Et plusieurs N7 déjà affichées ne comptent pas dans les six.
        value.shown = (0..<6).map { ShownNotification(type: .n7, atMillis: at(10, 0, $0)) }
        XCTAssertEqual(plan(value).keys.sorted(), ["n1", "n2", "n7"])
    }

    // MARK: - stillTrue

    func testStillTrueN7NeedsNoColorAndWhileMyTripIsOnNoReminderAndNoNewsIsTrue() {
        let members = [out(me, ref: at(8, 13, 40)), out("x", ref: at(8, 13, 0))]
        let snapshot = NotificationPlanSnapshot(miffId: miff, myUid: me, members: members, savedAtMillis: at(8, 20, 0))
        let now = at(8, 20, 0)

        XCTAssertTrue(NotificationPlanner.stillTrue(.n7, snapshot: snapshot, atMillis: now))
        for type in [NotificationType.n1, .n2, .n3, .n4] {
            XCTAssertFalse(
                NotificationPlanner.stillTrue(type, snapshot: snapshot, atMillis: now, myTravelUntilMillis: tripEnd),
                "\(type)"
            )
        }
        // Les mêmes faits sans voyage : vrai (la garde est le voyage, rien d'autre).
        XCTAssertTrue(NotificationPlanner.stillTrue(.n2, snapshot: snapshot, atMillis: now))
    }

    func testTheDeliveredN7StaysOnScreenWhileItsSwitchAndThePermissionHold() {
        let on = NotificationPreferences()
        let off = on.setting(.n7, to: false)

        XCTAssertTrue(NotificationPlanner.travelEndStillWanted(preferences: on, authorization: .authorized))
        XCTAssertFalse(NotificationPlanner.travelEndStillWanted(preferences: off, authorization: .authorized))
        XCTAssertFalse(NotificationPlanner.travelEndStillWanted(preferences: on, authorization: .denied))
    }
}
// swiftlint:enable line_length
