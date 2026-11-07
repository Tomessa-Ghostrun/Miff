import Foundation

// Les textes des catégories et des types de lieu, tels que les écrans les disent (clés du catalogue, partagées avec Android). Rien
// d'autre ici que des clés : le nom d'un lieu n'y passe jamais.

extension PlaceCategory {
    var labelKey: String {
        switch self {
        case .home:
            return "place_category_home"
        case .work:
            return "place_category_work"
        case .school:
            return "place_category_school"
        case .sport:
            return "place_category_sport"
        case .relative:
            return "place_category_relative"
        case .leisure:
            return "place_category_leisure"
        case .other:
            return "place_category_other"
        }
    }

    var label: String {
        L10n.string(labelKey)
    }
}

extension PlaceType {
    /// « Zone de confiance » / « Zone de danger » (jamais « zone à risque » : décision du PM).
    var label: String {
        L10n.string(self == .trusted ? "place_type_trusted" : "place_type_danger")
    }

    /// « Lieu sûr pour tes proches » / « Lieu à éviter ».
    var detail: String {
        L10n.string(self == .trusted ? "place_type_trusted_desc" : "place_type_danger_desc")
    }
}
