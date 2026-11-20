import XCTest
@testable import Miff

/// CNC-I : l'écran du centre avec le journal du serveur et les visages : les lignes de proches restent visibles quand les notifications du téléphone sont
/// coupées (CNC-7), une ligne d'une Miff qui n'est plus la sienne n'est pas dessinée, le toucher mène au Feed sur la carte du proche, supprimer une ligne
/// supprime son entrée sur le serveur (CNC-15), les mots d'une ligne viennent de la même fiche que son image (CNC-10), et sans journal ni visages le centre
/// est celui d'avant.
@MainActor
final class NotificationCenterJournalTests: XCTestCase {
    private let miff = "abcdefghijklmnopqrst"
    private let now = 1_800_000_000_000
    private let updateKey = "n9|u|uidB|u-abcdefghijklmnopqrst-uidB-1"
    private let alertKey = "al|u|uidB|start|as-abcdefghijklmnopqrst-uidB-1"

    private struct Rig {
        let model: NotificationCenterViewModel
        let service: NotificationCenterService
        let faces: CenterFaces
        let gateway: FakeJournalGateway
    }

    private func makeRig(
        _ authorization: NotificationAuthorization = .authorized,
        myMiffs: Set<String>? = nil,
        withJournal: Bool = true
    ) -> Rig {
        let service = NotificationCenterService(
            store: MemoryNotificationCenterStore(), currentUid: { "uidA" }, nowMillis: { [now] in now }
        )
        let faces = CenterFaces(reader: SilentFaceReader())
        let gateway = FakeJournalGateway()
        let journal = NotificationJournalSync(
            gateway: gateway, cursors: MemoryJournalCursorStore(), center: service,
            currentUid: { "uidA" }, nowMillis: { [now] in now }
        )
        let extras = NotificationCenterExtras(
            faces: faces,
            journal: withJournal ? journal : nil,
            isMyMiff: { id in myMiffs?.contains(id) ?? true }
        )
        let model = NotificationCenterViewModel(
            service: service, permissions: NotificationStubPermissions(authorization),
            directory: RequesterDirectory(), extras: extras
        )
        return Rig(model: model, service: service, faces: faces, gateway: gateway)
    }

    private func item(_ id: String, _ type: NotificationType, miffId: String? = nil) -> NotificationItem {
        NotificationFixtures.item(id, type: type, miffId: miffId ?? miff, at: now - 1_000, state: .silent)
    }

    private func member(_ uid: String, _ first: String) -> MiffMember {
        MiffMember(uid: uid, firstName: first, lastName: "", avatarJpeg: nil, role: .member, sharingEnabled: true)
    }

    // MARK: - Une ligne d'une Miff qui n'est plus la sienne n'est pas dessinée

    func testALovedOneLineOfAMiffThatIsNotTheAccountsAnyMoreIsNotShown() {
        let rig = makeRig(myMiffs: [miff])
        let mine = item(updateKey, .n9)
        let gone = item("n9|u|uidB|u-x", .n9, miffId: "autre")
        let reminder = item("n1|autre|me|1", .n1, miffId: "autre")

        XCTAssertTrue(rig.model.isShown(mine))
        XCTAssertFalse(rig.model.isShown(gone))
        XCTAssertTrue(rig.model.isShown(reminder), "seules les lignes de proches suivent la liste des Miffs")
        XCTAssertEqual(rig.model.shown([mine, gone, reminder]).map { $0.id }, [mine.id, reminder.id])
    }

    func testWhenEveryLineIsHiddenThereIsNothingToShow() {
        let rig = makeRig(myMiffs: [])
        rig.service.record(item(updateKey, .n9))

        XCTAssertFalse(rig.service.history.items.contains { rig.model.isShown($0) })
    }

    // MARK: - Visibles quand les notifications du téléphone sont coupées (CNC-7)

    func testWhatLovedOnesDidStaysVisibleWhenNotificationsAreOffButRemindersDoNot() async {
        let rig = makeRig(.denied)
        rig.service.record(item(updateKey, .n9))
        rig.service.record(item(alertKey, .n10))
        rig.service.record(item("n1|miff|me|1", .n1))
        await rig.model.onAppear()

        XCTAssertNotNil(rig.model.lock)
        XCTAssertEqual(Set(rig.model.shownLovedOneItems.map { $0.type }), [.n9, .n10])
    }

