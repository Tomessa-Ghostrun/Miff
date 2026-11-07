import AVFoundation
import Foundation

// CONFIDENTIALITÉ ET SÉCURITÉ, pour ce fichier (viseur, ticket Update §5.3) :
// - la caméra est liée à l'écran : elle démarre quand le viseur apparaît et est RELÂCHÉE quand il disparaît (autre onglet, retour) ET quand
//   l'app passe en arrière-plan ou que l'écran se verrouille (l'écran appelle `stop()`). Miff n'ouvre jamais la caméra de lui-même et rien
//   ne peut la déclencher à distance (critère P1) ;
// - la photo est prise EN MÉMOIRE (`AVCapturePhoto.fileDataRepresentation`) : aucun fichier n'est créé, rien ne va dans la photothèque,
//   aucune autorisation de stockage ni de photothèque ;
// - ni flash, ni zoom, ni filtre, ni minuterie : non demandés (V4). Rien ici ne journalise quoi que ce soit.
// - le REFLET (D37) : la capture et l'aperçu sont réglés EXPLICITEMENT (le système ne décide pas) : la capture n'est jamais retournée par la
//   caméra, c'est `PhotoEncoder` qui retourne l'image de la caméra frontale (testé) ; l'aperçu de la caméra frontale est retourné (comme
//   un miroir), celui de l'arrière jamais.

enum CameraLens: Equatable {
    case front
    case back
}

/// Ce que l'écran a besoin du viseur : quelle caméra, s'il y en a deux, « prendre la photo ».
final class CameraController: NSObject, ObservableObject, AVCapturePhotoCaptureDelegate {
    @Published private(set) var lens: CameraLens = .front
    /// Une caméra frontale ET une arrière existent : le bouton et le double toucher changent de caméra (sinon le bouton est masqué).
    @Published private(set) var canSwitch = false

    let session = AVCaptureSession()
    /// Appelé (fil principal) quand aucune caméra ne peut servir : l'écran le dit calmement, jamais un message technique.
    var onUnavailable: (() -> Void)?

    private let queue = DispatchQueue(label: "com.tomessa.miff.camera")
    private let output = AVCapturePhotoOutput()
    private var input: AVCaptureDeviceInput?
    private weak var previewLayer: AVCaptureVideoPreviewLayer?
    private var busy = false
    private var pending: ((CGImage?, CameraLens) -> Void)?
    private var pendingLens: CameraLens = .front
    private var observer: NSObjectProtocol?

    override init() {
        super.init()
        observer = NotificationCenter.default.addObserver(
            forName: AVCaptureSession.runtimeErrorNotification, object: session, queue: .main
        ) { [weak self] _ in
            self?.onUnavailable?()
        }
    }

    deinit {
        if let observer {
            NotificationCenter.default.removeObserver(observer)
        }
    }

    /// Le calque d'aperçu à régler (reflet) après chaque configuration.
    func attach(_ layer: AVCaptureVideoPreviewLayer) {
        previewLayer = layer
        applyPreviewMirror()
    }

    // MARK: - Cycle de vie

    /// Démarre la caméra : la FRONTALE d'abord (selfie par défaut, à chaque ouverture) ; sans frontale, l'arrière ; sans aucune, l'écran
    /// dit « caméra indisponible ».
    func start() {
        let wanted = lens
        queue.async { [weak self] in
            self?.configureAndRun(wanted)
        }
    }

    /// Relâche la caméra (quitter l'écran, arrière-plan, verrouillage).
    func stop() {
        queue.async { [weak self] in
            guard let self else {
                return
            }
            if self.session.isRunning {
                self.session.stopRunning()
            }
            self.session.beginConfiguration()
            self.session.inputs.forEach { self.session.removeInput($0) }
            self.session.outputs.forEach { self.session.removeOutput($0) }
            self.session.commitConfiguration()
            self.input = nil
        }
    }

    /// Change de caméra (double toucher, bouton). Sans effet quand il n'y a qu'une caméra.
    func switchLens() {
        guard canSwitch else {
            return
        }
        let wanted: CameraLens = lens == .front ? .back : .front
        lens = wanted
        queue.async { [weak self] in
            self?.configureAndRun(wanted)
        }
    }

