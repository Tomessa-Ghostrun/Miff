import XCTest
@testable import Miff

/// La suppression comme une chose que l'app continue de faire (critères S3, S4, S7) : le marqueur est écrit avant la première
/// écriture, un réseau coupé la met en pause et son retour la reprend, un lancement reprend ce qui n'était pas fini, et le
/// marqueur ne part que quand la Miff a disparu.
@MainActor
final class MiffDeletionCoordinatorTests: XCTestCase {
    private lazy var deleter = ScriptedDeleter()
    private let pending = MemoryPendingDeletions()
    private let network = FakeNetwork()
    private lazy var background = CountingBackground()
    private let uid = TestBox<String?>("uid-owner")
    private let started = TestBox<[String]>([])
    private let deleted = TestBox<[String]>([])

    private func coordinator(using custom: MiffDeleter? = nil) -> MiffDeletionCoordinator {
        let account = uid
        let startedLog = started
        let deletedLog = deleted
        let hooks = DeletionHooks(
            onStarted: { startedLog.value.append($0) },
            onDeleted: { deletedLog.value.append($0) }
        )
        let made = MiffDeletionCoordinator(
            deletion: custom ?? deleter,
            pending: pending,
            network: network,
            currentUid: { account.value },
            hooks: hooks
        )
        made.retryPauseNanos = 1_000_000
        made.background = background
        return made
    }

    func testStartWritesTheMarkerFirstTellsTheDirectoryRunsAndClearsTheMarkerOnlyWhenTheMiffIsGone() async {
        let coordinator = coordinator()

        let state = coordinator.start(miffId: testMiffId)

        XCTAssertEqual(state, .running(resumed: false))
        XCTAssertEqual(pending.ids(uid: "uid-owner"), [testMiffId], "le marqueur est là avant que rien ne tourne")
        XCTAssertEqual(started.value, [testMiffId])
        await waitUntil { self.deleted.value == [testMiffId] }
        XCTAssertTrue(pending.ids(uid: "uid-owner").isEmpty)
        XCTAssertEqual(deleter.runs, ["uid-owner|\(testMiffId)"])
        XCTAssertEqual(background.begun, 1)
        XCTAssertEqual(background.ended, 1, "le temps d'arrière-plan est rendu")
    }

    func testASecondTapWhileItRunsDoesNotStartASecondRun() async {
        deleter.append(.paused(.offline))
        network.online = false
        let coordinator = coordinator()

        _ = coordinator.start(miffId: testMiffId)
        _ = coordinator.start(miffId: testMiffId)
        await waitUntil { self.deleter.runs.count >= 1 }
        try? await Task.sleep(nanoseconds: 30_000_000)

        XCTAssertEqual(deleter.runs.count, 1)
    }

    func testNoNetworkWaitsThenCarriesOnWhenTheNetworkIsBackAndKeepsTheMarkerMeanwhile() async {
        deleter.append(.paused(.offline))
        network.online = false
        let coordinator = coordinator()
        let states = TestBox<[String: DeletionState]>([:])
        let listening = Task {
            for await current in coordinator.stateStream() {
                states.value = current
            }
        }
        defer {
            listening.cancel()
        }

        _ = coordinator.start(miffId: testMiffId)
        await waitUntil { states.value[testMiffId] == .waitingForNetwork }

        XCTAssertEqual(states.value[testMiffId], .waitingForNetwork)
        try? await Task.sleep(nanoseconds: 30_000_000)
        XCTAssertEqual(deleter.runs.count, 1, "rien d'autre n'est essayé tant qu'il n'y a pas de réseau")
        XCTAssertEqual(pending.ids(uid: "uid-owner"), [testMiffId])
        network.online = true
        await waitUntil { states.value[testMiffId] == .done }
        XCTAssertEqual(deleter.runs.count, 2)
        XCTAssertEqual(states.value[testMiffId], .done)
        XCTAssertTrue(pending.ids(uid: "uid-owner").isEmpty)
    }

