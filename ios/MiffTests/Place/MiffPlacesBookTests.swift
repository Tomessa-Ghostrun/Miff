import XCTest
@testable import Miff

private final class BookClock {
    var now = 1_000_000
}

/// Quand une lecture des lieux d'une Miff est nécessaire (le COÛT Firestore est ce qu'on prouve ici) : jamais pour la Miff du Feed, au plus
/// une fois par Miff et par 5 minutes pour les autres, jamais en boucle après un échec, une relecture après un passage du créateur.
final class MiffPlacesBookTests: XCTestCase {
    private let clock = BookClock()

    private func makeBook() -> MiffPlacesBook {
        let clock = self.clock
        return MiffPlacesBook(clock: { clock.now })
    }

    private func places(_ ids: [String]) -> [Place] {
        ids.map { testPlace(id: $0, name: "Lieu \($0)") }
    }

    // MARK: - Rien n'est connu

    func testNothingKnownMeansNothingDrawnAndAReadingIsNeeded() {
        let book = makeBook()

        XCTAssertEqual(book.view("A"), .loading)
        XCTAssertTrue(book.needsReading("A"))
    }

    // MARK: - La Miff du Feed : zéro lecture

    func testWhatTheFeedReadIsDrawnWithoutAnyReadingAndNeverAges() {
        var book = makeBook()
        book.seedFromFeed(miffId: "A", places: places(["p1"]))
        clock.now += 3 * 60 * 60 * 1000

        XCTAssertEqual(book.view("A"), .ready(places(["p1"])))
        XCTAssertFalse(book.needsReading("A"))
    }

    func testAnEmptyListGivenByTheFeedIsATrueNoPlace() {
        var book = makeBook()
        book.seedFromFeed(miffId: "A", places: [])

        XCTAssertEqual(book.view("A"), .ready([]))
        XCTAssertFalse(book.needsReading("A"))
    }

    // MARK: - Une lecture, réutilisée 5 minutes

    func testWhatWasReadIsReusedForFiveMinutesThenReadAgain() {
        var book = makeBook()
        book.readingStarted("B")
        book.readingSucceeded("B", places: places(["p1", "p2"]))

        clock.now += MiffPlacesBook.freshMillis - 1
        XCTAssertFalse(book.needsReading("B"))
        clock.now += 1
        XCTAssertTrue(book.needsReading("B"))
    }

    func testNoSecondReadingStartsWhileOneIsUnderWayAndTheListKeepsWhatItShowed() {
        var book = makeBook()
        book.seedFromFeed(miffId: "A", places: places(["p1"]))
        book.invalidateAll()
        XCTAssertTrue(book.needsReading("A"))

        book.readingStarted("A")

        XCTAssertFalse(book.needsReading("A"))
        XCTAssertEqual(book.view("A"), .ready(places(["p1"])))
    }

    // MARK: - Un échec : jamais de boucle

    func testAFailedReadingWaitsThirtySecondsAndShowsUnavailableWhenNothingWasDrawn() {
        var book = makeBook()
        book.readingStarted("B")
        book.readingFailed("B")

        XCTAssertEqual(book.view("B"), .unavailable)
        clock.now += MiffPlacesBook.retryMillis - 1
        XCTAssertFalse(book.needsReading("B"))
        clock.now += 1
        XCTAssertTrue(book.needsReading("B"))
    }

    func testAListThatWasDrawnDoesNotVanishBecauseTheNetworkBlinked() {
        var book = makeBook()
        book.readingStarted("B")
        book.readingSucceeded("B", places: places(["p1"]))
        clock.now += MiffPlacesBook.freshMillis
        book.readingStarted("B")
        book.readingFailed("B")

        XCTAssertEqual(book.view("B"), .ready(places(["p1"])))
    }

    // MARK: - Le créateur a touché un lieu

    func testInvalidateAllMakesEveryListReadAgainEvenTheFeedOne() {
        var book = makeBook()
        book.seedFromFeed(miffId: "A", places: places(["p1"]))
        book.readingStarted("B")
        book.readingSucceeded("B", places: places(["p2"]))

        book.invalidateAll()

        XCTAssertTrue(book.needsReading("A"))
        XCTAssertTrue(book.needsReading("B"))
    }

    func testWhatTheFeedHearsAfterAnInvalidationClearsTheMark() {
        var book = makeBook()
        book.seedFromFeed(miffId: "A", places: places(["p1"]))
        book.invalidateAll()

        book.seedFromFeed(miffId: "A", places: places(["p1", "p9"]))

        XCTAssertFalse(book.needsReading("A"))
        XCTAssertEqual(book.view("A"), .ready(places(["p1", "p9"])))
    }

    func testAReadingThatStartedBeforeAnInvalidationDoesNotClearTheMark() {
        var book = makeBook()
        book.readingStarted("B")
        book.invalidateAll()
        book.readingSucceeded("B", places: places(["p1"]))

        // Ce que la lecture apporte peut dater d'avant le changement : la prochaine apparition relit.
        XCTAssertTrue(book.needsReading("B"))
        XCTAssertEqual(book.view("B"), .ready(places(["p1"])))
    }

    func testRetryReadsAtOnceWithoutWaitingForTheThirtySeconds() {
        var book = makeBook()
        book.readingStarted("B")
        book.readingFailed("B")
        XCTAssertFalse(book.needsReading("B"))

        book.invalidate("B")

        XCTAssertTrue(book.needsReading("B"))
    }

    // MARK: - Une Miff qui disparaît

    func testAMiffThatIsNoLongerInTheListTakesItsPlacesWithIt() {
        var book = makeBook()
        book.seedFromFeed(miffId: "A", places: places(["p1"]))
        book.readingStarted("B")
        book.readingSucceeded("B", places: places(["p2"]))

        book.retainOnly(["A"])

        XCTAssertEqual(book.view("A"), .ready(places(["p1"])))
        XCTAssertEqual(book.view("B"), .loading)
    }

    // MARK: - Qui est le créateur

    func testOnlyTheKnownCreatorIsACreatorAndADoubtIsAMember() {
        XCTAssertTrue(PlaceViewerRole.isCreator(ownerUid: "u1", myUid: "u1"))
        XCTAssertFalse(PlaceViewerRole.isCreator(ownerUid: "u1", myUid: "u2"))
        XCTAssertFalse(PlaceViewerRole.isCreator(ownerUid: nil, myUid: "u1"), "créateur inconnu")
        XCTAssertFalse(PlaceViewerRole.isCreator(ownerUid: "u1", myUid: nil), "personne inconnue")
        XCTAssertFalse(PlaceViewerRole.isCreator(ownerUid: "", myUid: ""), "deux valeurs vides ne font pas un créateur")
        XCTAssertFalse(PlaceViewerRole.isCreator(ownerUid: nil, myUid: nil))
    }
}
