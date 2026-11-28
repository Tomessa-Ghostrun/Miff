import XCTest
@testable import Miff

/// Les faits de l'écran que l'offre périodique lit (critères PP12, PP13, PP15) : le droit à Miff+ tel que le SERVEUR l'a dit (jamais une estimation), les messages de l'app, les
/// alertes d'un proche. Fonctions pures, sans écran.
final class PeriodicPaywallSituationTests: XCTestCase {
    private func ready(premium: Bool, known: Bool = true, fromCache: Bool = false) -> DirectoryState {
        .ready(DirectoryReady(miffs: [], activeId: nil, premium: premium, fromCache: fromCache, premiumKnown: known))
    }

    // MARK: - Le droit à Miff+

    func testAnAccountKnownToHaveMiffPlusSaysTrue() {
        XCTAssertEqual(PeriodicPaywallSituation.miffPlus(of: ready(premium: true)), true)
    }

    func testAnAccountKnownToBeFreeSaysFalse() {
        XCTAssertEqual(PeriodicPaywallSituation.miffPlus(of: ready(premium: false)), false)
    }

    func testAnUnknownRightSaysNilNeverFreeByDefault() {
        // Lecture de l'abonnement échouée, ou réponse du cache du téléphone : `premium` vaut faux par défaut, ce qui ne veut PAS dire « gratuit ».
        XCTAssertNil(PeriodicPaywallSituation.miffPlus(of: ready(premium: false, known: false)))
        XCTAssertNil(PeriodicPaywallSituation.miffPlus(of: ready(premium: false, known: false, fromCache: true)))
    }

    func testNothingReadYetSaysNil() {
        XCTAssertNil(PeriodicPaywallSituation.miffPlus(of: .loading))
        XCTAssertNil(PeriodicPaywallSituation.miffPlus(of: .unavailable))
    }

    // MARK: - Un message de l'app

    private func message(
        lost: Bool = false, agreement: Bool = false, locate: Bool = false, label: StateLabel? = nil, popIn: Bool = false
    ) -> Bool {
        PeriodicPaywallSituation.messageOpen(
            lostAccessNotice: lost, awaitingAgreement: agreement, locateSheetOpen: locate, myLabel: label,
            connectionPopInOpen: popIn
        )
    }

    func testNothingOnScreenIsNoMessage() {
        XCTAssertFalse(message())
        XCTAssertFalse(message(label: .noNews))
    }

    func testEveryMessageOfTheAppHoldsTheOfferBack() {
        XCTAssertTrue(message(lost: true), "accès perdu")
        XCTAssertTrue(message(agreement: true), "en attente de ton accord")
        XCTAssertTrue(message(locate: true), "feuille « Localiser »")
        XCTAssertTrue(message(popIn: true), "pop-in « Pas de connexion »")
        XCTAssertTrue(message(label: .sharingStopped), "« Tu as arrêté de partager »")
        XCTAssertTrue(message(label: .waitingAgreement), "ma carte attend mon accord")
    }

    // MARK: - Les alertes

    private func item(_ type: NotificationType, read: Bool) -> NotificationItem {
        NotificationItem(
            id: "\(type)-\(read)", type: type, miffId: "m1", createdAtMillis: 1_000,
            readAtMillis: read ? 2_000 : nil, state: .silent
        )
    }

    func testAnUnreadAlertLineHoldsTheOfferBack() {
        XCTAssertTrue(PeriodicPaywallSituation.alertLineUnread(in: [item(.n10, read: false)]))
    }

    func testAReadAlertLineOrAnotherKindOfLineDoesNot() {
        XCTAssertFalse(PeriodicPaywallSituation.alertLineUnread(in: [item(.n10, read: true)]))
        XCTAssertFalse(PeriodicPaywallSituation.alertLineUnread(in: [item(.n9, read: false), item(.n11, read: false)]))
        XCTAssertFalse(PeriodicPaywallSituation.alertLineUnread(in: []))
    }

    func testTheFeedSnapshotOfNothingIsQuiet() {
        XCTAssertEqual(PeriodicFeedSnapshot.of(content: nil), PeriodicFeedSnapshot())
        XCTAssertFalse(PeriodicFeedSnapshot().alertActive)
    }

    // MARK: - Ma propre alerte et les lignes calmes

    func testMyOwnAlertHoldsTheOfferBackWhileInProgressAndWhileItsSentScreenIsOpen() {
        XCTAssertFalse(PeriodicPaywallSituation.ownAlertHolds(inProgress: false, sentScreenOpen: false))
        XCTAssertTrue(PeriodicPaywallSituation.ownAlertHolds(inProgress: true, sentScreenOpen: false))
        // « Partage arrêté », « indisponible » et les numéros d'urgence : l'écran est ouvert alors que l'alerte n'est plus « en cours ».
        XCTAssertTrue(PeriodicPaywallSituation.ownAlertHolds(inProgress: false, sentScreenOpen: true))
    }

    func testTheCalmLinesOfTheAppHoldTheOfferBack() {
        XCTAssertFalse(PeriodicPaywallSituation.transientMessageOpen(alertNotice: false, deletionBanner: false))
        let notice = PeriodicPaywallSituation.transientMessageOpen(alertNotice: true, deletionBanner: false)
        let banner = PeriodicPaywallSituation.transientMessageOpen(alertNotice: false, deletionBanner: true)
        XCTAssertTrue(notice, "« Alerte terminée… »")
        XCTAssertTrue(banner, "bannière de suppression")
    }

    @MainActor
    func testTheSignalOfMyAlertStartsQuiet() {
        let signal = DangerAlertSignal()

        let holds = PeriodicPaywallSituation.ownAlertHolds(inProgress: signal.inProgress, sentScreenOpen: false)
        XCTAssertFalse(holds)
        XCTAssertFalse(signal.sentScreenOpen)
        XCTAssertFalse(signal.noticeShown)
    }

    // MARK: - Ce qui interdit

    func testEverySituationThatForbidsIsReportedAndACalmOneIsNot() {
        XCTAssertFalse(calmSituation().forbidden)
        XCTAssertTrue(calmSituation { $0.tabsInFront = false }.forbidden)
        XCTAssertTrue(calmSituation { $0.updateScreenOpen = true }.forbidden)
        XCTAssertTrue(calmSituation { $0.keyboardOpen = true }.forbidden)
        XCTAssertTrue(calmSituation { $0.messageOpen = true }.forbidden)
        XCTAssertTrue(calmSituation { $0.ownAlertInProgress = true }.forbidden)
        XCTAssertTrue(calmSituation { $0.lovedOneAlertUnseen = true }.forbidden)
        XCTAssertTrue(calmSituation { $0.offerReachable = false }.forbidden)
        // La présentation du système n'est pas un « interdit » : elle fait attendre (voir `PeriodicPaywallRules.evaluate`).
        XCTAssertFalse(calmSituation { $0.modalPresented = true }.forbidden)
    }
}
