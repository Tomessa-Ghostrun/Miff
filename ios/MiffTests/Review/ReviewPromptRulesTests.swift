#if REVIEW_PROMPT
import XCTest
@testable import Miff

/// Quand la fenêtre d'avis peut apparaître (`docs/tickets/pop-ins-avis.md`) : au plus tous les 90 jours après « Peux faire mieux... », retour
/// après 30 jours quand elle a été fermée sans réponse (AUCUNE limite aux fermetures, décision du PM du 2026-11-08), seulement pour une Miff de
/// 7 jours ou plus avec le statut d'un proche vu, jamais sur l'Update, avec le clavier ou par-dessus un message de l'app, jamais après « J'adore ! ».
/// Du Swift pur, avec une horloge injectée. Miroir de `ReviewPromptRulesTest` et `ReviewSituationTest` d'Android.
final class ReviewPromptRulesTests: XCTestCase {
    private let day = 86_400_000
    private var now: Int {
        1_000 * day
    }

    private var ready: ReviewSituation {
        ReviewSituation(hasMiff: true, friendStatusSeen: true, updateScreenOpen: false, keyboardOpen: false)
    }

    private var oldMiff: ReviewState {
        ReviewState(firstMiffSeenAtMillis: now - 8 * day)
    }

    private func may(_ state: ReviewState, _ situation: ReviewSituation? = nil, at moment: Int? = nil) -> Bool {
        ReviewPromptRules.mayAppear(state: state, situation: situation ?? ready, nowMillis: moment ?? now)
    }

    func testTheThresholdsAreTheOnesOfTheBrief() {
        XCTAssertEqual(ReviewPromptDefaults.minMiffAgeMillis, 7 * day)
        XCTAssertEqual(ReviewPromptDefaults.minIntervalAfterAnswerMillis, 90 * day)
        XCTAssertEqual(ReviewPromptDefaults.minIntervalAfterCloseMillis, 30 * day)
        XCTAssertEqual(ReviewPromptDefaults.settleMillis, 1_500)
        XCTAssertEqual(ReviewPromptDefaults.thanksMillis, 1_600)
    }

    // MARK: - La Miff et le Feed

    func testAMiffOfEightDaysWithAFriendStatusSeenMayShowTheWindow() {
        XCTAssertTrue(may(oldMiff))
    }

    func testAMiffSeenForLessThanSevenDaysNeverShowsIt() {
        XCTAssertFalse(may(ReviewState(firstMiffSeenAtMillis: now - 7 * day + 1)))
        XCTAssertFalse(may(ReviewState(firstMiffSeenAtMillis: now)))
    }

    func testExactlySevenDaysIsEnough() {
        XCTAssertTrue(may(ReviewState(firstMiffSeenAtMillis: now - 7 * day)))
    }

    func testWithoutADateForTheFirstMiffOrWithoutAMiffOrWithoutAFriendStatusNothing() {
        var noMiff = ready
        noMiff.hasMiff = false
        var noFriend = ready
        noFriend.friendStatusSeen = false

        XCTAssertFalse(may(ReviewState()))
        XCTAssertFalse(may(oldMiff, noMiff))
        XCTAssertFalse(may(oldMiff, noFriend))
    }

    func testAClockThatWentBackwardsWaitsInsteadOfShowing() {
        // La première Miff « dans le futur » et une dernière apparition « dans le futur » : trop tôt, jamais deux fois.
        XCTAssertFalse(may(ReviewState(firstMiffSeenAtMillis: now + day)))
        var closedInTheFuture = oldMiff
        closedInTheFuture.lastShownAtMillis = now + day
        closedInTheFuture.lastOutcome = .closed
        XCTAssertFalse(may(closedInTheFuture))
    }

    // MARK: - Jamais sur l'Update, avec le clavier, ni par-dessus un message

    func testNeverOnTheUpdateNeverWithTheKeyboard() {
        var update = ready
        update.updateScreenOpen = true
        var keyboard = ready
        keyboard.keyboardOpen = true

        XCTAssertFalse(may(oldMiff, update))
        XCTAssertFalse(may(oldMiff, keyboard))
    }

