import Combine
import CoreGraphics
import Foundation

/// Le déplacement de l'en-tête pendant le défilement (D41), tenu HORS de l'écran du Feed : seul l'en-tête observe ce modèle, donc un
/// défilement ne redessine pas toute la liste, seulement l'en-tête qui bouge. Il traduit la position du contenu (lue par l'écran)
/// en déplacements pour `CollapsingHeaderState` (la logique pure, testée).
@MainActor
final class FeedHeaderModel: ObservableObject {
    @Published private(set) var state = CollapsingHeaderState()
    /// La position du contenu au repos (la première lue) : le défilement est mesuré depuis elle.
    private var baseline: CGFloat?
    private var lastScrolled: CGFloat = 0

    /// La position actuelle du haut du contenu dans la zone de défilement (diminue quand on descend dans la liste).
    func contentMoved(to position: CGFloat) {
        guard let baseline else {
            baseline = position
            lastScrolled = 0
            return
        }
        let scrolled = baseline - position
        var next = state
        // Le contenu monte (on descend dans la liste) : delta négatif, l'en-tête monte avec ; il redescend dès qu'on remonte.
        next.follow(delta: lastScrolled - scrolled)
        next.limit(scrolledFromTop: scrolled)
        lastScrolled = scrolled
        if next != state {
            state = next
        }
    }

    func measured(_ height: CGFloat) {
        var next = state
        next.measured(height)
        if next != state {
            // La marge haute de la liste change avec la hauteur de l'en-tête : la position de repos est relue au prochain mouvement.
            if next.height != state.height {
                resetBaseline()
            }
            state = next
        }
    }

    /// Garde l'en-tête visible tant que `value` est vrai (chargement, erreur, hors connexion, lecteur d'écran, sélecteur ouvert).
    func lock(_ value: Bool) {
        var next = state
        next.lock(value)
        if next != state {
            state = next
        }
    }

    /// Le logo (retour en haut) ou un toucher sur l'onglet déjà actif : l'en-tête est montré de nouveau.
    func show() {
        var next = state
        next.show()
        if next != state {
            state = next
        }
    }

    /// La mise en page a changé (la liste n'est plus la même) : la position de repos est relue au prochain mouvement.
    func resetBaseline() {
        baseline = nil
        lastScrolled = 0
    }
}
