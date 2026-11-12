import SwiftUI

/// Les gestes de l'onglet Profil.
struct ProfileTabActions {
    let onOpenMyInfo: () -> Void
    let onOpenSettings: () -> Void
    let onRetry: () -> Void
    let onOpenMiff: (String) -> Void
    /// « Créer une Miff » (dernière carte du carrousel) : l'hôte choisit entre l'offre (compte gratuit qui a déjà une Miff) et le parcours.
    let onCreateMiff: () -> Void
}

/// Ce que l'onglet Profil sait de la personne et des gestes pour la liste des lieux sous le carrousel.
struct ProfilePlacesContext {
    /// La personne connectée : elle est comparée au créateur de la Miff pour savoir quels boutons dessiner.
    var myUid: String?
    var actions = ProfilePlacesActions.none
}

/// L'onglet Profil (maquette « profil », ticket Onboarding §6.8 et décision Q2 du ticket Feed / Profil / Réglages) : la photo (ou les
/// initiales), « Prénom Nom », « C'est toi » et l'engrenage qui ouvre les Réglages ; en dessous, « Mes Miffs » : un carrousel, une carte
/// par Miff (nom, nombre de membres, miniatures des membres ; elle ouvre l'écran de la Miff) et, en dernier, « Créer une Miff ». Un
/// toucher sur la photo ou le nom ouvre « Mes informations ». Aucune donnée inventée : pas de « Famille » par défaut.
/// Sous le carrousel, les lieux de confiance de la Miff dont la carte est montrée, visibles par TOUS ses membres (décision du PM, 2026-11-11).
struct ProfileTabView: View {
    let state: ProfileTabState
    let miffs: DirectoryState
    /// Les membres des cartes (la Miff du Feed : ce que le Feed a déjà lu ; les autres : une lecture ponctuelle à l'apparition).
    @ObservedObject var previews: MiffMemberPreviews
    /// Vrai quand l'onglet Profil est celui en usage : aucune lecture de membres avant.
    let isActive: Bool
    /// La carte montrée au départ (nil : la Miff du Feed ; les écrans de démonstration des captures en choisissent une autre).
    var initialCarouselPage: Int?
    /// MODE VOYAGE (VOY-2I) : la carte « Mode voyage », en DERNIÈRE position de l'écran (après tous les autres blocs), ouverte à tous les comptes.
    @ObservedObject var travel: TravelViewModel
    /// Les lieux de la Miff du carrousel (lus une fois par Miff, sans écouteur) et le message calme d'un geste.
    @ObservedObject var placesModel: ProfilePlacesViewModel
    @ObservedObject var placeNotices: PlaceNotices
    let placesContext: ProfilePlacesContext
    let actions: ProfileTabActions

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                Group {
                    header
                    if state == .unavailable {
                        unavailable
                    }
                }
                .padding(.horizontal, Spacing.lg)
                miffCards
                placesSection
                // MODE VOYAGE : l'encart est le DERNIER bloc de l'écran (décision du PM après la recette du build 26).
                TravelCardView(model: travel)
            }
            .padding(.vertical, Spacing.md)
        }
        .background(gradientProfileHeader.ignoresSafeArea())
        .accessibilityElement(children: .contain)
        .accessibilityLabel(Text("profile_tab_pane_a11y"))
        // Retour d'un lieu ou d'un formulaire : la liste regardée est relue si elle est périmée. Départ de l'onglet : le message calme s'en va.
        .onAppear {
            if isActive {
                placesContext.actions.onAppear()
            }
        }
        .onChange(of: isActive) { active in
            if !active {
                placesContext.actions.onLeave()
            }
        }
    }

    private var header: some View {
        HStack(spacing: Spacing.sm) {
            switch state {
            case .ready(let profile):
                identity(profile)
            case .loading:
                LoadingIndicator()
            case .unavailable:
                Spacer(minLength: 0)
            }
            gearButton
        }
    }

    private func identity(_ profile: ProfileSummary) -> some View {
        Button(action: actions.onOpenMyInfo) {
            HStack(spacing: Spacing.md) {
                MiffAvatar(jpeg: profile.avatarJpeg, initials: profile.initials, size: 80)
                VStack(alignment: .leading, spacing: 0) {
                    Text(verbatim: profile.displayName)
                        .miffTextStyle(textDisplayHeadline)
                        .foregroundColor(colorTextForm.color)
                        .accessibilityAddTraits(.isHeader)
                    Text("profile_tab_you_label")
                        .miffTextStyle(textBodyRegular)
                        .foregroundColor(colorTextDescription.color)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
            }
            .frame(minHeight: Spacing.minTouchTarget)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: "\(profile.displayName), \(L10n.string("profile_tab_you_label"))"))
        .accessibilityHint(Text("profile_tab_open_my_info_a11y"))
        .accessibilityAddTraits(.isButton)
    }

    /// L'engrenage des maquettes : un disque blanc, un engrenage bleu, 44 pt au moins.
    private var gearButton: some View {
        Button(action: actions.onOpenSettings) {
            Image(systemName: "gearshape")
                .font(.system(size: Spacing.sectionIcon, weight: .regular))
                .foregroundColor(colorActionPrimary.color)
                .frame(width: Spacing.minTouchTarget, height: Spacing.minTouchTarget)
                .background(Circle().fill(colorBgSurface.color))
                .miffShadow(shadowDefault)
        }
        .accessibilityLabel(Text("profile_tab_settings_a11y"))
    }

    private var unavailable: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text("profile_tab_unavailable")
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextDescription.color)
            Button(action: actions.onRetry) {
                Text("auth_retry_action")
            }
            .buttonStyle(.miffSecondaryFullWidth)
        }
    }

    /// « Mes Miffs » : le carrousel (une carte par Miff, puis « Créer une Miff »). Le titre garde les marges de l'écran ; le carrousel, lui,
    /// a les siennes (ses cartes sont pleine largeur, ombre comprise).
    @ViewBuilder
    private var miffCards: some View {
        if case .ready(let ready) = miffs {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                Text("profile_tab_miffs_title")
                    .miffTextStyle(textDisplayTitle)
                    .foregroundColor(colorTextForm.color)
                    .accessibilityAddTraits(.isHeader)
                    .padding(.horizontal, Spacing.lg)
                ProfileMiffCarousel(
                    content: ProfileCarouselContent(
                        miffs: ready.miffs, activeId: ready.activeId, initialPage: initialCarouselPage
                    ),
                    previews: previews,
                    isActive: isActive,
                    actions: ProfileCarouselActions(
                        onOpenMiff: actions.onOpenMiff,
                        onCreate: actions.onCreateMiff,
                        onMiffShown: { placesModel.onCardShown(miffId: $0) },
                        onMiffsKnown: { placesModel.retainOnly($0) }
                    )
                )
                .padding(.horizontal, Spacing.lg)
            }
        }
    }

    /// Les lieux de la Miff du carrousel, pour tous ses membres. Rien n'est dessiné (pas même une marge) tant qu'ils n'ont pas été lus.
    @ViewBuilder
    private var placesSection: some View {
        if case .ready(let ready) = miffs,
           let presentation = ProfilePlacesPresentation.make(
               state: placesModel.state, miffs: ready.miffs, myUid: placesContext.myUid
           ) {
            ProfilePlacesSection(
                presentation: presentation,
                // Miff+ n'est affirmé (ou nié) que s'il a été LU auprès du serveur : sinon nil, et la liste n'affirme rien de la formule gratuite.
                premium: ready.premiumKnown ? ready.premium : nil,
                notice: placeNotices.notice,
                actions: placesContext.actions
            )
            .padding(.horizontal, Spacing.lg)
        }
    }
}
