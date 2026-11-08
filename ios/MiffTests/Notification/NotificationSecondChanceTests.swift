import XCTest
@testable import Miff

/// La carte « deuxième chance » du Feed (ticket §8.1, étape 3) : après la première update publiée, si la fenêtre du système n'a jamais
/// été montrée ; non modale ; jamais plus d'une fois ; jamais si le téléphone a déjà refusé.
@MainActor
final class NotificationSecondChanceTests: XCTestCase {
    // MARK: - La règle, table complète

    func testTheCardIsDueOnlyWhenTheWindowWasNeverShownAFirstUpdateWasSeenAndItWasNotAnswered() {
        let authorizations: [NotificationAuthorization] = [.notDetermined, .denied, .authorized]
        for authorization in authorizations {
            for seen in [false, true] {
                for answered in [false, true] {
                    let state = SecondChanceState(firstUpdateSeen: seen, answered: answered)
                    let expected = authorization == .notDetermined && seen && !answered

                    XCTAssertEqual(
                        SecondChanceRule.isDue(authorization: authorization, state: state),
                        expected,
                        "\(authorization) vue=\(seen) répondue=\(answered)"
                    )
                }
            }
        }
    }

    // MARK: - Le comportement

    private func makeModel(
        _ authorization: NotificationAuthorization,
        store: MemorySecondChanceStore = MemorySecondChanceStore(),
        uid: @escaping () -> String? = { "uid-1" }
    ) -> (NotificationSecondChanceViewModel, NotificationStubPermissions, MemorySecondChanceStore) {
        let permissions = NotificationStubPermissions(authorization)
        let model = NotificationSecondChanceViewModel(permissions: permissions, store: store, currentUid: uid)
        return (model, permissions, store)
    }

    func testNothingShowsBeforeTheFirstUpdate() async {
        let (model, _, _) = makeModel(.notDetermined)

        await model.refresh()

        XCTAssertFalse(model.isDue)
    }

    func testTheFirstUpdateMakesTheCardAppearOnceAndAskingNothingOfTheSystem() async {
        let (model, permissions, store) = makeModel(.notDetermined)

        model.noteFirstUpdate()
        await model.refresh()

        XCTAssertTrue(model.isDue)
        XCTAssertTrue(store.state(uid: "uid-1").firstUpdateSeen)
        XCTAssertEqual(permissions.notificationRequests, 0, "non modale : rien sans un toucher")
    }

    func testActivatingShowsTheSystemWindowAtOnceThenTheCardLeavesForGood() async {
        let (model, permissions, store) = makeModel(.notDetermined)
        model.noteFirstUpdate()
        await model.refresh()

        await model.activate()

        XCTAssertEqual(permissions.notificationRequests, 1)
        XCTAssertFalse(model.isDue)
        XCTAssertTrue(store.state(uid: "uid-1").answered)
        await model.refresh()
        XCTAssertFalse(model.isDue, "elle ne revient pas, même si la personne a refusé dans la fenêtre")
    }

    func testNotNowMakesTheCardLeaveForGoodWithoutAskingTheSystem() async {
        let (model, permissions, _) = makeModel(.notDetermined)
        model.noteFirstUpdate()
        await model.refresh()

        model.dismiss()
        await model.refresh()

        XCTAssertFalse(model.isDue)
        XCTAssertEqual(permissions.notificationRequests, 0)
    }

    func testTheCardNeverNagsAPersonWhoAlreadyRefusedOrAllowed() async {
        for authorization in [NotificationAuthorization.denied, .authorized] {
            let (model, _, _) = makeModel(authorization)

            model.noteFirstUpdate()
            await model.refresh()

            XCTAssertFalse(model.isDue, "\(authorization)")
        }
    }

    func testTheCardWaitsForTheFirstUpdateOfThisAccountOnly() async {
        let store = MemorySecondChanceStore()
        var uid: String? = "uid-1"
        let (model, _, _) = makeModel(.notDetermined, store: store, uid: { uid })
        model.noteFirstUpdate()
        await model.refresh()
        XCTAssertTrue(model.isDue)

        uid = "uid-2"
        await model.refresh()

        XCTAssertFalse(model.isDue, "le compte suivant n'hérite pas de la carte du précédent")
        uid = nil
        await model.refresh()
        XCTAssertFalse(model.isDue)
        model.noteFirstUpdate()
        XCTAssertNil(store.states["uid-2"], "sans session, rien n'est gardé")
    }

    func testTheFirstUpdateIsNoticedOnceOnly() {
        let (model, _, store) = makeModel(.notDetermined)

        model.noteFirstUpdate()
        let first = store.state(uid: "uid-1")
        model.noteFirstUpdate()

        XCTAssertEqual(store.state(uid: "uid-1"), first)
    }
}
