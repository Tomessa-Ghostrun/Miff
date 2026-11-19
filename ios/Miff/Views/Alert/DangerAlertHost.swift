#if ALERT_DANGER
import SwiftUI

// L'hôte de l'alerte dans la coquille (docs/tickets/alerte-danger.md 6 et 7 ; miroir d'`AlertHost.kt` et d'`AlertLifecycle` d'Android) : ce qui garde l'alerte
// VRAIE pendant que les onglets sont à l'écran. Le pointeur du téléphone est repris à l'ouverture ; l'alerte est vérifiée à chaque retour au premier plan (1 lecture,
// seulement pendant une alerte active) ; l'horloge termine une alerte arrivée à ses 7 jours (aucune écriture, aucune notification) ; les Miffs de la personne
// disent si la Miff d'origine est encore là. L'écran « avertis » est posé par-dessus la coquille (barre d'onglets comprise : jamais la bannière dessus).

/// Les noms des Miffs de la personne par identifiant, tels que l'annuaire les donne.
func dangerMiffNames(_ directory: DirectoryState) -> [String: String] {
    guard case .ready(let ready) = directory else {
        return [:]
    }
    return Dictionary(ready.miffs.map { ($0.id, $0.name) }, uniquingKeysWith: { first, _ in first })
}

/// Un nom de Miff vide ou blanc n'est pas un nom : le repli est « ta Miff » (les textes `_no_name`), jamais une ligne vide ni un nom lu comme un blanc par VoiceOver.
func dangerNamed(_ name: String?) -> String? {
    guard let trimmed = name?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else {
        return nil
    }
    return trimmed
}

struct DangerAlertHost: ViewModifier {
    @ObservedObject var model: DangerAlertViewModel
    let directory: DirectoryState
    let onOpenMiff: (String) -> Void
    @Environment(\.scenePhase) private var scenePhase

    /// Les Miffs connues. Une liste ne termine JAMAIS une alerte (une Miff dont la lecture échoue manque dans la liste) : elle ne sert qu'à l'adoption sur un nouveau téléphone.
    private var known: [String] {
        guard case .ready(let ready) = directory else {
            return []
        }
        return ready.miffs.map { $0.id }
    }

    func body(content: Content) -> some View {
        content
            .overlay {
                if let route = model.sentRoute {
                    DangerSentHost(model: model, route: route, onOpenMiff: onOpenMiff)
                        .transition(.opacity)
                }
            }
            .overlay(alignment: .bottom) {
                if let notice = model.notice {
                    UpdateConfirmation(text: notice)
                        .padding(.bottom, Spacing.xl * 2)
                        .allowsHitTesting(false)
                }
            }
            .onAppear {
                model.miffNames = dangerMiffNames(directory)
                model.restore()
            }
            .onChange(of: directory) { next in
                model.miffNames = dangerMiffNames(next)
            }
            // Les Miffs sont connues : un nouveau téléphone adopte une alerte en cours.
            .task(id: known) {
                if !known.isEmpty {
                    await model.onMiffsKnown(known)
                }
            }
            .onChange(of: scenePhase) { phase in
                if phase == .active {
                    Task { await model.onForeground() }
                }
            }
            .task {
                while !Task.isCancelled {
                    try? await Task.sleep(nanoseconds: UInt64(DangerAlertRules.tickSeconds * 1_000_000_000))
                    model.controller.tick()
                }
            }
            .task(id: model.notice) {
                if model.notice != nil {
                    try? await Task.sleep(nanoseconds: UInt64(DangerAlertRules.endedNoticeSeconds * 1_000_000_000))
                    if !Task.isCancelled {
                        model.notice = nil
                    }
                }
            }
    }
}

/// L'écran « avertis » avec ce qu'il lit de l'alerte : il ne dit que ce qui est vrai (le titre « viennent d'être avertis » seulement une fois l'alerte acceptée).
struct DangerSentHost: View {
    @ObservedObject var model: DangerAlertViewModel
    @ObservedObject private var controller: DangerAlertController
    let route: DangerSentRoute
    let onOpenMiff: (String) -> Void
    @Environment(\.openURL) private var openURL

    init(model: DangerAlertViewModel, route: DangerSentRoute, onOpenMiff: @escaping (String) -> Void) {
        self.model = model
        _controller = ObservedObject(wrappedValue: model.controller)
        self.route = route
        self.onOpenMiff = onOpenMiff
    }

