import XCTest
@testable import Miff

/// L'écran Update (tickets Update, critères P1, P2, V1, V2, V5, V6, V8, U9) avec de faux services : la permission demandée seulement à
/// l'ouverture et après l'explication, la photo seulement en mémoire, la croix = « Recommencer », rien n'est publié sans photo, l'astuce du
/// double toucher qui disparaît pour de bon, le partage arrêté sans demande de permission. Miroir d'`UpdateViewModelTest.kt`.
@MainActor
final class UpdateViewModelTests: XCTestCase {
    private let rig = UpdateRig()
    private let permission = FakeCameraPermission()
    private var stopped = false
    private var sharingProbeCalls = 0
    private var published: [Int] = []

    private func makeModel() -> UpdateViewModel {
        var dependencies = UpdateViewModelDependencies(
            publisher: rig.makePublisher(),
            cleaner: rig.makeCleaner(),
            ledger: rig.ledger,
            permission: permission,
            sharingStopped: { [unowned self] in
                self.sharingProbeCalls += 1
                return self.stopped
            }
        )
        dependencies.nowMillis = { [unowned self] in self.rig.now }
        let model = UpdateViewModel(dependencies)
        model.onPublished = { [unowned self] count in self.published.append(count) }
        return model
    }

    private func opened(_ model: UpdateViewModel) async {
        model.onOpened()
        await waitUntil { model.stage != .idle }
    }

    private func withPhoto(_ model: UpdateViewModel) async {
        await opened(model)
        model.onPhotoReady(UpdateFixtures.photo(takenAt: rig.now))
    }

    private func preview(_ model: UpdateViewModel) -> PreviewState? {
        if case .preview(let state) = model.stage {
            return state
        }
        return nil
    }

    // MARK: - La caméra : l'explication d'abord, jamais à froid

    func testTheFirstOpeningShowsTheExplanationAndAsksNothing() async {
        permission.current = .notDetermined
        let model = makeModel()

        await opened(model)

        XCTAssertEqual(model.stage, .explain)
        XCTAssertEqual(permission.requests, 0, "la boîte du système n'est jamais montrée avant le geste de la personne")
    }

    func testTheExplanationThenAcceptingLeadsToTheViewfinderARefusalToTheSettings() async {
        permission.current = .notDetermined
        let model = makeModel()
        await opened(model)

        model.onExplanationAccepted()
        await waitUntil { model.stage != .explain }

        XCTAssertEqual(permission.requests, 1)
        XCTAssertEqual(model.stage, .viewfinder(showTip: true, prepareFailed: false))

        let refusing = FakeCameraPermission()
        refusing.current = .notDetermined
        refusing.grantsOnRequest = false
        let other = UpdateViewModel(
            UpdateViewModelDependencies(
                publisher: rig.makePublisher(), cleaner: rig.makeCleaner(), ledger: rig.ledger, permission: refusing,
                sharingStopped: { false }
            )
        )
        other.onOpened()
        await waitUntil { other.stage != .idle }
        other.onExplanationAccepted()
        await waitUntil { other.stage != .explain }

        XCTAssertEqual(other.stage, .permissionDisabled)
        other.openSettings()
        XCTAssertEqual(refusing.settingsOpened, 1)
    }

    func testAnAlreadyRefusedCameraShowsTheSettingsAndARestrictedOneHasNoSettingsButton() async {
        permission.current = .denied
        let denied = makeModel()
        await opened(denied)
        permission.current = .restricted
        let restricted = makeModel()
        await opened(restricted)

        XCTAssertEqual(denied.stage, .permissionDisabled)
        XCTAssertEqual(restricted.stage, .cameraRestricted)
        XCTAssertEqual(permission.requests, 0, "aucune boîte du système : elle ne revient jamais sur iPhone")
    }

    func testAuthorizedGoesStraightToTheViewfinderWithTheTip() async {
        let model = makeModel()

        await opened(model)

        XCTAssertEqual(model.stage, .viewfinder(showTip: true, prepareFailed: false))
    }

    func testComingBackToTheForegroundWithTheViewfinderAlreadyThereChangesNothing() async {
        let model = makeModel()
        await opened(model)
        let calls = sharingProbeCalls

        model.onOpened()

        XCTAssertEqual(model.stage, .viewfinder(showTip: true, prepareFailed: false))
        XCTAssertEqual(sharingProbeCalls, calls, "l'ouverture n'est pas comptée deux fois (aucune lecture de plus)")
    }

    func testAPermissionTakenBackInTheSettingsClosesTheViewfinderWithoutCrash() async {
        let model = makeModel()
        await opened(model)

        permission.current = .denied
        model.onOpened()
        await waitUntil { model.stage != .viewfinder(showTip: true, prepareFailed: false) }

        XCTAssertEqual(model.stage, .permissionDisabled)
    }

    func testSharingStoppedMeansNoViewfinderAndNoPermissionAsked() async {
        permission.current = .notDetermined
        stopped = true
        let model = makeModel()

        await opened(model)

        XCTAssertEqual(model.stage, .sharingStopped)
        XCTAssertEqual(permission.requests, 0)
    }

