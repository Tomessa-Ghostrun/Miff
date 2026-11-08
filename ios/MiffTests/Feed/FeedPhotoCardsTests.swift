import XCTest
@testable import Miff

// La photo sur les cartes du Feed (FEED-8, critères P1 à P4) : une photo n'est un CANDIDAT à la lecture que si le statut est montré, dit qu'il y a
// une photo et que la dernière update a moins de 23 h ; un membre qui ne partage plus n'en a aucune ; ma carte (la dernière, D43) a la sienne.
// Miroir des cas de photo de `FeedBuilderTest.kt` d'Android.
final class FeedPhotoCardsTests: XCTestCase {
    private let me = feedMember("me", first: "Camille")
    private let thomas = feedMember("a", first: "Thomas")
    private let lea = feedMember("b", first: "Léa", sharing: false)

    private func feed(
        statuses: [String: MemberStatus],
        members: [MiffMember]? = nil,
        view: StatusesView? = nil,
        waiting: Bool = false
    ) -> FeedContent {
        buildFeed(feedInput(members: members ?? [me, thomas], statuses: statuses, view: view, waiting: waiting))
    }

    func testAMemberWhoSaysThereIsAPhotoIsACandidateForTheReadingWithTheLastUpdate() {
        let content = feed(statuses: ["a": feedPhotoStatus(updatedAgo: feedMinutes(20))])

        XCTAssertEqual(content.card("a")?.photo, .candidate(lastUpdateAtMillis: feedNow - feedMinutes(20)))
    }

    func testNoPhotoInTheStatusMeansNothingIsRead() {
        let content = feed(statuses: ["a": feedPhotoStatus(hasPhoto: false)])

        XCTAssertEqual(content.card("a")?.photo, PhotoSlot.none)
    }

    func testAnUpdateOlderThan23HoursIsNotReadAnyMore() {
        let content = feed(statuses: [
            "a": feedPhotoStatus(updatedAgo: 23 * FeedMillis.hour - 1),
            "me": feedPhotoStatus(updatedAgo: 23 * FeedMillis.hour)
        ])

        XCTAssertEqual(content.card("a")?.photo, .candidate(lastUpdateAtMillis: feedNow - (23 * FeedMillis.hour - 1)))
        XCTAssertEqual(content.card("me")?.photo, PhotoSlot.none, "à 23 h exactement : expirée, rien n'est lu")
    }

    func testAMemberWhoStoppedSharingHasNoPhotoEvenIfAnOldStatusIsStillThere() {
        let content = feed(statuses: ["b": feedPhotoStatus()], members: [me, lea])

        XCTAssertEqual(content.card("b")?.photo, PhotoSlot.none)
    }

    func testWhenIStopSharingTheOthersPhotosAreNotReadAndNotShown() {
        let stopped = feedMember("me", first: "Camille", sharing: false)

        let content = feed(statuses: ["a": feedPhotoStatus()], members: [stopped, thomas])

        XCTAssertEqual(content.card("a")?.photo, PhotoSlot.none)
    }

    func testWhileMyAgreementIsAwaitedTheOthersPhotosAreNotReadButMineIsKept() {
        let content = feed(statuses: ["a": feedPhotoStatus(), "me": feedPhotoStatus()], waiting: true)

        XCTAssertEqual(content.card("a")?.photo, PhotoSlot.none)
        XCTAssertEqual(content.card("me")?.photo, .candidate(lastUpdateAtMillis: feedNow - feedMinutes(20)))
    }

    func testWhileTheStatusesAreNotReadNoPhotoIsRead() {
        let content = feed(statuses: [:], view: .pending)

        XCTAssertEqual(content.card("a")?.photo, PhotoSlot.none)
        XCTAssertEqual(content.card("me")?.photo, PhotoSlot.none)
    }

    func testMyCardHasMyPhotoAndIsTheLastOne() {
        let content = feed(statuses: ["me": feedPhotoStatus(), "a": feedPhotoStatus()])

        XCTAssertEqual(content.cards.map { $0.member.uid }, ["a", "me"], "ma carte est la dernière (D43)")
        XCTAssertEqual(content.cards.last?.photo, .candidate(lastUpdateAtMillis: feedNow - feedMinutes(20)))
    }

    func testTheFeedSaysItIsOfflineSoTheCardsCanRetryWhenTheNetworkIsBack() {
        let online = buildFeed(feedInput(members: [me, thomas], statuses: ["a": feedPhotoStatus()]))
        let offline = buildFeed(feedInput(members: [me, thomas], statuses: ["a": feedPhotoStatus()], offline: true))

        XCTAssertFalse(online.isOffline)
        XCTAssertTrue(offline.isOffline)
    }
}
