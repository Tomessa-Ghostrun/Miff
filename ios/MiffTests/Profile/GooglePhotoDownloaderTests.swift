import XCTest
@testable import Miff

/// Un faux serveur d'images (aucun vrai réseau) : répond à des adresses connues, note celles qu'on lui demande.
private class StubPhotoServer: URLProtocol {
    struct Reply {
        var status = 200
        var headers: [String: String] = ["Content-Type": "image/jpeg"]
        var body = Data(count: 1_000)
        var redirectTo: URL?
    }

    private static let lock = NSLock()
    private static var replies: [String: Reply] = [:]
    private static var asked: [String] = []

    static func reset() {
        lock.lock()
        replies = [:]
        asked = []
        lock.unlock()
    }

    static func answer(_ url: String, with reply: Reply) {
        lock.lock()
        replies[url] = reply
        lock.unlock()
    }

    static var requested: [String] {
        lock.lock()
        defer { lock.unlock() }
        return asked
    }

    private static func take(_ url: URL) -> Reply? {
        lock.lock()
        defer { lock.unlock() }
        asked.append(url.absoluteString)
        return replies[url.absoluteString]
    }

    override class func canInit(with request: URLRequest) -> Bool {
        true
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        guard let url = request.url, let reply = Self.take(url) else {
            client?.urlProtocol(self, didFailWithError: URLError(.fileDoesNotExist))
            return
        }
        let made = HTTPURLResponse(
            url: url, statusCode: reply.status, httpVersion: "HTTP/1.1", headerFields: reply.headers
        )
        guard let response = made else {
            client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
            return
        }
        if let target = reply.redirectTo {
            client?.urlProtocol(self, wasRedirectedTo: URLRequest(url: target), redirectResponse: response)
            return
        }
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: reply.body)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}

/// Le téléchargeur réel contre un faux serveur : taille limitée, seulement une image, redirections limitées et gardées dans Google.
final class GooglePhotoDownloaderTests: XCTestCase {
    private let first = "https://lh3.googleusercontent.com/a/one=s256-c"
    private let second = "https://lh4.googleusercontent.com/a/two=s256-c"
    private let third = "https://lh5.googleusercontent.com/a/three=s256-c"
    private let fourth = "https://lh6.googleusercontent.com/a/four=s256-c"

    override func setUp() {
        super.setUp()
        StubPhotoServer.reset()
    }

    override func tearDown() {
        StubPhotoServer.reset()
        super.tearDown()
    }

    private func downloader() -> GooglePhotoDownloader {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [StubPhotoServer.self]
        configuration.timeoutIntervalForRequest = 5
        configuration.timeoutIntervalForResource = 10
        return GooglePhotoDownloader(configuration: configuration)
    }

    private func fetch(_ text: String) async throws -> Data? {
        let url = try XCTUnwrap(URL(string: text))
        return await downloader().fetch(url)
    }

    private func redirect(_ from: String, to target: String) throws {
        let url = try XCTUnwrap(URL(string: target))
        StubPhotoServer.answer(
            from,
            with: StubPhotoServer.Reply(status: 302, headers: ["Location": target], body: Data(), redirectTo: url)
        )
    }

    // MARK: - La réponse

    func testAnImageAnswerIsReturned() async throws {
        StubPhotoServer.answer(first, with: StubPhotoServer.Reply())
        let data = try await fetch(first)
        XCTAssertEqual(data?.count, 1_000)
        XCTAssertEqual(StubPhotoServer.requested, [first])
    }

    func testAnAnswerOverTheSizeLimitIsRefusedWhileItStreams() async throws {
        var reply = StubPhotoServer.Reply()
        reply.body = Data(count: GooglePhotoDownloader.maxBytes + 1)
        StubPhotoServer.answer(first, with: reply)
        let data = try await fetch(first)
        XCTAssertNil(data)
    }

