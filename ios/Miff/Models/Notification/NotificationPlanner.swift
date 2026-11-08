import Foundation

// Le planificateur de rappels N1 à N4 (ticket Notifications §20.3), en fonction PURE : mêmes entrées, même sortie, sans horloge ni
// système. Il ne programme rien lui-même : `NotificationReconciler` applique son plan au système. Les cas de test T1 à T15 du ticket
// (§20.9) sont le contrat avec Android.
//
// - N1 / N2 : MA couleur passe orange / rouge (hors zone de danger : jamais de rappel « à toi » en zone de danger, Q27).
// - N3 / N4 : la couleur de la MIFF passe orange / rouge ; 30 minutes après, si la Miff l'est toujours et sauf si je suis responsable.
// - Un seul rappel en attente par type et par Miff ; plafonds du jour (2 par type, 6 au total) ; nuit calme ; clés jamais renotifiées.

enum NotificationPlanner {
    /// N3 / N4 partent 30 minutes après le passage de la Miff à la couleur.
    static let followUpDelayMillis = 30 * 60 * 1000
    /// Un rattrapage de plus de 15 minutes n'est plus une notification mais une entrée silencieuse du centre (§20.8).
    static let catchUpWindowMillis = 15 * 60 * 1000
    /// Parade iOS n° 4 : N3 / N4 ne sont programmées que si leur heure est à 3 h au plus de la donnée la plus fraîche (réglage).
    static let iosHorizonMillis = 3 * 60 * 60 * 1000
    /// Plafonds du jour local (décision Q30 : 2 par type, 6 au total ; N6 a son propre plafond, hors de celui-ci).
    static let maxPerTypePerDay = 2
    static let maxPerDay = 6
    /// Les clés notifiées sont gardées 48 h.
    static let keysRetentionMillis = 48 * 60 * 60 * 1000
    /// Nuit calme : de 22 h (inclus) à 7 h (exclu), heure locale à l'instant de l'affichage.
    static let nightStartHour = 22
    static let nightEndHour = 7

    /// Le plan pour cet instant. Vide quand une garde ne passe pas (pas de session, pas de Miff active, interrupteur général coupé,
    /// autorisation absente, mon partage arrêté ou mon accord attendu).
    static func plan(_ input: NotificationPlanInput) -> NotificationPlan {
        guard let snapshot = input.snapshot,
              input.sessionUid == snapshot.myUid,
              input.activeMiffId == snapshot.miffId,
              input.authorization.isAllowed,
              input.preferences.general,
              !input.agreementWaiting,
              let me = snapshot.members.first(where: { $0.uid == snapshot.myUid }),
              me.sharing else {
            return NotificationPlan()
        }
        let timeline = Timeline(members: snapshot.members, myUid: snapshot.myUid)
        let candidates = ownCandidates(input, snapshot: snapshot, me: me, timeline: timeline)
            + miffCandidates(input, snapshot: snapshot, timeline: timeline)
        return applyRules(candidates, input: input)
    }

    // MARK: - Les niveaux à un instant (pour retirer ce qui n'est plus vrai, et juger une fausse alerte)

    /// MA couleur et celle de la Miff à `atMillis`, d'après les faits de l'instantané.
    static func levels(of snapshot: NotificationPlanSnapshot, atMillis: Int) -> (mine: StatusLevel, miff: StatusLevel) {
        let timeline = Timeline(members: snapshot.members, myUid: snapshot.myUid)
        return (timeline.myLevel(at: atMillis), timeline.miffLevel(at: atMillis))
    }

    /// Ce qu'une notification déjà affichée disait est-il toujours vrai à `atMillis` ? N1 / N2 : je suis encore orange / rouge. N3 / N4 :
    /// la Miff a encore cette couleur ET je n'en suis pas responsable. Sert à retirer une notification obsolète de l'écran, et à
    /// compter une fausse alerte.
    static func stillTrue(
        _ type: NotificationType,
        snapshot: NotificationPlanSnapshot,
        atMillis: Int,
        responsibility: NotificationResponsibility = .current
    ) -> Bool {
        let timeline = Timeline(members: snapshot.members, myUid: snapshot.myUid)
        switch type {
        case .n1:
            return timeline.myLevel(at: atMillis) == .orange
        case .n2:
            return timeline.myLevel(at: atMillis) == .red
        case .n3:
            return timeline.miffLevel(at: atMillis) == .orange
                && !timeline.isResponsible(at: atMillis, mode: responsibility)
        case .n4:
            return timeline.miffLevel(at: atMillis) == .red
                && !timeline.isResponsible(at: atMillis, mode: responsibility)
        case .n6:
            // N6 est immédiate et ne s'annule pas : rien à retirer.
            return true
        }
    }

