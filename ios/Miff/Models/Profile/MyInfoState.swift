import Foundation

// SÉCURITÉ, pour ce fichier : prénom, nom, numéro de téléphone et photo sont des données personnelles. Ils vivent dans l'état de l'écran
// pour la durée de la session seulement : jamais sur le disque, jamais journalisés, jamais dans un événement Analytics.

/// Ce que dit l'écran après un enregistrement, ou pour une photo (ticket `docs/tickets/modifier-infos-perso.md` §4). Une phrase calme
/// chacun, jamais un mot technique.
enum MyInfoMessage: Equatable {
    /// Le serveur a confirmé : « C'est enregistré. »
    case saved
    /// Le profil est enregistré mais la fiche d'une Miff n'a pas pu être atteinte : « Enregistrer » de nouveau finit le travail.
    case syncPending
    /// Pas de réseau : rien n'a été écrit, les champs sont gardés.
    case network
    /// Le serveur n'a pas pris l'écriture : les champs sont gardés, réessayer. Aussi la réponse à un ancien compte sans `termsVersion`
    /// (les règles refusent toute mise à jour) : une phrase neutre, jamais un texte technique.
    case saveFailed
    /// Plusieurs refus de suite : conseil bienveillant de revenir plus tard.
    case tryLater
    /// Le fichier choisi n'est pas une image utilisable.
    case photoUnusable
}

/// Tout ce que montre « Mes informations » une fois le profil lu (mêmes valeurs que `MyInfoUiState` d'Android).
struct MyInfoUiState {
    var firstName: String
    var lastName: String
    /// La dernière frappe aurait dépassé 50 unités : « 50 caractères maximum. »
    var firstNameLimitHit = false
    var lastNameLimitHit = false
    /// La personne a vidé le champ : « Écris au moins un caractère. » (jamais avant qu'elle y ait touché).
    var firstNameBlank = false
    var lastNameBlank = false
    var avatar: Data?
    var crop: CropState?
    var regionCode: String
    var phoneText: String
    /// Montré quand la personne quitte le champ (ou touche « Enregistrer ») avec un numéro qui ne semble pas correct.
    var phoneInvalid = false
    /// Un numéro valide d'un genre refusé (surtaxé, gratuit...) : une phrase plus précise.
    var phoneTypeRefused = false
    var isSaving = false
    var message: MyInfoMessage?
    /// Quelque chose diffère de ce que détient le serveur (ou une fiche de Miff reste à écrire).
    var hasChanges = false
    /// Quelque chose que la personne a saisi ou choisi n'est pas enregistré : quitter l'écran demande confirmation.
    var hasEdits = false
    var namesComplete = false

    /// « Enregistrer » reste gris tant que rien n'a changé ET que chaque valeur n'est pas utilisable (critère MI5) : noms de 1 à 50, un
    /// chiffre au moins dans le numéro, rien d'en cours.
    var canSave: Bool {
        hasChanges && namesComplete && PhoneInput.hasDigit(phoneText) && !phoneInvalid && !isSaving
    }

    /// Le compteur « 45/50 » n'apparaît qu'à partir de 45 unités (critère MI2), avec le nombre réel.
    static func counter(for text: String) -> String? {
        let length = text.utf16.count
        return ProfileForm.showCounter(length: length) ? "\(length)/\(ProfileForm.maxNameLength)" : nil
    }
}
