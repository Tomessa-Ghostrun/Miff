import Foundation

/// Les trois destinations de la barre d'onglets (ticket Onboarding §6.7, décision Q1 du ticket Feed / Profil / Réglages) : le Feed au
/// début, le bouton rond au centre, le Profil à la fin. La barre est mise en page avec début et fin, jamais gauche et droite : le
/// système l'inverse en droite-à-gauche.
enum MainTab: Equatable, Hashable, CaseIterable {
    case feed
    /// « Donner des nouvelles » (ticket Update, UPDATE-3) : pour l'instant un emplacement calme « bientôt ».
    case update
    case profile
}

/// Ce que montre la barre. Le bouton central est VISIBLE dans les builds que l'équipe essaie elle-même (Debug, Internal) et ABSENT des
/// builds destinés à des testeurs extérieurs (Release : la barre n'a alors que deux onglets) : un bouton qui n'a pas encore de
/// fonction ne doit jamais atteindre quelqu'un qui ne sait pas pourquoi (décision Q1, 2026-10-30).
struct TabBarConfig: Equatable {
    let showsUpdateButton: Bool

    /// Les onglets dessinés, dans l'ordre.
    var tabs: [MainTab] {
        showsUpdateButton ? [.feed, .update, .profile] : [.feed, .profile]
    }

    static func forBuild(isInternalBuild: Bool) -> TabBarConfig {
        TabBarConfig(showsUpdateButton: isInternalBuild)
    }

    static var current: TabBarConfig {
        forBuild(isInternalBuild: BuildFlavor.includesDiagnostic)
    }
}

/// La couleur de la pastille du bouton central : celle du statut de la personne, SANS pictogramme (décision du PM, 2026-11-06). Grise
/// quand le statut est inconnu ou que le partage est arrêté. Rien n'alimente encore les trois couleurs : le bouton reste gris
/// jusqu'à UPDATE-3.
enum CentralButtonState: Equatable, CaseIterable {
    case neutral
    case safe
    case warning
    case danger

    var dotColor: ColorToken {
        switch self {
        case .neutral:
            return tabBarIconInactiveLight
        case .safe:
            return colorStatusSafe
        case .warning:
            return colorStatusWarning
        case .danger:
            return colorStatusDanger
        }
    }

    /// L'anneau est bleu, sauf à l'état neutre où il est gris comme les autres boutons de la barre.
    var ringColor: ColorToken {
        self == .neutral ? tabBarIconInactiveLight : colorActionPrimary
    }
}

/// Les noms parlés (VoiceOver) de la barre : une clé du catalogue par onglet. L'état « sélectionné » est annoncé à part, jamais par la
/// couleur seule.
enum TabBarNames {
    static func key(for tab: MainTab) -> String {
        switch tab {
        case .feed:
            return "tabbar_feed_a11y"
        case .update:
            // Tant que le bouton ne fait pas encore ce qu'il promet, son nom le dit.
            return "tabbar_update_soon_a11y"
        case .profile:
            return "tabbar_profile_a11y"
        }
    }
}

/// Les mesures de la barre (design/tab-bar-liquid-glass.md §2) : les mêmes que celles d'Android, en points.
enum TabBarLayout {
    static let barHeight: CGFloat = 64
    static let entryWidth: CGFloat = 64
    static let entryHeight: CGFloat = 48
    static let sideMargin: CGFloat = 24
    static let bottomMargin: CGFloat = 22
    static let cornerRadius: CGFloat = 20
    static let iconSize: CGFloat = 24
    static let plateSize: CGFloat = 44
    static let ringSize: CGFloat = 36
    static let ringWidth: CGFloat = 3
    static let dotSize: CGFloat = 24

    /// La barre est posée à 22 pt du BAS DE L'ÉCRAN : sur un iPhone à zone sûre (34 pt) elle déborde donc un peu sur la zone de
    /// l'indicateur d'accueil (comme les barres d'iOS 26), sur un iPhone sans zone sûre elle garde ses 22 pt.
    static func bottomPadding(safeAreaBottom: CGFloat) -> CGFloat {
        bottomMargin - safeAreaBottom
    }
}

/// Le niveau de verre de la barre : natif (iOS 26 et plus), matériau translucide avec voile (iOS 16 à 25), ou opaque.
enum GlassLevel: Equatable {
    case native
    case material
    case opaque
}

enum GlassPolicy {
    /// Interrupteur de repli (une ligne) : à `false`, la barre n'utilise JAMAIS le verre natif, même sur iOS 26. Signal pour s'en
    /// servir : la recette du PM sur l'iPhone 11 Pro montre un rendu illisible ou saccadé.
    static let nativeGlassAllowed = true

    /// « Réduire la transparence » et « Augmenter le contraste » donnent toujours le fond opaque ; sinon le verre natif quand il existe.
    static func level(
        nativeAvailable: Bool,
        reduceTransparency: Bool,
        increasedContrast: Bool,
        nativeAllowed: Bool = nativeGlassAllowed
    ) -> GlassLevel {
        if reduceTransparency || increasedContrast {
            return .opaque
        }
        return nativeAvailable && nativeAllowed ? .native : .material
    }

    /// Le verre natif existe à partir d'iOS 26 ET quand l'app est compilée avec le SDK qui le connaît.
    static var nativeAvailable: Bool {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            return true
        }
        #endif
        return false
    }
}
