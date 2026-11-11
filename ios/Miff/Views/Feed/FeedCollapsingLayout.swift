import SwiftUI

// La mise en page du Feed : l'en-tête (avatar, logo, pastille de la Miff) PAR-DESSUS le contenu, qui se masque quand on descend dans la liste et
// revient dès qu'on remonte (D41), et le contenu qui commence EXACTEMENT sous lui. Extraite de `FeedScreen` pour que l'écran réel ET l'aperçu des
// captures de la CI (`FeedScreenPreview`) passent par le MÊME code de mise en page.

/// Ce que la mise en page donne au contenu.
struct FeedLayoutContext {
    /// La hauteur MESURÉE de l'en-tête.
    let headerHeight: CGFloat
    /// La marge haute à donner au contenu d'une vue de défilement : son haut tombe exactement au bas de l'en-tête (`FeedTopInset`).
    let listInset: CGFloat
    /// Monte d'un cran quand le logo est touché : la liste remonte tout en haut.
    let scrollToTop: Int
    let onContentMoved: (CGFloat) -> Void
    let onScrolled: (CGFloat) -> Void
    /// L'origine du contenu de la vue de défilement, dans la fenêtre (lue par `ScrollDistanceReader`).
    let onOrigin: (CGFloat) -> Void
}

/// La durée du retour animé de l'en-tête (un toucher sur le logo, un verrouillage) : courte.
enum FeedHeaderMotion {
    static let showSeconds: Double = 0.2
}

/// Le bas de l'en-tête AU REPOS (sans son déplacement), dans la fenêtre.
struct HeaderRestBottomKey: PreferenceKey {
    static var defaultValue: CGFloat?

    static func reduce(value: inout CGFloat?, nextValue: () -> CGFloat?) {
        value = nextValue() ?? value
    }
}

struct HeaderHeightKey: PreferenceKey {
    static var defaultValue: CGFloat = 0

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}

struct FeedCollapsingLayout<Content: View>: View {
    /// L'en-tête reste visible quoi que fasse le défilement (chargement, hors connexion, lecteur d'écran, sélecteur ouvert).
    let locked: Bool
    /// La liste est celle des cartes (sinon : squelette, erreur, état vide).
    let isList: Bool
    let refreshing: Bool
    /// Construit l'en-tête ; reçoit l'action du logo (remonter en haut et montrer l'en-tête).
    let header: (@escaping () -> Void) -> FeedHeaderContent
    @ViewBuilder let content: (FeedLayoutContext) -> Content

    @State private var headerModel = FeedHeaderModel()
    @State private var headerHeight: CGFloat = 0
    @State private var headerRestBottom: CGFloat?
    @State private var contentOrigin: CGFloat?
    @State private var scrollToTop = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack(alignment: .top) {
            content(
                FeedLayoutContext(
                    headerHeight: headerHeight,
                    listInset: FeedTopInset.listInset(
                        headerBottom: headerRestBottom,
                        contentOrigin: contentOrigin,
                        fallback: headerHeight
                    ),
                    scrollToTop: scrollToTop,
                    onContentMoved: { headerModel.contentMoved(to: $0) },
                    onScrolled: { headerModel.scrolled(to: $0) },
                    onOrigin: { origin in
                        if FeedTopInset.isNewMeasure(origin, old: contentOrigin) {
                            contentOrigin = origin
                        }
                    }
                )
            )
            // Le contenu ne passe JAMAIS sous la barre d'état : une bande fixe de la couleur du fond couvre la zone sûre du haut, même quand
            // l'en-tête est caché (comme Android, qui coupe le contenu à la zone sûre).
            colorBgCanvas.color.ignoresSafeArea(edges: .top).frame(height: 0)
            headerView
            if refreshing {
                // Le geste est en haut de la liste, sous l'en-tête : l'indicateur de l'actualisation se montre sous lui.
                ProgressView()
                    .tint(colorActionPrimary.color)
                    .padding(.top, headerHeight + Spacing.sm)
                    .accessibilityHidden(true)
            }
        }
        .background(colorBgCanvas.color.ignoresSafeArea())
        .onPreferenceChange(HeaderHeightKey.self) { height in
            headerHeight = height
            headerModel.measured(height)
        }
        .onPreferenceChange(HeaderRestBottomKey.self) { bottom in
            if let bottom, FeedTopInset.isNewMeasure(bottom, old: headerRestBottom) {
                headerRestBottom = bottom
            }
        }
        .onAppear { headerModel.lock(locked) }
        // Verrouillé : l'en-tête revient avec une courte animation, immédiate avec « Réduire les animations ».
        .onChange(of: locked) { value in withHeaderAnimation { headerModel.lock(value) } }
        .onChange(of: isList) { _ in headerModel.resetBaseline() }
    }

    /// L'animation courte du retour de l'en-tête ; aucune avec « Réduire les animations ».
    private func withHeaderAnimation(_ change: () -> Void) {
        if reduceMotion {
            change()
        } else {
            withAnimation(.easeOut(duration: FeedHeaderMotion.showSeconds), change)
        }
    }

    private var headerView: some View {
        FeedHeaderOverlay(
            model: headerModel,
            content: header {
                withHeaderAnimation { headerModel.show() }
                scrollToTop += 1
            }
        )
        // L'en-tête qui monte est COUPÉ à la zone sûre (il ne passe pas sur l'heure de la barre d'état) ; le masque descend un peu plus bas
        // pour laisser voir son ombre.
        .mask(alignment: .top) { Rectangle().padding(.bottom, -Spacing.md) }
        .accessibilitySortPriority(1)
    }
}

/// L'en-tête par-dessus la liste : déplacé par une translation (aucune mise en page ne bouge), mesuré pour que la liste lui laisse sa place.
/// Le conteneur extérieur ne bouge JAMAIS : c'est lui qui mesure le bas de l'en-tête au repos (dans la fenêtre) ; seul le contenu glisse.
private struct FeedHeaderOverlay: View {
    @ObservedObject var model: FeedHeaderModel
    let content: FeedHeaderContent

    var body: some View {
        ZStack(alignment: .top) {
            content
                .background(
                    GeometryReader { proxy in
                        Color.clear.preference(key: HeaderHeightKey.self, value: proxy.size.height)
                    }
                )
                .background(
                    colorBgCanvas.color
                        .ignoresSafeArea(edges: .top)
                        .miffShadow(shadowSubtle)
                )
                .offset(y: model.state.offset)
        }
        .background(
            GeometryReader { proxy in
                Color.clear.preference(key: HeaderRestBottomKey.self, value: proxy.frame(in: .global).maxY)
            }
        )
    }
}
