// Les assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

/// La description facultative, de la saisie à l'écriture (ticket description-update, critères P1 à P10, D9 à D12) avec de faux services : publier avec et
/// sans description, le MÊME texte dans chaque Miff, un échec qui laisse la photo ET le texte à l'écran, « Réessayer » qui renvoie les deux, le brouillon
/// oublié avec la photo, ma propre photo montrée avec sa description sans relecture. Miroir des cas d'`UpdatePublisherTest.kt` et
/// d'`UpdateViewModelTest.kt` d'Android.
@MainActor
final class UpdateDescriptionFlowTests: XCTestCase {
    private let rig = UpdateRig()
    private let permission = FakeCameraPermission()
    private var announced: [PublishedPhoto] = []
    private var publishedCounts: [Int] = []

    private func ids(_ count: Int) -> [String] {
        (0..<count).map { String(format: "MiffNumber%010d", $0) }
    }

    private func photoWrites() -> [DocWrite] {
        rig.store.writes.filter { $0.path.contains("/photos/") }
    }

    private func statusWrites() -> [DocWrite] {
        rig.store.writes.filter { $0.path.contains("/status/") }
    }

    private func makePublisher(store: PhotoStore? = nil) -> UpdatePublisher {
        var dependencies = UpdatePublisherDependencies(
            store: store ?? rig.store, device: rig.device, position: rig.position, network: rig.network,
            ledger: rig.ledger, environment: rig.environment
        )
        dependencies.isPositionPublished = { _ in true }
        dependencies.onPhotoPublished = { [unowned self] in self.announced.append($0) }
        return UpdatePublisher(dependencies)
    }

    private func makeModel(store: PhotoStore? = nil) -> UpdateViewModel {
        var dependencies = UpdateViewModelDependencies(
            publisher: makePublisher(store: store), cleaner: rig.makeCleaner(), ledger: rig.ledger, permission: permission,
            sharingStopped: { false }
        )
        dependencies.nowMillis = { [unowned self] in self.rig.now }
        let model = UpdateViewModel(dependencies)
        model.onPublished = { [unowned self] count in self.publishedCounts.append(count) }
        return model
    }

    private func withPhoto(_ model: UpdateViewModel) async {
        model.onOpened()
        await waitUntil { model.stage != .idle }
        model.onPhotoReady(UpdateFixtures.photo(takenAt: rig.now))
    }

    private func preview(_ model: UpdateViewModel) -> PreviewState? {
        if case .preview(let state) = model.stage {
            return state
        }
        return nil
    }

    // MARK: - Le publieur

    func testPublishingWithADescriptionWritesTheSameCleanedTextInTheDocumentOfThePhotoOfEveryMiff() async {
        rig.miffIds = ids(3)

        let result = await makePublisher().publish(UpdateFixtures.photo(), description: "  Bien   arrivée \n ")

        XCTAssertEqual(result, .published(sharedIn: 3))
        XCTAssertEqual(photoWrites().count, 3)
        for write in photoWrites() {
            XCTAssertEqual(write.data["description"], .string("Bien arrivée"))
            XCTAssertEqual(write.kind, .set)
        }
        XCTAssertEqual(statusWrites().count, 3, "toujours 2 écritures par Miff : la description n'ajoute rien")
        for status in statusWrites() {
            XCTAssertFalse(status.data.keys.contains { $0.lowercased().contains("description") })
        }
    }

    func testPublishingWithoutADescriptionOrWithOnlySpacesLeavesTheKeyOutNeverNullNeverEmpty() async {
        for description in [nil, "", "   ", "\n \t"] as [String?] {
            let fresh = UpdateRig()
            let publisher = UpdatePublisher(UpdatePublisherDependencies(
                store: fresh.store, device: fresh.device, position: fresh.position, network: fresh.network,
                ledger: fresh.ledger, environment: fresh.environment
            ))

            let result = await publisher.publish(UpdateFixtures.photo(), description: description)

            XCTAssertEqual(result, .published(sharedIn: 1))
            let photo = fresh.store.writes.first { $0.path.contains("/photos/") }
            XCTAssertEqual(photo.map { Set($0.data.keys) }, ["jpeg", "createdAt", "expiresAt", "lat", "lng", "measuredAt"])
        }
    }

