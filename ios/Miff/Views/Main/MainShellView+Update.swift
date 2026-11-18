import SwiftUI

// L'onglet Update de la coquille et ce que l'alerte y ajoute (docs/tickets/alerte-danger.md). Séparé de `MainShellView.swift` pour rester sous la limite de
// longueur d'un fichier. Sans la condition de compilation `ALERT_DANGER` (Release), c'est exactement l'Update d'avant.

extension MainShellView {
    @ViewBuilder
    var updateTab: some View {
        let tab = UpdateTabView(model: updateModel, miffCount: miffCount, actions: updateActions)
        #if ALERT_DANGER
        tab
            .environment(\.tabBarTopY, dangerTabBarTop)
            .environment(\.dangerUpdateHooks, dangerHooks)
        #else
        tab.environment(\.tabBarTopY, barVisible ? barTopY : nil)
        #endif
    }
}

#if ALERT_DANGER
/// Le haut de la bannière de l'alerte, mesuré (coordonnées globales) : l'obturateur de l'Update se pose au-dessus d'elle.
struct DangerBannerTopPreference: PreferenceKey {
    static let defaultValue: CGFloat? = nil

    static func reduce(value: inout CGFloat?, nextValue: () -> CGFloat?) {
        value = nextValue() ?? value
    }
}

extension MainShellView {
    /// La barre ou, quand la bannière est là, la bannière : le contenu de l'Update s'arrête au-dessus du plus haut des deux.
    var dangerTabBarTop: CGFloat? {
        guard barVisible else {
            return nil
        }
        if let banner = dangerBannerTopY, let bar = barTopY {
            return min(banner, bar)
        }
        return barTopY
    }

    /// La bannière n'est jamais sur l'aperçu d'une photo en cours de publication (ni sur l'écran « avertis », qui la recouvre).
    var dangerBannerHidden: Bool {
        if case .preview = updateModel.stage, shownTab == .update {
            return true
        }
        return false
    }

    var dangerBanner: some View {
        DangerBannerSlot(
            model: danger.model, activeMiffId: activeMiffId, hidden: dangerBannerHidden, onSeeMiff: danger.onSelectMiff
        )
        .background(
            GeometryReader { proxy in
                Color.clear.preference(key: DangerBannerTopPreference.self, value: proxy.frame(in: .global).minY)
            }
        )
    }

    /// Ce que l'Update a besoin de savoir de l'alerte à cet instant.
    var dangerHooks: DangerUpdateHooks {
        let model = danger.model
        let names = dangerMiffNames(miffs)
        let state = model.controller.state
        let activeName = activeMiffId.flatMap { dangerNamed(names[$0]) }
        return DangerUpdateHooks(
            hintVisible: !model.hintDone,
            onHintDone: { model.onHintDone() },
            targetMiffName: activeName,
            several: names.count > 1,
            activeAlertMiffName: state.miffId.flatMap { dangerNamed(names[$0]) },
            // Seule une alerte VRAIMENT en route ou envoyée interdit un second maintien (et dit « Une alerte est déjà en cours ») : une alerte refusée, la limite du jour
            // ou la question des 15 minutes ne l'interdisent pas, la personne peut recommencer.
            alertInProgress: model.controller.blocksNewAlert,
            onTrigger: { dangerTriggered(model: model, activeMiffId: activeMiffId, names: names) },
            onAlreadyActive: { name in
                model.notice = alreadyActiveNotice(name)
            },
            // Depuis les écrans de l'Update SANS viseur (partage arrêté, caméra indisponible ou refusée) : les numéros d'urgence.
            onOpenEmergency: { stopped in
                if stopped, let origin = activeMiffId {
                    model.openSent(miffId: origin, stopped: true)
                } else {
                    model.openSent(miffId: nil, unavailable: true)
                }
            }
        )
    }

    /// Les 5 secondes ont été tenues (ou la confirmation accessible donnée) : l'alerte part dans la Miff sélectionnée dans le Feed. Sans Miff connue
    /// (le dossier n'est pas chargé), RIEN ne part mais la personne n'est jamais laissée sans issue : l'écran des numéros d'urgence s'ouvre.
    private func dangerTriggered(model: DangerAlertViewModel, activeMiffId: String?, names: [String: String]) {
        guard let miffId = activeMiffId else {
            // Les Miffs ne sont pas connues (encore) : rien ne part, mais les 5 secondes ont été tenues dans le stress : les numéros d'urgence et une
            // phrase calme, jamais rien.
            model.openSent(miffId: nil, unavailable: true)
            return
        }
        switch model.onTriggered(miffId: miffId) {
        case .started:
            model.openSent(miffId: miffId)
        case .alreadyActive(let current):
            model.notice = alreadyActiveNotice(dangerNamed(names[current]))
        case .notSignedIn:
            model.openSent(miffId: nil, unavailable: true)
        }
    }

    /// « Une alerte est déjà en cours pour la Miff {nom} » (ou « Une alerte est déjà en cours. » quand le nom n'est pas connu : jamais un blanc).
    private func alreadyActiveNotice(_ name: String?) -> String {
        guard let name, !name.isEmpty else {
            return L10n.string("alert_already_active_no_name")
        }
        return L10n.format("alert_already_active", name)
    }
}
#endif
