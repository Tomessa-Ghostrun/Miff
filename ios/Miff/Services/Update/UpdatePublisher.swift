import Foundation

// La PUBLICATION d'une update (ticket docs/tickets/update.md §5.7 ; décision du PM du 2026-11-06 « une photo doit être attachée à une
// position, forcément »). Miroir d'`UpdatePublisher.kt` d'Android.
//
// Ce que ce fichier garantit, chaque point couvert par un test :
// - RIEN n'est écrit sans photo (D16) NI sans position récente (le document de la photo porte toujours lat, lng et measuredAt ensemble) ;
// - RIEN n'est écrit hors connexion, quand ce téléphone n'est plus celui du compte (SESS-3), quand le garde-fou dit « plus tard » (20 par
//   jour, 30 s entre deux) ou quand la photo attend depuis plus de 5 minutes : la photo reste à l'écran, rien n'est perdu, rien n'est mis en
//   file pour partir plus tard à l'insu de la personne ;
// - la photo ET le statut sont dans la MÊME transaction par lot de 5 Miffs au plus (un statut qui dit `hasPhoto` sans photo n'existe
//   jamais), par les constructeurs qui portent le `deviceId` de la session (`UpdateWrites`) ;
// - la position est mesurée UNE fois, au toucher de « Publier » (jamais en boucle, jamais en arrière-plan), gardée en mémoire jusqu'à
//   l'envoi du lot et écrite seulement dans le document de la photo. Rien ici ne la journalise, ni ne l'envoie comme événement.

/// Ce que `UpdatePublisher` demande au téléphone et au compte, regroupé pour garder des initialiseurs lisibles.
struct UpdateEnvironment {
    var currentUid: () -> String?
    /// Les identifiants des Miffs de la personne (l'annuaire). Une Miff où le partage est arrêté est écartée par la transaction, pas ici.
    var miffIds: () -> [String]
    var battery: () async -> Int?
    var nowMillis: () -> Int = { Int(Date().timeIntervalSince1970 * 1000) }
    var timeZone: () -> TimeZone = { TimeZone.current }

    /// Les Miffs de la personne, sans doublon, dans l'ordre.
    func distinctMiffIds() -> [String] {
        var seen = Set<String>()
        return miffIds().filter { seen.insert($0).inserted }
    }
}

struct UpdatePublisherDependencies {
    let store: PhotoStore
    let device: ActiveDevice
    let position: PhotoPositionSource
    let network: NetworkMonitor
    let ledger: PublishLedger
    let environment: UpdateEnvironment
    /// Ma position a-t-elle été publiée dans cette Miff ? (contrat du modèle de données : pas de photo là où elle ne l'a pas été ; la lecture
    /// des photos des autres en dépend.) Sinon l'Update remet au suivi la position qu'il vient de mesurer (`handOver`).
    var isPositionPublished: (String) -> Bool = { _ in true }
    /// Le suivi connaît-il cette Miff (éligible : partage actif, consentement de version 3) ? Sert seulement au journal de diagnostic.
    var trackingKnowsMiff: (String) -> Bool = { _ in true }
    /// Remet au suivi la position mesurée au toucher de « Publier » ; il la publie LUI-MÊME (l'Update n'écrit jamais `positions/{moi}`,
    /// critère U8) et rend la main à la fin de son passage.
    var handOver: (Fix) async -> Void = { _ in }
    /// Le journal local de diagnostic (builds Debug et Internal seulement) : des CODES de cause, jamais une coordonnée.
    var journal: DetectionJournal = NoDetectionJournal()
    /// FEED-8 : une photo vient d'être publiée dans ces Miffs ; le Feed la montre sur MA carte sans la relire (en mémoire seulement).
    var onPhotoPublished: (PublishedPhoto) -> Void = { _ in }
    var timeoutSeconds = UpdateLimits.timeoutSeconds
    /// Le délai de la remise de la position au suivi (voir `UpdateLimits.handOverTimeoutSeconds`).
    var handOverTimeoutSeconds = UpdateLimits.handOverTimeoutSeconds
}

