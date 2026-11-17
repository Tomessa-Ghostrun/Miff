import Foundation

// NOTIF-P1I (ticket docs/tickets/notifications-p1-derniere-visite.md, « Depuis ta dernière visite », critères C1-T1 à C1-T20). Du Swift PUR,
// sans horloge ni disque ni serveur : miroir exact de `LastVisit.kt` d'Android (livré et validé par le PM le 2026-11-13), dont les vecteurs
// de test sont le contrat commun. Rien n'est lu ni écrit sur le serveur : la comparaison se fait entre deux images que le Feed détient déjà.
//
// CONFIDENTIALITÉ (critère C1-7) : une image porte, pour chaque AUTRE membre, un identifiant, le MOT de sa présence, la CATÉGORIE de son
// lieu (une sorte, jamais le nom ni l'adresse d'un lieu) et deux heures. AUCUNE position, AUCUN nom de lieu, AUCUN nom de personne, AUCUNE
// photo. Une ligne du centre porte les mêmes choses, dans sa clé. Chaque type cache son contenu dans `description`.

/// Ce que le Feed a montré d'UN autre membre de la Miff active, en faits. `muted` : on ne dit jamais rien de lui (voir `LastVisit.sample`).
struct VisitMember: Equatable, CustomStringConvertible {
    let uid: String
    let muted: Bool
    let presence: PresenceValue?
    /// La catégorie du lieu de sa présence (Maison, Travail, École, Sport, Chez un proche) ; nil pour Loisir, Autre, sans catégorie ou un
    /// lieu que la liste ne contient pas.
    let place: PlaceKind?
    let presenceAtMillis: Int?
    let lastUpdateAtMillis: Int?
    /// « Silence » : hors de toute zone de confiance et rouge (plus de 4 h sans nouvelles) quand l'image a été prise.
    let silent: Bool

    var description: String {
        "VisitMember(redacted)"
    }
}

/// L'image d'UNE Miff (les autres seulement : jamais moi).
struct VisitState: Equatable, CustomStringConvertible {
    let miffId: String
    let members: [VisitMember]

    var description: String {
        "VisitState(redacted)"
    }
}

/// Les quatre sortes de ligne (ticket §3.3). `rawValue` est la lettre gardée dans la clé de la ligne.
enum VisitKind: String, Equatable {
    case arrival = "a"
    case departure = "d"
    case update = "u"
    case silence = "s"
}

/// Une ligne « Depuis ta dernière visite », avant qu'elle devienne une ligne du centre.
struct VisitLine: Equatable, CustomStringConvertible {
    let uid: String
    let kind: VisitKind
    /// La catégorie du lieu pour une arrivée ou un départ ; nil dit « un lieu de confiance ».
    let place: PlaceKind?
    /// L'instant du CHANGEMENT (pour un silence : quand la personne est devenue rouge). Il ordonne les lignes.
    let atMillis: Int
    /// L'heure que dit la phrase (pour un silence : l'heure de sa dernière nouvelle).
    let shownAtMillis: Int

    var description: String {
        "VisitLine(redacted)"
    }
}

/// Comment une heure se dit dans une ligne (ticket §3.3 et décisions du 2026-11-13, point 2).
enum VisitDay: Equatable {
    case today
    case yesterday
    case weekday
    case date
}

/// Tout ce qu'il faut pour prendre l'image de la Miff que montre le Feed (une fonction à plus de 5 paramètres est refusée par SwiftLint).
struct VisitSampleInput {
    let miffId: String
    let members: [MiffMember]
    let places: [Place]
    let myUid: String
    let statuses: StatusesView
    /// Le renouvellement de mon accord est attendu : les nouvelles des autres sont masquées.
    let waitingAgreement: Bool
    /// Pas de réseau, ou un « Actualiser » que le serveur n'a pas répondu : ce qui est montré est daté.
    let offline: Bool
}

enum LastVisit {
    /// Revenir au premier plan après MOINS que cela ne compare pas (décision Q3 du PM) : un aller-retour rapide ne fait aucun bruit.
    static let minAbsenceMillis = 30 * 60 * 1000

    /// Au plus ce nombre de lignes (décision Q7).
    static let maxLines = 3

    /// Une arrivée écrit aussi la dernière update : une update à moins de cela de l'arrivée est le même événement, pas une seconde nouvelle.
    static let sameEventMillis = 5 * 60 * 1000

    /// Le serveur accepte une heure jusqu'à 5 minutes en avance sur sa propre horloge : un téléphone à l'heure fausse peut en voir une dans
    /// son futur. Au-delà, aucune ligne.
    static let clockToleranceMillis = 5 * 60 * 1000

