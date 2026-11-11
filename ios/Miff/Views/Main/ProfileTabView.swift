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

/// L'onglet Profil (maquette « profil », ticket Onboarding §6.8 et décision Q2 du ticket Feed / Profil / Réglages) : la photo (ou les
/// initiales), « Prénom Nom », « C'est toi » et l'engrenage qui ouvre les Réglages ; en dessous, « Mes Miffs » : un carrousel, une carte
/// par Miff (nom, nombre de membres, miniatures des membres ; elle ouvre l'écran de la Miff) et, en dernier, « Créer une Miff ». Un
/// toucher sur la photo ou le nom ouvre « Mes informations ». Aucune donnée inventée : pas de « Famille » par défaut.
/// Les lieux de la Miff restent dans l'écran de la Miff (ticket Lieux iOS), pas ici.
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
                // MODE VOYAGE : l'encart est le DERNIER bloc de l'écran (décision du PM après la recette du build 26).
                TravelCardView(model: travel)
            }
            .padding(.vertical, Spacing.md)
        }
        .background(gradientProfileHeader.ignoresSafeArea())
        .accessibilityElement(children: .contain)
        .accessibilityLabel(Text("profile_tab_pane_a11y"))
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
                    actions: ProfileCarouselActions(onOpenMiff: actions.onOpenMiff, onCreate: actions.onCreateMiff)
                )
                .padding(.horizontal, Spacing.lg)
            }
        }
    }
}