/// Ce que la publication dit à qui veut montrer ma photo sans la relire : la photo, son lieu, et les Miffs où elle est partie. Elle ne
/// va nulle part ailleurs qu'en mémoire (jamais un fichier, un journal ou un événement) et ne montre rien dans `description`.
struct PublishedPhoto: CustomStringConvertible {
    let uid: String
    let miffIds: [String]
    let photo: SharedPhoto

    var description: String {
        "PublishedPhoto(redacted)"
    }
}

/// Les mesures de la publication, hors de l'acteur principal pour pouvoir servir de valeur par défaut.
enum UpdateLimits {
    /// 5 Miffs par lot : 3 lectures de règles chacune = 15 des 20 permises (backend/data-model.md, §8).
    static let lotSize = 5
    static let timeoutSeconds = 20.0
    /// La remise de la position au suivi attend le travail précédent du suivi (au plus `TrackingLimits.wakeBudgetSeconds`, 25 s) puis fait le
    /// sien : plus long qu'une écriture, pour ne pas dire « introuvable » à tort quand le suivi est simplement occupé.
    static let handOverTimeoutSeconds = 40.0
}

/// Ce que les lots ont donné, Miff par Miff, et ce qui a arrêté la suite (un lot qui échoue arrête les suivants).
private struct LotsOutcome {
    var written: [String] = []
    var notSharing: [String] = []
    var needsAgreement: [String] = []
    var failure: PublishResult?

    /// Ce que la personne doit en comprendre : publié, publié en partie (les Miffs restantes sont nommées pour « Réessayer »), ou rien.
    func result(all: [String]) -> PublishResult {
        let untried = all.filter { !written.contains($0) && !notSharing.contains($0) && !needsAgreement.contains($0) }
        if written.isEmpty {
            return failure ?? (needsAgreement.isEmpty ? .sharingStopped : .needsAgreement)
        }
        if failure != nil || !untried.isEmpty {
            return .partial(sharedIn: written.count, total: written.count + untried.count, remaining: untried)
        }
        return .published(sharedIn: written.count)
    }
}

/// Ce que `writeLots` a besoin de savoir de la publication en cours.
private struct PublishJob {
    let uid: String
    let photo: UpdatePhoto
    let place: PhotoPlace
    let deviceId: String
    let batteryPct: Int?
}

@MainActor
final class UpdatePublisher {
    private let store: PhotoStore
    private let device: ActiveDevice
    private let position: PhotoPositionSource
    private let network: NetworkMonitor
    private let ledger: PublishLedger
    private let environment: UpdateEnvironment
    private let isPositionPublished: (String) -> Bool
    private let trackingKnowsMiff: (String) -> Bool
    private let journal: DetectionJournal
    private let handOver: (Fix) async -> Void
    private let onPhotoPublished: (PublishedPhoto) -> Void
    private let timeoutSeconds: Double
    fileprivate let handOverTimeoutSeconds: Double

    init(_ dependencies: UpdatePublisherDependencies) {
        store = dependencies.store
        device = dependencies.device
        position = dependencies.position
        network = dependencies.network
        ledger = dependencies.ledger
        environment = dependencies.environment
        isPositionPublished = dependencies.isPositionPublished
        trackingKnowsMiff = dependencies.trackingKnowsMiff
        journal = dependencies.journal
        handOver = dependencies.handOver
        onPhotoPublished = dependencies.onPhotoPublished
        timeoutSeconds = dependencies.timeoutSeconds
        handOverTimeoutSeconds = dependencies.handOverTimeoutSeconds
    }