    // MARK: - Configuration (sur la file de la caméra)

    private static func device(for lens: CameraLens) -> AVCaptureDevice? {
        AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: lens == .front ? .front : .back)
    }

    private func configureAndRun(_ wanted: CameraLens) {
        let hasFront = Self.device(for: .front) != nil
        let hasBack = Self.device(for: .back) != nil
        let resolved: CameraLens
        if wanted == .front && hasFront {
            resolved = .front
        } else if hasBack {
            resolved = .back
        } else if hasFront {
            resolved = .front
        } else {
            DispatchQueue.main.async { [weak self] in
                self?.onUnavailable?()
            }
            return
        }
        guard configure(resolved) else {
            DispatchQueue.main.async { [weak self] in
                self?.onUnavailable?()
            }
            return
        }
        if !session.isRunning {
            session.startRunning()
        }
        DispatchQueue.main.async { [weak self] in
            self?.canSwitch = hasFront && hasBack
            self?.lens = resolved
            self?.applyPreviewMirror()
        }
    }

    private func configure(_ lens: CameraLens) -> Bool {
        session.beginConfiguration()
        defer {
            session.commitConfiguration()
        }
        session.sessionPreset = .photo
        if let existing = input {
            session.removeInput(existing)
            input = nil
        }
        guard let device = Self.device(for: lens),
              let newInput = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(newInput) else {
            return false
        }
        session.addInput(newInput)
        input = newInput
        if !session.outputs.contains(output) {
            guard session.canAddOutput(output) else {
                return false
            }
            session.addOutput(output)
        }
        configureCaptureConnection()
        return true
    }

    /// La capture est TOUJOURS non retournée et verticale (portrait) : le reflet de la caméra frontale est fait par `PhotoEncoder`.
    private func configureCaptureConnection() {
        guard let connection = output.connection(with: .video) else {
            return
        }
        if connection.isVideoMirroringSupported {
            connection.automaticallyAdjustsVideoMirroring = false
            connection.isVideoMirrored = false
        }
        if #available(iOS 17.0, *) {
            if connection.isVideoRotationAngleSupported(90) {
                connection.videoRotationAngle = 90
            }
        } else if connection.isVideoOrientationSupported {
            connection.videoOrientation = .portrait
        }
    }

    /// L'aperçu de la caméra frontale est un reflet (comme un miroir), celui de l'arrière non (fil principal).
    private func applyPreviewMirror() {
        guard let connection = previewLayer?.connection, connection.isVideoMirroringSupported else {
            return
        }
        connection.automaticallyAdjustsVideoMirroring = false
        connection.isVideoMirrored = lens == .front
    }

    // MARK: - Prendre la photo

    /// Prend UNE photo (une seconde demande pendant qu'une est en cours est ignorée : deux touchers sur l'obturateur donnent une photo,
    /// critère V1). `completion` est appelé sur le fil principal avec l'image REDRESSÉE et la caméra utilisée, ou nil si ça a échoué.
    /// Vrai si la demande est partie.
    @discardableResult
    func takePhoto(completion: @escaping (CGImage?, CameraLens) -> Void) -> Bool {
        guard !busy, session.isRunning else {
            return false
        }
        busy = true
        pending = completion
        pendingLens = lens
        queue.async { [weak self] in
            guard let self else {
                return
            }
            let settings: AVCapturePhotoSettings
            if self.output.availablePhotoCodecTypes.contains(.jpeg) {
                settings = AVCapturePhotoSettings(format: [AVVideoCodecKey: AVVideoCodecType.jpeg])
            } else {
                settings = AVCapturePhotoSettings()
            }
            self.output.capturePhoto(with: settings, delegate: self)
        }
        return true
    }

    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        // Les octets de la capture ne vivent que dans cette fonction : lus en mémoire, redressés, puis oubliés.
        let image = error == nil ? photo.fileDataRepresentation().flatMap { PhotoEncoder.decodeUpright($0) } : nil
        DispatchQueue.main.async { [weak self] in
            guard let self else {
                return
            }
            let done = self.pending
            let usedLens = self.pendingLens
            self.pending = nil
            self.busy = false
            done?(image, usedLens)
        }
    }
}
