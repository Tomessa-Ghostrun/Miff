import XCTest
@testable import Miff

/// Les vecteurs de test T1 à T15 du ticket Notifications (§20.9) : le contrat de `NotificationPlanner.plan` avec Android. Convention :
/// membres moi, X, Y ; tous partagent ; seuils 2 h / 4 h ; mode LARGE ; Miff active ; autorisation accordée ; « verts » = `in_trusted`.
/// Les heures sont écrites en heures et minutes d'un même jour (UTC) ; « + 1 ms » vient du « plus de 2 h » (à 2 h 00 pile : encore vert).
/// L'horizon de 3 h de N3 / N4 est un réglage PROPRE À iOS (parade n° 4 de la limite iOS) : il est désactivé pour les vecteurs (le contrat
/// commun avec Android) et a ses propres tests plus bas.
final class NotificationPlannerTests: XCTestCase {
    private let miffId = "miff-1"
    private var utc: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC") ?? .current
        return calendar
    }

    /// 2026-11-08 00:00 UTC.
    private let midnight = 1_794_096_000_000

    private func at(_ hour: Int, _ minute: Int, ms: Int = 0) -> Int {
        midnight + hour * 3_600_000 + minute * 60_000 + ms
    }

    /// L'instant où la couleur change : le repère + durée + 1 ms.
    private func after(_ hour: Int, _ minute: Int) -> Int {
        at(hour, minute, ms: 1)
    }

    // MARK: - Les membres

    private func green(_ uid: String, since: Int? = nil) -> PlanMember {
        let moment = since ?? at(8, 0)
        return PlanMember(
            uid: uid, sharing: true, presence: "in_trusted", presenceAtMillis: moment, lastUpdateAtMillis: moment
        )
    }

    private func out(_ uid: String, ref: Int, update: Int? = nil) -> PlanMember {
        PlanMember(uid: uid, sharing: true, presence: "out", presenceAtMillis: ref, lastUpdateAtMillis: update)
    }

    private func danger(_ uid: String, lastUpdate: Int?) -> PlanMember {
        PlanMember(
            uid: uid, sharing: true, presence: "in_risk", presenceAtMillis: at(9, 0), lastUpdateAtMillis: lastUpdate
        )
    }

    private func input(
        _ members: [PlanMember],
        now: Int,
        savedAt: Int? = nil,
        configure: (inout NotificationPlanInput) -> Void = { _ in }
    ) -> NotificationPlanInput {
        var value = NotificationPlanInput(
            nowMillis: now,
            snapshot: NotificationPlanSnapshot(
                miffId: miffId, myUid: "me", members: members, savedAtMillis: savedAt ?? now
            ),
            activeMiffId: miffId,
            sessionUid: "me"
        )
        value.calendar = utc
        value.horizonMillis = Int.max
        configure(&value)
        return value
    }

    private func fires(_ plan: NotificationPlan) -> [String] {
        plan.scheduled.map { "\($0.type.rawValue)@\($0.fireAtMillis)" }
    }

    // MARK: - T1 à T7

    func testT1MeOutWhileTheOthersAreGreenGivesN1AndN2AndNoFollowUp() {
        let plan = NotificationPlanner.plan(input([out("me", ref: at(13, 40)), green("x"), green("y")], now: at(14, 0)))

        XCTAssertEqual(fires(plan), ["n1@\(after(15, 40))", "n2@\(after(17, 40))"], "responsable : ni N3 ni N4")
        XCTAssertTrue(plan.catchUp.isEmpty)
    }

    func testT2AnotherMemberOutGivesN3AndN4ThirtyMinutesAfterTheMiffTurns() {
        let plan = NotificationPlanner.plan(input([green("me"), out("x", ref: at(13, 40)), green("y")], now: at(14, 0)))

        XCTAssertEqual(fires(plan), ["n3@\(after(16, 10))", "n4@\(after(18, 10))"], "ni N1 ni N2 : je suis vert")
    }

    func testT3IAmOutTooLargeModeHasNoFollowUpAndStrictModeKeepsThem() {
        let members = [out("me", ref: at(13, 50)), out("x", ref: at(13, 40)), green("y")]

        let broad = NotificationPlanner.plan(input(members, now: at(14, 0)))
        let strict = NotificationPlanner.plan(input(members, now: at(14, 0)) { $0.responsibility = .strict })

        XCTAssertEqual(fires(broad), ["n1@\(after(15, 50))", "n2@\(after(17, 50))"])
        XCTAssertEqual(
            fires(strict),
            ["n1@\(after(15, 50))", "n3@\(after(16, 10))", "n2@\(after(17, 50))", "n4@\(after(18, 10))"]
        )
    }

    func testT4TwoMembersOutTheMiffTurnsWithTheFirstOne() {
        let members = [green("me"), out("x", ref: at(13, 40)), out("y", ref: at(14, 30))]

        let plan = NotificationPlanner.plan(input(members, now: at(14, 45)))

        XCTAssertEqual(fires(plan), ["n3@\(after(16, 10))", "n4@\(after(18, 10))"])
    }

    func testT5APublicationMovesTheFollowUpsToTheNewReference() {
        let members = [green("me"), out("x", ref: at(13, 40), update: at(15, 50)), green("y")]

        let plan = NotificationPlanner.plan(input(members, now: at(15, 55)))

        XCTAssertEqual(fires(plan), ["n3@\(after(18, 20))", "n4@\(after(20, 20))"])
    }

    func testT6IAmInADangerZoneNoN1NoN2NoReminderAtAll() {
        let members = [danger("me", lastUpdate: at(13, 0)), green("x")]

        let plan = NotificationPlanner.plan(input(members, now: at(15, 10)))

        XCTAssertTrue(plan.isEmpty, "Q27 : aucun rappel « à toi » en zone de danger ; responsable du rouge de la Miff")
    }

    func testT6bAnotherMemberInADangerZoneNoOrangeStepJustN4() {
        let members = [green("me"), danger("x", lastUpdate: at(15, 0)), green("y")]

        let plan = NotificationPlanner.plan(input(members, now: at(15, 10)))

        XCTAssertEqual(fires(plan), ["n4@\(after(16, 30))"], "rouge après 1 h sans update : pas d'orange, pas de N3")
    }

    func testT7TheMiffTurnsRedBeforeTheOrangeFollowUpThenOnlyN4Remains() {
        // Y entre en zone de danger : rouge à 16 h 05 (dernière update à 15 h 05). X est orange depuis 15 h 40.
        let members = [green("me"), out("x", ref: at(13, 40)), danger("y", lastUpdate: at(15, 5))]

        let plan = NotificationPlanner.plan(input(members, now: at(16, 5, ms: 1_000)))

        XCTAssertEqual(fires(plan), ["n4@\(after(16, 35))"], "N3 abandonnée : à 16 h 10 la Miff est déjà rouge")
    }

    // MARK: - Mode voyage : un voyageur est neutre, jamais un rappel ni un rouge

    private func traveller(_ uid: String) -> PlanMember {
        PlanMember(uid: uid, sharing: true, presence: "travel", presenceAtMillis: at(1, 0), lastUpdateAtMillis: nil)
    }

    func testIAmTravellingNoReminderAtAllHoweverLongSinceMyLastSignal() {
        let plan = NotificationPlanner.plan(input([traveller("me"), green("x")], now: at(15, 10)))

        XCTAssertTrue(plan.isEmpty)
    }

    func testAnotherMemberTravellingNeverTurnsTheMiffOrangeOrRed() {
        let plan = NotificationPlanner.plan(input([green("me"), traveller("x")], now: at(15, 10)))

        XCTAssertTrue(plan.isEmpty)
    }

    // MARK: - T9 à T15

    func testT9TheThirdN1OfTheDayIsNotNotified() {
        let members = [out("me", ref: at(13, 40)), green("x")]
        let shown = [ShownNotification(type: .n1, atMillis: at(8, 0)), ShownNotification(type: .n1, atMillis: at(9, 0))]

        let plan = NotificationPlanner.plan(input(members, now: at(14, 0)) { $0.shown = shown })

        XCTAssertEqual(fires(plan), ["n2@\(after(17, 40))"])
        XCTAssertEqual(plan.dropped.map { $0.reason }, [.dailyCap])
    }

    func testTheCatchUpOverTheDailyCapBecomesASilentEntry() {
        let members = [out("me", ref: at(13, 40)), green("x")]
        let shown = [ShownNotification(type: .n1, atMillis: at(8, 0)), ShownNotification(type: .n1, atMillis: at(9, 0))]

        let plan = NotificationPlanner.plan(input(members, now: at(16, 0)) { $0.shown = shown })

        XCTAssertEqual(plan.silent.map { $0.type }, [.n1])
        XCTAssertTrue(plan.catchUp.isEmpty)
    }

    func testTheDailyTotalIsSixAcrossTheTypes() {
        let members = [out("me", ref: at(13, 40)), green("x")]
        let shown = (0..<6).map { ShownNotification(type: [.n3, .n4][$0 % 2], atMillis: at(8, $0)) }

        let plan = NotificationPlanner.plan(input(members, now: at(14, 0)) { $0.shown = shown })

        XCTAssertTrue(plan.scheduled.isEmpty, "6 au total : plus rien ce jour-là")
        XCTAssertEqual(plan.dropped.count, 2)
    }

    func testAnotherDayHasItsOwnCap() {
        let members = [out("me", ref: at(13, 40)), green("x")]
        let yesterday = (0..<6).map { ShownNotification(type: .n3, atMillis: at(8, $0) - 24 * 3_600_000) }

        let plan = NotificationPlanner.plan(input(members, now: at(14, 0)) { $0.shown = yesterday })

        XCTAssertEqual(plan.scheduled.count, 2)
    }

    func testT10AnotherActiveMiffMeansNothingIsPlannedForThisOne() {
        let plan = NotificationPlanner.plan(
            input([out("me", ref: at(13, 40)), green("x")], now: at(14, 0)) { $0.activeMiffId = "another-miff" }
        )

        XCTAssertTrue(plan.isEmpty)
    }

    func testT11AfterARebootTheFollowUpIsPlannedAgainFromTheSavedSnapshot() {
        let members = [green("me"), out("x", ref: at(13, 40)), green("y")]

        let plan = NotificationPlanner.plan(input(members, now: at(16, 0), savedAt: at(15, 55)))

        XCTAssertEqual(plan.scheduled.first.map { "\($0.type.rawValue)@\($0.fireAtMillis)" }, "n3@\(after(16, 10))")
    }

    func testT12AFollowUpWhoseTimeJustPassedIsACatchUpNotAScheduledNotification() {
        let members = [green("me"), out("x", ref: at(13, 40)), green("y")]

        let plan = NotificationPlanner.plan(input(members, now: at(16, 10, ms: 5)))

        XCTAssertEqual(plan.catchUp.map { $0.type }, [.n3])
        XCTAssertEqual(plan.catchUp.first?.fireAtMillis, after(16, 10))
        XCTAssertEqual(fires(plan), ["n4@\(after(18, 10))"])
    }

    func testT13AtNightN1IsQuietAndN2KeepsItsNormalLevel() {
        // Moi : orange à 22 h 10, rouge à 0 h 10 le lendemain.
        let members = [out("me", ref: at(20, 10)), green("x")]

        let plan = NotificationPlanner.plan(input(members, now: at(21, 0)))

        let byType = Dictionary(uniqueKeysWithValues: plan.scheduled.map { ($0.type, $0.quiet) })
        XCTAssertEqual(byType[.n1], true, "22 h 10 : nuit calme")
        XCTAssertEqual(byType[.n2], false, "0 h 10 : N2 garde son niveau normal")
    }

    func testT13AtNightN3IsQuietAndN4KeepsItsNormalLevel() {
        // X part à 20 h 40 : orange à 22 h 40 (N3 à 23 h 10), rouge à 0 h 40 (N4 à 1 h 10).
        let members = [green("me"), out("x", ref: at(20, 40)), green("y")]

        let plan = NotificationPlanner.plan(input(members, now: at(22, 30)))

        let byType = Dictionary(uniqueKeysWithValues: plan.scheduled.map { ($0.type, $0.quiet) })
        XCTAssertEqual(byType[.n3], true, "23 h 10 : nuit calme")
        XCTAssertEqual(byType[.n4], false, "1 h 10 : N4 aussi")
        XCTAssertEqual(plan.scheduled.map { $0.fireAtMillis }, [after(23, 10), after(25, 10)])
    }

    func testTheQuietNightCanBeTurnedOff() {
        let members = [out("me", ref: at(20, 10)), green("x")]

        let plan = NotificationPlanner.plan(input(members, now: at(21, 0)) { value in
            value.preferences = value.preferences.settingNightCalm(false)
        })

        XCTAssertEqual(plan.scheduled.first?.quiet, false)
    }

    func testTheNightIsFrom22hIncludedTo7hExcluded() {
        let boundaries: [(Int, Bool)] = [
            (at(6, 59, ms: 59_999), true), (at(7, 0), false), (at(21, 59, ms: 59_999), false), (at(22, 0), true),
            (at(0, 0), true), (at(12, 0), false)
        ]
        for (millis, expected) in boundaries {
            XCTAssertEqual(NotificationPlanner.isNight(atMillis: millis, calendar: utc), expected, "\(millis)")
        }
    }

    func testTheNightFollowsTheLocalHourAtTheTimeOfTheNotificationNotTheOneOfThePlanning() {
        var paris = Calendar(identifier: .gregorian)
        paris.timeZone = TimeZone(identifier: "Europe/Paris") ?? .current

        // 21 h 30 UTC = 22 h 30 à Paris (UTC+1 en novembre).
        XCTAssertFalse(NotificationPlanner.isNight(atMillis: at(21, 30), calendar: utc))
        XCTAssertTrue(NotificationPlanner.isNight(atMillis: at(21, 30), calendar: paris))
    }

    // MARK: - L'horizon de 3 h (parade iOS n° 4)

    func testTheIosHorizonOnlyProgramsFollowUpsWithinThreeHoursOfTheFreshestData() {
        let members = [green("me"), out("x", ref: at(13, 40)), green("y")]

        let plan = NotificationPlanner.plan(input(members, now: at(14, 0)) {
            $0.horizonMillis = NotificationPlanner.iosHorizonMillis
        })

        XCTAssertEqual(fires(plan), ["n3@\(after(16, 10))"], "N4 serait à 4 h 10 de la donnée : pas encore programmée")
    }

    func testTheHorizonIsInclusiveAtExactlyThreeHours() {
        let members = [green("me"), out("x", ref: at(13, 40)), green("y")]
        let horizon = NotificationPlanner.iosHorizonMillis
        let exactly = after(18, 10) - horizon
        let oneMillisecondTooFar = exactly - 1

        let inside = NotificationPlanner.plan(input(members, now: at(15, 20), savedAt: exactly) {
            $0.horizonMillis = horizon
        })
        let outside = NotificationPlanner.plan(input(members, now: at(15, 20), savedAt: oneMillisecondTooFar) {
            $0.horizonMillis = horizon
        })

        XCTAssertEqual(inside.scheduled.map { $0.type }, [.n3, .n4])
        XCTAssertEqual(outside.scheduled.map { $0.type }, [.n3])
    }

    func testTheHorizonNeverConcernsTheOwnRemindersWhoseCauseIsOnThisPhone() {
        let members = [out("me", ref: at(13, 40)), green("x")]

        let plan = NotificationPlanner.plan(input(members, now: at(14, 0), savedAt: at(14, 0) - 10 * 3_600_000) {
            $0.horizonMillis = NotificationPlanner.iosHorizonMillis
        })

        XCTAssertEqual(fires(plan), ["n1@\(after(15, 40))", "n2@\(after(17, 40))"])
    }

    func testT14TheAuthorizationMustBeGrantedOtherwiseNothingIsPlanned() {
        let members = [out("me", ref: at(13, 40)), green("x")]
        for authorization in [NotificationAuthorization.denied, .notDetermined] {
            let plan = NotificationPlanner.plan(input(members, now: at(14, 0)) { $0.authorization = authorization })

            XCTAssertTrue(plan.isEmpty, "\(authorization)")
        }
    }

    func testT15IfIStoppedSharingOrMyAgreementIsAwaitedNothingIsPlanned() {
        let stopped = PlanMember(
            uid: "me", sharing: false, presence: "out", presenceAtMillis: at(13, 40), lastUpdateAtMillis: nil
        )

        let notSharing = NotificationPlanner.plan(input([stopped, out("x", ref: at(13, 40))], now: at(14, 0)))
        let waiting = NotificationPlanner.plan(
            input([out("me", ref: at(13, 40)), green("x")], now: at(14, 0)) { $0.agreementWaiting = true }
        )

        XCTAssertTrue(notSharing.isEmpty)
        XCTAssertTrue(waiting.isEmpty)
    }
}
