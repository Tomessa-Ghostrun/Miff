// Les assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// Retour du PM sur le TestFlight 38 (#263) : (1) l'indicateur de « tirer pour actualiser » ne se voyait pas, (2) le Feed SAUTILLAIT quand on le
/// tirait. Cause du saut : UIKit ajoute la hauteur de la roue d'actualisation à la marge haute de la vue de défilement pendant le geste, le
/// lecteur d'origine du contenu la prenait pour une nouvelle origine et SwiftUI réduisait la marge haute de la liste d'autant (puis l'inverse à la
/// fin). `RefreshInsetGuard` ignore ces mesures ; l'indicateur reste au moins 0,8 s.
final class RefreshInsetGuardTests: XCTestCase {
    func testAnOriginIsMeasuredAtRest() {
        var guardian = RefreshInsetGuard()

        XCTAssertTrue(guardian.allowsMeasure(refreshing: false, now: 0))
        XCTAssertTrue(guardian.allowsMeasure(refreshing: false, now: 100))
        XCTAssertNil(guardian.secondsUntilMeasure(now: 100))
    }

    func testNothingIsMeasuredWhileRefreshing() {
        var guardian = RefreshInsetGuard()

        XCTAssertFalse(guardian.allowsMeasure(refreshing: true, now: 10))
        XCTAssertFalse(guardian.allowsMeasure(refreshing: true, now: 15), "même une longue actualisation")
    }

    func testTheMeasureWaitsForTheWheelToWithdrawAfterTheRefresh() {
        var guardian = RefreshInsetGuard()
        _ = guardian.allowsMeasure(refreshing: true, now: 10)

        XCTAssertFalse(guardian.allowsMeasure(refreshing: false, now: 10.2), "la roue se retire encore")
        XCTAssertEqual(guardian.secondsUntilMeasure(now: 10.2) ?? 0, RefreshInsetGuard.settleSeconds - 0.2, accuracy: 0.0001)
        XCTAssertFalse(guardian.allowsMeasure(refreshing: false, now: 10 + RefreshInsetGuard.settleSeconds - 0.01))
        XCTAssertTrue(guardian.allowsMeasure(refreshing: false, now: 10 + RefreshInsetGuard.settleSeconds))
        XCTAssertNil(guardian.secondsUntilMeasure(now: 11))
    }

    func testANewRefreshRestartsTheWait() {
        var guardian = RefreshInsetGuard()
        _ = guardian.allowsMeasure(refreshing: true, now: 0)
        _ = guardian.allowsMeasure(refreshing: true, now: 0.5)

        XCTAssertFalse(guardian.allowsMeasure(refreshing: false, now: 0.9))
        XCTAssertTrue(guardian.allowsMeasure(refreshing: false, now: 1.2))
    }

    func testTheSettleDelayCoversTheSystemAnimationOfTheWheel() {
        XCTAssertGreaterThanOrEqual(RefreshInsetGuard.settleSeconds, 0.4)
        XCTAssertLessThanOrEqual(RefreshInsetGuard.settleSeconds, 1)
    }
}