    func testNeverOverAMessageOfTheApp() {
        var message = ready
        message.blockingMessageOpen = true

        XCTAssertFalse(may(oldMiff, message))
    }

    func testTheMessagesThatMustNotBeCoveredAreTheLostAccessOneTheAgreementTheLocateSheetAndASharingThatStopped() {
        func open(
            lost: Bool = false,
            agreement: Bool = false,
            locate: Bool = false,
            label: StateLabel? = nil
        ) -> Bool {
            ReviewSituation.messageOpen(
                lostAccessNotice: lost, awaitingAgreement: agreement, locateSheetOpen: locate, myLabel: label
            )
        }

        XCTAssertFalse(open())
        XCTAssertFalse(open(label: .at(.category(.home))))
        XCTAssertFalse(open(label: .noNews))
        XCTAssertTrue(open(lost: true))
        XCTAssertTrue(open(agreement: true))
        XCTAssertTrue(open(locate: true))
        XCTAssertTrue(open(label: .sharingStopped))
        XCTAssertTrue(open(label: .waitingAgreement))
    }

    func testTheConnectionPopInIsAWindowTooTheReviewWindowNeverAppearsOverIt() {
        XCTAssertFalse(
            ReviewSituation.messageOpen(
                lostAccessNotice: false, awaitingAgreement: false, locateSheetOpen: false, myLabel: nil
            )
        )
        XCTAssertTrue(
            ReviewSituation.messageOpen(
                lostAccessNotice: false, awaitingAgreement: false, locateSheetOpen: false, myLabel: nil,
                connectionPopInOpen: true
            )
        )
    }

    // MARK: - Après « J'adore ! » : jamais plus

    func testAfterLoveNeverAgain() {
        var state = oldMiff
        state.lastShownAtMillis = now - 1_000 * day
        state.lastOutcome = .loved

        XCTAssertFalse(may(state))
    }

    // MARK: - 90 jours après « Peux faire mieux... » (sans limite de fois)

    func testAfterCanDoBetterTheQuestionIsAskedAgainOnlyAfterNinetyDaysAndAgainEachTime() {
        let state = ReviewState(
            firstMiffSeenAtMillis: now - 400 * day, lastShownAtMillis: now - 89 * day, lastOutcome: .canDoBetter
        )

        XCTAssertFalse(may(state))
        XCTAssertFalse(may(state, at: now + day - 1))
        XCTAssertTrue(may(state, at: now + day))
        // Posée de nouveau, et « Peux faire mieux... » de nouveau : encore 90 jours, autant de fois qu'il faut.
        let again = state.shown(nowMillis: now + day).answered(.canDoBetter)
        XCTAssertFalse(may(again, at: now + 90 * day))
        XCTAssertTrue(may(again, at: now + 91 * day))
    }

    // MARK: - Fermée sans réponse : retour après 30 jours, SANS limite

    func testAfterAClosureTheWindowComesBackAfterThirtyDaysNotBefore() {
        let state = ReviewState(
            firstMiffSeenAtMillis: now - 400 * day, lastShownAtMillis: now - 29 * day, lastOutcome: .closed
        )

        XCTAssertFalse(may(state))
        XCTAssertTrue(may(state, at: now + day))
    }

    func testClosuresAreNeverCountedTheWindowKeepsComingBackEveryThirtyDays() {
        var state = oldMiff
        var at = now
        for appearance in 1...50 {
            XCTAssertTrue(may(state, at: at), "apparition \(appearance)")
            state = state.shown(nowMillis: at).closed()
            XCTAssertFalse(may(state, at: at + 30 * day - 1))
            at += 30 * day
        }
    }

    func testAWindowTheAppWasStoppedOnWaitsLikeAClosure() {
        let state = ReviewState(
            firstMiffSeenAtMillis: now - 400 * day, lastShownAtMillis: now - 10 * day, lastOutcome: .shown
        )

        XCTAssertFalse(may(state))
        XCTAssertTrue(may(state, at: now + 20 * day))
    }

    // MARK: - Les transitions de l'état

