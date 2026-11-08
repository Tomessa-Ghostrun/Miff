import Combine
import UIKit

// CONFIDENTIALITÉ et COÛT, pour ce fichier (FEED-8) : le view model de la photo d'UNE carte. Il demande la photo UNE fois (la mémoire d'abord,
// le serveur ensuite : `PhotoReading`), seulement quand la carte vient à l'écran (la liste est paresseuse : l'écran appelle `load` dans sa tâche,
// qui s'arrête quand la carte disparaît ou que l'onglet change). La photo et son image décodée ne vivent QUE dans ce view model et dans la mémoire
// de la session : jamais un fichier, un journal, un événement. Une photo absente, expirée, refusée ou illisible ne dessine RIEN, jamais une erreur ;
// seule la réciprocité se dit, calmement. L'état (`PhotoSlotState`) ne montre rien dans `description`. Miroir de `MemberPhotoSlot` d'Android.

/// Ce que dessine l'emplacement de la photo d'une carte.
enum PhotoSlotState: Equatable, CustomStringConvertible {
    /// La lecture est en cours : un cadre pâle de la taille de la photo, pour que la liste ne saute pas.
    case loading
    case shown(SharedPhoto)
    /// « Partage ta position pour voir cette photo. »
    case needsOwnPosition
    /// Rien : ni trou ni message (absente, expirée, illisible, ou serveur injoignable ; le retour du réseau relance la lecture).
    case nothing

    var description: String {
        "PhotoSlotState(redacted)"
    }
}

@MainActor
final class PhotoSlotViewModel: ObservableObject {
    @Published private(set) var state: PhotoSlotState
    /// L'image décodée de la photo montrée, faite UNE fois par photo.
    private(set) var image: UIImage?
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

    /// Lit la photo de ce statut (mémoire, puis UN `get` si elle n'est pas connue). Appelée à l'apparition de la carte et quand la photo
    /// change (`lastUpdateAt`) ou que le réseau revient ; jamais en boucle.
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
            set(.nothing, image: nil)
        case .needsOwnPosition:
            set(.needsOwnPosition, image: nil)
        case .offline:
            // Une photo déjà montrée le reste ; sans photo, rien n'est dessiné (la mention hors connexion du Feed le dit déjà).
            if case .shown = state {
                return
            }
            set(.nothing, image: nil)
        }
    }

    private func show(_ photo: SharedPhoto) {
        if case .shown(let current) = state, current == photo, image != nil {
            return
        }
        guard let decoded = decoder.image(from: photo.jpeg) else {
            // Pas une image, ou démesurée (quelqu'un d'autre l'a écrite) : rien n'est dessiné.
            set(.nothing, image: nil)
            return
        }
        set(.shown(photo), image: decoded)
    }

    private func set(_ next: PhotoSlotState, image decoded: UIImage?) {
        image = decoded
        if next != state {
            state = next
        }
    }
}

/// Ce que le Feed donne à ses cartes pour montrer les photos : la lecture, l'horloge et le fuseau, et le view model de chaque carte (un par
/// membre et par Miff : une carte qui revient à l'écran retrouve le sien, donc sa photo, sans rien relire). Une carte sans `FeedPhotos`
/// (aperçus, tests) ne dessine aucune photo.
@MainActor
final class FeedPhotos: ObservableObject {
    let reader: PhotoReading
    let nowMillis: () -> Int
    let zone: () -> TimeZone
    private var models: [String: PhotoSlotViewModel] = [:]

    init(
        reader: PhotoReading,
        nowMillis: @escaping () -> Int = { Int(Date().timeIntervalSince1970 * 1000) },
        zone: @escaping () -> TimeZone = { TimeZone.current }
    ) {
        self.reader = reader
        self.nowMillis = nowMillis
        self.zone = zone
    }

    func model(for request: PhotoRequest) -> PhotoSlotViewModel {
        let key = "\(request.miffId)/\(request.uid)"
        if let existing = models[key] {
            return existing
        }
        let created = PhotoSlotViewModel(reader: reader, request: request)
        models[key] = created
        return created
    }
}
