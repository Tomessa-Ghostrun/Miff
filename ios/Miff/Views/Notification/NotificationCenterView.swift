import SwiftUI

// Le centre de notifications (ticket §7 et §20.4) : l'historique LOCAL de ce qui s'est passé sur cet appareil. Il ne montre ni position,
// ni adresse, ni nom de lieu. Wireframes de la spec (pas encore de maquettes) : écarts listés dans la PR.

/// Ce que fait le centre vers l'extérieur.
struct NotificationCenterActions {
    let onBack: () -> Void
    /// Toucher une ligne : l'écran à montrer (le centre se referme).
    let onOpen: (NotificationDestination) -> Void
    /// Refus définitif du téléphone : l'explication calme, puis les Réglages d'iOS.
    let onOpenHelp: () -> Void
}

struct NotificationCenterView: View {
    @StateObject private var model: NotificationCenterViewModel
    @ObservedObject private var service: NotificationCenterService
    private let actions: NotificationCenterActions
    /// Le nom de la Miff d'une ligne, seulement quand le compte en a plusieurs (sinon nil).
    private let miffName: (String) -> String?
    @Environment(\.scenePhase) private var scenePhase

    init(
        model: @autoclosure @escaping () -> NotificationCenterViewModel,
        service: NotificationCenterService,
        miffName: @escaping (String) -> String?,
        actions: NotificationCenterActions
    ) {
        _model = StateObject(wrappedValue: model())
        _service = ObservedObject(wrappedValue: service)
        self.miffName = miffName
        self.actions = actions
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            if let lock = model.lock {
                // Refusées ou jamais demandées : le centre flou, jamais une vraie ligne (la personne les voit dès qu'elle les active).
                NotificationCenterLockedView(lock: lock) {
                    Task { await model.enable(openHelp: actions.onOpenHelp) }
                }
            } else if model.showsContent {
                if service.history.isEmpty {
                    emptyState
                } else {
                    list
                }
            } else {
                // L'état de l'autorisation n'est pas encore lu : rien, le temps d'un instant.
                Color.clear.frame(maxHeight: .infinity)
            }
        }
        .background(colorBgCanvas.color.ignoresSafeArea())
        .task { await model.onAppear() }
        .onDisappear { model.onDisappear() }
        .onChange(of: scenePhase) { phase in
            if phase == .active {
                Task { await model.onAppear() }
            }
        }
        .confirmationDialog(
            Text("notif_center_clear_confirm_title"),
            isPresented: $model.deleteAllAsked,
            titleVisibility: .visible
        ) {
            Button("notif_center_clear_confirm_action", role: .destructive) { model.confirmDeleteAll() }
            Button("common_cancel", role: .cancel) {}
        } message: {
            Text("notif_center_clear_confirm_body")
        }
    }

    private var header: some View {
        ZStack {
            MiffHeader(title: L10n.string("notif_center_title"), onBack: actions.onBack)
            HStack {
                Spacer()
                if model.showsContent && !service.history.isEmpty {
                    menu
                }
            }
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.vertical, Spacing.sm)
    }

    private var menu: some View {
        Menu {
            Button("notif_center_mark_all_read") { model.markAllRead() }
            Button("notif_center_clear_all", role: .destructive) { model.askDeleteAll() }
        } label: {
            Image(systemName: "ellipsis")
                .font(.system(size: 20, weight: .regular))
                .foregroundColor(colorTextForm.color)
                .frame(width: Spacing.minTouchTarget, height: Spacing.minTouchTarget)
        }
        .accessibilityLabel("notif_center_menu_a11y")
    }

    private var emptyState: some View {
        VStack(spacing: Spacing.sm) {
            Image(systemName: "bell")
                .font(.system(size: 40, weight: .light))
                .foregroundColor(colorActionPrimary.color)
                .accessibilityHidden(true)
            Text("notif_center_empty_title")
                .miffTextStyle(textDisplayTitle)
                .foregroundColor(colorTextForm.color)
                .accessibilityAddTraits(.isHeader)
            Text("notif_center_empty_body")
                .miffTextStyle(textBodyRegular14)
                .foregroundColor(colorTextDescription.color)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(Spacing.lg)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .combine)
    }

    private var list: some View {
        let now = NotificationClock.wallMillis()
        let groups = NotificationGrouping.groups(of: service.history.items, nowMillis: now, calendar: .current)
        return List {
            ForEach(groups, id: \.section.titleKey) { group in
                Section {
                    ForEach(group.items) { item in
                        row(item, section: group.section)
                    }
                } header: {
                    Text(verbatim: L10n.string(group.section.titleKey))
                        .miffTextStyle(textBodyBold14)
                        .foregroundColor(colorTextDescription.color)
                        .accessibilityAddTraits(.isHeader)
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
    }

    private func row(_ item: NotificationItem, section: NotificationSection) -> some View {
        let time = NotificationTimeText.text(
            createdAtMillis: item.createdAtMillis,
            section: section,
            locale: .current,
            timeZone: .current
        )
        return Button {
            actions.onOpen(model.open(item))
        } label: {
            NotificationRowContent(item: item, time: time, miffName: miffName(item.miffId))
        }
        .buttonStyle(.plain)
        .listRowBackground(colorBgCanvas.color)
        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
            Button("ios_notif_delete", role: .destructive) { model.delete(item) }
        }
        .contextMenu {
            Button("ios_notif_delete", role: .destructive) { model.delete(item) }
        }
    }
}

/// Une ligne : le point « non lu » (jamais un chiffre), le titre, la phrase, le nom de la Miff s'il y en a plusieurs, l'heure. Lue comme
/// UN élément par VoiceOver : « {titre}, {heure}, non lu ».
private struct NotificationRowContent: View {
    let item: NotificationItem
    let time: String
    let miffName: String?

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.sm) {
            Circle()
                .fill(item.isUnread ? colorActionPrimary.color : Color.clear)
                .frame(width: 10, height: 10)
                .padding(.top, Spacing.xs + 2)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: L10n.string(item.type.titleKey))
                    .miffTextStyle(item.isUnread ? textBodyBold16 : textBodyRegular)
                    .foregroundColor(colorTextForm.color)
                Text(verbatim: L10n.string(item.type.bodyKey))
                    .miffTextStyle(textBodyRegular14)
                    .foregroundColor(colorTextDescription.color)
                if let miffName {
                    Text(verbatim: L10n.format("notif_center_in_miff", miffName))
                        .miffTextStyle(textBodyCaption)
                        .foregroundColor(colorTextSecondary.color)
                }
                Text(verbatim: time)
                    .miffTextStyle(textBodyCaption)
                    .foregroundColor(colorTextSecondary.color)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.vertical, Spacing.xs)
        .frame(minHeight: Spacing.minTouchTarget)
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(
            Text(verbatim: NotificationTimeText.spokenLabel(
                title: L10n.string(item.type.titleKey), time: time, unread: item.isUnread
            ))
        )
        .accessibilityAddTraits(.isButton)
    }
}
