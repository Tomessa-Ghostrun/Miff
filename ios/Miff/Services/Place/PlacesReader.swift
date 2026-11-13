import Foundation

// CONFIDENTIALITÉ, pour ce fichier : les lieux (noms, adresses, coordonnées) ne sont jamais journalisés ni envoyés dans un événement ;
// `description` ne montre rien.

/// Ce qu'une lecture des lieux d'une Miff a donné.
enum PlacesReadResult: Equatable, CustomStringConvertible {
    case loaded([Place])
    /// Refusée, hors ligne sans copie, trop lente, ou toute autre cause : la liste le dit calmement et réessaiera plus tard.
    case failed

    var description: String {
        "PlacesReadResult(redacted)"
    }
}

/// La lecture unique derrière les lieux de l'onglet Profil : UN `get` de `miffs/{id}/places`, jamais un écouteur. Derrière un protocole
/// pour que `ProfilePlacesViewModel` soit testé avec un double. La vraie lecture est `FirestorePlacesReader`.
protocol PlacesReader {
    func read(miffId: String) async -> PlacesReadResult
}

extension Place {
    /// Les lieux dans l'ordre de l'écran de la Miff (par nom, sans tenir compte des majuscules) : un ordre stable, une liste qui se
    /// réordonne bouge sous un doigt. Le Feed, l'écran de la Miff et l'onglet Profil montrent la même suite.
    static func sortedForList(_ places: [Place]) -> [Place] {
        places.sorted { $0.name.lowercased() < $1.name.lowercased() }
    }
}
