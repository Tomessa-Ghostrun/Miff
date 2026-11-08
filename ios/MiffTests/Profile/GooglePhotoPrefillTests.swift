import XCTest
@testable import Miff

private struct SizedPhoto: DecodedPhoto {
    let width: Int
    let height: Int
}

private final class FakePhotoSource: ProviderPhotoSource {
    var data: Data? = Data(count: 30_000)
    var delayNanoseconds: UInt64 = 0
    private(set) var fetched: [URL] = []

    func fetch(_ url: URL) async -> Data? {
        fetched.append(url)
        if delayNanoseconds > 0 {
            try? await Task.sleep(nanoseconds: delayNanoseconds)
        }
        return data
    }
}

private final class FakePhotoCodec: AvatarCodec {
    var decoded: (any DecodedPhoto)? = SizedPhoto(width: 1_200, height: 800)
    var encoded: Data? = Data(count: 15_000)
    private(set) var decodeCalls = 0
    private(set) var crops: [SquareCrop] = []

    func decode(_ source: Data) async -> (any DecodedPhoto)? {
        decodeCalls += 1
        return decoded
    }

    func encode(_ photo: any DecodedPhoto, crop: SquareCrop) async -> Data? {
        crops.append(crop)
        return encoded
    }
}

/// Le chemin entre l'adresse de la photo Google et l'avatar du profil : une seule requête, un carré au centre, les mêmes outils que la
/// photo choisie par la personne, et jamais d'erreur ni d'attente longue.
@MainActor
final class GooglePhotoPrefillTests: XCTestCase {
    private let address = URL(string: "https://lh3.googleusercontent.com/a/ACg8ocJabc=s96-c")

    private struct Setup {
        let prefill: GooglePhotoPrefill
        let source: FakePhotoSource
        let codec: FakePhotoCodec
    }

    private func make(
        source: FakePhotoSource = FakePhotoSource(),
        codec: FakePhotoCodec = FakePhotoCodec(),
        timeout: TimeInterval = 5
    ) -> Setup {
        let prefill = GooglePhotoPrefill(source: source, codec: codec, timeout: timeout)
        return Setup(prefill: prefill, source: source, codec: codec)
    }

    func testThePhotoIsDownloadedOnceAtTheRequestedSizeAndCroppedAtTheCentre() async {
        let setup = make()
        let jpeg = await setup.prefill.load(address)
        XCTAssertEqual(jpeg, setup.codec.encoded)
        let expected = "https://lh3.googleusercontent.com/a/ACg8ocJabc=s256-c"
        XCTAssertEqual(setup.source.fetched.map { $0.absoluteString }, [expected])
        XCTAssertEqual(setup.codec.decodeCalls, 1)
        // 1 200 x 800 : le plus grand carré, au centre.
        XCTAssertEqual(setup.codec.crops, [SquareCrop(left: 200, top: 0, size: 800)])
    }

    func testAnAddressThatIsNotGoogleIsNeverContacted() async {
        let setup = make()
        let jpeg = await setup.prefill.load(URL(string: "https://example.com/photo.jpg"))
        XCTAssertNil(jpeg)
        XCTAssertTrue(setup.source.fetched.isEmpty)
    }

    func testTheGenericSilhouetteIsNeverDownloaded() async {
        let setup = make()
        let jpeg = await setup.prefill.load(URL(string: "https://lh3.googleusercontent.com/a/default-user=s96-c"))
        XCTAssertNil(jpeg)
        XCTAssertTrue(setup.source.fetched.isEmpty)
    }

    func testNoAddressGivesNothing() async {
        let setup = make()
        let jpeg = await setup.prefill.load(nil)
        XCTAssertNil(jpeg)
        XCTAssertTrue(setup.source.fetched.isEmpty)
    }

    func testAFailedDownloadGivesNothing() async {
        let source = FakePhotoSource()
        source.data = nil
        let jpeg = await make(source: source).prefill.load(address)
        XCTAssertNil(jpeg)
    }

    func testAnImageThatCannotBeReadGivesNothing() async {
        let codec = FakePhotoCodec()
        codec.decoded = nil
        let jpeg = await make(codec: codec).prefill.load(address)
        XCTAssertNil(jpeg)
        XCTAssertTrue(codec.crops.isEmpty)
    }

    func testAnAvatarThatDoesNotFitTheLimitIsRefused() async {
        let codec = FakePhotoCodec()
        codec.encoded = Data(count: AvatarCompression.maxBytes + 1)
        let tooBig = await make(codec: codec).prefill.load(address)
        XCTAssertNil(tooBig)
        codec.encoded = Data()
        let empty = await make(codec: codec).prefill.load(address)
        XCTAssertNil(empty)
        codec.encoded = nil
        let failed = await make(codec: codec).prefill.load(address)
        XCTAssertNil(failed)
    }

    func testAnAvatarAtExactlyTheLimitIsAccepted() async {
        let codec = FakePhotoCodec()
        codec.encoded = Data(count: AvatarCompression.maxBytes)
        let jpeg = await make(codec: codec).prefill.load(address)
        XCTAssertEqual(jpeg?.count, 20_000)
    }

    func testASlowDownloadIsGivenUpAfterTheTimeout() async {
        let source = FakePhotoSource()
        source.delayNanoseconds = 3_000_000_000
        let started = Date()
        let jpeg = await make(source: source, timeout: 0.1).prefill.load(address)
        XCTAssertNil(jpeg)
        XCTAssertLessThan(Date().timeIntervalSince(started), 2)
    }

    func testTheDefaultTimeoutIsShort() {
        XCTAssertLessThanOrEqual(GooglePhotoPrefill.defaultTimeout, 10)
    }

    // MARK: - Le téléchargement réel

    func testTheDownloaderRefusesAnAddressThatIsNotGoogleWithoutAnyRequest() async throws {
        let local = await GooglePhotoDownloader().fetch(URL(fileURLWithPath: "/etc/hosts"))
        XCTAssertNil(local)
        let clearText = try XCTUnwrap(URL(string: "http://lh3.googleusercontent.com/a/abc"))
        let clear = await GooglePhotoDownloader().fetch(clearText)
        XCTAssertNil(clear)
    }

    func testTheDownloaderOnlyAcceptsASuccessfulImageAnswerFromGoogle() throws {
        let good = try XCTUnwrap(URL(string: "https://lh3.googleusercontent.com/a/abc=s256-c"))
        func response(_ url: URL, status: Int = 200, mime: String = "image/jpeg", length: Int = 1_000) -> URLResponse? {
            HTTPURLResponse(
                url: url,
                statusCode: status,
                httpVersion: "HTTP/1.1",
                headerFields: ["Content-Type": mime, "Content-Length": String(length)]
            )
        }
        XCTAssertTrue(GooglePhotoDownloader.isAcceptable(try XCTUnwrap(response(good))))
        XCTAssertFalse(GooglePhotoDownloader.isAcceptable(try XCTUnwrap(response(good, status: 404))))
        XCTAssertFalse(GooglePhotoDownloader.isAcceptable(try XCTUnwrap(response(good, mime: "text/html"))))
        XCTAssertFalse(GooglePhotoDownloader.isAcceptable(
            try XCTUnwrap(response(good, length: GooglePhotoDownloader.maxBytes + 1))
        ))
        let elsewhere = try XCTUnwrap(URL(string: "https://example.com/photo.jpg"))
        XCTAssertFalse(GooglePhotoDownloader.isAcceptable(try XCTUnwrap(response(elsewhere))))
        XCTAssertFalse(GooglePhotoDownloader.isAcceptable(URLResponse()))
    }
}
