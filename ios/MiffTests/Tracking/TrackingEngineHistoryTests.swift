import XCTest
@testable import Miff

/// TIME-3, critère TS-8b : le journal des changements de présence REPRODUIT la présence du moteur. À chaque changement de présence d'une Miff
/// écrit dans le statut, un fait de même état, même lieu et même instant est journalisé (mêmes scénarios que le Feed), et le temps que le suivi
/// ne peut pas confirmer (lieu supprimé ou déplacé, voyage, suivi arrêté, partage arrêté) devient « inconnue ». Critères TS-5 (effacement) et TS-2
/// (rien d'autre qu'un identifiant de lieu). Doubles pour le téléphone, le système et Firestore : aucune vraie position, aucun réseau.
final class TrackingEngineHistoryTests: XCTestCase {
    private let rig = TrackingRig()
    private let minute = TrackingFixtures.minute
    private let hour = TrackingFixtures.hour

    private func wire(_ state: PresenceState) -> String {
        switch state {
        case .trusted:
            return "in_trusted"
        case .danger:
            return "in_risk"
        case .out:
            return "out"
        case .unknown:
            return "unknown"
        }
    }

    /// Les statuts de présence écrits dans Firestore : (présence, lieu, instant).
    private func writtenTriples(_ target: TrackingRig? = nil) -> [String] {
        (target ?? rig).store.statusWrites().compactMap { write in
            guard case .string(let presence)? = write.data["presence"],
                  case .timestamp(let at)? = write.data["presenceAt"] else {
                return nil
            }
            var place = "-"
            if case .string(let id)? = write.data["placeId"] {
                place = id
            }
            return "\(presence)|\(place)|\(at)"
        }
    }

    /// Les faits du journal : les mêmes triplets.
    private func journalTriples(_ target: TrackingRig? = nil) -> [String] {
        (target ?? rig).history.facts.map { "\(wire($0.state))|\($0.placeId ?? "-")|\($0.atMillis)" }
    }

    private var trip: TravelPeriod {
        TravelPeriod(untilMillis: TrackingFixtures.noon + 2 * 24 * hour, untilDay: "2026-11-04")
    }

