import Foundation

// CONFIDENTIALITÉ, pour ce fichier : prénoms, noms de lieux, code de la Miff, où est quelqu'un et comment va son téléphone.
// Chaque type qui en porte un a une `description` qui ne montre rien (une ligne de journal, un paramètre Analytics ou un
// rapport de plantage ne peut jamais les laisser fuiter). Le Feed ne journalise rien et n'envoie aucun événement Analytics
// (ticket FEED, D16).
//
// Ces types sont TOUTE l'image que dessinent les écrans, calculée par `FeedBuilder` à partir de ce que disent les écoutes et
// l'horloge. Ils portent des faits (une sorte de lieu, une heure, un niveau), jamais une phrase : les phrases sont les clés
// `feed_*` (français et anglais, mêmes clés qu'Android).

/// Les cinq catégories dont le NOM est remplacé par la catégorie dans les phrases (« À la maison ») : Maison, Travail, École, Sport,
/// Chez un proche (décision du PM, 2026-11-08). Loisir, Autre et un lieu sans catégorie se disent par leur NOM (`PlaceRef.named`).
enum PlaceKind: Equatable {
    case home
    case work
    case school
    case sport
    case relative
}

/// Où dit-on qu'est quelqu'un, d'après la liste des lieux de CETTE Miff (la seule source).
enum PlaceRef: Equatable, CustomStringConvertible {
    case category(PlaceKind)
    case named(String)
    /// Le lieu a été supprimé, ou la liste ne le contient pas : une formule générique, jamais une supposition.
    case unknown

    var description: String {
        "PlaceRef(redacted)"
    }
}

/// L'étiquette d'une carte : l'état d'une personne en peu de mots (la couleur n'est jamais seule).
enum StateLabel: Equatable {
    case at(PlaceRef)
    case left(PlaceRef)
    /// Dans une zone de danger (le mot du PM, jamais « zone à risque »).
    case danger
    case noNews
    /// La personne a arrêté de partager (calme, rien d'anormal).
    case sharingStopped
    /// J'ai arrêté de partager : les nouvelles des autres ne sont pas lues.
    case newsHidden
    /// Ma carte pendant que le renouvellement de mon accord attend : rien n'est partagé avec cette Miff.
    case waitingAgreement
}

/// La ligne sous l'étiquette. UN seul repère de temps par carte.
enum TimeLine: Equatable {
    case nothing
    /// « Depuis 12 min », « Depuis 3 h », « Depuis hier à 08 h 12 », « Depuis le 3 nov. à 08 h 12 ».
    case since(Age)
    /// « Départ il y a 12 min » : vert, une zone de confiance quittée il y a moins de 2 h (au-delà : orange, puis rouge après 4 h).
    case departedAgo(Age)
    /// Orange : « Dernière nouvelle à 16 h 02 » (une heure ABSOLUE).
    case latestNews(Moment)
    /// Rouge d'ancienneté : « Pas de nouvelles depuis 16 h 02 ».
    case noNewsSince(Moment)
    /// Rouge, zone de danger : « Depuis 16 h 02 ».
    case inDangerSince(Moment)
}

/// La pastille de batterie : « 85 % » seul, jamais l'heure de la mesure (décision du PM, 2026-11-07, D44). Masquée au-delà de 6 h
/// (`FeedBuilder.batteryMaxAgeMillis`) ou quand elle est absente.
struct Battery: Equatable {
    let pct: Int
}

/// Une carte : un membre, moi compris. MA carte est la DERNIÈRE de la liste (décision D43) et n'a ni Localiser, ni Message, ni Appeler.
struct FeedMemberCard: Equatable, CustomStringConvertible {
    let member: MiffMember
    let isMe: Bool
    let level: StatusLevel
    let label: StateLabel
    let time: TimeLine
    let battery: Battery?
    /// « Localisation limitée » : un badge doux, sans effet sur la bulle.
    let limitedLocation: Bool
    /// Le numéro de la carte (E.164), seulement pour les AUTRES membres : « Écrire à » et « Appeler » n'existent qu'avec lui.
    let phone: String?
    /// « Localiser » : un autre membre qui partage, pendant que je partage aussi et que les statuts sont vraiment lus.
    let canLocate: Bool
    /// FEED-8 : la photo de la dernière update, à lire quand la carte vient à l'écran (`none` : rien n'est dessiné, ni trou ni message).
    var photo: PhotoSlot = .none

    var description: String {
        "FeedMemberCard(redacted)"
    }
}

