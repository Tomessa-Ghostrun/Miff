import SwiftUI

/// Carrousel de bienvenue (maquettes « Welcoming screen », « -16 », « -17 » de design/exports/onboarding-original ; ticket
/// Onboarding §6.1), montré avant l'inscription à la TOUTE PREMIÈRE ouverture seulement.
///
/// Fidèle aux maquettes : le mot-symbole en haut, les formes vert pâle et l'illustration de chaque slide, trois points,
/// la phrase « Savoir qu'ils vont bien, sans les déranger » (la même sur les trois slides, il n'y a aucune autre phrase) et
/// le bouton « Commencer ». Tout est placé dans les unités des maquettes (320 de large) multipliées par l'échelle de
/// l'écran (une unité = largeur de l'écran / 320).
///
/// - Défilement manuel seulement (balayage, ou actions du lecteur d'écran) : aucun défilement automatique ;
/// - aucun événement Analytics : cet écran n'a pas accès au traceur (le consentement n'est demandé qu'à l'inscription) ;
/// - « Page 1 sur 3 » est annoncé par le lecteur d'écran à chaque changement de page ; « Commencer » est sur chaque slide ;
/// - chaque illustration est décrite par UNE phrase pour le lecteur d'écran et ses textes intérieurs lui sont cachés.
@MainActor
struct WelcomeCarouselView: View {
    let onStart: () -> Void
    let onHaveAccount: () -> Void

    @State private var page: Int
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(initialPage: Int = 0, onStart: @escaping () -> Void, onHaveAccount: @escaping () -> Void) {
        self.onStart = onStart
        self.onHaveAccount = onHaveAccount
        _page = State(initialValue: initialPage)
    }

    var body: some View {
        GeometryReader { proxy in
            let unit = proxy.size.width / CGFloat(WelcomeBlobs.unitsWidth)
            ScrollView(showsIndicators: false) {
                VStack(spacing: 0) {
                    pager(unit: unit)
                    Text("onboarding_headline")
                        .miffTextStyle(textTitleBold20)
                        .miffBrandTitleFill()
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                        .padding(.horizontal, Spacing.lg)
                        .padding(.top, Spacing.sm)
                    Spacer().frame(height: Spacing.md)
                    Button("onboarding_start", action: onStart)
                        .buttonStyle(.miffPrimaryFullWidth)
                        .padding(.horizontal, Spacing.lg)
                    haveAccountLink
                }
            }
            .ignoresSafeArea(edges: .top)
        }
        .background(colorBgCanvas.color.ignoresSafeArea())
    }

    /// La zone des illustrations : formes, illustration de la page, mot-symbole en haut et points en bas.
    private func pager(unit: CGFloat) -> some View {
        let topInset = PresentationAnchor.keyWindow()?.safeAreaInsets.top ?? 0
        let wordmarkTop = max(unit * 48, topInset + Spacing.sm)
        return ZStack(alignment: .top) {
            TabView(selection: $page) {
                ForEach(0..<WelcomeBlobs.pageCount, id: \.self) { index in
                    CarouselPage(page: index, unit: unit)
                        .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            MiffWordmark()
                .frame(width: unit * 90)
                .padding(.top, wordmarkTop)
            pageDots(unit: unit)
                .frame(maxHeight: .infinity, alignment: .bottom)
                .padding(.bottom, unit * 36)
        }
        .frame(height: unit * CGFloat(WelcomeBlobs.unitsHeight))
    }

    /// Les points de pagination, annoncés « Page 1 sur 3 » (les points eux-mêmes sont décoratifs).
    private func pageDots(unit: CGFloat) -> some View {
        HStack(spacing: unit * 7) {
            ForEach(0..<WelcomeBlobs.pageCount, id: \.self) { index in
                Circle()
                    .fill((index == page ? colorActionPrimary : colorBorderSubtle).color)
                    .frame(width: unit * 8, height: unit * 8)
                    .animation(reduceMotion ? nil : .easeOut(duration: 0.2), value: page)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: pageDescription))
        .onChange(of: page) { _ in
            Announcer.announce(pageDescription)
        }
    }

    private var pageDescription: String {
        L10n.format("onboarding_page_a11y", Int32(page + 1), Int32(WelcomeBlobs.pageCount))
    }

    private var haveAccountLink: some View {
        Button(action: onHaveAccount) {
            Text("onboarding_have_account")
                .miffTextStyle(textBodyBold16)
                .underline()
                .foregroundColor(colorActionPrimary.color)
                .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget)
        }
    }
}

/// Une slide : les formes vert pâle, puis l'illustration à l'emplacement de la maquette.
private struct CarouselPage: View {
    let page: Int
    let unit: CGFloat

    private var descriptionKey: String {
        switch page {
        case 0:
            return "onboarding_slide1_illustration_a11y"
        case 1:
            return "onboarding_slide2_illustration_a11y"
        default:
            return "onboarding_slide3_illustration_a11y"
        }
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            BlobShape(page: page)
                .fill(colorStatusSafeTint.color)
            illustration
        }
        .frame(
            width: unit * CGFloat(WelcomeBlobs.unitsWidth),
            height: unit * CGFloat(WelcomeBlobs.unitsHeight),
            alignment: .topLeading
        )
        // Une phrase pour le lecteur d'écran ; les textes et photos dessinés à l'intérieur sont décoratifs.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: L10n.string(descriptionKey)))
    }

    @ViewBuilder
    private var illustration: some View {
        switch page {
        case 0:
            FeedIllustration(unit: unit)
        case 1:
            NewPlaceIllustration(unit: unit)
        default:
            MessagesIllustration(unit: unit)
        }
    }
}

/// Les formes vert pâle d'une slide : courbes lisses passant par les points tracés sur les maquettes.
struct BlobShape: Shape {
    let page: Int

    func path(in rect: CGRect) -> Path {
        let scale = rect.width / CGFloat(WelcomeBlobs.unitsWidth)
        func scaled(_ point: PathPoint) -> CGPoint {
            CGPoint(x: rect.minX + CGFloat(point.px) * scale, y: rect.minY + CGFloat(point.py) * scale)
        }
        func middle(_ first: CGPoint, _ second: CGPoint) -> CGPoint {
            CGPoint(x: (first.x + second.x) / 2, y: (first.y + second.y) / 2)
        }
        var path = Path()
        for outline in WelcomeBlobs.outlines(page: page) where outline.count >= 3 {
            let points = outline.map { scaled($0) }
            // Courbes quadratiques entre les milieux des côtés : un contour fermé lisse qui passe près des points tracés.
            path.move(to: middle(points[points.count - 1], points[0]))
            for index in points.indices {
                let control = points[index]
                let end = middle(control, points[(index + 1) % points.count])
                path.addQuadCurve(to: end, control: control)
            }
            path.closeSubpath()
        }
        return path
    }
}
