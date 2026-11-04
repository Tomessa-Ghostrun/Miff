import SwiftUI

/// Les gestes de l'écran d'une Miff.
struct MiffScreenActions {
    let onBack: () -> Void
    let onRetry: () -> Void
    let onNameChanged: (String) -> Void
    let onSaveName: () -> Void
    let onSharingRequested: () -> Void
    let onSharingConfirmed: () -> Void
    let onSharingDismissed: () -> Void
}

/// Ce qui relance l'écoute : l'app qui revient au premier plan, ou « Réessayer ».
private struct ObserveKey: Hashable {
    let active: Bool
    let restart: Int
}

/// L'écran d'une Miff relié à son view model. Il LIT la Miff tant qu'il est à l'écran : les trois écoutes Firestore et la veille
/// du réseau tournent dans une tâche que le système annule quand l'écran disparaît ou que l'app passe en arrière-plan, et qui
/// repart au retour (rien n'écoute une Miff que personne ne regarde). Sans cette tâche l'écran ne lirait jamais rien.
@MainActor
struct MiffScreenView: View {
    @StateObject private var viewModel: MiffScreenViewModel
    @Environment(\.scenePhase) private var scenePhase
    private let onBack: () -> Void

    init(miffId: String, dependencies: MiffScreenDependencies, onBack: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: MiffScreenViewModel(miffId: miffId, dependencies: dependencies))
        self.onBack = onBack
    }

    var body: some View {
        let key = ObserveKey(active: scenePhase != .background, restart: viewModel.state.restart)
        MiffScreenContent(
            state: viewModel.state,
            actions: MiffScreenActions(
                onBack: onBack,
                onRetry: { viewModel.retry() },
                onNameChanged: { viewModel.onNameInputChanged($0) },
                onSaveName: { viewModel.onSaveName() },
                onSharingRequested: { viewModel.onSharingRequested() },
                onSharingConfirmed: { viewModel.onSharingConfirmed() },
                onSharingDismissed: { viewModel.onSharingDialogDismissed() }
            )
        )
        .task(id: key) {
            if key.active {
                await viewModel.observe()
            }
        }
    }
}

/// L'écran d'une Miff, pour son créateur (F) et pour un membre (G) : mêmes sections, même ordre, et seul le créateur peut
/// changer le nom. Aucun statut, aucune batterie, aucune position, aucune photo d'update n'est dessinée ici (critère G7).
/// Sans état propre.
struct MiffScreenContent: View {
    let state: MiffScreenState
    let actions: MiffScreenActions

    private var title: String {
        state.name.isEmpty ? L10n.string("miff_screen_title_loading") : state.name
    }

    var body: some View {
        MiffFrame(title: title, onBack: actions.onBack, usesCanvas: true) {
            switch state.status {
            case .loading:
                LoadingIndicator()
            case .accessLost:
                // Le message unique d'une perte d'accès est dessiné par-dessus tout par l'écran hôte ; rien de la Miff ne reste ici.
                EmptyView()
            case .unavailable:
                MiffNotice(text: L10n.string("miff_unavailable"))
                Button(action: actions.onRetry) {
                    Text("auth_retry_action")
                }
                .buttonStyle(.miffSecondaryFullWidth)
            case .ready:
                MiffReadySections(state: state, actions: actions)
            }
        }
        // Le code est à l'écran : pas de vignette dans le sélecteur d'applications.
        .hidesInAppSwitcher()
        .sheet(item: dialogBinding) { dialog in
            dialogSheet(dialog)
        }
    }

    private var dialogBinding: Binding<SharingDialog?> {
        Binding(
            get: { state.sharingDialog },
            set: { newValue in
                if newValue == nil {
                    actions.onSharingDismissed()
                }
            }
        )
    }

    @ViewBuilder
    private func dialogSheet(_ dialog: SharingDialog) -> some View {
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
                // Le même consentement, mot pour mot et avec la même version, qu'à l'entrée (critère K6).
                SharingConsentCard(
                    sentence: L10n.string("miff_sharing_resume_body"),
                    systemNote: L10n.string("miff_consent_system_note_create")
                )
            }
        }
    }
}

/// Les sections d'une Miff lue : nom (créateur), code, proches, partage, lieux.
private struct MiffReadySections: View {
    let state: MiffScreenState
    let actions: MiffScreenActions

