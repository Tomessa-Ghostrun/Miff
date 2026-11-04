import Foundation
@testable import Miff

/// La suppression telle que l'écran de la Miff la voit : ce qu'on lui a demandé, et l'état dont elle le prévient.
@MainActor
final class FakeDeletionController: DeletionController {
    private let broadcaster = Broadcaster<[String: DeletionState]>(replay: true, initial: [:])
    private var states: [String: DeletionState] = [:]
    private(set) var started: [String] = []
    private(set) var retried: [String] = []

    func stateStream() -> AsyncStream<[String: DeletionState]> {
        broadcaster.stream()
    }

    func start(miffId: String) -> DeletionState? {
        started.append(miffId)
        let state = DeletionState.running(resumed: false)
        set(miffId, state)
        return state
    }

    func retry(miffId: String) {
        retried.append(miffId)
    }

    func set(_ miffId: String, _ state: DeletionState) {
        states[miffId] = state
        broadcaster.send(states)
    }
}

/// Ce que contient le marqueur « une suppression est en cours », en mémoire.
final class MemoryPendingDeletions: PendingDeletionStore {
    private var entries = Set<String>()

    private static func key(_ uid: String, _ miffId: String) -> String {
        "\(uid)|\(miffId)"
    }

    func ids(uid: String) -> Set<String> {
        let prefix = "\(uid)|"
        return Set(entries.filter { $0.hasPrefix(prefix) }.map { String($0.dropFirst(prefix.count)) })
    }

    func add(uid: String, miffId: String) {
        entries.insert(Self.key(uid, miffId))
    }

    func remove(uid: String, miffId: String) {
        entries.remove(Self.key(uid, miffId))
    }

    /// Tout ce qui est gardé, à qui que ce soit.
    func all() -> Set<String> {
        entries
    }
}

/// Une procédure de suppression dont les réponses sont données par le test, une par exécution (« supprimée » quand il n'y en a
/// plus). Sur l'acteur principal, comme le coordinateur et les tests : aucun verrou.
@MainActor
final class ScriptedDeleter: MiffDeleter {
    private var answers: [MiffDeletionResult]
    private(set) var runs: [String] = []

    init(_ answers: [MiffDeletionResult] = []) {
        self.answers = answers
    }

    func append(_ answer: MiffDeletionResult) {
        answers.append(answer)
    }

    func clearAnswers() {
        answers.removeAll()
    }

    /// Chaque exécution est notée « compte|Miff ».
    func run(uid: String, miffId: String) async -> MiffDeletionResult {
        runs.append("\(uid)|\(miffId)")
        return answers.isEmpty ? .deleted : answers.removeFirst()
    }
}

/// Du temps d'arrière-plan factice : compte les jetons pris et rendus.
@MainActor
final class CountingBackground: BackgroundExecution {
    private(set) var begun = 0
    private(set) var ended = 0

    func begin() -> Int {
        begun += 1
        return begun
    }

    func end(_ token: Int) {
        ended += 1
    }
}
