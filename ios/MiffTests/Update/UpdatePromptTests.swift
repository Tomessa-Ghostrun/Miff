import XCTest
@testable import Miff

/// Quand la bulle d'incitation s'affiche (ticket Update D18, D19, D21, critères A1, A2) et de quelle couleur est le bouton central (critère
/// B1) : la couleur est `StatusColor.level` INCHANGÉE ; la bulle ajoute la seule règle de la zone de danger. Les limites viennent de
/// `StatusThresholds`, jamais retapées. Miroir d'`UpdatePromptTest.kt`.
final class UpdatePromptTests: XCTestCase {
    private let now = UpdateFixtures.noon
    private let hour = 60 * 60 * 1000

    private func status(_ presence: PresenceValue?, presenceAt: Int? = nil, lastUpdateAt: Int? = nil) -> MyStatusFacts {
        MyStatusFacts(
        presence: presence, presenceAtMillis: presenceAt, lastUpdateAtMillis: lastUpdateAt, hasPhoto: false
        )
    }

    private func prompt(_ facts: MyStatusFacts?, now: Int? = nil, sharing: Bool = true) -> PromptLevel {
        UpdatePrompt.prompt(facts, sharing: sharing, nowMillis: now ?? self.now)
    }

    // MARK: - Orange et rouge, aux limites des seuils

    func testJustUnderAnHourNothingAtAnHourOrangeAtTwoHoursIncludedOrangeJustOverRed() {
        func at(_ elapsed: Int) -> PromptLevel {
            prompt(status(.out, presenceAt: now - elapsed))
        }

        XCTAssertEqual(at(StatusThresholds.orangeAfterMillis - 1), .hidden)
        XCTAssertEqual(at(StatusThresholds.orangeAfterMillis), .orange)
        XCTAssertEqual(at(StatusThresholds.redAfterMillis), .orange)
        XCTAssertEqual(at(StatusThresholds.redAfterMillis + 1), .red)
    }

    func testTheColourOfTheBubbleIsTheColourOfTheCentralButtonOneCalculation() {
        // À chaque moment d'une longue journée, la bulle dit orange exactement quand StatusColor dit orange, rouge quand rouge.
        for minutes in stride(from: 0, through: 300, by: 5) {
            let facts = status(.out, presenceAt: now - minutes * 60_000)
            let memberFacts = MemberStatusFacts(
                sharingEnabled: true, presence: .out, presenceAtMillis: facts.presenceAtMillis, lastUpdateAtMillis: nil
            )
            let expected: PromptLevel
            switch StatusColor.level(memberFacts, nowMillis: now) {
            case .orange:
                expected = .orange
            case .red:
                expected = .red
            default:
                expected = .hidden
            }

            XCTAssertEqual(prompt(facts), expected, "à \(minutes) min")
            let level = UpdatePrompt.level(facts, sharing: true, nowMillis: now)
            XCTAssertEqual(level, StatusColor.level(memberFacts, nowMillis: now))
        }
    }

    func testTheCentralButtonTakesTheColourOfTheLevelAndGreyWhenNeutral() {
        XCTAssertEqual(CentralButtonState(level: .neutral), .neutral)
        XCTAssertEqual(CentralButtonState(level: .green), .safe)
        XCTAssertEqual(CentralButtonState(level: .orange), .warning)
        XCTAssertEqual(CentralButtonState(level: .red), .danger)
        // Statut absent ou partage arrêté : neutre (gris), jamais une couleur inventée.
        XCTAssertEqual(UpdatePrompt.level(nil, sharing: true, nowMillis: now), .neutral)
        let stopped = status(.out, presenceAt: now - 3 * hour)
        XCTAssertEqual(UpdatePrompt.level(stopped, sharing: false, nowMillis: now), .neutral)
    }

    // MARK: - Elle disparaît quand je ne suis plus orange ni rouge (D18)

    func testAnUpdatePublishedBringsBackToGreenTheBubbleGoes() {
        let before = status(.out, presenceAt: now - 3 * hour)
        XCTAssertEqual(prompt(before), .red)
        // Publier fixe lastUpdateAt à maintenant : le repère de la couleur est le plus récent du départ et de l'update.
        let after = status(.out, presenceAt: now - 3 * hour, lastUpdateAt: now)
        XCTAssertEqual(prompt(after), .hidden)
    }

    func testOrangeThenArrivalAtHomeWithoutPublishingTheBubbleGoes() {
        XCTAssertEqual(prompt(status(.out, presenceAt: now - 90 * 60_000)), .orange)
        XCTAssertEqual(prompt(status(.inTrusted, presenceAt: now, lastUpdateAt: now)), .hidden)
    }

    func testTheBubbleNeverGoesWithTimeOnlyWithAChangeOfLevel() {
        let facts = status(.out, presenceAt: now - 3 * hour)

        for later in [0, 10 * hour, 40 * hour] {
            XCTAssertEqual(prompt(facts, now: now + later), .red)
        }
    }

