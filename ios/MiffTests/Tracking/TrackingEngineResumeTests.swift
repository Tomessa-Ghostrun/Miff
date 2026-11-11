import XCTest
@testable import Miff

/// REPRISE du partage d'une Miff (recette TestFlight du 2026-11-11 : la carte restait grise après « arrêter puis reprendre »). Arrêter supprime le
/// statut, la photo et la dernière position sur le serveur, la reprise récrit un statut `unknown` ; le suivi doit alors recalculer la présence
/// de la personne (zone actuelle) et la réécrire, au lieu d'attendre une entrée ou une sortie de zone qui peut ne jamais venir.
final class TrackingEngineResumeTests: XCTestCase {
    private let rig = TrackingRig()

    /// Les statuts écrits qui disent une PRÉSENCE (une écriture de la seule autorisation n'en dit aucune).
    private func presenceWrites() -> [DocWrite] {
        rig.store.statusWrites().filter { $0.data["presence"] != nil }
    }

    private func presences() -> [FieldValueSpec?] {
        presenceWrites().map { $0.data["presence"] }
    }

    /// La personne est arrivée chez elle : un statut `in_trusted` est écrit.
    private func arrivedHome() async {
        await rig.engine.refresh(.opened)
        await rig.engine.onTransition(.enter, zoneKey: rig.homeKey, atMillis: rig.env.now - 60 * 1000)
        rig.putPositionHome()
    }

    private func resume() async {
        await rig.engine.markResumed(testMiffId)
        await rig.engine.refresh(.sharingChanged)
    }

    func testWithoutTheResumeSignalAStaleKnownPresenceIsNotWrittenAgainWhichIsWhatLeftTheCardGrey() async {
        await arrivedHome()
        XCTAssertEqual(presenceWrites().count, 1)

        await rig.engine.refresh(.sharingChanged)

        XCTAssertEqual(presenceWrites().count, 1, "le suivi n'écrit que sur un changement de présence")
    }

    func testResumingWhileInsideTheTrustedPlaceWritesTheTrustedPresenceAgain() async {
        await arrivedHome()

        await resume()

        XCTAssertEqual(presences(), [.string("in_trusted"), .string("in_trusted")])
        let again = presenceWrites().last
        XCTAssertEqual(again?.data["placeId"], .string("p1"))
        XCTAssertTrue(rig.journal.kinds.contains(.statusAfterResume))
    }

    func testResumingAfterTheTrackingStoodDownAndWasWipedAlsoRecomputesFromTheCurrentPosition() async {
        await arrivedHome()
        // L'arrêt du partage : la Miff n'est plus éligible, le suivi efface tout.
        rig.store.consents[testMiffId] = SharingConsentRead(enabled: false, version: MiffSharingConsent.version)
        await rig.engine.refresh(.sharingChanged)
        XCTAssertNil(rig.local.state?.miffs[testMiffId])
        rig.store.consents[testMiffId] = SharingConsentRead(enabled: true, version: MiffSharingConsent.version)

        await resume()

        XCTAssertEqual(presences(), [.string("in_trusted"), .string("in_trusted")])
    }

    func testResumingWhileOutsideEveryZoneWithAPositionWritesOut() async {
        await arrivedHome()
        await rig.engine.onTransition(.exit, zoneKey: rig.homeKey, atMillis: rig.env.now - 30 * 1000)
        rig.putPositionFar()
        let before = presenceWrites().count

        await resume()

        XCTAssertEqual(presenceWrites().count, before + 1)
        let written = presenceWrites().last
        XCTAssertEqual(written?.data["presence"], .string("out"))
        XCTAssertNil(written?.data["lastUpdateAt"], "R-LIEU-1 : jamais réécrit pour passer les règles")
    }

    func testResumingWithoutAnyPositionAndOutsideEveryZoneInventsNothing() async {
        rig.position.fix = nil

        await resume()

        XCTAssertTrue(presenceWrites().isEmpty)
    }

    func testTheResumeFlagIsUsedOnceAndTheNextPassesWriteNothingMore() async {
        await arrivedHome()
        await resume()
        let written = presenceWrites().count

        await rig.engine.refresh(.sharingChanged)
        await rig.engine.refresh(.opened)

        XCTAssertEqual(presenceWrites().count, written)
        XCTAssertNil(rig.local.state?.resumedMiffs)
    }

    func testResumingPublishesThePositionAgainBecauseTheServerDeletedIt() async {
        await arrivedHome()
        let before = rig.store.positionWrites().count

        await resume()

        XCTAssertEqual(rig.store.positionWrites().count, before + 1)
    }

    func testNothingIsWrittenOnResumeWithoutAlwaysAndTheExactPosition() async {
        await arrivedHome()
        rig.env.snapshot = PermissionSnapshot(location: .always, preciseLocation: false, notifications: .authorized)
        let before = presenceWrites().count

        await resume()

        XCTAssertEqual(presenceWrites().count, before)
    }
}
