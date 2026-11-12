import Foundation

// Ce que le planificateur de rappels sait et ce qu'il rend (ticket Notifications §20.3). Du Swift PUR : rien ici ne demande quoi que ce
// soit au système. Aucune position, aucun nom de lieu, aucune photo : des identifiants, des heures, des couleurs.

/// Les faits d'UN membre dont la couleur a besoin (`MemberStatusFacts`), dans une forme qu'on peut garder sur le disque.
struct PlanMember: Equatable, Codable, CustomStringConvertible {
    let uid: String
    /// Le membre partage-t-il sa position ? (Sinon sa couleur est neutre.)
    let sharing: Bool
    /// La valeur de `presence` du statut (`in_trusted`, `out`, `in_risk`, `unknown`), nil sans statut.
    let presence: String?
    let presenceAtMillis: Int?
    let lastUpdateAtMillis: Int?

    var facts: MemberStatusFacts {
        MemberStatusFacts(
            sharingEnabled: sharing,
            presence: PresenceValue.fromWire(presence),
            presenceAtMillis: presenceAtMillis,
            lastUpdateAtMillis: lastUpdateAtMillis
        )
    }

    var description: String {
        "PlanMember(redacted)"
    }
}

/// L'instantané local (`NotifPlanSnapshot`) : la Miff active, moi, et les faits de chaque membre tels que le dernier écouteur du Feed
/// (ou ma propre écriture de statut) les a donnés. C'est sa seule source hors du premier plan.
struct NotificationPlanSnapshot: Equatable, Codable, CustomStringConvertible {
    let miffId: String
    let myUid: String
    var members: [PlanMember]
    let savedAtMillis: Int

    var description: String {
        "NotificationPlanSnapshot(redacted)"
    }

    /// L'instantané construit à partir des membres de la Miff et de leurs statuts lus sur le serveur.
    static func make(
        miffId: String,
        myUid: String,
        members: [(uid: String, sharing: Bool)],
        statuses: [String: MemberStatus],
        savedAtMillis: Int
    ) -> NotificationPlanSnapshot {
        let planMembers = members.map { member -> PlanMember in
            let status = statuses[member.uid]
            return PlanMember(
                uid: member.uid,
                sharing: member.sharing,
                presence: status?.presence?.rawValue,
                presenceAtMillis: status?.presenceAtMillis,
                lastUpdateAtMillis: status?.lastUpdateAtMillis
            )
        }
        return NotificationPlanSnapshot(
            miffId: miffId,
            myUid: myUid,
            members: planMembers,
            savedAtMillis: savedAtMillis
        )
    }

    /// Mes faits changent (le suivi des lieux vient de détecter une arrivée ou un départ) : seule MA ligne bouge.
    func withMyFacts(
        presence: PresenceValue,
        presenceAtMillis: Int,
        setLastUpdate: Bool,
        savedAtMillis: Int
    ) -> NotificationPlanSnapshot {
        replacingMine(savedAtMillis: savedAtMillis) { mine in
            PlanMember(
                uid: mine.uid,
                sharing: mine.sharing,
                presence: presence.rawValue,
                presenceAtMillis: presenceAtMillis,
                lastUpdateAtMillis: setLastUpdate ? presenceAtMillis : mine.lastUpdateAtMillis
            )
        }
    }

    /// J'ai publié une update : mon repère avance.
    func withMyUpdate(atMillis: Int, savedAtMillis: Int) -> NotificationPlanSnapshot {
        replacingMine(savedAtMillis: savedAtMillis) { mine in
            PlanMember(
                uid: mine.uid,
                sharing: mine.sharing,
                presence: mine.presence,
                presenceAtMillis: mine.presenceAtMillis,
                lastUpdateAtMillis: atMillis
            )
        }
    }

    private func replacingMine(
        savedAtMillis: Int,
        _ change: (PlanMember) -> PlanMember
    ) -> NotificationPlanSnapshot {
        var changed = members
        if let index = changed.firstIndex(where: { $0.uid == myUid }) {
            changed[index] = change(changed[index])
        } else {
            // Je ne suis pas encore dans la liste (le Feed n'a pas encore répondu) : seule ma ligne existe.
            let blank = PlanMember(
                uid: myUid, sharing: true, presence: nil, presenceAtMillis: nil, lastUpdateAtMillis: nil
            )
            changed.append(change(blank))
        }
        return NotificationPlanSnapshot(
            miffId: miffId, myUid: myUid, members: changed, savedAtMillis: savedAtMillis
        )
    }
}

