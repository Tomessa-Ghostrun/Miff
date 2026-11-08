import SwiftUI

/// Le fond de la barre : verre natif (iOS 26 et plus, `glassEffect`), matériau translucide + voile (iOS 16 à 25), ou fond opaque
/// (« Réduire la transparence », « Augmenter le contraste »). Un SEUL élément de verre : le bouton central est opaque et hors du
/// verre (jamais du verre sur du verre).
struct GlassBarSurface: ViewModifier {
    let level: GlassLevel
    let variant: TabBarVariant

    private var shape: RoundedRectangle {
        RoundedRectangle(cornerRadius: TabBarLayout.cornerRadius, style: .continuous)
    }

    func body(content: Content) -> some View {
        surface(content)
            .shadow(color: tabBarShadowColor.color, radius: tabBarShadowRadius, x: 0, y: tabBarShadowOffsetY)
    }

    @ViewBuilder
    private func surface(_ content: Content) -> some View {
        switch level {
        case .native:
            nativeSurface(content)
        case .material:
            materialSurface(content)
        case .opaque:
            content
                .background(shape.fill(variant.palette.opaque.color))
        }
    }

    @ViewBuilder
    private func nativeSurface(_ content: Content) -> some View {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            content
                .glassEffect(.regular, in: shape)
        } else {
            materialSurface(content)
        }
        #else
        materialSurface(content)
        #endif
    }

    private func materialSurface(_ content: Content) -> some View {
        content
            .background(
                ZStack {
                    shape.fill(.regularMaterial)
                    shape.fill(variant.palette.tint.color)
                }
                .environment(\.colorScheme, variant == .light ? .light : .dark)
            )
            .overlay(shape.strokeBorder(variant.palette.border.color, lineWidth: strokeHairline))
    }
}

/// La barre d'onglets personnalisée : trois entrées de 64 x 48 pt dans une barre flottante de 64 pt (marges latérales de 24 pt,
/// arrondi 20). Personnalisée plutôt qu'un `TabView` parce que le bouton central n'est pas un onglet de contenu et porte une couleur de
/// statut, et que la forme des maquettes doit être la même sur toutes les versions d'iOS.
struct MainTabBar: View {
    let config: TabBarConfig
    let selected: MainTab
    var central: CentralButtonState = .neutral
    var variant: TabBarVariant = .light
    let onSelect: (MainTab) -> Void

    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @Environment(\.colorSchemeContrast) private var contrast

    private var level: GlassLevel {
        GlassPolicy.level(
            nativeAvailable: GlassPolicy.nativeAvailable,
            reduceTransparency: reduceTransparency,
            increasedContrast: contrast == .increased
        )
    }

    var body: some View {
        HStack(spacing: 0) {
            ForEach(config.tabs, id: \.self) { tab in
                entry(for: tab)
            }
        }
        .frame(height: TabBarLayout.barHeight)
        .modifier(GlassBarSurface(level: level, variant: variant))
        .padding(.horizontal, TabBarLayout.sideMargin)
    }

    private func entry(for tab: MainTab) -> some View {
        let isSelected = tab == selected
        return Button {
            onSelect(tab)
        } label: {
            ZStack {
                if isSelected {
                    RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                        .fill(variant.palette.pillActive.color)
                        .frame(width: TabBarLayout.entryWidth, height: TabBarLayout.entryHeight)
                }
                icon(for: tab, isSelected: isSelected)
            }
            .frame(maxWidth: .infinity, minHeight: TabBarLayout.barHeight)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(verbatim: L10n.string(TabBarNames.key(for: tab, central: central))))
        .accessibilityAddTraits(.isButton)
        .accessibilityAddTraits(isSelected ? AccessibilityTraits.isSelected : AccessibilityTraits())
    }

    @ViewBuilder
    private func icon(for tab: MainTab, isSelected: Bool) -> some View {
        let color = (isSelected ? variant.palette.iconActive : variant.palette.iconInactive).color
        switch tab {
        case .feed:
            // La maison avec un petit coeur des maquettes : dessinée avec deux symboles du système, au trait.
            ZStack {
                Image(systemName: "house")
                    .font(.system(size: TabBarLayout.iconSize, weight: .regular))
                Image(systemName: "heart")
                    .font(.system(size: 9, weight: .bold))
                    .offset(y: 3)
            }
            .foregroundColor(color)
            .accessibilityHidden(true)
        case .update:
            CentralStatusButton(state: central)
                .accessibilityHidden(true)
        case .profile:
            Image(systemName: "person")
                .font(.system(size: TabBarLayout.iconSize, weight: .regular))
                .foregroundColor(color)
                .accessibilityHidden(true)
        }
    }
}

/// Le bouton central : plaque blanche opaque de 44 pt, anneau de 3 pt, pastille de statut de 24 pt avec 3 pt d'air blanc, SANS
/// pictogramme. La couleur est le seul signal : le sens passe par le nom parlé du bouton.
struct CentralStatusButton: View {
    let state: CentralButtonState

    var body: some View {
        ZStack {
            Circle()
                .fill(colorBgSurface.color)
                .frame(width: TabBarLayout.plateSize, height: TabBarLayout.plateSize)
                .shadow(color: tabBarPlateShadowColor.color, radius: 2, x: 0, y: 1)
            Circle()
                .strokeBorder(state.ringColor.color, lineWidth: TabBarLayout.ringWidth)
                .frame(width: TabBarLayout.ringSize, height: TabBarLayout.ringSize)
            Circle()
                .fill(state.dotColor.color)
                .frame(width: TabBarLayout.dotSize, height: TabBarLayout.dotSize)
        }
    }
}
