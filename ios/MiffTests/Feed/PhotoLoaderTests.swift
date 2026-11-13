// Les assertions citent des cas entiers : leurs lignes dépassent la limite.
// swiftlint:disable line_length
import XCTest
@testable import Miff

// La lecture paresseuse des photos (FEED-8, critères P1 à P4, Q2) : UNE lecture par photo et par statut, la mémoire d'abord, rien lu hors connexion,
// un refus jamais pris pour « expirée » (aucune suppression, jamais), la réciprocité dite calmement, deux demandes simultanées = une lecture.
// Miroir de `FirestorePhotoReaderTest.kt` d'Android. Le sens d'un refus (la phrase seulement si le serveur confirme que ma position n'est pas publiée) : `PhotoGateTests`.
@MainActor
final class PhotoLoaderTests: XCTestCase {
    private let source = FakePhotoSource()
    private let cache = PhotoCache()
    private let network = FakeNetwork()
    /// La note locale du suivi (seul un « oui » est fiable) et ce que dit le serveur de MON document de position (nil : pas su).
    private var ownPositionPublished = false
    private let ownServer = FakeOwnPositionServer()

    private func makeLoader() -> PhotoLoader {
        PhotoLoader(
            PhotoLoaderDependencies(
                source: source,
                cache: cache,
                isOnline: { [network] in network.isOnline() },
                ownPosition: OwnPositionResolver(
                    myUid: { "me" },
                    localPublished: { [unowned self] _ in self.ownPositionPublished },
                    server: ownServer,
                    nowMillis: { feedNow }
                ),
                nowMillis: { feedNow }
            )
        )
    }

    // MARK: - Une seule lecture

    func testAPhotoIsReadOnceThenGivenBackFromTheMemoryWithoutAnotherRead() async {
        let photo = PhotoFixtures.photo()
        source.answers = [.photo(photo)]
        let loader = makeLoader()

        let first = await loader.read(PhotoFixtures.request())
        let second = await loader.read(PhotoFixtures.request())
        let third = await loader.read(PhotoFixtures.request())

        XCTAssertEqual(first, .found(photo))
        XCTAssertEqual(second, .found(photo))
        XCTAssertEqual(third, .found(photo))
        XCTAssertEqual(source.fetches.count, 1, "une carte qui revient à l'écran ne relit rien")
    }

    func testANewStatusReadsTheNewPhotoOnce() async {
        let first = PhotoFixtures.photo(createdAgo: feedHours(2))
        let second = PhotoFixtures.photo(createdAgo: feedMinutes(1))
        source.answers = [.photo(first), .photo(second)]
        let loader = makeLoader()

        _ = await loader.read(PhotoFixtures.request(lastUpdate: feedNow - feedHours(2)))
        let next = await loader.read(PhotoFixtures.request(lastUpdate: feedNow - feedMinutes(1)))
        _ = await loader.read(PhotoFixtures.request(lastUpdate: feedNow - feedMinutes(1)))

        XCTAssertEqual(next, .found(second))
        XCTAssertEqual(source.fetches.count, 2)
    }

    func testTwoCardsAskingTheSamePhotoAtTheSameTimeShareOneRead() async {
        source.answers = [.photo(PhotoFixtures.photo())]
        source.delayNanoseconds = 50_000_000
        let loader = makeLoader()

        async let first = loader.read(PhotoFixtures.request())
        async let second = loader.read(PhotoFixtures.request())
        let results = await [first, second]

        XCTAssertEqual(source.fetches.count, 1)
        XCTAssertEqual(results[0], results[1])
    }

    func testPeekKnowsAPhotoReadBeforeWithoutReadingAnything() async {
        let photo = PhotoFixtures.photo()
        source.answers = [.photo(photo)]
        let loader = makeLoader()
        XCTAssertNil(loader.peek(PhotoFixtures.request()), "inconnue : il faut lire")

        _ = await loader.read(PhotoFixtures.request())

        XCTAssertEqual(loader.peek(PhotoFixtures.request()), .found(photo))
        XCTAssertEqual(source.fetches.count, 1)
    }

