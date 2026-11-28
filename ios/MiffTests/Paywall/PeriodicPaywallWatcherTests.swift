import XCTest
@testable import Miff

/// Le regard sur les règles (ticket `docs/tickets/paywall-periodique.md`, PP9, PP15, PP20, PP12) : il ouvre l'offre quand tout est bon et seulement alors, attend les 1,5 s, relit
/// la situation à chaque tour (et une dernière fois avant d'ouvrir), n'ouvre jamais depuis l'arrière-plan ni par-dessus une présentation du système. L'attente est injectée : elle
/// fait avancer l'horloge au lieu de durer. Miroir de `PeriodicPaywallWatcherTest` d'Android.
@MainActor
final class PeriodicPaywallWatcherTests: XCTestCase {
    /// Tout ce qui change pendant un test, dans une boîte simple (sans isolation) : les fermetures données au regard ne touchent jamais l'acteur principal.
    private final class Stage {
        let clock = PeriodicClockBox()
        var situation = calmSituation()
        var active = true
        var modal = false
        var opened = 0
        var openedAt: Int?
        var sleeps: [Int] = []
        /// Appelé à chaque attente, avec le nombre d'attentes déjà faites : l'écran change PENDANT l'attente.
        var duringSleep: (Int) -> Void = { _ in }
    }

    private let scene = Stage()
    private let store = MemoryPeriodicStore()

    private func makeRules(counter: Int = 9) -> PeriodicPaywallRules {
        store.value = counter
        let box = scene.clock
        return PeriodicPaywallRules(store: store, clock: { box.now })
    }

    /// Un démarrage à froid, puis un premier appui.
    private func firstTap(_ rules: PeriodicPaywallRules) {
        rules.onForeground()
        rules.onShellShown()
        rules.onTouch(pressed: true)
        rules.onActivation()
        rules.onTouch(pressed: false)
    }

    private func makeWatcher(_ rules: PeriodicPaywallRules) -> PeriodicPaywallWatcher {
        let scene = self.scene
        return PeriodicPaywallWatcher(
            rules: rules,
            situation: { scene.situation },
            appIsActive: { scene.active },
            modalPresented: { scene.modal },
            sleep: { millis in
                scene.sleeps.append(millis)
                scene.clock.advance(millis)
                scene.duringSleep(scene.sleeps.count)
            },
            open: {
                scene.opened += 1
                scene.openedAt = scene.clock.now
            }
        )
    }

    func testTheOfferOpensOnceAfterTheFirstTapAndOneAndAHalfSecondsOfCalm() async {
        let rules = makeRules()
        firstTap(rules)

        let opened = await makeWatcher(rules).look()

        XCTAssertTrue(opened)
        XCTAssertEqual(scene.opened, 1)
        XCTAssertEqual(scene.sleeps, [1_500], "une seule attente : le moment calme")
        XCTAssertEqual(store.value, 10, "le compteur ne retombe qu'à l'affichage effectif, pas à l'ouverture")
    }

    func testNothingOpensWithoutAFirstTap() async {
        let rules = makeRules()
        rules.onForeground()
        rules.onShellShown()

        let opened = await makeWatcher(rules).look()

        XCTAssertFalse(opened)
        XCTAssertEqual(scene.opened, 0)
        XCTAssertEqual(scene.sleeps, [])
    }

    func testNothingOpensFromTheBackgroundAndTheCounterIsLeftAlone() async {
        let rules = makeRules()
        firstTap(rules)
        scene.active = false

        let opened = await makeWatcher(rules).look()

        XCTAssertFalse(opened)
        XCTAssertEqual(scene.opened, 0)
        XCTAssertEqual(store.value, 10)
    }

    func testAnOfferNeverComesOverASystemPresentationAndWaitsAtLeastOneAndAHalfSecondsAfterItCloses() async {
        let rules = makeRules()
        firstTap(rules)
        let scene = self.scene
        scene.clock.advance(PeriodicPaywallDefaults.quietAfterTouchMillis)
        scene.modal = true
        var closedAt = 0
        scene.duringSleep = { count in
            // La feuille se ferme pendant la troisième attente.
            if count == 3 {
                scene.modal = false
                closedAt = scene.clock.now
            }
        }

        let opened = await makeWatcher(rules).look()

        XCTAssertTrue(opened)
        XCTAssertEqual(scene.opened, 1)
        let waited = (scene.openedAt ?? 0) - closedAt
        let quiet = PeriodicPaywallDefaults.quietAfterTouchMillis
        let poll = PeriodicPaywallDefaults.modalPollMillis
        XCTAssertGreaterThanOrEqual(waited, quiet, "au moins 1,5 s après la fermeture")
        XCTAssertLessThanOrEqual(waited, quiet + poll)
        XCTAssertEqual(Set(scene.sleeps.prefix(3)), [poll], "regardée à chaque intervalle tant qu'elle est là")
    }

    func testMiffPlusAppearingDuringTheWaitCancelsTheOpening() async {
        let rules = makeRules()
        firstTap(rules)
        let scene = self.scene
        scene.duringSleep = { _ in scene.situation.miffPlus = true }

        let opened = await makeWatcher(rules).look()

        XCTAssertFalse(opened)
        XCTAssertEqual(scene.opened, 0)
        XCTAssertEqual(store.value, 0, "une personne qui a Miff+ repart de zéro")
    }

    func testTheSituationIsReadAgainAtEveryTurnAKeyboardThatOpensDuringTheWaitCancelsTheOpening() async {
        let rules = makeRules()
        firstTap(rules)
        let scene = self.scene
        scene.duringSleep = { _ in scene.situation.keyboardOpen = true }

        let opened = await makeWatcher(rules).look()

        XCTAssertFalse(opened)
        XCTAssertEqual(scene.opened, 0)
        XCTAssertEqual(store.value, 10, "le compteur est laissé tranquille : l'offre reste due")
    }

    func testANewTouchDuringTheWaitStartsTheWaitAgain() async {
        let rules = makeRules()
        firstTap(rules)
        let scene = self.scene
        scene.duringSleep = { count in
            if count == 1 {
                rules.onTouch(pressed: true)
                rules.onTouch(pressed: false)
            }
        }

        let opened = await makeWatcher(rules).look()

        XCTAssertTrue(opened)
        XCTAssertEqual(scene.sleeps, [1_500, 1_500])
    }

    func testTheOfferOpensOnlyOncePerSession() async {
        let rules = makeRules()
        firstTap(rules)
        let watcher = makeWatcher(rules)

        let first = await watcher.look()
        let second = await watcher.look()

        XCTAssertTrue(first)
        XCTAssertFalse(second)
        XCTAssertEqual(scene.opened, 1)
    }

    func testACancelledWatchOpensNothing() async {
        let rules = makeRules()
        firstTap(rules)
        let watcher = makeWatcher(rules)

        let task = Task { await watcher.look() }
        task.cancel()
        let opened = await task.value

        XCTAssertFalse(opened)
        XCTAssertEqual(scene.opened, 0)
    }
}
