import SwiftUI

/// L'écran d'offre Miff+ (ticket `paywall-v2.md`, maquettes `design/exports/paywall/`) : le MÊME pour tous les points d'entrée
/// (4e lieu, zone de danger, 2e Miff) et tous les états. Il est présenté en plein écran par l'hôte (`PaywallPresentation`).
///
/// LA CROIX (règle du PM du 2026-11-12, §6) : absente pendant `PaywallTiming.closeDelay` secondes (ni lue par VoiceOver, ni atteignable
/// au clavier ; son emplacement est réservé, rien ne bouge), puis elle apparaît en fondu et reste. Pendant ce temps le glissement
/// de fermeture de la feuille est désactivé. L'écran n'a AUCUN autre chemin de sortie.
struct PaywallScreen: View {
    @ObservedObject var model: PaywallViewModel
    let actions: PaywallActions
    let onClose: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dynamicTypeSize) private var typeSize
    @Environment(\.scenePhase) private var scenePhase

    init(model: PaywallViewModel, actions: PaywallActions = .none, onClose: @escaping () -> Void) {
        self.model = model
        self.actions = actions
        self.onClose = onClose
    }

    /// Aux très grandes tailles de texte la zone d'achat ne reste pas fixée en bas (elle prendrait l'écran) : elle défile avec le reste.
    private var pinsPurchaseArea: Bool {
        !typeSize.isAccessibilitySize
    }

    var body: some View {
        VStack(spacing: 0) {
            PaywallTopBar(isCloseAvailable: model.isCloseAvailable, reduceMotion: reduceMotion, onClose: onClose)
            ScrollView {
                VStack(spacing: Spacing.md) {
                    content
                    if !pinsPurchaseArea {
                        PaywallPurchaseArea(model: model, actions: actions, onClose: onClose)
                    }
                    PaywallInformationView(model: model, actions: actions)
                }
                .padding(.horizontal, Spacing.lg)
                .padding(.vertical, Spacing.sm)
            }
            if pinsPurchaseArea {
                PaywallPurchaseArea(model: model, actions: actions, onClose: onClose)
                    .padding(.horizontal, Spacing.lg)
                    .padding(.vertical, Spacing.sm)
            }
        }
        .background(gradientAuthBackground.ignoresSafeArea())
        // Glissement de fermeture de la feuille : désactivé tant que la croix n'est pas là (critère CR7), actif ensuite.
        .interactiveDismissDisabled(!model.isCloseAvailable)
        // Le compte à rebours part à l'affichage, même si les prix chargent encore ou s'il n'y a pas de connexion (CR3, CR13).
        .task {
            await model.runCloseTimer()
        }
        // Retour de l'arrière-plan : on relit l'horloge, on ne relance rien (CR5).
        .onChange(of: scenePhase) { phase in
            if phase == .active {
                model.tick()
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        if model.state == .subscribed {
            PaywallSubscribedContent()
        } else {
            Text("place_paywall_headline")
                .miffTextStyle(textTitleBold24)
                .miffBrandTitleFill()
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .accessibilityAddTraits(.isHeader)
            PlacePaywallFeature(
                symbol: "mappin.and.ellipse",
                title: L10n.string("place_paywall_feature_places"),
                detail: L10n.string("place_paywall_feature_places_desc")
            )
            PlacePaywallFeature(
                symbol: "person.2",
                title: L10n.string("place_paywall_feature_miffs"),
                detail: L10n.string("place_paywall_feature_miffs_desc")
            )
            PlacePaywallFeature(
                symbol: "exclamationmark.triangle",
                title: L10n.string("place_paywall_feature_danger"),
                detail: L10n.string("place_paywall_feature_danger_desc")
            )
        }
    }
}

/// Le haut de l'écran : le logo au centre, la croix au début de la ligne. Son emplacement (44 x 44 pt) est TOUJOURS réservé.
struct PaywallTopBar: View {
    let isCloseAvailable: Bool
    let reduceMotion: Bool
    let onClose: () -> Void

    var body: some View {
        ZStack {
            MiffWordmark()
                .frame(width: Spacing.logoWidth / 2)
            HStack {
                closeSlot
                Spacer()
            }
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.vertical, Spacing.sm)
    }

    /// Avant l'échéance : seulement un emplacement vide, caché des lecteurs d'écran et sans toucher (la croix n'est PAS dans
    /// l'arbre d'accessibilité, ni atteignable au clavier : elle n'existe pas). Après : la croix, en fondu (instantanée si la
    /// personne a réduit les animations). Aucune pulsation, aucun changement de couleur.
    private var closeSlot: some View {
        ZStack {
            Color.clear
                .frame(width: Spacing.minTouchTarget, height: Spacing.minTouchTarget)
                .accessibilityHidden(true)
                .allowsHitTesting(false)
            if isCloseAvailable {
                PaywallCloseButton(action: onClose)
                    .transition(.opacity)
            }
        }
        .animation(reduceMotion ? nil : .easeIn(duration: PaywallTiming.closeFadeDuration), value: isCloseAvailable)
    }
}

/// La croix : un disque gris foncé (lisible sur le dégradé, écart E10 de la maquette) de 32 pt dans une zone tactile de 44 pt.
struct PaywallCloseButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "xmark")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(colorBgSurface.color)
                .frame(width: 32, height: 32)
                .background(Circle().fill(colorTextPrimary.color.opacity(0.65)))
                .frame(width: Spacing.minTouchTarget, height: Spacing.minTouchTarget)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(verbatim: L10n.string("place_paywall_close_a11y")))
    }
}

/// « Tu as Miff+. Merci ! » : une confirmation, aucune offre.
struct PaywallSubscribedContent: View {
    var body: some View {
        VStack(spacing: Spacing.sm) {
            Image(systemName: "checkmark.circle")
                .font(.system(size: 44, weight: .regular))
                .foregroundColor(colorActionPrimary.color)
                .accessibilityHidden(true)
            Text("paywall_subscribed")
                .miffTextStyle(textTitleBold24)
                .miffBrandTitleFill()
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            Text("paywall_subscribed_detail")
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextDescription.color)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Spacing.lg)
        .accessibilityElement(children: .combine)
    }
}
