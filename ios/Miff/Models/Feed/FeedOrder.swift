import Foundation

// Ordre des cartes du Feed (ticket FEED-ORDRE) : du membre qui a donné des nouvelles le plus récemment au plus ancien. Logique PURE, sans
// aucune lecture ni écriture : seules les dates DÉJÀ lues (`status.lastUpdateAt`) sont comparées entre elles ; l'horloge de celui qui regarde
// n'intervient jamais. Miroir de l'ordre d'Android (les cas des tests sont le contrat).

/// Un membre à ordonner, dans l'ordre d'arrivée dans la Miff. `lastUpdateMillis` nil : aucune date exploitable (décision §3 #3 du ticket :
/// pas de statut lisible, partage arrêté ou caché, accord attendu, ou carte « Pas encore de nouvelles »).
struct FeedOrderEntry: Equatable {
    let uid: String
    let lastUpdateMillis: Int?
}

enum FeedOrder {
    /// Les identifiants dans l'ordre à afficher : les dates les plus récentes d'abord ; à dates égales, l'ordre d'arrivée puis l'identifiant
    /// (toujours le même ordre d'un lancement à l'autre) ; les membres sans date exploitable APRÈS tous les autres, dans l'ordre d'arrivée.
    /// Le tri est explicite (jamais « stable par chance ») : l'indice d'arrivée est la dernière clé avant l'identifiant.
    static func sorted(_ entries: [FeedOrderEntry]) -> [String] {
        let indexed = entries.enumerated().map { (index: $0.offset, entry: $0.element) }
        return indexed.sorted { left, right in
            switch (left.entry.lastUpdateMillis, right.entry.lastUpdateMillis) {
            case (nil, nil):
                return arrivesBefore(left, right)
            case (nil, _?):
                return false
            case (_?, nil):
                return true
            case (let leftDate?, let rightDate?):
                if leftDate != rightDate {
                    return leftDate > rightDate
                }
                return arrivesBefore(left, right)
            }
        }
        .map { $0.entry.uid }
    }

    private static func arrivesBefore(
        _ left: (index: Int, entry: FeedOrderEntry),
        _ right: (index: Int, entry: FeedOrderEntry)
    ) -> Bool {
        if left.index != right.index {
            return left.index < right.index
        }
        return left.entry.uid < right.entry.uid
    }
}

/// Ce que dit le lecteur d'écran quand l'ordre change réellement (ticket §3 #7), sans interrompre.
enum FeedOrderAnnouncement: Equatable {
    /// « {Prénom} a donné des nouvelles » : une seule carte a monté.
    case member(uid: String)
    /// « Le Feed a été mis à jour » : plusieurs cartes ont bougé.
    case generic
}

enum FeedOrderChange {
    /// L'annonce d'un changement d'ordre, ou nil : rien n'a changé entre les membres présents des deux côtés (une arrivée ou un départ ne
    /// s'annonce pas), ou c'est MA carte qui est arrivée en tête (jamais d'annonce pour elle). Une seule carte qui monte ET arrive en tête de
    /// la liste : son prénom ; sinon (plusieurs cartes, ou une carte qui monte sans arriver en tête), la phrase générale.
    static func announcement(from old: [String], to new: [String], myUid: String) -> FeedOrderAnnouncement? {
        let common = Set(old).intersection(new)
        let before = old.filter { common.contains($0) }
        let after = new.filter { common.contains($0) }
        guard before != after else {
            return nil
        }
        let risers = before.filter { uid in
            moved(uid, from: before, to: after) && singleMover(uid, before: before, after: after)
        }
        guard risers.count == 1, let riser = risers.first, new.first == riser else {
            return .generic
        }
        return riser == myUid ? nil : .member(uid: riser)
    }

    /// Le membre est plus haut dans la nouvelle liste.
    private static func moved(_ uid: String, from before: [String], to after: [String]) -> Bool {
        guard let oldIndex = before.firstIndex(of: uid), let newIndex = after.firstIndex(of: uid) else {
            return false
        }
        return newIndex < oldIndex
    }

    /// Retirer ce membre des deux listes les rend identiques : c'est lui seul qui a bougé.
    private static func singleMover(_ uid: String, before: [String], after: [String]) -> Bool {
        before.filter { $0 != uid } == after.filter { $0 != uid }
    }
}

/// L'ordre réellement AFFICHÉ, et celui qui l'attend (ticket §3 #6). Le contenu d'une carte se met à jour tout de suite ; l'ordre, lui,
/// n'est appliqué que quand personne n'a le doigt posé ni ne fait défiler la liste, pour ne jamais déplacer un bouton sous le doigt. Aucun
/// réordonnancement en cascade : l'ordre en attente est toujours le DERNIER reçu, appliqué en une seule fois.
struct FeedDisplayOrder: Equatable {
    private(set) var shown: [String] = []
    private(set) var target: [String] = []

    /// Ce qu'une mise à jour a fait de l'ordre affiché.
    enum Applied: Equatable {
        case unchanged
        case reordered(from: [String], to: [String])
    }

    /// Un nouvel ordre calculé. `immediate` : retour sur le Feed, rafraîchissement, autre Miff, premier affichage (l'ordre à jour est
    /// appliqué sans attendre). `gestureActive` : un doigt est posé ou la liste défile : seule la liste des membres suit (une personne partie
    /// disparaît, une arrivée se met en bas) ; aucune carte déjà là ne change de place.
    mutating func receive(_ newTarget: [String], gestureActive: Bool, immediate: Bool) -> Applied {
        target = newTarget
        if shown.isEmpty || immediate || !gestureActive {
            return applyTarget()
        }
        let kept = shown.filter { newTarget.contains($0) }
        let added = newTarget.filter { !shown.contains($0) }
        shown = kept + added
        return .unchanged
    }

    /// Le geste est terminé : l'ordre en attente est appliqué, une seule fois.
    mutating func gestureEnded() -> Applied {
        applyTarget()
    }

    /// L'ordre à jour est appliqué tout de suite.
    mutating func flush() -> Applied {
        applyTarget()
    }

    /// Oublie l'ordre affiché (le Feed n'a plus de contenu : squelette, autre Miff) : le prochain ordre est appliqué directement.
    mutating func reset() {
        shown = []
        target = []
    }

    /// Les cartes dans l'ordre AFFICHÉ. Le contenu vient toujours des cartes reçues ; une carte que l'ordre affiché ne connaît pas encore va
    /// à la fin, dans l'ordre reçu ; une carte qui n'existe plus n'est jamais montrée. L'ordre affiché vide donne l'ordre reçu.
    func resolve(_ cards: [FeedMemberCard]) -> [FeedMemberCard] {
        guard !shown.isEmpty else {
            return cards
        }
        var byUid: [String: FeedMemberCard] = [:]
        for card in cards {
            byUid[card.member.uid] = card
        }
        let known = shown.compactMap { byUid[$0] }
        let knownUids = Set(shown)
        return known + cards.filter { !knownUids.contains($0.member.uid) }
    }

    private mutating func applyTarget() -> Applied {
        let old = shown
        shown = target
        guard !old.isEmpty, old != target else {
            return .unchanged
        }
        return .reordered(from: old, to: target)
    }
}