    func testAFailureThatIsNotTheNetworkIsTriedAgainALittleThenSaysStuckAndKeepsTheMarker() async {
        for _ in 0..<5 {
            deleter.append(.paused(.refused))
        }
        let coordinator = coordinator()
        let states = TestBox<[String: DeletionState]>([:])
        let listening = Task {
            for await current in coordinator.stateStream() {
                states.value = current
            }
        }
        defer {
            listening.cancel()
        }

        _ = coordinator.start(miffId: testMiffId)
        await waitUntil { states.value[testMiffId] == .stuck }

        XCTAssertEqual(states.value[testMiffId], .stuck)
        XCTAssertEqual(deleter.runs.count, MiffDeletionCoordinator.maxOtherFailures)
        XCTAssertEqual(pending.ids(uid: "uid-owner"), [testMiffId], "le prochain lancement réessaie")
        XCTAssertTrue(deleted.value.isEmpty)
        // « Réessayer » la relance.
        deleter.clearAnswers()
        coordinator.retry(miffId: testMiffId)
        await waitUntil { states.value[testMiffId] == .done }
        XCTAssertEqual(states.value[testMiffId], .done)
    }

    func testResumePendingTakesUpWhatWasNotFinishedAtLaunchAndSaysSo() async {
        pending.add(uid: "uid-owner", miffId: testMiffId)
        pending.add(uid: "uid-owner", miffId: testOtherMiffId)
        let coordinator = coordinator()
        let notices = TestBox<[DeletionNotice]>([])
        let stream = coordinator.notices()
        let listening = Task {
            for await notice in stream {
                notices.value.append(notice)
            }
        }
        defer {
            listening.cancel()
        }

        let resumed = coordinator.resumePending()

        XCTAssertEqual(Set(resumed), [testMiffId, testOtherMiffId])
        await waitUntil { self.deleted.value.count == 2 }
        XCTAssertTrue(pending.ids(uid: "uid-owner").isEmpty)
        XCTAssertTrue(notices.value.contains(.resumed))
        XCTAssertTrue(notices.value.contains(.completed(miffId: testMiffId)))
    }

    func testResumePendingWithNoMarkerOrNoAccountDoesNothing() async {
        let coordinator = coordinator()
        XCTAssertTrue(coordinator.resumePending().isEmpty)
        pending.add(uid: "uid-owner", miffId: testMiffId)
        uid.value = nil

        XCTAssertTrue(coordinator.resumePending().isEmpty)

        try? await Task.sleep(nanoseconds: 30_000_000)
        XCTAssertTrue(deleter.runs.isEmpty)
        XCTAssertEqual(pending.ids(uid: "uid-owner"), [testMiffId], "le marqueur attend le compte qui revient")
    }

    func testAnotherAccountOnTheSamePhoneNeverTakesUpTheMarkerOfSomeoneElse() async {
        pending.add(uid: "uid-owner", miffId: testMiffId)
        uid.value = "uid-other"
        let coordinator = coordinator()

        XCTAssertTrue(coordinator.resumePending().isEmpty)

        try? await Task.sleep(nanoseconds: 30_000_000)
        XCTAssertTrue(deleter.runs.isEmpty && deleted.value.isEmpty, "rien n'est tenté, rien n'est annoncé")
        XCTAssertEqual(pending.all(), ["uid-owner|\(testMiffId)"], "le marqueur reste à son compte")
    }

    func testAMiffThatIsNotTheirsDropsTheMarkerAndSaysNothing() async {
        deleter.append(.notOwner)
        pending.add(uid: "uid-owner", miffId: testMiffId)
        let coordinator = coordinator()
        let states = TestBox<[String: DeletionState]>([:])
        let listening = Task {
            for await current in coordinator.stateStream() {
                states.value = current
            }
        }
        defer {
            listening.cancel()
        }

        coordinator.resumePending()
        await waitUntil { self.pending.ids(uid: "uid-owner").isEmpty }

        XCTAssertTrue(pending.ids(uid: "uid-owner").isEmpty)
        await waitUntil { states.value[testMiffId] == nil }
        XCTAssertNil(states.value[testMiffId])
        XCTAssertTrue(deleted.value.isEmpty)
    }

