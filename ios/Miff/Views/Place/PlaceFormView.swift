import SwiftUI

/// Les gestes du formulaire d'un lieu.
struct PlaceFormActions {
    let onBack: () -> Void
    let onRetryLoad: () -> Void
    let onNameChanged: (String) -> Void
    let onMiffSheetOpened: () -> Void
    let onMiffSheetClosed: () -> Void
    let onMiffToggled: (String) -> Void
    let onAddressChanged: (String) -> Void
    let onSuggestionChosen: (AddressSuggestion) -> Void
    let onAddressCleared: () -> Void
    let onRetrySearch: () -> Void
    let onRadiusChanged: (Double) -> Void
    let onTypeSelected: (PlaceType) -> Void
    let onCategorySelected: (PlaceCategory) -> Void
    let onSave: () -> Void
    let onDeleteRequested: () -> Void
    let onDeleteDismissed: () -> Void
    let onDeleteConfirmed: () -> Void

    /// Des gestes qui ne font rien (aperçus des captures de la CI).
    static let none = PlaceFormActions(
        onBack: {},
        onRetryLoad: {},
        onNameChanged: { _ in },
        onMiffSheetOpened: {},
        onMiffSheetClosed: {},
        onMiffToggled: { _ in },
        onAddressChanged: { _ in },
        onSuggestionChosen: { _ in },
        onAddressCleared: {},
        onRetrySearch: {},
        onRadiusChanged: { _ in },
        onTypeSelected: { _ in },
        onCategorySelected: { _ in },
        onSave: {},
        onDeleteRequested: {},
        onDeleteDismissed: {},
        onDeleteConfirmed: {}
    )
}

/// Ce que l'hôte fait quand le formulaire dit qu'il est fini ou qu'il faut l'offre.
struct PlaceFormExits {
    let onEnd: (PlaceFormEnd) -> Void
    /// L'offre doit s'ouvrir ; vrai quand elle REMPLACE le formulaire (la limite trouvée à l'ouverture : il n'y a rien à offrir).
    let onPaywall: (PaywallReason, _ replacesForm: Bool) -> Void
}

/// Le formulaire relié à son view model. `end` et `paywall` sont des drapeaux à usage unique : l'hôte les consomme.
@MainActor
struct PlaceFormView: View {
    @StateObject private var viewModel: PlaceFormViewModel
    private let onBack: () -> Void
    private let exits: PlaceFormExits

    init(
        placeId: String?,
        miffId: String?,
        dependencies: PlaceFormDependencies,
        onBack: @escaping () -> Void,
        exits: PlaceFormExits
    ) {
        _viewModel = StateObject(
            wrappedValue: PlaceFormViewModel(placeId: placeId, miffId: miffId, dependencies: dependencies)
        )
        self.onBack = onBack
        self.exits = exits
    }

    var body: some View {
        let model = viewModel
        PlaceFormContent(
            state: model.state,
            actions: PlaceFormActions(
                onBack: onBack,
                onRetryLoad: { model.retry() },
                onNameChanged: { model.onNameChanged($0) },
                onMiffSheetOpened: { model.onMiffSheetOpened() },
                onMiffSheetClosed: { model.onMiffSheetClosed() },
                onMiffToggled: { model.onMiffToggled($0) },
                onAddressChanged: { model.onAddressChanged($0) },
                onSuggestionChosen: { model.onSuggestionChosen($0) },
                onAddressCleared: { model.onAddressCleared() },
                onRetrySearch: { model.onRetrySearch() },
                onRadiusChanged: { model.onRadiusChanged($0) },
                onTypeSelected: { model.onTypeSelected($0) },
                onCategorySelected: { model.onCategorySelected($0) },
                onSave: { model.onSave() },
                onDeleteRequested: { model.onDeleteRequested() },
                onDeleteDismissed: { model.onDeleteDismissed() },
                onDeleteConfirmed: { model.onDeleteConfirmed() }
            )
        )
        .onChange(of: model.state.end) { end in
            if let end {
                exits.onEnd(end)
            }
        }
        .onChange(of: model.state.paywall) { reason in
            if let reason {
                let state = model.state
                // La limite trouvée à l'ouverture : le formulaire n'a rien à offrir, l'offre le remplace.
                let replaces = reason == .limit && state.mode == .create && state.owned.isEmpty
                model.onPaywallHandled()
                exits.onPaywall(reason, replaces)
            }
        }
        // Ouvert avec un lieu qui n'existe déjà plus (ou une limite trouvée à l'ouverture) : l'état est déjà là au premier dessin.
        .onAppear {
            if let end = model.state.end {
                exits.onEnd(end)
            }
        }
    }
}

