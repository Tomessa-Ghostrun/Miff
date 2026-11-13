import Foundation

// CONFIDENTIALITÉ, pour ce fichier (ticket LIEU-REQ, critère C2) : le créateur voit QUI a demandé un lieu (prénom, et photo ou initiales,
// comme la fiche du membre dans la Miff). Rien d'autre de la personne n'est gardé (pas le nom entier, pas le numéro). Cela reste en mémoire,
// jamais journalisé, jamais dans un événement ; chaque type qui le porte cache son contenu dans `description`.

/// La personne derrière une demande, telle que le créateur la voit.
struct RequesterCard: Equatable, CustomStringConvertible {
    let uid: String
    let firstName: String
    let initials: String
    let avatarJpeg: Data?

    var description: String {
        "RequesterCard(redacted)"
    }

    static func of(uid: String, firstName: String, lastName: String, avatarJpeg: Data?) -> RequesterCard {
        // Une photo plus grande que ce que les règles permettent n'est jamais crue (l'app ne l'aurait pas écrite).
        let photo = avatarJpeg.flatMap { bytes -> Data? in
            bytes.isEmpty || bytes.count > ProfileDocument.maxAvatarBytes ? nil : bytes
        }
        return RequesterCard(
            uid: uid,
            firstName: firstName.isEmpty ? lastName : firstName,
            initials: Initials.of(first: firstName, last: lastName),
            avatarJpeg: photo
        )
    }
}

/// Ce qu'on sait de l'auteur d'une demande.
enum RequesterState: Equatable, CustomStringConvertible {
    /// La fiche du membre a été lue : le créateur voit qui a demandé.
    case known(RequesterCard)
    /// Le serveur dit que cette personne n'est plus dans la Miff (partie, retirée) : ses demandes sont masquées, puis supprimées par le
    /// créateur (ticket 5.3, L2).
    case notMember
    /// Illisible (hors ligne, lenteur) : la demande est montrée sans prénom, jamais cachée sur un doute.
    case unknown

    var description: String {
        "RequesterState(redacted)"
    }
}

/// Une demande que le CRÉATEUR a reçue, avec qui l'a faite (nil tant que la fiche du membre n'est pas lue : la demande est alors montrée
/// sans prénom, jamais cachée sur un doute).
struct ReceivedRequest: Equatable, CustomStringConvertible {
    let request: PlaceRequest
    let requester: RequesterState?

    var description: String {
        "ReceivedRequest(redacted)"
    }

    /// Le prénom, quand la fiche est lue.
    var firstName: String {
        if case .known(let card)? = requester {
            return card.firstName
        }
        return ""
    }
}

enum RequesterKey {
    /// La clé d'une personne dans une Miff.
    static func key(miffId: String, uid: String) -> String {
        "\(miffId)|\(uid)"
    }
}