    func testLeavingTheCentreBehindTheBlurReadsOnlyWhatWasVisible() async {
        let rig = makeRig(.denied)
        rig.service.record(item(updateKey, .n9))
        rig.service.record(item("n1|miff|me|1", .n1))
        await rig.model.onAppear()

        rig.model.onDisappear()

        let unread = rig.service.history.items.filter { $0.isUnread }.map { $0.id }
        XCTAssertEqual(unread, ["n1|miff|me|1"], "le rappel est resté derrière le flou, il n'a pas été vu")
    }

    func testLeavingTheCentreReadsEverythingWhenNotificationsAreOn() async {
        let rig = makeRig(.authorized)
        rig.service.record(item(updateKey, .n9))
        rig.service.record(item("n1|miff|me|1", .n1))
        await rig.model.onAppear()

        rig.model.onDisappear()

        XCTAssertFalse(rig.service.hasUnread)
    }

    // MARK: - Les visages dont les lignes ont besoin

    func testOnlyThePeopleOfTheShownLinesNeedAFace() {
        let rig = makeRig(myMiffs: [miff])
        rig.service.record(item(updateKey, .n9))
        rig.service.record(item(alertKey, .n10))
        rig.service.record(item("n3|a", .n3))
        rig.service.record(item("n1|a", .n1))
        rig.service.record(item("n9|u|uidC|u-x", .n9, miffId: "autre"))

        XCTAssertEqual(rig.model.neededFaces(), [FaceKey(miffId: miff, uid: "uidB")], "une personne, une lecture")
    }

    func testAMemberTheFeedKnowsGivesTheNameAndThePhotoOfTheLineTogether() {
        let rig = makeRig()
        let line = item(updateKey, .n9)
        rig.faces.seedFromFeed(miffId: miff, members: [member("uidB", "Léa")])

        let look = rig.model.look(of: line)

        XCTAssertEqual(look.name, "Léa")
        XCTAssertEqual(look.avatar, .initials("L"))
        let text = NotificationRowTexts.text(
            for: line, count: 1, miffName: nil, requestMiffName: nil, firstName: nil, lovedOneName: look.name
        )
        XCTAssertEqual(text.title, L10n.format("notif_circle_update_title", "Léa"))
        XCTAssertEqual(text.body, L10n.string("notif_circle_update_body"))
    }

    func testBeforeTheFaceIsKnownTheLineSaysAnotherLovedOneNeverAnInventedName() {
        let rig = makeRig()
        let line = item(updateKey, .n9)

        let look = rig.model.look(of: line)

        XCTAssertEqual(look.avatar, .placeholder)
        XCTAssertNil(look.name)
        let text = NotificationRowTexts.text(
            for: line, count: 1, miffName: nil, requestMiffName: nil, firstName: nil, lovedOneName: look.name
        )
        XCTAssertEqual(text.title, L10n.string("notif_center_circle_update_title"))
    }

    func testAReminderShowsMyOwnPhotoFromMyProfile() {
        let rig = makeRig()
        rig.faces.setMine(initials: "TM", photo: nil)

        XCTAssertEqual(rig.model.look(of: item("n1|a", .n1)).avatar, .initials("TM"))
        XCTAssertEqual(rig.model.look(of: item("n3|a", .n3)).avatar, .logo)
    }

    func testWithoutFacesTheCentreIsTheOneOfBefore() {
        let service = NotificationCenterService(
            store: MemoryNotificationCenterStore(), currentUid: { "uidA" }, nowMillis: { [now] in now }
        )
        let model = NotificationCenterViewModel(
            service: service, permissions: NotificationStubPermissions(.authorized), directory: RequesterDirectory()
        )

        XCTAssertEqual(model.look(of: item(updateKey, .n9)).avatar, .placeholder)
        XCTAssertEqual(model.look(of: item("n3|a", .n3)).avatar, .logo)
        XCTAssertTrue(model.neededFaces().isEmpty)
    }

    // MARK: - Le toucher

