import Foundation

// Le NETTOYAGE par l'auteur (ticket §6.4) : une photo publiée il y a plus de 24 h est supprimée de la base et le statut dit
// `hasPhoto = false`, dans chaque Miff où je partage. Au lancement et au retour au premier plan. AUCUNE lecture du serveur tant que la
// date existe (c'est la note du téléphone) ; rien n'est fait hors ligne, ni quand ce téléphone n'est plus celui du compte (ce sera pour la
// prochaine fois). Les seules écritures : `photos/{moi}` (suppression) et le statut (`UpdateWrites.photoStatus`). Miroir de `PhotoCleaner`
// d'Android.
//
// La note de la date est liée au COMPTE et survit à une déconnexion (revue C1). SANS note (réinstallation, nouveau téléphone) :
// - `hasPhoto` et une dernière update de plus de 25 h : la photo est expirée, elle est supprimée (une lecture d'une photo expirée est
//   refusée par les règles même pour son auteur, donc la date de l'update est ce qui le dit) ;
// - `hasPhoto` et une dernière update de moins de 25 h : UN `get` de MA propre photo (2 lectures de règles, décision D35) décide sur son
//   `expiresAt` ; ce qui n'est pas lisible ne conclut RIEN (JAMAIS de suppression sur un refus des règles) et la date de la dernière update
//   tient lieu de note, donc le nettoyage viendra plus tard.

/// Ce que le nettoyage sait de mon statut : `hasPhoto` et la date de ma dernière update.
struct MyPhotoFacts: Equatable {
    let hasPhoto: Bool
    let lastUpdateAtMillis: Int?
}

struct PhotoCleanerDependencies {
    let store: PhotoStore
    let device: ActiveDevice
    let network: NetworkMonitor
    let ledger: PublishLedger
    let environment: UpdateEnvironment
    var reader: OwnPhotoReader?
    var timeoutSeconds = UpdateLimits.timeoutSeconds
}

@MainActor
final class PhotoCleaner {
    /// La photo est lisible 23 h ; le nettoyage commence à 24 h comme le dit le modèle de données (« illisible après 24 h »).
    static let expiredAfterMillis = 24 * 60 * 60 * 1000

    /// Sans note : les règles refusent la lecture d'une photo à partir de 25 h (24 h + 1 h de marge), donc un statut aussi vieux ne peut
    /// pas avoir de photo lisible.
    static let unreadableAfterMillis = 25 * 60 * 60 * 1000

    private let store: PhotoStore
    private let device: ActiveDevice
    private let network: NetworkMonitor
    private let ledger: PublishLedger
    private let environment: UpdateEnvironment
    private let reader: OwnPhotoReader?
    private let timeoutSeconds: Double

    init(_ dependencies: PhotoCleanerDependencies) {
        store = dependencies.store
        device = dependencies.device
        network = dependencies.network
        ledger = dependencies.ledger
        environment = dependencies.environment
        reader = dependencies.reader
        timeoutSeconds = dependencies.timeoutSeconds
    }

    /// Vrai quand une photo a été retirée. Ne lève jamais.
    @discardableResult
    func cleanUp(_ mine: MyPhotoFacts?) async -> Bool {
        guard let uid = environment.currentUid() else {
            return false
        }
        let now = environment.nowMillis()
        guard await isExpired(uid: uid, mine: mine, now: now) else {
            return false
        }
        let ids = environment.distinctMiffIds()
        guard !ids.isEmpty, network.isOnline(),
              case .cleared(let deviceId) = await device.clearance(uid: uid) else {
            return false
        }
        let battery = await environment.battery()
        for start in stride(from: 0, to: ids.count, by: UpdateLimits.lotSize) {
            let lot = Array(ids[start..<min(start + UpdateLimits.lotSize, ids.count)])
            guard await remove(uid: uid, lot: lot, battery: battery, deviceId: deviceId) else {
                // Un refus ou un échec : la note reste, le prochain lancement réessaie (pas de boucle).
                return false
            }
        }
        ledger.clearPhotoSent()
        return true
    }

    /// La photo est-elle expirée ? Gère la note locale, puis le repli sans note.
    private func isExpired(uid: String, mine: MyPhotoFacts?, now: Int) async -> Bool {
        if let sentAt = ledger.photoSentMillis(uid: uid) {
            return now - sentAt > Self.expiredAfterMillis
        }
        // Pas de note (réinstallation, nouveau téléphone) : mon propre statut le dit, aucune lecture de la photo d'abord.
        guard let mine, mine.hasPhoto, let update = mine.lastUpdateAtMillis else {
            return false
        }
        guard now - min(update, now) <= Self.unreadableAfterMillis else {
            return true
        }
        // D35 : UN `get` de ma propre photo décide sur son `expiresAt` ; jamais de suppression sur un refus.
        let own = await readOwnPhoto(uid: uid)
        if case .found(_, let expiresAt)? = own, expiresAt <= now {
            return true
        }
        // Lisible : la date de la photo elle-même est la note ; illisible (refusée, absente, hors ligne) : celle de la dernière update.
        if case .found(let createdAt, _)? = own {
            ledger.notePhotoSent(uid: uid, millis: createdAt)
        } else {
            ledger.notePhotoSent(uid: uid, millis: update)
        }
        return false
    }

    private func readOwnPhoto(uid: String) async -> OwnPhotoRead? {
        guard let miffId = environment.miffIds().first, let reader else {
            return nil
        }
        return try? await timedOperation(seconds: timeoutSeconds) { await reader.read(miffId: miffId, uid: uid) }
    }

    private func remove(uid: String, lot: [String], battery: Int?, deviceId: String) async -> Bool {
        let store = self.store
        do {
            _ = try await timedOperation(seconds: timeoutSeconds) {
                try await store.commit(uid: uid, miffIds: lot, requireConsent: false) { allowed in
                    allowed.flatMap {
                        UpdateWrites.removeExpired(miffId: $0, uid: uid, batteryPct: battery, deviceId: deviceId)
                    }
                }
            }
            return true
        } catch {
            return false
        }
    }
}
