import SwiftUI

// Le centre de notifications (ticket §7 et §20.4) : l'historique de ce qui s'est passé sur cet appareil. Il ne montre ni position, ni adresse, ni nom de
// lieu. Wireframes de la spec (pas encore de maquettes) : écarts listés dans la PR. CNC-I : chaque ligne a son image ronde (la photo d'un proche, ma
// photo pour mes rappels, ou le logo de Miff), et les messages du serveur (« a donné des nouvelles », alertes) y entrent aussi.

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
    /// CNC-I : les visages (photo ou initiales, prénom) des proches : la ligne se redessine quand l'un d'eux est appris (mémoire seulement).
    let faces: CenterFaces
}

struct NotificationCenterView: View {
    @StateObject var model: NotificationCenterViewModel
    @ObservedObject var service: NotificationCenterService
    let actions: NotificationCenterActions
    let miffName: (String) -> String?
    let requestMiffName: (String) -> String?
    /// Observé : la ligne d'une demande se redessine quand le prénom du demandeur est lu.
    @ObservedObject var directory: RequesterDirectory
    /// CNC-I : observé : une ligne se redessine quand le visage de son proche est appris (le prénom et l'image viennent de la même fiche).
    @ObservedObject var faces: CenterFaces
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
        _faces = ObservedObject(wrappedValue: names.faces)
        self.actions = actions
    }

    var body: some View {
        // CNC-I : les membres dont les lignes montrées ont besoin du visage ; la lecture ne se relance que si cette liste change (jamais en boucle).
        let wanted = model.neededFaces()
        return VStack(spacing: 0) {
            header
            if let lock = model.lock {
                // Refusées ou jamais demandées : le centre flou, jamais une vraie ligne du téléphone (la personne les voit dès qu'elle les active).
                // Restent visibles AU-DESSUS du flou : « Depuis ta dernière visite » et ce que les proches ont fait (ce ne sont pas des notifications du téléphone).
                lockedContent(lock)
            } else if model.showsContent {
                if !hasShownLines {
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
        // CNC-I : le journal du serveur est lu une fois à l'ouverture (en arrière-plan : l'écran n'attend jamais ; hors ligne ou refusé, rien n'est montré),
        // puis les visages qui manquent.
        .task { await model.syncJournal() }
        .task(id: wanted) { await model.ensureFaces(wanted) }
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

    /// Le menu « Tout marquer comme lu » / « Tout effacer » : quand le centre a des lignes à montrer, ou, derrière le flou, ce qui reste visible au-dessus :
    /// « Depuis ta dernière visite » et ce que les proches ont fait (visibles quoi que dise l'autorisation du téléphone).
    private var hasMenu: Bool {
        if model.showsContent {
            return hasShownLines
        }
        return model.lock != nil && (!service.history.visitItems.isEmpty || !model.shownLovedOneItems.isEmpty)
    }

    /// Y a-t-il une ligne à dessiner ? (Une ligne d'un proche d'une Miff qui n'est plus celle du compte ne l'est pas.)
    private var hasShownLines: Bool {
        service.history.items.contains { model.isShown($0) }
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

    /// Le groupe quand les notifications du téléphone sont coupées ou jamais demandées : il reste visible AU-DESSUS du flou (décision Q4 :
    /// ce n'est pas une notification), le reste du centre derrière le flou et son unique bouton. CNC-7 : ce que les proches ont fait (nouvelles,
    /// alertes) reste visible aussi : c'est précisément le cas où le système n'avait rien montré, donc où la ligne est la seule trace. Les rappels
    /// du téléphone, eux, restent derrière le flou.
    @ViewBuilder
    private func lockedContent(_ lock: NotificationCenterLock) -> some View {
        let locked = NotificationCenterLockedView(lock: lock) {
            Task { await model.enable(openHelp: actions.onOpenHelp) }
        }
        let lovedGroups = NotificationGrouping.groups(
            of: model.shownLovedOneItems, nowMillis: NotificationClock.wallMillis(), calendar: .current
        )
        if visitItems.isEmpty && lovedGroups.isEmpty {
            locked
        } else {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    if !visitItems.isEmpty {
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
                    }
                    ForEach(lovedGroups, id: \.section.titleKey) { group in
                        Text(verbatim: L10n.string(group.section.titleKey))
                            .miffTextStyle(textBodyBold14)
                            .foregroundColor(colorTextDescription.color)
                            .accessibilityAddTraits(.isHeader)
                        ForEach(group.items) { item in
                            row(item, section: group.section)
                        }
                    }
                    locked
                }
                .padding(.horizontal, Spacing.md)
            }
        }
    }
}
