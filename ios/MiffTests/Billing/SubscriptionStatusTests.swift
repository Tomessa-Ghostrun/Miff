// Quelques assertions portent un message en clair : elles dépassent la limite de ligne.
// swiftlint:disable line_length
import FirebaseFirestore
import XCTest
@testable import Miff

/// Ce que l'app sait du Miff+ du compte (ticket `docs/tickets/premium-achat-reel.md` §8 et critères AN6, AN7, AN8) : l'ACCÈS vient des dates, comme les règles ; un document ancien ou
/// avec des champs inconnus ne plante jamais.
final class SubscriptionStatusTests: XCTestCase {
    private let now = 1_800_000_000_000
    private let day = 86_400_000

    private func status(
        _ document: EntitlementDocument?
    ) -> SubscriptionStatus {
        interpretSubscription(document, nowMillis: now)
    }

    private func store(
        state: String?,
        active: Bool? = true,
        end: Int? = nil,
        source: String = "app_store",
        trial: Bool? = nil
    ) -> EntitlementDocument {
        EntitlementDocument(
            active: active, expiresAtMillis: end ?? now + 30 * day, source: source, state: state, plan: "yearly", trial: trial
        )
    }

    private func subscription(_ status: SubscriptionStatus) -> StoreSubscription? {
        if case .store(let value) = status {
            return value
        }
        return nil
    }

    func testNoDocumentMeansFree() {
        XCTAssertEqual(status(nil), .free)
        XCTAssertFalse(status(nil).hasAccess)
    }

    func testAManualAccessKeepsTheOldBehaviourAndAnOldDocumentWithoutTheNewFieldsStillReads() {
        let old = EntitlementDocument(active: true, expiresAtMillis: now + day, source: "manual")

        XCTAssertEqual(status(old), .manual(expiresAtMillis: now + day))
        XCTAssertTrue(status(old).hasAccess)
    }

    func testAnEndedManualAccessLeavesNoTraceOfASubscription() {
        XCTAssertEqual(status(EntitlementDocument(active: true, expiresAtMillis: now - 1, source: "manual")), .free)
        XCTAssertEqual(status(EntitlementDocument(active: false, expiresAtMillis: now + day, source: "manual")), .free)
    }

    func testEveryStateOfAPurchaseGrantsOrNotLikeTheTicketTable() {
        // État écrit par le serveur, et si l'accès est allumé (la grâce garde l'accès, la suspension le coupe).
        let table: [(String, Bool)] = [
            ("trial", true), ("active", true), ("canceled", true), ("grace", true),
            ("on_hold", false), ("expired", false), ("revoked", false)
        ]
        for (state, access) in table {
            let end = access ? now + day : now - day
            let result = status(store(state: state, active: access, end: end))

            XCTAssertEqual(result.hasAccess, access, state)
            XCTAssertEqual(subscription(result)?.source, .appStore)
        }
    }

    func testTheDatesWinOverTheDeclaredState() {
        let declaredActiveButOver = store(state: "active", active: true, end: now - day)
        let declaredExpiredButRunning = store(state: "expired", active: true, end: now + day)

        XCTAssertEqual(subscription(status(declaredActiveButOver))?.state, .expired)
        XCTAssertEqual(subscription(status(declaredExpiredButRunning))?.state, .active)
        XCTAssertFalse(status(declaredActiveButOver).hasAccess)
        XCTAssertTrue(status(declaredExpiredButRunning).hasAccess)
    }

    func testAnUnknownOrMissingStateIsDeducedFromTheDates() {
        XCTAssertEqual(subscription(status(store(state: "mystery")))?.state, .active)
        XCTAssertEqual(subscription(status(store(state: nil, trial: true)))?.state, .trial)
        XCTAssertEqual(subscription(status(store(state: nil, active: false, end: now - 1)))?.state, .expired)
    }

    func testADocumentWithoutAnyEndNeverGrantsAnything() {
        let document = EntitlementDocument(active: true, expiresAtMillis: nil, source: "app_store", state: "active")

        XCTAssertFalse(status(document).hasAccess)
    }

    func testAnAccessOfUnknownOriginKeepsTheNeutralWordingAndIsFreeWhenOver() {
        let running = store(state: nil, source: "somewhere")
        let over = store(state: nil, active: true, end: now - 1, source: "somewhere")

        XCTAssertEqual(subscription(status(running))?.source, .other)
        XCTAssertEqual(status(over), .free)
    }

    func testTheTestFlagAndThePlanAreRead() {
        let document = EntitlementDocument(
            active: true, expiresAtMillis: now + day, source: "app_store", state: "trial", plan: "monthly", trial: true,
            willRenew: false, test: true
        )

        let result = subscription(status(document))

        XCTAssertEqual(result?.plan, .monthly)
        XCTAssertEqual(result?.test, true)
        XCTAssertEqual(result?.state, .trial)
    }

    func testTheDatesAreWrittenByTheSystemFormatterInTheLongFormatOfTheLanguage() {
        let utc = TimeZone(identifier: "UTC") ?? .current

        XCTAssertEqual(SubscriptionDates.long(millis: now, locale: Locale(identifier: "fr_FR"), zone: utc), "15 janvier 2027")
        XCTAssertEqual(SubscriptionDates.long(millis: now, locale: Locale(identifier: "en_US"), zone: utc), "January 15, 2027")
    }

    // MARK: - La lecture du document (types exacts seulement, AN7)

    func testOnlyTheExactTypesCountWhenTheDocumentIsRead() {
        let date = Date(timeIntervalSince1970: 1_800_000_000)
        let data: [String: Any] = [
            "active": true, "expiresAt": Timestamp(date: date), "source": "google_play", "state": "grace",
            "plan": "yearly", "trial": false, "willRenew": true, "test": true, "someFutureField": ["a": 1]
        ]

        let document = FirestoreSubscriptionStore.document(from: data)

        XCTAssertEqual(document.active, true)
        XCTAssertEqual(document.expiresAtMillis, 1_800_000_000_000)
        XCTAssertEqual(document.source, "google_play")
        XCTAssertEqual(document.state, "grace")
        XCTAssertEqual(document.plan, "yearly")
        XCTAssertEqual(document.willRenew, true)
        XCTAssertEqual(document.test, true)
    }

    func testAWrongTypeReadsAsAMissingFieldNeverAsAnError() {
        let data: [String: Any] = ["active": "true", "expiresAt": "2027-01-12", "source": 3, "state": ["x"]]

        let document = FirestoreSubscriptionStore.document(from: data)

        XCTAssertNil(document.active, "le texte « true » n'est pas un booléen")
        XCTAssertNil(document.expiresAtMillis, "un texte tapé à la main n'est pas un horodatage")
        XCTAssertNil(document.source)
        XCTAssertNil(document.state)
        XCTAssertFalse(interpretSubscription(document, nowMillis: now).hasAccess)
    }
}
// swiftlint:enable line_length
