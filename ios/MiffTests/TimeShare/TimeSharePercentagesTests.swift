import XCTest
@testable import Miff

/// Les pourcentages entiers (TIME-3, ticket §5.3, critère TS-10) : somme EXACTEMENT 100, méthode du plus grand reste, égalité au plus grand
/// temps réel puis à l'ordre de la légende, « < 1 % » pour une part réelle de moins de 1 %, détail de la zone de danger arrondi À L'INTÉRIEUR
/// de sa ligne.
final class TimeSharePercentagesTests: XCTestCase {
    private func percents(_ millis: [Int]) -> [Int] {
        TimeSharePercentages.allocate(millis).map { $0.percent }
    }

    func testTheLargestRemainderGivesTheLastPointsAndTheSumIsOneHundred() {
        XCTAssertEqual(percents([1, 1, 1]), [34, 33, 33], "égalité parfaite : l'ordre de la légende")
        XCTAssertEqual(percents([2, 1]), [67, 33])
        XCTAssertEqual(percents([84, 21, 63]), [50, 12, 38], "égalité à 0,5 : le plus grand temps réel reçoit le point")
    }

    func testASingleCategoryIsOneHundredPercent() {
        XCTAssertEqual(percents([7_000_000]), [100])
    }

    func testAShareUnderOnePercentCountsZeroAndTheOthersShareTheOneHundredPoints() {
        // 98,01 %, 1 % pile (pas « moins de 1 % ») et 0,99 % (moins de 1 %).
        let shares = TimeSharePercentages.allocate([9_801, 100, 99])

        XCTAssertEqual(shares.map { $0.isUnderOne }, [false, false, true])
        XCTAssertEqual(shares.map { $0.percent }, [99, 1, 0])
    }

    func testSeveralTinyPartsNeverBreakTheSum() {
        // Trois parts à 0,8 % : elles comptent 0, les trois autres se partagent les 100 points.
        let millis = [32_500, 32_500, 32_500, 800, 800, 800]

        let shares = TimeSharePercentages.allocate(millis)

        XCTAssertEqual(shares.map { $0.isUnderOne }, [false, false, false, true, true, true])
        XCTAssertEqual(shares.map { $0.percent }.reduce(0, +), 100)
        XCTAssertEqual(shares.prefix(3).map { $0.percent }, [34, 33, 33])
    }

    func testAnEmptyCategoryIsNeverTinyNorPositive() {
        let shares = TimeSharePercentages.allocate([0, 60, 40])

        XCTAssertEqual(shares[0], TimeSharePercentages.Share(percent: 0, isUnderOne: false))
        XCTAssertEqual(shares.map { $0.percent }, [0, 60, 40])
    }

    func testNoTimeAtAllGivesZeros() {
        XCTAssertEqual(percents([0, 0]), [0, 0])
        XCTAssertEqual(percents([]), [])
    }

    func testAnyDistributionAddsUpToOneHundredAndNeverGoesNegative() {
        var seed: UInt64 = 20_261_117
        func next() -> Int {
            seed = seed &* 6_364_136_223_846_793_005 &+ 1_442_695_040_888_963_407
            return Int((seed >> 33) % 100_000)
        }
        for _ in 0..<600 {
            let count = 1 + next() % 8
            let millis = (0..<count).map { _ in next() % 5 == 0 ? 0 : next() * 36 }
            guard millis.reduce(0, +) > 0 else {
                continue
            }
            let shares = TimeSharePercentages.allocate(millis)
            XCTAssertEqual(shares.map { $0.percent }.reduce(0, +), 100, "\(millis)")
            for (time, share) in zip(millis, shares) {
                XCTAssertGreaterThanOrEqual(share.percent, 0)
                if time == 0 {
                    XCTAssertEqual(share.percent, 0)
                    XCTAssertFalse(share.isUnderOne)
                }
            }
        }
    }

    func testTheDetailOfTheDangerZoneAddsUpToItsLine() {
        let given = TimeSharePercentages.largestRemainder([14, 10], among: [0, 1], target: 12)

        XCTAssertEqual(given, [0: 7, 1: 5])
    }

    func testTheDetailKeepsTheSumEvenWhenOnePlaceRoundsToZero() {
        let given = TimeSharePercentages.largestRemainder([9, 1], among: [0, 1], target: 1)

        XCTAssertEqual(given.values.reduce(0, +), 1)
        XCTAssertEqual(given[0], 1)
    }

    func testTheFormatIsTheOneOfTheSystemForEachLanguage() {
        let french = TimeShareTexts.percent(72, locale: Locale(identifier: "fr_FR"))
        let english = TimeShareTexts.percent(72, locale: Locale(identifier: "en_US"))

        XCTAssertEqual(english, "72%")
        XCTAssertTrue(french.hasPrefix("72"))
        XCTAssertTrue(french.hasSuffix("%"))
        XCTAssertEqual(french.count, 4, "« 72 % » : un espace insécable entre le nombre et le signe")
        XCTAssertNotEqual(french, "72 %", "l'espace est insécable (fine), jamais une espace ordinaire")
    }
}