    func testTheFirstMiffDateIsKeptTheFirstTimeOnly() {
        let state = ReviewState().withMiffSeen(nowMillis: 100).withMiffSeen(nowMillis: 900)

        XCTAssertEqual(state.firstMiffSeenAtMillis, 100)
    }

    func testShownThenClosedSetsTheOutcomeAndTheDate() {
        let shown = oldMiff.shown(nowMillis: now)

        XCTAssertEqual(shown.lastOutcome, .shown)
        XCTAssertEqual(shown.lastShownAtMillis, now)
        XCTAssertEqual(shown.closed().lastOutcome, .closed)
    }

    func testAnAnswerIsLovedOrCanDoBetterAndNothingElse() {
        // Le type `ReviewAnswer` n'a que ces deux valeurs : répondre par une fermeture ne s'écrit pas.
        XCTAssertEqual(oldMiff.shown(nowMillis: now).answered(.canDoBetter).lastOutcome, .canDoBetter)
        XCTAssertEqual(oldMiff.shown(nowMillis: now).answered(.loved).lastOutcome, .loved)
    }

    func testAFreshStateHoldsNothing() {
        let state = ReviewState()

        XCTAssertNil(state.firstMiffSeenAtMillis)
        XCTAssertNil(state.lastShownAtMillis)
        XCTAssertNil(state.lastOutcome)
    }

    func testTheOutcomesAreWrittenWithTheNamesOfAndroid() {
        XCTAssertEqual(ReviewOutcome.shown.rawValue, "SHOWN")
        XCTAssertEqual(ReviewOutcome.closed.rawValue, "CLOSED")
        XCTAssertEqual(ReviewOutcome.loved.rawValue, "LOVED")
        XCTAssertEqual(ReviewOutcome.canDoBetter.rawValue, "CAN_DO_BETTER")
    }

    // MARK: - « A vu le statut d'un proche »

    func testALovedOnesPlaceOrDepartureOrDangerZoneIsAStatusSeen() {
        XCTAssertTrue(ReviewSituation.isFriendStatusSeen(in: content(card(isMe: false, label: .at(.category(.home))))))
        XCTAssertTrue(ReviewSituation.isFriendStatusSeen(in: content(card(isMe: false, label: .left(.unknown)))))
        XCTAssertTrue(ReviewSituation.isFriendStatusSeen(in: content(card(isMe: false, label: .danger))))
    }

    func testNoNewsStoppedSharingMyOwnCardOrNoFeedIsNot() {
        XCTAssertFalse(ReviewSituation.isFriendStatusSeen(in: content(card(isMe: false, label: .noNews))))
        XCTAssertFalse(ReviewSituation.isFriendStatusSeen(in: content(card(isMe: false, label: .sharingStopped))))
        XCTAssertFalse(ReviewSituation.isFriendStatusSeen(in: content(card(isMe: true, label: .at(.category(.home))))))
        XCTAssertFalse(ReviewSituation.isFriendStatusSeen(in: content()))
        XCTAssertFalse(ReviewSituation.isFriendStatusSeen(in: nil))
    }

    func testOneCardIsEnoughAmongOthers() {
        let feed = content(
            card(isMe: false, label: .noNews),
            card(isMe: false, label: .at(.category(.home))),
            card(isMe: true, label: .at(.category(.home)))
        )

        XCTAssertTrue(ReviewSituation.isFriendStatusSeen(in: feed))
    }

    // MARK: - Aides

    private func card(isMe: Bool, label: StateLabel) -> FeedMemberCard {
        FeedMemberCard(
            member: feedMember(isMe ? "me" : "friend", first: "Camille"),
            isMe: isMe,
            level: .green,
            label: label,
            time: .nothing,
            battery: nil,
            limitedLocation: false,
            phone: nil,
            canLocate: false
        )
    }

    private func content(_ cards: FeedMemberCard...) -> FeedContent {
        FeedContent(
            miffId: "m",
            miffName: "Famille",
            joinCode: "CODE",
            isFull: false,
            myLevel: .green,
            myLabel: .noNews,
            miffLevel: .green,
            bubble: .noNews,
            banners: [],
            cards: cards,
            announceKey: "k"
        )
    }
}
#endif
