import SwiftUI

/// Les gestes de l'onglet Profil.
struct ProfileTabActions {
    let onOpenMyInfo: () -> Void
    let onOpenSettings: () -> Void
    let onRetry: () -> Void
    let onOpenMiff: (String) -> Void
}

/// L'onglet Profil (maquette « profil », ticket Onboarding §6.8 et décision Q2 du ticket Feed / Profil / Réglages) : la photo (ou les
/// initiales), « Prénom Nom », « C'est toi » et l'engrenage qui ouvre les Réglages ; en dessous, « Mes Miffs » : la carte de chaque Miff
/// ouvre son écran. Un toucher sur la photo ou le nom ouvre « Mes informations ». Aucune donnée inventée : pas de « Famille » par défaut.
/// Les lieux de la Miff restent dans l'écran de la Miff (ticket Lieux iOS), pas ici.
struct ProfileTabView: View {
    let state: ProfileTabState
    let miffs: DirectoryState
    let actions: ProfileTabActions

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                header
                if state == .unavailable {
                    unavailable
                }
                miffCards
            }
            .padding(.horizontal, Spacing.lg)
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

    /// « Mes Miffs » : une carte par Miff ; le nom seulement (pas de nombre de membres ni de photos tant que le Feed et le Profil
    /// complets n'existent pas sur iPhone).
    @ViewBuilder
    private var miffCards: some View {
        if case .ready(let ready) = miffs, !ready.miffs.isEmpty {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                Text("profile_tab_miffs_title")
                    .miffTextStyle(textDisplayTitle)
                    .foregroundColor(colorTextForm.color)
                    .accessibilityAddTraits(.isHeader)
                ForEach(ready.miffs, id: \.id) { miff in
                    MiffCard(name: miff.name, action: { actions.onOpenMiff(miff.id) })
                }
            }
        }
    }
}

/// La carte d'une Miff : son nom, une courte aide, la flèche. Ouvre l'écran de la Miff.
private struct MiffCard: View {
    let name: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.sm) {
                VStack(alignment: .leading, spacing: 0) {
                    Text(verbatim: name)
                        .miffTextStyle(textBodyBold16)
                        .foregroundColor(colorTextForm.color)
                    Text("profile_tab_miff_hint")
                        .miffTextStyle(textBodyRegular14)
                        .foregroundColor(colorTextDescription.color)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
                Image(systemName: "chevron.forward")
                    .foregroundColor(colorActionPrimary.color)
                    .accessibilityHidden(true)
            }
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm)
            .frame(minHeight: Spacing.minTouchTarget)
            .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
            .miffShadow(shadowDefault)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: L10n.format("profile_tab_miff_open_a11y", name)))
        .accessibilityAddTraits(.isButton)
    }
}

/// Le bouton central, pour l'instant : un emplacement calme. « Donner des nouvelles » (ticket Update, UPDATE-3) viendra sur cette
/// coquille ; en attendant, la pastille de la barre est grise et cet écran dit honnêtement que la fonction arrive.
struct UpdateSoonView: View {
    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.md) {
                Text("ios_update_soon_title")
                    .miffTextStyle(textDisplayHeadline)
                    .foregroundColor(colorTextForm.color)
                    .multilineTextAlignment(.center)
                    .accessibilityAddTraits(.isHeader)
                Text("ios_update_soon_body")
                    .miffTextStyle(textBodyRegular)
                    .foregroundColor(colorTextDescription.color)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.xl)
        }
        .background(colorBgCanvas.color.ignoresSafeArea())
    }
}