    func testACameraThatCannotStartIsACalmScreenNotAnErrorAndTryAgainComesBack() async {
        let model = makeModel()
        await opened(model)

        model.onCameraUnavailable()
        XCTAssertEqual(model.stage, .cameraUnavailable)
        model.onRetryCamera()

        XCTAssertEqual(model.stage, .viewfinder(showTip: true, prepareFailed: false))
    }

    // MARK: - L'astuce du double toucher (D38)

    func testTheTipShowsUntilTheFirstDoubleTapThatSwitchesTheCameraThenNeverAgain() async {
        let first = makeModel()
        await opened(first)
        XCTAssertEqual(first.stage, .viewfinder(showTip: true, prepareFailed: false))

        first.onSwitchedByDoubleTap()
        XCTAssertEqual(first.stage, .viewfinder(showTip: false, prepareFailed: false))

        // Rouvert, relancé : elle ne revient pas (état par installation).
        let second = makeModel()
        await opened(second)
        XCTAssertEqual(second.stage, .viewfinder(showTip: false, prepareFailed: false))
        XCTAssertTrue(rig.ledger.doubleTapUsed())
    }

    func testTheTipStaysWhenTheCameraIsSwitchedByTheButtonOnlyTheDoubleTapCounts() async {
        // Seul `onSwitchedByDoubleTap` la masque : un changement par le bouton n'appelle rien sur le modèle.
        let model = makeModel()
        await opened(model)

        XCTAssertFalse(rig.ledger.doubleTapUsed())
        XCTAssertEqual(model.stage, .viewfinder(showTip: true, prepareFailed: false))
    }

    // MARK: - La photo : en mémoire, Recommencer = la croix

    func testAPhotoTakenIsOnlyInMemoryNothingIsWrittenBeforePublier() async {
        let model = makeModel()

        await withPhoto(model)

        XCTAssertNotNil(preview(model))
        XCTAssertTrue(rig.store.lots.isEmpty)
        XCTAssertEqual(rig.position.calls, 0, "aucune position n'est mesurée avant « Publier » (D33)")
    }

    func testTheCrossAndRecommencerDoTheSameBackToTheViewfinderThePhotoForgottenNothingWritten() async {
        let model = makeModel()
        await withPhoto(model)

        model.onRetake()

        XCTAssertEqual(model.stage, .viewfinder(showTip: true, prepareFailed: false))
        XCTAssertTrue(rig.store.lots.isEmpty)
        XCTAssertEqual(rig.position.calls, 0, "ni croix ni « Recommencer » ne mesurent ni ne gardent une position")
        XCTAssertEqual(rig.ledger.countToday(), 0)
    }

    func testLeavingTheUpdateForgetsThePhotoWhateverTheWayOut() async {
        let model = makeModel()
        await withPhoto(model)

        model.onClosed()

        XCTAssertEqual(model.stage, .idle)
        XCTAssertNil(preview(model))
    }

    func testAPhotoThatCouldNotBePreparedGivesACalmSentenceOnTheViewfinderAndNoPreview() async {
        let model = makeModel()
        await opened(model)

        model.onPhotoFailed()

        XCTAssertEqual(model.stage, .viewfinder(showTip: true, prepareFailed: true))
        XCTAssertNil(preview(model))
    }

    func testAPhotoStillFreshWhenTheAppComesBackStaysAsItWasAnOldOneIsMarkedStale() async {
        let model = makeModel()
        await withPhoto(model)

        rig.now += 4 * 60 * 1000
        model.onOpened()
        XCTAssertNil(preview(model)?.notice)

        rig.now += 2 * 60 * 1000
        model.onOpened()
        XCTAssertEqual(preview(model)?.notice, .stale)
        XCTAssertFalse(preview(model)?.canPublish ?? true, "« Publier » est inactif : reprends une photo")
    }

    // MARK: - Publier

    func testNoPublicationWithoutAPhotoFromAnyStateButThePreview() async {
        let model = makeModel()
        permission.current = .notDetermined

        model.onPublish()
        await opened(model)
        model.onPublish()
        permission.current = .authorized
        model.onRetryCamera()
        model.onPublish()
        model.onPhotoFailed()
        model.onPublish()

        XCTAssertTrue(rig.store.lots.isEmpty)
        XCTAssertEqual(rig.position.calls, 0)
        XCTAssertTrue(published.isEmpty)
    }

    func testAPhotoPublishedOnceLeavesTheScreenAndTellsTheTabs() async {
        let model = makeModel()
        await withPhoto(model)

        model.onPublish()
        await waitUntil { !self.published.isEmpty }

        XCTAssertEqual(published, [1])
        XCTAssertEqual(model.stage, .idle)
        XCTAssertEqual(rig.store.writes.count, 2)
    }

