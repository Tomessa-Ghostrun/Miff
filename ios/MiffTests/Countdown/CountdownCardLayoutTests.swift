import SwiftUI
import UIKit
import XCTest
@testable import Miff

/// La mise en page de l'encart (§2.8, §2.9, critères CD-26 et CD-I2), mesurée sur un vrai rendu SwiftUI : zone tactile d'au moins 44 pt, texte
/// qui passe à la ligne avec les plus grandes tailles de texte (l'encart GRANDIT, rien n'est coupé), et aucun encart sans contexte.
@MainActor
final class CountdownCardLayoutTests: XCTestCase {
    private let width: CGFloat = 358

    private func card(_ size: DynamicTypeSize) -> some View {
        let display = CountdownDisplay(remainingMillis: 72 * 60_000, minutes: 72, nextChangeMillis: 0)
        return CountdownCard(display: display, texts: CountdownTexts(), onOpen: {})
            .environment(\.dynamicTypeSize, size)
    }

    private func facts(_ presence: PresenceValue, since: Int) -> MyStatusFacts {
        MyStatusFacts(presence: presence, presenceAtMillis: since, lastUpdateAtMillis: nil, hasPhoto: false)
    }

    private func height(of view: some View) -> CGFloat {
        let host = UIHostingController(rootView: view)
        return host.sizeThatFits(in: CGSize(width: width, height: .greatestFiniteMagnitude)).height
    }

    func testTheTouchTargetIsAtLeast44PointsHighAtTheDefaultSize() {
        XCTAssertGreaterThanOrEqual(height(of: card(.large)), Spacing.minTouchTarget)
    }

    func testTheTouchTargetStaysAtLeast44PointsAtTheSmallestSize() {
        XCTAssertGreaterThanOrEqual(height(of: card(.xSmall)), Spacing.minTouchTarget)
    }

    func testTheCardGrowsInHeightWithTheLargestAccessibilitySizeInsteadOfCuttingTheText() {
        let normal = height(of: card(.large))
        let largest = height(of: card(.accessibility5))
        XCTAssertGreaterThan(largest, normal * 1.5, "le texte passe à la ligne : l'encart est plus haut")
    }

    func testTheHourglassFollowsTheTextWithoutExceedingOneAndAHalfTimesItsBaseSize() {
        XCTAssertEqual(CountdownCard.hourglassSize(scaled: 24), 24)
        XCTAssertEqual(CountdownCard.hourglassSize(scaled: 30), 30)
        XCTAssertEqual(CountdownCard.hourglassSize(scaled: 36), 36)
        XCTAssertEqual(CountdownCard.hourglassSize(scaled: 80), 36, "au plus 1,5 fois 24 pt")
    }

    func testWithoutAContextTheSlotDrawsNothing() {
        let empty = height(of: CountdownSlot(isActive: true))
        XCTAssertEqual(empty, 0, accuracy: 0.5)
    }

    func testWithAContextButNoStatusTheSlotDrawsNothingNoFlash() {
        let context = CountdownContext(status: MyStatusStore(), clock: { 1_794_063_600_000 }, openUpdate: {})
        let empty = height(of: CountdownSlot(isActive: true).environment(\.countdownContext, context))
        XCTAssertEqual(empty, 0, accuracy: 0.5)
    }

    func testWithMyStatusOutsideAndGreenTheSlotDrawsTheCard() {
        let now = 1_794_063_600_000
        let store = MyStatusStore()
        store.update(facts(.out, since: now - 48 * 60_000))
        let context = CountdownContext(status: store, clock: { now }, openUpdate: {})
        let shown = height(of: CountdownSlot(isActive: true).environment(\.countdownContext, context))
        XCTAssertGreaterThanOrEqual(shown, Spacing.minTouchTarget)
    }

    func testOrangeOrRedOrInsideDrawsNothing() {
        let now = 1_794_063_600_000
        let hour = 3_600_000
        let cases = [
            facts(.out, since: now - 3 * hour),
            facts(.out, since: now - 6 * hour),
            facts(.inTrusted, since: now - 60_000)
        ]
        for facts in cases {
            let store = MyStatusStore()
            store.update(facts)
            let context = CountdownContext(status: store, clock: { now }, openUpdate: {})
            let drawn = height(of: CountdownSlot(isActive: true).environment(\.countdownContext, context))
            XCTAssertEqual(drawn, 0, accuracy: 0.5)
        }
    }

    func testStoppedSharingDrawsNothing() {
        let now = 1_794_063_600_000
        let store = MyStatusStore()
        store.update(facts(.out, since: now - 60_000), sharingStopped: true)
        let context = CountdownContext(status: store, clock: { now }, openUpdate: {})
        let drawn = height(of: CountdownSlot(isActive: true).environment(\.countdownContext, context))
        XCTAssertEqual(drawn, 0, accuracy: 0.5)
    }

    // MARK: - L'espacement (revue de la PR #244) : masqué, l'encart ne change RIEN à la mise en page d'avant

    /// Un hôte tel que le Profil et le Feed : une pile qui espace de `Spacing.lg`, dont un enfant est la paire « ce qui précède + l'encart »
    /// rangée dans une pile d'espacement 0.
    private func column(slot: Bool, context: CountdownContext) -> some View {
        VStack(spacing: Spacing.lg) {
            Text(verbatim: "Haut")
            VStack(spacing: 0) {
                Text(verbatim: "Bulle")
                if slot {
                    CountdownSlot(isActive: true, topSpacing: Spacing.lg)
                }
            }
            Text(verbatim: "Bas")
        }
        .environment(\.countdownContext, context)
    }

    func testAHiddenSlotAddsNoSpaceInsideItsHostColumn() {
        let context = CountdownContext(status: MyStatusStore(), clock: { 1_794_063_600_000 }, openUpdate: {})
        let without = height(of: column(slot: false, context: context))
        let hidden = height(of: column(slot: true, context: context))
        XCTAssertEqual(hidden, without, accuracy: 0.5, "masqué : la mise en page de main, jamais 24 pt de plus")
    }

    func testAVisibleSlotAddsItsOwnSpaceAboveAndItsCardHeight() {
        let now = 1_794_063_600_000
        let store = MyStatusStore()
        store.update(facts(.out, since: now - 48 * 60_000))
        let context = CountdownContext(status: store, clock: { now }, openUpdate: {})
        let without = height(of: column(slot: false, context: context))
        let shown = height(of: column(slot: true, context: context))
        XCTAssertGreaterThanOrEqual(shown - without, Spacing.lg + Spacing.minTouchTarget - 0.5)
    }
}
