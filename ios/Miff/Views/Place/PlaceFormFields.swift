import SwiftUI

// Les trois premières sections du formulaire d'un lieu : le nom, les Miffs, l'adresse (avec ses suggestions).

/// « Nom » : 2 à 50 caractères, compteur dès le premier caractère, « 2 caractères minimum » au toucher de « Enregistrer ».
struct PlaceNameSection: View {
    let state: PlaceFormState
    let actions: PlaceFormActions

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            MiffSectionHeader(
                systemImage: "pencil",
                title: L10n.string("place_name_title"),
                description: L10n.string("place_name_help")
            )
            PlaceTextField(
                text: Binding(get: { state.name }, set: { actions.onNameChanged($0) }),
                placeholder: L10n.string("place_name_placeholder"),
                spokenLabel: L10n.string("place_name_a11y"),
                isEnabled: !state.busy,
                capitalization: .words
            )
            if state.nameTooShort {
                MessageText(text: L10n.string("place_name_too_short"), isError: true)
            } else if state.nameLimitReached {
                Text("place_name_limit")
                    .miffTextStyle(textBodyCaption)
                    .foregroundColor(colorTextDescription.color)
            }
            if !state.name.isEmpty {
                Text(verbatim: L10n.format("place_name_counter", state.name.utf16.count, PlaceLimits.nameMax))
                    .miffTextStyle(textBodyCaption)
                    .foregroundColor(colorTextDescription.color)
                    .frame(maxWidth: .infinity, alignment: .trailing)
            }
        }
        .onChange(of: state.nameLimitReached) { reached in
            if reached {
                Announcer.announce(L10n.string("place_name_limit"))
            }
        }
    }
}

/// « Miffs » (« Miff » s'il n'y en a qu'une) : les Miffs que la personne a créées, 5 au plus ; une seule : pas de sélecteur, la Miff
/// est montrée (maquette 05E). Sous le champ, la phrase « qui voit quoi » (§6.8).
struct PlaceMiffsSection: View {
    let state: PlaceFormState
    let actions: PlaceFormActions

    private var chosenMiffs: [OwnedMiff] {
        state.owned.filter { state.selectedMiffIds.contains($0.id) }
    }

    var body: some View {
        let single = state.singleMiff
        VStack(alignment: .leading, spacing: Spacing.sm) {
            MiffSectionHeader(
                systemImage: "person.2",
                title: L10n.string(single ? "place_miff_title" : "place_miffs_title"),
                description: L10n.string(single ? "place_miff_help" : "place_miffs_help")
            )
            field(single: single)
            // Une demande n'est vue que du créateur tant qu'elle n'est pas acceptée : la phrase d'un lieu (« les membres verront ce lieu »)
            // serait fausse.
            if state.mode != .request {
                PlaceInfoLine(text: L10n.string(single ? "place_miff_visibility" : "place_miffs_visibility"))
            }
        }
        .sheet(isPresented: sheetBinding) {
            PlaceMiffPickerSheet(state: state, actions: actions)
        }
    }

