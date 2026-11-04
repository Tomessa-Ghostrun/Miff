import Foundation

// SÉCURITÉ, pour ce fichier : mêmes règles que `PlaceModels.swift` (aucun nom, adresse ni coordonnée dans un journal ou un
// événement). Ce sont de petites règles pures que les écrans des lieux utilisent, pour qu'elles soient testées sans écran.

/// Ce dont la liste des lieux d'UNE Miff a besoin pour se dessiner.
///
/// `premium` est nil tant qu'il n'est pas connu : rien n'est alors affirmé sur la formule gratuite (ni compteur, ni pastille
/// « Miff+ »). `notice` est le message calme qui suit un geste (lieu enregistré : maquette 04L ; lieu qui n'existe plus).
struct PlaceListUi: Equatable, CustomStringConvertible {
    let places: [Place]
    let isCreator: Bool
    var premium: Bool?
    var notice: PlaceNotice?

    /// Combien de lieux différents la liste montre (un lieu n'apparaît qu'une fois dans la liste d'une Miff).
    var distinctCount: Int {
        Set(places.map { $0.id }).count
    }

    /// Un créateur en formule gratuite qui a déjà 2 lieux : « Ajouter un lieu » ouvre l'écran de la limite plutôt que le
    /// formulaire (critère LIM1).
    var atFreeLimit: Bool {
        isCreator && premium == false && PlaceQuota.isAtFreeLimit(premium: false, distinctPlaces: distinctCount)
    }

    var description: String {
        "PlaceListUi(redacted)"
    }
}

/// Les événements Analytics des lieux : seulement « l'offre s'est ouverte / fermée », avec un déclencheur fixe et rien d'autre
/// (critère LIM5). Le contrat n'a un déclencheur que pour le 3e lieu ; il n'en existe pas encore pour la zone de danger (point
/// ouvert de la PR : rien n'est envoyé dans ce cas).
enum PlacePaywall {
    static func shown(for reason: PaywallReason) -> AnalyticsEvent? {
        reason == .limit ? .paywallShown(trigger: .extraTrustedPlace) : nil
    }

    static func dismissed(for reason: PaywallReason) -> AnalyticsEvent? {
        reason == .limit ? .paywallDismissed(trigger: .extraTrustedPlace) : nil
    }
}

/// L'adresse qui ouvre l'application Plans sur le LIEU (ses coordonnées, jamais la position d'une personne) : 5 décimales et un
/// point, quelle que soit la langue du téléphone (une virgule ferait une adresse invalide en français). `maps:` n'ouvre que
/// l'app Plans, jamais une page web.
enum PlaceMapLink {
    static func url(lat: Double, lng: Double) -> URL? {
        guard PlaceCoordinates.isValid(lat: lat, lng: lng) else {
            return nil
        }
        let point = String(format: "%.5f,%.5f", locale: Locale(identifier: "en_US_POSIX"), lat, lng)
        return URL(string: "maps://?ll=\(point)&q=\(point)")
    }
}

/// Ce que la carte montre : assez large pour que le cercle entier tienne, jamais plus serré que 400 m.
enum PlaceMapLayout {
    /// La largeur (et la hauteur) de la zone montrée, en mètres, pour un cercle de `radiusM`.
    static func visibleMeters(radiusM: Int) -> Double {
        max(Double(radiusM) * 2 * 2.4, 400)
    }
}

/// L'adresse d'un lieu sur deux lignes, comme la maquette 16 : la rue et son numéro, puis le code postal, la ville et le pays.
/// L'adresse est enregistrée entière (« 26 rue du Volnay, 21000 Dijon, France ») : la première virgule sépare les deux lignes.
enum PlaceAddressLines {
    static func split(_ address: String) -> (title: String, subtitle: String) {
        guard let comma = address.firstIndex(of: ",") else {
            return (address, "")
        }
        let title = address[..<comma].trimmingCharacters(in: .whitespacesAndNewlines)
        let rest = address[address.index(after: comma)...].trimmingCharacters(in: .whitespacesAndNewlines)
        return title.isEmpty ? (address, "") : (title, rest)
    }
}
