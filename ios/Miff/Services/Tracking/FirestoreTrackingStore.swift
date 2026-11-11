import Foundation

// SÉCURITÉ ET CONFIDENTIALITÉ, pour ce fichier : il ne fait que relayer des lectures et des lots déjà construits ailleurs. Les lieux lus
// perdent leur nom et leur adresse ici (le moteur n'en a pas besoin). Rien n'est journalisé. Les écritures passent par
// `MiffStore.commit`, donc par le garde `SessionWriteGuard` (aucun statut ni position sans l'identifiant de la session).

struct FirestoreTrackingStore: TrackingStore {
    private let base: MiffStore
    private let placeStore: PlaceStore

    init(base: MiffStore, placeStore: PlaceStore) {
        self.base = base
        self.placeStore = placeStore
    }

    func memberships(uid: String) async throws -> [MembershipRef] {
        try await base.readMemberships(uid: uid, fromServer: true)
    }

    func consent(miffId: String, uid: String) async throws -> SharingConsentRead? {
        try await base.readSharingConsent(miffId: miffId, uid: uid)
    }

    func places(miffId: String) async throws -> [PlaceFact] {
        // La même lecture que les écrans des lieux (identifiant, type et bornes vérifiés : un document qui ne peut pas être un lieu de
        // cette app est laissé de côté) ; le nom et l'adresse sont écartés.
        let found = try await placeStore.readPlaces(miffId: miffId)
        return found.map { PlaceFact(id: $0.id, lat: $0.lat, lng: $0.lng, radiusM: $0.radiusM, type: $0.type) }
    }

    func commit(_ writes: [DocWrite]) async throws {
        try await base.commit(writes)
    }

    func ownTravel(miffId: String, uid: String) async throws -> TravelRead? {
        try await base.readOwnTravel(miffId: miffId, uid: uid)
    }
}
