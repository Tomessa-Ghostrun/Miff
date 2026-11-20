#if ALERT_DANGER
import Foundation
import SwiftUI

// ALERTE « Je suis en danger » (docs/tickets/alerte-danger.md, section 17). AUCUNE couleur nouvelle : les fonds sont ceux du design system (mêmes
// valeurs que `ui/theme/AlertColor.kt` d'Android).
// - `colorAlertFill` : le rouge des maquettes (status/danger, #FF5650) : l'obturateur en mode alerte, le remplissage de la barre, le grand bouton « Appeler »
//   (gros texte gras : 3:1 est le rapport WCAG AA pour lui). Jamais pour du petit texte.
// - `colorAlertBanner` : le rouge foncé du texte d'erreur (#D60700) : la bannière porte du PETIT texte blanc, elle prend le rouge qui atteint 4,5:1 avec
//   le blanc (5,4:1) au lieu du rouge plus clair de la maquette (3,1:1). Un écart à la maquette 05, listé dans la PR.

let colorAlertFill = colorStatusDanger
let colorAlertBanner = colorTextError
/// Le blanc posé sur la bannière et sur le bouton rouge.
let colorOnAlert = colorTextOnAction
/// La progression de la fin d'une alerte sur la bannière : un voile clair sur le rouge, qui se remplit depuis le début.
let colorAlertBannerProgress = ColorToken(hex: 0xFFFFFF, alpha: 0.28)
/// Le voile sombre derrière les phrases posées sur la photo du viseur en mode alerte (le texte blanc y reste lisible sur une photo claire).
let colorAlertOnPhotoScrim = ColorToken(hex: 0x000000, alpha: 0.55)

// Le TITRE de la bannière d'une alerte en cours (« Mettre fin à l'alerte ? ») : DEUX FOIS la taille d'avant (textBodyBold16 : 16 pt) pour hiérarchiser le message
// (retour du PM en recette). L'interligne reste serré (40 pt, pas 44) pour que deux lignes ne gonflent pas la bannière. Dynamic Type : la courbe du style
// système « title » (plus douce que « body » : un titre de 32 pt qui suivrait « body » dépasserait 90 pt aux tailles d'accessibilité), et la bannière
// plafonne ce titre à `DangerBannerView.titleMaxDynamicType`. Style propre à l'alerte : il n'entre pas dans `TextStyleTokens.all`.
let textAlertBannerTitle = TextStyleToken(
    name: "textAlertBannerTitle",
    face: .hankenBold,
    size: textBodyBold16.size * 2,
    lineHeight: 40,
    dynamicTypeStyle: .title
)
#endif