    /// Arrivée chez soi : la présence est « dans un lieu de confiance ».
    private func arrivedHome() async {
        await rig.engine.refresh(.opened)
        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now - 60 * minute)
    }

    // MARK: - Ce que le moteur écrit dans le statut, le journal le recopie

    func testArrivalAndDepartureAreJournalledWithTheSameStateThePlaceAndTheInstantAsTheStatus() async {
        await rig.engine.refresh(.opened)
        XCTAssertEqual(journalTriples(), [], "rien n'a changé à l'ouverture : aucun fait")
        let arrival = rig.env.now - 90 * minute
        let departure = rig.env.now - 10 * minute

        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: arrival)
        await rig.engine.onTransition(.exit, zoneKey: rig.homeKey, atMillis: departure)

        XCTAssertEqual(journalTriples(), ["in_trusted|p1|\(arrival)", "out|p1|\(departure)"])
        XCTAssertEqual(journalTriples(), writtenTriples())
    }

    func testTheSameScenarioAsTheFeedGivesTheSameFactsWhenTheTrustedZoneWinsOverTheDangerZone() async {
        let overlap = TrackingRig(places: [TrackingFixtures.home, TrackingFixtures.danger])
        await overlap.engine.refresh(.opened)
        let now = overlap.env.now

        await overlap.engine.onTransition(.enter, zoneKey: TrackingFixtures.danger.key, atMillis: now - 50 * minute)
        await overlap.engine.onTransition(.enter, zoneKey: TrackingFixtures.home.key, atMillis: now - 40 * minute)
        await overlap.engine.onTransition(.exit, zoneKey: TrackingFixtures.home.key, atMillis: now - 20 * minute)

        XCTAssertEqual(overlap.history.facts.map { $0.state }, [.danger, .trusted, .danger])
        XCTAssertEqual(journalTriples(overlap), writtenTriples(overlap))
    }

    func testAnEntryThatChangesNothingForTheMiffIsNotJournalled() async {
        await arrivedHome()

        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now)

        XCTAssertEqual(rig.history.facts.count, 1)
    }

    func testTheSamePlaceInTwoMiffsGivesOneFactPerMiff() async {
        rig.addMiff(testOtherMiffId, places: [TrackingFixtures.home])
        await rig.engine.refresh(.opened)

        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now - minute)

        XCTAssertEqual(rig.history.facts.map { $0.miffId }.sorted(), [testMiffId, testOtherMiffId].sorted())
        XCTAssertEqual(Set(rig.history.facts.map { $0.state }), [.trusted])
    }

    func testAMissedExitIsUnknownAndNeverOut() async {
        await arrivedHome()

        await rig.engine.onZoneState(zoneKey: rig.homeKey, isInside: false)

        XCTAssertEqual(rig.history.facts.map { $0.state }, [.trusted, .unknown])
        XCTAssertEqual(journalTriples(), writtenTriples())
    }

    // MARK: - Ce que le suivi ne peut plus confirmer devient « inconnue »

    func testADeletedPlaceBecomesUnknownAndItsIdentifierLeavesTheJournal() async {
        await arrivedHome()
        rig.store.places[testMiffId] = []

        await rig.engine.refresh(.sharingChanged)

        XCTAssertEqual(rig.history.facts.map { $0.state }, [.unknown, .unknown])
        XCTAssertTrue(rig.history.facts.allSatisfy { $0.placeId == nil }, "le lieu supprimé n'est plus nommé")
        XCTAssertEqual(rig.history.facts.last?.atMillis, rig.env.now)
    }

    func testAMovedPlaceBecomesUnknownAtTheMomentOfTheReading() async {
        await arrivedHome()
        rig.store.places[testMiffId] = [PlaceFact(id: "p1", lat: 10, lng: 10, radiusM: 100, type: .trusted)]

        await rig.engine.refresh(.sharingChanged)

        XCTAssertEqual(rig.history.facts.map { $0.state }.first, .trusted)
        XCTAssertEqual(rig.history.facts.last?.state, .unknown)
        XCTAssertEqual(rig.history.facts.last?.atMillis, rig.env.now)
    }

    func testAPlaceWhoseTypeChangedBecomesUnknown() async {
        await arrivedHome()
        rig.store.places[testMiffId] = [PlaceFact(
            id: "p1", lat: TrackingFixtures.homeLat, lng: TrackingFixtures.homeLng, radiusM: 100, type: .risk
        )]

        await rig.engine.refresh(.sharingChanged)

        XCTAssertEqual(rig.history.facts.last?.state, .unknown)
    }

    func testATripStartsAndEndsAsUnknown() async {
        await arrivedHome()

        let started = await rig.engine.setTravel(trip)
        XCTAssertEqual(started, .done)
        XCTAssertEqual(rig.history.facts.map { $0.state }, [.trusted, .unknown])
        XCTAssertEqual(rig.history.facts.last?.atMillis, rig.env.now)

        let ended = await rig.engine.endTravel()
        XCTAssertTrue(ended)
        XCTAssertEqual(rig.history.facts.first?.state, .trusted)
        XCTAssertEqual(rig.history.facts[1].state, .unknown)
        XCTAssertEqual(rig.history.facts[2].state, .unknown)
    }

    func testLosingTheAlwaysPermissionMakesTheFollowingTimeUnknown() async {
        await arrivedHome()
        rig.env.snapshot = PermissionSnapshot(location: .whenInUse, notifications: .authorized)

        await rig.engine.refresh(.accessChanged)

        XCTAssertEqual(rig.history.facts.map { $0.state }.first, .trusted)
        XCTAssertEqual(rig.history.facts.last?.state, .unknown)
    }

    func testStoppingTheSharingMakesTheTimeUnknownButKeepsTheJournalOfThatMiff() async {
        await arrivedHome()
        rig.store.consents[testMiffId] = SharingConsentRead(enabled: false, version: MiffSharingConsent.version)

        await rig.engine.refresh(.sharingChanged)

        XCTAssertEqual(rig.history.facts.map { $0.state }, [.trusted, .unknown])
        XCTAssertEqual(rig.history.purged, [], "D16 : le journal est gardé jusqu'à 35 jours")
    }

    func testResumingTheSharingJournalsThePresenceRecomputedFromTheKnownZones() async {
        await arrivedHome()
        let entered = rig.env.now - 60 * minute
        rig.putPositionHome()

        await rig.engine.markResumed(testMiffId)
        await rig.engine.refresh(.sharingChanged)

        XCTAssertEqual(rig.history.facts.map { $0.state }, [.trusted, .trusted])
        XCTAssertEqual(rig.history.facts.last?.placeId, "p1")
        XCTAssertEqual(rig.history.facts.last?.atMillis, entered)
    }

    // MARK: - TS-5 : l'effacement

    func testLeavingAMiffPurgesItsJournal() async {
        await arrivedHome()
        rig.store.memberships = []

        await rig.engine.refresh(.sharingChanged)

        XCTAssertEqual(rig.history.purged, [testMiffId])
        XCTAssertTrue(rig.history.facts.isEmpty)
    }

    func testLosingAccessToAMiffPurgesItsJournal() async {
        await arrivedHome()
        rig.store.consentFailure = NSError(
            domain: FirestoreErrorNumber.domain, code: FirestoreErrorNumber.permissionDenied
        )

        await rig.engine.refresh(.sharingChanged)

        XCTAssertEqual(rig.history.purged, [testMiffId])
    }

    func testANetworkProblemWhileRereadingDoesNotPurgeAnything() async {
        await arrivedHome()
        rig.store.consentFailure = NSError(domain: NSURLErrorDomain, code: NSURLErrorNotConnectedToInternet)

        await rig.engine.refresh(.sharingChanged)

        XCTAssertEqual(rig.history.purged, [])
        XCTAssertEqual(rig.history.facts.map { $0.state }, [.trusted])
    }

    func testTheEndOfTheSessionClearsTheWholeJournal() async {
        await arrivedHome()

        await rig.engine.stop()

        XCTAssertGreaterThanOrEqual(rig.history.clearAllCalls, 1)
        XCTAssertTrue(rig.history.facts.isEmpty)
    }

    func testNobodySignedInAtAWakeNeverErasesAnHistoryThatCannotBeRebuilt() async {
        await arrivedHome()
        // Un réveil avant le premier déverrouillage : le Trousseau est illisible, « personne n'est connecté » n'est qu'une supposition.
        rig.env.currentUid = nil

        await rig.engine.refresh(.opened)

        XCTAssertEqual(rig.history.clearAllCalls, 0)
        XCTAssertEqual(rig.history.facts.map { $0.state }, [.trusted])
    }

    func testTheCopyOfAnotherAccountTakesItsJournalWithIt() async {
        rig.local.state = LocalState(uid: "someone-else")

        await rig.engine.refresh(.opened)

        XCTAssertGreaterThanOrEqual(rig.history.clearAllCalls, 1)
    }

    // MARK: - TS-2 : rien d'autre qu'un identifiant de lieu

    func testNothingButPlaceIdentifiersIsEverJournalled() async {
        await arrivedHome()
        await rig.engine.onTransition(.exit, zoneKey: rig.homeKey, atMillis: rig.env.now)
        _ = await rig.engine.setTravel(trip)

        for fact in rig.history.facts {
            if let placeId = fact.placeId {
                XCTAssertTrue(Place.isPlaceId(placeId), "jamais une clé de zone (elle porte des coordonnées)")
            }
            XCTAssertTrue(PresenceHistoryFileStore.isMiffId(fact.miffId))
        }
    }
}