    /// Publie `photo`. `only` : les Miffs qu'un essai précédent n'a pas atteintes (nil = toutes). Ne lève jamais.
    func publish(_ photo: UpdatePhoto, only: [String]? = nil) async -> PublishResult {
        guard let uid = environment.currentUid() else {
            return .failed
        }
        if let refusal = guardRefusal(photo) {
            return refusal
        }
        var seen = Set<String>()
        let candidates = only ?? environment.miffIds()
        let targets = candidates.filter { seen.insert($0).inserted }
        if targets.isEmpty {
            return .sharingStopped
        }
        // Hors ligne : rien n'est tenté (une transaction échouerait de toute façon, après une longue attente). La photo reste à l'écran.
        guard network.isOnline() else {
            journal.record(.updateOfflineNoNetwork)
            return .offline
        }
        // Qui tient la session : relue auprès du serveur AVANT toute écriture (un téléphone remplacé n'écrit rien).
        let deviceId: String
        switch await device.checkAfterRefusal(uid: uid) {
        case .replaced:
            return .refused
        case .unverified:
            journal.record(.updateOfflineSessionUnverified)
            return .offline
        case .confirmed:
            guard case .cleared(let identifier) = await device.clearance(uid: uid) else {
                journal.record(.updateOfflineNoClearance)
                return .offline
            }
            deviceId = identifier
        }
        // Contrat du modèle de données : une photo n'est écrite que là où MA position est publiée (voir `positionRefusal` et
        // `publishableTargets` : là où le suivi ne l'a pas écrite, l'Update lui remet sa mesure, il ne l'écrit jamais lui-même).
        let missing = targets.filter { !isPositionPublished($0) }
        if !missing.isEmpty, let refusal = await positionRefusal(missing) {
            return refusal
        }
        let place: PhotoPlace
        switch await measurePlace() {
        case .success(let measured):
            place = measured
        case .failure(let refusal):
            journal.record(.updateMeasureFailed)
            return refusal.result
        }
        let publishable = await publishableTargets(targets, missing: missing, place: place)
        if publishable.isEmpty {
            return .positionNotFound
        }
        let battery = await environment.battery()
        let job = PublishJob(uid: uid, photo: photo, place: place, deviceId: deviceId, batteryPct: battery)
        return await send(job, targets: publishable, all: targets)
    }

    private func guardRefusal(_ photo: UpdatePhoto) -> PublishResult? {
        let now = environment.nowMillis()
        if photo.isStale(nowMillis: now) {
            return .stale
        }
        let verdict = PublishGuard.check(
            nowMillis: now,
            timeZone: environment.timeZone(),
            countDay: ledger.countedDay(),
            countToday: ledger.countToday(),
            lastMillis: ledger.lastPublishMillis()
        )
        switch verdict {
        case .tooMany:
            return .tooMany
        case .tooSoon:
            return .tooSoon
        case .go:
            return nil
        }
    }

    /// UNE mesure de la position, ici et pas avant : personne n'est localisé pour une photo jamais publiée.
    private func measurePlace() async -> Result<PhotoPlace, PublishResultRefusal> {
        switch await position.measure() {
        case .locationOff:
            return .failure(PublishResultRefusal(result: .locationOff))
        case .approximate:
            return .failure(PublishResultRefusal(result: .locationApproximate))
        case .notFound:
            return .failure(PublishResultRefusal(result: .positionNotFound))
        case .found(let fix):
            guard let place = PhotoPlace.from(fix, nowMillis: environment.nowMillis()) else {
                return .failure(PublishResultRefusal(result: .positionNotFound))
            }
            return .success(place)
        }
    }

    /// La note de la date de l'envoi est écrite AVANT qu'il parte (revue C2) : une transaction qui part après que l'écran a renoncé est quand
    /// même nettoyée 24 h plus tard. Un envoi CONNU pour ne pas être parti (refus, pas de réseau, rien de partagé) remet la note d'avant ;
    /// « non confirmé » garde la nouvelle.
    private func send(_ job: PublishJob, targets: [String], all: [String]) async -> PublishResult {
        let previousNote = ledger.photoSentMillis(uid: job.uid)
        ledger.notePhotoSent(uid: job.uid, millis: environment.nowMillis())
        let result = await writeLots(job, targets: targets, all: all)
        switch result {
        case .offline, .refused, .failed, .sharingStopped, .needsAgreement:
            if let previousNote {
                ledger.notePhotoSent(uid: job.uid, millis: previousNote)
            } else {
                ledger.clearPhotoSent()
            }
        default:
            break
        }
        return result
    }