    var body: some View {
        if state.offline {
            MiffNotice(text: L10n.string("miff_offline_notice"))
        }
        // Le message d'une fenêtre est dit dans la fenêtre ; les autres (renommer) le sont ici.
        if state.sharingDialog == nil, let message = state.message {
            MiffNotice(text: actionMessageText(message))
        }
        if state.isOwner {
            MiffNameSection(state: state, actions: actions)
        }
        MiffCodeSection(state: state)
        MiffMembersSection(state: state)
        if let sharing = state.sharing {
            MiffSwitchRow(
                title: L10n.string("miff_sharing_title"),
                description: L10n.string(sharing ? "miff_sharing_on_description" : "miff_sharing_off_description"),
                isOn: sharing,
                isEnabled: state.canWrite,
                stateText: L10n.string(sharing ? "miff_sharing_state_on" : "miff_sharing_state_off"),
                action: actions.onSharingRequested
            )
        }
        MiffPlacesSection(state: state)
        Spacer().frame(height: Spacing.lg)
    }
}

private struct MiffNameSection: View {
    let state: MiffScreenState
    let actions: MiffScreenActions

    @FocusState private var focus: MiffField?

    private var helper: String {
        state.nameLimitReached ? L10n.string("miff_name_limit") : L10n.string("miff_name_help")
    }

    private var saveKey: LocalizedStringKey {
        state.saving ? "miff_name_saving" : "miff_name_save_action"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            MiffSectionHeader(systemImage: "pencil", title: L10n.string("miff_section_name_title"), description: nil)
            LabeledTextField(
                label: "miff_name_label",
                text: Binding(get: { state.nameInput }, set: { actions.onNameChanged($0) }),
                focus: LabeledFieldFocus(field: MiffField.name, binding: $focus),
                options: LabeledFieldOptions(
                    placeholder: L10n.string("miff_name_placeholder"),
                    counter: counter,
                    isEnabled: !state.saving && !state.offline,
                    submitLabel: .done,
                    capitalization: .words
                ),
                onSubmit: actions.onSaveName
            )
            Text(verbatim: helper)
                .miffTextStyle(textBodyCaption)
                .foregroundColor(colorTextDescription.color)
                .fixedSize(horizontal: false, vertical: true)
            Button(action: actions.onSaveName) {
                Text(saveKey)
            }
            .buttonStyle(.miffPrimaryFullWidth)
            .disabled(!state.canSaveName)
            if state.nameSaved {
                Text("miff_name_saved")
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextDescription.color)
                    .onAppear {
                        Announcer.announce(L10n.string("miff_name_saved"))
                    }
            }
        }
    }

    /// Le compteur « 34 / 40 » apparaît à partir de 30 caractères (fiche `miff-creation.md`).
    private var counter: String? {
        let length = state.nameInput.utf16.count
        return length >= 30 ? L10n.format("miff_name_counter", length, MiffName.maxLength) : nil
    }
}

private struct MiffCodeSection: View {
    let state: MiffScreenState

    @State private var copied = false

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            MiffSectionHeader(
                systemImage: "lock",
                title: L10n.string("miff_section_code_title"),
                description: L10n.string("miff_section_code_description")
            )
            MiffCodeDisplay(code: state.joinCode)
            if state.isFull {
                Text("miff_full_notice")
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextDescription.color)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
            } else {
                ShareLink(item: MiffSharing.message(miffName: state.name, code: state.joinCode)) {
                    MiffPrimaryLabel(key: "miff_share_code_action")
                }
                .buttonStyle(.plain)
            }
            Button {
                copy()
            } label: {
                Text("miff_copy_code_action")
            }
            .buttonStyle(.miffSecondaryFullWidth)
            if copied {
                Text("miff_code_copied")
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextDescription.color)
                    .frame(maxWidth: .infinity)
            }
        }
    }

    private func copy() {
        MiffSharing.copy(code: state.joinCode)
        copied = true
        Announcer.announce(L10n.string("miff_code_copied"))
        Task {
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            copied = false
        }
    }
}

private struct MiffMembersSection: View {
    let state: MiffScreenState

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            MiffSectionHeader(
                systemImage: "person.2",
                title: L10n.string("miff_section_members_title"),
                description: L10n.string("miff_section_members_description")
            )
            ForEach(state.members, id: \.uid) { member in
                MiffMemberRow(member: member, isMe: member.uid == state.myUid)
            }
            if state.members.count <= 1 {
                Text("miff_members_alone")
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextDescription.color)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Spacing.sm)
            }
        }
    }
}

private struct MiffPlacesSection: View {
    let state: MiffScreenState

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            MiffSectionHeader(
                systemImage: "mappin.and.ellipse",
                title: L10n.string("miff_section_places_title"),
                description: L10n.string("miff_section_places_description")
            )
            if state.places.isEmpty {
                Text("miff_places_empty")
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextDescription.color)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, Spacing.sm)
            } else {
                ForEach(state.places, id: \.id) { place in
                    Text(verbatim: place.name)
                        .miffTextStyle(textBodyRegular14)
                        .foregroundColor(colorTextDescription.color)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, Spacing.sm)
                        .padding(.vertical, Spacing.xs)
                }
            }
        }
    }
}
