import Foundation

// CNC-L et CNC-I (ticket docs/tickets/centre-notifications-complet.md §5.3 et §11, décisions du PM du 2026-11-19) : DE QUI parle une ligne du centre,
// donc quelle est son image ronde. « La photo dit QUI, le logo dit MIFF » :
// - une PERSONNE est sa photo de profil (celle que ses proches voient déjà dans leur Miff), ses initiales quand elle n'en a pas ;
// - la Miff ENTIÈRE (N3, N4) est le LOGO de Miff ;
// - MES PROPRES rappels (N1, N2, N6, N7) sont MA photo (mes initiales quand je n'en ai pas) ;
// - quelqu'un qu'on ne retrouve pas (parti de la Miff, compte supprimé, fiche illisible) est le LOGO, avec les mots « Un proche » : jamais un rond
//   vide, jamais un « ? ».
// Règles pures (aucun écran, aucun Firestore), testées sans réseau. Miroir de `CenterAvatars.kt` d'Android.
//
// CONFIDENTIALITÉ : le centre ne garde JAMAIS une photo. Elle est lue en mémoire quand la ligne est dessinée (les membres que le Feed a lus, mon
// profil, ou une lecture de la fiche du membre) et part avec la session. Une photo plus lourde que ce que les règles autorisent n'est pas crue :
// les initiales la remplacent, elle n'est pas décodée. Ce dossier est EXCLU du garde-fou « aucune photo dans le code des notifications » parce que
// c'est ici, et seulement ici (avec `CenterFaces` et la vue de l'avatar), que la photo passe, en mémoire.

/// L'image ronde d'une ligne.
enum CenterAvatar: Equatable, CustomStringConvertible {
    /// Le logo de Miff sur son rond bleu : la Miff entière, ou quelqu'un qu'on ne peut pas nommer.
    case logo
    /// La photo n'est pas connue ENCORE (une lecture est en cours) : un rond neutre, ni lettres ni logo, pour que rien ne clignote.
    case placeholder
    /// Les initiales d'une personne sans photo (ou dont la photo ne peut pas être lue).
    case initials(String)
    /// La photo (JPEG carré de 20 000 octets au plus) ; les `initials` la remplacent si elle ne se décode pas.
    case photo(Data, initials: String)

    var description: String {
        "CenterAvatar(redacted)"
    }
}

/// De qui parle une ligne.
enum CenterSubject: Equatable, CustomStringConvertible {
    /// La Miff entière (N3, N4) : le logo.
    case wholeMiff
    /// Moi (N1, N2, N6, N7) : ma photo.
    case myself
    /// Un proche de la Miff `miffId` : `uid`.
    case person(miffId: String, uid: String)
    /// Personne qu'on puisse nommer (une ligne dont la clé ne dit pas qui) : le logo.
    case nobody

    var description: String {
        "CenterSubject(redacted)"
    }
}

/// Ce que l'app sait d'un membre d'une Miff, tiré de la mémoire (jamais du centre).
struct ActorFace: Equatable, CustomStringConvertible {
    let firstName: String
    let initials: String
    let photo: Data?

    var description: String {
        "ActorFace(redacted)"
    }
}

/// La réponse de la mémoire au sujet d'un membre.
enum FaceLookup: Equatable, CustomStringConvertible {
    /// La fiche du membre est connue.
    case known(ActorFace)
    /// Le membre n'est pas dans la Miff (parti, retiré, compte supprimé, accès perdu) : le logo et « Un proche ».
    case gone
    /// Une lecture est en cours ou n'a pas commencé : le rond neutre.
    case pending
    /// La lecture a échoué (hors ligne, trop lent) : introuvable pour l'instant ; réessayée plus tard, jamais en boucle.
    case failed

    var description: String {
        "FaceLookup(redacted)"
    }
}

/// Ce qui est dessiné et écrit pour une ligne : l'image, et le prénom à écrire (« Un proche » quand il est nil).
struct CenterLook: Equatable, CustomStringConvertible {
    let avatar: CenterAvatar
    /// Le prénom du proche, lu MAINTENANT sur la même fiche que l'image ; nil dit « Un proche ».
    let name: String?

    var description: String {
        "CenterLook(redacted)"
    }
}

enum CenterAvatars {
    /// La photo d'une fiche, ou ses initiales : une photo vide, ou plus lourde que ce que les règles autorisent, n'est jamais utilisée.
    static func avatar(photo: Data?, initials: String) -> CenterAvatar {
        guard let photo, !photo.isEmpty, photo.count <= ProfileDocument.maxAvatarBytes else {
            return .initials(initials)
        }
        return .photo(photo, initials: initials)
    }

    /// L'image et le prénom d'une ligne qui parle de `subject`. `lookup` répond au sujet d'un membre d'une Miff ; `me` est le profil de la personne
    /// connectée (nil tant qu'il n'est pas lu). Le prénom et l'image viennent de la MÊME fiche : un profil modifié entre-temps change les deux ensemble.
    static func look(
        subject: CenterSubject,
        lookup: (_ miffId: String, _ uid: String) -> FaceLookup,
        me: ActorFace?
    ) -> CenterLook {
        switch subject {
        case .wholeMiff, .nobody:
            return CenterLook(avatar: .logo, name: nil)
        case .myself:
            guard let me else {
                return CenterLook(avatar: .placeholder, name: nil)
            }
            return CenterLook(avatar: avatar(photo: me.photo, initials: me.initials), name: nil)
        case .person(let miffId, let uid):
            switch lookup(miffId, uid) {
            case .known(let face):
                return CenterLook(
                    avatar: avatar(photo: face.photo, initials: face.initials),
                    name: face.firstName.isEmpty ? nil : face.firstName
                )
            case .gone, .failed:
                return CenterLook(avatar: .logo, name: nil)
            case .pending:
                return CenterLook(avatar: .placeholder, name: nil)
            }
        }
    }

    /// De qui parle une ligne du centre, d'après son type et sa clé. N8 (le demandeur) et C1 (le proche cité) le disent dans leur clé.
    static func subject(of item: NotificationItem) -> CenterSubject {
        switch item.type {
        case .n1, .n2, .n6, .n7:
            return .myself
        case .n3, .n4:
            return .wholeMiff
        case .n9:
            return person(miffId: item.miffId, uid: CircleNewsKeys.actorOf(item.id))
        case .n10, .n11:
            return person(miffId: item.miffId, uid: CircleAlertKeys.actorOf(item.id))
        case .n8:
            return person(miffId: item.miffId, uid: PlaceRequestLines.requester(of: item))
        case .c1:
            return person(miffId: item.miffId, uid: LastVisit.uid(of: item))
        }
    }

    /// Une personne d'une Miff, ou personne quand la ligne ne le dit pas.
    static func person(miffId: String, uid: String?) -> CenterSubject {
        guard let uid, !miffId.isEmpty else {
            return .nobody
        }
        return .person(miffId: miffId, uid: uid)
    }
}
