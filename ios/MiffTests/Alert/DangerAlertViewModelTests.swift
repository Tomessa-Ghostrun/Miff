// Quelques lignes appellent des fabriques à nombreux paramètres : elles dépassent la limite.
// swiftlint:disable line_length
#if ALERT_DANGER
import XCTest
@testable import Miff

private final class FakeMembersReader: DangerAlertMembersReader {
    var members: [MiffMember]?
    private(set) var reads = 0

    init(_ members: [MiffMember]?) {
        self.members = members
    }

    func members(miffId: String) async -> [MiffMember]? {
        reads += 1
        return members
    }
}

/// L'écran « avertis » et la bulle bleue (ticket 7.1, 8, AD7 à AD9, AD11 à AD17 ; revue de l'Android M2, M3) : les numéros d'urgence sont là DÈS le premier instant, les proches
/// s'y ajoutent, un choix de la personne n'est jamais remplacé ; l'indicateur de la bulle est local ; les phrases ne laissent jamais un blanc.
@MainActor
final class DangerAlertViewModelTests: XCTestCase {
    private let miffA = "aaaaaaaaaaaaaaaaaaaa"
    private let store = FakeDangerStore()
    private let local = MemoryDangerLocalStore()
    private let signal = DangerAlertSignal()

    private func member(_ uid: String, _ first: String, role: MiffRole = .member, phone: String? = "+33611111111") -> MiffMember {
        MiffMember(uid: uid, firstName: first, lastName: "Nom", avatarJpeg: nil, role: role, sharingEnabled: true, phone: phone)
    }

    private func makeModel(_ members: [MiffMember]?, region: String? = "FR") -> DangerAlertViewModel {
        let controller = DangerAlertController(
            store: store, device: FakeActiveDevice(), network: FakeDangerNetwork(), local: local,
            currentUid: { "me" }, sleep: { _ in await Task.yield() }
        )
        return DangerAlertViewModel(
            controller: controller, local: local, members: FakeMembersReader(members),
            myUid: { "me" }, region: { region }, signal: signal
        )
    }

    // MARK: - La liste, tout de suite

    func testTheEmergencyNumbersAreThereAtTheVeryFirstInstantWith112Chosen() {
        let model = makeModel([member("c", "Claire", role: .owner), member("me", "Moi")])

        XCTAssertTrue(model.call.loading)
        XCTAssertEqual(model.call.list.emergency.map { $0.number }, ["17", "15", "18", "112"])
        XCTAssertTrue(model.call.list.people.isEmpty)
        XCTAssertEqual(model.call.selected.number, "112")
        XCTAssertNotNil(model.dialURL(for: model.call.selected))
    }

    func testThePeopleJoinTheListOnceReadAndTheCreatorBecomesTheChoice() async {
        let model = makeModel([member("c", "Claire", role: .owner), member("a", "Anna"), member("me", "Moi")])
        model.loadCallList(miffId: miffA)

        XCTAssertEqual(model.call.selected.number, "112", "pendant la lecture, les secours sont déjà le choix")
        await eventually { !model.call.loading }

        XCTAssertEqual(model.call.list.people.map { $0.name }, ["Claire", "Anna"])
        XCTAssertEqual(model.call.selected, .person(model.call.list.people[0]))
        XCTAssertFalse(model.call.contactsUnavailable)
    }

    func testAChoiceMadeWhileThePeopleLoadIsNeverReplaced() async {
        let model = makeModel([member("c", "Claire", role: .owner), member("me", "Moi")])
        model.loadCallList(miffId: miffA)
        model.selectEmergency()
        let chosen = model.call.selected

        await eventually { !model.call.loading }

        XCTAssertEqual(model.call.selected, chosen)
    }

    func testWhenTheMembersCannotBeReadOnlyTheEmergencyServicesAreOfferedWithACalmSentence() async {
        let model = makeModel(nil)
        model.loadCallList(miffId: miffA)

        await eventually { !model.call.loading }

        XCTAssertTrue(model.call.contactsUnavailable)
        XCTAssertTrue(model.call.list.people.isEmpty)
        XCTAssertEqual(model.call.selected.number, "112")
    }

