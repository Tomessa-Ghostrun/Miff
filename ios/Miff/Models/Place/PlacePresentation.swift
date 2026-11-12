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

    /// « 2 lieux sur 2 avec la formule gratuite » : seulement pour un créateur dont la formule gratuite est CONNUE (`premium` faux
    /// ET connu : jamais d'après une lecture échouée ou un cache, voir `DirectoryReady.premiumKnown`).
    var showsFreeCounter: Bool {
        isCreator && premium == false
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

/// Les événements Analytics de l'offre : seulement « l'offre s'est ouverte / fermée », avec un déclencheur fixe et rien d'autre
/// (critère LIM5). Le contrat a un déclencheur par point d'entrée : le 3e lieu (`extra_trusted_place`), la 2e Miff (`extra_miff`) et la
/// zone de danger (`danger_zone`, ajouté par PAY-UI, ticket `paywall-v2.md` Q-PAY5).
enum PlacePaywall {
    static func trigger(for reason: PaywallReason) -> AnalyticsEvent.PaywallTrigger? {
        switch reason {
        case .limit:
            return .extraTrustedPlace
        case .extraMiff:
            return .extraMiff
        case .danger:
            return .dangerZone
        }
    }

    static func shown(for reason: PaywallReason) -> AnalyticsEvent? {
        trigger(for: reason).map { AnalyticsEvent.paywallShown(trigger: $0) }
    }

    static func dismissed(for reason: PaywallReason) -> AnalyticsEvent? {
        trigger(for: reason).map { AnalyticsEvent.paywallDismissed(trigger: $0) }
    }
}

/// Compte l'offre une fois par apparition : « ouverte » à la première apparition, « fermée » quand l'écran disparaît (croix, « Plus
/// tard » ou geste retour), jamais deux fois, et jamais « fermée » sans avoir été « ouverte ».
final class PlacePaywallTracking {
    private let reason: PaywallReason
    private var opened = false
    private var closed = false

    init(reason: PaywallReason) {
        self.reason = reason
    }

    func appeared() -> AnalyticsEvent? {
        guard !opened else {
            return nil
        }
        opened = true
        return PlacePaywall.shown(for: reason)
    }

    func disappeared() -> AnalyticsEvent? {
        guard opened, !closed else {
            return nil
        }
        closed = true
        return PlacePaywall.dismissed(for: reason)
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

/// Interrupteurs de fonctionnalité des lieux.
enum PlaceFeatures {
    /// L'encart « ce que ça change pour toi » du détail (« ...ta Miff voit ton statut... même quand l'app est fermée », « ...ton
    /// statut en rouge... ») décrit la DÉTECTION, qui n'existe pas encore (LOC-2 et LOC-4) : il reste caché pour ne rien promettre
    /// d'inexistant (même décision qu'Android, validée par le PM le 2026-11-04). Les textes sont gardés dans le catalogue.
    static let explainsWhatItChanges = false
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
