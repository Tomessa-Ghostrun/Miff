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

/// Les noms dont le centre a besoin pour écrire ses lignes, à l'affichage seulement.
struct NotificationCenterNames {
    /// Le nom de la Miff d'une ligne, seulement quand le compte en a plusieurs (sinon nil).
    let miff: (String) -> String?
    /// Le nom de la Miff d'une ligne de demande, TOUJOURS (sa phrase dit pour quelle Miff le lieu est proposé).
    let requestMiff: (String) -> String?
    /// Les prénoms des demandeurs (mémoire seulement) : la ligne se redessine quand l'un d'eux est lu.
    let requesters: RequesterDirectory
}

struct NotificationCenterView: View {
    @StateObject private var model: NotificationCenterViewModel
    @ObservedObject private var service: NotificationCenterService
    private let actions: NotificationCenterActions
    private let miffName: (String) -> String?
    private let requestMiffName: (String) -> String?
    /// Observé : la ligne d'une demande se redessine quand le prénom du demandeur est lu.
    @ObservedObject private var directory: RequesterDirectory
    @Environment(\.scenePhase) private var scenePhase

    init(
        model: @autoclosure @escaping () -> NotificationCenterViewModel,
        service: NotificationCenterService,
        names: NotificationCenterNames,
        actions: NotificationCenterActions
    ) {
        _model = StateObject(wrappedValue: model())
        _service = ObservedObject(wrappedValue: service)
        miffName = names.miff
        requestMiffName = names.requestMiff
        _directory = ObservedObject(wrappedValue: names.requesters)
        self.actions = actions
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            if let lock = model.lock {
                // Refusées ou jamais demandées : le centre flou, jamais une vraie ligne (la personne les voit dès qu'elle les active).
                // Seul le groupe « Depuis ta dernière visite » reste visible, AU-DESSUS du flou (ce n'est pas une notification du téléphone).
                lockedContent(lock)
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
                if hasMenu {
                    menu
                }
            }
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.vertical, Spacing.sm)
    }

    /// Le menu « Tout marquer comme lu » / « Tout effacer » : quand le centre a des lignes à montrer, ou, derrière le flou, le groupe
    /// « Depuis ta dernière visite » (visible quoi que dise l'autorisation du téléphone).
    private var hasMenu: Bool {
        if model.showsContent {
            return !service.history.isEmpty
        }
        return model.lock != nil && !service.history.visitItems.isEmpty
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
        let groups = NotificationGrouping.groups(
            of: PlaceRequestLines.displayed(service.history.dayItems), nowMillis: now, calendar: .current
        )
        return List {
            // NOTIF-P1I : « Depuis ta dernière visite », au-dessus d'« Aujourd'hui », puis la phrase qui dit ce que ces lignes sont.
            if !visitItems.isEmpty {
                Section {
                    ForEach(visitItems) { item in
                        row(item, section: nil)
                    }
                } header: {
                    Text(verbatim: L10n.string("notif_c1_title"))
                        .miffTextStyle(textBodyBold16)
                        .foregroundColor(colorTextForm.color)
                        .accessibilityAddTraits(.isHeader)
                } footer: {
                    Text(verbatim: L10n.string("notif_c1_limit"))
                        .miffTextStyle(textBodyCaption)
                        .foregroundColor(colorTextSecondary.color)
                }
            }
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

    /// Les lignes du groupe « Depuis ta dernière visite » (3 au plus, la plus récente d'abord).
    private var visitItems: [NotificationItem] {
        service.history.visitItems
    }

    /// Le groupe quand les notifications du téléphone sont coupées ou jamais demandées : il reste visible AU-DESSUS du flou (décision Q4 :
    /// ce n'est pas une notification), le reste du centre derrière le flou et son unique bouton.
    @ViewBuilder
    private func lockedContent(_ lock: NotificationCenterLock) -> some View {
        let locked = NotificationCenterLockedView(lock: lock) {
            Task { await model.enable(openHelp: actions.onOpenHelp) }
        }
        if visitItems.isEmpty {
            locked
        } else {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text(verbatim: L10n.string("notif_c1_title"))
                        .miffTextStyle(textBodyBold16)
                        .foregroundColor(colorTextForm.color)
                        .accessibilityAddTraits(.isHeader)
                    ForEach(visitItems) { item in
                        row(item, section: nil)
                    }
                    Text(verbatim: L10n.string("notif_c1_limit"))
                        .miffTextStyle(textBodyCaption)
                        .foregroundColor(colorTextSecondary.color)
                    locked
                }
                .padding(.horizontal, Spacing.md)
            }
        }
    }

    private func rowContent(_ item: NotificationItem, section: NotificationSection?) -> NotificationRowContent {
        if item.type == .c1 {
            // Une phrase entière faite maintenant (prénom connu maintenant, heure au format du téléphone) : l'heure y est, aucune seconde ligne.
            var sentence = L10n.string("notif_c1_title")
            if let parsed = LastVisit.parse(item) {
                sentence = LastVisitTexts().sentence(
                    parsed, firstName: model.visitName(of: item), nowMillis: NotificationClock.wallMillis()
                )
            }
            let text = NotificationRowText(title: sentence, body: nil, miffName: miffName(item.miffId))
            return NotificationRowContent(item: item, text: text, time: nil)
        }
        let time = NotificationTimeText.text(
            createdAtMillis: item.createdAtMillis,
            section: section ?? .today,
            locale: AppLanguage.locale,
            timeZone: .current
        )
        let text = NotificationRowTexts.text(
            for: item,
            count: model.ids(of: item).count,
            miffName: miffName(item.miffId),
            requestMiffName: requestMiffName(item.miffId),
            firstName: model.requesterName(of: item)
        )
        return NotificationRowContent(item: item, text: text, time: time)
    }

    private func row(_ item: NotificationItem, section: NotificationSection?) -> some View {
        let content = rowContent(item, section: section)
        return Button {
            actions.onOpen(model.open(item))
        } label: {
            content
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
    let text: NotificationRowText
    /// nil pour une ligne « Depuis ta dernière visite » : sa phrase porte déjà son heure.
    let time: String?

    private var spoken: String {
        guard let time else {
            return NotificationTimeText.spokenLabel(sentence: text.title, unread: item.isUnread)
        }
        return NotificationTimeText.spokenLabel(title: text.title, time: time, unread: item.isUnread)
    }

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.sm) {
            Circle()
                .fill(item.isUnread ? colorActionPrimary.color : Color.clear)
                .frame(width: 10, height: 10)
                .padding(.top, Spacing.xs + 2)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: text.title)
                    .miffTextStyle(item.isUnread ? textBodyBold16 : textBodyRegular)
                    .foregroundColor(colorTextForm.color)
                if let body = text.body {
                    Text(verbatim: body)
                        .miffTextStyle(textBodyRegular14)
                        .foregroundColor(colorTextDescription.color)
                }
                if let miffName = text.miffName {
                    Text(verbatim: L10n.format("notif_center_in_miff", miffName))
                        .miffTextStyle(textBodyCaption)
                        .foregroundColor(colorTextSecondary.color)
                }
                if let time {
                    Text(verbatim: time)
                        .miffTextStyle(textBodyCaption)
                        .foregroundColor(colorTextSecondary.color)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.vertical, Spacing.xs)
        .frame(minHeight: Spacing.minTouchTarget)
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: spoken))
        .accessibilityAddTraits(.isButton)
    }
}
