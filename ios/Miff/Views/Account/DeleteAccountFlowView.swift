import SwiftUI

/// Le début du parcours de suppression : l'information (A) avec sa fenêtre de confirmation (B), puis la ré-authentification (C). Ni l'un ni
/// l'autre n'écrit quoi que ce soit : la suppression ne commence qu'après une ré-authentification réussie, et prend alors tout l'écran
/// (`AppDestination.accountDeletion`), cette vue disparaissant avec le reste de l'app connectée.
///
/// Un seul endroit l'ouvre : Réglages > Mon compte (poussée dans la pile unique de l'app). Décision du PM du 2026-11-07 : la suppression
/// n'est accessible que depuis les Réglages.
/// `onClose` revient d'où l'on vient, sans rien avoir touché (SC4).
struct DeleteAccountFlowView: View {
    private enum Step {
        case information
        case verification
    }

    private let environment: AppEnvironment
    private let onClose: () -> Void

    @StateObject private var information: DeleteAccountInfoViewModel
    @StateObject private var verification: DeleteReauthViewModel
    @State private var step = Step.information
    @State private var showingReset = false

    init(environment: AppEnvironment, onClose: @escaping () -> Void) {
        self.environment = environment
        self.onClose = onClose
        _information = StateObject(wrappedValue: environment.makeDeleteInfoViewModel())
        _verification = StateObject(wrappedValue: environment.makeDeleteReauthViewModel(last: false))
    }

    var body: some View {
        content
            .sheet(isPresented: $showingReset) {
                // « Mot de passe oublié ? » : l'écran d'AUTH-4, dans une feuille. La suppression n'a pas commencé, rien n'est perdu.
                PasswordResetView(
                    environment: environment,
                    initialEmail: environment.accountInfo.currentEmail() ?? "",
                    onBack: { showingReset = false }
                )
            }
    }

    @ViewBuilder
    private var content: some View {
        switch step {
        case .information:
            DeleteAccountInfoView(
                model: information,
                onBack: onClose,
                onConfirmed: { step = .verification }
            )
        case .verification:
            DeleteAccountReauthView(
                model: verification,
                onBack: { step = .information },
                onForgotPassword: { showingReset = true }
            )
        }
    }
}

/// G2. La bannière au-dessus du carrousel quand la suppression s'est terminée derrière l'écran (l'app a été relancée, ticket §5.6) : une
/// phrase calme, annoncée par VoiceOver, qui s'efface seule au bout de quelques secondes ou d'un toucher.
struct AccountDeletedBanner: View {
    /// Assez long pour être lu, assez court pour ne pas gêner.
    private static let seconds: UInt64 = 6

    let onDismiss: () -> Void

    var body: some View {
        Button(action: onDismiss) {
            HStack(spacing: Spacing.sm + Spacing.xs) {
                Image(systemName: "checkmark.circle")
                    .foregroundColor(colorActionPrimary.color)
                    .accessibilityHidden(true)
                Text("auth_account_deleted_notice")
                    .miffTextStyle(textBodyRegular)
                    .foregroundColor(colorTextForm.color)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(Spacing.md)
            .background(
                RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                    .fill(colorBgSurface.color)
                    .miffShadow(shadowDefault)
            )
        }
        .buttonStyle(.plain)
        .padding(.horizontal, Spacing.lg)
        .padding(.vertical, Spacing.sm)
        .accessibilityElement(children: .combine)
        .onAppear {
            Announcer.announce(L10n.string("auth_account_deleted_notice"))
        }
        .task {
            try? await Task.sleep(nanoseconds: Self.seconds * 1_000_000_000)
            if !Task.isCancelled {
                onDismiss()
            }
        }
    }
}