    // MARK: - Absente

    func testAMissingPhotoIsNotAskedAgainForTheSameStatus() async {
        source.answers = [.absent]
        let loader = makeLoader()

        let first = await loader.read(PhotoFixtures.request())
        let second = await loader.read(PhotoFixtures.request())

        XCTAssertEqual(first, .missing)
        XCTAssertEqual(second, .missing)
        XCTAssertEqual(source.fetches.count, 1)
    }

    // MARK: - Refusée : la réciprocité, jamais « expirée »

    func testARefusalWithoutMyOwnPositionIsTheCalmSentenceAndIsNotAskedAgain() async {
        source.answers = [.refused]
        ownPositionPublished = false
        ownServer.answer = false
        let loader = makeLoader()

        let first = await loader.read(PhotoFixtures.request())
        let second = await loader.read(PhotoFixtures.request())

        XCTAssertEqual(first, .needsOwnPosition)
        XCTAssertEqual(second, .needsOwnPosition)
        XCTAssertEqual(source.fetches.count, 1, "un refus coûte 3 lectures de règles : pas deux fois")
        XCTAssertEqual(ownServer.reads, 1, "ma position n'est lue qu'une fois (mémoire de 30 s)")
    }

    func testARefusalAfterIPublishedMyPositionIsNotTheReciprocityNothingIsShown() async {
        source.answers = [.refused]
        ownPositionPublished = true

        let result = await makeLoader().read(PhotoFixtures.request())

        XCTAssertEqual(result, .missing)
        XCTAssertEqual(ownServer.reads, 0, "la note locale « publiée » suffit : aucune lecture")
    }

    func testARefusalOnMyOwnCardIsNeverTheReciprocity() async {
        source.answers = [.refused]
        ownPositionPublished = false

        let result = await makeLoader().read(PhotoFixtures.request(uid: "me", isMe: true))

        XCTAssertEqual(result, .missing)
        XCTAssertEqual(ownServer.reads, 0)
    }

    func testOnceMyPositionIsPublishedAKnownRefusalIsReadAgainAndTheNewPhotoIsShown() async {
        let photo = PhotoFixtures.photo()
        source.answers = [.refused, .photo(photo)]
        ownPositionPublished = false
        ownServer.answer = false
        let loader = makeLoader()
        let before = await loader.read(PhotoFixtures.request())

        ownPositionPublished = true
        let after = await loader.read(PhotoFixtures.request())

        XCTAssertEqual(before, .needsOwnPosition)
        XCTAssertEqual(after, .found(photo))
        XCTAssertEqual(source.fetches.count, 2)
    }

    // MARK: - Hors connexion

    func testOfflineNothingIsReadAndTheCardShowsNothing() async {
        network.online = false
        let loader = makeLoader()

        let result = await loader.read(PhotoFixtures.request())

        XCTAssertEqual(result, .offline)
        XCTAssertTrue(source.fetches.isEmpty, "aucune lecture tentée sans réseau")
    }

    func testOfflineAPhotoAlreadyInMemoryIsStillShown() async {
        let photo = PhotoFixtures.photo()
        source.answers = [.photo(photo)]
        let loader = makeLoader()
        _ = await loader.read(PhotoFixtures.request())

        network.online = false
        let result = await loader.read(PhotoFixtures.request())

        XCTAssertEqual(result, .found(photo))
        XCTAssertEqual(source.fetches.count, 1)
    }

    func testAnUnreachableServerIsOfflineAndIsReadAgainTheNextTime() async {
        source.answers = [.unreachable, .photo(PhotoFixtures.photo())]
        let loader = makeLoader()

        let first = await loader.read(PhotoFixtures.request())
        let second = await loader.read(PhotoFixtures.request())

        XCTAssertEqual(first, .offline)
        guard case .found = second else {
            return XCTFail("la lecture suivante réussit")
        }
        XCTAssertEqual(source.fetches.count, 2)
    }

