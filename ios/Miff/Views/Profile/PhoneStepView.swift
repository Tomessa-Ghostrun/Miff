import SwiftUI

enum PhoneField: Hashable {
    case number
}

/// Les gestes de l'étape téléphone.
struct PhoneStepActions {
    let onCountrySelected: (String) -> Void
    let onPhoneChanged: (String) -> Void
    let onSubmit: () -> Void
}

/// Étape téléphone (maquettes « -2 » et « -6 », ticket §6.4) : le sélecteur de pays, le numéro avec son préfixe « +33 » et
/// un exemple, l'aide « sans le premier zéro », la mention OBLIGATOIRE de qui voit le numéro, l'erreur calme, et la flèche
/// ronde (grise tant qu'un chiffre n'a pas été saisi). `onBack` est la flèche de l'en-tête (nil quand cette étape est
/// seule : ancien compte sans numéro, ligne 2b).
struct PhoneStepContent: View {
    let state: ProfileUiState
    let tools: PhoneNumberTools
    let onBack: (() -> Void)?
    let actions: PhoneStepActions

    @State private var pickerOpen = false
    @State private var example: String?
    @FocusState private var focus: PhoneField?

    init(state: ProfileUiState, tools: PhoneNumberTools, onBack: (() -> Void)?, actions: PhoneStepActions) {
        self.state = state
        self.tools = tools
        self.onBack = onBack
        self.actions = actions
    }

    private var country: PhoneCountry {
        tools.country(region: state.regionCode, locale: .current)
    }

    var body: some View {
        ProfileScaffold(
            title: "profile_phone_title",
            // Pas de retour pendant l'écriture : l'écran ne doit pas changer sous elle.
            onBack: state.isWriting ? nil : onBack,
            nextButton: RoundNextButton(enabled: state.canSubmitPhone, isLoading: state.isWriting, action: submit)
        ) {
            CountryField(country: country, isEnabled: !state.isWriting) {
                pickerOpen = true
            }
            Spacer().frame(height: Spacing.md)
            numberField
            Spacer().frame(height: Spacing.sm)
            Text("profile_phone_help")
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextDescription.color)
                .frame(maxWidth: .infinity, alignment: .leading)
            Spacer().frame(height: Spacing.sm)
            // Obligatoire (décision N1) : qui voit le numéro, dit là où on le saisit, jamais caché.
            Text("profile_phone_visibility")
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextForm.color)
                .frame(maxWidth: .infinity, alignment: .leading)
            messageView
        }
        // Le pavé numérique n'a pas de touche de validation : une barre « OK » au-dessus du clavier valide la saisie.
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("ios_phone_keyboard_done") {
                    focus = nil
                    submit()
                }
            }
        }
        // Pendant l'écriture, le balayage depuis le bord ne ramène pas à l'étape profil.
        .background(SwipeBackLock(isLocked: state.isWriting))
        .task(id: state.regionCode) {
            let region = state.regionCode
            let phoneTools = tools
            example = await Task.detached { phoneTools.exampleHint(region: region) }.value
        }
        .sheet(isPresented: $pickerOpen) {
            CountryPickerSheet(
                tools: tools,
                selectedRegion: state.regionCode,
                onSelected: { region in
                    pickerOpen = false
                    actions.onCountrySelected(region)
                }
            )
            .presentationDetents([.large])
        }
    }

    private var numberField: some View {
        LabeledTextField(
            label: "profile_phone_number_label",
            text: Binding(get: { state.phoneText }, set: { actions.onPhoneChanged($0) }),
            focus: LabeledFieldFocus(field: PhoneField.number, binding: $focus),
            options: LabeledFieldOptions(
                errorText: numberError,
                placeholder: example,
                prefix: country.dialCode,
                isEnabled: !state.isWriting,
                submitLabel: .done,
                // Pavé numérique, sans correction orthographique ; le système peut proposer le numéro du téléphone
                // (saisie automatique), que l'app ne lit jamais d'elle-même.
                keyboard: .phonePad,
                contentType: .telephoneNumber
            ),
            onSubmit: submit
        )
    }

    private var numberError: String? {
        if state.phoneTypeRefused {
            return L10n.string("profile_phone_invalid_type")
        }
        return state.phoneInvalid ? L10n.string("profile_phone_invalid") : nil
    }

    @ViewBuilder
    private var messageView: some View {
        if let message = state.message {
            MessageText(text: text(for: message), isError: true)
                .padding(.top, Spacing.md)
        }
    }

    private func text(for message: ProfileMessage) -> String {
        switch message {
        case .accountUnavailable:
            return L10n.format("auth_error_account_unavailable", AppLinks.supportEmail)
        case .network:
            return L10n.string("auth_error_network")
        case .tryLater:
            return L10n.string("profile_save_failed_later")
        case .saveFailed:
            return L10n.string("profile_save_failed")
        case .photoUnusable:
            return L10n.string("profile_photo_unusable")
        }
    }

    private func submit() {
        if state.canSubmitPhone {
            focus = nil
            actions.onSubmit()
        }
    }
}

