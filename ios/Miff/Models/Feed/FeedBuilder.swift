import Foundation

// Toute la logique du Feed, en UNE fonction pure (ticket FEED, critères B1 à B6 et C1 à C7) : de ce que les écoutes ont dit et de
// l'heure du moment, à ce que dessinent les écrans. Rien n'est lu, écrit ni journalisé ici. La couleur d'un membre n'est calculée
// QUE par `StatusColor.level` : les constantes 2 h et 4 h vivent dans `StatusThresholds`, jamais réécrites ici. Miroir exact de
// `FeedBuilder.kt` d'Android (les cas des tests sont le contrat).

/// Ce que l'écoute des statuts dit, tel que le Feed le voit.
enum StatusesView: Equatable {
    /// Pas lus du tout : j'ai arrêté de partager, ou mon accord est attendu (les règles refuseraient, et rien n'est voulu : zéro lecture).
    case off
    /// Écoute attachée, pas encore de réponse : l'écran montre son squelette, jamais une couleur que personne ne connaît.
    case waiting
    /// Les règles refusent la lecture tant que mon propre statut n'est pas publié : « Partage de ton statut en cours… », jamais une erreur.
    case pending
    /// L'écoute n'a rien pu dire pendant un moment : cartes neutres, aucune phrase de sécurité.
    case unknown
    /// Tous les statuts de la Miff. `fromCache` : seul le cache du téléphone en répond (le serveur n'a pas encore confirmé) ; cela
    /// se dit « informations de 16 h 02 », jamais comme le présent.
    case loaded(byUid: [String: MemberStatus], fromCache: Bool)

    var isLoaded: Bool {
        if case .loaded = self {
            return true
        }
        return false
    }
}

/// Tout ce dont le Feed a besoin pour la Miff active.
struct FeedInput: CustomStringConvertible {
    let miffId: String
    let miffName: String
    let joinCode: String
    let members: [MiffMember]
    let places: [Place]
    let myUid: String
    let statuses: StatusesView
    /// Le renouvellement de mon accord est attendu : rien n'est partagé, les statuts des autres ne sont pas lus.
    let waitingAgreement: Bool
    let offline: Bool

    /// Pas de réseau, OU des données que seul le cache garantit : les deux se disent « informations de 16 h 02 ».
    var effectivelyOffline: Bool {
        if offline {
            return true
        }
        if case .loaded(_, let fromCache) = statuses {
            return fromCache
        }
        return false
    }

    var description: String {
        "FeedInput(redacted)"
    }
}

enum FeedBuilder {
    /// BATTERY_MAX_AGE (décision du PM, 2026-11-07, D44) : au-delà de 6 h la batterie n'est plus montrée (« là où on n'a pas l'info »). L'âge se
    /// compte depuis `updatedAt` du statut (l'heure SERVEUR de la dernière écriture, qui porte toujours une batterie fraîche), JAMAIS depuis
    /// `lastUpdateAt` (la dernière photo). Le seuil est susceptible de changer : une seule constante.
    static let batteryMaxAgeMillis = 6 * FeedMillis.hour
    private static let limitedAccess: Set<MemberLocationAccess> = [.denied, .foreground]

    /// Ce que les fonctions du calcul se passent (une fonction à plus de 5 paramètres est refusée par SwiftLint).
    struct Context {
        let input: FeedInput
        let now: Int
        let zone: TimeZone
        let mySharing: Bool
        let hidden: Bool
        let statuses: [String: MemberStatus]
        let places: [String: Place]

        func moment(_ millis: Int) -> Moment {
            Moment.of(atMillis: millis, nowMillis: now, zone: zone)
        }

        func age(_ millis: Int) -> Age {
            Age.of(thenMillis: millis, nowMillis: now, zone: zone)
        }
    }

