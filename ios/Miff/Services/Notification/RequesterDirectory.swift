import Foundation

// CONFIDENTIALITÉ, pour ce fichier (ticket LIEU-REQ, §7.1) : le centre de notifications est local et ne garde AUCUN nom de personne. Les
// mots de ses lignes au sujet d'une demande (« Léa propose un lieu pour « Famille » ») se font quand la ligne est DESSINÉE, avec les
// prénoms gardés ICI, en mémoire seulement, pour la session : jamais dans un fichier, un journal ou un événement, et effacés à la
// déconnexion (cet objet fait partie de la fin de session des notifications).

/// Les prénoms des personnes qui ont demandé un lieu, par Miff et par personne : de quoi écrire les lignes du centre.
@MainActor
final class RequesterDirectory: ObservableObject, CustomStringConvertible {
    @Published private(set) var firstNames: [String: String] = [:]

    nonisolated var description: String {
        "RequesterDirectory(redacted)"
    }

    /// Le prénom d'une personne dans une Miff ; nil tant qu'il n'a pas été lu (la ligne le dit alors sans prénom).
    func firstName(miffId: String, uid: String) -> String? {
        firstNames[RequesterKey.key(miffId: miffId, uid: uid)]
    }

    func put(miffId: String, uid: String, firstName: String) {
        guard !firstName.isEmpty else {
            return
        }
        let key = RequesterKey.key(miffId: miffId, uid: uid)
        if firstNames[key] != firstName {
            firstNames[key] = firstName
        }
    }

    /// Fin de session : plus aucun prénom.
    func stop() {
        firstNames = [:]
    }
}
