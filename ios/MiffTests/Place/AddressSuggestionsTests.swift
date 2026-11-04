import XCTest
@testable import Miff

/// Ce qui vient du service d'adresses est écrit par quelqu'un d'autre : nettoyé, borné, vérifié AVANT d'être montré ou écrit ; et le
/// même texte tapé deux fois ne coûte qu'une requête (ticket LOC-3 ; critères ADR1, ADR4).
@MainActor
final class AddressSuggestionsTests: XCTestCase {
    // MARK: - Nettoyage

    func testAPartIsCleanedTrimmedAndBounded() {
        XCTAssertEqual(AddressCleaning.part("\u{202E}17 rue\u{200B} "), "17 rue")
        XCTAssertNil(AddressCleaning.part("\u{200B}\u{202E}  "))
        XCTAssertEqual(AddressCleaning.part(String(repeating: "x", count: 300))?.utf16.count, 100)
    }

    func testASuggestionWithNothingVisibleInItsTitleIsLeftOut() {
        XCTAssertNil(AddressCleaning.suggestion(id: "s1", title: "\u{200B} ", subtitle: "75013 Paris"))
        let kept = AddressCleaning.suggestion(id: "s1", title: "17 Avenue d'Italie", subtitle: "\u{200B}")
        XCTAssertEqual(kept?.subtitle, "", "un sous-titre sans rien de visible est simplement vide")
    }

    func testTheWholeAddressIsTheTitleThenTheSubtitle() {
        let suggestion = testSuggestion()

        XCTAssertEqual(AddressCleaning.label(of: suggestion), "17 Avenue d'Italie, 75013 Paris, France")
        let alone = testSuggestion(title: "Paris", subtitle: "")
        XCTAssertEqual(AddressCleaning.label(of: alone), "Paris")
    }

    func testTheWholeAddressIsBoundedLikeTheRules() {
        let title = String(repeating: "a", count: 100)
        let long = testSuggestion(title: title, subtitle: String(repeating: "b", count: 100))

        XCTAssertLessThanOrEqual(AddressCleaning.label(of: long).utf16.count, PlaceLimits.addressMax)
    }

    func testResolvedAddressKeepsFiveDecimalsAndRefusesCoordinatesOffThePlanet() {
        let suggestion = testSuggestion()

        let good = AddressCleaning.resolved(suggestion: suggestion, lat: 48.8292675, lng: 2.3567938)
        XCTAssertEqual(good?.lat, 48.82927)
        XCTAssertEqual(good?.lng, 2.35679)
        XCTAssertEqual(good?.label, "17 Avenue d'Italie, 75013 Paris, France")
        XCTAssertNil(AddressCleaning.resolved(suggestion: suggestion, lat: 91, lng: 0))
        XCTAssertNil(AddressCleaning.resolved(suggestion: suggestion, lat: 0, lng: .nan))
    }

    func testTheListHasNoDuplicateAndFiveAtMost() {
        let same = [testSuggestion(id: "a"), testSuggestion(id: "b")]
        XCTAssertEqual(AddressCleaning.list(same).map { $0.id }, ["a"])

        let many = (1...9).map { testSuggestion(id: "s\($0)", title: "\($0) rue") }
        XCTAssertEqual(AddressCleaning.list(many).count, 5)
    }

    func testNoDescriptionShowsAnything() {
        XCTAssertFalse("\(testSuggestion())".contains("Italie"))
        XCTAssertFalse("\(testResolved)".contains("Italie"))
    }

    // MARK: - Le cache de la session

    func testTheSameTextTypedTwiceCostsOneRequest() async {
        let inner = FakeAddressSearch()
        let cached = CachedAddressSearch(inner)

        let first = await cached.search(query: "17 avenue")
        let second = await cached.search(query: "  17 AVENUE ")

        XCTAssertEqual(first, second)
        XCTAssertEqual(inner.queries, ["17 avenue"])
    }

    func testAnUnavailableAnswerIsNeverKept() async {
        let inner = FakeAddressSearch(answer: .unavailable)
        let cached = CachedAddressSearch(inner)

        _ = await cached.search(query: "17 avenue")
        inner.answer = .found([testSuggestion()])
        let second = await cached.search(query: "17 avenue")

        XCTAssertEqual(second, .found([testSuggestion()]))
        XCTAssertEqual(inner.queries.count, 2)
    }

    func testNoResultsIsKeptToo() async {
        let inner = FakeAddressSearch(answer: .noResults)
        let cached = CachedAddressSearch(inner)

        _ = await cached.search(query: "zzzzzz")
        _ = await cached.search(query: "zzzzzz")

        XCTAssertEqual(inner.queries.count, 1)
    }

    func testTheCacheIsBoundedAndOldestOnesGoFirst() async {
        let inner = FakeAddressSearch()
        let cached = CachedAddressSearch(inner, capacity: 2)

        _ = await cached.search(query: "aaa")
        _ = await cached.search(query: "bbb")
        _ = await cached.search(query: "ccc")
        _ = await cached.search(query: "aaa") // sortie du cache : redemandée

        XCTAssertEqual(inner.queries, ["aaa", "bbb", "ccc", "aaa"])
    }

    func testResetForgetsWhatWasTypedAndTellsTheServiceToo() async {
        let inner = FakeAddressSearch()
        let cached = CachedAddressSearch(inner)
        _ = await cached.search(query: "17 avenue")

        cached.reset()
        _ = await cached.search(query: "17 avenue")

        XCTAssertEqual(inner.queries.count, 2, "ce qui a été tapé ne survit pas à la session")
        XCTAssertEqual(inner.resets, 1)
    }

    func testAnAnswerWhoseSuggestionTheServiceForgotIsEvictedSoRetryAsksAgain() async {
        let inner = FakeAddressSearch()
        let cached = CachedAddressSearch(inner)
        _ = await cached.search(query: "17 avenue")
        inner.resolved = nil

        let resolved = await cached.resolve(testSuggestion())
        inner.resolved = testResolved
        _ = await cached.search(query: "17 avenue")

        XCTAssertNil(resolved)
        XCTAssertEqual(inner.queries.count, 2, "la réponse périmée n'est pas resservie : une vraie recherche repart")
    }

    func testResolveIsAlwaysAskedOfTheService() async {
        let inner = FakeAddressSearch()
        let cached = CachedAddressSearch(inner)

        let resolved = await cached.resolve(testSuggestion())

        XCTAssertEqual(resolved, testResolved)
        XCTAssertEqual(inner.resolveCalls.count, 1)
    }
}