    func testTheEndIsAnnouncedOnceToWhoeverListens() async {
        let coordinator = coordinator()
        let heard = TestBox<[DeletionNotice]>([])
        let stream = coordinator.notices()
        let listening = Task {
            for await notice in stream {
                heard.value.append(notice)
            }
        }
        defer {
            listening.cancel()
        }

        _ = coordinator.start(miffId: testMiffId)
        await waitUntil { !heard.value.isEmpty }
        try? await Task.sleep(nanoseconds: 30_000_000)

        XCTAssertEqual(heard.value, [.completed(miffId: testMiffId)])
    }

    func testTheEndOfASessionStopsEverythingAndForgetsTheStatesButKeepsTheMarker() async {
        deleter.append(.paused(.offline))
        network.online = false
        let coordinator = coordinator()
        let states = TestBox<[String: DeletionState]>([:])
        let listening = Task {
            for await current in coordinator.stateStream() {
                states.value = current
            }
        }
        defer {
            listening.cancel()
        }
        _ = coordinator.start(miffId: testMiffId)
        await waitUntil { states.value[testMiffId] == .waitingForNetwork }

        await coordinator.stop()
        network.online = true
        await waitUntil { states.value.isEmpty }
        try? await Task.sleep(nanoseconds: 30_000_000)

        XCTAssertTrue(states.value.isEmpty)
        XCTAssertEqual(deleter.runs.count, 1, "rien ne reprend pour la personne qui est partie")
        XCTAssertEqual(pending.ids(uid: "uid-owner"), [testMiffId], "la suppression reprendra avec ce compte")
    }

    func testTheEndOfASessionWaitsForTheStepInFlightAndNothingIsSaidAfterIt() async {
        let gated = GatedDeleter()
        let coordinator = coordinator(using: gated)
        _ = coordinator.start(miffId: testMiffId)
        await waitUntil { gated.gate.arrivals == 1 }

        let stopping = Task {
            await coordinator.stop()
        }
        try? await Task.sleep(nanoseconds: 30_000_000)
        XCTAssertEqual(gated.finished, 0, "stop() attend la fin de l'étape en cours avant de rendre la main")
        gated.gate.open()
        await stopping.value

        XCTAssertEqual(gated.finished, 1)
        XCTAssertTrue(deleted.value.isEmpty, "une suppression annulée n'annonce rien à la session suivante")
        XCTAssertEqual(pending.ids(uid: "uid-owner"), [testMiffId], "le marqueur reste pour le même compte")
    }

    func testTheSameAccountCanStartAgainAfterAStopWithoutADoubleRun() async {
        deleter.append(.paused(.offline))
        deleter.append(.paused(.offline))
        network.online = false
        let coordinator = coordinator()
        _ = coordinator.start(miffId: testMiffId)
        await waitUntil { self.deleter.runs.count == 1 }
        await coordinator.stop()

        _ = coordinator.start(miffId: testMiffId)
        await waitUntil { self.deleter.runs.count == 2 }
        _ = coordinator.start(miffId: testMiffId)
        try? await Task.sleep(nanoseconds: 30_000_000)

        XCTAssertEqual(deleter.runs.count, 2, "le lancement annulé n'a pas effacé l'entrée du nouveau")
    }

    func testAnIdentifierThatIsNotAMiffIdentifierIsNeverWrittenToTheDisk() {
        let coordinator = coordinator()

        XCTAssertNil(coordinator.start(miffId: "../x"))

        XCTAssertTrue(pending.all().isEmpty)
        XCTAssertTrue(started.value.isEmpty)
    }
}
