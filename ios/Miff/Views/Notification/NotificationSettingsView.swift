import SwiftUI

// Réglages > Notifications (ticket §9 et §20.11) : l'état de l'autorisation du téléphone, l'interrupteur général, les cinq interrupteurs
// de type et la nuit calme. Aucun interrupteur pour N5 (abandonnée : on ne montre pas ce qui ne marche pas). Un changement a effet
// aussitôt. Wireframe de la spec (pas encore de maquette) : écarts listés dans la PR.

struct NotificationSettingsActions {
    let onBack: () -> Void
    /// « Voir mes notifications » : le centre.
    let onOpenCenter: () -> Void
    /// « Tes notifications sont coupées » : l'écran d'aide.
    let onOpenHelp: () -> Void
}

struct NotificationSettingsView: View {
    @StateObject private var model: NotificationSettingsViewModel
    private let actions: NotificationSettingsActions
    @Environment(\.scenePhase) private var scenePhase

    init(model: @autoclosure @escaping () -> NotificationSettingsViewModel, actions: NotificationSettingsActions) {
        _model = StateObject(wrappedValue: model())
        self.actions = actions
    }

    var body: some View {
        MiffFrame(title: L10n.string("notif_settings_title"), onBack: actions.onBack, usesCanvas: true) {
            authorizationCard
            NotificationSwitchRow(
                title: L10n.string("notif_settings_general"),
                description: nil,
                isOn: Binding(get: { model.generalIsOn }, set: { model.setGeneral($0) }),
                isEnabled: model.generalIsEnabled
            )
            sectionForYou
            sectionForYourMiff
            nightCalm
            SettingsLinkRow(
                title: L10n.string("notif_settings_see_all"),
                spokenHint: "",
                action: actions.onOpenCenter
            )
            recordedCounters
        }
        .task { await model.refresh() }
        .onChange(of: scenePhase) { phase in
            if phase == .active {
                Task { await model.refresh() }
            }
        }
        .sheet(isPresented: $model.primeVisible) {
            NotificationsPopIn(
                onAllow: { Task { await model.primeAllowed() } },
                onLater: { model.primeDeferred() }
            )
        }
    }

    // MARK: - Recette (builds Debug et Internal seulement)

    /// Les rappels N3 / N4 affichés alors que la couleur de la Miff était déjà revenue : un compteur LOCAL pour décider après la bêta.
    @ViewBuilder
    private var recordedCounters: some View {
        if BuildFlavor.includesDiagnostic, let counters = model.recordedCounters {
            SettingsBody(text: L10n.format("ios_notif_debug_counters", counters.falseAlerts, counters.judged))
        }
    }

    // MARK: - L'autorisation du téléphone

    @ViewBuilder
    private var authorizationCard: some View {
        if let authorization = model.authorization {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                Text("notif_settings_authorization")
                    .miffTextStyle(textBodyBold16)
                    .foregroundColor(colorTextPrimary.color)
                    .accessibilityAddTraits(.isHeader)
                Text(verbatim: L10n.string(authorization.stateTextKey))
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextDescription.color)
                if authorization.needsPhoneSettings {
                    Button {
                        model.openPhoneSettings()
                    } label: {
                        Text("notif_state_open_settings")
                    }
                    .buttonStyle(.miffSecondaryFullWidth)
                    SettingsLinkRow(
                        title: L10n.string("ios_notif_help_link"),
                        spokenHint: "",
                        action: actions.onOpenHelp
                    )
                }
            }
            .padding(Spacing.md)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
            .overlay(
                RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                    .stroke(colorBorderDefault.color, lineWidth: strokeHairline)
            )
            .accessibilityElement(children: .contain)
        }
    }

    // MARK: - Les types

    @ViewBuilder
    private var sectionForYou: some View {
        SettingsSectionTitle(key: "notif_settings_section_you")
        typeRow(.n1)
        typeRow(.n2)
        typeRow(.n6)
        // Couper N6 coupe la notification, jamais ce que la Miff voit : c'est dit ici.
        SettingsBody(text: L10n.string("notif_settings_n6_hint"))
    }

    @ViewBuilder
    private var sectionForYourMiff: some View {
        SettingsSectionTitle(key: "notif_settings_section_miff")
        typeRow(.n3)
        typeRow(.n4)
    }

    private func typeRow(_ type: NotificationType) -> some View {
        NotificationSwitchRow(
            title: L10n.string(type.settingTitleKey),
            description: nil,
            isOn: Binding(
                get: { model.preferences.typeValue(type) },
                set: { model.setType(type, isOn: $0) }
            ),
            isEnabled: model.typesAreEnabled
        )
    }

    @ViewBuilder
    private var nightCalm: some View {
        SettingsSectionTitle(key: "notif_settings_night_title")
        NotificationSwitchRow(
            title: L10n.string("notif_settings_night_title"),
            description: L10n.string("notif_settings_night_body"),
            isOn: Binding(get: { model.preferences.nightCalm }, set: { model.setNightCalm($0) }),
            isEnabled: model.typesAreEnabled
        )
    }
}

/// Une ligne avec son interrupteur : le titre, une courte explication éventuelle, l'interrupteur. 44 pt au moins ; l'état est lu par
/// VoiceOver (l'interrupteur natif dit « activé » ou « désactivé »), jamais porté par la seule couleur.
struct NotificationSwitchRow: View {
    let title: String
    let description: String?
    @Binding var isOn: Bool
    let isEnabled: Bool

    var body: some View {
        Toggle(isOn: $isOn) {
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: title)
                    .miffTextStyle(textBodyBold16)
                    .foregroundColor(colorTextPrimary.color)
                if let description {
                    Text(verbatim: description)
                        .miffTextStyle(textBodyRegular14)
                        .foregroundColor(colorTextDescription.color)
                }
            }
            .fixedSize(horizontal: false, vertical: true)
        }
        .tint(colorActionPrimary.color)
        .disabled(!isEnabled)
        .padding(.horizontal, Spacing.md)
        .padding(.vertical, Spacing.sm)
        .frame(minHeight: Spacing.minTouchTarget)
        .background(RoundedRectangle(cornerRadius: radiusMd, style: .continuous).fill(colorBgSurface.color))
        .overlay(
            RoundedRectangle(cornerRadius: radiusMd, style: .continuous)
                .stroke(colorBorderDefault.color, lineWidth: strokeHairline)
        )
        .opacity(isEnabled ? 1 : 0.5)
    }
}
