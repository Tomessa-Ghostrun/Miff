import FirebaseFirestore
import XCTest
@testable import Miff

/// Ce qu'on écrit dans `users/{uid}.analyticsConsent` quand la personne allume ou éteint l'interrupteur (ONB-3b, RG6) : exactement les
/// transitions que les règles autorisent, les mêmes qu'Android. Du Swift pur : aucun réseau.
final class AnalyticsConsentTransitionTests: XCTestCase {
    private let active: [String: Any] = [
        "enabled": true,
        "consentedAt": Timestamp(seconds: 1_700_000_000, nanoseconds: 123_456_000),
        "consentVersion": "2026-10-v01",
        "revokedAt": NSNull()
    ]

    private let withdrawn: [String: Any] = [
        "enabled": false,
        "consentedAt": Timestamp(seconds: 1_700_000_000, nanoseconds: 123_456_000),
        "consentVersion": "2026-10-v01",
        "revokedAt": Timestamp(seconds: 1_700_100_000, nanoseconds: 0)
    ]

    private let refusedAtSignUp: [String: Any] = [
        "enabled": false,
        "consentedAt": NSNull(),
        "consentVersion": NSNull(),
        "revokedAt": NSNull()
    ]

    func testAccountWithoutABlockCanBeActivatedAndHasNothingToWithdraw() {
        XCTAssertEqual(AnalyticsConsentTransition.change(previous: nil, enable: true), .activate)
        XCTAssertEqual(AnalyticsConsentTransition.change(previous: nil, enable: false), .unchanged)
    }

    func testAnActiveConsentIsWithdrawnAndNeverRewrittenWhenAlreadyActive() {
        XCTAssertEqual(AnalyticsConsentTransition.change(previous: active, enable: false), .withdraw)
        XCTAssertEqual(AnalyticsConsentTransition.change(previous: active, enable: true), .unchanged)
    }

    func testAWithdrawnOrRefusedConsentCanBeActivatedAgainButNotWithdrawnTwice() {
        XCTAssertEqual(AnalyticsConsentTransition.change(previous: withdrawn, enable: true), .activate)
        XCTAssertEqual(AnalyticsConsentTransition.change(previous: withdrawn, enable: false), .unchanged)
        XCTAssertEqual(AnalyticsConsentTransition.change(previous: refusedAtSignUp, enable: true), .activate)
        XCTAssertEqual(AnalyticsConsentTransition.change(previous: refusedAtSignUp, enable: false), .unchanged)
    }

    func testTheVersionOfTheTextCanNeverGoBack() {
        let newer = withdrawn.merging(["consentVersion": "2026-12-v02"]) { _, new in new }

        XCTAssertEqual(
            AnalyticsConsentTransition.change(previous: newer, enable: true, version: "2026-10-v01"), .refused
        )
        // Une version égale ou plus récente passe.
        XCTAssertEqual(
            AnalyticsConsentTransition.change(previous: newer, enable: true, version: "2026-12-v02"), .activate
        )
        XCTAssertEqual(
            AnalyticsConsentTransition.change(previous: newer, enable: true, version: "2027-01-v03"), .activate
        )
        // Retirer ne dépend jamais de la version.
        let newerActive = active.merging(["consentVersion": "2026-12-v02"]) { _, new in new }
        XCTAssertEqual(
            AnalyticsConsentTransition.change(previous: newerActive, enable: false, version: "2026-10-v01"), .withdraw
        )
    }

    func testAMalformedBlockIsNeverReadAsActive() {
        // `enabled` qui n'est pas un booléen : rien à retirer, et activer reste permis.
        let odd: [String: Any] = ["enabled": "true"]

        XCTAssertEqual(AnalyticsConsentTransition.change(previous: odd, enable: false), .unchanged)
        XCTAssertEqual(AnalyticsConsentTransition.change(previous: odd, enable: true), .activate)
    }

    // MARK: - Les blocs écrits

    func testActivationWritesTheFourKeysWithTheServerTimeAndTheCurrentVersion() throws {
        let block = AnalyticsConsentBlocks.activation()

        XCTAssertEqual(Set(block.keys), ["enabled", "consentedAt", "consentVersion", "revokedAt"])
        XCTAssertEqual(block["enabled"] as? Bool, true)
        XCTAssertEqual(block["consentVersion"] as? String, ConsentVersions.analytics)
        XCTAssertTrue(block["revokedAt"] is NSNull)
        // L'heure est celle du SERVEUR : jamais une date prise sur le téléphone.
        XCTAssertNotNil(block["consentedAt"])
        XCTAssertFalse(block["consentedAt"] is Date)
        XCTAssertFalse(block["consentedAt"] is Timestamp)
    }

    func testWithdrawalKeepsTheTraceOfTheConsentExactlyAsRead() throws {
        let block = AnalyticsConsentBlocks.withdrawal(previous: active)

        XCTAssertEqual(Set(block.keys), ["enabled", "consentedAt", "consentVersion", "revokedAt"])
        XCTAssertEqual(block["enabled"] as? Bool, false)
        XCTAssertEqual(block["consentVersion"] as? String, "2026-10-v01")
        // Les règles exigent une trace inchangée : la valeur lue est réécrite telle quelle, à la microseconde près.
        let kept = try XCTUnwrap(block["consentedAt"] as? Timestamp)
        XCTAssertEqual(kept, Timestamp(seconds: 1_700_000_000, nanoseconds: 123_456_000))
        XCTAssertNotNil(block["revokedAt"])
        XCTAssertFalse(block["revokedAt"] is NSNull)
        XCTAssertFalse(block["revokedAt"] is Timestamp)
    }
}
