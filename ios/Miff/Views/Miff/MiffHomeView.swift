import SwiftUI

/// Les gestes de l'accueil provisoire.
struct MiffHomeActions {
    let onRetry: () -> Void
    let onSelect: (String) -> Void
    let onCreate: () -> Void
    let onJoin: () -> Void
    let onOpenMiff: (String) -> Void
    let onSignOut: () -> Void
}

/// L'accueil de l'app une fois l'onboarding fini (destination « fil »), PROVISOIRE comme son équivalent d'Android avant le
/// ticket Feed. Ce que lui donne le ticket Miff (ticket §7 et décisions Q2 et Q12 du ticket Feed, maquettes « feed -2 » et « feed
/// -3 ») :
/// - la pastille de la Miff sous le mot-symbole : le NOM de la Miff, non touchable avec un compte gratuit (un compte gratuit
///   n'a qu'une Miff) ; un sélecteur avec « + Rejoindre une Miff » et « + Créer une Miff » pour un compte Premium ;
/// - la carte « Mes Miffs » qui ouvre l'écran de la Miff (sur Android elle est dans l'onglet Profil : iOS n'a pas encore de
///   barre d'onglets, ni de Profil, ni de Réglages) ;
/// - l'état « Pas encore de Miff » avec « Créer une Miff » et « Rejoindre une Miff ». Le « Tout le monde est en sécurité » vert
///   de la maquette n'est PAS dessiné : sans Miff, rien n'est connu de personne, et l'app ne dit jamais « tout va bien » quand
///   elle ne le sait pas (décision Q12).
/// Le lien « Se déconnecter » reste ici faute d'écran Réglages sur iOS (voir `ios/README.md`). Sans état propre.
struct MiffHomeContent: View {
    let state: DirectoryState
    let actions: MiffHomeActions

    var body: some View {
        MiffFrame(title: nil, onBack: nil, usesCanvas: true) {
            VStack(spacing: Spacing.lg) {
                MiffWordmark()
                    .frame(width: Spacing.logoWidth / 2)
                    .padding(.top, Spacing.sm)
                stateContent
                Button(action: actions.onSignOut) {
                    Text("auth_signout_action")
                        .miffTextStyle(textBodyBold16)
                        .foregroundColor(colorActionPrimary.color)
                        .frame(minHeight: Spacing.minTouchTarget)
                }
            }
            .frame(maxWidth: .infinity)
        }
    }

    @ViewBuilder
    private var stateContent: some View {
        switch state {
        case .loading:
            LoadingIndicator()
        case .unavailable:
            MiffNotice(text: L10n.string("feed_unavailable"))
            Button(action: actions.onRetry) {
                Text("auth_retry_action")
            }
            .buttonStyle(.miffSecondaryFullWidth)
        case .ready(let ready):
            if ready.miffs.isEmpty {
                noMiff
            } else {
                MiffHomeReady(ready: ready, actions: actions)
            }
        }
    }

    /// « Pas encore de Miff » : une pastille qui ne fait rien, une phrase calme, et les deux façons de commencer.
    @ViewBuilder
    private var noMiff: some View {
        MiffPill(text: L10n.string("feed_selector_none"), showsArrow: false)
        Text("feed_nomiff_body")
            .miffTextStyle(textBodyRegular)
            .foregroundColor(colorTextDescription.color)
            .multilineTextAlignment(.center)
            .padding(.vertical, Spacing.md)
        Button(action: actions.onCreate) {
            Text("feed_create_action")
        }
        .buttonStyle(.miffPrimaryFullWidth)
        Button(action: actions.onJoin) {
            Text("feed_join_action")
        }
        .buttonStyle(.miffPrimaryFullWidth)
    }
}

/// L'accueil d'une personne qui a au moins une Miff.
private struct MiffHomeReady: View {
    let ready: DirectoryReady
    let actions: MiffHomeActions

    var body: some View {
        if let active = ready.active {
            selector(active: active)
            if ready.fromCache {
                MiffNotice(text: L10n.string("feed_offline_note"))
            }
            Text("feed_empty_title")
                .miffTextStyle(textDisplayHeadline)
                .foregroundColor(colorTextForm.color)
                .multilineTextAlignment(.center)
                .padding(.top, Spacing.md)
                .accessibilityAddTraits(.isHeader)
            Text("feed_empty_body")
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextDescription.color)
                .multilineTextAlignment(.center)
            MyMiffCard(name: active.name, action: { actions.onOpenMiff(active.id) })
        }
    }

    /// Le nom de la Miff active ; un sélecteur seulement pour un compte Premium (un compte gratuit n'a qu'une Miff).
    @ViewBuilder
    private func selector(active: MiffSummary) -> some View {
        if ready.premium {
            Menu {
                ForEach(ready.miffs, id: \.id) { miff in
                    Button {
                        actions.onSelect(miff.id)
                    } label: {
                        Text(verbatim: miff.name)
                    }
                }
                Divider()
                Button(action: actions.onJoin) {
                    Text("feed_selector_join")
                }
                Button(action: actions.onCreate) {
                    Text("feed_selector_create")
                }
            } label: {
                MiffPill(text: active.name, showsArrow: true)
            }
            .accessibilityLabel(Text(verbatim: L10n.format("feed_selector_a11y", active.name)))
        } else {
            MiffPill(text: active.name, showsArrow: false)
        }
    }
}

/// La pastille arrondie des maquettes : le nom en italique, un petit triangle quand elle ouvre une liste.
private struct MiffPill: View {
    let text: String
    let showsArrow: Bool

    var body: some View {
        HStack(spacing: Spacing.sm) {
            Text(verbatim: text)
                .miffTextStyle(textBodyItalic)
                .foregroundColor(colorTextForm.color)
            if showsArrow {
                Image(systemName: "arrowtriangle.down.fill")
                    .font(.system(size: 10))
                    .foregroundColor(colorActionPrimary.color)
                    .accessibilityHidden(true)
            }
        }
        .padding(.horizontal, Spacing.md)
        .frame(minHeight: Spacing.minTouchTarget)
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
        .miffShadow(shadowDefault)
    }
}

/// « Mes Miffs » : la carte de la Miff, qui ouvre son écran (décision Q2 du ticket Feed).
private struct MyMiffCard: View {
    let name: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.md) {
                Image(systemName: "person.2")
                    .font(.system(size: Spacing.sectionIcon, weight: .regular))
                    .foregroundColor(colorActionPrimary.color)
                    .frame(width: Spacing.minTouchTarget, height: Spacing.minTouchTarget)
                    .background(
                        RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                            .fill(colorActionPrimaryTint20.color)
                    )
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text("profile_tab_miffs_title")
                        .miffTextStyle(textBodyCaption)
                        .foregroundColor(colorTextDescription.color)
                    Text(verbatim: name)
                        .miffTextStyle(textBodyBold16)
                        .foregroundColor(colorTextPrimary.color)
                    Text("profile_tab_miff_hint")
                        .miffTextStyle(textBodyRegular14)
                        .foregroundColor(colorActionPrimary.color)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
                Image(systemName: "chevron.right")
                    .foregroundColor(colorTextDescription.color)
                    .accessibilityHidden(true)
            }
            .padding(Spacing.md)
            .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
            .miffShadow(shadowDefault)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(Text(verbatim: L10n.format("profile_tab_miff_open_a11y", name)))
        .accessibilityAddTraits(.isButton)
    }
}
