#if REVIEW_PROMPT
import XCTest
@testable import Miff

/// Un magasin en mémoire : ce que la fenêtre retient, sans fichier.
final class MemoryReviewStore: ReviewStateStore {
    var state: ReviewState
    var force: Bool

    init(state: ReviewState = ReviewState(), force: Bool = false) {
        self.state = state
        self.force = force
    }

    func read() -> ReviewState {
        state
    }

    func write(_ state: ReviewState) {
        self.state = state
    }

    func clear() {
        state = ReviewState()
        force = false
    }

    func forceNext() -> Bool {
        force
    }

    func setForceNext(_ value: Bool) {
        force = value
    }
}

/// Les écrans de la fenêtre d'avis et ce que retient chaque sortie (`docs/tickets/pop-ins-avis.md`) : « J'adore ! » = un court « Merci ! » puis la
/// demande du store, une fois, pour de bon ; « Peux faire mieux... » = AUCUNE demande du store, le formulaire dans le navigateur (ou un
/// remerciement calme sans formulaire), redemandé seulement après 90 jours ; la croix ou le geste « échappe » = une fermeture, sans limite ; seuls
/// les builds de test peuvent forcer la fenêtre. Horloge et attente injectées : rien n'attend pour de vrai. Miroir de `ReviewPromptViewModelTest`.
@MainActor
final class ReviewPromptCoordinatorTests: XCTestCase {
    private let day = 86_400_000
    /// L'horloge et l'attente, dans des boîtes simples (sans isolation) : les fermetures données au modèle ne touchent jamais l'acteur principal.
    private final class Box {
        var now = 1_000 * 86_400_000
        var pausesAsked: [Int] = []
    }

    private let box = Box()
    private let form = "https://forms.example.org/miff"

    private var now: Int {
        get { box.now }
        set { box.now = newValue }
    }

    private var pausesAsked: [Int] {
        box.pausesAsked
    }

    private var ready: ReviewSituation {
        ReviewSituation(hasMiff: true, friendStatusSeen: true, updateScreenOpen: false, keyboardOpen: false)
    }

    private func oldMiff() -> ReviewState {
        ReviewState(firstMiffSeenAtMillis: now - 10 * day)
    }

    private func coordinator(
        _ store: MemoryReviewStore,
        debug: Bool = false,
        form: String = ""
    ) -> ReviewPromptCoordinator {
        let box = self.box
        return ReviewPromptCoordinator(
            store: store,
            nowMillis: { box.now },
            debugForceAllowed: debug,
            feedbackFormAddress: form,
            pause: { millis in box.pausesAsked.append(millis) }
        )
    }

    // MARK: - L'apparition

    func testAtAReturnToTheForegroundWithTheRulesMetTheQuestionAppearsAndIsRememberedBeforeItIsDrawn() {
        let store = MemoryReviewStore(state: oldMiff())
        let model = coordinator(store)

        model.onForeground(tick: 1, situation: ready)

        XCTAssertEqual(model.stage, .question)
        XCTAssertEqual(store.state.lastShownAtMillis, now)
        XCTAssertEqual(store.state.lastOutcome, .shown)
    }

    func testAReturnIsLookedAtOnceASecondLookWithTheSameReturnDoesNothing() {
        let store = MemoryReviewStore(state: oldMiff())
        let model = coordinator(store)
        var noFriend = ready
        noFriend.friendStatusSeen = false

        model.onForeground(tick: 1, situation: noFriend)

        XCTAssertEqual(model.stage, .hidden)
        XCTAssertTrue(model.hasHandled(1))
        model.onForeground(tick: 1, situation: ready)
        XCTAssertEqual(model.stage, .hidden)
        // Le retour suivant est regardé.
        XCTAssertFalse(model.hasHandled(2))
        model.onForeground(tick: 2, situation: ready)
        XCTAssertEqual(model.stage, .question)
    }

    func testAMiffSeenForLessThanSevenDaysShowsNothingAndTheFirstDateIsKept() {
        let store = MemoryReviewStore()
        let model = coordinator(store)

        model.onMiffSeen()
        XCTAssertEqual(store.state.firstMiffSeenAtMillis, now)
        let first = now
        now += day
        model.onMiffSeen()
        XCTAssertEqual(store.state.firstMiffSeenAtMillis, first)
        model.onForeground(tick: 1, situation: ready)

        XCTAssertEqual(model.stage, .hidden)
        XCTAssertNil(store.state.lastShownAtMillis)
    }

    func testWhileTheWindowIsOnScreenANewReturnDoesNotStackAnother() {
        let store = MemoryReviewStore(state: oldMiff())
        let model = coordinator(store)
        model.onForeground(tick: 1, situation: ready)
        let shownAt = store.state.lastShownAtMillis

        now += 1_000
        model.onForeground(tick: 2, situation: ready)

        XCTAssertEqual(model.stage, .question)
        XCTAssertEqual(store.state.lastShownAtMillis, shownAt)
    }

