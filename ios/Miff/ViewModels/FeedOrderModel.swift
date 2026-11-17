import Foundation
import SwiftUI

// L'ordre AFFICHÉ des cartes du Feed et son report (ticket FEED-ORDRE, §3 #6 et #7). Rien n'est lu ni écrit ici : l'ordre vient de
// `FeedBuilder` (les dates déjà lues), la logique est dans `FeedDisplayOrder` (pure, testée). Ce modèle y ajoute seulement ce qui touche
// l'écran : le geste en cours, l'animation douce (jamais avec « Réduire les animations ») et l'annonce du lecteur d'écran. Aucun prénom
// n'est gardé : les noms passent par `update` et sont oubliés à la mise à jour suivante ; rien n'est journalisé.
// Miroir du report d'ordre d'Android.

/// Ce que le Feed dit de l'ordre à afficher : la Miff, les membres dans l'ordre voulu, moi, et le prénom de chacun (identifiant vers prénom ;
/// vide quand la personne n'a pas de prénom lisible). Les prénoms servent seulement à dire qui a donné des nouvelles.
struct FeedOrderContext: Equatable, CustomStringConvertible {
    let miffId: String
    let uids: [String]
    let myUid: String
    let callNames: [String: String]

    var description: String {
        "FeedOrderContext(redacted)"
    }
}

@MainActor
final class FeedOrderModel: ObservableObject {
    /// L'ordre affiché : la vue en tire ses cartes (`resolve`).
    @Published private(set) var order = FeedDisplayOrder()

    /// Appelé quand l'ordre change réellement, jamais à l'arrivée sur l'écran ni pour ma carte. Branché sur le lecteur d'écran par la vue.
    var onAnnounce: (String) -> Void = { _ in }

    private var gestureActive = false
    private var context: FeedOrderContext?
    private static let moveDuration = 0.35

    /// Un nouvel ordre calculé pour la Miff affichée. L'ordre à jour est appliqué tout de suite au premier affichage et quand la Miff change
    /// (sans animation) ; sinon il attend la fin du geste. `animated` : faux avec « Réduire les animations ».
    func update(_ next: FeedOrderContext, animated: Bool) {
        let miffChanged = context?.miffId != next.miffId
        context = next
        if miffChanged {
            order.reset()
        }
        perform(animated: animated && !miffChanged, announcing: !miffChanged) {
            $0.receive(next.uids, gestureActive: gestureActive, immediate: miffChanged)
        }
    }

    /// Le Feed n'a plus de contenu à montrer (squelette) : à son retour, l'ordre à jour est appliqué directement, sans animation.
    func contentGone() {
        context = nil
        gestureActive = false
        order.reset()
    }

    /// Un doigt est posé ou la liste défile (`active`), ou le geste est fini : l'ordre en attente est alors appliqué, en une fois.
    func setGestureActive(_ active: Bool, animated: Bool) {
        guard gestureActive != active else {
            return
        }
        gestureActive = active
        if !active {
            perform(animated: animated, announcing: true) { $0.gestureEnded() }
        }
    }

    /// Retour sur l'onglet, rafraîchissement manuel : l'ordre à jour est appliqué tout de suite, sans animation ni annonce, quel que soit
    /// l'état du geste.
    func flush() {
        gestureActive = false
        perform(animated: false, announcing: false) { $0.flush() }
    }

    private func perform(
        animated: Bool,
        announcing: Bool,
        _ change: (inout FeedDisplayOrder) -> FeedDisplayOrder.Applied
    ) {
        var next = order
        let applied = change(&next)
        guard next != order else {
            return
        }
        if animated, case .reordered = applied {
            withAnimation(.easeInOut(duration: Self.moveDuration)) { order = next }
        } else {
            order = next
        }
        guard announcing, case .reordered(let from, let to) = applied, let context,
              let announcement = FeedOrderChange.announcement(from: from, to: to, myUid: context.myUid),
              let text = Self.text(for: announcement, names: context.callNames) else {
            return
        }
        onAnnounce(text)
    }

    /// Le texte de l'annonce (FR/EN, ton rassurant). Un prénom illisible se dit comme « plusieurs cartes ».
    static func text(for announcement: FeedOrderAnnouncement, names: [String: String]) -> String? {
        switch announcement {
        case .member(let uid):
            guard let name = names[uid], !name.isEmpty else {
                return L10n.string("feed_order_updated_a11y")
            }
            return L10n.format("feed_order_member_a11y", name)
        case .generic:
            return L10n.string("feed_order_updated_a11y")
        }
    }
}
