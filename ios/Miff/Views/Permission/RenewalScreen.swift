import SwiftUI

// L'écran de renouvellement du consentement (maquettes 21 et 22). Du pur dessin : l'état entre, les gestes sortent. RIEN ici ne
// lit une position, n'envoie un événement ni n'écrit dans un journal. Rien n'est écrit tant que la personne n'a pas touché
// « J'accepte » ; « Pas maintenant » n'écrit RIEN.

struct RenewalActions {
    let onAccept: () -> Void
    let onLater: () -> Void
    let onLeave: () -> Void
    let onLeaveConfirmed: () -> Void
    let onLeaveCancelled: () -> Void
    /// La sortie discrète « Se déconnecter » (GatePolicy) ; nil quand elle n'est pas proposée.
    let onSignOut: (() -> Void)?
}

/// « Une précision sur ce que tu partages » : un écran plein cadre (un consentement se lit, il ne se survole pas), le texte de la
/// version 3 avec le nom de la Miff, les lignes que le chapeau ne dit pas, et deux boutons ÉPINGLÉS en bas. « Pas maintenant »
/// est aussi visible que « J'accepte » (jamais caché, jamais puni) et n'écrit rien.
struct RenewalScreen: View {
    let state: RenewalUiState
    let actions: RenewalActions

    private var acceptKey: LocalizedStringKey {
        state.busy ? "miff_renewal_saving" : "miff_renewal_accept"
    }

    var body: some View {
        MiffPinnedFrame(title: nil, onBack: nil) {
            VStack(spacing: Spacing.md) {
                MiffWordmark()
                    .frame(width: Spacing.logoWidth / 2)
                    .padding(.top, Spacing.sm)
                Text("miff_renewal_title")
                    .miffTextStyle(textDisplayHeadline)
                    .foregroundColor(colorTextForm.color)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
                if state.miffs.count > 1 {
                    names
                }
                Text(verbatim: intro)
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextPrimary.color)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
                RenewalDetailsCard()
                exits
            }
            .frame(maxWidth: .infinity)
        } footer: {
            if let message = state.message, !state.leaveAsked {
                MiffNotice(text: actionMessageText(message), isError: true)
                Text("miff_renewal_error_extra")
                    .miffTextStyle(textBodyCaption)
                    .foregroundColor(colorTextDescription.color)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Button(action: actions.onAccept) {
                Text(acceptKey)
            }
            .buttonStyle(.miffPrimaryFullWidth)
            .disabled(state.busy)
            if state.busy {
                LoadingIndicator()
            }
            Button(action: actions.onLater) {
                Text("miff_renewal_later")
                    .miffTextStyle(textBodyBold16)
                    .foregroundColor(colorActionPrimary.color)
                    .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget)
            }
            .disabled(state.busy)
        }
        .sheet(isPresented: leaveBinding) {
            leaveSheet
        }
    }

    /// Plusieurs Miffs (maquette 21C) : leurs noms en pastilles, en lecture seule.
    private var names: some View {
        VStack(spacing: Spacing.xs) {
            ForEach(state.miffs.filter { !$0.name.isEmpty }, id: \.miffId) { miff in
                Text(verbatim: miff.name)
                    .miffTextStyle(textBodyBold14)
                    .foregroundColor(colorActionPrimary.color)
                    .padding(.horizontal, Spacing.md)
                    .padding(.vertical, Spacing.xs)
                    .background(Capsule().fill(colorActionPrimaryTint10.color))
                    .overlay(Capsule().stroke(colorBorderAction.color, lineWidth: strokeHairline))
            }
        }
    }

    private var intro: String {
        guard state.miffs.count == 1, let only = state.miffs.first else {
            return L10n.string("miff_renewal_intro_many")
        }
        if only.name.isEmpty {
            return L10n.string("miff_renewal_intro_unnamed")
        }
        return L10n.format("miff_renewal_intro_one", only.name)
    }

    /// La sortie de la maquette 21B, à la FIN du texte (pas dans le pied : deux choix de plus en permanence alourdiraient l'écran) :
    /// un membre dont la seule Miff attend l'accord peut la quitter ; le créateur supprime sa Miff depuis son écran, et « Supprimer
    /// mon compte » n'existe pas encore (AUTH-5), donc la phrase ne le promet pas. « Se déconnecter » est la sortie discrète.
    @ViewBuilder
    private var exits: some View {
        let leavable = state.leavable != nil
        if leavable || actions.onSignOut != nil {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                if leavable {
                    Text("miff_renewal_exit_note")
                        .miffTextStyle(textBodyRegular14)
                        .foregroundColor(colorTextDescription.color)
                        .fixedSize(horizontal: false, vertical: true)
                    Button(action: actions.onLeave) {
                        Text("miff_leave_link")
                            .miffTextStyle(textBodyBold16)
                            .foregroundColor(colorActionPrimary.color)
                            .frame(minHeight: Spacing.minTouchTarget)
                    }
                    .disabled(state.busy)
                }
                if let onSignOut = actions.onSignOut {
                    Button(action: onSignOut) {
                        Text("auth_signout_action")
                            .miffTextStyle(textBodyRegular14)
                            .foregroundColor(colorActionPrimary.color)
                            .frame(minHeight: Spacing.minTouchTarget)
                    }
                    .disabled(state.busy)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm)
            .background(
                RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                    .fill(colorBgSurface.color.opacity(0.7))
            )
        }
    }

    private var leaveBinding: Binding<Bool> {
        Binding(
            get: { state.leaveAsked },
            set: { shown in
                if !shown {
                    actions.onLeaveCancelled()
                }
            }
        )
    }

    /// « Quitter la Miff « X » ? » : la question habituelle du flux (« Annuler » est le choix sûr). Rien n'est écrit avant le
    /// toucher sur « Quitter la Miff ».
    @ViewBuilder
    private var leaveSheet: some View {
        if let miff = state.leavable {
            MiffConfirmSheet(
                content: MiffConfirmContent(
                    title: L10n.format("miff_leave_title", miff.name),
                    message: L10n.string("miff_leave_body"),
                    confirmLabel: L10n.string("miff_leave_action"),
                    isBusy: state.busy,
                    failure: state.message,
                    tone: .sensitive
                ),
                actions: MiffConfirmActions(onConfirm: actions.onLeaveConfirmed, onCancel: actions.onLeaveCancelled)
            )
            .hidesInAppSwitcher()
        }
    }
}