    // MARK: - Les candidats

    private struct Candidate {
        let type: NotificationType
        let miffId: String
        let key: String
        let fireAtMillis: Int
        let isCatchUp: Bool
    }

    /// N1 et N2 : seulement `presence = out` (jamais en zone de danger).
    private static func ownCandidates(
        _ input: NotificationPlanInput,
        snapshot: NotificationPlanSnapshot,
        me: PlanMember,
        timeline: Timeline
    ) -> [Candidate] {
        guard PresenceValue.fromWire(me.presence) == .out,
              let reference = [me.presenceAtMillis, me.lastUpdateAtMillis].compactMap({ $0 }).max() else {
            return []
        }
        let instants = StatusColor.changeInstants(me.facts)
        var result: [Candidate] = []
        let wanted: [(NotificationType, StatusLevel)] = [(.n1, .orange), (.n2, .red)]
        for (type, color) in wanted where input.preferences.isEnabled(type) {
            guard let at = instants.first(where: { timeline.myLevel(at: $0) == color }) else {
                continue
            }
            let key = "\(type.rawValue)|\(snapshot.miffId)|\(snapshot.myUid)|\(reference)"
            if at > input.nowMillis {
                result.append(
                    Candidate(type: type, miffId: snapshot.miffId, key: key, fireAtMillis: at, isCatchUp: false)
                )
            } else if timeline.myLevel(at: input.nowMillis) == color {
                // Déjà passé et toujours vrai ; si je suis déjà plus haut (orange passé au rouge), N1 n'est pas affichée.
                result.append(
                    Candidate(type: type, miffId: snapshot.miffId, key: key, fireAtMillis: at, isCatchUp: true)
                )
            }
        }
        return result
    }

    /// N3 et N4 : 30 minutes après le début de l'épisode de la couleur de la Miff, si elle l'est toujours et sauf si j'en suis responsable.
    private static func miffCandidates(
        _ input: NotificationPlanInput,
        snapshot: NotificationPlanSnapshot,
        timeline: Timeline
    ) -> [Candidate] {
        var result: [Candidate] = []
        let wanted: [(NotificationType, StatusLevel)] = [(.n3, .orange), (.n4, .red)]
        for (type, color) in wanted where input.preferences.isEnabled(type) {
            guard let start = timeline.episodeStart(of: color, nowMillis: input.nowMillis) else {
                continue
            }
            let fire = start + followUpDelayMillis
            let key = "\(type.rawValue)|\(snapshot.miffId)|\(start)"
            if fire > input.nowMillis {
                let holds = timeline.miffLevel(at: fire) == color
                    && !timeline.isResponsible(at: fire, mode: input.responsibility)
                let withinHorizon = fire - snapshot.savedAtMillis <= input.horizonMillis
                if holds && withinHorizon {
                    result.append(
                        Candidate(type: type, miffId: snapshot.miffId, key: key, fireAtMillis: fire, isCatchUp: false)
                    )
                }
            } else {
                let holds = timeline.miffLevel(at: input.nowMillis) == color
                    && !timeline.isResponsible(at: input.nowMillis, mode: input.responsibility)
                if holds {
                    result.append(
                        Candidate(type: type, miffId: snapshot.miffId, key: key, fireAtMillis: fire, isCatchUp: true)
                    )
                }
            }
        }
        return result
    }

    // MARK: - Clés déjà notifiées, plafonds du jour, nuit calme

    private static func applyRules(_ candidates: [Candidate], input: NotificationPlanInput) -> NotificationPlan {
        let ordered = candidates.sorted { left, right in
            if left.fireAtMillis != right.fireAtMillis {
                return left.fireAtMillis < right.fireAtMillis
            }
            return left.type.rawValue < right.type.rawValue
        }
        var counts = DayCounts(calendar: input.calendar)
        for record in input.shown where record.type != .n6 {
            counts.add(type: record.type, atMillis: record.atMillis)
        }
        var plan = NotificationPlan()
        for candidate in ordered {
            if input.notifiedKeys.contains(candidate.key) {
                plan.dropped.append(DroppedNotification(key: candidate.key, reason: .alreadyNotified))
                continue
            }
            let planned = PlannedNotification(
                type: candidate.type,
                miffId: candidate.miffId,
                key: candidate.key,
                fireAtMillis: candidate.fireAtMillis,
                quiet: isQuiet(candidate.type, atMillis: candidate.fireAtMillis, input: input)
            )
            guard counts.canAdd(type: candidate.type, atMillis: candidate.fireAtMillis) else {
                if candidate.isCatchUp {
                    plan.silent.append(planned)
                } else {
                    plan.dropped.append(DroppedNotification(key: candidate.key, reason: .dailyCap))
                }
                continue
            }
            counts.add(type: candidate.type, atMillis: candidate.fireAtMillis)
            if candidate.isCatchUp {
                plan.catchUp.append(planned)
            } else {
                plan.scheduled.append(planned)
            }
        }
        return plan
    }

