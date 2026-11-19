import PhotosUI
import SwiftUI

private enum MyInfoField: Hashable {
    case firstName
    case lastName
    case phone
}

/// Réglages > Mes informations (ticket `docs/tickets/modifier-infos-perso.md`, critères MI1 à MI20 ; à l'identique d'Android, ONB-3a) :
/// la photo (avec son crayon), le prénom, le nom, le pays et le numéro, et « Enregistrer » en bas. L'écran garde la mise en page de la
/// première version (photo en haut, puis les champs) ; aucune maquette n'existe pour cet écran (décision du PM du 2026-11-11).
///
/// Tant que le profil n'est pas lu : un indicateur calme ; s'il ne peut pas l'être : une phrase et « Réessayer ». Une fois lu, le
/// formulaire (`MyInfoEditor`) vit dans son propre view model, qui ne change pas quand l'en-tête du Profil reçoit la version enregistrée.
struct MyInfoView: View {
    let state: ProfileTabState
    let tools: PhoneNumberTools
    let makeViewModel: (ProfileSummary) -> MyInfoViewModel
    let onBack: () -> Void
    let onRetry: () -> Void

    /// Le dernier profil lu : si l'état repasse à « chargement » ou « illisible » pendant la saisie, le formulaire (et ce qui y est tapé)
    /// reste affiché au lieu d'être jeté.
    @State private var kept: ProfileSummary?

    init(
        state: ProfileTabState,
        tools: PhoneNumberTools,
        makeViewModel: @escaping (ProfileSummary) -> MyInfoViewModel,
        onBack: @escaping () -> Void,
        onRetry: @escaping () -> Void
    ) {
        self.state = state
        self.tools = tools
        self.makeViewModel = makeViewModel
        self.onBack = onBack
        self.onRetry = onRetry
    }

    private var shown: ProfileSummary? {
        if case .ready(let profile) = state {
            return profile
        }
        return kept
    }

    var body: some View {
        content
            .onAppear {
                kept = shown
            }
            .onChange(of: state) { _ in
                kept = shown
            }
    }

    @ViewBuilder
    private var content: some View {
        if let profile = shown {
            MyInfoEditor(profile: profile, tools: tools, makeViewModel: makeViewModel, onBack: onBack)
        } else {
            placeholder
        }
    }

    @ViewBuilder
    private var placeholder: some View {
        switch state {
        case .ready:
            EmptyView()
        case .loading:
            MiffFrame(title: L10n.string("settings_my_info_title"), onBack: onBack, usesCanvas: true) {
                LoadingIndicator()
            }
        case .unavailable:
            MiffFrame(title: L10n.string("settings_my_info_title"), onBack: onBack, usesCanvas: true) {
                MiffNotice(text: L10n.string("settings_my_info_load_failed"))
                Button(action: onRetry) {
                    Text("auth_retry_action")
                }
                .buttonStyle(.miffSecondaryFullWidth)
            }
        }
    }
}

/// Le formulaire. Les gestes passent tous par `MyInfoViewModel` ; l'écran ne garde que ce qui est purement visuel (quelle feuille est
/// ouverte, où est le focus).
private struct MyInfoEditor: View {
    @StateObject private var model: MyInfoViewModel
    let tools: PhoneNumberTools
    let onBack: () -> Void

    @State private var pickerPresented = false
    @State private var optionsPresented = false
    @State private var countriesPresented = false
    @State private var discardAsked = false
    @State private var pickerItem: PhotosPickerItem?
    @State private var example: String?
    @State private var phoneWasFocused = false
    @FocusState private var focus: MyInfoField?

    private static let photoSize: CGFloat = 96
    private static let badgeSize: CGFloat = 36

    init(
        profile: ProfileSummary,
        tools: PhoneNumberTools,
        makeViewModel: @escaping (ProfileSummary) -> MyInfoViewModel,
        onBack: @escaping () -> Void
    ) {
        _model = StateObject(wrappedValue: makeViewModel(profile))
        self.tools = tools
        self.onBack = onBack
    }

