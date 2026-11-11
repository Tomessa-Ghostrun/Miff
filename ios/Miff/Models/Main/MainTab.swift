import Foundation

/// Les trois destinations de la barre d'onglets (ticket Onboarding §6.7, décision Q1 du ticket Feed / Profil / Réglages) : le Feed au
/// début, le bouton rond au centre, le Profil à la fin. La barre est mise en page avec début et fin, jamais gauche et droite : le
/// système l'inverse en droite-à-gauche.
enum MainTab: Equatable, Hashable, CaseIterable {
    case feed
    /// « Donner des nouvelles » (ticket Update, UPDATE-3) : le viseur, l'aperçu et la publication de la photo du moment.
    case update
    case profile
}

/// Ce que montre la barre. Le bouton central est ACTIF dans TOUS les builds depuis l'Update (UPDATE-3) : la décision Q1 du 2026-10-30
/// (« absent des builds de diffusion tant qu'il n'a pas de fonction ») est levée par le ticket Update (D14), comme sur Android.
/// `forBuild` est gardé pour que le choix reste une ligne.
struct TabBarConfig: Equatable {
    let showsUpdateButton: Bool

    /// Les onglets dessinés, dans l'ordre.
    var tabs: [MainTab] {
        showsUpdateButton ? [.feed, .update, .profile] : [.feed, .profile]
    }

    static func forBuild(isInternalBuild: Bool) -> TabBarConfig {
        TabBarConfig(showsUpdateButton: true)
    }

    static var current: TabBarConfig {
        forBuild(isInternalBuild: BuildFlavor.includesDiagnostic)
    }
}

/// La couleur de la pastille du bouton central : celle du statut de la personne, SANS pictogramme (décision du PM, 2026-11-06). Grise
/// quand le statut est inconnu ou que le partage est arrêté. Elle vient de `StatusColor.level` (UN seul calcul), via `init(level:)`.
enum CentralButtonState: Equatable, CaseIterable {
    case neutral
    case safe
    case warning
    case danger

    init(level: StatusLevel) {
        switch level {
        case .neutral:
            self = .neutral
        case .green:
            self = .safe
        case .orange:
            self = .warning
        case .red:
            self = .danger
        }
    }

    /// Le nom parlé du bouton central : il dit l'état (jamais la couleur seule, D25).
    var accessibilityKey: String {
        switch self {
        case .neutral:
            // Clé propre à iOS : « Share news » comme les trois autres noms (la clé partagée `tabbar_update_a11y` dit « Share an update »).
            return "ios_tabbar_update_neutral_a11y"
        case .safe:
            return "tabbar_update_green_a11y"
        case .warning:
            return "tabbar_update_orange_a11y"
        case .danger:
            return "tabbar_update_red_a11y"
        }
    }

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
/// couleur seule. Le nom du bouton central dit l'état de la personne (« Donner des nouvelles, tu es en orange »).
enum TabBarNames {
    static func key(for tab: MainTab, central: CentralButtonState = .neutral) -> String {
        switch tab {
        case .feed:
            return "tabbar_feed_a11y"
        case .update:
            return central.accessibilityKey
        case .profile:
            return "tabbar_profile_a11y"
        }
    }
}

/// Les pictogrammes de la barre : des symboles du système, au trait quand l'onglet est inactif, pleins quand il est actif (en plus de
/// la pastille et de la couleur, jamais la couleur seule). Le Feed est une MAISON CLASSIQUE (`house`), plus la maison avec un coeur
/// (décision du PM, 2026-11-08). Le bouton central n'a pas de pictogramme : il porte la couleur du statut.
enum TabBarSymbols {
    static func name(for tab: MainTab, isSelected: Bool) -> String? {
        switch tab {
        case .feed:
            return isSelected ? "house.fill" : "house"
        case .update:
            return nil
        case .profile:
            return isSelected ? "person.fill" : "person"
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

    /// Du bas de l'écran au haut de la barre, tel que le design la pose (sert aux tests de géométrie ; à l'écran, la coquille MESURE).
    static func topClearance(barHeight: CGFloat = TabBarLayout.barHeight) -> CGFloat {
        bottomMargin + barHeight
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
