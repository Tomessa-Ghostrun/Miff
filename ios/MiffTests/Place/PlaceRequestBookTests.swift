import XCTest
@testable import Miff

/// Quand une lecture des demandes est nécessaire, et ce que la liste montre (ticket LIEU-REQ, miroir d'Android `PlaceRequestBookTest`) : une
/// lecture par Miff et par 5 minutes, jamais en boucle après un échec, jamais pendant que l'écouteur du créateur nourrit la liste, et une
/// demande de plus de 30 jours n'est jamais montrée.
final class PlaceRequestBookTests: XCTestCase {
    private let now = TestBox(10_000_000)
    private let miff = testMiffId
    private let other = testOtherMiffId

    private func book() -> PlaceRequestBook {
        let clock = now
        return PlaceRequestBook(nowMillis: { clock.value })
    }

    func testNothingKnownIsLoadingAndNeedsAReading() {
        let book = book()

        XCTAssertEqual(book.view(miffId: miff), .loading)
        XCTAssertTrue(book.needsReading(miffId: miff))
        XCTAssertNil(book.known(miffId: miff))
    }

    func testAReadingInProgressIsNotDoubled() {
        let book = book()

        book.readingStarted(miffId: miff)

        XCTAssertFalse(book.needsReading(miffId: miff))
        XCTAssertEqual(book.view(miffId: miff), .loading, "rien n'est dessiné pendant la première lecture")
    }

    func testAReadingIsReusedFiveMinutesThenReadAgainOnce() {
        let book = book()
        book.readingStarted(miffId: miff)
        book.readingSucceeded(miffId: miff, requests: [testRequest()])

        XCTAssertFalse(book.needsReading(miffId: miff))
        now.value += PlaceRequestBook.ttlMillis - 1
        XCTAssertFalse(book.needsReading(miffId: miff), "sous 5 minutes, ouvrir le Profil de nouveau ne lit rien")
        now.value += 1
        XCTAssertTrue(book.needsReading(miffId: miff))
    }

    func testAnEmptyAnswerIsATrueNoneNotLoading() {
        let book = book()
        book.readingSucceeded(miffId: miff, requests: [])

        XCTAssertEqual(book.view(miffId: miff), .ready([]))
    }

    func testAFailureWaitsThirtySecondsAndKeepsWhatWasDrawn() {
        let book = book()
        book.readingSucceeded(miffId: miff, requests: [testRequest()])
        book.readingFailed(miffId: miff)

        XCTAssertFalse(book.needsReading(miffId: miff), "jamais de boucle après un échec")
        // Une liste qui y était ne disparaît pas parce que le réseau a cligné.
        XCTAssertEqual(book.view(miffId: miff).requests.count, 1)
        now.value += PlaceRequestBook.retryMillis
        XCTAssertTrue(book.needsReading(miffId: miff))
    }

    func testAFailureWithNothingKnownIsUnavailableNotAnInventedNone() {
        let book = book()
        book.readingStarted(miffId: miff)
        book.readingFailed(miffId: miff)

        XCTAssertEqual(book.view(miffId: miff), .unavailable)
    }

    func testNothingNeedsReadingWhileTheCreatorsListenerFeedsTheList() {
        let book = book()
        book.live(miffId: miff, requests: [testRequest()])
        now.value += 10 * PlaceRequestBook.ttlMillis

        XCTAssertFalse(book.needsReading(miffId: miff))
        book.invalidateAll()
        XCTAssertFalse(book.needsReading(miffId: miff), "l'écouteur est la source : rien à lire")
    }

    func testWhenTheListenerStopsWhatItGaveAgesAndIsReadAgain() {
        let book = book()
        book.live(miffId: miff, requests: [testRequest()])

        book.liveStopped(miffId: miff)

        XCTAssertTrue(book.needsReading(miffId: miff))
        XCTAssertEqual(book.view(miffId: miff).requests.count, 1, "ce qui était connu reste dessiné en attendant")
    }

    func testInvalidateAllMakesEveryReadingStaleButNotTheLiveOnes() {
        let book = book()
        book.readingSucceeded(miffId: miff, requests: [])
        book.live(miffId: other, requests: [])

        book.invalidateAll()

        XCTAssertTrue(book.needsReading(miffId: miff))
        XCTAssertFalse(book.needsReading(miffId: other))
    }

    func testRetryInvalidatesOneMiffOnly() {
        let book = book()
        book.readingSucceeded(miffId: miff, requests: [])
        book.readingSucceeded(miffId: other, requests: [])

        book.invalidate(miffId: miff)

        XCTAssertTrue(book.needsReading(miffId: miff))
        XCTAssertFalse(book.needsReading(miffId: other))
    }

    func testARequestOfMoreThanThirtyDaysIsNeverShownButIsKnownToBeDeleted() {
        let book = book()
        let day = 24 * 60 * 60 * 1000
        let old = testRequest(slot: 1, createdAtMillis: now.value - 31 * day)
        let fresh = testRequest(slot: 2, createdAtMillis: now.value - day)
        book.readingSucceeded(miffId: miff, requests: [old, fresh])

        XCTAssertEqual(book.view(miffId: miff).requests.map { $0.id }, [fresh.id])
        XCTAssertEqual(book.known(miffId: miff)?.count, 2, "la liste connue garde l'expirée, pour la supprimer")
        now.value += 30 * day
        XCTAssertEqual(book.view(miffId: miff).requests.count, 0, "l'horloge avance : la fraîche expire à son tour")
    }

    func testTheOrderFollowsTheRole() {
        let book = book()
        let first = testRequest(slot: 1, createdAtMillis: 100)
        let second = testRequest(slot: 2, createdAtMillis: 200)
        book.readingSucceeded(miffId: miff, requests: [first, second])

        XCTAssertEqual(book.view(miffId: miff, creator: true).requests.map { $0.id }, [first.id, second.id])
        XCTAssertEqual(book.view(miffId: miff, creator: false).requests.map { $0.id }, [second.id, first.id])
    }

    func testARequestThatLeavesIsRemovedAtOnceAndOneJustSentAppearsAtOnce() {
        let book = book()
        let existing = testRequest(slot: 1)
        book.readingSucceeded(miffId: miff, requests: [existing])

        book.add(miffId: miff, request: testRequest(slot: 2, createdAtMillis: nil))
        XCTAssertEqual(book.view(miffId: miff).requests.count, 2)

        book.remove(miffId: miff, requestId: existing.id)
        XCTAssertEqual(book.view(miffId: miff).requests.map { $0.slot }, [2])
    }

    func testAddingToAnUnknownMiffStartsItsList() {
        let book = book()

        book.add(miffId: miff, request: testRequest())

        XCTAssertEqual(book.view(miffId: miff).requests.count, 1)
    }

    func testForgetAndRetainOnlyTakeTheRequestsOfAMiffThatIsGone() {
        let book = book()
        book.readingSucceeded(miffId: miff, requests: [testRequest()])
        book.readingSucceeded(miffId: other, requests: [testRequest(miffId: other)])

        book.forget(miffId: miff)
        XCTAssertEqual(book.view(miffId: miff), .loading)

        book.retainOnly(miffIds: [])
        XCTAssertEqual(book.view(miffId: other), .loading, "rien d'une Miff quittée ne reste")
    }
}