    func testOfflineNothingIsWrittenAndNothingIsQueuedForLaterThenTheSameTextGoesOnRetry() async {
        let publisher = makePublisher()
        rig.network.online = false

        let offline = await publisher.publish(UpdateFixtures.photo(), description: "Coucou")

        XCTAssertEqual(offline, .offline)
        XCTAssertTrue(rig.store.writes.isEmpty, "aucune file d'attente : rien ne part tout seul plus tard")
        rig.network.online = true
        let retry = await publisher.publish(UpdateFixtures.photo(), description: "Coucou")
        XCTAssertEqual(retry, .published(sharedIn: 1))
        XCTAssertEqual(photoWrites().first?.data["description"], .string("Coucou"))
    }

    func testARefusalOfTheRulesWritesNothingAndTheRetryWithTheSameTextGoesThrough() async {
        // Les règles pas encore déployées refusent la clé inconnue : le client dit un refus calme, la photo et le texte restent à l'écran.
        let publisher = makePublisher()
        rig.store.failures = [firestoreError(FirestoreErrorNumber.permissionDenied)]

        let refused = await publisher.publish(UpdateFixtures.photo(), description: "Coucou")

        XCTAssertEqual(refused, .refused)
        XCTAssertTrue(rig.store.writes.isEmpty)
        let retry = await publisher.publish(UpdateFixtures.photo(), description: "Coucou")
        XCTAssertEqual(retry, .published(sharedIn: 1))
        XCTAssertEqual(photoWrites().first?.data["description"], .string("Coucou"))
    }

    func testAPartialSuccessSendsTheSameTextToTheMiffsThatRemain() async {
        rig.miffIds = ids(7)
        rig.store.failures = [nil, firestoreError(FirestoreErrorNumber.unavailable)]
        let publisher = makePublisher()

        let first = await publisher.publish(UpdateFixtures.photo(), description: "Même texte")

        guard case .partial(_, _, let remaining) = first else {
            return XCTFail("un succès partiel était attendu : \(first)")
        }
        XCTAssertEqual(remaining.count, 2)
        rig.now += 60_000
        let second = await publisher.publish(
            UpdateFixtures.photo(takenAt: rig.now), only: remaining, description: "Même texte"
        )
        XCTAssertEqual(second, .published(sharedIn: 2))
        XCTAssertEqual(photoWrites().count, 7)
        XCTAssertTrue(photoWrites().allSatisfy { $0.data["description"] == .string("Même texte") })
    }

    func testMyOwnPhotoIsToldToTheFeedWithItsDescriptionInMemoryAndShowsNothingOfIt() async throws {
        _ = await makePublisher().publish(UpdateFixtures.photo(), description: " Je suis là ")

        let told = try XCTUnwrap(announced.first)
        XCTAssertEqual(told.photo.descriptionText, "Je suis là")
        XCTAssertFalse("\(told)".contains("suis"))
        XCTAssertFalse("\(told.photo)".contains("suis"))
    }

    func testMyOwnPhotoWithoutADescriptionHasNone() async throws {
        _ = await makePublisher().publish(UpdateFixtures.photo(), description: "   ")

        XCTAssertNil(try XCTUnwrap(announced.first).photo.descriptionText)
    }

    // MARK: - L'écran

    func testTypingKeepsACleanedTextCutAt140AndSaysWhenTheLimitIsHit() async {
        let model = makeModel()
        await withPhoto(model)

        model.onDescriptionChanged("Salut\nça va ?")
        XCTAssertEqual(preview(model)?.descriptionText, "Salut ça va ?")
        XCTAssertEqual(preview(model)?.descriptionLimitHit, false)

        model.onDescriptionChanged(String(repeating: "a", count: 141))
        XCTAssertEqual(preview(model)?.descriptionText, String(repeating: "a", count: 140))
        XCTAssertEqual(preview(model)?.descriptionLimitHit, true)

        model.onDescriptionChanged("court")
        XCTAssertEqual(preview(model)?.descriptionLimitHit, false)
    }

    func testPublishingFromTheScreenSendsTheTextAndThePhotoOnceThenForgetsEverything() async {
        let model = makeModel()
        await withPhoto(model)
        model.onDescriptionChanged("Bien arrivée")

        model.onPublish()
        await waitUntil { !self.publishedCounts.isEmpty }

        XCTAssertEqual(publishedCounts, [1])
        XCTAssertEqual(photoWrites().count, 1)
        XCTAssertEqual(photoWrites().first?.data["description"], .string("Bien arrivée"))
        XCTAssertEqual(model.stage, .idle, "plus rien en mémoire après la publication")
    }

