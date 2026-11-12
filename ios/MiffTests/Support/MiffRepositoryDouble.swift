import Foundation
@testable import Miff

struct RenameCall: Equatable {
    let miffId: String
    let code: String
    let name: String
}

/// Le dépôt tel que les view models le voient.
final class FakeMiffRepository: MiffRepository {
    var createPreparation: CreatePreparation = .ready(CreateReady(premium: false, firstMiff: true))
    var createResult: CreateResult = .created(
        MiffCreation(miffId: testMiffId, joinCode: testCode, name: "Chez nous", firstMiff: true)
    )
    var joinPreparation: JoinPreparation = .ready(
        JoinReady(miffId: testMiffId, miffName: "Chez nous", code: testCode, premium: false, firstMiff: true)
    )
    var joinResult: JoinResult = .joined(miffId: testMiffId, firstMiff: true)
    var renameResult: MiffWriteResult = .done
    var sharingResult: MiffWriteResult = .done
    var myMiffsResult: MyMiffsLoad = .loaded(MyMiffs(miffs: [], lost: [], premium: false, fromCache: false))
    // MIFF-4
    var renewResult: CodeChangeResult = .changed(newCode: "NEWCODE23456")
    var removeResult: CodeChangeResult = .changed(newCode: "NEWCODE23456")
    var leaveResult: MiffWriteResult = .done
    let observer = FakeMiffObserver()

    private(set) var calls: [String] = []
    private(set) var created: [String] = []
    private(set) var prepareJoinCodes: [String] = []
    private(set) var renamed: [RenameCall] = []
    private(set) var sharingChanges: [Bool] = []
    private(set) var cleaned: [LostMiff] = []
    private(set) var removed: [String] = []
    var onCreate: () -> Void = {}
    var onJoin: () -> Void = {}
    var onMyMiffs: () -> Void = {}
    /// Retient la lecture des Miffs tant que le test ne la libère pas (une lecture lancée hors connexion qui attend le réseau).
    var myMiffsGate: (() async -> Void)?
    var onRemove: (String) -> Void = { _ in }

    func prepareCreate(uid: String) async -> CreatePreparation {
        calls.append("prepareCreate")
        return createPreparation
    }

    func create(uid: String, name: String, preparation: CreateReady) async -> CreateResult {
        calls.append("create")
        created.append(name)
        onCreate()
        return createResult
    }

    func prepareJoin(uid: String, typedCode: String) async -> JoinPreparation {
        calls.append("prepareJoin")
        prepareJoinCodes.append(typedCode)
        return joinPreparation
    }

    func join(uid: String, preparation: JoinReady) async -> JoinResult {
        calls.append("join")
        onJoin()
        return joinResult
    }

    func rename(miffId: String, code: String, name: String) async -> MiffWriteResult {
        calls.append("rename")
        renamed.append(RenameCall(miffId: miffId, code: code, name: name))
        return renameResult
    }

    func setSharing(miffId: String, uid: String, enabled: Bool) async -> MiffWriteResult {
        calls.append("setSharing")
        sharingChanges.append(enabled)
        return sharingResult
    }

    func myMiffs(uid: String) async -> MyMiffsLoad {
        calls.append("myMiffs")
        onMyMiffs()
        await myMiffsGate?()
        return myMiffsResult
    }

    func cleanUpLost(uid: String, lost: LostMiff) async -> Bool {
        calls.append("cleanUp")
        cleaned.append(lost)
        return true
    }

    func renewCode(miffId: String) async -> CodeChangeResult {
        calls.append("renewCode")
        return renewResult
    }

    func removeMember(miffId: String, targetUid: String) async -> CodeChangeResult {
        calls.append("removeMember")
        removed.append(targetUid)
        onRemove(targetUid)
        return removeResult
    }

    func leave(uid: String, miffId: String) async -> MiffWriteResult {
        calls.append("leave")
        return leaveResult
    }

    func observe(miffId: String) -> AsyncStream<MiffObservation> {
        observer.observe(miffId: miffId)
    }
}
