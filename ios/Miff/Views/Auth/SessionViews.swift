import SwiftUI

/// Cadre centré des écrans d'état (attente, lecture du compte impossible, étape suivante) : le contenu est au milieu quand
/// il tient, et défile quand il ne tient pas (très grande taille de texte, paysage) : rien n'est coupé sans moyen de
/// l'atteindre.
struct CenteredFrame<Content: View>: View {
    let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        ViewThatFits(in: .vertical) {
            centered
            ScrollView {
                centered
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(gradientAuthBackground.ignoresSafeArea())
    }

    private var centered: some View {
        VStack(spacing: Spacing.md) {
            content
        }
        .padding(.horizontal, Spacing.xl)
        .padding(.vertical, Spacing.lg)
        .frame(maxWidth: .infinity)
    }
}

/// Pendant que la session ou le compte est vérifié (lancement, déconnexion en cours).
struct SessionLoadingView: View {
    var body: some View {
        CenteredFrame {
            LoadingIndicator()
        }
    }
}

/// Connecté, mais le compte n'a pas pu être lu : réessayer ou se déconnecter. Aucun autre écran n'est accessible.
struct AccountLookupFailedView: View {
    let onRetry: () -> Void
    let onSignOut: () -> Void

    var body: some View {
        CenteredFrame {
            MessageText(text: L10n.string("auth_lookup_failed"), isError: false)
            Button("auth_retry_action", action: onRetry)
                .buttonStyle(.miffPrimaryFullWidth)
            Button(action: onSignOut) {
                Text("auth_signout_action")
                    .miffTextStyle(textBodyBold16)
                    .foregroundColor(colorActionPrimary.color)
                    .frame(minHeight: Spacing.minTouchTarget)
            }
        }
    }
}

/// Panneau de consentement d'une personne nouvelle arrivée par Google ou Apple depuis l'écran de connexion (ticket §7.8).
/// Rien d'autre n'est accessible tant qu'il est ouvert. Pas de case (décision du 2026-10-28) : la ligne au-dessus des
/// boutons dit que « Continuer » accepte les conditions, la politique et les statistiques. « Annuler » est le refus : le
/// compte tout juste créé est supprimé.
struct ConsentPanelContent: View {
    let state: ConsentPanelUiState
    let onContinue: () -> Void
    let onRefuse: () -> Void

    var body: some View {
        AuthScaffold(paneTitle: "auth_panel_title", onBack: nil) {
            ScreenTitle(key: "auth_panel_title")
            AuthMessageArea(message: state.message)
            ConsentNotice(sentenceKey: "ios_auth_panel_notice")
            Spacer().frame(height: Spacing.md)
            Button("auth_panel_continue", action: onContinue)
                .buttonStyle(.miffPrimaryFullWidth)
                .disabled(state.isLoading)
            Button(action: onRefuse) {
                Text("auth_panel_cancel")
                    .miffTextStyle(textBodyBold16)
                    .foregroundColor(colorActionPrimary.color)
                    .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget)
            }
            .disabled(state.isLoading)
            if state.isLoading {
                LoadingIndicator()
            }
        }
    }
}
