import XCTest
@testable import Miff

/// Les numéros d'urgence par région de la locale du téléphone (ticket 8.4, AD15) et la liste de l'écran « avertis » (8.2 et 8.3, AD11 à AD14, AD17).
final class EmergencyCatalogTests: XCTestCase {
    private func numbers(_ region: String?) -> [String] {
        EmergencyCatalog.forRegion(region).map { $0.number }
    }

    func testFranceGetsTheFourFrenchNumbersAndItsOverseasDepartmentsToo() {
        XCTAssertEqual(numbers("FR"), ["17", "15", "18", "112"])
        for region in ["GP", "MQ", "GF", "RE", "YT", "fr", " FR "] {
            XCTAssertEqual(numbers(region), ["17", "15", "18", "112"], region)
        }
    }

    func testTheRestOfEuropeGetsTheSingleEuropeanNumber() {
        for region in ["DE", "ES", "BE", "IT", "NO", "IS", "LI", "PT", "IE"] {
            XCTAssertEqual(numbers(region), ["112"], region)
        }
    }

    func testAnUnknownRegionFallsBackToAnHonest112() {
        let regions: [String?] = [nil, "", "ZZ", "US", "??"]
        for region in regions {
            let list = EmergencyCatalog.forRegion(region)
            XCTAssertEqual(list.map { $0.number }, ["112"])
            XCTAssertEqual(list.first?.label, .international112)
        }
    }

    func testEveryNumberOfTheTableIsAShortDialableNumberWithAnExistingText() {
        let all = EmergencyCatalog.all()
        XCTAssertFalse(all.isEmpty)
        for entry in all {
            XCTAssertTrue(EmergencyCatalog.isDialable(entry.number), entry.number)
            XCTAssertNotNil(EmergencyCatalog.dialURL(entry.number))
            let key = entry.label.textKey
            XCTAssertNotEqual(L10n.string(key), key, "texte absent : \(key)")
        }
    }

    func testOnlyAShortNumberReachesTheDialler() {
        for bad in ["", "1", "12345", "+33612345678", "17;", "tel:17", "1 2"] {
            XCTAssertFalse(EmergencyCatalog.isDialable(bad), bad)
            XCTAssertNil(EmergencyCatalog.dialURL(bad), bad)
        }
        XCTAssertEqual(EmergencyCatalog.dialURL("112")?.absoluteString, "tel:112")
    }

    // MARK: - La liste

    private func member(
        _ uid: String,
        _ first: String,
        role: MiffRole = .member,
        phone: String? = "+33611111111"
    ) -> MiffMember {
        MiffMember(
            uid: uid, firstName: first, lastName: "Nom\(uid)", avatarJpeg: nil,
            role: role, sharingEnabled: true, phone: phone
        )
    }

    func testTheCreatorIsPreselectedWhenItIsNotMe() {
        let members = [member("c", "Claire", role: .owner), member("a", "Anna"), member("me", "Moi")]
        let list = AlertCallList.build(members: members, myUid: "me", region: "FR")

        XCTAssertEqual(list.people.map { $0.name }, ["Claire", "Anna"])
        XCTAssertEqual(list.preselected, .person(list.people[0]))
    }

    func testWhenIAmTheCreatorTheFirstOtherMemberByArrivalIsPreselected() {
        let members = [member("me", "Moi", role: .owner), member("a", "Anna"), member("b", "Bea")]
        let list = AlertCallList.build(members: members, myUid: "me", region: "FR")

        XCTAssertEqual(list.people.map { $0.uid }, ["a", "b"])
        XCTAssertEqual(list.preselected, .person(list.people[0]))
    }

    func testAMemberWithoutANumberIsNotListedAndTheNextOneIsChosen() {
        let members = [member("c", "Claire", role: .owner, phone: nil), member("a", "Anna"), member("me", "Moi")]
        let list = AlertCallList.build(members: members, myUid: "me", region: "FR")

        XCTAssertEqual(list.people.map { $0.uid }, ["a"])
        XCTAssertEqual(list.preselected, .person(list.people[0]))
    }

    func testAloneOrNobodyWithANumberLeavesOnlyTheEmergencyServicesWith112Preselected() {
        let cases = [[member("me", "Moi", role: .owner)], [member("me", "Moi"), member("a", "Anna", phone: nil)]]
        for members in cases {
            let list = AlertCallList.build(members: members, myUid: "me", region: "FR")

            XCTAssertTrue(list.people.isEmpty)
            XCTAssertEqual(list.preselected, .emergency(EmergencyNumber(label: .european112, number: "112")))
            XCTAssertEqual(list.targets.count, list.emergency.count)
        }
    }

    func testTheEmergencyRegionComesFromTheLocaleNotFromThePosition() {
        let members = [member("me", "Moi")]

        XCTAssertEqual(AlertCallList.build(members: members, myUid: "me", region: "FR").emergency.count, 4)
        XCTAssertEqual(AlertCallList.build(members: members, myUid: "me", region: "DE").emergency.count, 1)
        XCTAssertEqual(AlertCallList.build(members: members, myUid: "me", region: nil).preselected.number, "112")
    }

    func testTwoHomonymsReceiveTheirFamilyNameToo() {
        let members = [member("c", "Emma", role: .owner), member("d", "emma"), member("me", "Moi")]
        let list = AlertCallList.build(members: members, myUid: "me", region: "FR")

        XCTAssertEqual(list.people.map { $0.name }, ["Emma Nomc", "emma Nomd"])
    }

    func testMyselfIsNeverInTheList() {
        let members = [member("me", "Moi", role: .owner), member("a", "Anna")]
        let list = AlertCallList.build(members: members, myUid: "me", region: "FR")

        XCTAssertFalse(list.people.contains { $0.uid == "me" })
    }

    func testATargetIdIsStableAndAContactNeverShowsItsContent() {
        let list = AlertCallList.build(members: [member("a", "Anna"), member("me", "Moi")], myUid: "me", region: "FR")

        XCTAssertEqual(list.people[0].uid, "a")
        XCTAssertEqual(AlertDialTarget.person(list.people[0]).id, "p:a")
        XCTAssertFalse("\(list.people[0])".contains("Anna"))
        XCTAssertFalse("\(list.people[0])".contains("+3361"))
        XCTAssertFalse("\(list)".contains("Anna"))
    }
}
