import Foundation

// La bordure dynamique de l'encart (ticket §2.4) : sa couleur et ses durées d'animation. La forme est dans `CountdownBorderShape`.

enum CountdownBorder {
    /// Épaisseur du tracé : celle de l'anneau d'avatar de statut.
    static let lineWidth: CGFloat = strokeAvatarRing
    /// Au-dessus de cette part (le dernier quart de la fenêtre = les 30 dernières minutes), la bordure est `status/safe` ; en dessous, elle
    /// glisse vers `status/warning`.
    static let warmingStartsBelow = 0.25
    /// Une frontière de minute : la bordure glisse vers sa nouvelle longueur. Aucune animation entre deux minutes.
    static let glideSeconds = 0.6
    /// Apparition et disparition de l'encart.
    static let fadeSeconds = 0.2

    /// La durée du glissement de la bordure à une frontière de minute ; nil = INSTANTANÉ (« Réduire les animations »).
    static func glideDuration(reduceMotion: Bool) -> Double? {
        reduceMotion ? nil : glideSeconds
    }

    /// La durée du fondu d'apparition et de disparition ; nil = apparition instantanée (« Réduire les animations »).
    static func fadeDuration(reduceMotion: Bool) -> Double? {
        reduceMotion ? nil : fadeSeconds
    }

    /// La part du tracé GARDÉE pour une fraction `f` de bordure restante : de `1 - f` à `1`. Le tracé part du haut au centre vers la droite ;
    /// garder la FIN fait donc disparaître d'abord le début (le quart haut-droit), puis le côté droit : la bordure se vide dans le sens
    /// horaire. (`trim(from: 0, to: f)` garderait le début et viderait la bordure à l'envers.) `f` est borné à 0...1.
    static func keptRange(fraction: Double) -> (from: CGFloat, to: CGFloat) {
        let kept = CGFloat(min(1, max(0, fraction)))
        return (from: 1 - kept, to: 1)
    }

    /// 0 = `status/safe`, 1 = `status/warning` : t = (0,25 − f) / 0,25, borné. Jamais au-delà du orange, donc jamais de rouge.
    static func warmth(fraction: Double) -> Double {
        min(1, max(0, (warmingStartsBelow - fraction) / warmingStartsBelow))
    }

    /// Le mélange de `status/safe` et `status/warning` à ce point (composantes sRGB, arrondies).
    static func color(fraction: Double) -> ColorToken {
        let mix = warmth(fraction: fraction)
        func channel(_ from: Int, _ to: Int) -> Int {
            Int((Double(from) + (Double(to) - Double(from)) * mix).rounded())
        }
        let red = channel(colorStatusSafe.red, colorStatusWarning.red)
        let green = channel(colorStatusSafe.green, colorStatusWarning.green)
        let blue = channel(colorStatusSafe.blue, colorStatusWarning.blue)
        return ColorToken(hex: UInt32(red << 16 | green << 8 | blue))
    }
}
