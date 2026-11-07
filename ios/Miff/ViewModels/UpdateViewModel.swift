import Foundation

// CONFIDENTIALITÉ ET SÉCURITÉ, pour ce fichier : la photo prise vit ICI, en mémoire, et nulle part ailleurs (jamais sur le disque, dans un
// journal, un événement, l'état restauré par le système, le presse-papiers). Elle est oubliée dès que la personne quitte l'Update,
// recommence, publie, ou attend plus de 5 minutes. RIEN n'est écrit avant « Publier » (critère V2), et « Publier » n'existe que sur
// l'aperçu d'une photo prise (D16, critère U9) : aucun chemin de cette classe ne publie sans photo. Miroir d'`UpdateViewModel.kt` d'Android.

struct UpdateViewModelDependencies {
    let publisher: UpdatePublisher
    let cleaner: PhotoCleaner
    let ledger: PublishLedger
    let permission: CameraPermissionProviding
    /// Le partage est-il arrêté dans TOUTES mes Miffs ? (D36 : connu d'avance seulement avec UNE Miff ; avec plusieurs, le cas mixte est
    /// traité à l'écriture.) Une lecture auprès du serveur à l'ouverture de l'écran ; faux quand elle ne répond pas.
    let sharingStopped: () async -> Bool
    var nowMillis: () -> Int = { Int(Date().timeIntervalSince1970 * 1000) }
}

@MainActor
final class UpdateViewModel: ObservableObject {
    @Published private(set) var stage: UpdateStage = .idle

    /// Une photo est publiée dans `sharedIn` Miff(s) : retour au Feed, avec le message calme.
    var onPublished: ((Int) -> Void)?

    private let publisher: UpdatePublisher
    private let cleaner: PhotoCleaner
    private let ledger: PublishLedger
    private let permission: CameraPermissionProviding
    private let sharingStopped: () async -> Bool
    private let nowMillis: () -> Int
    private var publishTask: Task<Void, Never>?
    private var cleanTask: Task<Void, Never>?
    private var openingTask: Task<Void, Never>?

    init(_ dependencies: UpdateViewModelDependencies) {
        publisher = dependencies.publisher
        cleaner = dependencies.cleaner
        ledger = dependencies.ledger
        permission = dependencies.permission
        sharingStopped = dependencies.sharingStopped
        nowMillis = dependencies.nowMillis
    }

    // MARK: - Ouverture

    /// L'Update vient à l'écran (l'onglet), ou l'app revient au premier plan. Une photo qui attend encore (l'écran n'était que masqué par
    /// l'app passée en arrière-plan) est gardée 5 minutes puis oubliée.
    func onOpened() {
        if case .preview(let current) = stage {
            if current.photo.isStale(nowMillis: nowMillis()) && current.notice != .stale {
                stage = .preview(PreviewState(photo: current.photo, notice: .stale))
            }
            return
        }
        // Rouvert alors que le viseur est déjà là (l'app revient au premier plan) : rien ne change, l'ouverture n'est pas comptée deux fois.
        if case .viewfinder = stage, permission.status() == .authorized {
            return
        }
        openingTask?.cancel()
        openingTask = Task { [weak self] in
            guard let self else {
                return
            }
            let stopped = await self.sharingStopped()
            guard !Task.isCancelled else {
                return
            }
            self.applyOpening(sharingStopped: stopped)
        }
    }

    private func applyOpening(sharingStopped: Bool) {
        if sharingStopped {
            stage = .sharingStopped
            return
        }
        switch CameraAccess.forOpening(permission.status()) {
        case .viewfinder:
            stage = viewfinder()
        case .explain:
            stage = .explain
        case .disabled:
            stage = .permissionDisabled
        case .restricted:
            stage = .cameraRestricted
        }
    }

    private func viewfinder(prepareFailed: Bool = false) -> UpdateStage {
        // L'astuce du double toucher reste jusqu'à ce que la personne ait changé de caméra par un double toucher une fois (PM, 2026-11-06),
        // puis plus jamais.
        .viewfinder(showTip: !ledger.doubleTapUsed(), prepareFailed: prepareFailed)
    }

    /// « Continuer » du pop-in d'explication : la fenêtre du système s'affiche (UNE seule fois sur iPhone), puis le viseur ou « désactivée ».
    func onExplanationAccepted() {
        guard stage == .explain else {
            return
        }
        Task { [weak self] in
            guard let self else {
                return
            }
            let granted = await self.permission.request()
            guard self.stage == .explain else {
                return
            }
            switch CameraAccess.afterRequest(granted: granted) {
            case .viewfinder:
                self.stage = self.viewfinder()
            default:
                self.stage = .permissionDisabled
            }
        }
    }