    // MARK: - La zone de danger : le rouge reste rouge, la bulle part quand une update suit l'arrivée (C8, D8)

    func testInADangerZoneTheBubbleIsRedUntilAnUpdateIsPublishedAfterTheArrival() {
        let entered = status(.inRisk, presenceAt: now - 10 * 60_000, lastUpdateAt: now - 5 * hour)
        XCTAssertEqual(prompt(entered), .red)
        let updated = status(.inRisk, presenceAt: now - 10 * 60_000, lastUpdateAt: now - 60_000)
        XCTAssertEqual(prompt(updated), .hidden)
        // La couleur elle-même reste rouge en zone de danger : une update ne ramène pas au vert là.
        XCTAssertEqual(UpdatePrompt.level(updated, sharing: true, nowMillis: now), .red)
    }

    func testInADangerZoneAnUpdateExactlyAtTheArrivalCountsAsAfterIt() {
        XCTAssertEqual(prompt(status(.inRisk, presenceAt: now - 1_000, lastUpdateAt: now - 1_000)), .hidden)
    }

    func testInADangerZoneWithNoUpdateAtAllTheBubbleShows() {
        XCTAssertEqual(prompt(status(.inRisk, presenceAt: now - 1_000, lastUpdateAt: nil)), .red)
    }

    // MARK: - Rien à encourager

    func testNoStatusSharingStoppedUnknownPresenceNeverAnyBubble() {
        XCTAssertEqual(prompt(nil), .hidden)
        XCTAssertEqual(prompt(status(.out, presenceAt: now - 3 * hour), sharing: false), .hidden)
        // Une personne jamais passée en zone de confiance est neutre (N4) : pas de bulle, quoi que dise l'horloge.
        XCTAssertEqual(prompt(status(.unknown, presenceAt: now - 30 * hour)), .hidden)
        XCTAssertEqual(prompt(status(nil)), .hidden)
    }

    func testAReferenceInTheFutureAWrongClockIsGreenNoBubble() {
        XCTAssertEqual(prompt(status(.out, presenceAt: now + 5 * hour)), .hidden)
    }

    // MARK: - Où elle s'affiche (D21) et ce que dit le lecteur d'écran (D34)

    func testTheBubbleIsOnEveryTabButTheUpdateAndHidesWithTheBar() {
        for tab in [MainTab.feed, .profile] {
            XCTAssertTrue(MainShellRules.bubbleVisible(prompt: .orange, selected: tab, barVisible: true), "\(tab)")
            XCTAssertTrue(MainShellRules.bubbleVisible(prompt: .red, selected: tab, barVisible: true), "\(tab)")
        }
        XCTAssertFalse(MainShellRules.bubbleVisible(prompt: .red, selected: .update, barVisible: true))
        // Avec le clavier (la barre est masquée), la bulle l'est aussi : une queue vers un bouton absent n'a pas de sens.
        XCTAssertFalse(MainShellRules.bubbleVisible(prompt: .red, selected: .feed, barVisible: false))
        XCTAssertFalse(MainShellRules.bubbleVisible(prompt: .hidden, selected: .feed, barVisible: true))
    }

    func testTheBubbleIsAnnouncedOnceWhenItAppearsAndWhenItGoesFromOrangeToRedNeverEveryMinute() {
        XCTAssertTrue(UpdatePrompt.shouldAnnounce(previous: .hidden, current: .orange))
        XCTAssertTrue(UpdatePrompt.shouldAnnounce(previous: .hidden, current: .red))
        XCTAssertTrue(UpdatePrompt.shouldAnnounce(previous: .orange, current: .red))
        // Rien de nouveau : pas d'annonce (le niveau est recalculé chaque minute).
        XCTAssertFalse(UpdatePrompt.shouldAnnounce(previous: .orange, current: .orange))
        XCTAssertFalse(UpdatePrompt.shouldAnnounce(previous: .red, current: .red))
        // Quand elle disparaît, rien n'est annoncé.
        XCTAssertFalse(UpdatePrompt.shouldAnnounce(previous: .red, current: .hidden))
    }

    func testTheTextsOfTheBubbleAreTheOnesOfTheMockupsWordForWord() {
        // D21 : le texte des maquettes du PM, conservé tel quel ; le catalogue est celui du français (langue source).
        let orange = L10n.string("update_bubble_orange")
        let red = L10n.string("update_bubble_red")

        XCTAssertNotEqual(orange, "update_bubble_orange")
        XCTAssertNotEqual(red, "update_bubble_red")
        XCTAssertTrue(orange.contains("orange") && (red.contains("rouge") || red.contains("turned red")))
        XCTAssertTrue(orange.contains("publiant une update") || orange.contains("posting an update"))
        XCTAssertTrue(red.contains("publiant une update") || red.contains("posting an update"))
    }
}
