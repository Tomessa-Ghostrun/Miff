import CoreGraphics
import Foundation

// La mise en page du viseur de l'Update (retour du PM, iPhone 16 : « l'écran n'est pas en full screen, il est coupé en haut »). Le viseur doit
// remplir TOUT l'écran, derrière la barre d'état et l'île dynamique, comme une app caméra. On ne se fie plus aux seules mesures du lecteur de
// géométrie de SwiftUI (qui, dans une pile de navigation ou un plein écran, peut annoncer des zones sûres nulles ou fausses sur un vrai appareil,
// voir `PhotoFullScreenLayout`) : la TAILLE de l'écran vient de la fenêtre, et l'aperçu de la caméra est posé sur le rectangle de la fenêtre.
// Du Swift pur : aucune mesure de vue, aucune lecture, aucune écriture.

enum ViewfinderLayout {
    /// Les quatre zones sûres, en points.
    struct Insets: Equatable {
        var top: CGFloat = 0
        var leading: CGFloat = 0
        var bottom: CGFloat = 0
        var trailing: CGFloat = 0
    }

    /// Plancher de la zone sûre du haut, en portrait, quand NI le lecteur de géométrie NI la fenêtre n'annoncent rien (l'encoche : 47 pt).
    /// Il ne vaut qu'à ce moment-là : dès qu'une vraie valeur est lue (île dynamique, encoche, barre d'état d'un iPhone SE), elle gagne.
    static let topInsetFloor: CGFloat = 47

    /// Ce que le dégradé sombre du haut garde sous la zone sûre (il couvre la barre d'état ET un peu de l'image, jamais une bande nette).
    static let gradientExtra: CGFloat = 40

    /// L'opacité du noir en haut du dégradé (une très légère ombre : la barre d'état est en clair, voir `AppAppearance`).
    static let gradientTopAlpha: Double = 0.35

    /// La taille de l'écran ENTIER : celle de la fenêtre quand elle est connue (c'est la vérité, quoi que disent les zones sûres du lecteur de
    /// géométrie), sinon la zone visible plus les zones sûres annoncées.
    static func screenSize(visible: CGSize, insets: Insets, window: CGSize?) -> CGSize {
        if let window, window.width > 0, window.height > 0 {
            return window
        }
        return PhotoFullScreenLayout.screenSize(
            visible: visible,
            top: insets.top, bottom: insets.bottom, leading: insets.leading, trailing: insets.trailing
        )
    }

    /// La zone sûre du haut à respecter, à partir de `readTop` (la plus grande de celle du lecteur et de celle de la fenêtre, voir
    /// `ViewfinderWindow`) ; si elle est nulle, le plancher de l'encoche en portrait (en paysage il n'y a rien en haut).
    static func topInset(readTop: CGFloat, isPortrait: Bool) -> CGFloat {
        if readTop > 0 {
            return readTop
        }
        return isPortrait ? topInsetFloor : 0
    }

    /// Ce qu'il faut AJOUTER en haut d'un contenu que SwiftUI a déjà posé `layoutTop` sous le bord de l'écran, pour qu'il soit au moins à
    /// `topInset` : 0 dans le cas normal, jamais négatif.
    static func extraTopPadding(topInset: CGFloat, layoutTop: CGFloat) -> CGFloat {
        max(0, topInset - max(layoutTop, 0))
    }

    /// La hauteur du dégradé sombre du haut : la zone sûre et un peu d'image en dessous.
    static func gradientHeight(topInset: CGFloat) -> CGFloat {
        max(topInset, 0) + gradientExtra
    }

    /// Le plus grand rectangle de ratio `aspect` (largeur / hauteur) dans `full` : quand l'écran est hors des bornes 9:21 à 9:16 (tablette,
    /// écran partagé), le viseur se limite à la zone bornée, centrée, avec des bandes sombres. Sur un iPhone, il vaut l'écran entier.
    static func boundedBox(full: CGSize, aspect: Double) -> CGSize {
        guard full.width > 0, full.height > 0, aspect > 0 else {
            return full
        }
        if Double(full.width / full.height) > aspect {
            return CGSize(width: full.height * CGFloat(aspect), height: full.height)
        }
        return CGSize(width: full.width, height: full.width / CGFloat(aspect))
    }

    /// Le rectangle de l'aperçu de la caméra dans le repère de la fenêtre (ou de l'écran) `container` : la zone bornée, CENTRÉE, donc l'écran
    /// entier sur un iPhone, zones sûres comprises.
    static func previewRect(in container: CGRect, aspect: Double) -> CGRect {
        let box = boundedBox(full: container.size, aspect: aspect)
        return CGRect(
            x: container.minX + (container.width - box.width) / 2,
            y: container.minY + (container.height - box.height) / 2,
            width: box.width,
            height: box.height
        )
    }

    /// Vrai quand l'écran est plus haut que large (le plancher du haut ne vaut qu'alors).
    static func isPortrait(_ size: CGSize) -> Bool {
        size.height >= size.width
    }
}
