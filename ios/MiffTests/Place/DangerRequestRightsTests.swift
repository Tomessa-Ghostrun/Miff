import XCTest
@testable import Miff

/// Qui peut quoi avec une zone de danger (décision du PM du 2026-11-17, qui remplace la décision D5 du ticket LIEU-REQ) :
///
/// | | gratuit | Miff+ |
/// |---|---|---|
/// | DEMANDER une zone de danger (membre) | oui, sans offre | oui |
/// | DÉCLARER directement une zone de danger (créateur) | offre Miff+ | oui |
/// | ACCEPTER une demande « danger » (créateur) | offre Miff+, la demande reste | oui |
/// | REFUSER une demande « danger » (créateur) | oui | oui |
///
/// La sécurité ne bouge pas : l'écriture d'un lieu `risk` reste réservée au créateur Miff+ par les règles Firestore ; le client ne fait que
/// le dire avant d'écrire. Les tests de source plus bas empêchent qu'un changement ultérieur remette une barrière sur la DEMANDE ou retire
/// celle de l'ACCEPTATION sans que personne le voie.
private struct SourceNotFound: Error {
    let relative: String
}

@MainActor
final class DangerRequestRightsTests: XCTestCase {
    private let creator = testCreatorUid
    private let requests = FakePlaceRequestStore()
    private let places = FakePlaceStore()
    private let placeRepository = FakePlaceRepository()

    private func repository() -> FirestorePlaceRequestRepository {
        FirestorePlaceRequestRepository(
            store: requests,
            places: places,
            placeRepository: placeRepository,
            newPlaceId: { "NEWPLACE00000000001" },
            timeoutSeconds: 1
        )
    }

    private func context(premium: Bool, places: [Place] = []) -> PlaceContext {
        let owned = OwnedMiff(id: testMiffId, name: "Famille", joinedAtMillis: 100)
        return PlaceContext(premium: premium, ownedMiffs: [owned], lastJoinedMiffId: testMiffId, places: places)
    }

    private func slot(premium: Bool, type: PlaceType) -> PlaceSlot {
        PlaceQuota.forCreate(
            premium: premium,
            type: type,
            existingIdsByMiff: ["m1": []],
            distinctPlaces: 0,
            newId: { "NEWID123456789012345" }
        )
    }

    // MARK: - Demander (membre) : jamais d'offre

    func testAskingNeverOpensTheOfferWhateverTheTypeAndThePlan() {
        for type in [PlaceType.trusted, .risk] {
            XCTAssertNil(PlaceQuota.forRequestTypeChange(to: type))
        }
    }

    // MARK: - Déclarer (créateur) : l'offre pour une zone de danger sans Miff+

    func testDeclaringADangerZoneDirectlyStillNeedsMiffPlus() {
        XCTAssertEqual(slot(premium: false, type: .risk), .needsPremium(.danger))
        XCTAssertEqual(slot(premium: true, type: .risk), .use("NEWID123456789012345"))
        XCTAssertEqual(slot(premium: false, type: .trusted), .use("p1"), "un lieu de confiance reste gratuit")
        XCTAssertEqual(PlaceQuota.forTypeChange(premium: false, from: .trusted, to: .risk), .danger)
        XCTAssertNil(PlaceQuota.forTypeChange(premium: true, from: .trusted, to: .risk))
        // Une zone de danger déjà là se garde, même quand Miff+ a pris fin.
        XCTAssertNil(PlaceQuota.forTypeChange(premium: false, from: .risk, to: .risk))
    }

    // MARK: - Accepter (créateur)

    func testAFreeCreatorCannotAcceptADangerRequestAndNothingIsWritten() async {
        placeRepository.context = context(premium: false)
        let request = testRequest(type: .risk)
        requests.requests = [request]

        let result = await repository().accept(uid: creator, request: request, knownCount: 1)

        XCTAssertEqual(result, .needsPremium(.danger))
        XCTAssertTrue(requests.commits.isEmpty, "la demande reste en attente")
        XCTAssertEqual(requests.requests.map { $0.id }, [request.id])
    }