    private func field(single: Bool) -> some View {
        HStack(alignment: .center, spacing: Spacing.sm) {
            LazyVGrid(
                columns: [GridItem(.adaptive(minimum: 150), spacing: Spacing.sm)],
                alignment: .leading,
                spacing: Spacing.sm
            ) {
                ForEach(chosenMiffs, id: \.id) { miff in
                    PlaceMiffChip(name: miff.name, canRemove: !single && !state.busy) {
                        actions.onMiffToggled(miff.id)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if !single {
                Button(action: actions.onMiffSheetOpened) {
                    Image(systemName: "chevron.down")
                        .foregroundColor(colorActionPrimary.color)
                        .frame(width: Spacing.minTouchTarget, height: Spacing.minTouchTarget)
                }
                .disabled(!state.canEditAddress)
                .accessibilityLabel(Text(verbatim: L10n.string("place_miffs_open_a11y")))
            }
        }
        .padding(.horizontal, Spacing.sm)
        .padding(.vertical, Spacing.sm)
        .frame(minHeight: Spacing.minTouchTarget)
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
        .overlay(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .stroke(colorBorderStrong.color, lineWidth: strokeHairline)
        )
    }

    private var sheetBinding: Binding<Bool> {
        Binding(
            get: { state.miffSheetOpen },
            set: { newValue in
                if !newValue {
                    actions.onMiffSheetClosed()
                }
            }
        )
    }
}

/// Une pastille de Miff cochée, avec sa croix de retrait (44 pt) quand elle peut être retirée.
struct PlaceMiffChip: View {
    let name: String
    let canRemove: Bool
    let onRemove: () -> Void

    var body: some View {
        HStack(spacing: Spacing.xs) {
            Text(verbatim: name)
                .miffTextStyle(textBodyBold14)
                .foregroundColor(colorActionPrimary.color)
                .lineLimit(2)
            if canRemove {
                Button(action: onRemove) {
                    Image(systemName: "xmark")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(colorActionPrimary.color)
                        .frame(width: Spacing.minTouchTarget, height: Spacing.minTouchTarget)
                }
                .accessibilityLabel(Text(verbatim: L10n.format("place_miffs_remove_a11y", name)))
            }
        }
        .padding(.leading, Spacing.sm + Spacing.xs)
        .padding(.trailing, canRemove ? 0 : Spacing.sm + Spacing.xs)
        .frame(minHeight: Spacing.minTouchTarget)
        .background(Capsule().fill(colorActionPrimaryTint10.color))
        .overlay(Capsule().stroke(colorBorderAction.color, lineWidth: strokeHairline))
    }
}

/// « Adresse » : on tape, des suggestions arrivent dès 3 caractères, on en CHOISIT une (la latitude et la longitude viennent de ce
/// choix). Taper après avoir choisi efface le choix.
struct PlaceAddressSection: View {
    let state: PlaceFormState
    let actions: PlaceFormActions

    var body: some View {
        let chosen = state.chosen != nil
        VStack(alignment: .leading, spacing: Spacing.sm) {
            MiffSectionHeader(
                systemImage: "mappin.and.ellipse",
                title: L10n.string("place_address_title"),
                description: L10n.string("place_address_help")
            )
            PlaceTextField(
                text: Binding(get: { state.addressText }, set: { actions.onAddressChanged($0) }),
                placeholder: L10n.string("place_address_placeholder"),
                spokenLabel: L10n.string("place_address_a11y"),
                leadingSymbol: chosen ? "mappin.and.ellipse" : "magnifyingglass",
                leadingIsActive: chosen,
                isEnabled: state.canEditAddress,
                isBusy: state.resolving,
                capitalization: .words,
                onClear: actions.onAddressCleared
            )
            suggestions
        }
    }

    @ViewBuilder
    private var suggestions: some View {
        switch state.suggestions {
        case .idle:
            idleHint
        case .loading:
            PlaceSuggestionsPanel {
                ForEach(0..<3, id: \.self) { index in
                    PlaceSkeletonRow(announce: index == 0)
                }
            }
        case .found(let items):
            PlaceSuggestionsPanel {
                ForEach(items, id: \.id) { item in
                    PlaceSuggestionRow(item: item) {
                        actions.onSuggestionChosen(item)
                    }
                }
            }
        case .noResults:
            PlaceSuggestionsPanel {
                PlaceSuggestionMessage(symbol: "magnifyingglass", text: L10n.string("place_address_none"), retry: nil)
            }
        case .unavailable:
            PlaceSuggestionsPanel {
                PlaceSuggestionMessage(
                    symbol: "icloud.slash",
                    text: L10n.string("place_address_unavailable"),
                    retry: actions.onRetrySearch
                )
            }
        }
    }

    /// Un lieu écrit avant que les adresses soient obligatoires : on dit quoi faire. Une adresse tapée en entier mais pas choisie :
    /// le bouton reste grisé et une phrase calme dit pourquoi (maquette 04F).
    @ViewBuilder
    private var idleHint: some View {
        let typed = state.addressText.trimmingCharacters(in: .whitespacesAndNewlines).utf16.count
            >= PlaceLimits.suggestionMinChars
        if state.chosen == nil && !state.resolving {
            if !typed && state.addressMissing {
                PlaceInfoLine(text: L10n.string("place_address_legacy_hint"))
            } else if typed {
                PlaceInfoLine(text: L10n.string("place_address_pick_hint"))
            }
        }
    }
}

/// Le cadre blanc sous le champ d'adresse qui contient la liste, un message ou des lignes de chargement.
struct PlaceSuggestionsPanel<Content: View>: View {
    let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        VStack(spacing: 0) {
            content
        }
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .fill(colorBgSurface.color)
                .miffShadow(shadowDefault)
        )
        .overlay(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .stroke(colorBorderDefault.color, lineWidth: strokeHairline)
        )
    }
}

/// Une suggestion : la rue et son numéro (ou le nom du lieu), puis le code postal, la ville, le pays.
struct PlaceSuggestionRow: View {
    let item: AddressSuggestion
    let onChoose: () -> Void