    static func build(input: FeedInput, nowMillis: Int, zone: TimeZone) -> FeedContent {
        let mySharing = input.members.first { $0.uid == input.myUid }?.sharingEnabled ?? true
        var statuses: [String: MemberStatus] = [:]
        if case .loaded(let byUid, _) = input.statuses {
            statuses = byUid
        }
        let context = Context(
            input: input,
            now: nowMillis,
            zone: zone,
            // Rien des autres n'est lu (ni montré) tant que je ne partage pas ou que mon accord est attendu.
            mySharing: mySharing,
            hidden: !mySharing || input.waitingAgreement,
            statuses: statuses,
            places: Dictionary(input.places.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        )
        let cards = orderMembers(input.members, myUid: input.myUid).map { card($0, context) }
        let mine = cards.first { $0.isMe }
        let miffColor = miffLevel(cards.map { $0.level })
        let latestUpdate = statuses.values.compactMap { $0.updatedAtMillis }.max()
        let shownBubble = bubble(context, cards: cards, latestUpdate: latestUpdate, miffLevel: miffColor)
        return FeedContent(
            miffId: input.miffId,
            miffName: input.miffName,
            joinCode: input.joinCode,
            isFull: input.members.count >= MiffSharingConsent.softMemberLimit,
            myLevel: mine?.level ?? .neutral,
            myLabel: mine?.label ?? .noNews,
            miffLevel: miffColor,
            bubble: shownBubble,
            banners: banners(context, latestUpdate: latestUpdate),
            cards: cards,
            announceKey: announceKey(shownBubble),
            myFacts: myFacts(input, mySharing: mySharing, statuses: statuses)
        )
    }

    /// MON statut pour l'Update : seulement quand je partage et que mon accord n'est pas attendu (sinon le bouton central est neutre).
    private static func myFacts(
        _ input: FeedInput,
        mySharing: Bool,
        statuses: [String: MemberStatus]
    ) -> MyStatusFacts? {
        guard mySharing, !input.waitingAgreement, let mine = statuses[input.myUid] else {
            return nil
        }
        return MyStatusFacts(
            presence: mine.presence,
            presenceAtMillis: mine.presenceAtMillis,
            lastUpdateAtMillis: mine.lastUpdateAtMillis,
            hasPhoto: mine.hasPhoto
        )
    }

    /// L'ordre des cartes (décision du PM, 2026-11-07, D43) : les AUTRES membres d'abord, dans l'ordre où la liste les donne (le
    /// créateur, puis l'ordre d'arrivée : jamais réordonné selon l'urgence), et MOI EN DERNIER. Le tri est stable : les autres
    /// gardent exactement l'ordre reçu.
    static func orderMembers(_ members: [MiffMember], myUid: String) -> [MiffMember] {
        members.filter { $0.uid != myUid } + members.filter { $0.uid == myUid }
    }

    /// La couleur de la Miff (décision du PM) : rouge si au moins un membre est rouge ; verte si tous ceux dont le statut est connu
    /// sont verts ; sinon orange. Les neutres (pas de statut, partage arrêté, `unknown`) sont IGNORÉS ; personne de connu = neutre.
    static func miffLevel(_ levels: [StatusLevel]) -> StatusLevel {
        let known = levels.filter { $0 != .neutral }
        if known.isEmpty {
            return .neutral
        }
        if known.contains(.red) {
            return .red
        }
        return known.allSatisfy { $0 == .green } ? .green : .orange
    }

    private static func banners(_ context: Context, latestUpdate: Int?) -> [FeedBanner] {
        var banners: [FeedBanner] = []
        if context.input.effectivelyOffline {
            banners.append(.offline(latest: latestUpdate.map { context.moment($0) }))
        }
        if context.input.statuses == .pending && context.mySharing && !context.input.waitingAgreement {
            banners.append(.publishing)
        }
        return banners
    }

    // MARK: - Les cartes

    private static func card(_ member: MiffMember, _ context: Context) -> FeedMemberCard {
        let isMe = member.uid == context.input.myUid
        // Défense en profondeur : quelqu'un qui a arrêté de partager n'a ni batterie ni « Localisation limitée » ni lieu montrés, même si un
        // ancien statut traîne dans le cache ; et rien des autres n'est montré tant que je ne partage pas.
        let hiddenStatus = (context.hidden && !isMe) || !member.sharingEnabled
        let status = hiddenStatus ? nil : context.statuses[member.uid]
        let facts = status.map {
            MemberStatusFacts(
                sharingEnabled: member.sharingEnabled,
                presence: $0.presence,
                presenceAtMillis: $0.presenceAtMillis,
                lastUpdateAtMillis: $0.lastUpdateAtMillis
            )
        }
        let level = StatusColor.level(facts, nowMillis: context.now)
        return FeedMemberCard(
            member: member,
            isMe: isMe,
            level: level,
            label: label(member: member, isMe: isMe, status: status, level: level, context),
            time: timeLine(level: level, status: status, context),
            battery: status.flatMap { battery($0, now: context.now) },
            limitedLocation: status?.locationAccess.map { limitedAccess.contains($0) } ?? false,
            phone: isMe ? nil : member.phone,
            // Un autre membre qui partage, pendant que je partage aussi et que les statuts sont VRAIMENT lus (les règles demandent que
            // mon statut soit publié) : même sans statut connu, « pas de position » est l'une des réponses calmes de la feuille.
            canLocate: !isMe && context.mySharing && !context.hidden && member.sharingEnabled
                && context.input.statuses.isLoaded,
            // FEED-8 : seulement quand le statut est montré (partage actif, rien de caché) ET dit qu'il y a une photo récente.
            photo: PhotoSlot.of(
                hasPhoto: status?.hasPhoto ?? false,
                lastUpdateAtMillis: status?.lastUpdateAtMillis,
                visible: status != nil,
                nowMillis: context.now
            )
        )
    }

    private static func label(
        member: MiffMember,
        isMe: Bool,
        status: MemberStatus?,
        level: StatusLevel,
        _ context: Context
    ) -> StateLabel {
        if isMe && context.input.waitingAgreement {
            return .waitingAgreement
        }
        if !member.sharingEnabled {
            return .sharingStopped
        }
        if context.hidden && !isMe {
            return .newsHidden
        }
        guard level != .neutral, let status else {
            return .noNews
        }
        switch status.presence {
        case .inTrusted:
            return .at(placeRef(status.placeId, context.places, trusted: true))
        case .inRisk:
            return .danger
        default:
            return .left(placeRef(status.placeId, context.places, trusted: true))
        }
    }

    /// Un lieu des catégories Maison, Travail, École, Sport, Chez un proche se dit par sa catégorie ; Loisir, Autre et un lieu sans
    /// catégorie (écrit avant que la catégorie existe) par leur nom ; introuvable : générique. La catégorie est lue dans la liste des
    /// lieux que le Feed écoute déjà : aucune lecture de plus.
    /// Dans une zone de danger (`trusted` faux) le nom du lieu est toujours dit.
    static func placeRef(_ placeId: String?, _ places: [String: Place], trusted: Bool) -> PlaceRef {
        guard let placeId, let place = places[placeId] else {
            return .unknown
        }
        // Un nom vide ou fait d'espaces ne se dit jamais (« À  ») : espaces retirés, puis formule générique (comme Android).
        let name = place.name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trusted else {
            return name.isEmpty ? .unknown : .named(name)
        }
        switch place.category {
        case .home:
            return .category(.home)
        case .work:
            return .category(.work)
        case .school:
            return .category(.school)
        case .sport:
            return .category(.sport)
        case .relative:
            return .category(.relative)
        case .leisure, .other, nil:
            return name.isEmpty ? .unknown : .named(name)
        }
    }

    private static func timeLine(level: StatusLevel, status: MemberStatus?, _ context: Context) -> TimeLine {
        guard let status, level != .neutral else {
            return .nothing
        }
        let presenceAt = status.presenceAtMillis
        switch status.presence {
        case .inTrusted:
            return presenceAt.map { TimeLine.since(context.moment($0)) } ?? .nothing
        case .inRisk:
            return presenceAt.map { TimeLine.inDangerSince(context.moment($0)) } ?? .nothing
        case .out:
            guard let reference = status.referenceMillis else {
                return .nothing
            }
            return outTimeLine(level: level, presenceAt: presenceAt, reference: reference, context)
        default:
            return .nothing
        }
    }

    private static func outTimeLine(
        level: StatusLevel,
        presenceAt: Int?,
        reference: Int,
        _ context: Context
    ) -> TimeLine {
        switch level {
        case .green:
            let departure = presenceAt ?? reference
            // Un départ d'un jour ou plus encore vert (une update récente) : seule la nouvelle est dite. Sinon l'heure du départ.
            switch context.age(departure) {
            case .minutes, .hours:
                return .since(context.moment(departure))
            default:
                return .latestNews(context.moment(reference))
            }
        case .orange:
            return .latestNews(context.moment(reference))
        default:
            return .noNewsSince(context.moment(reference))
        }
    }

    /// Décision D44 : la pastille dit « 85 % » seul, jamais l'heure de la mesure. Une heure que le serveur n'a pas encore remplie
    /// (une écriture en attente) est « maintenant » ; au-delà de `batteryMaxAgeMillis` la valeur est masquée.
    private static func battery(_ status: MemberStatus, now: Int) -> Battery? {
        guard let pct = status.batteryPct else {
            return nil
        }
        guard let written = status.updatedAtMillis else {
            return Battery(pct: pct)
        }
        return now - written > batteryMaxAgeMillis ? nil : Battery(pct: pct)
    }
}
