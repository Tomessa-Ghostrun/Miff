import SwiftUI

// Couleurs propres au Feed (miroir de `FeedColor.kt` d'Android, design/screens/feed/) : les teintes FONCÉES du texte posé sur
// les teintes à 30 % des statuts (contraste AA, pas le #ff5650 du trait) et le gris des personnes dont le statut n'est pas
// connu. Jamais de valeur en dur dans un écran : tout passe par ces jetons.

let colorStatusSafeText = ColorToken(hex: 0x4D5200)
let colorStatusWarningText = ColorToken(hex: 0x7A4300)
let colorStatusDangerText = ColorToken(hex: 0xA30500)
/// Une personne dont le statut n'est pas connu est GRISE : jamais verte quand personne ne sait.
let colorStatusNeutral = ColorToken(hex: 0xC9C4BD)
let colorStatusNeutralTint = colorStatusNeutral.withAlpha(0.45)
