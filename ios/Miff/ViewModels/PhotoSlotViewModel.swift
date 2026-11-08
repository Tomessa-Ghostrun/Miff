import Combine
import UIKit

// CONFIDENTIALITÉ et COÛT, pour ce fichier (FEED-8) : le view model de la photo d'UNE carte. Il demande la photo UNE fois (la mémoire d'abord,
// le serveur ensuite : `PhotoReading`), seulement quand la carte vient à l'écran (la liste est paresseuse : l'écran appelle `load` dans sa tâche,
// qui s'arrête quand la carte disparaît ou que l'onglet change). La photo et son image décodée ne vivent QUE dans ce view model et dans la mémoire
// de la session : jamais un fichier, un journal, un événement. Une photo absente, expirée, refusée ou illisible ne dessine RIEN, jamais une erreur ;
// seule la réciprocité se dit, calmement. L'état (`PhotoSlotState`) ne montre rien dans `description`. Miroir de `MemberPhotoSlot` d'Android.
// MÉMOIRE : l'aperçu est décodé EN RÉDUIT (1 024 px au plus) et seulement quand la carte le dessine ; un avertissement de mémoire du système le
// libère (il est refait au besoin) ; la photo entière n'est décodée qu'à l'ouverture du plein écran.

/// Ce que dessine l'emplacement de la photo d'une carte.
enum PhotoSlotState: Equatable, CustomStringConvertible {
    /// La lecture est en cours : RIEN n'est dessiné (aucune réserve d'espace : la liste ne bouge que lorsqu'une photo arrive, comme sur Android).
    case loading
    case shown(SharedPhoto)
    /// « Partage ta position pour voir cette photo. »
    case needsOwnPosition
    /// Rien : ni trou ni message (absente, expirée, illisible, ou serveur injoignable ; le retour du réseau relance la lecture).
    case nothing

    var isShown: Bool {
        if case .shown = self {
            return true
        }
        return false
    }

    var description: String {
        "PhotoSlotState(redacted)"
    }
}

@MainActor
final class PhotoSlotViewModel: ObservableObject {
    @Published private(set) var state: PhotoSlotState
    private var previewImage: UIImage?
    private let reader: PhotoReading
    private let decoder: PhotoDecoding

    /// Si la mémoire connaît déjà la photo (ou son absence), la carte n'a pas d'état de chargement.
    init(reader: PhotoReading, request: PhotoRequest, decoder: PhotoDecoding = ImagePhotoDecoder()) {
        self.reader = reader
        self.decoder = decoder
        state = .loading
        if let known = reader.peek(request) {
            apply(known)
        }
    }

    /// L'aperçu de la photo montrée (réduit), décodé UNE fois, refait seulement après un avertissement de mémoire.
    var image: UIImage? {
        if previewImage == nil, case .shown(let photo) = state {
            previewImage = decoder.preview(from: photo.jpeg)
        }
        return previewImage
    }

    /// La photo ENTIÈRE pour le plein écran (jamais recadrée), décodée à l'ouverture seulement ; à défaut, l'aperçu.
    func fullImage() -> UIImage? {
        guard case .shown(let photo) = state else {
            return nil
        }
        return decoder.image(from: photo.jpeg) ?? image
    }

    /// Avertissement de mémoire du système : l'aperçu décodé est libéré (il sera refait s'il est redessiné).
    func releaseImage() {
        previewImage = nil
    }

    /// Lit la photo de ce statut (mémoire, puis UN `get` si elle n'est pas connue). Appelée à l'apparition de la carte et quand la photo
    /// change (`lastUpdateAt`) ou que le réseau revient ; jamais en boucle. La photo déjà montrée reste jusqu'à la fin de la lecture.
    func load(_ request: PhotoRequest) async {
        let lookup = await reader.read(request)
        guard !Task.isCancelled else {
            return
        }
        apply(lookup)
    }

