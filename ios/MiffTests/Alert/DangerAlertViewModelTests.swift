// Quelques lignes appellent des fabriques à nombreux paramètres : elles dépassent la limite.
// swiftlint:disable line_length
#if ALERT_DANGER
import Combine
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

    // MARK: - Aucune boucle de rendu, une lecture refaite, un nom blanc (revue de l'Android, v2 et v3)

    func testSelectingTheSameLineEmitsNothingAndAnEmergencyAlreadyChosenIsNeverReplaced() async {
        let model = makeModel([member("c", "Claire", role: .owner), member("me", "Moi")])
        model.loadCallList(miffId: miffA)
        await eventually { !model.call.loading }
        model.selectEmergency()
        XCTAssertEqual(model.call.selected.number, "112")
        var emissions = 0
        let subscription = model.$call.dropFirst().sink { _ in emissions += 1 }

        model.selectEmergency()
        model.selectEmergency()
        model.select(model.call.selected)

        XCTAssertEqual(emissions, 0, "l'écran de la limite relançait son effet sans fin quand un nouvel état était émis pour le même choix")
        model.select(.emergency(EmergencyNumber(label: .police, number: "17")))
        XCTAssertEqual(emissions, 1)
        model.selectEmergency()
        XCTAssertEqual(model.call.selected.number, "17", "un numéro d'urgence déjà choisi n'est jamais remplacé")
        XCTAssertEqual(emissions, 1)
        subscription.cancel()
    }

    func testTheLimitScreenStaysOnTheEmergencyServicesEvenWhenThePeopleArriveAfterwards() async {
        let model = makeModel([member("c", "Claire", role: .owner), member("me", "Moi")])
        model.loadCallList(miffId: miffA)
        XCTAssertTrue(model.call.loading)

        model.selectEmergency()
        await eventually { !model.call.loading }

        XCTAssertEqual(model.call.selected.number, "112", "les secours étaient déjà le choix : l'arrivée du créateur ne le remplace pas")
    }

    func testAFailedReadOfThePeopleIsReadAgainWhenTheScreenOpensAgainForTheSameMiff() async {
        let reader = FakeMembersReader(nil)
        let controller = DangerAlertController(
            store: store, device: FakeActiveDevice(), network: FakeDangerNetwork(), local: local,
            currentUid: { "me" }, sleep: { _ in await Task.yield() }
        )
        let model = DangerAlertViewModel(
            controller: controller, local: local, members: reader,
            myUid: { "me" }, region: { "FR" }, signal: signal
        )
        model.loadCallList(miffId: miffA)
        await eventually { !model.call.loading }
        XCTAssertTrue(model.call.contactsUnavailable)

        reader.members = [member("c", "Claire", role: .owner), member("me", "Moi")]
        model.loadCallList(miffId: miffA)
        await eventually { !model.call.loading }

        XCTAssertEqual(reader.reads, 2)
        XCTAssertFalse(model.call.contactsUnavailable)
        XCTAssertEqual(model.call.list.people.map { $0.name }, ["Claire"])
        model.loadCallList(miffId: miffA)
        XCTAssertEqual(reader.reads, 2, "une lecture réussie n'est pas refaite")
    }

    func testABlankMiffNameIsNoNameAndTheSentencesFallBackToYourMiff() {
        XCTAssertNil(dangerNamed(nil))
        XCTAssertNil(dangerNamed(""))
        XCTAssertNil(dangerNamed("   \n"))
        XCTAssertEqual(dangerNamed("  Famille "), "Famille")
        let model = makeModel([])
        model.miffNames = [miffA: "   "]

        model.controller.onEvent?(.ended(miffId: miffA))

        XCTAssertEqual(model.notice, L10n.string("alert_ended_notice_no_name"))
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

    func testTheSignalTellsWhenTheSentScreenAndTheCalmLineAreOnScreenEvenWithoutAnAlertInProgress() {
        // L'offre Miff+ périodique ne vient jamais par-dessus ces écrans : « partage arrêté » et « indisponible » existent alors que l'alerte n'est plus « en cours ».
        let model = makeModel([])
        XCTAssertFalse(signal.sentScreenOpen)
        XCTAssertFalse(signal.noticeShown)

        model.controller.onEvent?(.notSent(miffId: miffA, failure: .sharingStopped))
        XCTAssertTrue(signal.sentScreenOpen)
        XCTAssertFalse(signal.inProgress)
        XCTAssertTrue(PeriodicPaywallSituation.ownAlertHolds(inProgress: signal.inProgress, sentScreenOpen: signal.sentScreenOpen))
        model.sentRoute = nil
        XCTAssertFalse(signal.sentScreenOpen)

        model.openSent(miffId: miffA, unavailable: true)
        XCTAssertTrue(signal.sentScreenOpen)
        model.sentRoute = nil

        model.controller.onEvent?(.endNotSent)
        XCTAssertTrue(signal.noticeShown)
        model.notice = nil
        XCTAssertFalse(signal.noticeShown)
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