/// Sur les SOURCES : ce qui garde le Feed stable et l'indicateur visible, pour qu'un changement ne le casse pas sans que personne le voie.
final class FeedRefreshSourcesTests: XCTestCase {
    private var app: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Feed
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
            .appendingPathComponent("Miff")
    }

    private func code(_ relative: String) throws -> String {
        let url = app.appendingPathComponent(relative)
        guard FileManager.default.fileExists(atPath: url.path) else {
            XCTFail("Fichier introuvable : \(relative)")
            return ""
        }
        return try String(contentsOf: url, encoding: .utf8)
            .components(separatedBy: "\n")
            .filter { !$0.trimmingCharacters(in: .whitespaces).hasPrefix("//") }
            .joined(separator: "\n")
    }

    func testTheOriginOfTheContentIsNeverMeasuredWhileTheRefreshWheelHoldsItsInset() throws {
        let reader = try code("Views/Feed/ScrollDistanceReader.swift")
        let layout = try code("Views/Feed/FeedCollapsingLayout.swift")

        XCTAssertTrue(reader.contains("RefreshInsetGuard()"))
        XCTAssertTrue(reader.contains("refreshControl?.isRefreshing"))
        let measure = try XCTUnwrap(reader.range(of: "insetGuard.allowsMeasure("))
        let report = try XCTUnwrap(reader.range(of: "onOrigin?(frame.minY"))
        XCTAssertLessThan(measure.lowerBound, report.lowerBound, "la garde passe AVANT toute mesure de l'origine")
        XCTAssertTrue(layout.contains("if !refreshing, FeedTopInset.isNewMeasure(origin, old: contentOrigin)"), "défense en profondeur côté SwiftUI")
    }

    func testTheCardsKeepTheirIdentityAndTheListIsNeverAnimatedOrRebuiltByTheRefresh() throws {
        let list = try code("Views/Feed/FeedListView.swift")

        XCTAssertTrue(list.contains("ForEach(content.cards, id: \\.member.uid)"), "une carte = un identifiant de membre, jamais un numéro ni un UUID")
        XCTAssertEqual(list.components(separatedBy: ".refreshable").count - 1, 1)
        for forbidden in [".animation(", ".transaction", "UUID()", ".id(UUID", ".id(model.", "Date()"] {
            XCTAssertFalse(list.contains(forbidden), forbidden)
        }
    }

    func testTheIndicatorIsOnTopOfEverythingBelowTheHeaderAndReadableOverAnything() throws {
        let layout = try code("Views/Feed/FeedCollapsingLayout.swift")
        let header = try XCTUnwrap(layout.range(of: "headerView\n            refreshIndicator"))
        XCTAssertGreaterThan(header.lowerBound, layout.startIndex, "dernier enfant du ZStack : au-dessus du contenu ET de l'en-tête")
        XCTAssertTrue(layout.contains(".padding(.top, headerHeight + Spacing.sm)"), "juste SOUS l'en-tête, jamais masqué par lui")
        XCTAssertTrue(layout.contains("Circle().fill(colorBgSurface.color)"), "une pastille lisible sur n'importe quel fond")
        XCTAssertTrue(layout.contains(".animation(reduceMotion ? nil"), "« Réduire les animations » est lu")
        XCTAssertGreaterThanOrEqual(FeedRefreshIndicator.size, 28)
        XCTAssertLessThanOrEqual(FeedRefreshIndicator.fadeSeconds, 0.3)
    }

    func testTheMinimumDisplayIsAtLeastSixTenthsOfASecondAndWaitsNothingElse() throws {
        XCTAssertGreaterThanOrEqual(FeedTimings().minRefreshDisplayMillis, 600)
        XCTAssertLessThanOrEqual(FeedTimings().minRefreshDisplayMillis, 1500, "un minimum d'AFFICHAGE, pas une attente")
        let actions = try code("ViewModels/FeedViewModel+Actions.swift")
        XCTAssertEqual(actions.components(separatedBy: "await holdIndicator(").count - 1, 2, "lecture faite, et geste trop rapproché")
        XCTAssertFalse(actions.contains("await dependencies.onPullOwnPresence"), "la position n'est jamais attendue")
    }
}

/// L'indicateur reste au moins 0,8 s depuis le geste, sans rien relire de plus et sans attendre la position.
@MainActor
final class FeedRefreshIndicatorTests: XCTestCase {
    private let repo = FakeMiffRepository()
    private let statuses = FakeStatusObserver()
    private let refresher = ClockAdvancingRefresher()
    private let network = FakeNetwork()
    private let clock = TestBox<Int>(feedNow)
    private let slept = TestBox<[Int]>([])
    private let refreshingWhileSleeping = TestBox<[Bool]>([])
    private let other = feedMember("a", first: "Thomas")
    private let mine = feedMember("me", first: "Camille")
    private var model: FeedViewModel?
    /// Les attentes de l'indicateur que ces tests provoquent (le minimum entier, ou son reste).
    nonisolated fileprivate static let holds: Set<Int> = [800, 500]

