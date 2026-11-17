import Foundation

/// Le zoom de la carte du formulaire d'un lieu (ticket `docs/tickets/lieu-carte-zoom.md`) : des règles pures, sans MapKit, pour que les
/// bornes et les pas soient testés. Le « niveau » est la HAUTEUR de carte montrée, en mètres (la carte est plus large que haute) :
/// plus il est petit, plus on est près. Le centre ne bouge jamais (c'est le lieu) ; le rayon du cercle n'est jamais touché.
enum PlaceMapZoom {
    /// Le plus près : 100 m de haut (une rue et ses immeubles). Plus près, les tuiles d'Apple n'apportent plus rien.
    static let closestMeters = 100.0

    /// Le plus loin, en nombre de diamètres du cercle : le cercle garde au moins 1/11 de la hauteur de la carte, soit environ 16 pt
    /// sur 176 pt. Un cercle de 50 m ne devient donc jamais un point invisible, et un cercle de 800 m ne devient pas minuscule.
    static let farthestDiameters = 11.0

    /// Un double toucher rapproche d'un cran (la moitié), un toucher à deux doigts éloigne d'un cran (le double), comme Plans.
    static let tapFactor = 2.0

    /// Le plus loin permis pour un cercle de `radiusM` : jamais plus près que le cadrage de départ.
    static func farthestMeters(radiusM: Int) -> Double {
        max(PlaceMapLayout.visibleMeters(radiusM: radiusM), Double(radiusM) * 2 * farthestDiameters)
    }

    /// Ramène un niveau dans les bornes ; une valeur absurde (zéro, négative, infinie, pas un nombre) redonne le cadrage de départ.
    static func clamped(_ meters: Double, radiusM: Int) -> Double {
        guard meters.isFinite, meters > 0 else {
            return PlaceMapLayout.visibleMeters(radiusM: radiusM)
        }
        return min(max(meters, closestMeters), farthestMeters(radiusM: radiusM))
    }

    /// Le niveau pendant un pincement : écarter les doigts (échelle > 1) rapproche, les resserrer éloigne.
    static func pinched(from base: Double, scale: Double, radiusM: Int) -> Double {
        guard scale.isFinite, scale > 0 else {
            return clamped(base, radiusM: radiusM)
        }
        return clamped(base / scale, radiusM: radiusM)
    }

    static func doubleTapped(from current: Double, radiusM: Int) -> Double {
        clamped(current / tapFactor, radiusM: radiusM)
    }

    static func twoFingerTapped(from current: Double, radiusM: Int) -> Double {
        clamped(current * tapFactor, radiusM: radiusM)
    }

    /// Le niveau après un changement de rayon (le curseur) : sans zoom de la personne, le cadrage suit le cercle comme avant ; sinon
    /// son niveau est gardé, ramené dans les nouvelles bornes.
    static func reframed(keeping current: Double?, radiusM: Int) -> Double {
        guard let current else {
            return PlaceMapLayout.visibleMeters(radiusM: radiusM)
        }
        return clamped(current, radiusM: radiusM)
    }

    /// La part de la hauteur de la carte que prend le diamètre du cercle à ce niveau (1 : le cercle touche les bords).
    static func circleShare(radiusM: Int, visibleMeters: Double) -> Double {
        guard visibleMeters > 0 else {
            return 0
        }
        return Double(radiusM) * 2 / visibleMeters
    }
}
