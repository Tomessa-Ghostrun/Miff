import Foundation

// CONFIDENTIALITÉ, pour ce fichier : la position qui accompagne une photo (décision du PM du 2026-11-06 : « une photo doit être attachée à
// une position, forcément »). Une coordonnée n'est ici qu'en mémoire, ne montre rien dans `description` et part vers Firestore
// seulement dans le document de la photo (`lat`, `lng` et `measuredAt`, TOUJOURS les trois ensemble : backend/firestore.rules,
// `validPhotoLocation`). Jamais dans un journal, un événement, un rapport de plantage, le presse-papiers ni la photo elle-même.

/// Le lieu d'une photo : 5 décimales (la précision que le modèle de données demande) et l'heure de la MESURE.
struct PhotoPlace: Equatable, CustomStringConvertible {
    let lat: Double
    let lng: Double
    let measuredAtMillis: Int

    var description: String {
        "PhotoPlace(redacted)"
    }

    /// Le lieu d'une photo d'après une mesure du téléphone, ou nil quand elle est inutilisable : pas un nombre ou hors des plages (les
    /// règles refuseraient toute la photo), ou plus vieille que l'âge maximal d'une position (10 minutes : une vieille position est un
    /// endroit que la personne a quitté). Une mesure dans le futur (horloge fausse) est plafonnée à `nowMillis`. Les règles acceptent 24 h ;
    /// le client est plus strict exprès (« mesure récente »).
    static func from(_ fix: Fix?, nowMillis: Int) -> PhotoPlace? {
        guard let fix, fix.lat.isFinite, fix.lng.isFinite,
              (-90.0...90.0).contains(fix.lat), (-180.0...180.0).contains(fix.lng) else {
            return nil
        }
        let measuredAt = min(fix.measuredAtMillis, nowMillis)
        guard nowMillis - measuredAt <= TrackingLimits.fixMaxAgeMillis else {
            return nil
        }
        return PhotoPlace(
            lat: TrackingGeo.round5(fix.lat),
            lng: TrackingGeo.round5(fix.lng),
            measuredAtMillis: measuredAt
        )
    }
}
