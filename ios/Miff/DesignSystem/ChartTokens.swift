import SwiftUI

// Palette « graphique » de la carte « Répartition de ton temps » (ticket « Profil, répartition du temps » §15.1, écart E5). PROPOSITION de
// TIME-4, à valider par `design-system` (TIME-0 n'était pas livré : aucune palette dessinée) : dérivée du bleu de la marque
// (`action/primary` #043CD1 et teintes) et du jaune-vert de la marque ASSOMBRI pour tenir 3:1 sur le fond blanc de la carte ; AUCUNE teinte
// rouge, orange ou jaune-vert de statut, sauf la zone de danger (seule exception, légèrement assombrie pour tenir 3:1, avec hachures et
// icône : la couleur n'est jamais seule). Contrastes sur #FFFFFF, vérifiés par `ChartTokensTests` (>= 3:1).
// Noms de code : `chart/1`... du ticket, en lowerCamelCase comme les autres tokens (colorChart1...).

/// `chart/1` : bleu de la marque (`action/primary`).
let colorChart1 = ColorToken(hex: 0x043CD1)
/// `chart/2` : jaune-vert de la marque assombri (le jaune-vert d'origine n'atteint pas 3:1 sur blanc).
let colorChart2 = ColorToken(hex: 0x828619)
/// `chart/3` : indigo, un bleu de la marque tiré vers le violet.
let colorChart3 = ColorToken(hex: 0x6A4FD6)
/// `chart/4` : bleu-cyan.
let colorChart4 = ColorToken(hex: 0x1F8FB8)
/// `chart/5` : bleu nuit.
let colorChart5 = ColorToken(hex: 0x14246B)
/// `chart/reserve` : couleur de réserve (une 6e catégorie plus tard, sans refonte), bleu clair de la marque.
let colorChartReserve = ColorToken(hex: 0x6C8FEA)
/// `chart/other` : « {n} autres lieux », bleu-gris.
let colorChartOthers = ColorToken(hex: 0x7A86B8)
/// `chart/elsewhere` : « Ailleurs », gris neutre.
let colorChartElsewhere = ColorToken(hex: 0x8A8989)
/// `chart/danger` : « Zone de danger », la couleur d'alerte douce (`status/danger` #FF5650 un peu assombrie : 4:1 au lieu de 3,1:1).
/// Toujours accompagnée de hachures, de l'icône d'avertissement et des mots ; jamais utilisée pour du texte.
let colorChartDanger = ColorToken(hex: 0xE8403A)
/// Le fond de l'anneau vide (état « pas assez de données ») : un gris très clair, décoratif.
let colorChartEmpty = ColorToken(hex: 0xEFEFEF)

enum ChartTokens {
    /// Les cinq couleurs des lieux de confiance nommés, dans l'ordre de la légende.
    static let places: [ColorToken] = [colorChart1, colorChart2, colorChart3, colorChart4, colorChart5]

    /// Les couleurs qui doivent tenir 3:1 sur la carte blanche (tranches et pastilles de la légende).
    static let all: [NamedToken<ColorToken>] = [
        NamedToken("colorChart1", colorChart1),
        NamedToken("colorChart2", colorChart2),
        NamedToken("colorChart3", colorChart3),
        NamedToken("colorChart4", colorChart4),
        NamedToken("colorChart5", colorChart5),
        NamedToken("colorChartReserve", colorChartReserve),
        NamedToken("colorChartOthers", colorChartOthers),
        NamedToken("colorChartElsewhere", colorChartElsewhere),
        NamedToken("colorChartDanger", colorChartDanger)
    ]
}
