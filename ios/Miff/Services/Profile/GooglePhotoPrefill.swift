import Foundation

// SÉCURITÉ, pour ce fichier : la photo Google est une donnée personnelle. Elle est téléchargée UNE fois, ne vit qu'en mémoire,
// n'est écrite sur le disque que par le chemin normal du profil (le brouillon, puis le profil), n'est envoyée nulle part ailleurs
// et n'apparaît dans aucun journal, aucune erreur, aucun événement d'analyse.

/// Télécharge les octets d'une photo à une adresse déjà acceptée par `GooglePhotoURL`. Rend nil au moindre échec.
protocol ProviderPhotoSource {
    func fetch(_ url: URL) async -> Data?
}

/// Tout le chemin entre l'adresse donnée par Google et l'avatar du profil : choisir l'adresse, télécharger UNE fois, décoder (par le
/// chemin strict des photos reçues du réseau : `AvatarCodec.decodeRemote`), découper le carré au centre, ré-encoder SANS MÉTADONNÉE en
/// 20 000 octets au plus (les mêmes outils que la photo choisie par la personne : `AvatarCodec`, `AvatarCompression`). Rend nil quand
/// quelque chose échoue, y compris après `timeout` : jamais une erreur, jamais plus d'attente que ce délai. L'inscription ne dépend
/// jamais de ce résultat.
///
/// Tout tourne dans des tâches STRUCTURÉES : au délai, ou quand l'appelant annule (la personne a choisi une autre photo), le
/// téléchargement en cours est annulé lui aussi.
struct GooglePhotoPrefill {
    /// Court exprès : la photo est un plus, la personne ne doit pas l'attendre (l'étape profil est déjà utilisable).
    static let defaultTimeout: TimeInterval = 8

    let source: ProviderPhotoSource
    let codec: AvatarCodec
    var timeout: TimeInterval = GooglePhotoPrefill.defaultTimeout

    func load(_ provided: URL?) async -> Data? {
        guard let url = GooglePhotoURL.select(provided) else {
            return nil
        }
        let nanoseconds = UInt64(max(timeout, 0) * 1_000_000_000)
        // Le premier des deux gagne : la photo (ou son échec), ou le délai. L'autre est annulé aussitôt.
        return await withTaskGroup(of: Data?.self) { group in
            group.addTask { await self.avatar(from: url) }
            group.addTask {
                try? await Task.sleep(nanoseconds: nanoseconds)
                return nil
            }
            let first = await group.next()
            group.cancelAll()
            return first ?? nil
        }
    }

    private func avatar(from url: URL) async -> Data? {
        guard let bytes = await source.fetch(url), !Task.isCancelled else {
            return nil
        }
        guard let photo = await codec.decodeRemote(bytes) else {
            return nil
        }
        let crop = CropMath.toCrop(
            width: photo.width,
            height: photo.height,
            zoom: CropMath.minZoom,
            center: (Double(photo.width) / 2, Double(photo.height) / 2)
        )
        let encoded = await codec.encode(photo, crop: crop)
        guard let jpeg = encoded, !jpeg.isEmpty, jpeg.count <= AvatarCompression.maxBytes else {
            return nil
        }
        return jpeg
    }
}
