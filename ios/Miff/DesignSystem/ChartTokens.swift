import SwiftUI

// Palette de la carte « Répartition du temps » (ticket « Profil, répartition du temps » §15.1, retour du PM du 2026-11-17 après test sur Android).
// PROPOSITION de TIME-4, à valider par `design-system` (TIME-0 n'était pas livré) : les lieux de confiance sont des NUANCES DE VERT et de
// BLEU de Miff, en alternance (vert, bleu, vert, bleu, vert) ; le vert de la marque (#D1D828) est assombri parce qu'il n'atteint pas 3:1
// sur le blanc de la carte ; le bleu est celui des actions (`action/primary` #043CD1) et ses teintes. La zone de danger est EXACTEMENT le
// rouge de la bulle de statut danger (`status/danger`), avec hachures et icône : la couleur n'est jamais seule. « Ailleurs » est le gris
// de la maquette du PM (#D4D7DF relevé sur la capture), plus clair que 3:1 : il est toujours accompagné de son nom et de son pourcentage
// dans la légende et dans la description du lecteur d'écran. Contrastes sur #FFFFFF vérifiés par `TimeShareCardTests`.
// Noms de code : `chart/1`... du ticket, en lowerCamelCase comme les autres tokens (colorChart1...).

/// `chart/1` : vert de la marque assombri.
let colorChart1 = ColorToken(hex: 0x7E8316)
/// `chart/2` : bleu des actions (`action/primary`).
let colorChart2 = ColorToken(hex: 0x043CD1)
/// `chart/3` : vert de la marque, nuance plus claire.
let colorChart3 = ColorToken(hex: 0x8F951A)
/// `chart/4` : bleu clair.
let colorChart4 = ColorToken(hex: 0x4C78E8)
/// `chart/5` : vert de la marque, nuance foncée.
let colorChart5 = ColorToken(hex: 0x4F5A0A)
/// `chart/reserve` : couleur de réserve (une 6e catégorie plus tard, sans refonte), bleu nuit.
let colorChartReserve = ColorToken(hex: 0x14297F)
/// `chart/other` : « {n} autres lieux », bleu-gris.
let colorChartOthers = ColorToken(hex: 0x7A86B8)
/// `chart/elsewhere` : « Ailleurs », le gris de la maquette du PM.
let colorChartElsewhere = ColorToken(hex: 0xD4D7DF)
/// `chart/danger` : « Zone de danger » : le rouge de la bulle de statut danger (`status/danger`), jamais utilisé pour du texte.
let colorChartDanger = colorStatusDanger
/// Le vert de la pastille d'en-tête, de la coche et de l'encart de synthèse (teintes très légères du vert `chart/1`).
let colorChartGreenTint = colorChart1.withAlpha(0.12)
let colorChartGreenTintStrong = colorChart1.withAlpha(0.22)

enum ChartTokens {
    /// Les cinq couleurs des lieux de confiance nommés, dans l'ordre de la légende.
    static let places: [ColorToken] = [colorChart1, colorChart2, colorChart3, colorChart4, colorChart5]

    /// Les couleurs qui doivent tenir 3:1 sur la carte blanche (tranches et pastilles de la légende) : tout sauf « Ailleurs » (gris de la
    /// maquette, décision du PM).
    static let all: [NamedToken<ColorToken>] = [
        NamedToken("colorChart1", colorChart1),
        NamedToken("colorChart2", colorChart2),
        NamedToken("colorChart3", colorChart3),
        NamedToken("colorChart4", colorChart4),
        NamedToken("colorChart5", colorChart5),
        NamedToken("colorChartReserve", colorChartReserve),
        NamedToken("colorChartOthers", colorChartOthers),
        NamedToken("colorChartDanger", colorChartDanger)
    ]
}
