// Quelques assertions portent un message en clair : elles dépassent la limite de ligne.
// swiftlint:disable line_length
import Combine
import XCTest
@testable import Miff

/// Réglages > Abonnement (ticket `docs/tickets/premium-achat-reel.md` §8, critères AN6 à AN10, IO3, IO5) : les phrases, les actions, la lecture du droit et la restauration.
@MainActor
final class SubscriptionScreenTests: XCTestCase {
    private let utc = TimeZone(identifier: "UTC") ?? .current
    private let french = Locale(identifier: "fr_FR")
    private let now = 1_800_000_000_000
    private let day = 86_400_000

    private func subscription(
        _ state: StoreState,
        source: SubscriptionSource = .appStore,
        test: Bool = false
    ) -> SubscriptionStatus {
        .store(StoreSubscription(source: source, state: state, plan: .yearly, expiresAtMillis: now + 30 * day, test: test))
    }

    private func lines(_ status: SubscriptionStatus) -> [String] {
        SubscriptionTexts.lines(for: status, locale: french, zone: utc).map(\.text)
    }

    // MARK: - Les phrases

    func testEveryStateHasOneCalmSentenceWithTheDateAndNeverAPrice() {
        let date = SubscriptionDates.long(millis: now + 30 * day, locale: french, zone: utc)
        let dated: [StoreState] = [.trial, .active, .canceled, .grace]
        for state in dated {
            let texts = lines(subscription(state))

            XCTAssertEqual(texts.count, 1, "\(state)")
            XCTAssertTrue(texts[0].contains(date), "\(state) : la date au format long")
            XCTAssertFalse(texts[0].contains("€"), "\(state) : aucun prix (celui du renouvellement est celui d'Apple)")
            XCTAssertFalse(texts[0].contains("!"), "\(state) : jamais un point d'exclamation")
        }
        for state in [StoreState.onHold, .expired, .revoked] {
            XCTAssertEqual(lines(subscription(state)).count, 1, "\(state)")
        }
    }

    func testTheRenewalSentenceHasNoPriceAtAll() {
        let text = lines(subscription(.active))[0]

        XCTAssertEqual(text, L10n.format("settings_subscription_renews_noprice", SubscriptionDates.long(millis: now + 30 * day, locale: french, zone: utc)))
    }

    func testTheFreeAndTheManualAccountsKeepTheirSentences() {
        XCTAssertEqual(lines(.free), [L10n.string("settings_subscription_free")])
        let manual = lines(.manual(expiresAtMillis: now))
        XCTAssertEqual(manual.count, 3)
        XCTAssertEqual(manual[1], L10n.string("settings_subscription_source_manual"))
        XCTAssertEqual(manual[2], L10n.string("settings_subscription_no_store_note"))
    }

    func testASubscriptionBoughtOnAnAndroidPhoneSaysWhereToManageItAndATestPurchaseSaysSo() {
        let play = lines(subscription(.active, source: .googlePlay, test: true))

        XCTAssertEqual(play.count, 3)
        XCTAssertEqual(play[1], L10n.string("ios_settings_subscription_other_store"))
        XCTAssertEqual(play[2], L10n.string("settings_subscription_test_note"))
        XCTAssertFalse(lines(subscription(.active)).contains(L10n.string("ios_settings_subscription_other_store")))
    }

    func testEveryNoticeReusesTheSentencesOfTheOffer() {
        XCTAssertEqual(SubscriptionTexts.text(for: .pending), L10n.string("paywall_pending"))
        XCTAssertEqual(SubscriptionTexts.text(for: .refreshFailed), L10n.string("settings_subscription_refresh_failed"))
        XCTAssertTrue(SubscriptionTexts.text(for: .nothingFound).contains(L10n.string("ios_paywall_store_name")))
        XCTAssertTrue(SubscriptionTexts.text(for: .otherAccount).contains(AppLinks.supportEmail), "le contact, jamais un détail sur l'autre compte")
    }

    // MARK: - Les actions

    func testOnlyAFreeAccountOrAnEndedSubscriptionCanBuy() {
        XCTAssertTrue(SubscriptionActionsRules.canBuy(.free))
        XCTAssertTrue(SubscriptionActionsRules.canBuy(subscription(.expired)))
        XCTAssertTrue(SubscriptionActionsRules.canBuy(subscription(.revoked)))
        for state in [StoreState.trial, .active, .canceled, .grace, .onHold] {
            XCTAssertFalse(SubscriptionActionsRules.canBuy(subscription(state)), "\(state) : jamais un second abonnement")
        }
        XCTAssertFalse(SubscriptionActionsRules.canBuy(.manual(expiresAtMillis: now)))
    }

