#if ALERT_DANGER
import UserNotifications
import XCTest
@testable import Miff

/// Le message d'alerte du serveur (docs/tickets/alerte-danger.md §11, AD32 à AD38 ; forme exacte : `backend/functions/src/alerteRegles.ts`) : validé champ par champ, jamais une position,
/// un lieu, le nom de la Miff ni un nom de famille ; un toucher ouvre la Miff d'origine ; app ouverte, l'alerte s'affiche quand même. Miroir de `CircleAlertParserTest` d'Android.
final class DangerAlertPushTests: XCTestCase {
    private let miffId = "abcdefghijklmnopqrst"

    private func message(
        type: String = "circle_alert",
        event: String? = "start",
        miff: String? = "abcdefghijklmnopqrst",
        author: String? = "author123"
    ) -> [AnyHashable: Any] {
        var info: [AnyHashable: Any] = ["type": type, "aps": ["alert": "x"], "gcm.message_id": "m1"]
        info["event"] = event
        info["miffId"] = miff
        info["authorUid"] = author
        return info
    }

    // MARK: - L'analyse

    func testAStartAndAnEndAreUnderstood() {
        XCTAssertEqual(
            CircleAlertParser.parse(message()),
            CircleAlertTarget(event: .start, miffId: miffId, authorUid: "author123")
        )
        XCTAssertEqual(CircleAlertParser.parse(message(event: "end"))?.event, .end)
    }

    func testAnythingThatDoesNotLookExactlyRightIsIgnored() {
        XCTAssertNil(CircleAlertParser.parse(message(type: "circle_update")))
        XCTAssertNil(CircleAlertParser.parse(message(type: "other")))
        XCTAssertNil(CircleAlertParser.parse(message(event: nil)))
        XCTAssertNil(CircleAlertParser.parse(message(event: "expired")), "l'expiration ne notifie jamais")
        XCTAssertNil(CircleAlertParser.parse(message(miff: nil)))
        XCTAssertNil(CircleAlertParser.parse(message(miff: "../miffs/other")))
        XCTAssertNil(CircleAlertParser.parse(message(author: nil)))
        XCTAssertNil(CircleAlertParser.parse(message(author: "a/b")))
        XCTAssertNil(CircleAlertParser.parse(message(author: "")))
        XCTAssertNil(CircleAlertParser.parse([:]))
    }

    func testTheTargetOpensTheMiffOfOriginAndShowsNothingInALog() throws {
        let target = try XCTUnwrap(CircleAlertParser.parse(message()))

        XCTAssertEqual(target.openTarget, CircleNewsTarget(miffId: miffId, authorUid: "author123"))
        XCTAssertFalse("\(target)".contains("author123"))
        XCTAssertFalse("\(target)".contains(miffId))
    }

    func testAnUpdateMessageIsStillUnderstoodByTheOldParserOnly() {
        let update: [AnyHashable: Any] = ["type": "circle_update", "miffId": miffId, "authorUid": "author123"]

        XCTAssertNotNil(CircleNewsParser.parse(update))
        XCTAssertNil(CircleAlertParser.parse(update))
        XCTAssertNil(CircleNewsParser.parse(message()), "une alerte n'est pas « a donné des nouvelles »")
    }

    // MARK: - Le délégué

    func testAnAlertIsShownEvenWhileTheAppIsOpenAndAnUpdateStaysSilent() {
        let delegate = PushNotificationDelegate()

        XCTAssertEqual(delegate.routeWillPresent(userInfo: message()), .present)
        XCTAssertEqual(delegate.routeWillPresent(userInfo: message(event: "end")), .present)
        XCTAssertEqual(
            delegate.routeWillPresent(userInfo: ["type": "circle_update", "miffId": miffId, "authorUid": "a"]),
            .suppress
        )
        XCTAssertEqual(delegate.routeWillPresent(userInfo: message(type: "other")), .forward)
    }

    func testATouchOnAnAlertOpensTheMiffAndADismissOpensNothing() {
        let delegate = PushNotificationDelegate()
        var opened: [CircleNewsTarget] = []
        delegate.onTap = { opened.append($0) }

        XCTAssertEqual(
            delegate.routeResponse(userInfo: message(), actionIdentifier: UNNotificationDefaultActionIdentifier),
            .open
        )
        XCTAssertEqual(opened, [CircleNewsTarget(miffId: miffId, authorUid: "author123")])
        XCTAssertEqual(
            delegate.routeResponse(userInfo: message(), actionIdentifier: UNNotificationDismissActionIdentifier),
            .ignore
        )
        XCTAssertEqual(opened.count, 1)
        XCTAssertEqual(delegate.routeResponse(userInfo: message(type: "other"), actionIdentifier: "x"), .forward)
    }
}
#endif
