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

    /// L'emoji de la catégorie (décision du PM, 2026-11-10 : des emoji comme sur iPhone, pas des pictogrammes dessinés). TABLE
    /// COMMUNE AVEC ANDROID (`PlaceCategory.emoji`, mêmes sept emoji, documentés dans `ios/README.md`) : la modifier d'un côté sans
    /// l'autre ferait voir deux catégories différentes pour un même lieu. Ce sont des caractères Unicode rendus comme du TEXTE par la
    /// police du système (aucune image, aucune ressource) ; ils ne portent aucun sens à eux seuls : le NOM de la catégorie les
    /// accompagne toujours et les écrans cachent l'emoji au lecteur d'écran.
    var emoji: String {
        switch self {
        case .home:
            return "🏠"
        case .work:
            return "💼"
        case .school:
            return "🏫"
        case .sport:
            return "⚽"
        case .relative:
            // Famille : 👨‍👩‍👧 (HOMME + ZWJ + FEMME + ZWJ + FILLE). 🏡 aurait été confondu avec 🏠 (Maison) à 20 pt.
            return "👨‍👩‍👧"
        case .leisure:
            return "🎉"
        case .other:
            return "📍"
        }
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
