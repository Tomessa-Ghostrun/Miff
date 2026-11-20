import Foundation

// CONFIDENTIALITÉ, pour ce fichier : le nom d'une Miff est une donnée personnelle ; l'état ne le décrit jamais (`description` est masquée).

/// La carte calme « Une précision sur ce que tu partages » (CONS-4-I, option D du PM) pour les Miffs où la personne partage sous le texte
/// de la version 3. RIEN n'est en pause pour elles : ni la position, ni le statut, ni la photo, quelle que soit la réponse.
struct ClarifyUiState: Equatable, CustomStringConvertible {
    let miffs: [RenewalMiff]
    /// Écriture en cours : le bouton le dit et rien d'autre ne peut être touché (un appui = une opération).
    var busy = false
    var message: ActionMessage?

    var description: String {
        "ClarifyUiState(redacted)"
    }
}
