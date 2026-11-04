import XCTest
@testable import Miff

/// Qui a quitté la Miff depuis la dernière image du créateur (critère N3, ticket §10 cas 22).
final class MemberDeparturesTests: XCTestCase {
    private let me = "uid-owner"

    func testWithNoEarlierPictureThereIsNoDepartureSoNoInvitationForOldDeparturesOnANewPhone() {
        let result = MemberDepartures.between(
            previous: nil, current: [testMember(me)], myUid: me, removedByMe: []
        )

        XCTAssertTrue(result.isEmpty)
    }

    func testSomeoneWhoDisappearsIsADeparture() {
        let before = [testMember(me), testMember("a"), testMember("b")]
        let after = [testMember(me), testMember("b")]

        XCTAssertEqual(
            MemberDepartures.between(previous: before, current: after, myUid: me, removedByMe: []),
            ["a"]
        )
    }

    func testAPersonTheCreatorJustRemovedIsNotADeparture() {
        let before = [testMember(me), testMember("a")]

        let result = MemberDepartures.between(
            previous: before, current: [testMember(me)], myUid: me, removedByMe: ["a"]
        )

        XCTAssertTrue(result.isEmpty)
    }

    func testTheCreatorsOwnLineAndNewArrivalsNeverCount() {
        let before = [testMember(me), testMember("a")]
        let after = [testMember("a"), testMember("c")]

        let result = MemberDepartures.between(previous: before, current: after, myUid: me, removedByMe: [])

        XCTAssertTrue(result.isEmpty)
    }

    func testNoChangeIsNoDeparture() {
        let same = [testMember(me), testMember("a")]

        let result = MemberDepartures.between(previous: same, current: same, myUid: me, removedByMe: [])

        XCTAssertTrue(result.isEmpty)
    }

    func testTheNameUsedInTheQuestionsIsTheFirstNameElseTheLastName() {
        let normal = MiffMember(
            uid: "a", firstName: " Camille ", lastName: "Durand", avatarJpeg: nil, role: .member, sharingEnabled: true
        )
        let noFirstName = MiffMember(
            uid: "b", firstName: "  ", lastName: "Durand", avatarJpeg: nil, role: .member, sharingEnabled: true
        )

        XCTAssertEqual(normal.callName, "Camille")
        XCTAssertEqual(noFirstName.callName, "Durand")
        let blank = MiffMember(
            uid: "c", firstName: " ", lastName: "  ", avatarJpeg: nil, role: .member, sharingEnabled: true
        )
        XCTAssertEqual(blank.callName, "", "la question utilise alors une formule neutre")
    }
}
