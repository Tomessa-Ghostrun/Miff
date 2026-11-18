import XCTest
@testable import Miff

/// Un refus du serveur à l'envoi d'une demande (ticket LIEU-DANGER-REQ, v2, miroir d'Android #267) : le plafond de 10 demandes par Miff n'est
/// lisible que par le CRÉATEUR (compteur réservé), le téléphone d'un membre ne peut donc pas le prouver. Un refus sans cause identifiée est
/// `.refused` (phrase générique), jamais une limite de la Miff ; le réseau coupé garde sa phrase. Les magasins sont des doubles.
final class PlaceRequestRefusalTests: XCTestCase {
    private let uid = testMemberUid
    private let requests = FakePlaceRequestStore()
    private let places = FakePlaceStore()
    private let now = TestBox(1_000_000)

    private func repository() -> FirestorePlaceRequestRepository {
        let clock = now
        return FirestorePlaceRequestRepository(
            store: requests,
            places: places,
            placeRepository: FakePlaceRepository(),
            newPlaceId: { "FRESHID0000000000001" },
            nowMillis: { clock.value },
            timeoutSeconds: 1
        )
    }

    private func offline() -> NSError {
        firestoreError(FirestoreErrorNumber.unavailable)
    }

    private func refused() -> NSError {
        firestoreError(FirestoreErrorNumber.permissionDenied)
    }

    private func premium() {
        places.entitlement = EntitlementRead(active: true, expiresAtMillis: now.value + 10_000)
    }

    /// La Miff existe et son créateur est quelqu'un d'autre que la personne.
    private func setUpMiff() {
        places.miffs[testMiffId] = testMiffDetails(id: testMiffId, name: "Famille", owner: testCreatorUid)
    }

    private func submit(_ draft: PlaceRequestDraft? = nil) async -> RequestSubmitResult {
        await repository().submit(
            uid: uid,
            miffId: testMiffId,
            draft: draft ?? testRequestDraft(),
            context: RequestContext(miffName: "Famille", mine: []),
            online: true
        )
    }

    func testAnUnexplainedRefusalIsRefusedNeverAMiffLimit() async {
        // Tous les emplacements sont libres : le téléphone ne peut PAS savoir si la Miff porte 10 demandes (compteur lisible du créateur
        // seul). Rien n'est affirmé : `.refused`, dit par la phrase générique.
        setUpMiff()
        premium()
        requests.commitFailures = [refused()]

        let result = await submit()

        XCTAssertEqual(result, .refused)
        XCTAssertEqual(requests.commits.count, 0)
    }

    func testARaceThenASecondRefusalIsRefusedNeverAMiffLimit() async {
        setUpMiff()
        requests.commitFailures = [refused(), refused()]
        // Un autre téléphone a pris l'emplacement 1 pendant que le formulaire était ouvert : le lot repart sur l'emplacement 2, refusé aussi.
        requests.requests = [testRequest(slot: 1)]

        let result = await submit()

        XCTAssertEqual(result, .refused)
        XCTAssertTrue(requests.commits.isEmpty, "aucun lot accepté")
    }

    func testARaceThenALostNetworkOnTheSecondLotKeepsTheOfflinePhrase() async {
        setUpMiff()
        requests.commitFailures = [refused(), offline()]
        requests.requests = [testRequest(slot: 1)]

        let result = await submit()

        XCTAssertEqual(result, .failed(.offline))
    }

    func testARefusalFollowedByAReReadThatFailsForAnotherReasonIsNeverAMiffLimit() async {
        setUpMiff()
        requests.commitFailures = [refused()]
        requests.readFailure = NSError(domain: "test", code: -1)

        let result = await submit()

        XCTAssertEqual(result, .failed(.unknown), "pas de plafond affirmé sur une relecture qui échoue autrement")
    }

    func testARefusalFollowedByAReReadThatLostTheNetworkKeepsTheOfflinePhrase() async {
        setUpMiff()
        requests.commitFailures = [refused()]
        requests.readFailure = offline()

        let result = await submit()

        XCTAssertEqual(result, .failed(.offline))
    }

    func testARefusedDangerZoneIsNeverAnOfferAndNeverReadsTheSubscription() async {
        // Plus d'offre pour le demandeur : un refus du serveur est une phrase calme, jamais Miff+.
        setUpMiff()
        places.entitlement = EntitlementRead(active: true, expiresAtMillis: now.value - 1)
        requests.commitFailures = [refused()]

        let result = await submit(testRequestDraft(type: .risk))

        XCTAssertEqual(result, .refused)
        XCTAssertFalse(places.calls.contains("entitlement"))
    }
}