    func testTheManagementIsOfferedOnlyWhileTheSubscriptionExistsAtAStore() {
        XCTAssertEqual(SubscriptionActionsRules.manageableSource(subscription(.active)), .appStore)
        XCTAssertEqual(SubscriptionActionsRules.manageableSource(subscription(.grace, source: .googlePlay)), .googlePlay)
        XCTAssertEqual(SubscriptionActionsRules.manageableSource(subscription(.onHold)), .appStore)
        XCTAssertNil(SubscriptionActionsRules.manageableSource(subscription(.expired)))
        XCTAssertNil(SubscriptionActionsRules.manageableSource(subscription(.active, source: .other)))
        XCTAssertNil(SubscriptionActionsRules.manageableSource(.free))
        XCTAssertNil(SubscriptionActionsRules.manageableSource(.manual(expiresAtMillis: now)))
    }

    func testTheRestorationIsOfferedToEveryoneButAnAccessGivenByHand() {
        XCTAssertTrue(SubscriptionActionsRules.canRestore(.free))
        XCTAssertTrue(SubscriptionActionsRules.canRestore(subscription(.active)))
        XCTAssertFalse(SubscriptionActionsRules.canRestore(.manual(expiresAtMillis: now)))
    }

    // MARK: - Ce que dit la dernière restauration

    func testEachRestorationOutcomeHasItsCalmNoticeOrNone() {
        XCTAssertEqual(SubscriptionNotice.of(.noneFound), .nothingFound)
        XCTAssertEqual(SubscriptionNotice.of(.otherAccount), .otherAccount)
        XCTAssertEqual(SubscriptionNotice.of(.pending), .pending)
        XCTAssertEqual(SubscriptionNotice.of(.network), .refreshFailed)
        XCTAssertEqual(SubscriptionNotice.of(.failed), .refreshFailed)
        XCTAssertNil(SubscriptionNotice.of(.found))
        XCTAssertNil(SubscriptionNotice.of(.onHold), "dit par l'état lui-même")
        XCTAssertNil(SubscriptionNotice.of(.cancelled), "annulation sans message")
    }

    // MARK: - Le modèle

    private func makeModel(
        gateway: StubGateway = StubGateway(),
        store: StubSubscriptionStore = StubSubscriptionStore()
    ) -> (SubscriptionViewModel, PremiumCoordinator) {
        let repository = SubscriptionRepository(store: store, currentUid: { "uid-1" }, nowMillis: { testNow })
        let coordinator = PremiumCoordinator(
            gateway: gateway, backend: StubBackend(), subscriptions: repository,
            currentUid: { "uid-1" }, clock: ManualPaywallClock()
        )
        return (SubscriptionViewModel(subscriptions: repository, premium: coordinator), coordinator)
    }

    private func settle(_ model: SubscriptionViewModel) async {
        await waitUntil { !model.loading }
    }

    func testTheServerIsReadOnceAndTheStateShown() async {
        let store = StubSubscriptionStore()
        store.document = storeDocument(state: "active")
        let (model, _) = makeModel(store: store)

        model.load()
        await settle(model)

        XCTAssertFalse(model.loading)
        XCTAssertEqual(model.status?.hasAccess, true)
        XCTAssertEqual(store.reads, 1)
    }

    func testAnUnreadableServerIsNeverTakenForFree() async {
        let store = StubSubscriptionStore()
        store.failure = URLError(.notConnectedToInternet)
        let (model, _) = makeModel(store: store)

        model.load()
        await settle(model)

        XCTAssertNil(model.status, "illisible : un message calme et « Réessayer », jamais « tu n'as rien »")
        XCTAssertFalse(model.loading)
    }

    func testRestoringShowsTheNoticeOfItsResultThenReadsTheServerAgain() async {
        let gateway = StubGateway()
        let store = StubSubscriptionStore()
        let (model, coordinator) = makeModel(gateway: gateway, store: store)
        model.load()
        await settle(model)

        model.restore()
        await coordinator.work?.value
        await settle(model)

        XCTAssertEqual(model.notice, .nothingFound)
        XCTAssertFalse(model.refreshing)
        XCTAssertEqual(gateway.syncCalls, 1)
        XCTAssertGreaterThanOrEqual(store.reads, 2, "ce que le serveur sait a peut-être changé : relu")
    }

    func testAResultThatEndedBeforeTheScreenOpenedIsNotToldToIt() async {
        let gateway = StubGateway()
        let (_, coordinator) = makeModel(gateway: gateway)
        coordinator.startRestore()
        await coordinator.work?.value
        let repository = SubscriptionRepository(store: StubSubscriptionStore(), currentUid: { "uid-1" })

        let late = SubscriptionViewModel(subscriptions: repository, premium: coordinator)

        XCTAssertNil(late.notice)
    }

    func testManagingASubscriptionOfTheAppStoreOpensTheSystemManagement() async {
        let gateway = StubGateway()
        let (model, _) = makeModel(gateway: gateway)

        model.manage(.appStore)
        await waitUntil { gateway.managementOpened > 0 }

        XCTAssertEqual(gateway.managementOpened, 1, "aucun bouton pour arrêter dans l'app : la gestion d'Apple")
    }
}
// swiftlint:enable line_length