/// Le formulaire « Nouveau lieu » / « Modifier le lieu » (maquettes `design/exports/lieux/claude/01` à `06`, `20`). Sans état
/// propre. Le nom et l'adresse tapés sont cachés dans le sélecteur d'applications.
struct PlaceFormContent: View {
    let state: PlaceFormState
    let actions: PlaceFormActions

    @Environment(.dynamicTypeSize) private var dynamicTypeSize

    private var title: String {
        L10n.string(state.mode == .create ? "place_form_title_new" : "place_form_title_edit")
    }

    var body: some View {
        VStack(spacing: 0) {
            MiffHeader(title: title, onBack: actions.onBack)
                .padding(.horizontal, Spacing.lg)
                .padding(.top, Spacing.sm)
            content
        }
        .background(colorBgCanvas.color.ignoresSafeArea())
        .hidesInAppSwitcher()
    }

    @ViewBuilder
    private var content: some View {
        if state.loading {
            ScrollView {
                LoadingIndicator()
                    .padding(.top, Spacing.xl)
            }
        } else if let failure = state.loadFailed {
            loadFailure(failure)
        } else {
            // Aux tailles de texte d'accessibilité le pied (message, « Enregistrer », « Supprimer ») prendrait la moitié de l'écran,
            // surtout clavier ouvert : il défile alors avec le formulaire au lieu d'être fixé en bas.
            let inline = dynamicTypeSize.isAccessibilitySize
            PlaceFormBody(state: state, actions: actions, includesFoot: inline)
            if !inline {
                PlaceFormFoot(state: state, actions: actions)
            }
        }
    }

    private func loadFailure(_ failure: PlaceMessage) -> some View {
        ScrollView {
            VStack(spacing: Spacing.sm) {
                MiffNotice(text: L10n.string(failure == .offline ? "miff_message_offline" : "place_form_unavailable"))
                Button(action: actions.onRetryLoad) {
                    Text("auth_retry_action")
                }
                .buttonStyle(.miffSecondaryFullWidth)
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.top, Spacing.md)
        }
    }
}

/// Les sections du formulaire, dans l'ordre : Nom, Miffs, Adresse, Périmètre, Type, Catégorie.
struct PlaceFormBody: View {
    let state: PlaceFormState
    let actions: PlaceFormActions
    var includesFoot = false

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.lg) {
                    PlaceNameSection(state: state, actions: actions)
                    PlaceMiffsSection(state: state, actions: actions)
                    PlaceAddressSection(state: state, actions: actions)
                        .id(PlaceFormBody.addressAnchor)
                    PlaceRadiusSection(state: state, actions: actions)
                    PlaceTypeSection(state: state, actions: actions)
                    PlaceCategorySection(state: state, actions: actions)
                    if includesFoot {
                        PlaceFormFoot(state: state, actions: actions, isInline: true)
                    }
                    Spacer().frame(height: Spacing.sm)
                }
                .padding(.horizontal, Spacing.lg)
                .padding(.vertical, Spacing.sm)
            }
            .scrollDismissesKeyboard(.interactively)
            // Le clavier ne doit pas cacher la liste : le formulaire défile pour mettre le champ et ses suggestions en vue
            // (maquette 02).
            .onChange(of: state.suggestions) { suggestions in
                if suggestions != .idle {
                    withAnimation {
                        proxy.scrollTo(PlaceFormBody.addressAnchor, anchor: .top)
                    }
                }
            }
        }
    }

    private static let addressAnchor = "place-form-address"
}

