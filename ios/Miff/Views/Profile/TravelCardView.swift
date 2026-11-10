import SwiftUI

// MODE VOYAGE, la carte du Profil (docs/tickets/mode-voyage.md §7, VOY-2I) : un interrupteur, puis (quand il est allumé) la ligne « Date de
// fin » qui ouvre le sélecteur de date du système, et une explication HONNÊTE de ce que voient les proches : la position reste partagée,
// seule la carte devient grise. Ouvert à TOUS les comptes : aucune offre, aucun abonnement. Toute la ligne de l'interrupteur est UN
// élément pour le lecteur d'écran (« Mode voyage, activé, jusqu'au 15 novembre 2026 ») ; l'avion est décoratif, l'état est dit par les mots.
// Rien n'est journalisé ni envoyé comme événement.

struct TravelCardView: View {
    @ObservedObject var model: TravelViewModel
    @Environment(\.scenePhase) private var scenePhase
    /// Le voyage gardé sur le téléphone est relu de temps en temps : sa fin peut avoir été traitée à l'ouverture de l'app, ou depuis un autre
    /// geste. Un petit fichier local, aucune lecture du serveur.
    @State private var tick = Timer.publish(every: 15, on: .main, in: .common).autoconnect()
    /// Le sélecteur est-il VRAIMENT à l'écran ? La confirmation attend qu'il soit parti (un message ne peut pas se poser sur une feuille en
    /// train de se fermer).
    @State private var pickerVisible = false

    private var period: TravelPeriod? {
        model.state.period
    }

    private var endDay: TravelDate? {
        period.flatMap { TravelDate.parse($0.untilDay) }
    }

    private func short(_ date: TravelDate) -> String {
        let today = TravelDate.today(nowMillis: Int(Date().timeIntervalSince1970 * 1000), zone: .current)
        return TravelDayFormat.short(TravelDay(date: date, showYear: date.year != today.year))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            VStack(alignment: .leading, spacing: 0) {
                TravelSwitchRow(
                    isOn: period != nil && endDay != nil,
                    working: model.state.working,
                    shortDay: endDay.map { short($0) },
                    spokenDay: endDay.map { TravelDayFormat.spoken($0) },
                    action: model.onToggle
                )
                if let endDay {
                    TravelEndDateRow(
                        written: TravelDayFormat.withYear(endDay),
                        spoken: TravelDayFormat.spoken(endDay),
                        working: model.state.working,
                        action: model.onChangeDate
                    )
                    Text(verbatim: L10n.format("voyage_card_explain", short(endDay)))
                        .miffTextStyle(textBodyRegular14)
                        .foregroundColor(colorTextDescription.color)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal, Spacing.md)
                        .padding(.bottom, Spacing.md)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
            .overlay(
                RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                    .stroke(colorBorderDefault.color, lineWidth: strokeHairline)
            )
            .miffShadow(shadowDefault)
            if let message = model.state.message {
                MiffNotice(text: TravelCardTexts.text(for: message), isError: TravelCardTexts.isError(message))
            }
        }
        .sheet(isPresented: pickerBinding, onDismiss: { pickerVisible = false }, content: pickerSheet)
        .alert(
            Text("voyage_confirm_title"),
            isPresented: confirmBinding,
            actions: {
                Button("voyage_confirm_action") { model.onConfirm() }
                Button("voyage_confirm_cancel", role: .cancel) { model.onConfirmDismissed() }
            },
            message: { Text(verbatim: confirmBody) }
        )
        .onAppear { model.reload() }
        .onChange(of: scenePhase) { phase in
            if phase == .active {
                model.reload()
            }
        }
        .onReceive(tick) { _ in model.reload() }
    }

    // MARK: - Le sélecteur et la confirmation

    private var pickerBinding: Binding<Bool> {
        Binding(
            get: { model.state.picker != nil },
            set: { shown in
                if !shown {
                    model.onPickerDismissed()
                }
            }
        )
    }

    /// Les deux boutons de la feuille de confirmation ferment toujours la feuille : le système n'a rien à fermer lui-même (aucune action
    /// quand la liaison dit « fermé »), le modèle efface la demande.
    private var confirmBinding: Binding<Bool> {
        Binding(
            get: { model.state.confirm != nil && model.state.picker == nil && !pickerVisible },
            set: { _ in }
        )
    }

    private var confirmBody: String {
        guard let confirm = model.state.confirm, let day = TravelDate.parse(confirm.untilDay) else {
            return ""
        }
        return L10n.format("voyage_confirm_body", short(day))
    }

    @ViewBuilder
    private func pickerSheet() -> some View {
        if let picker = model.state.picker {
            TravelDatePickerSheet(
                picker: picker,
                onChoose: { model.onDayChosen($0) },
                onCancel: { model.onPickerDismissed() }
            )
            .onAppear { pickerVisible = true }
        }
    }
}

/// Les phrases calmes sous la carte (pures : testées sans interface).
enum TravelCardTexts {
    static func key(for message: TravelMessage) -> String {
        switch message {
        case .resumed:
            return "voyage_off_done"
        case .offline:
            return "voyage_offline_note"
        case .refused:
            return "voyage_error_refused"
        case .updateRefused:
            return "voyage_error_update_refused"
        }
    }

    static func text(for message: TravelMessage) -> String {
        L10n.string(key(for: message))
    }

    /// Seules les phrases qui disent qu'un geste n'a pas abouti sont dessinées comme une erreur calme.
    static func isError(_ message: TravelMessage) -> Bool {
        message == .refused || message == .updateRefused
    }
}

