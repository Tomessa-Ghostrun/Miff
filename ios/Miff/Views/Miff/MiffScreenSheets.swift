import SwiftUI

// SÉCURITÉ, pour ce fichier : le nouveau code rendu par un retrait est montré ici, dans la feuille, et dans la feuille de
// partage du système si la personne la choisit. Toute la feuille est recouverte dans le sélecteur d'applications
// (`hidesInAppSwitcher`) : une feuille est présentée au-dessus de l'écran, sa vignette ne passerait pas par le recouvrement
// de l'écran lui-même.

/// Ce que montre la feuille de l'écran d'une Miff. Il n'y en a qu'une à la fois, et son contenu change SANS qu'elle disparaisse
/// quand on passe d'une question à la suivante (de « Supprimer ? » à la progression, de « Retirer ? » à « Terminer ») : la
/// suppression d'abord, puis la question de MIFF-4, puis celle de « Partager mon statut ».
struct MiffSheetContent: View {
    let state: MiffScreenState
    let actions: MiffScreenActions

    var body: some View {
        sheet.hidesInAppSwitcher()
    }

    @ViewBuilder
    private var sheet: some View {
        if let deletion = state.deletion {
            MiffDeletionSheet(deletion: deletion, onClose: actions.onBack, onRetry: actions.onDeletionRetry)
        } else if let action = state.action {
            MiffActionSheet(state: state, action: action, actions: actions)
        } else if let dialog = state.sharingDialog {
            MiffSharingSheet(state: state, dialog: dialog, actions: actions)
        }
    }
}

/// « Arrêter / Reprendre le partage » (MIFF-3).
private struct MiffSharingSheet: View {
    let state: MiffScreenState
    let dialog: SharingDialog
    let actions: MiffScreenActions

    var body: some View {
        let stopping = dialog == .stop
        MiffConfirmSheet(
            content: MiffConfirmContent(
                title: L10n.string(stopping ? "miff_sharing_stop_title" : "miff_sharing_resume_title"),
                message: stopping ? L10n.string("miff_sharing_stop_body") : nil,
                confirmLabel: L10n.string(stopping ? "miff_sharing_stop_action" : "miff_sharing_resume_action"),
                isBusy: state.sharingBusy,
                failure: state.message
            ),
            actions: MiffConfirmActions(onConfirm: actions.onSharingConfirmed, onCancel: actions.onSharingDismissed)
        ) {
            if !stopping {
                // Le même consentement, mot pour mot et avec la même version (la 3), qu'à l'entrée (critère K6) ; seule la phrase
                // d'ouverture dit « à nouveau ».
                SharingConsentCard(sentence: L10n.string("miff_consent3_resume"))
            }
        }
    }
}

/// Les questions de MIFF-4 (écrans H, I, J, K du ticket) : un seul modèle, un ton calme, « Annuler » toujours disponible tant
/// que rien n'est parti.
private struct MiffActionSheet: View {
    let state: MiffScreenState
    let action: MiffAction
    let actions: MiffScreenActions

    private var buttons: MiffConfirmActions {
        MiffConfirmActions(onConfirm: actions.onActionConfirmed, onCancel: actions.onActionDismissed)
    }

    var body: some View {
        switch action {
        case .remove(_, let firstName):
            MiffConfirmSheet(content: removeContent(firstName: firstName), actions: buttons)
        case .renew:
            MiffConfirmSheet(content: renewContent, actions: buttons)
        case .leave:
            MiffConfirmSheet(content: leaveContent, actions: buttons)
        case .delete(let othersCount):
            MiffConfirmSheet(content: deleteContent(othersCount: othersCount), actions: buttons)
        case .removedDone(let firstName, let newCode):
            MiffConfirmSheet(content: removedDoneContent(firstName: firstName, newCode: newCode), actions: buttons) {
                MiffCodeDisplay(code: newCode)
            }
        }
    }

    /// « Retirer {prénom} de la Miff ? » : ton sensible (bouton blanc au trait rouge), une confirmation NOMINATIVE.
    private func removeContent(firstName: String) -> MiffConfirmContent {
        MiffConfirmContent(
            title: L10n.format("miff_remove_title", firstName),
            message: L10n.format("miff_remove_body", firstName),
            confirmLabel: L10n.string("miff_remove_action"),
            isBusy: state.actionBusy,
            failure: state.message,
            tone: .sensitive
        )
    }

    private var renewContent: MiffConfirmContent {
        MiffConfirmContent(
            title: L10n.string("miff_renew_title"),
            message: L10n.string("miff_renew_body"),
            confirmLabel: L10n.string("miff_renew_action"),
            isBusy: state.actionBusy,
            failure: state.message
        )
    }

