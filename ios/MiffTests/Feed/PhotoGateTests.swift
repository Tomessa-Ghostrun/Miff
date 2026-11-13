import XCTest
@testable import Miff

/// La phrase « Partage ta position pour voir cette photo. » (bug remonté par le PM, 2026-11-12) : elle ne s'affiche que s'il est SU que MA position n'est
/// pas publiée ET que les règles ont refusé la photo. Une photo absente, expirée, en cours de chargement ou refusée pour une autre raison n'affiche
/// jamais la phrase. Miroir de `PhotoGateTest.kt` d'Android (PR #213).
@MainActor
final class PhotoGateTests: XCTestCase {
    private let source = FakePhotoSource()
    private let server = FakeOwnPositionServer()
    private let cache = PhotoCache()
    private let network = FakeNetwork()
    private var localNote = false
    private var signedInUid: String? = "me"
    private var clock = feedNow
    private var resolverStorage: OwnPositionResolver?

    private func makeResolver() -> OwnPositionResolver {
        if let resolverStorage {
            return resolverStorage
        }
        let resolver = OwnPositionResolver(
            myUid: { [unowned self] in self.signedInUid },
            localPublished: { [unowned self] _ in self.localNote },
            server: server,
            nowMillis: { [unowned self] in self.clock }
        )
        resolverStorage = resolver
        return resolver
    }

    private func makeLoader(cache: PhotoCache? = nil) -> PhotoLoader {
        PhotoLoader(
            PhotoLoaderDependencies(
                source: source,
                cache: cache ?? self.cache,
                isOnline: { [network] in network.isOnline() },
                ownPosition: makeResolver(),
                nowMillis: { feedNow }
            )
        )
    }

    // MARK: - Partage actif (ma position est publiée sur le serveur)

    func testMyPositionPublishedAndAbsentPhotoShowsNoSentence() async {
        source.answers = [.absent]
        server.answer = true

        let result = await makeLoader().read(PhotoFixtures.request())

        XCTAssertEqual(result, .missing)
        XCTAssertEqual(server.reads, 0, "une photo absente n'est même pas une raison de regarder ma position")
    }

    func testMyPositionPublishedAndPhotoRefusedBecauseExpiredShowsNoSentenceEvenWhenTheLocalNoteIsEmpty() async {
        // Le cas du PM : la photo du proche est refusée (expirée), MA position EST sur le serveur, la note locale de la détection ne dit rien.
        source.answers = [.refused]
        server.answer = true
        localNote = false
        let loader = makeLoader()

        let result = await loader.read(PhotoFixtures.request())

        XCTAssertEqual(result, .missing)
        XCTAssertEqual(server.reads, 1)
        XCTAssertEqual(loader.peek(PhotoFixtures.request()), .missing, "rien n'est redemandé pour le même statut")
    }

    func testMyPositionPublishedAccordingToTheLocalNoteNeverReadsTheServerForIt() async {
        source.answers = [.refused]
        server.answer = false
        localNote = true

        let result = await makeLoader().read(PhotoFixtures.request())

        XCTAssertEqual(result, .missing)
        XCTAssertEqual(server.reads, 0)
    }

    func testMyPositionPublishedAndPhotoPresentShowsThePhoto() async {
        let photo = PhotoFixtures.photo()
        source.answers = [.photo(photo)]
        server.answer = true

        let result = await makeLoader().read(PhotoFixtures.request())

        XCTAssertEqual(result, .found(photo))
    }

    // MARK: - Partage non actif (ma position n'est pas publiée)

    func testMyPositionNotPublishedAndPhotoRefusedShowsTheSentence() async {
        source.answers = [.refused]
        server.answer = false

        let result = await makeLoader().read(PhotoFixtures.request())

        XCTAssertEqual(result, .needsOwnPosition)
    }

    func testMyPositionNotPublishedAndPhotoPresentShowsThePhoto() async {
        let photo = PhotoFixtures.photo()
        source.answers = [.photo(photo)]
        server.answer = false

        let result = await makeLoader().read(PhotoFixtures.request())

        XCTAssertEqual(result, .found(photo), "photo lisible = photo (règle du PM inchangée)")
    }

    func testMyPositionNotPublishedAndAbsentPhotoStillShowsNoSentenceThereIsNothingToLock() async {
        source.answers = [.absent]
        server.answer = false

        let result = await makeLoader().read(PhotoFixtures.request())

        XCTAssertEqual(result, .missing)
        XCTAssertEqual(server.reads, 0)
    }

    // MARK: - Inconnu : jamais la phrase

    func testMyPositionNotKnownNeverShowsTheSentence() async {
        source.answers = [.refused]
        server.answer = nil // hors connexion, lecture refusée, délai dépassé

        let result = await makeLoader().read(PhotoFixtures.request())

        XCTAssertNotEqual(result, .needsOwnPosition)
        XCTAssertEqual(result, .offline, "rien n'est dessiné ; la carte réessaiera au retour du réseau")
    }

    func testNotSignedInNeverShowsTheSentenceAndReadsNothingOfMyPosition() async {
        source.answers = [.refused]
        server.answer = false
        signedInUid = nil

        let result = await makeLoader().read(PhotoFixtures.request())

        XCTAssertNotEqual(result, .needsOwnPosition)
        XCTAssertEqual(server.reads, 0)
    }

