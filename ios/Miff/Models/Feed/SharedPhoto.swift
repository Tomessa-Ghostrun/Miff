import Foundation

// CONFIDENTIALITÉ, pour ce fichier (FEED-8) : la photo de la dernière update d'un proche, avec le lieu où elle a été prise. Elle vit en
// MÉMOIRE seulement, pour la session (le cache est vidé à la déconnexion) : jamais dans un fichier de l'app, ni dans la photothèque, ni dans un
// journal, un événement, un rapport de plantage ou le presse-papiers. `description` ne montre rien (pas même la DESCRIPTION facultative que l'auteur a écrite). Ce qui vient du réseau a été écrit par
// QUELQU'UN D'AUTRE : tout est vérifié avant d'être utilisé (`fromServer`). Miroir de `SharedPhoto.kt` d'Android.

/// Les champs d'un document `miffs/{id}/photos/{uid}` tels que le serveur les décrit, AVANT toute vérification.
struct RawPhoto {
    var jpeg: Data?
    var createdAtMillis: Int?
    var expiresAtMillis: Int?
    var lat: Double?
    var lng: Double?
    var measuredAtMillis: Int?
    /// La valeur BRUTE du champ `description` (n'importe quel type : QUELQU'UN D'AUTRE l'a écrite) ; nettoyée par `SharedPhoto.fromServer`.
    var writtenDescription: Any?
}

/// La photo d'une update : le JPEG, l'heure de sa publication (heure serveur), l'heure où elle cesse d'être lisible, son lieu s'il en a un et sa
/// DESCRIPTION si l'auteur en a écrit une (texte de 1 à 140 unités, déjà nettoyé : voir `PhotoDescription` ; nil = aucune). Elle vit et meurt avec la photo.
struct SharedPhoto: Equatable, CustomStringConvertible {
    let jpeg: Data
    let createdAtMillis: Int
    let expiresAtMillis: Int
    /// Le lieu de la photo et l'heure de la MESURE (pas celle de la photo : les deux peuvent différer de quelques minutes).
    let place: PhotoPlace?
    /// Ce que l'auteur a écrit au sujet de sa photo (jamais montré dans un journal : `description` reste « redacted »).
    let descriptionText: String?

    init(jpeg: Data, createdAtMillis: Int, expiresAtMillis: Int, place: PhotoPlace?, descriptionText: String? = nil) {
        self.jpeg = jpeg
        self.createdAtMillis = createdAtMillis
        self.expiresAtMillis = expiresAtMillis
        self.place = place
        self.descriptionText = descriptionText
    }

    var description: String {
        "SharedPhoto(redacted)"
    }

    /// Une photo dont `expiresAt` est passé n'est jamais montrée, même depuis la mémoire.
    func isExpired(nowMillis: Int) -> Bool {
        expiresAtMillis <= nowMillis
    }

    /// Une photo telle que le serveur la décrit, ou nil quand elle ne peut pas être montrée : pas d'octets, plus que les 150 000 octets
    /// des règles (ce n'est pas la nôtre), une heure manquante, ou déjà expirée. Un lieu n'est gardé que si les trois valeurs sont là et
    /// valides (les règles les écrivent ensemble) : jamais la moitié d'un lieu.
    static func fromServer(_ raw: RawPhoto, nowMillis: Int) -> SharedPhoto? {
        guard let jpeg = raw.jpeg, !jpeg.isEmpty, jpeg.count <= UpdatePhotoFormat.maxBytes,
              let created = raw.createdAtMillis, let expires = raw.expiresAtMillis, expires > nowMillis else {
            return nil
        }
        // La description : seulement un texte de 1 à 140 unités, nettoyé (critère C5) ; tout autre type est ignoré, la photo reste montrée.
        return SharedPhoto(
            jpeg: jpeg, createdAtMillis: created, expiresAtMillis: expires, place: place(of: raw),
            descriptionText: PhotoDescription.fromServer(raw.writtenDescription)
        )
    }

    private static func place(of raw: RawPhoto) -> PhotoPlace? {
        guard let lat = raw.lat, let lng = raw.lng, let measuredAt = raw.measuredAtMillis,
              lat.isFinite, lng.isFinite, (-90.0...90.0).contains(lat), (-180.0...180.0).contains(lng) else {
            return nil
        }
        return PhotoPlace(lat: lat, lng: lng, measuredAtMillis: measuredAt)
    }
}

/// La place de la photo sur une carte (FEED-8). `none` : rien n'est dessiné (ni trou ni message), comme pour une photo expirée, refusée ou
/// illisible. `candidate` : le statut dit qu'il y a une photo et qu'elle est assez récente pour être lisible ; la carte la lit quand elle
/// vient à l'écran.
enum PhotoSlot: Equatable {
    case none
    /// `lastUpdateAtMillis` : la dernière update du statut auquel va la photo (une copie faite pour la même valeur n'est pas relue).
    case candidate(lastUpdateAtMillis: Int)

    /// Une photo vit 23 h (le client écrit `expiresAt` = publication + 23 h) : un statut plus ancien ne peut pas avoir de photo lisible,
    /// donc rien n'est lu (une lecture refusée coûte quand même des lectures de règles).
    static let readWindowMillis = 23 * FeedMillis.hour

    /// Une dernière update dans le futur (horloge fausse) compte comme maintenant.
    static func of(hasPhoto: Bool, lastUpdateAtMillis: Int?, visible: Bool, nowMillis: Int) -> PhotoSlot {
        guard visible, hasPhoto, let update = lastUpdateAtMillis else {
            return .none
        }
        return nowMillis - min(update, nowMillis) < readWindowMillis ? .candidate(lastUpdateAtMillis: update) : .none
    }
}

/// Ce que la lecture de la photo d'un membre a dit, réduit à ce que la carte sait dire.
enum PhotoLookup: Equatable, CustomStringConvertible {
    case found(SharedPhoto)
    /// Pas de photo (absente, expirée, illisible) : la carte ne dessine rien, aucun message.
    case missing
    /// Les règles ont refusé ET je n'ai pas publié ma propre position : la réciprocité, dite calmement (« Partage ta position pour voir
    /// cette photo. »), jamais « erreur ».
    case needsOwnPosition
    /// Le serveur n'a pas pu être joint (et aucune copie en mémoire) : rien n'est dessiné ; la carte réessaie au retour du réseau.
    case offline

    var description: String {
        "PhotoLookup(redacted)"
    }
}

/// Ce que la lecture brute du serveur a dit, avant la mémoire et la réciprocité.
enum PhotoFetch: Equatable, CustomStringConvertible {
    case photo(SharedPhoto)
    /// Le document n'existe pas, est expiré ou n'est pas valide.
    case absent
    /// Les règles ont refusé : AMBIGU (réciprocité, partage arrêté, expirée). Jamais une raison de supprimer quoi que ce soit.
    case refused
    case unreachable

    var description: String {
        "PhotoFetch(redacted)"
    }
}

/// Ce que demande la carte d'un membre.
struct PhotoRequest: Equatable, CustomStringConvertible {
    let miffId: String
    let uid: String
    /// La `lastUpdateAt` du statut que la carte vient de lire.
    let lastUpdateAtMillis: Int
    /// C'est MA carte : un refus n'y est jamais la réciprocité.
    let isMe: Bool

    var description: String {
        "PhotoRequest(redacted)"
    }
}
