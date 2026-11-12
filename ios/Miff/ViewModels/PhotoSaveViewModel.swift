import Combine
import UIKit

// CONFIDENTIALITÉ et SÉCURITÉ, pour ce fichier (FEED-8) : « Enregistrer cette photo » (plein écran de la photo d'un proche, décision du PM du 2026-11-11).
// - L'écriture dans la galerie n'est lancée QUE par `save()`, appelée au TOUCHER de l'icône : jamais automatique, jamais en arrière-plan.
// - La première fois, la boîte du système demande l'AJOUT seul (`PhotoSaveAccess`) ; un refus ou une restriction se dit calmement (jamais culpabilisant),
//   avec « Ouvrir les réglages » pour un refus, et RIEN n'est écrit.
// - L'image enregistrée est ré-encodée depuis les pixels (`PhotoExport`) : aucune métadonnée, aucun lieu. Rien n'est journalisé ni envoyé.

enum PhotoSaveState: Equatable {
    case idle
    case saving
    case saved
    /// Autorisation refusée : « Ouvrir les réglages ».
    case denied
    /// Interdit par l'appareil : un message, pas de bouton.
    case restricted
    /// Le système n'a pas pu écrire (ou l'image n'a pas pu être préparée) : « Tu peux réessayer. »
    case failed
}

@MainActor
final class PhotoSaveViewModel: ObservableObject {
    @Published private(set) var state: PhotoSaveState = .idle
    private let saver: PhotoSaving
    private let image: UIImage
    private let encode: (UIImage) -> Data?

    init(saver: PhotoSaving, image: UIImage, encode: @escaping (UIImage) -> Data? = PhotoExport.cleanJpeg) {
        self.saver = saver
        self.image = image
        self.encode = encode
    }

    /// Le toucher de l'icône. Un second toucher pendant l'enregistrement ne fait rien.
    func save() async {
        guard state != .saving else {
            return
        }
        state = .saving
        var access = saver.access()
        if access == .notDetermined {
            access = await saver.requestAccess()
        }
        switch access {
        case .allowed:
            break
        case .restricted:
            state = .restricted
            return
        case .denied, .notDetermined:
            state = .denied
            return
        }
        guard let jpeg = encode(image) else {
            state = .failed
            return
        }
        state = await saver.save(jpeg: jpeg) ? .saved : .failed
    }

    /// Le message est lu ou a duré assez : retour au repos (jamais pendant l'enregistrement).
    func dismissMessage() {
        if state != .saving {
            state = .idle
        }
    }
}