    private func apply(_ lookup: PhotoLookup) {
        switch lookup {
        case .found(let photo):
            show(photo)
        case .missing:
            set(.nothing)
        case .needsOwnPosition:
            set(.needsOwnPosition)
        case .offline:
            // Une photo déjà montrée le reste ; sans photo, rien n'est dessiné (la mention hors connexion du Feed le dit déjà).
            if case .shown = state {
                return
            }
            set(.nothing)
        }
    }

    private func show(_ photo: SharedPhoto) {
        if case .shown(let current) = state, current == photo {
            return
        }
        guard let decoded = decoder.preview(from: photo.jpeg) else {
            // Pas un JPEG, ou démesuré (quelqu'un d'autre l'a écrit) : rien n'est dessiné.
            set(.nothing)
            return
        }
        previewImage = decoded
        set(.shown(photo))
    }

    private func set(_ next: PhotoSlotState) {
        if !next.isShown {
            previewImage = nil
        }
        if next != state {
            state = next
        }
    }
}

/// Ce que le Feed donne à ses cartes pour montrer les photos : la lecture, l'horloge et le fuseau, et le view model de chaque carte (un par
/// membre de la Miff affichée : une carte qui revient à l'écran retrouve le sien, donc sa photo, sans rien relire). Une carte sans `FeedPhotos`
/// (aperçus, tests) ne dessine aucune photo. Borné : au plus `maxModels` view models, vidés au changement de Miff, oubliés quand la carte n'a
/// plus de photo candidate, aperçus décodés libérés sur avertissement de mémoire.
@MainActor
final class FeedPhotos: ObservableObject {
    /// Un plafond doux de 15 membres par Miff : 16 suffit à tous les garder.
    static let maxModels = 16

    let reader: PhotoReading
    let nowMillis: () -> Int
    let zone: () -> TimeZone
    private var models: [String: PhotoSlotViewModel] = [:]
    private var order: [String] = []
    private var activeMiffId: String?
    private var memoryWarning: AnyCancellable?

    init(
        reader: PhotoReading,
        nowMillis: @escaping () -> Int = { Int(Date().timeIntervalSince1970 * 1000) },
        zone: @escaping () -> TimeZone = { TimeZone.current }
    ) {
        self.reader = reader
        self.nowMillis = nowMillis
        self.zone = zone
        memoryWarning = NotificationCenter.default
            .publisher(for: UIApplication.didReceiveMemoryWarningNotification)
            .sink { [weak self] _ in self?.releaseImages() }
    }

    /// Le nombre de view models gardés (les tests).
    var modelCount: Int {
        models.count
    }

    func model(for request: PhotoRequest) -> PhotoSlotViewModel {
        // Une autre Miff est affichée : ce qui a été gardé d'une autre Miff n'est jamais dessiné sur celle-ci.
        if request.miffId != activeMiffId {
            models = [:]
            order = []
            activeMiffId = request.miffId
        }
        let key = Self.key(request.miffId, request.uid)
        if let existing = models[key] {
            order.removeAll { $0 == key }
            order.append(key)
            return existing
        }
        let created = PhotoSlotViewModel(reader: reader, request: request)
        models[key] = created
        order.append(key)
        while order.count > Self.maxModels {
            models[order.removeFirst()] = nil
        }
        return created
    }

    /// La carte n'a plus de photo candidate (statut changé, plus de photo, partage arrêté) : son view model est oublié, plus rien d'ancien ne peut
    /// être redessiné si une photo revient.
    func forget(miffId: String, uid: String) {
        let key = Self.key(miffId, uid)
        models[key] = nil
        order.removeAll { $0 == key }
    }

    /// Avertissement de mémoire : les aperçus décodés sont libérés (les photos restent dans `PhotoCache`).
    func releaseImages() {
        for model in models.values {
            model.releaseImage()
        }
    }

    private static func key(_ miffId: String, _ uid: String) -> String {
        "\(miffId)/\(uid)"
    }
}
