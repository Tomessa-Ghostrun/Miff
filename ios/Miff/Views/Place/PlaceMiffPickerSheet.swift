import SwiftUI

/// La feuille de choix des Miffs (maquette 05) : les Miffs que la personne a CRÉÉES, une case chacune, 5 au plus ; « Dernière
/// rejointe » repère la Miff proposée au départ ; « Terminé » est grisé tant qu'aucune n'est cochée.
struct PlaceMiffPickerSheet: View {
    let state: PlaceFormState
    let actions: PlaceFormActions

    private var count: Int {
        state.selectedMiffIds.count
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                Text("place_miffs_sheet_title")
                    .miffTextStyle(textTitleBold20)
                    .foregroundColor(colorTextPrimary.color)
                    .accessibilityAddTraits(.isHeader)
                Text("place_miffs_sheet_subtitle")
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextDescription.color)
                VStack(spacing: 0) {
                    ForEach(state.owned, id: \.id) { miff in
                        PlaceMiffRow(
                            miff: miff,
                            isChecked: state.selectedMiffIds.contains(miff.id),
                            isLastJoined: miff.id == state.lastJoinedId,
                            isLocked: count >= PlaceLimits.maxMiffs && !state.selectedMiffIds.contains(miff.id)
                        ) {
                            actions.onMiffToggled(miff.id)
                        }
                    }
                }
                footer
                Button(action: actions.onMiffSheetClosed) {
                    Text("place_miffs_sheet_done")
                }
                .buttonStyle(.miffPrimaryFullWidth)
                .disabled(count == 0)
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.vertical, Spacing.lg)
        }
        .background(colorBgSurface.color.ignoresSafeArea())
        .presentationDetents([.medium, .large])
        .hidesInAppSwitcher()
    }

    /// « 3 Miffs sur 5 au maximum », « Choisis au moins une Miff. », « Un lieu peut être attaché à 5 Miffs au maximum. ».
    @ViewBuilder
    private var footer: some View {
        if count == 0 {
            PlaceInfoLine(text: L10n.string("place_miffs_none_selected"))
        } else if count >= PlaceLimits.maxMiffs {
            PlaceInfoLine(text: L10n.string("place_miffs_max_reached"))
        } else {
            Text(verbatim: L10n.format("place_miffs_count", count, PlaceLimits.maxMiffs))
                .miffTextStyle(textBodyCaption)
                .foregroundColor(colorTextDescription.color)
        }
    }
}

/// Une ligne de la feuille : une case, le nom de la Miff, « Dernière rejointe » quand c'est elle. Grisée (et dite verrouillée)
/// quand 5 sont déjà cochées.
struct PlaceMiffRow: View {
    let miff: OwnedMiff
    let isChecked: Bool
    let isLastJoined: Bool
    let isLocked: Bool
    let onToggle: () -> Void

    var body: some View {
        Button(action: onToggle) {
            HStack(spacing: Spacing.md) {
                Image(systemName: isChecked ? "checkmark.square.fill" : "square")
                    .font(.system(size: 22))
                    .foregroundColor((isChecked ? colorActionPrimary : colorTextDescription).color)
                    .accessibilityHidden(true)
                Text(verbatim: miff.name)
                    .miffTextStyle(textBodyBold16)
                    .foregroundColor(colorTextPrimary.color)
                    .multilineTextAlignment(.leading)
                Spacer(minLength: 0)
                if isLastJoined {
                    Text("place_miffs_last_joined")
                        .miffTextStyle(textCaptionBold12)
                        .foregroundColor(colorActionPrimary.color)
                        .padding(.horizontal, Spacing.sm)
                        .padding(.vertical, Spacing.xs)
                        .background(Capsule().fill(colorActionPrimaryTint10.color))
                }
            }
            .frame(minHeight: Spacing.minTouchTarget + Spacing.xs)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .opacity(isLocked ? 0.5 : 1)
        .disabled(isLocked)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: miff.name))
        .accessibilityValue(Text(verbatim: isChecked ? L10n.string("place_type_selected_a11y") : ""))
        .accessibilityAddTraits(isChecked ? [.isButton, .isSelected] : .isButton)
    }
}