    private func writeLots(_ job: PublishJob, targets: [String], all: [String]) async -> PublishResult {
        var outcome = LotsOutcome()
        for start in stride(from: 0, to: targets.count, by: UpdateLimits.lotSize) {
            let lot = Array(targets[start..<min(start + UpdateLimits.lotSize, targets.count)])
            do {
                let committed = try await commit(job, lot: lot)
                outcome.written += committed.written
                outcome.notSharing += committed.notSharing
                outcome.needsAgreement += committed.needsAgreement
            } catch {
                journal.record(Self.commitErrorKind(error))
                outcome.failure = Self.result(for: error)
                break
            }
        }
        if !outcome.written.isEmpty {
            recordPublication()
            announcePublished(job, written: outcome.written)
        }
        return outcome.result(all: all)
    }

    /// Ma carte montre ma photo sans lecture du serveur : l'heure est celle du téléphone, à l'horloge près (elle s'affiche comme l'heure de la photo).
    private func announcePublished(_ job: PublishJob, written: [String]) {
        let now = environment.nowMillis()
        let shared = SharedPhoto(
            jpeg: job.photo.jpeg,
            createdAtMillis: now,
            expiresAtMillis: now + UpdateWrites.photoLifetimeMillis,
            place: job.place
        )
        onPhotoPublished(PublishedPhoto(uid: job.uid, miffIds: written, photo: shared))
    }

    /// Une publication réussie (dans une partie du cercle au moins) est comptée pour le garde-fou.
    private func recordPublication() {
        let now = environment.nowMillis()
        let next = PublishGuard.next(
            nowMillis: now,
            timeZone: environment.timeZone(),
            countDay: ledger.countedDay(),
            countToday: ledger.countToday()
        )
        ledger.recordPublication(nowMillis: now, dayKey: next.day, count: next.count)
    }

    private func commit(_ job: PublishJob, lot: [String]) async throws -> PhotoCommit {
        // L'heure du lot est prise quand il est construit.
        let context = PublishContext(
            nowMillis: environment.nowMillis(), batteryPct: job.batteryPct, deviceId: job.deviceId
        )
        let store = self.store
        let uid = job.uid
        let photo = job.photo
        let place = job.place
        return try await timedOperation(seconds: timeoutSeconds) {
            try await store.commit(uid: uid, miffIds: lot, requireConsent: true) { allowed in
                allowed.flatMap {
                    UpdateWrites.publish(miffId: $0, uid: uid, photo: photo, place: place, context: context)
                }
            }
        }
    }

    /// Ce qu'une erreur de la transaction veut dire pour la personne : jamais un message technique.
    static func result(for error: Error) -> PublishResult {
        if error is OperationTimedOut {
            // Pas de réponse : la transaction a pu partir, ou non.
            return .unconfirmed
        }
        switch interpretMiffError(error) {
        case .offline:
            return isDeadline(error) ? .unconfirmed : .offline
        case .refused:
            return .refused
        case .noProfile, .unknown:
            return .failed
        }
    }

    private static func isDeadline(_ error: Error) -> Bool {
        let nsError = error as NSError
        return nsError.domain == FirestoreErrorNumber.domain && nsError.code == FirestoreErrorNumber.deadlineExceeded
    }
}

/// Une réponse de publication qui arrête tout avant l'écriture (position introuvable, localisation éteinte).
struct PublishResultRefusal: Error {
    let result: PublishResult

    init(result: PublishResult) {
        self.result = result
    }

    /// La réponse qui dit POURQUOI les autorisations empêchent la mesure ; nil quand la mesure n'est pas empêchée (position trouvée, ou
    /// simplement introuvable).
    init?(_ blocker: PhotoPosition) {
        switch blocker {
        case .locationOff:
            result = .locationOff
        case .approximate:
            result = .locationApproximate
        case .found, .notFound:
            return nil
        }
    }
}