/// Le pied du formulaire, toujours atteignable : le message calme, « Enregistrer », et (modification) « Supprimer ce lieu ».
struct PlaceFormFoot: View {
    let state: PlaceFormState
    let actions: PlaceFormActions
    /// Dans le défilement du formulaire (texte très grand) : pas de marges ni de fond propres.
    var isInline = false

    private var saveKey: LocalizedStringKey {
        state.saving ? "place_saving" : "place_save_action"
    }

    private var messageText: String? {
        guard let message = state.message else {
            return nil
        }
        switch message {
        case .offline:
            return L10n.string("miff_message_offline")
        case .generic:
            return L10n.string("miff_message_generic")
        case .miffGone:
            return L10n.string("place_message_miff_gone")
        case .idTaken:
            return L10n.string("ios_place_message_id_taken")
        }
    }

    var body: some View {
        VStack(spacing: Spacing.xs) {
            if state.categoryMissing && state.category == nil && !state.busy {
                // « Enregistrer » est grisé et la personne doit savoir pourquoi (un lieu écrit avant les catégories).
                PlaceInfoLine(text: L10n.string("place_save_hint_category"))
            }
            if let messageText {
                MiffNotice(text: messageText, isError: state.message != .offline)
            }
            Button(action: actions.onSave) {
                Text(saveKey)
            }
            .buttonStyle(.miffPrimaryFullWidth)
            .disabled(!state.canSave)
            if state.mode == .edit {
                Button(action: actions.onDeleteRequested) {
                    Text("place_delete_action")
                }
                .buttonStyle(.miffSensitiveFullWidth)
                .disabled(state.busy)
            }
        }
        .padding(.horizontal, isInline ? 0 : Spacing.lg)
        .padding(.vertical, Spacing.sm)
        .background(colorBgCanvas.color)
        .sheet(isPresented: deleteBinding) {
            PlaceDeleteSheet(state: state, actions: actions)
        }
    }

    private var deleteBinding: Binding<Bool> {
        Binding(
            get: { state.deleteDialogOpen },
            set: { newValue in
                if !newValue {
                    actions.onDeleteDismissed()
                }
            }
        )
    }
}

/// La question avant de supprimer (maquette 07) : la même fenêtre que celles de la Miff, au ton sensible (bouton blanc au trait
/// rouge, jamais de fond rouge). Un lieu dans plusieurs Miffs : la liste de celles où il est MAINTENANT.
struct PlaceDeleteSheet: View {
    let state: PlaceFormState
    let actions: PlaceFormActions

    var body: some View {
        let attached = state.attachedMiffs
        let several = attached.count > 1
        MiffConfirmSheet(
            content: MiffConfirmContent(
                title: L10n.string("place_delete_title"),
                message: L10n.string(several ? "place_delete_body_several" : "place_delete_body"),
                confirmLabel: L10n.string(state.deleting ? "place_deleting" : "place_delete_action"),
                isBusy: state.deleting,
                failure: state.deleteMessage,
                tone: .sensitive
            ),
            actions: MiffConfirmActions(onConfirm: actions.onDeleteConfirmed, onCancel: actions.onDeleteDismissed)
        ) {
            if several {
                VStack(spacing: Spacing.xs) {
                    ForEach(attached, id: \.id) { miff in
                        HStack(spacing: Spacing.sm) {
                            Image(systemName: "person.2")
                                .foregroundColor(colorActionPrimary.color)
                                .accessibilityHidden(true)
                            Text(verbatim: miff.name)
                                .miffTextStyle(textBodyBold16)
                                .foregroundColor(colorTextPrimary.color)
                            Spacer(minLength: 0)
                        }
                        .frame(minHeight: Spacing.minTouchTarget)
                    }
                }
            }
        }
    }
}