    private var phase: DangerSentPhase {
        if route.unavailable || route.miffId == nil {
            return .unavailable
        }
        if route.stopped {
            return .sharingStopped
        }
        switch controller.state {
        case .active:
            return .sent
        case .sending(_, _, let step):
            switch step {
            case .waitingNetwork:
                return .waiting
            case .needsConfirmation:
                return .needsConfirmation
            case .refused:
                return .refused
            case .quota:
                return .quota
            case .sending:
                return .sending
            }
        case .idle:
            return .sending
        }
    }

    var body: some View {
        DangerSentView(
            phase: phase,
            miffName: route.miffId.flatMap { dangerNamed(model.miffNames[$0]) },
            several: model.miffNames.count > 1,
            call: model.call,
            actions: actions
        )
        .transition(.opacity)
        .onAppear {
            model.loadCallList(miffId: route.miffId)
        }
        .onChange(of: route) { next in
            model.loadCallList(miffId: next.miffId)
        }
        // La limite du jour est atteinte : les services d'urgence sont le choix déjà fait (jamais laissé sans issue).
        .onChange(of: phase) { next in
            if next == .quota {
                model.selectEmergency()
            }
        }
        .onChange(of: model.call.loading) { loading in
            if !loading && phase == .quota {
                model.selectEmergency()
            }
        }
        // L'alerte a disparu (annulée pendant l'attente, ou téléphone remplacé) et rien ne l'explique : retour là où la personne était, sans un mot.
        .onChange(of: controller.state) { state in
            if state == .idle && !route.stopped && !route.unavailable && route.miffId != nil {
                model.sentRoute = nil
            }
        }
    }

    private var actions: DangerSentActions {
        let model = self.model
        let route = self.route
        let controller = self.controller
        return DangerSentActions(
            onBack: {
                // Une limite du jour atteinte n'a rien écrit : revenir en arrière laisse un état propre (l'abandon est confirmé par le serveur).
                if phase == .quota {
                    controller.cancelPending()
                }
                model.sentRoute = nil
            },
            onSelect: { model.select($0) },
            onCall: { target in
                if let url = model.dialURL(for: target) {
                    openURL(url)
                } else {
                    model.notice = L10n.string("feed_action_unavailable")
                }
            },
            onShowEmergency: { model.selectEmergency() },
            onRetry: { controller.retry() },
            onCancelPending: {
                controller.cancelPending()
                if phase == .unavailable || phase == .sharingStopped {
                    model.sentRoute = nil
                }
            },
            onConfirmStale: { controller.confirmStale() },
            onResumeSharing: {
                model.sentRoute = nil
                if let id = route.miffId {
                    onOpenMiff(id)
                }
            }
        )
    }
}

/// La bannière au-dessus de la barre d'onglets (sur Feed, Update et Profil), dans la colonne du bas de la coquille.
struct DangerBannerSlot: View {
    @ObservedObject var model: DangerAlertViewModel
    @ObservedObject private var controller: DangerAlertController
    let activeMiffId: String?
    /// Jamais sur l'aperçu d'une photo en cours de publication.
    let hidden: Bool
    let onSeeMiff: (String) -> Void
    @State private var endConfirmOpen = false

    init(model: DangerAlertViewModel, activeMiffId: String?, hidden: Bool, onSeeMiff: @escaping (String) -> Void) {
        self.model = model
        _controller = ObservedObject(wrappedValue: model.controller)
        self.activeMiffId = activeMiffId
        self.hidden = hidden
        self.onSeeMiff = onSeeMiff
    }

    var body: some View {
        if let origin = controller.state.miffId, !hidden, model.sentRoute == nil {
            DangerBannerView(
                alert: controller.state,
                miffName: dangerNamed(model.miffNames[origin]),
                showSeeMiff: origin != activeMiffId && dangerNamed(model.miffNames[origin]) != nil,
                actions: DangerBannerActions(
                    onCall: { model.openSent(miffId: origin) },
                    onSeeMiff: { onSeeMiff(origin) },
                    onEnd: { controller.end() },
                    onEndAccessible: { endConfirmOpen = true },
                    onRetry: { controller.retry() },
                    onClose: { controller.cancelPending() }
                )
            )
            .padding(.horizontal, Spacing.md)
            .padding(.bottom, Spacing.sm)
            .dangerEndConfirm(isPresented: $endConfirmOpen, miffName: dangerNamed(model.miffNames[origin])) {
                controller.end()
            }
        }
    }
}
#endif