    func testTheFirstReturnIsTheOpeningOfTheAppAndEachArrivalInTheForegroundAddsOne() {
        let model = coordinator(MemoryReviewStore())

        XCTAssertEqual(model.foregroundTick, 1)
        model.arrivedInForeground()
        model.arrivedInForeground()
        XCTAssertEqual(model.foregroundTick, 3)
    }

    // MARK: - J'adore

    func testLoveThanksBrieflyThenAsksTheStoreOnceAndNeverAgain() async {
        let store = MemoryReviewStore(state: oldMiff())
        let model = coordinator(store)
        model.onForeground(tick: 1, situation: ready)

        model.onLove()

        XCTAssertEqual(model.stage, .thanksForLove)
        XCTAssertEqual(store.state.lastOutcome, .loved)
        // Rien n'est demandé au store tant que « Merci ! » est à l'écran.
        XCTAssertNil(model.pendingEffect)
        await model.thanksTask?.value
        XCTAssertEqual(pausesAsked, [ReviewPromptDefaults.thanksMillis])
        XCTAssertEqual(model.stage, .hidden)
        XCTAssertEqual(model.takeEffect(), .requestStoreReview)
        // Une seconde demande (un double toucher) ne redemande pas.
        model.onLove()
        XCTAssertNil(model.takeEffect())
        XCTAssertEqual(model.stage, .hidden)
        // Un an plus tard : jamais plus.
        now += 365 * day
        model.onForeground(tick: 2, situation: ready)
        XCTAssertEqual(model.stage, .hidden)
    }

    func testTheEscapeGestureDuringTheThanksDoesNothing() {
        let model = coordinator(MemoryReviewStore(state: oldMiff()))
        model.onForeground(tick: 1, situation: ready)
        model.onLove()

        model.onClose()

        XCTAssertEqual(model.stage, .thanksForLove)
    }

    func testTheEffectIsGivenOnlyOnce() async {
        let model = coordinator(MemoryReviewStore(state: oldMiff()))
        model.onForeground(tick: 1, situation: ready)
        model.onLove()
        await model.thanksTask?.value

        XCTAssertEqual(model.takeEffect(), .requestStoreReview)
        XCTAssertNil(model.takeEffect())
    }

    // MARK: - Peux faire mieux...

    func testCanDoBetterDoesNotOpenTheStoreShowsACalmThanksAndWaitsNinetyDays() {
        let store = MemoryReviewStore(state: oldMiff())
        let model = coordinator(store)
        model.onForeground(tick: 1, situation: ready)

        model.onCanDoBetter()

        XCTAssertEqual(model.stage, .thanksForFeedback)
        XCTAssertEqual(store.state.lastOutcome, .canDoBetter)
        // Rien n'est parti vers l'extérieur : ni demande du store, ni courrier pour l'instant.
        XCTAssertNil(model.pendingEffect)
        model.onLater()
        XCTAssertEqual(model.stage, .hidden)
        now += 89 * day
        model.onForeground(tick: 2, situation: ready)
        XCTAssertEqual(model.stage, .hidden)
        now += 2 * day
        model.onForeground(tick: 3, situation: ready)
        XCTAssertEqual(model.stage, .question)
    }

    func testWriteToUsOpensTheMailOnceAndClosesTheWindow() {
        let model = coordinator(MemoryReviewStore(state: oldMiff()))
        model.onForeground(tick: 1, situation: ready)
        model.onCanDoBetter()

        model.onWrite()
        model.onWrite()

        XCTAssertEqual(model.stage, .hidden)
        XCTAssertEqual(model.takeEffect(), .openMail)
        XCTAssertNil(model.takeEffect())
    }

    func testTheEscapeGestureOnTheThanksJustClosesAndTheOutcomeStaysCanDoBetter() {
        let store = MemoryReviewStore(state: oldMiff())
        let model = coordinator(store)
        model.onForeground(tick: 1, situation: ready)
        model.onCanDoBetter()

        model.onClose()

        XCTAssertEqual(model.stage, .hidden)
        XCTAssertEqual(store.state.lastOutcome, .canDoBetter)
    }

    func testWithAFeedbackFormCanDoBetterClosesTheWindowAndOpensTheFormOnceWithoutTheStore() {
        let store = MemoryReviewStore(state: oldMiff())
        let model = coordinator(store, form: form)
        model.onForeground(tick: 1, situation: ready)

        model.onCanDoBetter()
        model.onCanDoBetter()

        XCTAssertEqual(model.stage, .hidden)
        XCTAssertEqual(store.state.lastOutcome, .canDoBetter)
        XCTAssertEqual(model.takeEffect(), .openFeedbackForm(address: form))
        XCTAssertNil(model.takeEffect())
        // Redemandé seulement après 90 jours.
        now += 89 * day
        model.onForeground(tick: 2, situation: ready)
        XCTAssertEqual(model.stage, .hidden)
        now += 2 * day
        model.onForeground(tick: 3, situation: ready)
        XCTAssertEqual(model.stage, .question)
    }