/// La ligne de l'interrupteur : l'avion, « Mode voyage », « En voyage jusqu'au 15 nov. » (ou l'aide quand il est éteint), l'interrupteur.
/// La ligne porte le toucher et le texte pour le lecteur d'écran : le dessin de l'interrupteur n'est qu'une image de l'état.
private struct TravelSwitchRow: View {
    let isOn: Bool
    let working: Bool
    let shortDay: String?
    let spokenDay: String?
    let action: () -> Void

    private var stateText: String {
        if isOn, let spokenDay {
            return L10n.format("voyage_card_state_on_a11y", spokenDay)
        }
        return L10n.string("voyage_card_state_off_a11y")
    }

    private var subtitle: String {
        if isOn, let shortDay {
            return L10n.format("voyage_card_on_status", shortDay)
        }
        return L10n.string("voyage_card_off_body")
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.md) {
                ZStack {
                    Circle().fill(colorActionPrimaryTint20.color)
                    Image(systemName: "airplane")
                        .font(.system(size: Spacing.sectionIcon, weight: .regular))
                        .foregroundColor(colorActionPrimary.color)
                }
                .frame(width: Spacing.xl + Spacing.sm, height: Spacing.xl + Spacing.sm)
                .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 0) {
                    Text("voyage_card_title")
                        .miffTextStyle(textBodyBold16)
                        .foregroundColor(colorTextPrimary.color)
                        .accessibilityAddTraits(.isHeader)
                    Text(verbatim: subtitle)
                        .miffTextStyle(textBodyRegular14)
                        .foregroundColor(colorTextDescription.color)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
                Toggle(isOn: .constant(isOn)) {
                    Text(verbatim: "")
                }
                .labelsHidden()
                .tint(colorActionPrimary.color)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
            }
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm + Spacing.xs)
            .frame(minHeight: Spacing.minTouchTarget)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(working)
        .opacity(working ? 0.5 : 1)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("voyage_card_title"))
        // La date n'est lue QU'UNE fois : par l'état de l'interrupteur (« Activé, jusqu'au 15 novembre 2026 »), pas par la phrase dessinée.
        .accessibilityValue(Text(verbatim: stateText))
        .accessibilityHint(Text(verbatim: isOn ? "" : L10n.string("voyage_card_off_body")))
        .accessibilityAddTraits(.isButton)
    }
}

/// « Date de fin    15 nov. 2026  > » : ouvre le sélecteur de date. La date est lue en toutes lettres, une seule fois.
private struct TravelEndDateRow: View {
    let written: String
    let spoken: String
    let working: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: Spacing.sm) {
                Text("voyage_card_end_label")
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextDescription.color)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(verbatim: written)
                    .miffTextStyle(textBodyBold16)
                    .foregroundColor(colorTextPrimary.color)
                Image(systemName: "chevron.forward")
                    .foregroundColor(colorActionPrimary.color)
            }
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.xs)
            .frame(minHeight: Spacing.minTouchTarget)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(working)
        .opacity(working ? 0.5 : 1)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("voyage_card_end_label"))
        .accessibilityValue(Text(verbatim: spoken))
        .accessibilityHint(Text("voyage_card_end_change_a11y"))
        .accessibilityAddTraits(.isButton)
    }
}

/// Le sélecteur de date du SYSTÈME, en feuille : aujourd'hui au plus tôt, aujourd'hui + 89 jours au plus tard (un voyage dure 90 jours au
/// plus). Les jours sont ceux du calendrier du téléphone. « Continuer » pour un nouveau voyage (la confirmation suit), « Valider » pour
/// changer la fin d'un voyage en cours.
private struct TravelDatePickerSheet: View {
    let picker: TravelPickerState
    let onChoose: (TravelDate) -> Void
    let onCancel: () -> Void
    @State private var selection: Date

    init(picker: TravelPickerState, onChoose: @escaping (TravelDate) -> Void, onCancel: @escaping () -> Void) {
        self.picker = picker
        self.onChoose = onChoose
        self.onCancel = onCancel
        _selection = State(initialValue: Self.date(of: picker.initialDay))
    }

    /// Midi, dans le calendrier du téléphone : jamais un jour décalé par une heure d'été.
    private static func date(of day: TravelDate) -> Date {
        Calendar.current.date(from: DateComponents(year: day.year, month: day.month, day: day.day, hour: 12)) ?? Date()
    }

    private var range: ClosedRange<Date> {
        let first = Self.date(of: picker.minDay)
        let last = Self.date(of: picker.maxDay)
        return first...max(first, last)
    }

    private var chosenDay: TravelDate {
        let parts = Calendar.current.dateComponents([.year, .month, .day], from: selection)
        return TravelDate(year: parts.year ?? picker.initialDay.year, month: parts.month ?? 1, day: parts.day ?? 1)
    }

    private func choose() {
        onChoose(chosenDay)
    }

    private var chooseKey: String {
        picker.purpose == .start ? "voyage_picker_confirm" : "voyage_picker_validate"
    }

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.md) {
                DatePicker(selection: $selection, in: range, displayedComponents: .date) {
                    Text("voyage_card_end_label")
                }
                .datePickerStyle(.graphical)
                .tint(colorActionPrimary.color)
                Button(action: choose) {
                    Text(verbatim: L10n.string(chooseKey))
                }
                .buttonStyle(.miffPrimaryFullWidth)
                Button(action: onCancel) {
                    Text("voyage_confirm_cancel")
                }
                .buttonStyle(.miffSecondaryFullWidth)
            }
            .padding(Spacing.md)
        }
        .background(colorBgSurface.color.ignoresSafeArea())
        .presentationDetents([.large])
    }
}
