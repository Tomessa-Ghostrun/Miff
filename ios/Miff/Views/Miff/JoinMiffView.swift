import SwiftUI

/// Les gestes des écrans « Rejoindre une Miff » et de sa confirmation.
struct JoinMiffActions {
    let onBack: () -> Void
    let onCodeChanged: (String) -> Void
    let onContinue: () -> Void
    let onConfirm: () -> Void
    let onCancelConfirm: () -> Void
    let onOpenMiff: (String) -> Void
}

/// Où va l'écran « Rejoindre » quand il se referme.
struct JoinNavigation {
    let onBack: () -> Void
    /// Le lot est confirmé par le serveur.
    let onJoined: () -> Void
    /// « Ouvrir la Miff » de « Tu fais déjà partie de la Miff ».
    let onOpenMiff: (String) -> Void
}

/// « Rejoindre une Miff » relié à son view model.
@MainActor
struct JoinMiffView: View {
    @StateObject private var viewModel: JoinMiffViewModel
    private let navigation: JoinNavigation

    init(context: MiffFlowContext, limiter: JoinAttemptLimiter, navigation: JoinNavigation) {
        _viewModel = StateObject(wrappedValue: JoinMiffViewModel(context: context, limiter: limiter))
        self.navigation = navigation
    }

    var body: some View {
        JoinMiffContent(
            state: viewModel.state,
            actions: JoinMiffActions(
                onBack: navigation.onBack,
                onCodeChanged: { viewModel.onCodeChanged($0) },
                onContinue: { viewModel.onContinue() },
                onConfirm: { viewModel.onConfirm() },
                onCancelConfirm: { viewModel.onCancelConfirm() },
                onOpenMiff: navigation.onOpenMiff
            )
        )
        // Le lot est confirmé par le serveur : l'écran se referme (l'étape de confirmation n'a plus rien à dire).
        .onChange(of: viewModel.state.joinedMiffId) { joinedId in
            if joinedId != nil {
                navigation.onJoined()
            }
        }
    }
}

/// D (la saisie du code) puis E (la confirmation avec le consentement explicite). Sans état propre.
struct JoinMiffContent: View {
    let state: JoinUiState
    let actions: JoinMiffActions

    var body: some View {
        switch state.step {
        case .code:
            JoinCodeStep(state: state, actions: actions)
        case .confirm:
            JoinConfirmStep(state: state, actions: actions)
        }
    }
}

struct JoinCodeStep: View {
    let state: JoinUiState
    let actions: JoinMiffActions

    @FocusState private var focus: MiffField?

    private var errorText: String? {
        if state.foreignCharacter {
            return L10n.string("miff_join_foreign")
        }
        if state.problem == .notFound {
            return L10n.string("miff_join_not_found")
        }
        return nil
    }

    private var buttonKey: LocalizedStringKey {
        state.checking ? "miff_join_checking" : "miff_join_continue_action"
    }