    func testAnAnswerThatDeclaresATooLargeBodyIsRefusedAtOnce() async throws {
        var reply = StubPhotoServer.Reply()
        reply.headers["Content-Length"] = String(GooglePhotoDownloader.maxBytes + 1)
        StubPhotoServer.answer(first, with: reply)
        let data = try await fetch(first)
        XCTAssertNil(data)
    }

    func testAnAnswerThatIsNotAnImageOrNotSuccessfulIsRefused() async throws {
        var page = StubPhotoServer.Reply()
        page.headers = ["Content-Type": "text/html"]
        StubPhotoServer.answer(first, with: page)
        let html = try await fetch(first)
        XCTAssertNil(html)
        var missing = StubPhotoServer.Reply()
        missing.status = 404
        StubPhotoServer.answer(second, with: missing)
        let notFound = try await fetch(second)
        XCTAssertNil(notFound)
    }

    func testAnAddressThatIsNotAllowedIsNeverRequested() async throws {
        let refused = try await fetch("http://lh3.googleusercontent.com/a/one")
        XCTAssertNil(refused)
        let elsewhere = try await fetch("https://example.com/a/one")
        XCTAssertNil(elsewhere)
        XCTAssertTrue(StubPhotoServer.requested.isEmpty)
    }

    // MARK: - Les redirections

    func testTwoRedirectsInsideGoogleAreFollowed() async throws {
        try redirect(first, to: second)
        try redirect(second, to: third)
        StubPhotoServer.answer(third, with: StubPhotoServer.Reply())
        let data = try await fetch(first)
        XCTAssertEqual(data?.count, 1_000)
        XCTAssertEqual(StubPhotoServer.requested, [first, second, third])
    }

    func testAThirdRedirectIsNotFollowed() async throws {
        try redirect(first, to: second)
        try redirect(second, to: third)
        try redirect(third, to: fourth)
        StubPhotoServer.answer(fourth, with: StubPhotoServer.Reply())
        let data = try await fetch(first)
        XCTAssertNil(data)
        XCTAssertFalse(StubPhotoServer.requested.contains(fourth))
    }

    private func request(_ text: String) throws -> URLRequest {
        URLRequest(url: try XCTUnwrap(URL(string: text)))
    }

    func testThePolicyRefusesARedirectOutsideGoogleOrInTheClear() throws {
        let policy = GooglePhotoRedirectPolicy()
        XCTAssertNil(policy.decide(try request("https://example.com/a")))
        XCTAssertNil(policy.decide(try request("http://lh3.googleusercontent.com/a")))
        XCTAssertNil(policy.decide(try request("https://googleusercontent.com.evil.example/a")))
        XCTAssertEqual(policy.followed, 0)
    }

    func testThePolicyFollowsTwoGoogleRedirectsThenStops() throws {
        let policy = GooglePhotoRedirectPolicy()
        let next = try request(second)
        XCTAssertNotNil(policy.decide(next))
        XCTAssertNotNil(policy.decide(next))
        XCTAssertNil(policy.decide(next))
        XCTAssertEqual(policy.followed, GooglePhotoRedirectPolicy.maxRedirects)
    }

    func testTheSessionDelegateAnswersThroughThePolicy() throws {
        let policy = GooglePhotoRedirectPolicy()
        let session = URLSession(configuration: .ephemeral)
        let origin = try XCTUnwrap(URL(string: first))
        let task = session.dataTask(with: origin)
        let made = HTTPURLResponse(url: origin, statusCode: 302, httpVersion: nil, headerFields: nil)
        let response = try XCTUnwrap(made)
        var answers: [URLRequest?] = []
        for target in ["https://example.com/a", second] {
            let next = try request(target)
            policy.urlSession(session, task: task, willPerformHTTPRedirection: response, newRequest: next) {
                answers.append($0)
            }
        }
        XCTAssertEqual(answers.count, 2)
        XCTAssertNil(answers[0])
        XCTAssertEqual(answers[1]?.url?.absoluteString, second)
        session.invalidateAndCancel()
    }
}
