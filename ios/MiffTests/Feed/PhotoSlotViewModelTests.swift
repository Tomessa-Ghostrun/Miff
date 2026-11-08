// Les assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

// Le view model de la photo d'UNE carte (FEED-8) avec une lecture factice : un chargement calme, la photo, la phrase de réciprocité, rien (absente,
// illisible, hors connexion), jamais une erreur ; une photo déjà montrée le reste hors connexion ; ce que la mémoire connaît n'a pas d'état de
// chargement ; une seule lecture par appel de `load`. Le décodage est réel (une vraie petite image).
@MainActor
final class PhotoSlotViewModelTests: XCTestCase {
    private let reader = FakePhotoReader()

    private func model(request: PhotoRequest = PhotoFixtures.request()) -> PhotoSlotViewModel {
        PhotoSlotViewModel(reader: reader, request: request)
    }

    func testTheCardStartsWithACalmLoadingFrameWhenNothingIsKnown() {
        XCTAssertEqual(model().state, .loading)
        XCTAssertTrue(reader.reads.isEmpty, "construire ne lit rien")
    }

    func testAPhotoFoundIsShownAndDecodedOnce() async throws {
        let photo = PhotoFixtures.photo()
        reader.answer = .found(photo)
        let slot = model()

        await slot.load(PhotoFixtures.request())
        let first = try XCTUnwrap(slot.image)
        await slot.load(PhotoFixtures.request())

        XCTAssertEqual(slot.state, .shown(photo))
        XCTAssertTrue(slot.image === first, "la même image : décodée une fois par photo")
        XCTAssertEqual(reader.reads.count, 2, "une lecture par appel (la mémoire répond ensuite)")
    }

    func testAPhotoThatIsNotAnImageIsNeverDrawnAndIsNotAnError() async {
        reader.answer = .found(PhotoFixtures.photo(jpeg: Data(repeating: 7, count: 1_000)))
        let slot = model()

        await slot.load(PhotoFixtures.request())

        XCTAssertEqual(slot.state, .nothing)
        XCTAssertNil(slot.image)
    }

    func testAMissingPhotoDrawsNothing() async {
        reader.answer = .missing
        let slot = model()

        await slot.load(PhotoFixtures.request())

        XCTAssertEqual(slot.state, .nothing)
    }

    func testTheReciprocityIsTheCalmTile() async {
        reader.answer = .needsOwnPosition
        let slot = model()

        await slot.load(PhotoFixtures.request())

        XCTAssertEqual(slot.state, .needsOwnPosition)
        XCTAssertNil(slot.image)
    }

    func testOfflineWithNothingKnownDrawsNothing() async {
        reader.answer = .offline
        let slot = model()

        await slot.load(PhotoFixtures.request())

        XCTAssertEqual(slot.state, .nothing)
    }

    func testOfflineKeepsAPhotoAlreadyShown() async {
        let photo = PhotoFixtures.photo()
        reader.answer = .found(photo)
        let slot = model()
        await slot.load(PhotoFixtures.request())

        reader.answer = .offline
        await slot.load(PhotoFixtures.request())

        XCTAssertEqual(slot.state, .shown(photo))
        XCTAssertNotNil(slot.image)
    }

    func testAMemberWhoStoppedSharingOrExpiredLaterDrawsNothingNotAnOldPhoto() async {
        reader.answer = .found(PhotoFixtures.photo())
        let slot = model()
        await slot.load(PhotoFixtures.request())

        reader.answer = .missing
        await slot.load(PhotoFixtures.request(lastUpdate: feedNow))

        XCTAssertEqual(slot.state, .nothing)
        XCTAssertNil(slot.image)
    }

    func testWhatTheMemoryAlreadyKnowsHasNoLoadingState() {
        let photo = PhotoFixtures.photo()
        reader.known = .found(photo)

        let slot = model()

        XCTAssertEqual(slot.state, .shown(photo))
        XCTAssertNotNil(slot.image)
    }

    func testAKnownAbsenceHasNoLoadingStateEither() {
        reader.known = .missing

        XCTAssertEqual(model().state, .nothing)
    }

    func testACancelledLoadAppliesNothing() async {
        reader.answer = .found(PhotoFixtures.photo())
        let slot = model()
        let task = Task { await slot.load(PhotoFixtures.request()) }
        task.cancel()

        await task.value

        XCTAssertEqual(slot.state, .loading, "une carte qui a quitté l'écran ne change plus")
    }

    func testTheRegistryGivesTheSameModelToTheSameMemberSoACardComingBackReadsNothing() {
        let photos = FeedPhotos(reader: reader)

        let first = photos.model(for: PhotoFixtures.request(uid: "uidA"))
        let again = photos.model(for: PhotoFixtures.request(uid: "uidA", lastUpdate: feedNow))
        let other = photos.model(for: PhotoFixtures.request(uid: "uidB"))

        XCTAssertTrue(first === again)
        XCTAssertFalse(first === other)
    }

    // MARK: - Le décodage

    func testOnlyAPlausibleSizeIsDecoded() {
        XCTAssertTrue(ImagePhotoDecoder.isAcceptable(width: 576, height: 1_024))
        XCTAssertTrue(ImagePhotoDecoder.isAcceptable(width: 2_000, height: 2_000), "4 millions de pixels : la limite")
        XCTAssertFalse(ImagePhotoDecoder.isAcceptable(width: 2_001, height: 2_000))
        XCTAssertFalse(ImagePhotoDecoder.isAcceptable(width: 20_000, height: 20_000), "une bombe de décompression")
        XCTAssertFalse(ImagePhotoDecoder.isAcceptable(width: 0, height: 100))
        XCTAssertFalse(ImagePhotoDecoder.isAcceptable(width: 100, height: -1))
    }

    func testTheDecoderReadsARealJpegAndRefusesBytesThatAreNotAnImage() {
        let decoder = ImagePhotoDecoder()

        XCTAssertNotNil(decoder.image(from: PhotoFixtures.jpeg(width: 60, height: 100)))
        XCTAssertNil(decoder.image(from: Data(repeating: 7, count: 1_000)))
        XCTAssertNil(decoder.image(from: Data()))
    }
}