/// Une notification déjà affichée (entrée « affichée » du centre), pour les plafonds du jour.
struct ShownNotification: Equatable {
    let type: NotificationType
    let atMillis: Int
}

/// Responsable du changement d'état (N3 / N4). Décision du PM : LARGE.
enum NotificationResponsibility {
    /// Je suis responsable quand MA couleur est celle de la Miff (j'ai déjà eu N1 / N2 pour ce passage).
    case broad
    /// Je ne le suis que si je suis la SEULE cause.
    case strict

    static let current = NotificationResponsibility.broad
}

/// Tout ce que `NotificationPlanner.plan` lit. Rien d'autre : la fonction est pure et idempotente.
struct NotificationPlanInput {
    var nowMillis: Int
    var snapshot: NotificationPlanSnapshot?
    /// L'identifiant de la Miff active du Feed.
    var activeMiffId: String?
    /// Le compte connecté (nil : plus de session).
    var sessionUid: String?
    /// Mon accord est attendu dans cette Miff (« Pas maintenant » au renouvellement).
    var agreementWaiting = false
    var preferences = NotificationPreferences()
    var authorization = NotificationAuthorization.authorized
    /// Les clés déjà notifiées (conservées 48 h) : jamais renotifiées.
    var notifiedKeys: Set<String> = []
    /// Ce qui a déjà été affiché (pour les plafonds du jour).
    var shown: [ShownNotification] = []
    var calendar = Calendar.current
    var responsibility = NotificationResponsibility.current
    /// N3 / N4 ne sont programmées que si `F - savedAt` ne dépasse pas cet horizon (parade iOS n° 4 : le système ne peut pas vérifier
    /// avant d'afficher, donc on ne programme pas trop loin d'une donnée fraîche).
    var horizonMillis = NotificationPlanner.iosHorizonMillis
    /// MODE VOYAGE : la fin (instant absolu) de MON voyage, ou nil. Tant que `nowMillis` est avant elle, je suis en voyage : ni N1 à N4. N7
    /// est programmée tant qu'une fin est connue, même sans Miff (un voyage n'en a pas besoin).
    var myTravelUntilMillis: Int?

    /// Je suis en voyage à cet instant.
    var isTravelling: Bool {
        TravelEndNotice.isTravelling(untilMillis: myTravelUntilMillis, nowMillis: nowMillis)
    }
}

/// Une notification que le planificateur veut voir apparaître.
struct PlannedNotification: Equatable {
    let type: NotificationType
    let miffId: String
    /// La clé de déduplication de l'événement (`n1|miff|moi|repère`, `n3|miff|début de l'épisode`…).
    let key: String
    let fireAtMillis: Int
    /// Nuit calme : sans son ni vibration (`.passive` sur iOS).
    let quiet: Bool

    /// L'identifiant de la demande du système : un par type et par Miff (en réutiliser un REMPLACE la demande).
    var identifier: String {
        NotificationIdentifier.make(type: type, miffId: miffId)
    }
}

enum DropReason: Equatable {
    case alreadyNotified
    case dailyCap
}

struct DroppedNotification: Equatable {
    let key: String
    let reason: DropReason
}

/// Ce que le planificateur rend.
struct NotificationPlan: Equatable {
    /// À programmer (instant futur).
    var scheduled: [PlannedNotification] = []
    /// Instant déjà passé et toujours vrai (rattrapage, §20.8) : le réconciliateur décide de l'afficher ou de l'inscrire en silence.
    var catchUp: [PlannedNotification] = []
    /// Rattrapages au-delà des plafonds du jour : entrée de centre silencieuse, jamais de notification du système.
    var silent: [PlannedNotification] = []
    var dropped: [DroppedNotification] = []

    var isEmpty: Bool {
        scheduled.isEmpty && catchUp.isEmpty && silent.isEmpty
    }
}

enum NotificationIdentifier {
    static let prefix = "miff.notif."

    static func make(type: NotificationType, miffId: String) -> String {
        "\(prefix)\(type.rawValue).\(miffId)"
    }
}
