import CoreGraphics
import Foundation
@testable import Miff

// Doubles en mémoire de l'Update (UPDATE-3). Aucun ne touche à la caméra, au réseau, à Firebase ni au disque ; les coordonnées sont de
// démonstration (autour de Paris) et la photo est une suite d'octets quelconque. Du Swift pur.

/// L'identifiant de compte de démonstration de l'Update.
let updateUid = "uid-update"

/// Le carnet en mémoire : mêmes règles que `FilePublishLedger` (la note de la photo survit à `clear()`, pas à `eraseAccountData`).
final class MemoryPublishLedger: PublishLedger {
    private(set) var state = LedgerState()

    func countedDay() -> String? {
        state.day
    }

    func countToday() -> Int {
        state.count
    }

    func lastPublishMillis() -> Int? {
        state.lastPublish
    }

    func recordPublication(nowMillis: Int, dayKey: String, count: Int) {
        state.day = dayKey
        state.count = count
        state.lastPublish = nowMillis
    }

    func photoSentMillis(uid: String) -> Int? {
        state.photoSentUid == uid ? state.photoSentAt : nil
    }

    func notePhotoSent(uid: String, millis: Int) {
        state.photoSentUid = uid
        state.photoSentAt = millis
    }

    func clearPhotoSent() {
        state.photoSentUid = nil
        state.photoSentAt = nil
    }

    func doubleTapUsed() -> Bool {
        state.doubleTapUsed
    }

    func markDoubleTapUsed() {
        state.doubleTapUsed = true
    }

    func clear() {
        state.day = nil
        state.count = 0
        state.lastPublish = nil
    }

    func eraseAccountData(uid: String) {
        if state.photoSentUid == uid {
            clearPhotoSent()
        }
    }
}

/// Une transaction de l'Update en mémoire : ce que le serveur dirait de chaque fiche, ce qui a été écrit, et un échec à la demande.
final class FakePhotoStore: PhotoStore {
    /// Les Miffs où je ne partage plus (ma fiche le dit).
    var notSharing: Set<String> = []
    /// Les Miffs où mon consentement ne couvre pas la position.
    var needsAgreement: Set<String> = []
    /// Les erreurs à lancer, dans l'ordre, une par lot (nil = le lot passe).
    var failures: [Error?] = []
    /// Chaque lot demandé : les Miffs et si le consentement était exigé.
    private(set) var lots: [(miffIds: [String], requireConsent: Bool)] = []
    /// Tout ce qui a été écrit, dans l'ordre.
    private(set) var writes: [DocWrite] = []

    func commit(
        uid: String,
        miffIds: [String],
        requireConsent: Bool,
        build: @escaping ([String]) -> [DocWrite]
    ) async throws -> PhotoCommit {
        lots.append((miffIds, requireConsent))
        if !failures.isEmpty, let failure = failures.removeFirst() {
            throw failure
        }
        let missing = miffIds.filter { notSharing.contains($0) }
        let agreement = miffIds.filter { !notSharing.contains($0) && requireConsent && needsAgreement.contains($0) }
        let allowed = miffIds.filter { !missing.contains($0) && !agreement.contains($0) }
        if !allowed.isEmpty {
            let lot = build(allowed)
            guard SessionWriteGuard.isSendable(lot) else {
                throw InvalidWriteError()
            }
            writes += lot
        }
        return PhotoCommit(written: allowed, notSharing: missing, needsAgreement: agreement)
    }

    /// Les écritures d'un genre sur un document de photo ou de statut.
    func writes(at suffix: String) -> [DocWrite] {
        writes.filter { $0.path.hasSuffix(suffix) }
    }
}

/// La mesure d'une position pour une photo, scriptée.
final class FakePhotoPosition: PhotoPositionSource {
    var result: PhotoPosition = .found(Fix(lat: 48.85661, lng: 2.35222, measuredAtMillis: 0))
    private(set) var calls = 0

    func measure() async -> PhotoPosition {
        calls += 1
        return result
    }
}

final class FakeOwnPhotoReader: OwnPhotoReader {
    var result: OwnPhotoRead = .unreadable
    private(set) var calls = 0

    func read(miffId: String, uid: String) async -> OwnPhotoRead {
        calls += 1
        return result
    }
}

final class FakeCameraPermission: CameraPermissionProviding {
    var current: CameraAuthorization = .authorized
    var grantsOnRequest = true
    private(set) var requests = 0
    private(set) var settingsOpened = 0

    func status() -> CameraAuthorization {
        current
    }

    func request() async -> Bool {
        requests += 1
        current = grantsOnRequest ? .authorized : .denied
        return grantsOnRequest
    }

    func openSettings() {
        settingsOpened += 1
    }
}

/// Ce que les tests de l'Update ont en commun : l'horloge, une photo, un lieu, et la fabrication du publieur.
enum UpdateFixtures {
    static let noon = TrackingFixtures.noon
    static let jpeg = Data(repeating: 7, count: 1_000)

    static func photo(takenAt: Int = noon, bytes: Int = 1_000) -> UpdatePhoto {
        UpdatePhoto(jpeg: Data(repeating: 7, count: bytes), width: 461, height: 1_024, takenAtMillis: takenAt)
    }

    static func fix(at: Int = noon) -> Fix {
        Fix(lat: 48.85661, lng: 2.35222, measuredAtMillis: at)
    }
}

/// Un banc d'essai de l'Update : l'horloge est la sienne (millisecondes), les dépendances sont des doubles.
@MainActor
final class UpdateRig {
    let store = FakePhotoStore()
    let device = FakeActiveDevice()
    let position = FakePhotoPosition()
    let network = FakeNetwork()
    let ledger = MemoryPublishLedger()
    let reader = FakeOwnPhotoReader()
    var now = UpdateFixtures.noon
    var uid: String? = updateUid
    var miffIds = [testMiffId]
    var published: (String) -> Bool = { _ in true }
    private(set) var positionMissingCalls = 0

    init() {
        position.result = .found(UpdateFixtures.fix())
    }

    var environment: UpdateEnvironment {
        UpdateEnvironment(
            currentUid: { [unowned self] in self.uid },
            miffIds: { [unowned self] in self.miffIds },
            battery: { 80 },
            nowMillis: { [unowned self] in self.now },
            timeZone: { TimeZone(secondsFromGMT: 0) ?? TimeZone.current }
        )
    }

    func makePublisher() -> UpdatePublisher {
        var dependencies = UpdatePublisherDependencies(
            store: store,
            device: device,
            position: position,
            network: network,
            ledger: ledger,
            environment: environment
        )
        dependencies.isPositionPublished = { [unowned self] in self.published($0) }
        dependencies.onPositionMissing = { [unowned self] in self.positionMissingCalls += 1 }
        return UpdatePublisher(dependencies)
    }

    func makeCleaner(withReader: Bool = true) -> PhotoCleaner {
        var dependencies = PhotoCleanerDependencies(
            store: store, device: device, network: network, ledger: ledger, environment: environment
        )
        dependencies.reader = withReader ? reader : nil
        return PhotoCleaner(dependencies)
    }
}