    func testAMiffPlusCreatorAcceptsADangerRequestAsBefore() async {
        placeRepository.context = context(premium: true)
        let request = testRequest(type: .risk)
        requests.requests = [request]

        let result = await repository().accept(uid: creator, request: request, knownCount: 1)

        XCTAssertEqual(result, .done(placeId: "NEWPLACE00000000001"))
        XCTAssertEqual(requests.commits.count, 1)
        XCTAssertEqual(requests.commits[0][0].data["type"], .string("risk"))
        XCTAssertEqual(requests.commits[0][0].data["createdBy"], .string(creator))
    }

    func testACreatorWhoLostMiffPlusKeepsThePendingDangerRequestsAndStillAcceptsTheOthers() async {
        // Le créateur n'est plus Miff+ alors que deux demandes attendent : la zone de danger ouvre l'offre (sans rien perdre),
        // la demande « confiance » est acceptée dans les emplacements gratuits.
        placeRepository.context = context(premium: false)
        let danger = testRequest(slot: 1, type: .risk)
        let trusted = testRequest(slot: 2, type: .trusted)
        requests.requests = [danger, trusted]

        let blocked = await repository().accept(uid: creator, request: danger, knownCount: 2)
        let allowed = await repository().accept(uid: creator, request: trusted, knownCount: 2)

        XCTAssertEqual(blocked, .needsPremium(.danger))
        XCTAssertEqual(allowed, .done(placeId: "p1"))
        XCTAssertEqual(requests.commits.count, 1, "seule la demande « confiance » a été écrite")
    }

    func testAFreeCreatorWithAFullFreePlanStillGetsTheLimitOfferForATrustedRequest() async {
        let three = ["p1", "p2", "p3"].map { testPlace(id: $0, name: "Lieu \($0)") }
        placeRepository.context = context(premium: false, places: three)
        let request = testRequest(type: .trusted)
        requests.requests = [request]

        let result = await repository().accept(uid: creator, request: request, knownCount: 1)

        XCTAssertEqual(result, .needsPremium(.limit), "les quotas ne changent pas")
    }

    // MARK: - Refuser (créateur) : toujours possible

    func testAFreeCreatorCanAlwaysDeclineADangerRequestAndNothingIsSaidToTheRequester() async {
        placeRepository.context = context(premium: false)
        let request = testRequest(type: .risk)
        requests.requests = [request]

        let result = await repository().decline(request, knownCount: 1)

        XCTAssertEqual(result, .done)
        XCTAssertEqual(requests.commits.count, 1)
        XCTAssertEqual(requests.commits[0].count, 2, "la demande supprimée et le compteur : rien d'autre")
        XCTAssertEqual(requests.commits[0][0].kind, .delete)
        XCTAssertEqual(placeRepository.prepareCalls, 0, "refuser ne regarde pas l'abonnement")
    }

    // MARK: - Tests de source : où se trouve (ou non) la barrière Miff+

