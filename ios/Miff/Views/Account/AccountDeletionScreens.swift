import SwiftUI
import UIKit

// Les écrans de la suppression du compte une fois qu'elle est en cours (ticket AUTH-5, maquettes 07 à 09) : progression, pause sans
// réseau, reprise au lancement, arrêt calme, « Dernière vérification » et fin. Ils n'ont NI flèche de retour NI « Annuler » (le point de
// non-retour est derrière nous). Ton : on informe calmement, jamais « erreur », « échec » ni « quota », jamais le décompte de ce qui reste.

/// Ce que montre la suppression en cours, décidé d'après l'état du contrôleur.
struct AccountDeletionHostView: View {
    @ObservedObject var controller: AccountDeletionController
    @ObservedObject var lastCheck: DeleteReauthViewModel
    let onLeave: () -> Void

    /// « Plus tard » à la seconde vérification : l'écran de pause, sans bouton pour quitter l'app (iOS n'en donne pas le droit).
    @State private var postponed = false

    var body: some View {
        content
            .onAppear {
                // Lancement avec un marqueur : la suppression reprend toute seule, sans nouvelle ré-authentification.
                if controller.state == .idle {
                    controller.resume()
                }
                updateScreenLock(controller.state)
            }
            .onChange(of: controller.state) { newState in
                if newState != .needsRecentLogin {
                    postponed = false
                    lastCheck.resetBusy()
                }
                updateScreenLock(newState)
            }
            .onDisappear {
                UIApplication.shared.isIdleTimerDisabled = false
            }
    }

    @ViewBuilder
    private var content: some View {
        switch controller.state {
        case .idle:
            DeletionProgressView(
                title: "settings_delete_progress_title", line: nil, hint: "settings_delete_progress_hint"
            )
        case .running(let resumed, let retrying, let step):
            running(resumed: resumed, retrying: retrying, step: step)
        case .waitingForNetwork:
            DeletionNoticeView(
                systemImage: "icloud.slash",
                title: "settings_delete_paused_title",
                message: L10n.string("settings_delete_paused"),
                extra: "settings_delete_paused_leave"
            )
        case .needsRecentLogin:
            reauthOrPostponed
        case .stuck:
            DeletionNoticeView(
                systemImage: "info.circle",
                title: "settings_delete_failed_title",
                message: L10n.format("settings_delete_failed", AppLinks.supportEmail),
                extra: "settings_delete_paused_leave",
                retry: DeletionAction(key: "settings_delete_retry", run: { controller.retry() }),
                secondary: DeletionAction(key: "settings_delete_stuck_signout", run: { controller.signInAgain() })
            )
        case .needsSignIn:
            // La session ne peut plus servir : PAS « supprimé ». Le marqueur reste ; se reconnecter avec le même compte reprend.
            DeletionNoticeView(
                systemImage: "info.circle",
                title: "settings_delete_failed_title",
                message: L10n.string("settings_delete_signin_again"),
                extra: nil,
                retry: DeletionAction(key: "settings_delete_signin_again_action", run: { controller.signInAgain() })
            )
        case .done(let apple):
            DeletionDoneView(appleRevocation: apple, onContinue: {
                controller.acknowledge()
                onLeave()
            })
        }
    }

    @ViewBuilder
    private func running(resumed: Bool, retrying: Bool, step: DeletionStep) -> some View {
        let line = DeleteAccountRules.stepLine(step)
        let longWait = DeleteAccountRules.isLongWait(step)
        if retrying {
            DeletionProgressView(
                title: "settings_delete_resume_title",
                line: line ?? L10n.string("settings_delete_retrying"),
                hint: nil,
                longWait: longWait
            )
        } else if resumed {
            DeletionProgressView(
                title: "settings_delete_resume_title", line: line, hint: "settings_delete_resume", longWait: longWait
            )
        } else {
            DeletionProgressView(
                title: "settings_delete_progress_title",
                line: line,
                hint: "settings_delete_progress_hint",
                longWait: longWait
            )
        }
    }

    /// « Dernière vérification avant de terminer » : le même écran que la première, demandée par Firebase tout à la fin.
    @ViewBuilder
    private var reauthOrPostponed: some View {
        if postponed {
            DeletionNoticeView(
                systemImage: "info.circle",
                title: "settings_delete_paused_title",
                message: L10n.string("settings_delete_reauth_later_hint"),
                extra: "settings_delete_paused_leave",
                retry: DeletionAction(key: "settings_delete_retry", run: { postponed = false })
            )
        } else {
            DeleteAccountReauthView(
                model: lastCheck,
                onBack: { postponed = true },
                onForgotPassword: nil
            )
        }
    }

    /// L'écran reste allumé SEULEMENT pendant que la procédure tourne (l'iPhone se suspend dès que l'écran se verrouille).
    private func updateScreenLock(_ state: AccountDeletionState) {
        UIApplication.shared.isIdleTimerDisabled = DeleteAccountRules.keepsScreenOn(state)
    }
}