    func testWithoutAFeedbackFormOrWithAnAddressThatIsNotHttpsTheCalmScreenWithWriteToUsShows() {
        for address in ["", "   ", "http://forms.example.org/miff", "javascript:alert(1)"] {
            let model = coordinator(MemoryReviewStore(state: oldMiff()), form: address)
            model.onForeground(tick: 1, situation: ready)

            model.onCanDoBetter()

            XCTAssertEqual(model.stage, .thanksForFeedback, address)
            XCTAssertNil(model.pendingEffect, address)
        }
    }

    func testTheLoveButtonNeverOpensTheFormAndTheFormNeverOpensTheStore() async {
        let model = coordinator(MemoryReviewStore(state: oldMiff()), form: form)
        model.onForeground(tick: 1, situation: ready)

        model.onLove()
        await model.thanksTask?.value

        XCTAssertEqual(model.takeEffect(), .requestStoreReview)
    }

    // MARK: - Fermer sans répondre

    func testClosingTheWindowComesBackAfterThirtyDaysEveryTimeWithNoLimit() {
        let store = MemoryReviewStore(state: oldMiff())
        let model = coordinator(store)
        var tick = 0
        for appearance in 1...5 {
            tick += 1
            model.onForeground(tick: tick, situation: ready)
            XCTAssertEqual(model.stage, .question, "apparition \(appearance)")
            model.onClose()
            XCTAssertEqual(model.stage, .hidden)
            XCTAssertEqual(store.state.lastOutcome, .closed)
            now += 29 * day
            tick += 1
            model.onForeground(tick: tick, situation: ready)
            XCTAssertEqual(model.stage, .hidden)
            now += 2 * day
        }
    }

    func testClosingTwiceInARowClosesOnce() {
        let store = MemoryReviewStore(state: oldMiff())
        let model = coordinator(store)
        model.onForeground(tick: 1, situation: ready)
        model.onClose()
        let after = store.state

        model.onClose()

        XCTAssertEqual(store.state, after)
    }

    // MARK: - Les messages de l'app et l'option de test

    func testAMessageOfTheAppKeepsTheWindowAwayEvenForcedInATestBuild() {
        let store = MemoryReviewStore(state: oldMiff(), force: true)
        let model = coordinator(store, debug: true)
        var message = ready
        message.blockingMessageOpen = true

        model.onForeground(tick: 1, situation: message)

        XCTAssertEqual(model.stage, .hidden)
        XCTAssertTrue(store.force)
        let plain = coordinator(MemoryReviewStore(state: oldMiff()))
        plain.onForeground(tick: 1, situation: message)
        XCTAssertEqual(plain.stage, .hidden)
    }

    func testATestBuildCanForceTheWindowOnceWhateverTheDates() {
        let store = MemoryReviewStore(state: ReviewState(), force: true)
        let model = coordinator(store, debug: true)
        var noFriend = ready
        noFriend.friendStatusSeen = false

        model.onForeground(tick: 1, situation: noFriend)

        XCTAssertEqual(model.stage, .question)
        XCTAssertFalse(store.force)
    }

    func testAForcedWindowNeverCoversTheUpdateOrTheKeyboardAndWaitsForTheNextReturn() {
        let store = MemoryReviewStore(state: ReviewState(), force: true)
        let model = coordinator(store, debug: true)
        var update = ready
        update.updateScreenOpen = true
        var keyboard = ready
        keyboard.keyboardOpen = true

        model.onForeground(tick: 1, situation: update)
        XCTAssertEqual(model.stage, .hidden)
        model.onForeground(tick: 1, situation: keyboard)
        XCTAssertEqual(model.stage, .hidden)
        XCTAssertTrue(store.force)
        model.onForeground(tick: 2, situation: ready)

        XCTAssertEqual(model.stage, .question)
    }

    func testABuildThatIsNotATestBuildIgnoresTheForce() {
        let store = MemoryReviewStore(state: ReviewState(), force: true)
        let model = coordinator(store, debug: false)

        model.onForeground(tick: 1, situation: ready)

        XCTAssertEqual(model.stage, .hidden)
    }

    // MARK: - La fin de session

    func testTheEndOfASessionForgetsEverythingAndStopsTheThanks() async {
        let store = MemoryReviewStore(state: oldMiff())
        let model = coordinator(store)
        model.onForeground(tick: 1, situation: ready)
        model.onLove()
        let thanks = model.thanksTask

        model.reset()
        await thanks?.value

        XCTAssertEqual(store.state, ReviewState())
        XCTAssertEqual(model.stage, .hidden)
        XCTAssertNil(model.takeEffect())
    }
}
#endif
