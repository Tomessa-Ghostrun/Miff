import Foundation

/// Pourquoi une action sur une Miff n'a pas fonctionné, réduit à ce que l'écran peut dire. Il ne porte ni message, ni code,
/// ni document : les messages de Firestore peuvent citer un chemin ou une valeur (ticket Miff, critère V2).
enum MiffFailure: Equatable {
    /// Le serveur n'a pas pu être joint (pas de réseau, trop lent), ou a répondu « quota épuisé » : la MÊME phrase calme,
    /// jamais le mot « quota » (critère H4).
    case offline

    /// Le serveur a répondu « non » à une écriture ou une lecture. DÉCISION (H4) : un refus d'App Check ne se distingue pas
    /// d'un refus des règles (les deux sont `permissionDenied`), donc il reçoit la phrase générique « Ça n'a pas
    /// fonctionné. Réessaie dans un instant. » et pas « pas de connexion », qui serait fausse.
    case refused

    /// Le profil manque (ou n'a pas de numéro) : la personne retourne à l'étape profil, jamais un message sur le code.
    case noProfile

    /// Tout le reste.
    case unknown

    /// Une écriture dont la réponse s'est perdue a pu partir quand même : le serveur est interrogé avant de dire qu'elle a
    /// échoué (un appui = une Miff, critère H3).
    var mayHaveLanded: Bool {
        self == .offline || self == .unknown
    }
}

/// `users/{uid}` manque, ou n'a pas de numéro valide : la fiche d'un membre ne peut pas être écrite.
struct MissingProfileError: Error, Equatable {
    /// Domaine de l'erreur posée dans la transaction (voir `FirestoreMiffStore`), pour la reconnaître à la sortie.
    static let domain = "com.tomessa.miff.missing-profile"
}

/// Une écriture refusée AVANT d'être envoyée (nom ou code invalide) : rien n'a été écrit.
struct InvalidWriteError: Error, Equatable {
    static let domain = "com.tomessa.miff.invalid-write"
}

/// Lit la réponse de Firestore comme une `MiffFailure` (n'expose jamais l'erreur).
func interpretMiffError(_ error: Error) -> MiffFailure {
    if error is MissingProfileError {
        return .noProfile
    }
    if error is OperationTimedOut {
        return .offline
    }
    let nsError = error as NSError
    if nsError.domain == NSURLErrorDomain {
        return .offline
    }
    guard nsError.domain == FirestoreErrorNumber.domain else {
        return .unknown
    }
    switch nsError.code {
    case FirestoreErrorNumber.unavailable,
         FirestoreErrorNumber.deadlineExceeded,
         FirestoreErrorNumber.cancelled,
         // « Quota » n'est jamais dit à la personne : c'est la même phrase que « pas de connexion » (critère H4).
         FirestoreErrorNumber.resourceExhausted:
        return .offline
    case FirestoreErrorNumber.permissionDenied, FirestoreErrorNumber.unauthenticated:
        return .refused
    default:
        return .unknown
    }
}

/// La lecture ou l'écoute d'une Miff vient-elle de dire que l'accès est perdu (ticket §5.7) ? SEUL un refus des règles
/// compte, ou une Miff qui n'existe pas. Une erreur réseau n'est pas une perte d'accès : personne n'apprend qu'on l'a retiré
/// parce que le téléphone est en mode avion (critère D3).
func isAccessLostError(_ error: Error) -> Bool {
    let nsError = error as NSError
    return nsError.domain == FirestoreErrorNumber.domain && nsError.code == FirestoreErrorNumber.permissionDenied
}
