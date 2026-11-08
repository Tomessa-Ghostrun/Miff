import XCTest
@testable import Miff

/// Le retour par balayage depuis le bord de l'écran (retour du PM après TestFlight, 2026-11-08) : quels écrans l'autorisent, et à
/// quelles conditions le geste commence.
final class SwipeBackPolicyTests: XCTestCase {
    // MARK: - La table des écrans

    func testTheOrdinaryStackedScreensAllowTheSwipe() {
        let open: [SwipeBackScreen] = [
            .signUp, .signIn, .passwordReset, .joinMiffCode, .miff, .placeDetail, .placePaywall, .settings
        ]
        for screen in open {
            XCTAssertTrue(screen.allowsSwipeBack, "\(screen)")
            XCTAssertFalse(screen.locksSwipeBack, "\(screen)")
        }
    }

    func testTheProfilePhoneStepIsLockedOnlyWhileTheProfileIsBeingWritten() {
        XCTAssertTrue(SwipeBackScreen.profilePhone(isWriting: false).allowsSwipeBack)
        XCTAssertFalse(SwipeBackScreen.profilePhone(isWriting: true).allowsSwipeBack)
    }

    func testCreatingAMiffLocksTheSwipeWhileSavingAndOnTheReadyScreen() {
        XCTAssertTrue(SwipeBackScreen.createMiffName(isSaving: false).allowsSwipeBack)
        XCTAssertFalse(SwipeBackScreen.createMiffName(isSaving: true).allowsSwipeBack)
        // « Ta Miff est prête » n'a pas de flèche de retour : « Continuer » est la sortie.
        XCTAssertFalse(SwipeBackScreen.createMiffReady.allowsSwipeBack)
    }

    func testTheJoinConfirmationHasNoSwipeBecauseCancelIsTheExit() {
        XCTAssertTrue(SwipeBackScreen.joinMiffCode.allowsSwipeBack)
        XCTAssertFalse(SwipeBackScreen.joinMiffConfirm.allowsSwipeBack)
    }

    func testAPlaceIsNotLeftBySwipeWhileItIsSavedOrDeleted() {
        XCTAssertTrue(SwipeBackScreen.placeForm(isBusy: false).allowsSwipeBack)
        XCTAssertFalse(SwipeBackScreen.placeForm(isBusy: true).allowsSwipeBack)
    }

    func testTheAccountDeletionIsNotLeftBySwipeWhileTheVerificationRuns() {
        XCTAssertTrue(SwipeBackScreen.deleteAccount(isBusy: false).allowsSwipeBack)
        XCTAssertFalse(SwipeBackScreen.deleteAccount(isBusy: true).allowsSwipeBack)
    }

    func testTheUpdateViewfinderAndPreviewAreNeverLeftBySwipe() {
        XCTAssertFalse(SwipeBackScreen.updateViewfinder.allowsSwipeBack)
        XCTAssertFalse(SwipeBackScreen.updatePreview.allowsSwipeBack)
    }

    func testLockingIsTheExactOppositeOfAllowing() {
        let all: [SwipeBackScreen] = [
            .signUp, .signIn, .passwordReset, .profilePhone(isWriting: true), .createMiffName(isSaving: true),
            .createMiffReady, .joinMiffCode, .joinMiffConfirm, .miff, .placeDetail, .placeForm(isBusy: false),
            .placePaywall, .settings, .deleteAccount(isBusy: false), .updateViewfinder, .updatePreview
        ]
        for screen in all {
            XCTAssertNotEqual(screen.allowsSwipeBack, screen.locksSwipeBack, "\(screen)")
        }
    }

    // MARK: - Le départ du geste

    func testTheGestureNeverStartsOnTheRootScreen() {
        XCTAssertFalse(SwipeBackPolicy.gestureMayBegin(stackDepth: 0, lockCount: 0, isTransitioning: false))
        XCTAssertFalse(SwipeBackPolicy.gestureMayBegin(stackDepth: 1, lockCount: 0, isTransitioning: false))
    }

    func testTheGestureStartsWhenThereIsAScreenToGoBackTo() {
        XCTAssertTrue(SwipeBackPolicy.gestureMayBegin(stackDepth: 2, lockCount: 0, isTransitioning: false))
        XCTAssertTrue(SwipeBackPolicy.gestureMayBegin(stackDepth: 5, lockCount: 0, isTransitioning: false))
    }

    func testALockedScreenBlocksTheGesture() {
        XCTAssertFalse(SwipeBackPolicy.gestureMayBegin(stackDepth: 3, lockCount: 1, isTransitioning: false))
        XCTAssertFalse(SwipeBackPolicy.gestureMayBegin(stackDepth: 3, lockCount: 2, isTransitioning: false))
    }

    func testTheGestureWaitsForAnAnimationInProgress() {
        XCTAssertFalse(SwipeBackPolicy.gestureMayBegin(stackDepth: 3, lockCount: 0, isTransitioning: true))
    }
}
