import SwiftUI

/// Les gestes des écrans « Nouvelle Miff » et « Ta Miff est prête ».
struct CreateMiffActions {
    let onBack: () -> Void
    let onNameChanged: (String) -> Void
    let onSubmit: () -> Void
    let onContinue: () -> Void
}

/// « Nouvelle Miff » puis « Ta Miff est prête » reliés à leur view model.
@MainActor
struct CreateMiffView: View {
    @StateObject private var viewModel: CreateMiffViewModel
    private let onBack: () -> Void
    private let onContinue: () -> Void

    init(context: MiffFlowContext, onBack: @escaping () -> Void, onContinue: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: CreateMiffViewModel(context: context))
        self.onBack = onBack
        self.onContinue = onContinue
    }

    var body: some View {
        CreateMiffContent(
            state: viewModel.state,
            actions: CreateMiffActions(
                onBack: onBack,
                onNameChanged: { viewModel.onNameChanged($0) },
                onSubmit: { viewModel.onSubmit() },
                onContinue: onContinue
            )
        )
    }
}

/// B (« Nouvelle Miff » : un champ, le consentement, un bouton) puis C (« Ta Miff est prête » : le code, partager, copier,
/// continuer). Sans état propre : il dessine ce que le view model décide.
struct CreateMiffContent: View {
    let state: CreateUiState
    let actions: CreateMiffActions

    var body: some View {
        switch state.step {
        case .name:
            CreateNameStep(state: state, actions: actions)
        case .ready:
            CreateReadyStep(state: state, onContinue: actions.onContinue)
        }
    }
}

struct CreateNameStep: View {
    let state: CreateUiState
    let actions: CreateMiffActions

    @FocusState private var focus: MiffField?

    private var helper: String {
        state.limitReached ? L10n.string("miff_name_limit") : L10n.string("miff_name_help")
    }

    private var buttonKey: LocalizedStringKey {
        state.saving ? "miff_create_saving" : "miff_create_action"
    }

    var body: some View {
        MiffFrame(title: L10n.string("miff_create_title"), onBack: state.saving ? nil : actions.onBack) {
            nameField
            Text(verbatim: helper)
                .miffTextStyle(textBodyCaption)
                .foregroundColor(colorTextDescription.color)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
            SharingConsentCard(
                sentence: L10n.string("miff_consent_create"),
                systemNote: L10n.string("miff_consent_system_note_create")
            )
            if let message = state.message {
                MiffNotice(text: actionMessageText(message))
            }
            Button(action: actions.onSubmit) {
                Text(buttonKey)
            }
            .buttonStyle(.miffPrimaryFullWidth)
            .disabled(!state.canSubmit)
            if state.saving {
                LoadingIndicator()
            }
        }
        // Pendant l'écriture, le balayage depuis le bord ne ramène pas en arrière (un appui = une opération).
        .background(SwipeBackLock(isLocked: state.saving))
        .onChange(of: state.limitReached) { reached in
            if reached {
                Announcer.announce(L10n.string("miff_name_limit"))
            }
        }
    }

    private var nameField: some View {
        let length = state.name.utf16.count
        let showCounter = length >= Self.counterFrom
        return LabeledTextField(
            label: "miff_name_label",
            text: Binding(get: { state.name }, set: { actions.onNameChanged($0) }),
            focus: LabeledFieldFocus(field: MiffField.name, binding: $focus),
            options: LabeledFieldOptions(
                placeholder: L10n.string("miff_name_placeholder"),
                counter: showCounter ? L10n.format("miff_name_counter", length, MiffName.maxLength) : nil,
                isEnabled: !state.saving,
                submitLabel: .done,
                capitalization: .words
            ),
            onSubmit: actions.onSubmit
        )
    }

    /// Le compteur « 34 / 40 » apparaît à partir de cette longueur (fiche `miff-creation.md`).
    private static let counterFrom = 30
}

/// C : « Ta Miff « X » est prête ». Pas de flèche de retour (la Miff existe déjà) ni de balayage de retour ; « Continuer » est
/// la sortie. Le code ne reste jamais dans la vignette du sélecteur d'applications.
struct CreateReadyStep: View {
    let state: CreateUiState
    let onContinue: () -> Void

    @State private var copied = false

    var body: some View {
        MiffFrame(title: nil, onBack: nil) {
            VStack(spacing: Spacing.md) {
                MiffWordmark()
                    .frame(width: Spacing.logoWidth / 2)
                    .padding(.top, Spacing.sm)
                if let created = state.created {
                    details(of: created)
                }
                Button(action: onContinue) {
                    Text("miff_ready_continue_action")
                }
                .buttonStyle(.miffPrimaryFullWidth)
            }
            .frame(maxWidth: .infinity)
        }
        .background(SwipeBackLock(isLocked: true))
        .hidesInAppSwitcher()
    }

    @ViewBuilder
    private func details(of created: MiffCreation) -> some View {
        Text(verbatim: L10n.format("miff_ready_title", created.name))
            .miffTextStyle(textDisplayHeadline)
            .foregroundColor(colorTextForm.color)
            .multilineTextAlignment(.center)
            .accessibilityAddTraits(.isHeader)
        Text("miff_ready_body")
            .miffTextStyle(textBodyRegular)
            .foregroundColor(colorTextDescription.color)
            .multilineTextAlignment(.center)
        MiffCodeDisplay(code: created.joinCode, isLarge: true)
        ShareLink(item: MiffSharing.message(miffName: created.name, code: created.joinCode)) {
            MiffPrimaryLabel(key: "miff_share_code_action")
        }
        .buttonStyle(.plain)
        Button {
            copy(created.joinCode)
        } label: {
            Text("miff_copy_code_action")
        }
        .buttonStyle(.miffSecondaryFullWidth)
        if copied {
            Text("miff_code_copied")
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextDescription.color)
        }
        Text("ios_miff_ready_hint")
            .miffTextStyle(textBodyCaption)
            .foregroundColor(colorTextDescription.color)
            .multilineTextAlignment(.center)
    }

    private func copy(_ code: String) {
        MiffSharing.copy(code: code)
        copied = true
        Announcer.announce(L10n.string("miff_code_copied"))
        Task {
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            copied = false
        }
    }
}