    var body: some View {
        let state = model.state
        MiffFrame(title: L10n.string("settings_my_info_title"), onBack: requestBack, usesCanvas: true) {
            photoBlock(state)
            namesBlock(state)
            phoneBlock(state)
            saveBlock(state)
        }
        // Avec des changements non enregistrés, ou pendant l'envoi, le geste de retour ne part pas : la flèche, qui demande confirmation,
        // est la seule sortie (comme le retour d'Android).
        .swipeBack(for: .myInfo(hasUnsavedWork: state.isSaving || state.hasEdits))
        .photosPicker(isPresented: $pickerPresented, selection: $pickerItem, matching: .images)
        .confirmationDialog("profile_photo_sheet_title", isPresented: $optionsPresented, titleVisibility: .visible) {
            Button("profile_photo_choose", action: chooseAgain)
            Button("profile_photo_remove", role: .destructive) {
                model.onRemovePhoto()
            }
        }
        .fullScreenCover(isPresented: cropIsPresented) {
            if let crop = model.state.crop {
                PhotoCropView(
                    crop: crop,
                    actions: PhotoCropActions(
                        onChanged: { model.onCropChanged(zoom: $0, centerX: $1, centerY: $2) },
                        onConfirm: { model.onCropConfirmed() },
                        onCancel: { model.onCropCancelled() }
                    )
                )
            }
        }
        .sheet(isPresented: $countriesPresented) {
            CountryPickerSheet(
                tools: tools,
                selectedRegion: state.regionCode,
                onSelected: { region in
                    countriesPresented = false
                    model.onCountrySelected(region)
                }
            )
            .presentationDetents([.large])
        }
        // Deux boutons de même poids : quitter n'est pas poussé plus que rester (aucun rôle « annuler », qui serait mis en gras).
        .alert(
            Text("settings_my_info_discard_title"),
            isPresented: $discardAsked,
            actions: {
                Button("settings_my_info_discard_cancel") {}
                Button("settings_my_info_discard_confirm", action: onBack)
            },
            message: {
                Text("settings_my_info_discard_body")
            }
        )
        // Le pavé numérique n'a pas de touche de validation : une barre « OK » au-dessus du clavier le range (et le numéro est alors contrôlé).
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("ios_phone_keyboard_done") {
                    focus = nil
                }
            }
        }
        .onChange(of: pickerItem) { item in
            if let item {
                load(item)
            }
        }
        // Le numéro est signalé quand la personne quitte le champ (critère MI3), pas à chaque frappe.
        .onChange(of: focus) { newFocus in
            if phoneWasFocused && newFocus != .phone {
                model.onPhoneFocusLost()
            }
            phoneWasFocused = newFocus == .phone
        }
        .task(id: state.regionCode) {
            let region = state.regionCode
            let phoneTools = tools
            example = await Task.detached { phoneTools.exampleHint(region: region) }.value
        }
    }
}

/// Les blocs du formulaire : photo, noms, téléphone, enregistrer, retour.
extension MyInfoEditor {
    // MARK: - Photo

    private func photoBlock(_ state: MyInfoUiState) -> some View {
        VStack(spacing: Spacing.sm) {
            Button(action: photoTapped) {
                ZStack(alignment: .bottomTrailing) {
                    MiffAvatar(
                        jpeg: state.avatar,
                        initials: Initials.of(first: state.firstName, last: state.lastName),
                        size: Self.photoSize
                    )
                    pencilBadge
                }
                .frame(minWidth: Spacing.minTouchTarget, minHeight: Spacing.minTouchTarget)
            }
            .buttonStyle(.plain)
            .disabled(state.isSaving)
            // Un seul bouton pour le lecteur d'écran : « Modifier la photo de profil ».
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("settings_my_info_photo_edit_a11y")
            .accessibilityAddTraits(.isButton)
            Text("profile_photo_visibility")
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextDescription.color)
                .multilineTextAlignment(.center)
            if state.message == .photoUnusable {
                MessageText(text: L10n.string("profile_photo_unusable"), isError: true)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Spacing.sm)
    }

    /// Le petit crayon blanc cerclé de bleu en bas à droite de la photo (comme sur Android). Décoratif.
    private var pencilBadge: some View {
        Image(systemName: "pencil")
            .font(.system(size: 16, weight: .regular))
            .foregroundColor(colorActionPrimary.color)
            .frame(width: Self.badgeSize, height: Self.badgeSize)
            .background(Circle().fill(colorBgSurface.color))
            .overlay(Circle().stroke(colorActionPrimary.color, lineWidth: strokeAccent))
            .accessibilityHidden(true)
    }

    /// Sans photo, le sélecteur s'ouvre ; avec une photo, le choix « une autre photo / supprimer ». Pas d'appareil photo ici : voir
    /// `ios/README.md` (le texte d'usage de la caméra ne couvre que « Donner des nouvelles »).
    private func photoTapped() {
        if model.state.avatar == nil {
            pickerPresented = true
        } else {
            optionsPresented = true
        }
    }

    /// Une feuille ne peut pas se présenter pendant que la précédente se ferme : on attend la fin de sa fermeture.
    private func chooseAgain() {
        Task {
            try? await Task.sleep(nanoseconds: 400_000_000)
            pickerPresented = true
        }
    }

    private func load(_ item: PhotosPickerItem) {
        Task {
            let data = try? await item.loadTransferable(type: Data.self)
            pickerItem = nil
            if let data {
                model.onPhotoChosen(data)
            } else {
                model.onPhotoLoadFailed()
            }
        }
    }

    private var cropIsPresented: Binding<Bool> {
        Binding(
            get: { model.state.crop != nil },
            set: { presented in
                if !presented {
                    model.onCropCancelled()
                }
            }
        )
    }