    var body: some View {
        MiffFrame(title: L10n.string("miff_join_title"), onBack: state.checking ? nil : actions.onBack) {
            Text("miff_join_help")
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextDescription.color)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
            codeField
            pasteRow
            problemNotice
            if let member = state.alreadyMember {
                alreadyMemberCard(member)
            }
            Button(action: actions.onContinue) {
                Text(buttonKey)
            }
            .buttonStyle(.miffPrimaryFullWidth)
            .disabled(!state.canContinue)
        }
        // Ce qu'on tape ici est un secret : pas de vignette dans le sélecteur d'applications.
        .hidesInAppSwitcher()
    }

    /// Le code s'affiche par groupes de 4 pendant la frappe ; ce qui est gardé est le code nettoyé (les espaces, les tirets et
    /// les minuscules sont normalisés par le view model, critère J1).
    private var codeField: some View {
        LabeledTextField(
            label: "miff_join_code_label",
            text: Binding(get: { JoinCode.grouped(state.code) }, set: { actions.onCodeChanged($0) }),
            focus: LabeledFieldFocus(field: MiffField.code, binding: $focus),
            options: LabeledFieldOptions(
                errorText: errorText,
                placeholder: L10n.string("miff_join_code_hint"),
                isEnabled: !state.checking,
                submitLabel: .go,
                keyboard: .asciiCapable,
                capitalization: .characters,
                textStyle: textBodyBold16
            ),
            onSubmit: actions.onContinue
        )
    }

    /// Le bouton « Coller » du système : il ne déclenche aucune alerte de collage et l'app ne lit jamais le presse-papiers
    /// elle-même (critère J3).
    private var pasteRow: some View {
        HStack {
            Spacer()
            PasteButton(payloadType: String.self) { strings in
                guard let text = strings.first else {
                    return
                }
                Task { @MainActor in
                    actions.onCodeChanged(text)
                }
            }
            .labelStyle(.titleAndIcon)
            .buttonBorderShape(.capsule)
            .tint(colorActionPrimary.color)
            .disabled(state.checking)
        }
    }

    @ViewBuilder
    private var problemNotice: some View {
        switch state.problem {
        case .paused:
            MiffNotice(text: L10n.string("miff_join_paused"))
        case .offline:
            MiffNotice(text: actionMessageText(.offline))
        case .generic:
            MiffNotice(text: actionMessageText(.generic))
        case .oneMiff:
            MiffNotice(text: actionMessageText(.oneMiff))
        case .notFound, .foreignCharacter, .none:
            EmptyView()
        }
    }

    private func alreadyMemberCard(_ member: AlreadyMemberInfo) -> some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text(verbatim: L10n.format("miff_join_already_member", member.miffName))
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextPrimary.color)
                .fixedSize(horizontal: false, vertical: true)
            Button {
                actions.onOpenMiff(member.miffId)
            } label: {
                Text("miff_join_open_action")
            }
            .buttonStyle(.miffPrimaryFullWidth)
        }
        .padding(Spacing.md)
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
        .overlay(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .stroke(colorBorderDefault.color, lineWidth: strokeHairline)
        )
    }
}

/// E : « Rejoindre la Miff « X » ? », avec le consentement en pleine vue AVANT le bouton : rien n'est caché, replié, ni
/// accessible seulement en défilant sur un téléphone normal. « Annuler » n'écrit rien. Le balayage de retour est coupé ici :
/// la sortie est « Annuler » (qui ramène au code), jamais un retour qui quitterait tout l'écran par accident.
struct JoinConfirmStep: View {
    let state: JoinUiState
    let actions: JoinMiffActions

    private var buttonKey: LocalizedStringKey {
        state.joining ? "miff_join_joining" : "miff_join_action"
    }

    var body: some View {
        MiffFrame(title: nil, onBack: nil) {
            VStack(spacing: Spacing.md) {
                Text(verbatim: L10n.format("miff_join_confirm_title", state.miffName ?? ""))
                    .miffTextStyle(textDisplayHeadline)
                    .foregroundColor(colorTextForm.color)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, Spacing.md)
                    .accessibilityAddTraits(.isHeader)
                SharingConsentCard(
                    sentence: L10n.string("miff_consent_join"),
                    systemNote: L10n.string("miff_consent_system_note_join")
                )
                if let message = state.confirmMessage {
                    MiffNotice(text: actionMessageText(message))
                }
                Button(action: actions.onConfirm) {
                    Text(buttonKey)
                }
                .buttonStyle(.miffPrimaryFullWidth)
                .disabled(state.joining)
                if state.joining {
                    LoadingIndicator()
                }
                Button(action: actions.onCancelConfirm) {
                    Text("common_cancel")
                        .miffTextStyle(textBodyBold16)
                        .foregroundColor(colorActionPrimary.color)
                        .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget)
                }
                .disabled(state.joining)
            }
            .frame(maxWidth: .infinity)
        }
        .background(SwipeBackLock(isLocked: true))
    }
}