    func testWithoutAnyKnownMiffOnlyTheNumbersAreShownAtOnce() {
        let model = makeModel([member("c", "Claire", role: .owner)])
        model.loadCallList(miffId: nil)

        XCTAssertFalse(model.call.loading)
        XCTAssertTrue(model.call.list.people.isEmpty)
        XCTAssertEqual(model.call.list.emergency.count, 4)
    }

    func testTheRegionOfThePhoneChoosesTheNumbers() {
        XCTAssertEqual(makeModel([], region: "DE").call.list.emergency.map { $0.number }, ["112"])
        XCTAssertEqual(makeModel([], region: nil).call.selected.number, "112")
    }

    func testOnlyALineOfTheListCanBeChosenAndTheDialUrlIsTheSystemDiallerOnly() async {
        let model = makeModel([member("c", "Claire", role: .owner), member("me", "Moi")])
        model.loadCallList(miffId: miffA)
        await eventually { !model.call.loading }

        model.select(.emergency(EmergencyNumber(label: .police, number: "17")))
        XCTAssertEqual(model.dialURL(for: model.call.selected)?.absoluteString, "tel:17")
        model.select(.emergency(EmergencyNumber(label: .police, number: "999")))
        XCTAssertEqual(model.call.selected.number, "17", "une ligne hors liste est refusée")
        model.select(.person(model.call.list.people[0]))
        XCTAssertEqual(model.dialURL(for: model.call.selected)?.absoluteString, "tel:+33611111111")
        let bad = AlertContact(uid: "x", name: "Bad", phone: "0612345678;rm")
        XCTAssertNil(model.dialURL(for: .person(bad)))
    }

    // MARK: - La bulle bleue

    func testTheHintIsLocalAndDoneForGoodOnceTheGestureWasDiscovered() {
        let model = makeModel([])

        XCTAssertFalse(model.hintDone)
        model.onHintDone()
        XCTAssertTrue(model.hintDone)
        XCTAssertTrue(local.hintDone())
        XCTAssertTrue(makeModel([]).hintDone, "relu depuis l'appareil au relancement")
    }

    func testDeclaringAnAlertValidatesTheHintToo() {
        let model = makeModel([])
        store.startResults = [.failed(.offline)]

        XCTAssertEqual(model.onTriggered(miffId: miffA), .started)
        XCTAssertTrue(model.hintDone)
        model.controller.onSessionEnded()
    }

    // MARK: - Les phrases et le signal

    func testTheEndNoticeNamesTheMiffOrSaysYourPeopleNeverABlank() {
        let model = makeModel([])
        model.miffNames = [miffA: "Famille"]

        model.controller.onEvent?(.ended(miffId: miffA))
        XCTAssertEqual(model.notice, L10n.format("alert_ended_notice", "Famille"))
        model.controller.onEvent?(.ended(miffId: "bbbbbbbbbbbbbbbbbbbb"))
        XCTAssertEqual(model.notice, L10n.string("alert_ended_notice_no_name"))
        model.controller.onEvent?(.endNotSent)
        XCTAssertEqual(model.notice, L10n.string("alert_end_not_sent"))
    }

    func testAStoppedSharingOpensTheCalmScreenOfThatMiff() {
        let model = makeModel([])

        model.controller.onEvent?(.notSent(miffId: miffA, failure: .sharingStopped))

        XCTAssertEqual(model.sentRoute, DangerSentRoute(miffId: miffA, stopped: true, unavailable: false))
    }

    func testTheSignalFollowsTheAlertForTheShellAndTheMiffScreen() async {
        let model = makeModel([])
        store.startResults = [.started(startedAtMillis: 1, expiresAtMillis: Int.max / 2)]
        XCTAssertFalse(signal.inProgress)

        _ = model.onTriggered(miffId: miffA)
        await eventually { self.signal.inProgress && self.signal.miffId == self.miffA }

        XCTAssertEqual(
            dangerAlertWarning("Base", miffId: miffA, signal: signal),
            "Base\n\n" + L10n.string("miff_alert_removed_warning")
        )
        XCTAssertEqual(dangerAlertWarning("Base", miffId: "bbbbbbbbbbbbbbbbbbbb", signal: signal), "Base")
        model.controller.onSessionEnded()
        await eventually { !self.signal.inProgress }
        XCTAssertEqual(dangerAlertWarning("Base", miffId: miffA, signal: signal), "Base")
    }
}
#endif
// swiftlint:enable line_length