/// Un bouton de la page d'arrêt : sa clé de texte et son geste.
private struct DeletionAction {
    let key: LocalizedStringKey
    let run: () -> Void
}

/// D. La progression : le mot-symbole, un indicateur sans pourcentage, le titre, UNE ligne d'étape, la phrase de patience. `longWait`
/// ajoute la phrase calme de la maquette D3 (plus de trois personnes à retirer). Une seule région, lue poliment : chaque changement de
/// ligne est annoncé par VoiceOver.
private struct DeletionProgressView: View {
    let title: LocalizedStringKey
    let line: String?
    let hint: LocalizedStringKey?
    var longWait = false

    var body: some View {
        CenteredFrame {
            MiffWordmark()
                .frame(width: Spacing.logoWidth / 2)
                .padding(.bottom, Spacing.lg)
            LoadingIndicator()
            Text(title)
                .miffTextStyle(textDisplayHeadline)
                .foregroundColor(colorTextPrimary.color)
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            if let line {
                Text(verbatim: line)
                    .miffTextStyle(textBodyBold16)
                    .foregroundColor(colorActionPrimary.color)
                    .multilineTextAlignment(.center)
            }
            if let hint {
                Text(hint)
                    .miffTextStyle(textBodyRegular)
                    .foregroundColor(colorTextDescription.color)
                    .multilineTextAlignment(.center)
            }
            if longWait {
                Text("settings_delete_progress_long")
                    .miffTextStyle(textBodyRegular)
                    .foregroundColor(colorTextDescription.color)
                    .multilineTextAlignment(.center)
            }
        }
        .onChange(of: line) { newLine in
            if let newLine {
                Announcer.announce(newLine)
            }
        }
    }
}

/// E. Une pause, ou un arrêt après plusieurs essais : ce qui se passe, ce que la personne peut faire, aucune alarme.
private struct DeletionNoticeView: View {
    let systemImage: String
    let title: LocalizedStringKey
    let message: String
    let extra: LocalizedStringKey?
    var retry: DeletionAction?
    var secondary: DeletionAction?

    var body: some View {
        CenteredFrame {
            MiffWordmark()
                .frame(width: Spacing.logoWidth / 2)
                .padding(.bottom, Spacing.lg)
            DeleteIconTile(systemImage: systemImage)
            Text(title)
                .miffTextStyle(textDisplayHeadline)
                .foregroundColor(colorTextPrimary.color)
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            Text(verbatim: message)
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextDescription.color)
                .multilineTextAlignment(.center)
            if let extra {
                Text(extra)
                    .miffTextStyle(textBodyRegular)
                    .foregroundColor(colorTextDescription.color)
                    .multilineTextAlignment(.center)
            }
            if let retry {
                Button {
                    retry.run()
                } label: {
                    Text(retry.key)
                }
                .buttonStyle(.miffPrimaryFullWidth)
            }
            if let secondary {
                DeleteTextAction(key: secondary.key, action: secondary.run)
            }
        }
    }
}

/// F. « Ton compte a été supprimé » : une phrase calme et UN bouton. Si l'autorisation « Se connecter avec Apple » n'a pas pu être retirée
/// automatiquement, la personne sait comment le faire elle-même (les trois étapes de la maquette H2).
private struct DeletionDoneView: View {
    let appleRevocation: AppleRevocation
    let onContinue: () -> Void

    var body: some View {
        CenteredFrame {
            MiffWordmark()
                .frame(width: Spacing.logoWidth / 2)
                .padding(.bottom, Spacing.lg)
            Image(systemName: "checkmark.circle")
                .font(.system(size: 32, weight: .regular))
                .foregroundColor(colorTextForm.color)
                .frame(width: 72, height: 72)
                .background(Circle().fill(colorStatusSafeTint.color))
                .accessibilityHidden(true)
            Text("settings_delete_done_title")
                .miffTextStyle(textDisplayHeadline)
                .foregroundColor(colorTextPrimary.color)
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            Text("settings_delete_done_body")
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextDescription.color)
                .multilineTextAlignment(.center)
            if appleRevocation == .failed {
                AppleHowTo()
            }
            Button(action: onContinue) {
                Text("settings_delete_done_continue")
            }
            .buttonStyle(.miffPrimaryFullWidth)
        }
    }
}

/// Comment retirer à la main l'autorisation « Se connecter avec Apple » (les noms exacts des menus d'Apple sont à vérifier sur l'iPhone).
private struct AppleHowTo: View {
    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text("ios_settings_delete_apple_failed")
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextForm.color)
            Text("settings_delete_apple_howto_title")
                .miffTextStyle(textBodyBold16)
                .foregroundColor(colorTextForm.color)
                .padding(.top, Spacing.sm)
            Text(verbatim: "1. " + L10n.string("settings_delete_apple_howto_step1"))
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextDescription.color)
            Text(verbatim: "2. " + L10n.string("settings_delete_apple_howto_step2"))
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextDescription.color)
            Text(verbatim: "3. " + L10n.string("settings_delete_apple_howto_step3"))
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextDescription.color)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}
