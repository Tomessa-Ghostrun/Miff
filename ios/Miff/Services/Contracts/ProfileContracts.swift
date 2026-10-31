import Foundation

/// Comment s'est terminée l'écriture du profil. Ni code, ni message, ni valeur : rien ici ne peut fuiter un nom ou un
/// numéro.
enum ProfileWriteResult: Equatable {
    /// Le SERVEUR a accepté l'écriture.
    case done
    /// Le serveur n'a pas pu être joint (pas de réseau, trop lent). Rien n'a été écrit ; on peut réessayer.
    case offline
    /// Le serveur a répondu « non » (règles, App Check). Ce n'est pas un problème de réseau : ne pas dire « pas de
    /// connexion ».
    case refused
    case failed
}

/// Ce que contient un ancien profil, gardé pour pré-remplir les étapes quand il faut le recréer (rien d'autre n'est
/// conservé).
struct ProfileSeed: Equatable {
    let firstName: String
    let lastName: String
    let avatarJpeg: Data?
}

/// Les écritures de `users/{uid}` faites par les étapes de l'Onboarding (création du profil, numéro d'un ancien
/// compte), chacune CONFIRMÉE par le serveur avant que l'écran suivant s'ouvre (ticket §6.4).
///
/// Elles passent par une TRANSACTION Firestore exprès : une écriture simple faite sans réseau reste en file d'attente et
/// part plus tard toute seule, peut-être après que la personne a changé sa saisie ou s'est déconnectée ; une
/// transaction n'existe qu'en ligne et ne laisse rien derrière elle en cas d'échec, donc « pas de confirmation » veut
/// toujours dire « rien d'écrit » (sauf délai dépassé : voir `timedOperation`).
protocol UserProfileRepository {
    /// Création du profil en UNE écriture avec toutes les clés (voir `ProfileDocument`), numéro compris.
    func create(uid: String, profile: NewProfile, consent: PendingConsent) async -> ProfileWriteResult

    /// Compte créé avant ONB-B : le profil existe, seul le numéro est ajouté (ticket §6.2, ligne 2b).
    func addPhone(uid: String, phone: String) async -> ProfileWriteResult

    /// Compte créé avant AUTH-B (profil sans `termsVersion`) : les règles refusent toute mise à jour, le profil est donc
    /// supprimé puis recréé avec toutes les clés (backend/data-model.md §2.1).
    func delete(uid: String) async -> ProfileWriteResult

    /// Les noms et la photo du profil tel qu'il est maintenant, lus AVANT sa suppression ; nil s'il est illisible.
    func read(uid: String) async -> ProfileSeed?
}

/// L'étape atteinte dans la création du profil, relue dans le brouillon après une fermeture de l'app (ticket §6.2).
enum DraftStep: String, Equatable, Codable {
    case profile
    case phone
}

/// Ce que la personne a saisi jusqu'ici dans les étapes du profil, gardé SUR L'APPAREIL jusqu'à ce que le serveur ait
/// confirmé le profil (ticket Onboarding §6.2). Lié au compte. Ne contient JAMAIS de mot de passe, de jeton ni d'adresse
/// e-mail. Ce sont des données personnelles (noms, photo, numéro) : stockage privé de l'app seulement, exclu des
/// sauvegardes, effacé après la confirmation, à chaque déconnexion et dès qu'un autre compte le demande.
struct ProfileDraft: Equatable, Codable {
    let uid: String
    let step: DraftStep
    let firstName: String
    let lastName: String
    /// Le pays choisi dans le sélecteur (« FR ») ; nil = pas encore choisi, le pays de l'appareil s'applique.
    let regionCode: String?
    /// Le numéro tel que saisi (format national, espaces...), PAS la forme E.164.
    let phoneText: String
    /// Le JPEG recadré (20 000 octets au plus).
    let avatar: Data?
}

protocol ProfileDraftStore {
    /// Le brouillon de CE compte. Le brouillon d'un autre compte est effacé et ignoré (ticket §9, cas 33).
    func read(uid: String) -> ProfileDraft?
    func save(_ draft: ProfileDraft)
    func clear()
}
