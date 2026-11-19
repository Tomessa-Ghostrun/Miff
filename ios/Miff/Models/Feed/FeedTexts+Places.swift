import Foundation

// Le complément de lieu des phrases du Feed et des notifications (« A quitté la maison », « Thomas a rejoint l'école à 16 h 02 »), dans un
// fichier à part. Rien n'est journalisé ; les noms de lieux reçus sont montrés, jamais gardés.

extension FeedTexts {
    /// Le sens dans lequel un lieu est cité : ce qui suit « a quitté » (`leave`) ou « a rejoint » (`arrive`).
    enum PlaceMovement {
        case leave
        case arrive
    }

    /// Le COMPLÉMENT DE LIEU d'une phrase, préposition et article compris, écrit en entier dans la langue (« la maison » après « A quitté » ; en
    /// espagnol « de casa » après « salió » et « a casa » après « llegó »). Une clé par catégorie ET par sens : aucun article ni aucune
    /// préposition n'est jamais collé à un nom de lieu par le code (accords et contractions « del », « al », « do », « na » impossibles à
    /// deviner). Un lieu nommé par la personne passe dans `feed_place_<sens>_named` (le nom, tel quel : jamais traduit ni décliné).
    func placeComplement(_ place: PlaceRef, movement: PlaceMovement) -> String {
        switch place {
        case .category(let kind):
            return text(categoryKey(kind, movement))
        case .named(let name):
            return text(movement == .leave ? "feed_place_leave_named" : "feed_place_arrive_named", name)
        case .unknown:
            return text(movement == .leave ? "feed_place_leave_generic" : "feed_place_arrive_generic")
        }
    }

    /// Les clés sont écrites en entier (et non bâties par morceaux) : on les retrouve en cherchant le texte, et un test vérifie qu'elles existent.
    private func categoryKey(_ kind: PlaceKind, _ movement: PlaceMovement) -> String {
        switch (movement, kind) {
        case (.leave, .home):
            return "feed_place_leave_home"
        case (.leave, .work):
            return "feed_place_leave_work"
        case (.leave, .school):
            return "feed_place_leave_school"
        case (.leave, .sport):
            return "feed_place_leave_sport"
        case (.leave, .relative):
            return "feed_place_leave_relative"
        case (.arrive, .home):
            return "feed_place_arrive_home"
        case (.arrive, .work):
            return "feed_place_arrive_work"
        case (.arrive, .school):
            return "feed_place_arrive_school"
        case (.arrive, .sport):
            return "feed_place_arrive_sport"
        case (.arrive, .relative):
            return "feed_place_arrive_relative"
        }
    }
}
