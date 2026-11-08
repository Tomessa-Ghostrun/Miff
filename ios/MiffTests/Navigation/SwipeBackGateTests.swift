import UIKit
import XCTest
@testable import Miff

/// Le gardien du geste de retour sur une vraie navigation UIKit : il prend la place du délégué du système (qui refuse le geste quand la
/// barre est masquée, comme dans toute l'app), compte les verrous, et ne laisse commencer le geste qu'à bon escient.
@MainActor
final class SwipeBackGateTests: XCTestCase {
    private func makeNavigation(depth: Int) -> UINavigationController {
        let navigation = UINavigationController(rootViewController: UIViewController())
        navigation.setNavigationBarHidden(true, animated: false)
        navigation.loadViewIfNeeded()
        for _ in 1..<max(depth, 1) {
            navigation.pushViewController(UIViewController(), animated: false)
        }
        return navigation
    }

    func testInstallingTakesTheGestureDelegateAndKeepsTheGestureEnabled() throws {
        let navigation = makeNavigation(depth: 2)
        let recognizer = try XCTUnwrap(navigation.interactivePopGestureRecognizer)
        recognizer.isEnabled = false
        let gate = SwipeBackGate.gate(for: navigation)
        gate.install()
        XCTAssertTrue(recognizer.delegate === gate)
        XCTAssertTrue(recognizer.isEnabled)
    }

    func testTheSameNavigationAlwaysGetsTheSameGate() {
        let navigation = makeNavigation(depth: 2)
        XCTAssertTrue(SwipeBackGate.gate(for: navigation) === SwipeBackGate.gate(for: navigation))
        XCTAssertFalse(SwipeBackGate.gate(for: navigation) === SwipeBackGate.gate(for: makeNavigation(depth: 2)))
    }

    func testTheGestureDoesNotStartOnTheRootScreen() throws {
        let navigation = makeNavigation(depth: 1)
        let recognizer = try XCTUnwrap(navigation.interactivePopGestureRecognizer)
        let gate = SwipeBackGate.gate(for: navigation)
        gate.install()
        XCTAssertFalse(gate.gestureRecognizerShouldBegin(recognizer))
    }

    func testTheGestureStartsOnAStackedScreen() throws {
        let navigation = makeNavigation(depth: 2)
        let recognizer = try XCTUnwrap(navigation.interactivePopGestureRecognizer)
        let gate = SwipeBackGate.gate(for: navigation)
        gate.install()
        XCTAssertTrue(gate.gestureRecognizerShouldBegin(recognizer))
    }

    func testEachLockerCountsOnceAndTheGestureReturnsWhenAllAreReleased() throws {
        let navigation = makeNavigation(depth: 3)
        let recognizer = try XCTUnwrap(navigation.interactivePopGestureRecognizer)
        let gate = SwipeBackGate.gate(for: navigation)
        let first = NSObject()
        let second = NSObject()
        gate.setLocked(true, owner: first)
        gate.setLocked(true, owner: first)
        XCTAssertEqual(gate.lockCount, 1)
        XCTAssertFalse(gate.gestureRecognizerShouldBegin(recognizer))
        gate.setLocked(true, owner: second)
        XCTAssertEqual(gate.lockCount, 2)
        gate.setLocked(false, owner: first)
        XCTAssertFalse(gate.gestureRecognizerShouldBegin(recognizer))
        gate.setLocked(false, owner: second)
        XCTAssertEqual(gate.lockCount, 0)
        XCTAssertTrue(gate.gestureRecognizerShouldBegin(recognizer))
    }

    func testTheNavigationIsFoundFromAChildOfAStackedScreen() {
        let navigation = makeNavigation(depth: 2)
        guard let screen = navigation.topViewController else {
            return XCTFail("Pas d'écran empilé.")
        }
        let child = UIViewController()
        screen.addChild(child)
        screen.view.addSubview(child.view)
        child.didMove(toParent: screen)
        XCTAssertTrue(SwipeBackGate.findNavigation(containing: child) === navigation)
        XCTAssertNil(SwipeBackGate.findNavigation(containing: UIViewController()))
    }

    func testALockedScreenLocksOnlyWhileItIsOnTopOfTheStack() throws {
        let navigation = makeNavigation(depth: 2)
        let screen = try XCTUnwrap(navigation.topViewController)
        let lock = SwipeBackLock.LockController()
        lock.isLocked = true
        screen.addChild(lock)
        screen.view.addSubview(lock.view)
        lock.didMove(toParent: screen)
        lock.apply()
        let gate = SwipeBackGate.gate(for: navigation)
        XCTAssertEqual(gate.lockCount, 1)
        // Un autre écran est empilé par-dessus : l'écran verrouillé est recouvert, son verrou ne retient plus personne.
        navigation.pushViewController(UIViewController(), animated: false)
        lock.viewWillDisappear(false)
        lock.apply()
        XCTAssertEqual(gate.lockCount, 0)
        // Retour sur l'écran verrouillé : le verrou revient.
        navigation.popViewController(animated: false)
        lock.viewWillAppear(false)
        XCTAssertEqual(gate.lockCount, 1)
        // L'écran part pour de bon.
        lock.willMove(toParent: nil)
        lock.removeFromParent()
        XCTAssertEqual(gate.lockCount, 0)
    }

    func testAnUnlockedScreenStillReEnablesTheGesture() throws {
        let navigation = makeNavigation(depth: 2)
        let screen = try XCTUnwrap(navigation.topViewController)
        let lock = SwipeBackLock.LockController()
        screen.addChild(lock)
        screen.view.addSubview(lock.view)
        lock.didMove(toParent: screen)
        lock.apply()
        let gate = SwipeBackGate.gate(for: navigation)
        XCTAssertTrue(navigation.interactivePopGestureRecognizer?.delegate === gate)
        XCTAssertEqual(gate.lockCount, 0)
    }
}