    func testAPublicationThatFailsKeepsThePhotoOnTheScreenWithACalmSentenceAndCanBeTriedAgain() async {
        rig.store.failures = [NSError(domain: NSURLErrorDomain, code: NSURLErrorNotConnectedToInternet)]
        let model = makeModel()
        await withPhoto(model)

        model.onPublish()
        await waitUntil { self.preview(model)?.notice != nil }

        XCTAssertEqual(preview(model)?.notice, .offline)
        XCTAssertEqual(preview(model)?.publishing, false)
        XCTAssertTrue(published.isEmpty, "« Photo partagée » n'apparaît jamais sans l'accusé du serveur")

        rig.now += 60_000
        model.onPublish()
        await waitUntil { !self.published.isEmpty }
        XCTAssertEqual(published, [1])
    }

    func testWhileThePhotoLeavesNothingCanBeCancelledNorSentTwice() async {
        let model = makeModel()
        await withPhoto(model)

        model.onPublish()
        XCTAssertEqual(preview(model)?.publishing, true)
        model.onPublish()
        model.onRetake()
        XCTAssertEqual(preview(model)?.publishing, true, "pas d'annulation une fois l'envoi parti")
        await waitUntil { !self.published.isEmpty }

        XCTAssertEqual(rig.store.lots.count, 1, "un seul envoi")
    }

    func testAPartialSuccessSaysSoAndTheNextTryTargetsTheOthersOnly() async {
        rig.miffIds = (0..<6).map { String(format: "MiffNumber%010d", $0) }
        rig.store.failures = [nil, firestoreError(FirestoreErrorNumber.unavailable)]
        let model = makeModel()
        await withPhoto(model)

        model.onPublish()
        await waitUntil { self.preview(model)?.notice != nil }

        XCTAssertEqual(preview(model)?.notice, .partial(sharedIn: 5, total: 6))
        XCTAssertEqual(preview(model)?.remaining?.count, 1)
        XCTAssertTrue(published.isEmpty)
    }

    func testPublishingWithNoPositionKeepsThePhotoAndSaysItCalmly() async {
        rig.position.result = .notFound
        let model = makeModel()
        await withPhoto(model)

        model.onPublish()
        await waitUntil { self.preview(model)?.notice != nil }

        XCTAssertEqual(preview(model)?.notice, .positionNotFound)
        XCTAssertTrue(preview(model)?.canPublish ?? false, "« Publier » reste utilisable pour réessayer")
        XCTAssertTrue(rig.store.writes.isEmpty)
    }

    // MARK: - Nettoyage

    func testTheCleanUpRunsOnceAtATimeAndRemovesMyExpiredPhoto() async {
        rig.ledger.notePhotoSent(uid: updateUid, millis: rig.now - 30 * 60 * 60 * 1000)
        let model = makeModel()

        model.cleanUpExpiredPhoto(MyPhotoFacts(hasPhoto: true, lastUpdateAtMillis: nil))
        model.cleanUpExpiredPhoto(MyPhotoFacts(hasPhoto: true, lastUpdateAtMillis: nil))
        await waitUntil { !self.rig.store.lots.isEmpty }

        XCTAssertEqual(rig.store.lots.count, 1)
    }

    func testAPublicationThatEndsAfterTheScreenWasLeftLeavesNoPhotoInMemory() async {
        rig.store.failures = [NSError(domain: NSURLErrorDomain, code: NSURLErrorNotConnectedToInternet)]
        let model = makeModel()
        await withPhoto(model)

        model.onPublish()
        model.onClosed()
        await waitUntil { self.preview(model) == nil }

        XCTAssertEqual(model.stage, .idle, "l'écran est fermé : la photo est oubliée, même si l'envoi a échoué")
        XCTAssertTrue(published.isEmpty)
    }

    func testAPublicationThatSucceedsAfterTheScreenWasLeftIsStillAnnounced() async {
        let model = makeModel()
        await withPhoto(model)

        model.onPublish()
        model.onClosed()
        await waitUntil { !self.published.isEmpty }

        XCTAssertEqual(published, [1])
        XCTAssertEqual(model.stage, .idle)
    }

    func testPublishingIsRefusedCalmlyWhileTheCleanUpRunsAndTheCleanUpWaitsWhilePublishing() async {
        rig.ledger.notePhotoSent(uid: updateUid, millis: rig.now - 30 * 60 * 60 * 1000)
        let model = makeModel()
        await withPhoto(model)

        // Le nettoyage est en cours : « Publier » ne part pas.
        model.cleanUpExpiredPhoto(MyPhotoFacts(hasPhoto: true, lastUpdateAtMillis: nil))
        model.onPublish()
        XCTAssertEqual(preview(model)?.notice, .failed)
        await waitUntil { !self.rig.store.lots.isEmpty }
        XCTAssertFalse(rig.store.writes.contains { $0.path.contains("/photos/") && $0.kind == .set })

        // Une publication est en cours : le nettoyage attend (aucun lot de plus).
        await waitUntil { self.rig.ledger.photoSentMillis(uid: updateUid) == nil }
        try? await Task.sleep(nanoseconds: 100_000_000)
        rig.now += 60_000
        model.onPublish()
        model.cleanUpExpiredPhoto(MyPhotoFacts(hasPhoto: true, lastUpdateAtMillis: nil))
        await waitUntil { !self.published.isEmpty }
        XCTAssertEqual(rig.store.lots.count, 2, "le nettoyage d'avant et la publication, rien d'autre")
    }
}
