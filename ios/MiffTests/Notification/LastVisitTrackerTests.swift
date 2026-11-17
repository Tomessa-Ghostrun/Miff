import XCTest
@testable import Miff

/// « Depuis ta dernière visite » côté appareil : quand la comparaison a lieu (ouverture à froid, retour après 30 minutes), ce qui est gardé
/// (l'image de ce qui était à l'écran, même après une fermeture forcée), ce qui est effacé (déconnexion, Miff perdue, membre parti).
/// Miroir de `LastVisitTrackerTest.kt` d'Android.
@MainActor
final class LastVisitTrackerTests: XCTestCase {
    private typealias Fixture = VisitFixtures
    private let hour = VisitFixtures.hour
    private let minute = VisitFixtures.minute
    private var clock = VisitFixtures.now
    private var uid: String? = "uid-1"
    private let centerStore = MemoryNotificationCenterStore()
    private let visitStore = MemoryLastVisitStore()

    private func makeCenter() -> NotificationCenterService {
        NotificationCenterService(
            store: centerStore, currentUid: { [unowned self] in self.uid }, nowMillis: { [unowned self] in self.clock }
        )
    }

    private func makeTracker(center: NotificationCenterService) -> LastVisitTracker {
        LastVisitTracker(
            store: visitStore, center: center, currentUid: { [unowned self] in self.uid },
            nowMillis: { [unowned self] in self.clock }
        )
    }

    /// Avant : Thomas est sorti il y a 5 h. Après : il est arrivé à la maison il y a 1 h.
    private var before: VisitState {
        Fixture.state([Fixture.member("t", .out, .home, presenceAt: clock - 5 * hour)])
    }

    private var arrived: VisitState {
        Fixture.state([Fixture.member("t", .inTrusted, .home, presenceAt: clock - hour)])
    }

    private func seedLastVisit() {
        visitStore.seed(LastVisitMemory(states: [Fixture.miff: before], lastMiffId: Fixture.miff), uid: "uid-1")
    }

    // MARK: - La comparaison

    func testTheFirstVisitOnThisPhoneSaysNothingAndKeepsThePicture() {
        let center = makeCenter()
        let tracker = makeTracker(center: center)

        tracker.onFreshPicture(myUid: "uid-1", state: arrived)

        XCTAssertTrue(center.history.visitItems.isEmpty)
        XCTAssertEqual(visitStore.memory.states[Fixture.miff], arrived)
        XCTAssertEqual(visitStore.memory.lastMiffId, Fixture.miff)
        XCTAssertEqual(visitStore.storedUid, "uid-1")
    }

    func testTheNextOpeningWritesTheLinesUnreadAndWithoutAnyNotification() {
        seedLastVisit()
        let center = makeCenter()
        let tracker = makeTracker(center: center)

        tracker.onFreshPicture(myUid: "uid-1", state: arrived)

        XCTAssertEqual(center.history.visitItems.count, 1)
        XCTAssertEqual(center.history.visitItems.first?.state, .silent)
        XCTAssertEqual(center.history.visitItems.first.flatMap { LastVisit.parse($0)?.kind }, .arrival)
        XCTAssertTrue(center.hasUnread, "le point de la cloche, jamais un chiffre ni une notification")
        XCTAssertEqual(visitStore.memory.states[Fixture.miff], arrived)
    }

    func testWhatWasSeenIsNotToldAgainWhileTheAppStaysOpen() {
        seedLastVisit()
        let center = makeCenter()
        let tracker = makeTracker(center: center)
        tracker.onFreshPicture(myUid: "uid-1", state: arrived)
        let first = center.history.visitItems.map { $0.id }
        let later = Fixture.state([
            Fixture.member("t", .out, .home, presenceAt: clock - 10 * minute)
        ])

        tracker.onFreshPicture(myUid: "uid-1", state: later)

        // La même ouverture : le groupe n'est pas remplacé ; l'image gardée suit l'écran.
        XCTAssertEqual(center.history.visitItems.map { $0.id }, first)
        XCTAssertEqual(visitStore.memory.states[Fixture.miff], later)
    }

    func testBackToTheFrontAfterThirtyMinutesCompares_butNotBefore() {
        let center = makeCenter()
        let tracker = makeTracker(center: center)
        tracker.onFreshPicture(myUid: "uid-1", state: before)
        tracker.onBackground()
        clock += 29 * minute
        tracker.onForeground()
        tracker.onFreshPicture(myUid: "uid-1", state: arrived)
        XCTAssertTrue(center.history.visitItems.isEmpty, "moins de 30 minutes : aucune comparaison")

        tracker.onBackground()
        clock += 30 * minute
        tracker.onForeground()
        let next = Fixture.state([Fixture.member("t", .out, .home, presenceAt: clock - minute)])
        tracker.onFreshPicture(myUid: "uid-1", state: next)

        XCTAssertEqual(center.history.visitItems.count, 1)
        XCTAssertEqual(center.history.visitItems.first.flatMap { LastVisit.parse($0)?.kind }, .departure)
    }