    private var leaveContent: MiffConfirmContent {
        MiffConfirmContent(
            title: L10n.format("miff_leave_title", state.name),
            message: L10n.string("miff_leave_body"),
            confirmLabel: L10n.string("miff_leave_action"),
            isBusy: state.actionBusy,
            failure: state.message,
            tone: .sensitive
        )
    }

    /// Après un retrait : « {Prénom} ne fait plus partie de la Miff » avec le NOUVEAU code, « Partager le code » et
    /// « Terminer » (pas d'« Annuler » : plus rien à annuler).
    private func removedDoneContent(firstName: String, newCode: String) -> MiffConfirmContent {
        MiffConfirmContent(
            title: L10n.format("miff_remove_done_title", firstName),
            message: L10n.string("miff_remove_done_body"),
            confirmLabel: L10n.string("miff_remove_done_close"),
            isBusy: false,
            failure: nil,
            showsCancel: false,
            share: MiffShareExtra(
                label: L10n.string("miff_share_code_action"),
                hint: nil,
                item: MiffSharing.message(miffName: state.name, code: newCode)
            )
        )
    }

    /// « Supprimer la Miff « X » ? » : la phrase dit le nombre des AUTRES proches (jamais « 0 proche ») ; « Prévenir mes
    /// proches » n'est proposé que s'il y en a. Il PRÉPARE un message que le créateur envoie lui-même.
    private func deleteContent(othersCount: Int) -> MiffConfirmContent {
        let others = othersCount > 0
        let body = others
            ? L10n.plural("miff_delete_body_others", count: othersCount)
            : L10n.string("miff_delete_body_alone")
        let notify = MiffShareExtra(
            label: L10n.string("miff_delete_notify_action"),
            hint: L10n.string("miff_delete_notify_hint"),
            item: MiffSharing.closingNotice(miffName: state.name)
        )
        return MiffConfirmContent(
            title: L10n.format("miff_delete_title", state.name),
            message: body,
            confirmLabel: L10n.string("miff_delete_action"),
            isBusy: state.actionBusy,
            failure: state.message,
            tone: .sensitive,
            share: others ? notify : nil
        )
    }
}

/// La suppression en cours (J3, J4) : une phrase calme et un indicateur, jamais le décompte de ce qui reste, jamais d'« Annuler »
/// (la procédure se reprend, elle ne se défait pas à moitié). « Fermer » quitte l'écran SANS l'arrêter : la suppression continue
/// dans l'app (critère S3). Glisser la feuille vers le bas fait la même chose (sur iPhone il n'y a pas de bouton « Retour »).
private struct MiffDeletionSheet: View {
    let deletion: DeletionState
    let onClose: () -> Void
    let onRetry: () -> Void

    var body: some View {
        switch deletion {
        case .running, .waitingForNetwork:
            MiffProgressSheet(
                title: L10n.string("miff_delete_progress"),
                message: deletion == .waitingForNetwork ? L10n.string("miff_delete_paused") : nil,
                closeLabel: L10n.string("miff_delete_close"),
                onClose: onClose
            )
        case .stuck:
            MiffConfirmSheet(
                content: MiffConfirmContent(
                    title: L10n.string("miff_delete_progress"),
                    message: L10n.string("miff_message_generic"),
                    confirmLabel: L10n.string("auth_retry_action"),
                    isBusy: false,
                    failure: nil,
                    cancelLabel: L10n.string("miff_delete_close")
                ),
                actions: MiffConfirmActions(onConfirm: onRetry, onCancel: onClose)
            )
        case .done:
            MiffConfirmSheet(
                content: MiffConfirmContent(
                    title: L10n.string("miff_delete_done"),
                    message: nil,
                    confirmLabel: L10n.string("miff_delete_close"),
                    isBusy: false,
                    failure: nil,
                    showsCancel: false
                ),
                actions: MiffConfirmActions(onConfirm: onClose, onCancel: onClose)
            )
        }
    }
}

/// Une feuille qui dit seulement où en sont les choses : une phrase, un indicateur, et un lien « Fermer ».
private struct MiffProgressSheet: View {
    let title: String
    let message: String?
    let closeLabel: String
    let onClose: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.md) {
                Text(verbatim: title)
                    .miffTextStyle(textTitleBold20)
                    .foregroundColor(colorTextPrimary.color)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
                if let message {
                    Text(verbatim: message)
                        .miffTextStyle(textBodyRegular)
                        .foregroundColor(colorTextDescription.color)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }
                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(colorActionPrimary.color)
                    .accessibilityLabel("miff_dialog_working_a11y")
                MiffLinkButton(title: closeLabel, action: onClose)
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.lg)
        }
        .background(colorBgSurface.color.ignoresSafeArea())
        .presentationDetents([.medium, .large])
        .onAppear {
            Announcer.announce(title)
        }
    }
}