    // MARK: - Expiration

    func testAnExpiredCopyInMemoryIsReadAgainNotShown() async {
        let expired = PhotoFixtures.photo(createdAgo: feedHours(24))
        cache.putRead(miffId: testMiffId, uid: "uidA", photo: expired, lastUpdateAtMillis: feedNow - feedMinutes(20))
        source.answers = [.absent]

        let result = await makeLoader().read(PhotoFixtures.request())

        XCTAssertEqual(result, .missing)
    }

    // MARK: - Ma propre carte

    func testMyOwnPublishedPhotoIsShownOnMyCardWithNoReadAtAll() async {
        let photo = PhotoFixtures.photo(createdAgo: 0)
        cache.putOwn(miffId: testMiffId, uid: "me", photo: photo)

        let result = await makeLoader().read(PhotoFixtures.request(uid: "me", lastUpdate: feedNow, isMe: true))

        XCTAssertEqual(result, .found(photo))
        XCTAssertTrue(source.fetches.isEmpty, "le serveur n'est pas lu pour sa propre photo publiée")
    }

    // MARK: - Fin de session pendant une lecture

    func testAReadInFlightWhenTheSessionEndsLeavesNothingInMemoryAndAnotherAccountCannotPeek() async throws {
        source.answers = [.photo(PhotoFixtures.photo())]
        source.delayNanoseconds = 150_000_000
        let loader = makeLoader()
        let reading = Task { await loader.read(PhotoFixtures.request()) }
        try await Task.sleep(nanoseconds: 40_000_000)
        XCTAssertEqual(source.fetches.count, 1, "la lecture est partie")

        cache.clear()
        let result = await reading.value

        XCTAssertEqual(result, .missing, "la réponse tardive n'est pas montrée")
        XCTAssertEqual(cache.count, 0)
        XCTAssertNil(loader.peek(PhotoFixtures.request()), "un autre compte ne peut rien en tirer")
    }

    func testALateAbsenceOrRefusalAfterTheSessionEndedIsNotRememberedEither() async throws {
        for late in [PhotoFetch.absent, .refused] {
            source.answers = [late]
            source.delayNanoseconds = 100_000_000
            let loader = makeLoader()
            let reading = Task { await loader.read(PhotoFixtures.request()) }
            try await Task.sleep(nanoseconds: 30_000_000)

            cache.clear()
            _ = await reading.value

            let request = PhotoFixtures.request()
            XCTAssertFalse(cache.isMissing(miffId: request.miffId, uid: request.uid, lastUpdateAtMillis: request.lastUpdateAtMillis))
            XCTAssertFalse(cache.isRefused(miffId: request.miffId, uid: request.uid, lastUpdateAtMillis: request.lastUpdateAtMillis))
        }
    }

    // MARK: - Ma position publiée

    func testTheProbeSaysMyPositionIsPublishedOnlyForMyAccountAndThatMiff() {
        var state = LocalState(uid: "me")
        state.miffs[testMiffId] = LocalMiff()
        XCTAssertFalse(OwnPositionProbe.isPublished(state: state, uid: "me", miffId: testMiffId), "pas encore de position")
        state.miffs[testMiffId]?.positionAtMillis = feedNow

        XCTAssertTrue(OwnPositionProbe.isPublished(state: state, uid: "me", miffId: testMiffId))
        XCTAssertFalse(OwnPositionProbe.isPublished(state: state, uid: "other", miffId: testMiffId), "un autre compte")
        XCTAssertFalse(OwnPositionProbe.isPublished(state: state, uid: "me", miffId: "OtherMiffNumber0001"))
        XCTAssertFalse(OwnPositionProbe.isPublished(state: nil, uid: "me", miffId: testMiffId))
        XCTAssertFalse(OwnPositionProbe.isPublished(state: state, uid: nil, miffId: testMiffId))
    }
}
// swiftlint:enable line_length