    var body: some View {
        Button(action: onChoose) {
            VStack(alignment: .leading, spacing: 0) {
                Text(verbatim: item.title)
                    .miffTextStyle(textBodyBold16)
                    .foregroundColor(colorTextForm.color)
                    .multilineTextAlignment(.leading)
                if !item.subtitle.isEmpty {
                    Text(verbatim: item.subtitle)
                        .miffTextStyle(textBodyRegular14)
                        .foregroundColor(colorTextDescription.color)
                        .multilineTextAlignment(.leading)
                }
            }
            .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget, alignment: .leading)
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.xs)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(
            Text(verbatim: item.subtitle.isEmpty ? item.title : "\(item.title), \(item.subtitle)")
        )
        .accessibilityHint(Text(verbatim: L10n.string("place_address_pick_hint")))
        .accessibilityAddTraits(.isButton)
    }
}

/// Une ligne grise de chargement (pas d'animation : « réduire les animations » est respecté d'office). La première annonce
/// « Recherche de l'adresse… » au lecteur d'écran.
struct PlaceSkeletonRow: View {
    let announce: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            RoundedRectangle(cornerRadius: radiusSm, style: .continuous)
                .fill(colorBgPlaceholder.color.opacity(0.5))
                .frame(width: 180, height: 14)
            RoundedRectangle(cornerRadius: radiusSm, style: .continuous)
                .fill(colorBgPlaceholder.color.opacity(0.3))
                .frame(width: 120, height: 11)
        }
        .frame(maxWidth: .infinity, minHeight: Spacing.minTouchTarget, alignment: .leading)
        .padding(.horizontal, Spacing.md)
        .padding(.vertical, Spacing.xs)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: announce ? L10n.string("place_address_loading_a11y") : ""))
        .accessibilityHidden(!announce)
    }
}

/// « On ne trouve pas cette adresse… » ou « La recherche d'adresse n'est pas disponible… » (avec « Réessayer »).
struct PlaceSuggestionMessage: View {
    let symbol: String
    let text: String
    let retry: (() -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            HStack(alignment: .top, spacing: Spacing.sm) {
                Image(systemName: symbol)
                    .foregroundColor(colorTextDescription.color)
                    .accessibilityHidden(true)
                Text(verbatim: text)
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextPrimary.color)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if let retry {
                Button(action: retry) {
                    Text("auth_retry_action")
                        .miffTextStyle(textBodyBold14)
                        .foregroundColor(colorActionPrimary.color)
                        .frame(minHeight: Spacing.minTouchTarget)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Spacing.md)
        .onAppear {
            MiffNoticeAnnouncements.announceOnce(text)
        }
    }
}