    func testATouchOnALovedOneLineLeadsToHisCardInTheFeedAndReadsTheLine() {
        let rig = makeRig()
        let news = item(updateKey, .n9)
        let alert = item(alertKey, .n10)
        rig.service.record(news)
        rig.service.record(alert)

        XCTAssertEqual(rig.model.open(news), .feedCard(miffId: miff, uid: "uidB"))
        XCTAssertEqual(rig.model.open(alert), .feedCard(miffId: miff, uid: "uidB"))
        XCTAssertFalse(rig.service.hasUnread)
    }

    func testALineWithoutAKnownPersonLeadsToTheFeedNeverToAnError() {
        let rig = makeRig()

        XCTAssertEqual(rig.model.open(item("n9|m|\(miff)|x", .n9)), .feed)
    }

    // MARK: - Supprimer une ligne supprime son entrée (CNC-15)

    func testDeletingALineDeletesItsEntryOnTheServerToo() {
        let rig = makeRig()
        let news = item(updateKey, .n9)
        rig.service.record(news)
        rig.service.record(item("n1|a", .n1))

        rig.model.delete(news)

        XCTAssertEqual(rig.service.history.items.map { $0.id }, ["n1|a"])
        XCTAssertEqual(rig.gateway.deletedBatches, [["u-abcdefghijklmnopqrst-uidB-1"]])
    }

    func testDeletingAReminderDeletesNothingOnTheServer() {
        let rig = makeRig()
        let reminder = item("n1|a", .n1)
        rig.service.record(reminder)

        rig.model.delete(reminder)

        XCTAssertTrue(rig.gateway.deletedBatches.isEmpty)
    }

    func testClearAllEmptiesTheCentreAndTheWholeJournalOfTheAccount() async {
        let rig = makeRig()
        rig.service.record(item(updateKey, .n9))
        rig.service.record(item("n1|a", .n1))
        rig.model.askDeleteAll()

        rig.model.confirmDeleteAll()
        await waitUntil { rig.gateway.deleteAllCalls > 0 }

        XCTAssertTrue(rig.service.history.isEmpty)
        XCTAssertFalse(rig.model.deleteAllAsked)
        XCTAssertEqual(rig.gateway.deletedBatches, [["u-abcdefghijklmnopqrst-uidB-1"]])
        XCTAssertEqual(rig.gateway.deleteAllCalls, 1, "même ce que ce téléphone n'a jamais vu")
    }

    func testWithoutTheJournalNothingIsDeletedOnTheServer() async {
        let rig = makeRig(withJournal: false)
        let news = item(updateKey, .n9)
        rig.service.record(news)

        rig.model.delete(news)
        rig.model.confirmDeleteAll()
        await Task.yield()

        XCTAssertTrue(rig.gateway.deletedBatches.isEmpty)
        XCTAssertEqual(rig.gateway.deleteAllCalls, 0)
        XCTAssertTrue(rig.service.history.isEmpty, "le centre local, lui, est bien vidé")
    }

    // MARK: - La lecture à l'ouverture

    func testOpeningTheCentreReadsTheJournalOnceAndBringsItsLinesIn() async {
        let rig = makeRig()
        let entry = JournalEntry(
            entryId: "u-abcdefghijklmnopqrst-uidB-1", kind: .update, miffId: miff, actorUid: "uidB",
            createdAtMillis: now - 1_000, expireAtMillis: now + 1_000_000
        )
        rig.gateway.answer = .loaded([entry])

        await rig.model.syncJournal()

        XCTAssertEqual(rig.gateway.readCalls.count, 1)
        XCTAssertEqual(rig.service.history.items.map { $0.id }, [updateKey])
    }

    func testTheJournalNotDeployedYetShowsNothingAndBreaksNothing() async {
        let rig = makeRig()
        rig.gateway.answer = .refused
        rig.service.record(item("n1|a", .n1))

        await rig.model.syncJournal()
        await rig.model.syncJournal()

        XCTAssertEqual(rig.gateway.readCalls.count, 1, "pas de boucle de relecture")
        XCTAssertEqual(rig.service.history.items.map { $0.id }, ["n1|a"])
    }

    func testReadingTheMissingFacesNeverFailsOnAnEmptyList() async {
        let rig = makeRig()

        await rig.model.ensureFaces([])

        XCTAssertEqual(rig.faces.lookup(miffId: miff, uid: "uidB"), .pending)
    }
}