/// Le sélecteur « Pays » fermé des maquettes : drapeau, « France +33 » et le petit triangle. Un seul élément pour le lecteur
/// d'écran, qui dit le pays, l'indicatif, et qu'un toucher en propose un autre.
struct CountryField: View {
    let country: PhoneCountry
    let isEnabled: Bool
    let action: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text("profile_phone_country_label")
                .miffTextStyle(textBodyRegular)
                .foregroundColor(colorTextDescription.color)
                .accessibilityHidden(true)
            Button(action: action) {
                HStack(spacing: Spacing.sm) {
                    Text(verbatim: country.flag)
                        .miffTextStyle(textBodyRegular)
                    Text(verbatim: "\(country.name) \(country.dialCode)")
                        .miffTextStyle(textBodyRegular)
                        .foregroundColor(colorTextForm.color)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Image(systemName: "arrowtriangle.down.fill")
                        .font(.system(size: 10))
                        .foregroundColor(colorTextSecondary.color)
                }
                .padding(.horizontal, Spacing.md)
                .padding(.vertical, Spacing.sm)
                .frame(minHeight: Spacing.minTouchTarget)
                .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
                .overlay(
                    RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                        .stroke(colorBorderStrong.color, lineWidth: strokeHairline)
                )
            }
            .buttonStyle(.plain)
            .disabled(!isEnabled)
            .accessibilityLabel(
                Text(verbatim: L10n.format("profile_phone_country_field_a11y", country.name, country.dialCode))
            )
        }
    }
}

/// La liste de TOUS les pays (ticket §6.4) dans une feuille, avec un champ de recherche (accents et casse ignorés ; par
/// nom, code ou indicatif). La liste est construite en arrière-plan à la première ouverture.
struct CountryPickerSheet: View {
    let tools: PhoneNumberTools
    let selectedRegion: String
    let onSelected: (String) -> Void

    @State private var countries: [PhoneCountry] = []
    @State private var query = ""
    @FocusState private var searchFocus: SearchField?

    private enum SearchField: Hashable {
        case query
    }

    init(tools: PhoneNumberTools, selectedRegion: String, onSelected: @escaping (String) -> Void) {
        self.tools = tools
        self.selectedRegion = selectedRegion
        self.onSelected = onSelected
    }

    private var shown: [PhoneCountry] {
        countries.filter { PhoneInput.matchesSearch(country: $0, query: query) }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            Text("profile_phone_picker_title")
                .miffTextStyle(textTitleBold20)
                .foregroundColor(colorTextForm.color)
                .accessibilityAddTraits(.isHeader)
                .padding(.top, Spacing.lg)
            LabeledTextField(
                label: "profile_phone_picker_search",
                text: Binding(get: { query }, set: { query = String($0.prefix(PhoneInput.maxInputLength)) }),
                focus: LabeledFieldFocus(field: SearchField.query, binding: $searchFocus),
                options: LabeledFieldOptions(submitLabel: .search)
            )
            if !countries.isEmpty && shown.isEmpty {
                Text("profile_phone_picker_empty")
                    .miffTextStyle(textBodyRegular)
                    .foregroundColor(colorTextDescription.color)
            }
            List(shown) { country in
                row(country)
            }
            .listStyle(.plain)
        }
        .padding(.horizontal, Spacing.lg)
        .background(colorBgSurface.color.ignoresSafeArea())
        .task {
            let phoneTools = tools
            countries = await Task.detached { phoneTools.countries(locale: .current) }.value
        }
    }

    private func row(_ country: PhoneCountry) -> some View {
        let selected = country.region == selectedRegion
        return Button {
            onSelected(country.region)
        } label: {
            HStack(spacing: Spacing.md) {
                Text(verbatim: country.flag)
                    .miffTextStyle(textBodyRegular)
                Text(verbatim: country.name)
                    .miffTextStyle(selected ? textBodyBold16 : textBodyRegular)
                    .foregroundColor((selected ? colorActionPrimary : colorTextForm).color)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(verbatim: country.dialCode)
                    .miffTextStyle(textBodyRegular)
                    .foregroundColor(colorTextDescription.color)
            }
            .frame(minHeight: Spacing.minTouchTarget)
        }
        .accessibilityLabel(
            Text(verbatim: L10n.format("profile_phone_picker_item_a11y", country.name, country.dialCode))
        )
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}