    func testPublishingWithoutTypingNeverBlocksAndWritesNoDescription() async {
        let model = makeModel()
        await withPhoto(model)

        XCTAssertTrue(preview(model)?.canPublish ?? false, "le champ ne bloque jamais « Publier »")
        model.onPublish()
        await waitUntil { !self.publishedCounts.isEmpty }

        XCTAssertNil(photoWrites().first?.data["description"])
    }

    func testAFailedSendingKeepsThePhotoAndTheTextAndRetryingSendsBoth() async {
        rig.store.failures = [firestoreError(FirestoreErrorNumber.permissionDenied)]
        let model = makeModel()
        await withPhoto(model)
        model.onDescriptionChanged("Je reviens vite")

        model.onPublish()
        await waitUntil { self.preview(model)?.notice != nil }

        XCTAssertEqual(preview(model)?.notice, .refused)
        XCTAssertEqual(preview(model)?.descriptionText, "Je reviens vite", "photo et description restent à l'écran")
        XCTAssertTrue(rig.store.writes.isEmpty)
        model.onPublish()
        await waitUntil { !self.publishedCounts.isEmpty }
        XCTAssertEqual(photoWrites().first?.data["description"], .string("Je reviens vite"))
    }

    func testOfflineKeepsTheTextOnScreenAndNothingIsSent() async {
        rig.network.online = false
        let model = makeModel()
        await withPhoto(model)
        model.onDescriptionChanged("Hors connexion")

        model.onPublish()
        await waitUntil { self.preview(model)?.notice != nil }

        XCTAssertEqual(preview(model)?.notice, .offline)
        XCTAssertEqual(preview(model)?.descriptionText, "Hors connexion")
        XCTAssertTrue(rig.store.writes.isEmpty)
    }

    func testRecommencerTheCrossAndLeavingTheUpdateForgetTheTextWithThePhoto() async {
        let model = makeModel()
        await withPhoto(model)
        model.onDescriptionChanged("À oublier")

        model.onRetake()
        XCTAssertNil(preview(model))
        model.onPhotoReady(UpdateFixtures.photo(takenAt: rig.now))
        XCTAssertEqual(preview(model)?.descriptionText, "", "une nouvelle photo démarre avec un champ vide")

        model.onDescriptionChanged("À oublier aussi")
        model.onClosed()
        XCTAssertEqual(model.stage, .idle)
        XCTAssertNil(preview(model))
    }

    func testAPhotoOlderThanFiveMinutesIsForgottenWithItsText() async {
        let model = makeModel()
        await withPhoto(model)
        model.onDescriptionChanged("Trop tard")

        rig.now += 4 * 60 * 1000
        model.onOpened()
        XCTAssertEqual(preview(model)?.descriptionText, "Trop tard", "moins de 5 minutes : la description suit la photo")

        rig.now += 2 * 60 * 1000
        model.onOpened()
        XCTAssertEqual(preview(model)?.notice, .stale)
        XCTAssertEqual(preview(model)?.descriptionText, "")
    }

    func testTheFieldIsNotEditableWhileSendingAndOutsideThePreview() async {
        let gate = GateStore(inner: rig.store)
        let model = makeModel(store: gate)
        model.onDescriptionChanged("Pas d'aperçu")
        XCTAssertNil(preview(model))

        await withPhoto(model)
        model.onDescriptionChanged("Avant l'envoi")
        model.onPublish()
        await waitUntil { gate.reached }
        XCTAssertEqual(preview(model)?.publishing, true)
        model.onDescriptionChanged("Pendant l'envoi")

        XCTAssertEqual(preview(model)?.descriptionText, "Avant l'envoi", "le texte ne change pas pendant l'envoi")
        gate.open()
        await waitUntil { !self.publishedCounts.isEmpty }
        XCTAssertEqual(photoWrites().first?.data["description"], .string("Avant l'envoi"))
    }
}

/// Une transaction qui attend qu'on la libère : pour regarder l'écran PENDANT l'envoi.
private final class GateStore: PhotoStore {
    private let inner: PhotoStore
    private var continuation: CheckedContinuation<Void, Never>?
    private(set) var reached = false

    init(inner: PhotoStore) {
        self.inner = inner
    }

    func commit(
        uid: String,
        miffIds: [String],
        requireConsent: Bool,
        build: @escaping ([String]) -> [DocWrite]
    ) async throws -> PhotoCommit {
        await withCheckedContinuation { waiting in
            continuation = waiting
            reached = true
        }
        return try await inner.commit(uid: uid, miffIds: miffIds, requireConsent: requireConsent, build: build)
    }

    func open() {
        continuation?.resume()
        continuation = nil
    }
}
// swiftlint:enable line_length
