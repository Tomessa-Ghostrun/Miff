import CoreGraphics
import Foundation

// L'en-tête du Feed (avatar, logo, nom de la Miff et son sélecteur) se masque quand on fait défiler vers le bas et revient AUSSITÔT
// qu'on remonte, sans attendre d'être en haut de la liste (décision du PM, 2026-11-06, D41, qui remplace « en-tête fixe »). Cette
// logique est pure (testée sans interface) : l'écran ne fait que lui donner les déplacements du défilement et lire `offset`.
// Miroir de `CollapsingHeaderState` d'Android.

struct CollapsingHeaderState: Equatable {
    /// Le déplacement de l'en-tête en points : 0 = entièrement visible, -height = entièrement caché.
    private(set) var offset: CGFloat = 0
    /// La hauteur mesurée de l'en-tête (marges comprises). 0 tant qu'elle n'est pas mesurée : rien ne se cache avant.
    private(set) var height: CGFloat = 0
    /// Vrai quand l'en-tête doit rester visible quoi que fasse le défilement : chargement, erreur, hors connexion, lecteur d'écran
    /// allumé, sélecteur ouvert. Il revient alors (voir `lock`).
    private(set) var locked = false

    mutating func measured(_ newHeight: CGFloat) {
        height = max(newHeight, 0)
        offset = min(max(offset, -height), 0)
    }

    /// Le contenu a bougé de `delta` (négatif = le contenu monte = on descend dans la liste) : l'en-tête suit, dans les deux sens.
    mutating func follow(delta: CGFloat) {
        guard !locked, height > 0 else {
            return
        }
        offset = min(max(offset + delta, -height), 0)
    }

    /// Jamais plus caché que la liste n'a défilé tant qu'on est près du haut : la liste commence sous l'en-tête (marge), un en-tête
    /// plus caché que le défilement laisserait une bande vide au-dessus de la première carte. `scrolledFromTop` : nil loin du haut.
    mutating func limit(scrolledFromTop: CGFloat?) {
        guard let scrolledFromTop else {
            return
        }
        offset = max(offset, -max(scrolledFromTop, 0))
    }

    /// En dessous de ce déplacement (en points) le défilement est du bruit (un doigt qui tremble, un rebond) : l'en-tête ne bouge pas. Évite
    /// qu'il vibre à chaque image ; au-dessus, il suit le doigt point pour point (hystérésis : un petit va-et-vient ne le fait pas clignoter).
    static let minimumStep: CGFloat = 0.5

    /// Tant que le haut de la liste n'a pas défilé de plus que l'en-tête n'est haut, la liste est « près du haut » : l'en-tête n'y est jamais
    /// plus caché que la liste n'a défilé (sinon une bande vide, ou une première carte sous lui). Renvoie la distance défilée, ou nil loin du haut.
    static func scrolledFromTopIfNear(_ scrolled: CGFloat, headerHeight: CGFloat) -> CGFloat? {
        scrolled <= headerHeight ? scrolled : nil
    }

    /// Le défilement vient de passer de `previous` à `scrolled` (distance depuis le repos du haut de la liste ; négative quand on tire vers le
    /// bas) : l'en-tête suit, dans les deux sens, sauf pour un déplacement de moins de `minimumStep`, puis la règle du haut de liste s'applique.
    mutating func scrolled(from previous: CGFloat, to scrolled: CGFloat) {
        let delta = previous - scrolled
        if abs(delta) >= Self.minimumStep {
            follow(delta: delta)
        }
        limit(scrolledFromTop: Self.scrolledFromTopIfNear(scrolled, headerHeight: height))
    }

    mutating func show() {
        offset = 0
    }

    /// Garde l'en-tête visible tant que `value` est vrai ; il revient tout de suite quand il se verrouille.
    mutating func lock(_ value: Bool) {
        locked = value
        if value {
            offset = 0
        }
    }

    /// Les raisons de rester visible, décidées par l'écran (D41) : liste non chargée, hors connexion, lecteur d'écran, sélecteur ouvert.
    static func mustStayVisible(
        isList: Bool,
        offline: Bool,
        assistiveTechnology: Bool,
        selectorOpen: Bool
    ) -> Bool {
        !isList || offline || assistiveTechnology || selectorOpen
    }
}