    private var iosRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Place
            .deletingLastPathComponent() // MiffTests
            .deletingLastPathComponent() // ios
    }

    private func source(_ relative: String) throws -> String {
        let url = iosRoot.appendingPathComponent(relative)
        // Un fichier introuvable fait ÉCHOUER le test, jamais un « ignoré » silencieux : une source déplacée ne doit pas désarmer la garde.
        guard FileManager.default.fileExists(atPath: url.path) else {
            XCTFail("Source introuvable (\(relative)).")
            throw SourceNotFound(relative: relative)
        }
        return try String(contentsOf: url, encoding: .utf8)
    }

    /// Le code seul : sans les lignes de commentaire (qui peuvent parler d'abonnement pour dire qu'on ne le lit pas).
    private func code(_ relative: String) throws -> String {
        try source(relative)
            .split(separator: "\n", omittingEmptySubsequences: false)
            .filter { !$0.trimmingCharacters(in: .whitespaces).hasPrefix("//") }
            .joined(separator: "\n")
    }

    func testTheRequestingSideNeverReadsTheSubscriptionNorOpensTheOffer() throws {
        let repository = try code("Miff/Services/Place/PlaceRequestRepository.swift")
        XCTAssertFalse(repository.contains("readEntitlement"), "demander ne lit pas l'abonnement")
        XCTAssertFalse(repository.contains("needsPremium"), "demander n'ouvre jamais l'offre")
        let contracts = try code("Miff/Services/Contracts/PlaceRequestContracts.swift")
        XCTAssertFalse(contracts.contains("let premium"), "le contexte d'une demande ne porte pas d'abonnement")
        let form = try code("Miff/ViewModels/PlaceFormViewModel+Request.swift")
        XCTAssertFalse(form.contains("needsPremium"))
        XCTAssertFalse(form.contains("premium"))
    }

    func testTheRequestFormDecidesTheTypeThroughTheRequestRuleAndShowsNoLock() throws {
        let model = try code("Miff/ViewModels/PlaceFormViewModel.swift")
        XCTAssertTrue(model.contains("PlaceQuota.forRequestTypeChange"))
        let choices = try code("Miff/Views/Place/PlaceFormChoices.swift")
        // Pas de pastille Miff+ sur le formulaire d'une demande.
        XCTAssertTrue(choices.contains("state.mode != .request"))
    }

    func testAcceptingAndDeclaringStillGoThroughTheSameQuotaAsACreation() throws {
        let creatorSide = try code("Miff/Services/Place/PlaceRequestRepository+Creator.swift")
        // Accepter applique la règle de création d'un lieu.
        XCTAssertTrue(creatorSide.contains("PlaceQuota.forCreate"))
        let repository = try code("Miff/Services/Place/PlaceRepository.swift")
        XCTAssertTrue(repository.contains("PlaceQuota.forTypeChange"), "modifier un lieu en zone de danger reste Miff+")
        let rules = try code("Miff/Models/Place/PlaceRules.swift")
        XCTAssertTrue(rules.contains("return .needsPremium(.danger)"), "déclarer une zone de danger reste Miff+")
    }

    func testTheTextsOfTheAcceptOfferAndThePendingStateExistInFrenchAndEnglish() throws {
        // Lu dans le VRAI catalogue, langue par langue (le texte de la langue du simulateur n'en vérifierait qu'une).
        let url = iosRoot.appendingPathComponent("Miff/Resources/Localizable.xcstrings")
        let data = try Data(contentsOf: url)
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        let strings = try XCTUnwrap(root["strings"] as? [String: [String: Any]])
        for key in [
            "place_request_paywall_danger", "place_request_badge_pending", "place_request_detail_banner_member",
            "place_request_detail_banner_creator", "place_request_form_hint", "place_request_decline",
            "miff_message_generic"
        ] {
            let localizations = strings[key]?["localizations"] as? [String: Any]
            for language in ["fr", "en"] {
                let unit = (localizations?[language] as? [String: Any])?["stringUnit"] as? [String: Any]
                let value = unit?["value"] as? String ?? ""
                XCTAssertFalse(value.isEmpty, "texte manquant : \(key) (\(language))")
            }
        }
    }

    func testAnUnexplainedRefusalIsNeverTheSentenceOfAMiffLimit() throws {
        // Le plafond de 10 par Miff n'est lisible que par le créateur : le demandeur ne le voit jamais affirmé sans preuve.
        let repository = try code("Miff/Services/Place/PlaceRequestRepository.swift")
        XCTAssertFalse(repository.contains("miffLimit"))
        let state = try code("Miff/ViewModels/PlaceFormState.swift")
        XCTAssertFalse(state.contains("requestLimitMiff"))
    }
}
