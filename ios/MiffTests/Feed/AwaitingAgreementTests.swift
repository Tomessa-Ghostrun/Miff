import XCTest
@testable import Miff

// Les Miffs qui attendent l'accord de la personne (LOC-2b, maquette 23) : en mémoire seulement, vidées à la fin de la session.
@MainActor
final class AwaitingAgreementTests: XCTestCase {
    func testItHoldsOnlyIdentifiersAndForgetsThemAtTheEndOfTheSession() {
        let awaiting = AwaitingAgreement()

        awaiting.replace([testMiffId, testOtherMiffId])
        awaiting.remove(testOtherMiffId)
        XCTAssertEqual(awaiting.waiting, [testMiffId])

        awaiting.stop()
        XCTAssertTrue(awaiting.waiting.isEmpty)
    }

    func testAskingToReviewIsCountedSoTheGateCanListenToIt() {
        let awaiting = AwaitingAgreement()

        awaiting.requestReview()
        awaiting.requestReview()

        XCTAssertEqual(awaiting.reviewRequests, 2)
    }
}