    func testOfflineNeverShowsTheSentenceAndNothingIsRead() async {
        source.answers = [.refused]
        server.answer = false
        network.online = false

        let result = await makeLoader().read(PhotoFixtures.request())

        XCTAssertEqual(result, .offline)
        XCTAssertTrue(source.fetches.isEmpty)
        XCTAssertEqual(server.reads, 0)
    }

    func testWhileMyPositionIsBeingReadTheCardIsLoadingNeverTheSentence() async throws {
        source.answers = [.refused]
        server.answer = false
        server.delayNanoseconds = 120_000_000
        let loader = makeLoader()

        let reading = Task { await loader.read(PhotoFixtures.request()) }
        try await Task.sleep(nanoseconds: 40_000_000)
        XCTAssertNil(loader.peek(PhotoFixtures.request()), "pendant la lecture : état de chargement, rien n'est dit")
        let result = await reading.value

        XCTAssertEqual(result, .needsOwnPosition, "la phrase vient seulement quand le serveur a confirmé")
    }

    func testAnUnknownAnswerIsNotKeptSoTheNextAppearanceOfTheCardAsksAgain() async {
        source.answers = [.refused]
        server.answer = nil
        let loader = makeLoader()
        let first = await loader.read(PhotoFixtures.request())

        server.answer = false
        let second = await loader.read(PhotoFixtures.request())

        XCTAssertEqual(first, .offline)
        XCTAssertEqual(second, .needsOwnPosition)
        XCTAssertEqual(server.reads, 2)
    }

    func testAbsentOrUnreachablePhotoNeverShowsTheSentence() async {
        server.answer = false
        for answer in [PhotoFetch.absent, .unreachable] {
            source.answers = [answer]

            let result = await makeLoader(cache: PhotoCache()).read(PhotoFixtures.request())

            XCTAssertNotEqual(result, .needsOwnPosition)
        }
        XCTAssertEqual(server.reads, 0)
    }

    func testAnErrorOfTheFirestoreSourceThatIsNotARefusalIsNeverARefusal() {
        // Les erreurs autres que « refusé » : rien à montrer (jamais `refused`, donc jamais la phrase).
        let quota = NSError(domain: FirestoreErrorNumber.domain, code: FirestoreErrorNumber.resourceExhausted)
        let signedOut = NSError(domain: FirestoreErrorNumber.domain, code: FirestoreErrorNumber.unauthenticated)
        let denied = NSError(domain: FirestoreErrorNumber.domain, code: FirestoreErrorNumber.permissionDenied)

        XCTAssertEqual(FirestorePhotoSource.fetch(forError: quota), .absent)
        XCTAssertEqual(FirestorePhotoSource.fetch(forError: signedOut), .absent)
        XCTAssertEqual(FirestorePhotoSource.fetch(forError: denied), .refused)
    }

    // MARK: - Coût

    func testMyPositionIsReadAtMostOncePerHalfMinutePerMiff() async {
        server.answer = true
        let resolver = makeResolver()

        for _ in 0..<5 {
            let state = await resolver.state(miffId: testMiffId)
            XCTAssertEqual(state, .published)
        }
        XCTAssertEqual(server.reads, 1)
        _ = await resolver.state(miffId: "OtherMiffNumber0001")
        XCTAssertEqual(server.reads, 2, "une autre Miff : une autre lecture")

        clock += OwnPositionResolver.freshMillis + 1
        _ = await resolver.state(miffId: testMiffId)
        XCTAssertEqual(server.reads, 3, "après 30 s : relue")
    }

    func testTwoCardsAskingAtTheSameTimeShareOneRead() async {
        server.answer = false
        server.delayNanoseconds = 50_000_000
        let resolver = makeResolver()

        async let first = resolver.state(miffId: testMiffId)
        async let second = resolver.state(miffId: testMiffId)
        let results = await [first, second]

        XCTAssertEqual(results, [.notPublished, .notPublished])
        XCTAssertEqual(server.reads, 1)
    }

    func testKnownSaysWhatIsKnownWithoutReadingAnything() async {
        server.answer = false
        let resolver = makeResolver()
        XCTAssertNil(resolver.known(miffId: testMiffId), "rien n'est su sans lecture")

        _ = await resolver.state(miffId: testMiffId)
        XCTAssertEqual(resolver.known(miffId: testMiffId), .notPublished)
        XCTAssertEqual(server.reads, 1)

        clock += OwnPositionResolver.freshMillis + 1
        XCTAssertNil(resolver.known(miffId: testMiffId), "plus frais : à relire")
        localNote = true
        XCTAssertEqual(resolver.known(miffId: testMiffId), .published, "la note locale « publiée » prime")
    }

    func testAnAnswerIsNeverGivenToAnotherAccount() async {
        server.answer = false
        let resolver = makeResolver()
        _ = await resolver.state(miffId: testMiffId)

        signedInUid = "someoneElse"
        XCTAssertNil(resolver.known(miffId: testMiffId))
        server.answer = true
        let other = await resolver.state(miffId: testMiffId)

        XCTAssertEqual(other, .published)
        XCTAssertEqual(server.reads, 2)
    }

    func testOnceIPublishMyPositionARefusedPhotoIsReadAgainAndShown() async {
        let photo = PhotoFixtures.photo()
        source.answers = [.refused, .photo(photo)]
        server.answer = false
        let loader = makeLoader()
        let before = await loader.read(PhotoFixtures.request())

        localNote = true // je publie ma position : la photo devient lisible
        let after = await loader.read(PhotoFixtures.request())

        XCTAssertEqual(before, .needsOwnPosition)
        XCTAssertEqual(after, .found(photo))
    }
}
// swiftlint:enable line_length