    /// Sans son ni vibration la nuit pour N1, N3 (et N6) ; N2 et N4 gardent leur niveau normal (décision Q30).
    static func isQuiet(_ type: NotificationType, atMillis: Int, input: NotificationPlanInput) -> Bool {
        input.preferences.nightCalm && type.silentAtNight && isNight(atMillis: atMillis, calendar: input.calendar)
    }

    static func isNight(atMillis: Int, calendar: Calendar) -> Bool {
        let hour = calendar.component(.hour, from: Date(timeIntervalSince1970: Double(atMillis) / 1000))
        return hour >= nightStartHour || hour < nightEndHour
    }

    /// Les compteurs du jour local : par type et au total.
    private struct DayCounts {
        let calendar: Calendar
        private var perType: [Date: [NotificationType: Int]] = [:]
        private var total: [Date: Int] = [:]

        init(calendar: Calendar) {
            self.calendar = calendar
        }

        private func day(_ millis: Int) -> Date {
            calendar.startOfDay(for: Date(timeIntervalSince1970: Double(millis) / 1000))
        }

        func canAdd(type: NotificationType, atMillis: Int) -> Bool {
            let key = day(atMillis)
            return (perType[key]?[type] ?? 0) < NotificationPlanner.maxPerTypePerDay
                && (total[key] ?? 0) < NotificationPlanner.maxPerDay
        }

        mutating func add(type: NotificationType, atMillis: Int) {
            let key = day(atMillis)
            perType[key, default: [:]][type, default: 0] += 1
            total[key, default: 0] += 1
        }
    }

    // MARK: - La ligne du temps (les niveaux à un instant, avec les faits actuels)

    private struct Timeline {
        let members: [PlanMember]
        let myUid: String

        func level(of member: PlanMember, at millis: Int) -> StatusLevel {
            StatusColor.level(member.facts, nowMillis: millis)
        }

        func myLevel(at millis: Int) -> StatusLevel {
            guard let me = members.first(where: { $0.uid == myUid }) else {
                return .neutral
            }
            return level(of: me, at: millis)
        }

        func miffLevel(at millis: Int, excludingMe: Bool = false) -> StatusLevel {
            let counted = excludingMe ? members.filter { $0.uid != myUid } : members
            return FeedBuilder.miffLevel(counted.map { level(of: $0, at: millis) })
        }

        func isResponsible(at millis: Int, mode: NotificationResponsibility) -> Bool {
            let mine = myLevel(at: millis)
            let all = miffLevel(at: millis)
            guard mine == all else {
                return false
            }
            switch mode {
            case .broad:
                return true
            case .strict:
                return miffLevel(at: millis, excludingMe: true) != all
            }
        }

        /// Tous les instants où le niveau d'un membre peut changer, triés, sans doublon.
        func instants() -> [Int] {
            Array(Set(members.flatMap { StatusColor.changeInstants($0.facts) })).sorted()
        }

        /// Le début de l'épisode de `color` de la Miff : le dernier instant `c <= maintenant` où la Miff entre dans cette couleur ;
        /// ou, si elle n'y est pas encore, le prochain. nil quand la Miff n'a pas (et n'aura pas) cette couleur. Quand elle l'a
        /// « depuis toujours » d'après les faits (un membre en zone de danger sans update), l'heure d'arrivée la plus récente des
        /// membres de cette couleur : une valeur stable d'un calcul à l'autre.
        func episodeStart(of color: StatusLevel, nowMillis: Int) -> Int? {
            let entries = instants().filter { miffLevel(at: $0 - 1) != color && miffLevel(at: $0) == color }
            if miffLevel(at: nowMillis) == color {
                if let last = entries.last(where: { $0 <= nowMillis }) {
                    return last
                }
                let arrivals = members
                    .filter { level(of: $0, at: nowMillis) == color }
                    .compactMap { $0.presenceAtMillis }
                return arrivals.max()
            }
            return entries.first(where: { $0 > nowMillis })
        }
    }
}