/// Quand le suivi n'a pas encore publié MA position : la raison est dite, ou sa mesure lui est remise (en extension : le corps de la classe
/// reste court). Aucune écriture de position ici : l'Update n'écrit jamais `positions/{moi}` (critère U8).
extension UpdatePublisher {
    /// Si c'est une AUTORISATION qui empêche d'écrire ma position (« Pendant l'utilisation » au lieu de « Toujours », position approximative,
    /// localisation éteinte), « réessaie dans un instant » serait faux et sans fin : la phrase juste mène aux Réglages. Seules les
    /// autorisations sont lues ici, jamais une position. Le journal de diagnostic (codes seulement) dit si le suivi connaît seulement la Miff.
    fileprivate func positionRefusal(_ missing: [String]) async -> PublishResult? {
        journal.record(.updatePositionNotPublished)
        if missing.contains(where: { !trackingKnowsMiff($0) }) {
            journal.record(.updateTrackingUnawareOfMiff)
        }
        let blocker = await position.blocker(needsBackground: true)
        if let blocker, let refusal = PublishResultRefusal(blocker) {
            journal.record(.updatePermissionBlocked)
            return refusal.result
        }
        return nil
    }

    /// Les Miffs où la photo peut partir : celles où ma position est publiée. Pour les autres (`missing`), l'Update REMET au suivi la position
    /// qu'il vient de mesurer au toucher de « Publier » et attend la fin de son passage (borné) : le suivi la publie lui-même, avec toutes ses
    /// garanties (« Toujours » + position exacte, partage actif, consentement de version 3, session SESS-3, garde-fou, règles). La copie
    /// locale du suivi dit ensuite où elle est publiée (relue, jamais supposée). Vide : le suivi n'a pas pu (Miff inconnue de lui, refus, hors
    /// ligne, session), son journal et celui-ci en donnent la cause, et la personne est invitée à réessayer dans un instant.
    fileprivate func publishableTargets(_ targets: [String], missing: [String], place: PhotoPlace) async -> [String] {
        if missing.isEmpty {
            return targets
        }
        let fix = Fix(lat: place.lat, lng: place.lng, measuredAtMillis: place.measuredAtMillis)
        let handOver = self.handOver
        // Le passage du suivi attend le travail précédent du suivi (jusqu'à `wakeBudgetSeconds`) avant le sien : le délai de la remise est donc
        // plus long que celui d'une écriture (`handOverTimeoutSeconds`). Un dépassement n'est pas un échec du suivi : la copie locale est relue
        // quand même (le passage a pu finir juste après), et un code distinct le dit dans le journal.
        var timedOut = false
        do {
            try await timedOperation(seconds: handOverTimeoutSeconds) {
                await handOver(fix)
            }
        } catch {
            timedOut = true
        }
        let published = targets.filter { isPositionPublished($0) }
        if !published.isEmpty {
            journal.record(.updateHandOverWorked)
        } else {
            journal.record(timedOut ? .updateHandOverTimedOut : .updateHandOverFailed)
        }
        return published
    }
}

/// Le diagnostic d'un échec de la publication (recette TestFlight, 2026-11-11 : « Pas de connexion » avec du réseau). Le journal ne garde que
/// ce CODE de classe d'erreur : ni le message, ni le chemin, ni l'identifiant d'un document (l'erreur peut les citer).
extension UpdatePublisher {
    fileprivate static func commitErrorKind(_ error: Error) -> JournalKind {
        if error is OperationTimedOut {
            return .updateErrorTimedOut
        }
        let nsError = error as NSError
        if nsError.domain == NSURLErrorDomain {
            return .updateErrorNetworkUrl
        }
        guard nsError.domain == FirestoreErrorNumber.domain else {
            return .updateErrorOther
        }
        switch nsError.code {
        case FirestoreErrorNumber.unavailable:
            return .updateErrorUnavailable
        case FirestoreErrorNumber.cancelled:
            return .updateErrorCancelled
        case FirestoreErrorNumber.resourceExhausted:
            return .updateErrorQuota
        case FirestoreErrorNumber.deadlineExceeded:
            return .updateErrorTimedOut
        case FirestoreErrorNumber.permissionDenied, FirestoreErrorNumber.unauthenticated:
            return .updateErrorRefused
        default:
            return .updateErrorOther
        }
    }
}
