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
    // MIFF-4 : retirer, renouveler le code, quitter, supprimer.
    let onRemoveRequested: (String) -> Void
    let onRenewRequested: () -> Void
    let onLeaveRequested: () -> Void
    let onDeleteRequested: () -> Void
    let onActionConfirmed: () -> Void
    let onActionDismissed: () -> Void
    let onInviteLater: () -> Void
    let onDeletionRetry: () -> Void
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
        MiffScreenContent(state: viewModel.state, actions: viewModel.actions(onBack: onBack))
            .task(id: key) {
                if key.active {
                    await viewModel.observe()
                }
            }
            // La personne a quitté cette Miff et le serveur l'a confirmé : l'écran d'une Miff qui n'est plus la sienne se ferme.
            .onChange(of: viewModel.state.departed) { departed in
                if departed {
                    onBack()
                }
            }
    }
}

extension MiffScreenViewModel {
    /// Les gestes que l'écran donne au view model.
    func actions(onBack: @escaping () -> Void) -> MiffScreenActions {
        MiffScreenActions(
            onBack: onBack,
            onRetry: { [weak self] in self?.retry() },
            onNameChanged: { [weak self] in self?.onNameInputChanged($0) },
            onSaveName: { [weak self] in self?.onSaveName() },
            onSharingRequested: { [weak self] in self?.onSharingRequested() },
            onSharingConfirmed: { [weak self] in self?.onSharingConfirmed() },
            onSharingDismissed: { [weak self] in self?.onSharingDialogDismissed() },
            onRemoveRequested: { [weak self] in self?.onRemoveRequested(uid: $0) },
            onRenewRequested: { [weak self] in self?.onRenewRequested() },
            onLeaveRequested: { [weak self] in self?.onLeaveRequested() },
            onDeleteRequested: { [weak self] in self?.onDeleteRequested() },
            onActionConfirmed: { [weak self] in self?.onActionConfirmed() },
            onActionDismissed: { [weak self] in self?.onActionDismissed() },
            onInviteLater: { [weak self] in self?.onInviteLater() },
            onDeletionRetry: { [weak self] in self?.onDeletionRetry() }
        )
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
        .sheet(isPresented: sheetBinding) {
            MiffSheetContent(state: state, actions: actions)
        }
    }

    /// Une seule feuille à la fois : la suppression en cours, une question de MIFF-4, ou celle de « Partager mon statut ».
    /// Glisser la feuille vers le bas ferme la question sans rien écrire ; pour la suppression en cours, cela quitte l'écran
    /// SANS l'arrêter (elle continue dans l'app, critère S3).
    private var sheetBinding: Binding<Bool> {
        Binding(
            get: { state.deletion != nil || state.action != nil || state.sharingDialog != nil },
            set: { presented in
                if !presented {
                    dismissSheet()
                }
            }
        )
    }

    private func dismissSheet() {
        if state.deletion != nil {
            actions.onBack()
        } else if state.action != nil {
            actions.onActionDismissed()
        } else {
            actions.onSharingDismissed()
        }
    }
}

/// Les sections d'une Miff lue : nom (créateur), code, proches, partage, lieux, pied d'écran.
private struct MiffReadySections: View {
    let state: MiffScreenState
    let actions: MiffScreenActions

    /// Le message d'une fenêtre est dit dans la fenêtre ; les autres (renommer) le sont ici.
    private var showsMessage: Bool {
        state.sharingDialog == nil && state.action == nil && state.deletion == nil
    }

    var body: some View {
        if state.offline {
            MiffNotice(text: L10n.string("miff_offline_notice"))
        }
        if showsMessage, let message = state.message {
            MiffNotice(text: actionMessageText(message))
        }
        if state.isOwner, state.inviteToRenew, state.action == nil {
            MiffRenewInvitation(
                isEnabled: state.canWrite,
                onRenew: actions.onRenewRequested,
                onLater: actions.onInviteLater
            )
        }
        if state.isOwner {
            MiffNameSection(state: state, actions: actions)
        }
        MiffCodeSection(state: state, actions: actions)
        MiffMembersSection(state: state, actions: actions)
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
        MiffFootSection(state: state, actions: actions)
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
                    isEnabled: !state.saving && !state.offline && !state.deletionActive,
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
    let actions: MiffScreenActions

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
            if state.isOwner {
                renewControls
            }
        }
    }

    /// « Créer un nouveau code » sous le code (le créateur seulement), puis « Nouveau code créé. » une fois fait.
    @ViewBuilder
    private var renewControls: some View {
        MiffLinkButton(
            title: L10n.string("miff_renew_link"),
            action: actions.onRenewRequested,
            isEnabled: state.canWrite
        )
        if state.codeRenewed {
            Text("miff_renew_done")
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextDescription.color)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .onAppear {
                    Announcer.announce(L10n.string("miff_renew_done"))
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
    let actions: MiffScreenActions

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            MiffSectionHeader(
                systemImage: "person.2",
                title: L10n.string("miff_section_members_title"),
                description: L10n.string("miff_section_members_description")
            )
            ForEach(state.members, id: \.uid) { member in
                memberRow(member)
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

    /// Le menu du créateur est sur la ligne des AUTRES proches seulement : personne ne se retire soi-même, et un membre n'a pas de
    /// menu.
    private func memberRow(_ member: MiffMember) -> some View {
        let isMe = member.uid == state.myUid
        var remove: (() -> Void)?
        if state.isOwner, !isMe, member.role != .owner {
            let uid = member.uid
            let request = actions.onRemoveRequested
            remove = { request(uid) }
        }
        return MiffMemberRow(member: member, isMe: isMe, onRemove: remove, menuEnabled: state.canWrite)
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