    private func makeModel() async -> FeedViewModel {
        repo.myMiffsResult = .loaded(
            MyMiffs(miffs: [MiffSummary(id: testMiffId, name: "Chez nous")], lost: [], premium: false, fromCache: false)
        )
        let directory = MiffDirectory(
            repository: repo,
            currentUid: { "me" },
            activeStore: MemoryActiveMiffStore(),
            cache: FakeCachePurger(timeline: Timeline())
        )
        await directory.refresh()
        let now = clock
        let sleeps = slept
        let flags = refreshingWhileSleeping
        refresher.clock = clock
        let holder = TestBox<FeedViewModel?>(nil)
        let dependencies = FeedDependencies(
            directory: directory,
            repository: repo,
            services: FeedServices(statuses: statuses, positions: FakePositionReader(), refresher: refresher),
            network: network,
            awaiting: AwaitingAgreement(),
            currentUid: { "me" },
            clock: { now.value },
            sleep: { millis in
                // Seules les attentes de l'indicateur sont notées et rendues aussitôt ; les autres minuteries du Feed (la minute, les essais)
                // dorment pour de vrai (accélérées), sinon leurs boucles ne céderaient jamais la main.
                if FeedRefreshIndicatorTests.holds.contains(millis) {
                    sleeps.value.append(millis)
                    flags.value.append(holder.value?.refreshing ?? false)
                    return
                }
                let nanos = millis == FeedMillis.minute ? 20_000_000 : UInt64(max(millis, 0)) * 1_000_000
                try? await Task.sleep(nanoseconds: nanos)
            },
            timings: FeedTimings(statusRetryDelaysMillis: [5], firstStatusAnswerMillis: 600_000, cacheGraceMillis: 30)
        )
        let built = FeedViewModel(dependencies: dependencies)
        holder.value = built
        model = built
        return built
    }

    private func readyModel() async -> (FeedViewModel, Task<Void, Never>) {
        let built = await makeModel()
        let task = Task {
            await built.observe(miffId: testMiffId)
        }
        repo.observer.emit(
            .data(MiffSnapshot(miff: testMiffDetails(), members: [other, mine], places: [], fromCache: false))
        )
        await waitUntil { self.statuses.active == 1 }
        statuses.emit(["a": feedStatus(.inTrusted)])
        await waitUntil {
            if case .ready = built.uiState {
                return true
            }
            return false
        }
        return (built, task)
    }

    private func stop(_ task: Task<Void, Never>) async {
        task.cancel()
        await task.value
    }

    func testAFastRefreshKeepsTheIndicatorForTheRestOfTheMinimumTime() async {
        let (built, task) = await readyModel()

        await built.pullToRefresh()

        XCTAssertEqual(slept.value, [800], "rien n'a pris de temps : tout le minimum est attendu")
        XCTAssertEqual(refreshingWhileSleeping.value, [true], "l'indicateur est bien affiché pendant l'attente")
        XCTAssertFalse(built.refreshing)
        await stop(task)
    }

    func testASlowRefreshAddsNothing() async {
        let (built, task) = await readyModel()
        refresher.advanceBy = 900

        await built.pullToRefresh()

        XCTAssertTrue(slept.value.isEmpty, "le minimum est déjà dépassé : aucune attente de plus")
        await stop(task)
    }

    func testARefreshThatTookAPartOfTheMinimumWaitsOnlyForTheRest() async {
        let (built, task) = await readyModel()
        refresher.advanceBy = 300

        await built.pullToRefresh()

        XCTAssertEqual(slept.value, [500])
        await stop(task)
    }

    func testAGestureTooCloseReadsNothingButStillShowsTheIndicator() async {
        let (built, task) = await readyModel()

        await built.pullToRefresh()
        await built.pullToRefresh()

        XCTAssertEqual(refresher.calls, 1, "une seule lecture : la limite de 15 s est inchangée")
        XCTAssertEqual(slept.value, [800, 800])
        XCTAssertEqual(refreshingWhileSleeping.value, [true, true])
        XCTAssertFalse(built.refreshing)
        await stop(task)
    }

    func testOfflineTheGestureStillEndsAtOnceWithNoIndicator() async {
        let (built, task) = await readyModel()
        network.online = false

        await built.pullToRefresh()

        XCTAssertTrue(slept.value.isEmpty)
        XCTAssertEqual(refresher.calls, 0)
        XCTAssertFalse(built.refreshing)
        await stop(task)
    }
}

/// Un lecteur qui fait avancer l'horloge du test pendant sa lecture (une lecture qui « prend du temps »).
private final class ClockAdvancingRefresher: FeedRefresher {
    var clock: TestBox<Int>?
    var advanceBy = 0
    private(set) var calls = 0

    func refresh(miffId: String, readStatuses: Bool) async -> ServerRead {
        calls += 1
        clock?.value += advanceBy
        return .fresh(statuses: [:])
    }
}
// swiftlint:enable line_length