/// Ce que dit une alerte.
enum AlertKind: Equatable, CustomStringConvertible {
    /// Orange : « {Prénom} a quitté {lieu} à 16 h 40 ».
    case left(place: PlaceRef, at: Moment)
    /// Rouge, sans nouvelles : « Pas de nouvelles de {Prénom} depuis 16 h 02 ».
    case noNewsSince(Moment)
    /// Rouge, zone de danger : « {Prénom} est à {lieu} » pour les proches, « Tu es dans une zone de danger de ta Miff » pour la personne.
    case danger(place: PlaceRef, since: Moment)

    var description: String {
        "AlertKind(redacted)"
    }
}

/// Une page du carrousel d'alertes : un membre orange ou rouge.
struct AlertPage: Equatable, CustomStringConvertible {
    let member: MiffMember
    let isMe: Bool
    let level: StatusLevel
    let kind: AlertKind
    /// « Envoyer un message » : un autre membre qui a un numéro (jamais sur ma propre page).
    let canMessage: Bool
    /// Hors connexion : l'heure de la dernière information, dite sous l'alerte (« reconnecte-toi pour les mettre à jour »).
    let offlineSince: Moment?

    var description: String {
        "AlertPage(redacted)"
    }
}

/// La bulle de la Miff : UNE à la fois, de la couleur de la Miff. Elle informe, elle ne dit jamais « en sécurité ».
enum Bubble: Equatable, CustomStringConvertible {
    /// Tous les membres connus sont verts : « Dernière nouvelle il y a 2 min » et, si certains n'ont rien dit, « 3 sur 4 ont donné des nouvelles ».
    case allGood(latestNews: Age?, answered: Int?, total: Int?)
    /// Aucun autre membre n'a de statut connu : « Pas encore de nouvelles », jamais une phrase de sécurité.
    case noNews
    case alone
    /// J'ai arrêté de partager.
    case sharingStopped
    /// Mon accord au nouveau texte est attendu.
    case awaitingAgreement
    /// Orange ou rouge : une page par membre concerné, les rouges d'abord (jamais de défilement automatique).
    case alerts(pages: [AlertPage], level: StatusLevel)

    var level: StatusLevel {
        switch self {
        case .allGood:
            return .green
        case .alerts(_, let level):
            return level
        case .noNews, .alone, .sharingStopped, .awaitingAgreement:
            return .neutral
        }
    }

    var description: String {
        "Bubble(redacted)"
    }
}

/// Une mention au-dessus de la bulle.
enum FeedBanner: Equatable {
    /// Pas de réseau : « Dernières informations de 16 h 02 » (`latest` nil quand rien n'est connu : « Pas de connexion… » générique).
    case offline(latest: Moment?)
    /// Mon statut n'est pas encore publié (les règles refusent la lecture des autres tant qu'il ne l'est pas).
    case publishing
}

/// Ce que le Feed montre pour la Miff active une fois lue.
struct FeedContent: Equatable, CustomStringConvertible {
    let miffId: String
    let miffName: String
    /// Le code à partager (« Ajouter un proche ») : un secret, donné seulement à la feuille de partage du système.
    let joinCode: String
    /// 15 membres ou plus : « Cette Miff est bien remplie ! » remplace le bouton (plafond doux).
    let isFull: Bool
    /// MA couleur : l'anneau de l'avatar de l'en-tête.
    let myLevel: StatusLevel
    let myLabel: StateLabel
    /// La couleur de toute la Miff (ticket §5.2) ; la bulle porte la sienne.
    let miffLevel: StatusLevel
    let bubble: Bubble
    let banners: [FeedBanner]
    let cards: [FeedMemberCard]
    /// Ne change que quand un NIVEAU ou l'ensemble des alertes change : le lecteur d'écran annonce la bulle alors, jamais à chaque minute.
    let announceKey: String
    /// MON statut tel que l'Update en a besoin (couleur du bouton central, bulle, nettoyage de ma photo) : nil quand je ne partage pas ou
    /// que mon accord est attendu. L'Update ne lit rien de plus : ce Feed est l'UNIQUE écouteur de ma fiche (D19).
    var myFacts: MyStatusFacts?

    /// Le Feed dit « Dernières informations de… » : pas de réseau, ou seule une copie du cache répond. Les photos ne sont pas lues alors.
    var isOffline: Bool {
        banners.contains { banner in
            if case .offline = banner {
                return true
            }
            return false
        }
    }

    var description: String {
        "FeedContent(redacted)"
    }
}