    // MARK: - Noms

    private func namesBlock(_ state: MyInfoUiState) -> some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            nameField(.firstName, state: state)
            nameField(.lastName, state: state)
        }
    }

    private func nameField(_ field: MyInfoField, state: MyInfoUiState) -> some View {
        let isFirst = field == .firstName
        let value = isFirst ? state.firstName : state.lastName
        let limitHit = isFirst ? state.firstNameLimitHit : state.lastNameLimitHit
        let blank = isFirst ? state.firstNameBlank : state.lastNameBlank
        let counter = MyInfoUiState.counter(for: value)
        let length = Int32(value.utf16.count)
        let spoken = L10n.format("profile_name_counter_a11y", length, Int32(ProfileForm.maxNameLength))
        var errorText: String?
        if limitHit {
            errorText = L10n.string("profile_name_too_long")
        } else if blank {
            errorText = L10n.string("settings_my_info_name_required")
        }
        return LabeledTextField(
            label: isFirst ? "profile_first_name_label" : "profile_last_name_label",
            text: Binding(
                get: { value },
                set: { isFirst ? model.onFirstNameChanged($0) : model.onLastNameChanged($0) }
            ),
            focus: LabeledFieldFocus(field: field, binding: $focus),
            options: LabeledFieldOptions(
                errorText: errorText,
                counter: counter,
                counterSpoken: counter == nil ? nil : spoken,
                isEnabled: !state.isSaving,
                submitLabel: .next,
                contentType: isFirst ? .givenName : .familyName,
                capitalization: .words
            ),
            onSubmit: {
                focus = isFirst ? .lastName : .phone
            }
        )
    }

    // MARK: - Téléphone

    private func phoneBlock(_ state: MyInfoUiState) -> some View {
        let country = tools.country(region: state.regionCode, locale: AppLanguage.locale)
        return VStack(alignment: .leading, spacing: Spacing.sm) {
            CountryField(country: country, isEnabled: !state.isSaving) {
                countriesPresented = true
            }
            LabeledTextField(
                label: "settings_my_info_phone_label",
                text: Binding(get: { state.phoneText }, set: { model.onPhoneChanged($0) }),
                focus: LabeledFieldFocus(field: MyInfoField.phone, binding: $focus),
                options: LabeledFieldOptions(
                    errorText: phoneError(state),
                    placeholder: example,
                    prefix: country.dialCode,
                    isEnabled: !state.isSaving,
                    submitLabel: .done,
                    // Pavé numérique, sans correction orthographique ; le système peut proposer le numéro du téléphone (saisie
                    // automatique), que l'app ne lit jamais d'elle-même.
                    keyboard: .phonePad,
                    contentType: .telephoneNumber
                ),
                onSubmit: {
                    focus = nil
                }
            )
            Text("profile_phone_help")
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextDescription.color)
                .frame(maxWidth: .infinity, alignment: .leading)
            // Obligatoire (décision N1) : qui voit le numéro, dit là où on le saisit, jamais caché.
            Text("profile_phone_visibility")
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextForm.color)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func phoneError(_ state: MyInfoUiState) -> String? {
        if state.phoneTypeRefused {
            return L10n.string("profile_phone_invalid_type")
        }
        return state.phoneInvalid ? L10n.string("profile_phone_invalid") : nil
    }

    // MARK: - Enregistrer

    private func saveBlock(_ state: MyInfoUiState) -> some View {
        VStack(spacing: Spacing.md) {
            messageView(state.message)
            if state.isSaving {
                LoadingIndicator()
            }
            Button {
                focus = nil
                model.onSave()
            } label: {
                Text("settings_my_info_save_action")
            }
            .buttonStyle(.miffPrimaryFullWidth)
            .disabled(!state.canSave)
        }
        .padding(.top, Spacing.sm)
    }

    @ViewBuilder
    private func messageView(_ message: MyInfoMessage?) -> some View {
        switch message {
        case .saved:
            MessageText(text: L10n.string("settings_my_info_saved"), isError: false)
        case .syncPending:
            MessageText(text: L10n.string("settings_my_info_sync_pending"), isError: false)
        case .network:
            MessageText(text: L10n.string("auth_error_network"), isError: true)
        case .saveFailed:
            MessageText(text: L10n.string("profile_save_failed"), isError: true)
        case .tryLater:
            MessageText(text: L10n.string("ios_myinfo_save_failed_later"), isError: true)
        case .photoUnusable, .none:
            EmptyView()
        }
    }

    // MARK: - Retour

    /// La flèche : rien ne bouge pendant l'envoi ; des changements non enregistrés ne sont jamais abandonnés sans demander.
    private func requestBack() {
        let state = model.state
        if state.isSaving {
            return
        }
        if state.hasEdits {
            discardAsked = true
        } else {
            onBack()
        }
    }
}