    /// « Ouvrir les réglages » : les Réglages de Miff dans le système.
    func openSettings() {
        permission.openSettings()
    }

    // MARK: - Viseur

    /// La caméra a été changée par un double toucher : l'astuce disparaît POUR DE BON (gardé pour l'installation).
    func onSwitchedByDoubleTap() {
        ledger.markDoubleTapUsed()
        if case .viewfinder(_, let failed) = stage {
            stage = .viewfinder(showTip: false, prepareFailed: failed)
        }
    }

    /// La caméra n'a pas pu démarrer (une autre application la tient, une politique, aucune caméra) : état D, jamais un message technique.
    func onCameraUnavailable() {
        if case .viewfinder = stage {
            stage = .cameraUnavailable
        }
    }

    /// « Réessayer » après une caméra indisponible.
    func onRetryCamera() {
        guard stage == .cameraUnavailable else {
            return
        }
        applyOpening(sharingStopped: false)
    }

    /// Une photo a été prise et préparée. Seulement depuis le viseur (ou l'écran « caméra indisponible »).
    func onPhotoReady(_ photo: UpdatePhoto) {
        switch stage {
        case .viewfinder, .cameraUnavailable:
            stage = .preview(PreviewState(photo: photo))
        default:
            break
        }
    }

    /// La photo prise n'a pas pu être préparée (pas lisible, ou rien ne tient en 150 000 octets) : une phrase calme, rien n'est écrit.
    func onPhotoFailed() {
        switch stage {
        case .viewfinder, .cameraUnavailable:
            stage = viewfinder(prepareFailed: true)
        default:
            break
        }
    }

    // MARK: - Aperçu

    /// « Recommencer » ET la croix de l'aperçu (D22 : le même geste) : retour au viseur, la photo est oubliée, rien n'est publié, aucune
    /// question. Pas pendant qu'une publication est en cours (on ne l'annule pas une fois partie).
    func onRetake() {
        guard case .preview(let current) = stage, !current.publishing else {
            return
        }
        stage = viewfinder()
    }

    /// La personne a quitté l'Update (un autre onglet, retour) : la photo est oubliée, quoi qu'il arrive. Une publication déjà partie continue.
    func onClosed() {
        openingTask?.cancel()
        if publishTask != nil {
            return
        }
        stage = .idle
    }

    /// « Publier ». Seulement sur l'aperçu d'une photo.
    func onPublish() {
        guard case .preview(let current) = stage, !current.publishing, publishTask == nil else {
            return
        }
        var publishing = current
        publishing.publishing = true
        publishing.notice = nil
        stage = .preview(publishing)
        let publisher = self.publisher
        publishTask = Task { [weak self] in
            let result = await publisher.publish(current.photo, only: current.remaining)
            self?.finishPublication(result)
        }
    }

    private func finishPublication(_ result: PublishResult) {
        publishTask = nil
        guard case .preview(var current) = stage else {
            // L'écran a été quitté pendant l'envoi : rien ne reste en mémoire, la confirmation (si elle a lieu) est dite quand même.
            if case .published(let count) = result {
                onPublished?(count)
            }
            return
        }
        current.publishing = false
        switch result {
        case .published(let count):
            stage = .idle
            onPublished?(count)
        case .partial(let shared, let total, let remaining):
            current.notice = .partial(sharedIn: shared, total: total)
            current.remaining = remaining
            stage = .preview(current)
        default:
            current.notice = Self.notice(for: result)
            stage = .preview(current)
        }
    }

    /// La phrase calme d'une réponse qui n'a pas publié.
    static func notice(for result: PublishResult) -> PreviewNotice {
        switch result {
        case .offline:
            return .offline
        case .positionNotFound:
            return .positionNotFound
        case .tooMany:
            return .tooMany
        case .tooSoon:
            return .tooSoon
        case .stale:
            return .stale
        case .refused:
            return .refused
        case .locationOff:
            return .locationOff
        case .unconfirmed:
            return .unconfirmed
        case .needsAgreement:
            return .needsAgreement
        case .sharingStopped:
            return .sharingStopped
        case .partial(let shared, let total, _):
            return .partial(sharedIn: shared, total: total)
        case .published, .failed:
            return .failed
        }
    }

    // MARK: - Nettoyage

    /// Au lancement et au retour au premier plan : retire MA photo de la base une fois expirée (aucune lecture du serveur tant que la date
    /// locale existe).
    func cleanUpExpiredPhoto(_ mine: MyPhotoFacts?) {
        if cleanTask != nil {
            return
        }
        let cleaner = self.cleaner
        cleanTask = Task { [weak self] in
            await cleaner.cleanUp(mine)
            self?.cleanTask = nil
        }
    }
}