    func testAForcedClosureKeepsWhatWasOnTheScreen() {
        let center = makeCenter()
        let first = makeTracker(center: center)
        first.onFreshPicture(myUid: "uid-1", state: before)
        first.onFreshPicture(myUid: "uid-1", state: arrived)

        // Le processus est tué, puis relancé : un nouveau suivi, le même fichier.
        let reopened = makeTracker(center: makeCenter())
        let again = Fixture.state([Fixture.member("t", .inTrusted, .home, presenceAt: clock - hour)])
        reopened.onFreshPicture(myUid: "uid-1", state: again)

        XCTAssertTrue(makeCenter().history.visitItems.isEmpty, "ce qui était affiché n'est pas redit")
    }

    func testAnotherActiveMiffAndAReconnectionSayNothing() {
        seedLastVisit()
        let center = makeCenter()
        let tracker = makeTracker(center: center)
        let there = Fixture.member("t", .inTrusted, .home, presenceAt: clock - hour)
        let otherMiff = Fixture.state([there], miffId: "miff-2")

        tracker.onFreshPicture(myUid: "uid-1", state: otherMiff)

        XCTAssertTrue(center.history.visitItems.isEmpty)
        // Chaque Miff garde son image : celle de la première n'est pas perdue.
        XCTAssertEqual(visitStore.memory.states.count, 2)
        XCTAssertEqual(visitStore.memory.lastMiffId, "miff-2")
    }

    func testAPictureForAnotherSessionOrWithNoSessionIsIgnored() {
        seedLastVisit()
        let center = makeCenter()
        let tracker = makeTracker(center: center)

        tracker.onFreshPicture(myUid: "uid-2", state: arrived)
        uid = nil
        tracker.onFreshPicture(myUid: "uid-1", state: arrived)

        XCTAssertTrue(center.history.visitItems.isEmpty)
        XCTAssertEqual(visitStore.saveCalls, 0)
    }

    // MARK: - Ce qui est effacé

    func testAMemberWhoLeftTheMiffKeepsNoLine() {
        seedLastVisit()
        let center = makeCenter()
        let tracker = makeTracker(center: center)
        tracker.onFreshPicture(myUid: "uid-1", state: arrived)
        XCTAssertEqual(center.history.visitItems.count, 1)

        tracker.onFreshPicture(myUid: "uid-1", state: Fixture.state([Fixture.member("autre")]))

        XCTAssertTrue(center.history.visitItems.isEmpty)
    }

    func testAMiffThatIsGoneTakesItsPictureAndItsLinesAndAListFromTheCacheDecidesNothing() {
        seedLastVisit()
        let center = makeCenter()
        let tracker = makeTracker(center: center)
        tracker.onFreshPicture(myUid: "uid-1", state: arrived)

        tracker.onMiffsKnown([Fixture.miff])
        XCTAssertEqual(center.history.visitItems.count, 1)
        XCTAssertNotNil(visitStore.memory.states[Fixture.miff])

        tracker.onMiffsKnown(["miff-9"])
        XCTAssertTrue(center.history.visitItems.isEmpty)
        XCTAssertTrue(visitStore.memory.states.isEmpty)
        XCTAssertNil(visitStore.memory.lastMiffId)
    }

    func testTheSignOutLeavesNothingAndTheNextSessionStartsAsAFirstVisit() {
        seedLastVisit()
        let center = makeCenter()
        let tracker = makeTracker(center: center)
        tracker.onFreshPicture(myUid: "uid-1", state: arrived)

        center.purge()
        tracker.purge()
        let next = makeTracker(center: makeCenter())
        next.onFreshPicture(myUid: "uid-1", state: arrived)

        XCTAssertEqual(visitStore.clearCalls, 1)
        XCTAssertTrue(makeCenter().history.visitItems.isEmpty, "première visite : rien à comparer")
    }

    func testErasingTheSessionAlsoErasesTheLastVisitFile() {
        let memory = NotificationMemory()
        memory.lastVisit.seed(LastVisitMemory(states: [Fixture.miff: before], lastMiffId: Fixture.miff), uid: "uid-1")

        memory.localData.eraseSession()

        XCTAssertNil(memory.lastVisit.storedUid)
        XCTAssertTrue(memory.lastVisit.memory.states.isEmpty)
    }
}
