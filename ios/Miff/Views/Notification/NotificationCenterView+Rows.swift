import SwiftUI

// Les lignes du centre de notifications : la liste des jours (Aujourd'hui, Hier, Plus ancien) au-dessous du groupe « Depuis ta dernière visite », et une
// ligne. Séparé de `NotificationCenterView.swift` pour une taille de fichier et de type raisonnable ; rien de nouveau dans ce qui est dit.

extension NotificationCenterView {
    var list: some View {
        let now = NotificationClock.wallMillis()
        let groups = NotificationGrouping.groups(
            of: PlaceRequestLines.displayed(model.shown(service.history.dayItems)), nowMillis: now, calendar: .current
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
    var visitItems: [NotificationItem] {
        service.history.visitItems
    }

    private func rowContent(_ item: NotificationItem, section: NotificationSection?) -> NotificationRowContent {
        // CNC-I : l'image ronde et, pour un proche, son prénom : tirés de la MÊME fiche, en mémoire (jamais écrits dans le fichier du centre).
        let look = model.look(of: item)
        if item.type == .c1 {
            // Une phrase entière faite maintenant (prénom connu maintenant, heure au format du téléphone) : l'heure y est, aucune seconde ligne.
            var sentence = L10n.string("notif_c1_title")
            if let parsed = LastVisit.parse(item) {
                sentence = LastVisitTexts().sentence(
                    parsed, firstName: model.visitName(of: item), nowMillis: NotificationClock.wallMillis()
                )
            }
            let text = NotificationRowText(title: sentence, body: nil, miffName: miffName(item.miffId))
            return NotificationRowContent(item: item, text: text, time: nil, avatar: look.avatar)
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
            firstName: model.requesterName(of: item),
            lovedOneName: look.name
        )
        return NotificationRowContent(item: item, text: text, time: time, avatar: look.avatar)
    }

    func row(_ item: NotificationItem, section: NotificationSection?) -> some View {
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

/// Une ligne : le point « non lu » (jamais un chiffre), l'image ronde de la personne ou le logo, le titre, la phrase, le nom de la Miff s'il y en a
/// plusieurs, l'heure. Lue comme UN élément par VoiceOver : « {titre}, {heure}, non lu » ; l'image est décorative (le prénom est déjà dans le titre).
struct NotificationRowContent: View {
    let item: NotificationItem
    let text: NotificationRowText
    /// nil pour une ligne « Depuis ta dernière visite » : sa phrase porte déjà son heure.
    let time: String?
    /// CNC-I : l'image ronde de la ligne.
    let avatar: CenterAvatar

    /// Le titre, et pour une alerte aussi sa phrase (« Prends de ses nouvelles : appelle Léa ou ouvre Miff. » : ce qu'il y a à faire est dit, pas seulement ce qui s'est passé).
    private var spokenTitle: String {
        guard item.type == .n10 || item.type == .n11, let body = text.body else {
            return text.title
        }
        return [text.title, body].joined(separator: ". ")
    }

    private var spoken: String {
        guard let time else {
            return NotificationTimeText.spokenLabel(sentence: spokenTitle, unread: item.isUnread)
        }
        return NotificationTimeText.spokenLabel(title: spokenTitle, time: time, unread: item.isUnread)
    }

    var body: some View {
        HStack(alignment: .top, spacing: Spacing.sm) {
            Circle()
                .fill(item.isUnread ? colorActionPrimary.color : Color.clear)
                .frame(width: 10, height: 10)
                .padding(.top, Spacing.xs + 2)
                .accessibilityHidden(true)
            CenterAvatarView(avatar: avatar)
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