    private static let priorityUpdate = 1
    private static let priorityMovement = 2
    private static let prioritySilence = 3

    /// Vrai quand l'app est restée assez longtemps loin pour une nouvelle comparaison (ticket §3.1, Q3). Une durée négative (horloge
    /// reculée) compte comme une réouverture : mieux vaut comparer que rester muet.
    static func isReopening(absenceMillis: Int) -> Bool {
        absenceMillis < 0 || absenceMillis >= minAbsenceMillis
    }

    // MARK: - L'image

    /// L'image de la Miff que montre le Feed, ou nil quand ce n'en est PAS une fraîche (critère C1-T14) : les statuts ne sont pas lus, ou
    /// viennent du cache du téléphone, ou le téléphone est hors connexion, ou mon partage est arrêté, ou mon accord attendu (les nouvelles
    /// des autres sont alors masquées). Une ligne n'est jamais bâtie sur une image périmée, et une image périmée ne devient jamais « ce qui
    /// a été vu ».
    static func sample(_ input: VisitSampleInput, nowMillis: Int) -> VisitState? {
        guard case .loaded(let byUid, let fromCache) = input.statuses, !fromCache, !input.offline,
              !input.waitingAgreement else {
            return nil
        }
        if input.members.first(where: { $0.uid == input.myUid })?.sharingEnabled == false {
            return nil
        }
        let placesById = Dictionary(input.places.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let others = input.members.filter { $0.uid != input.myUid }
        let members = others.map { other in
            visitMember(of: other, status: byUid[other.uid], places: placesById, nowMillis: nowMillis)
        }
        return VisitState(miffId: input.miffId, members: members)
    }

    private static func visitMember(
        of member: MiffMember,
        status: MemberStatus?,
        places: [String: Place],
        nowMillis: Int
    ) -> VisitMember {
        let presence = status?.presence
        // On ne dit rien de quelqu'un qui a arrêté de partager, qui est en voyage, ou dont l'état n'a rien de lisible (aucun statut, inconnu).
        let unreadable = status == nil || presence == nil || presence == .unknown || presence == .travel
        let muted = !member.sharingEnabled || unreadable
        var silent = false
        var kind: PlaceKind?
        if !muted, let status {
            let facts = MemberStatusFacts(
                sharingEnabled: member.sharingEnabled,
                presence: status.presence,
                presenceAtMillis: status.presenceAtMillis,
                lastUpdateAtMillis: status.lastUpdateAtMillis,
                travelUntilMillis: status.travelUntilMillis
            )
            silent = presence == .out && StatusColor.level(facts, nowMillis: nowMillis) == .red
            if let placeId = status.placeId, let category = places[placeId]?.category {
                kind = PlaceKind(category: category)
            }
        }
        return VisitMember(
            uid: member.uid,
            muted: muted,
            presence: presence,
            place: kind,
            presenceAtMillis: status?.presenceAtMillis,
            lastUpdateAtMillis: status?.lastUpdateAtMillis,
            silent: silent
        )
    }

    // MARK: - La comparaison

    /// `compare(vu, actuel, maintenant) -> lignes`. `seen` est l'image de la dernière visite : nil (première visite, reconnexion, accès
    /// perdu) ne donne aucune ligne, ni l'image d'une autre Miff (critère C1-T17). Au plus `maxLines`, le changement le plus récent d'abord.
    static func compare(seen: VisitState?, current: VisitState, nowMillis: Int) -> [VisitLine] {
        guard let seen, seen.miffId == current.miffId else {
            return []
        }
        let before = Dictionary(seen.members.map { ($0.uid, $0) }, uniquingKeysWith: { first, _ in first })
        let lines = current.members.compactMap { member -> VisitLine? in
            guard let earlier = before[member.uid] else {
                return nil
            }
            return line(seen: earlier, now: member, nowMillis: nowMillis)
        }
        let ordered = lines.sorted {
            $0.atMillis != $1.atMillis ? $0.atMillis > $1.atMillis : $0.uid < $1.uid
        }
        return Array(ordered.prefix(maxLines))
    }

    private struct Candidate {
        let atMillis: Int
        let priority: Int
        let kind: VisitKind
        let place: PlaceKind?
        let shownAtMillis: Int
    }

    private static func line(seen: VisitMember, now: VisitMember, nowMillis: Int) -> VisitLine? {
        // Personne qui a arrêté de partager, voyage ou est inconnu n'est évoqué, avant ou après (C1-T10) ; une zone de danger est l'affaire
        // du Feed et de N6 (C1-T11, Q6).
        if seen.muted || now.muted || now.presence == .inRisk {
            return nil
        }
        let moved = movement(seen: seen, now: now)
        let candidates = [moved, update(seen: seen, now: now, movement: moved), silence(seen: seen, now: now)]
            .compactMap { $0 }
        // Une heure dans le futur (téléphone à l'heure fausse) ne fait aucune ligne absurde : quelques minutes d'avance comptent pour
        // « maintenant », au-delà on ne dit rien.
        let usable = candidates
            .filter { $0.atMillis <= nowMillis + clockToleranceMillis }
            .map {
                Candidate(
                    atMillis: min($0.atMillis, nowMillis),
                    priority: $0.priority,
                    kind: $0.kind,
                    place: $0.place,
                    shownAtMillis: min($0.shownAtMillis, nowMillis)
                )
            }
        guard let best = usable.max(by: { ($0.atMillis, $0.priority) < ($1.atMillis, $1.priority) }) else {
            return nil
        }
        return VisitLine(
            uid: now.uid,
            kind: best.kind,
            place: best.place,
            atMillis: best.atMillis,
            shownAtMillis: best.shownAtMillis
        )
    }

    /// Une arrivée dans une zone de confiance, ou un départ d'une : l'heure de la présence a AVANCÉ depuis la dernière visite.
    private static func movement(seen: VisitMember, now: VisitMember) -> Candidate? {
        guard let at = now.presenceAtMillis else {
            return nil
        }
        if let before = seen.presenceAtMillis, at <= before {
            return nil
        }
        switch now.presence {
        case .inTrusted:
            return Candidate(
                atMillis: at, priority: priorityMovement, kind: .arrival, place: now.place, shownAtMillis: at
            )
        case .out:
            // Sortir d'une zone de danger n'est pas « quitter un lieu de confiance » (Q6).
            guard seen.presence != .inRisk else {
                return nil
            }
            return Candidate(
                atMillis: at, priority: priorityMovement, kind: .departure, place: now.place, shownAtMillis: at
            )
        default:
            return nil
        }
    }

    private static func update(seen: VisitMember, now: VisitMember, movement: Candidate?) -> Candidate? {
        guard let at = now.lastUpdateAtMillis else {
            return nil
        }
        if let before = seen.lastUpdateAtMillis, at <= before {
            return nil
        }
        // Une arrivée écrit aussi la dernière update : celle-ci est l'arrivée, pas une seconde nouvelle.
        if let movement, movement.kind == .arrival, abs(at - movement.atMillis) <= sameEventMillis {
            return nil
        }
        return Candidate(atMillis: at, priority: priorityUpdate, kind: .update, place: nil, shownAtMillis: at)
    }

    /// La personne vient de devenir rouge (plus de 4 h sans nouvelles) et ne l'était pas à la dernière visite (C1-T6, C1-T7) ; l'orange
    /// n'est jamais un silence (C1-T8).
    private static func silence(seen: VisitMember, now: VisitMember) -> Candidate? {
        guard now.silent, !seen.silent,
              let reference = [now.presenceAtMillis, now.lastUpdateAtMillis].compactMap({ $0 }).max() else {
            return nil
        }
        return Candidate(
            atMillis: reference + StatusThresholds.redAfterMillis + 1,
            priority: prioritySilence,
            kind: .silence,
            place: nil,
            shownAtMillis: reference
        )
    }
}

extension PlaceKind {
    /// La catégorie d'un lieu, dite par sa sorte : seules cinq catégories ont leur mot ; Loisir et Autre n'en ont pas (« un lieu de
    /// confiance », décision Q5 : le centre ne garde aucun nom de lieu).
    init?(category: PlaceCategory) {
        switch category {
        case .home:
            self = .home
        case .work:
            self = .work
        case .school:
            self = .school
        case .sport:
            self = .sport
        case .relative:
            self = .relative
        case .leisure, .other:
            return nil
        }
    }

    /// Le mot gardé dans la clé d'une ligne et dans l'image (mêmes mots qu'Android).
    var visitCode: String {
        switch self {
        case .home:
            return "HOME"
        case .work:
            return "WORK"
        case .school:
            return "SCHOOL"
        case .sport:
            return "SPORT"
        case .relative:
            return "RELATIVE"
        }
    }

    init?(visitCode: String) {
        let all: [PlaceKind] = [.home, .work, .school, .sport, .relative]
        guard let found = all.first(where: { $0.visitCode == visitCode }) else {
            return nil
        }
        self = found
    }
}
