import Foundation
@testable import Miff

// Doubles en mémoire pour les tests des lieux (LOC-3). Aucun ne touche au réseau, à Firebase ni à MapKit ; les noms, adresses et
// coordonnées sont des valeurs de démonstration. Du Swift pur.

let testThirdMiffId = "QwErTyUiOpAsDfGhJkLz"
let testPlaceId = "PLACEID0000000000001"

/// Un lieu de démonstration d'une Miff.
func testPlace(
    id: String,
    name: String,
    miffId: String = testMiffId,
    type: PlaceType = .trusted,
    category: PlaceCategory? = .home,
    address: String? = "17 Avenue d'Italie, 75013 Paris, France",
    radiusM: Int = 100,
    createdBy: String = "u1"
) -> Place {
    Place(
        id: id,
        miffId: miffId,
        name: name,
        type: type,
        category: category,
        address: address,
        lat: 48.8292,
        lng: 2.3567,
        radiusM: radiusM,
        createdBy: createdBy
    )
}

/// Les champs d'un document de lieu tel que Firestore les rend (pour `Place.fromServer`).
func placeFields(name: String = "Maison") -> [String: Any] {
    [
        "name": name,
        "type": "trusted",
        "category": "home",
        "address": "17 Avenue d'Italie, 75013 Paris, France",
        "lat": 48.8292,
        "lng": 2.3567,
        "radiusM": Int64(100),
        "createdBy": "u1"
    ]
}

/// Une suggestion d'adresse de démonstration.
func testSuggestion(
    id: String = "s1",
    title: String = "17 Avenue d'Italie",
    subtitle: String = "75013 Paris, France"
) -> AddressSuggestion {
    AddressSuggestion(id: id, title: title, subtitle: subtitle)
}

/// L'adresse choisie qui va avec `testSuggestion()`.
let testResolved = ResolvedAddress(label: "17 Avenue d'Italie, 75013 Paris, France", lat: 48.82927, lng: 2.35679)

/// Ce que Firestore détient pour les lieux, et ce qu'on lui a demandé (l'ordre des lectures, ce qui a été écrit).
final class FakePlaceStore: PlaceStore {
    var entitlement: EntitlementRead?
    var entitlementFailure: Error?
    var memberships: [PlaceMembership] = []
    var membershipsFailure: Error?
    /// `get miffs/{id}` : un `MiffDetails`, ou une erreur ; absent = la Miff n'existe pas.
    var miffs: [String: Any] = [:]
    /// Les lieux que chaque Miff détient en ce moment.
    var places: [String: [Place]] = [:]
    var placesFailure: Error?
    /// Ce qu'a dit le serveur pour chaque lot : nil = accepté, une erreur = le lot a échoué (rien n'est alors écrit).
    var commitFailures: [Error?] = []
    /// Tourne quand un lot part, avant qu'il compte (pour simuler un lot parti dont la réponse se perd).
    var onCommit: ([DocWrite]) -> Void = { _ in }

    private(set) var commits: [[DocWrite]] = []
    private(set) var calls: [String] = []

    func readEntitlement(uid: String) async throws -> EntitlementRead? {
        calls.append("entitlement")
        if let entitlementFailure {
            throw entitlementFailure
        }
        return entitlement
    }

    func readMemberships(uid: String) async throws -> [PlaceMembership] {
        calls.append("memberships")
        if let membershipsFailure {
            throw membershipsFailure
        }
        return memberships
    }

    func readMiff(miffId: String) async throws -> MiffRead {
        calls.append("miff:\(miffId)")
        guard let answer = miffs[miffId] else {
            return .missing
        }
        if let failure = answer as? Error {
            throw failure
        }
        if let details = answer as? MiffDetails {
            return .exists(details)
        }
        return .missing
    }

    func readPlaces(miffId: String) async throws -> [Place] {
        calls.append("places:\(miffId)")
        if let placesFailure {
            throw placesFailure
        }
        return places[miffId] ?? []
    }

    func commit(_ writes: [DocWrite]) async throws {
        calls.append("commit")
        let failure: Error? = commitFailures.isEmpty ? nil : commitFailures.removeFirst()
        onCommit(writes)
        if let failure {
            throw failure
        }
        commits.append(writes)
    }

    func seed(_ id: String, miffs miffIds: [String], type: PlaceType = .trusted) {
        for miffId in miffIds {
            places[miffId, default: []].append(testPlace(id: id, name: "Maison", miffId: miffId, type: type))
        }
    }
}

/// Les réponses de la recherche d'adresse, et ce qui a été demandé (pour prouver la pause, le minimum et l'annulation).
@MainActor
final class FakeAddressSearch: AddressSearch {
    var answer: AddressSearchResult
    var resolved: ResolvedAddress? = testResolved
    /// Une pause dans la réponse, en nanosecondes (0 = tout de suite).
    var delayNanos: UInt64 = 0
    private(set) var queries: [String] = []
    private(set) var resolveCalls: [AddressSuggestion] = []
    private(set) var resets = 0

    init(answer: AddressSearchResult = .found([testSuggestion()])) {
        self.answer = answer
    }

    func search(query: String) async -> AddressSearchResult {
        queries.append(query)
        // La réponse est celle du moment de la demande : une réponse tardive à une frappe périmée garde sa valeur d'origine.
        let current = answer
        if delayNanos > 0 {
            try? await Task.sleep(nanoseconds: delayNanos)
        }
        return current
    }

    func resolve(_ suggestion: AddressSuggestion) async -> ResolvedAddress? {
        resolveCalls.append(suggestion)
        if delayNanos > 0 {
            try? await Task.sleep(nanoseconds: delayNanos)
        }
        return resolved
    }

    func reset() {
        resets += 1
    }
}

/// Le dépôt des lieux tel que les view models le voient : ce que dit `prepare`, et chaque appel reçu.
final class FakePlaceRepository: PlaceRepository {
    var context: PlaceContext?
    var prepareFailure: MiffFailure?
    var createResult = PlaceWriteResult.done(placeId: "p1")
    var updateResult = PlaceWriteResult.done(placeId: "p1")
    var deleteResult = PlaceWriteResult.done(placeId: "p1")
    private(set) var created: [PlaceDraft] = []
    private(set) var updated: [PlaceDraft] = []
    private(set) var deleted: [(placeId: String, miffIds: [String])] = []
    private(set) var prepareCalls = 0
    /// Tenue fermée : une écriture « en route » (pour prouver qu'un second appui ne fait rien).
    var gate: TestGate?

    func prepare(uid: String) async -> PlaceContextLoad {
        prepareCalls += 1
        if let prepareFailure {
            return .failed(prepareFailure)
        }
        return .loaded(context ?? PlaceContext(premium: false, ownedMiffs: [], lastJoinedMiffId: nil, places: []))
    }

    func create(uid: String, draft: PlaceDraft, context: PlaceContext) async -> PlaceWriteResult {
        created.append(draft)
        await gate?.wait()
        return createResult
    }

    func update(
        uid: String,
        original: PlaceGroup,
        draft: PlaceDraft,
        context: PlaceContext
    ) async -> PlaceWriteResult {
        updated.append(draft)
        await gate?.wait()
        return updateResult
    }

    func delete(placeId: String, miffIds: [String]) async -> PlaceWriteResult {
        deleted.append((placeId: placeId, miffIds: miffIds))
        await gate?.wait()
        return deleteResult
    }
}
